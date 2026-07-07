package lostmanager.commands.discord.util;

import java.nio.ByteBuffer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.messages.MessagePoll;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class checkpoll extends ListenerAdapter {

	// Max voters fetched per answer (JDA paginates the requests internally)
	private static final int VOTER_FETCH_LIMIT = 1000;
	// Headroom below Discord's 4096-char embed description limit
	private static final int DESCRIPTION_SOFT_LIMIT = 3800;

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("checkpoll"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Check-Poll";

			OptionMapping roleOption = event.getOption("role");
			OptionMapping messagelinkOption = event.getOption("message_link");

			if (roleOption == null || messagelinkOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Die Parameter Rolle und Message-Link sind pflicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Role role = roleOption.getAsRole();
			String messagelink = messagelinkOption.getAsString();

			// Encode numeric IDs to stay under 100 char button ID limit
			String buttonId;
			try {
				String[] linkParts = messagelink.split("/");
				String channelId = linkParts[linkParts.length - 2];
				String messageId = linkParts[linkParts.length - 1];
				buttonId = encodeButtonId(role.getId(), channelId, messageId);
			} catch (Exception e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültiger Message-Link.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			performCheckpoll(event.getHook(), event.getGuild(), title, role, messagelink, buttonId);

		}, "CheckpollCommand-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("cp_") && !id.equals("cpping"))
			return;

		event.deferEdit().queue();

		String title = "Check-Poll";

		// Handle ping button
		if (id.equals("cpping")) {
			// Check permissions - must be at least co-leader
			lostmanager.datawrapper.User userExecuted = new lostmanager.datawrapper.User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userExecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasPermission = true;
					break;
				}
			}

			if (!hasPermission) {
				return;
			}

			// Extract user IDs from message content and send pings
			new Thread(() -> {
				try {
					String messageContent = event.getMessage().getContentRaw();
					List<String> userIds = extractUserIdsFromMessage(messageContent);
					if (!userIds.isEmpty()) {
						event.getInteraction().getMessageChannel().sendMessage(
								String.join(" ",
										userIds.stream().map(uid -> "<@" + uid + ">").toArray(String[]::new)))
								.queue();
					}
				} catch (Exception e) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler beim Dekodieren der Benutzer-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				}
			}, "CheckpollPing-" + event.getUser().getId()).start();
			return;
		}

		// Handle refresh button
		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		new Thread(() -> {
			Guild guild = event.getGuild();
			if (guild == null) {
				return;
			}

			try {
				String[] params = decodeButtonId(id);
				if (params == null || params.length < 3) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String roleId = params[0];
				String channelId = params[1];
				String messageId = params[2];

				Role discordRole = guild.getRoleById(roleId);
				if (discordRole == null) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Fehler: Rolle konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				// Reconstruct the message link from IDs
				String messagelink = "https://discord.com/channels/" + guild.getId() + "/" + channelId + "/" + messageId;

				performCheckpoll(event.getHook(), guild, title, discordRole, messagelink, id);

			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "CheckpollRefresh-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	private void performCheckpoll(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild,
			String title, Role role, String messagelink, String buttonId) {

		if (guild == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Dieser Befehl kann nur auf einem Server ausgeführt werden.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String messageId = messagelink.split("/")[messagelink.split("/").length - 1];
		String channelId = messagelink.split("/")[messagelink.split("/").length - 2];

		MessageChannelUnion channel = null;
		if (channelId != null) {
			channel = MessageUtil.getChannelById(channelId);
			if (channel == null) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Channel mit dieser ID nicht gefunden.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		channel.retrieveMessageById(messageId).queue(message -> {
			MessagePoll poll = message.getPoll();

			if (poll == null) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Umfrage auf der Nachricht "
						+ messagelink + " gefunden.", MessageUtil.EmbedType.INFO)).queue();
				return;
			}

			List<MessagePoll.Answer> answers = poll.getAnswers();

			// Fetch the voters of all answers asynchronously
			List<CompletableFuture<List<net.dv8tion.jda.api.entities.User>>> futures = new ArrayList<>();
			for (MessagePoll.Answer answer : answers) {
				futures.add(message.retrievePollVoters(answer.getId()).takeAsync(VOTER_FETCH_LIMIT));
			}

			CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete((ok, error) -> {
				if (error != null) {
					hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler beim Laden der Umfrage-Stimmen.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				// Index-aligned with the poll answers
				List<Set<String>> voterIdsPerAnswer = new ArrayList<>();
				Set<String> allVoterIds = new HashSet<>();
				for (CompletableFuture<List<net.dv8tion.jda.api.entities.User>> future : futures) {
					Set<String> ids = future.join().stream()
							.map(net.dv8tion.jda.api.entities.User::getId).collect(Collectors.toSet());
					voterIdsPerAnswer.add(ids);
					allVoterIds.addAll(ids);
				}

				processMembers(hook, guild, title, role, messagelink, buttonId, poll, voterIdsPerAnswer,
						allVoterIds);
			});
		}, _ -> {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Nachricht mit dieser ID konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR)).queue();
		});
	}

	@SuppressWarnings("null")
	private void processMembers(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild, String title,
			Role role, String messagelink, String buttonId, MessagePoll poll,
			List<Set<String>> voterIdsPerAnswer, Set<String> allVoterIds) {

		// Resolve which clan this role belongs to (if any)
		String roleClanTag = getClanTagForRole(role.getId());

		guild.loadMembers().onSuccess(members -> {
			List<Member> roleMembers = members.stream()
					.filter(member -> member.getRoles().contains(role))
					.collect(Collectors.toList());

			// Voted = voted for at least one answer
			List<Member> missingMembers = roleMembers.stream()
					.filter(member -> !allVoterIds.contains(member.getId()))
					.collect(Collectors.toList());

			// Separate signed-off members
			List<Member> signedOffMembers = new ArrayList<>();
			List<Member> regularMissingMembers = new ArrayList<>();
			List<String> regularMissingUserIds = new ArrayList<>();

			for (Member member : missingMembers) {
				String discordId = member.getId();

				User user = new User(discordId);
				ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

				boolean isSignedOff = false;
				for (Player player : linkedAccounts) {
					Clan playerClan = player.getClanDB();
					if (roleClanTag != null && (playerClan == null || !playerClan.getTag().equals(roleClanTag))) {
						continue;
					}
					MemberSignoff signoff = new MemberSignoff(player.getTag());
					if (signoff.isActive() && !signoff.isReceivePings()) {
						isSignedOff = true;
						break;
					}
				}

				if (isSignedOff) {
					signedOffMembers.add(member);
				} else {
					regularMissingMembers.add(member);
					regularMissingUserIds.add(member.getId());
				}
			}

			// Build embed description
			StringBuilder description = new StringBuilder();
			description.append("**Rolle:** ").append(role.getAsMention()).append("\n");
			description.append("**Umfrage:** ").append(poll.getQuestion().getText()).append("\n");
			if (poll.isMultiAnswer()) {
				description.append("**Mehrfachauswahl:** Ja\n");
			}
			if (poll.isExpired() || poll.isFinalizedVotes()) {
				description.append("**Status:** Beendet\n");
			}
			description.append("**Nachricht:** [Link](").append(messagelink).append(")\n\n");

			List<MessagePoll.Answer> answers = poll.getAnswers();
			for (int i = 0; i < answers.size(); i++) {
				MessagePoll.Answer answer = answers.get(i);
				Set<String> answerVoterIds = voterIdsPerAnswer.get(i);
				List<Member> answerVoters = roleMembers.stream()
						.filter(member -> answerVoterIds.contains(member.getId()))
						.collect(Collectors.toList());

				String emojiPrefix = answer.getEmoji() != null ? answer.getEmoji().getFormatted() + " " : "";
				String answerText = answer.getText() != null ? answer.getText() : "";
				description.append("**").append(emojiPrefix).append(answerText)
						.append(" (").append(answerVoters.size()).append("):**\n");
				if (!answerVoters.isEmpty()) {
					appendMemberLines(description, answerVoters, false);
				} else {
					description.append("---\n");
				}
				description.append("\n");
			}

			if (missingMembers.isEmpty()) {
				description.append("Alle Mitglieder der Rolle haben bereits abgestimmt. ✅");
			} else {
				description.append("**Nicht abgestimmt (").append(regularMissingMembers.size())
						.append("):**\n");
				if (!regularMissingMembers.isEmpty()) {
					appendMemberLines(description, regularMissingMembers, true);
				} else {
					description.append("---\n");
				}
				description.append("\n");

				description.append("**Abgemeldete Mitglieder (").append(signedOffMembers.size())
						.append("):**\n");
				if (!signedOffMembers.isEmpty()) {
					appendMemberLines(description, signedOffMembers, true);
				} else {
					description.append("---\n");
				}
			}

			// Create buttons
			Button refreshButton = Button.secondary(buttonId, "​").withEmoji(Emoji.fromUnicode("🔁"));
			List<Button> buttons = new ArrayList<>();
			buttons.add(refreshButton);

			String messageContent = "";

			if (!regularMissingUserIds.isEmpty()) {
				Button pingButton = Button.primary("cpping", "Fehlende Mitglieder pingen");
				buttons.add(pingButton);
				messageContent = encodeUserIds(regularMissingUserIds);
			}

			// Add timestamp
			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			// Hard clamp as safety net below Discord's 4096-char description limit
			String desc = description.toString();
			if (desc.length() > 4096) {
				desc = desc.substring(0, 4093) + "…";
			}

			hook.editOriginal(messageContent)
					.setEmbeds(MessageUtil.buildEmbed(title, desc,
							MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
					.setActionRow(buttons).queue();
		});
	}

	/**
	 * Appends one member per line, stopping early with a summary line once the
	 * description approaches Discord's embed description limit.
	 */
	private void appendMemberLines(StringBuilder description, List<Member> members, boolean asMention) {
		for (int i = 0; i < members.size(); i++) {
			String line = (asMention ? members.get(i).getAsMention() : members.get(i).getEffectiveName()) + "\n";
			if (description.length() + line.length() > DESCRIPTION_SOFT_LIMIT) {
				description.append("… und ").append(members.size() - i).append(" weitere\n");
				return;
			}
			description.append(line);
		}
	}

	/**
	 * Encodes parameters into a compact Base64 string for use in button IDs.
	 * Stores 3 longs (24 bytes) for the role, channel and message IDs.
	 */
	private String encodeButtonId(String roleId, String channelId, String messageId) {
		ByteBuffer buffer = ByteBuffer.allocate(24);
		buffer.putLong(Long.parseLong(roleId));
		buffer.putLong(Long.parseLong(channelId));
		buffer.putLong(Long.parseLong(messageId));
		return "cp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
	}

	/**
	 * Decodes a Base64-encoded button ID back into parameters.
	 * Returns [roleId, channelId, messageId].
	 */
	private String[] decodeButtonId(String buttonId) {
		String encoded = buttonId.substring(3); // Remove "cp_" prefix
		byte[] data = Base64.getUrlDecoder().decode(encoded);
		ByteBuffer buffer = ByteBuffer.wrap(data);

		String roleId = String.valueOf(buffer.getLong());
		String channelId = String.valueOf(buffer.getLong());
		String messageId = String.valueOf(buffer.getLong());

		return new String[] { roleId, channelId, messageId };
	}

	/**
	 * Encodes user IDs into a compact Base64 string for storage in message content.
	 */
	private String encodeUserIds(List<String> userIds) {
		int bufferSize = userIds.size() * 8;
		ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
		for (String userId : userIds) {
			buffer.putLong(Long.parseLong(userId));
		}
		return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
	}

	/**
	 * Resolves which clan tag a Discord role belongs to by checking the guilds table.
	 * Checks member_role_id, leader_role_id, co_leader_role_id, and elder_role_id.
	 * Returns null if no clan is associated with this role.
	 */
	private String getClanTagForRole(String roleId) {
		for (String clantag : DBManager.getAllClans()) {
			Clan clan = new Clan(clantag);
			for (Clan.Role clanRole : Clan.Role.values()) {
				String clanRoleId = clan.getRoleID(clanRole);
				if (roleId.equals(clanRoleId)) {
					return clantag;
				}
			}
		}
		return null;
	}

	/**
	 * Extracts user IDs from Base64-encoded message content.
	 */
	private List<String> extractUserIdsFromMessage(String messageContent) {
		if (messageContent == null || messageContent.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			byte[] data = Base64.getUrlDecoder().decode(messageContent);
			ByteBuffer buffer = ByteBuffer.wrap(data);
			List<String> userIds = new ArrayList<>();
			while (buffer.hasRemaining()) {
				long userId = buffer.getLong();
				userIds.add(String.valueOf(userId));
			}
			return userIds;
		} catch (Exception e) {
			return new ArrayList<>();
		}
	}
}
