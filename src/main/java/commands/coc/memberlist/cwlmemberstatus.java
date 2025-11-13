package commands.coc.memberlist;

import java.nio.ByteBuffer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import dbutil.DBManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class cwlmemberstatus extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("cwlmemberstatus"))
			return;

		String title = "CWL Memberstatus";
		event.deferReply().queue();

		new Thread(() -> {
			// Check permissions - must be at least co-leader
			User userExecuted = new User(event.getUser().getId());
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
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// Get parameters
			OptionMapping roleOption = event.getOption("team_role");
			OptionMapping clantagOption = event.getOption("cwl_clan_tag");

			if (roleOption == null || clantagOption == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Parameter 'team_role' und 'cwl_clan_tag' sind erforderlich.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Role discordRole = roleOption.getAsRole();
			String searchClantag = normalizeClanTag(clantagOption.getAsString());

			// Create button ID with encoded data
			String buttonId = encodeButtonId(discordRole.getId(), searchClantag);

			performCWLMemberStatusCheck(event.getHook(), event.getGuild(), title, discordRole,
					searchClantag, buttonId);

		}, "CWLMemberstatusCommand-" + event.getUser().getId()).start();
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("cwlmemberstatus"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("cwl_clan_tag")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
				List<Command.Choice> choices2 = DBManager.getSideClansAutocomplete(input);
				for (Command.Choice c : choices2) {
					if (choices.size() < 25) {
						choices.add(c);
					}
				}
				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
		}, "CWLMemberstatusAutocomplete-" + event.getUser().getId()).start();
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("cwlms_") && !id.equals("cwlmsping"))
			return;

		event.deferEdit().queue();

		String title = "CWL Memberstatus";

		// Handle ping button
		if (id.equals("cwlmsping")) {
			// Check permissions
			User userExecuted = new User(event.getUser().getId());
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
								String.join(" ", userIds.stream().map(uid -> "<@" + uid + ">").toArray(String[]::new)))
								.queue();
					}
				} catch (Exception e) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler beim Dekodieren der Benutzer-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				}
			}, "CWLMemberstatusPing-" + event.getUser().getId()).start();
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

			// Decode button ID to extract parameters
			try {
				String[] params = decodeButtonId(id, guild);
				if (params == null || params.length < 2) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String roleId = params[0];
				String searchClantag = params[1];

				Role discordRole = guild.getRoleById(roleId);
				if (discordRole == null) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Fehler: Rolle konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				performCWLMemberStatusCheck(event.getHook(), guild, title, discordRole,
						searchClantag, id);

			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "CWLMemberstatusRefresh-" + event.getUser().getId()).start();
	}

	private void performCWLMemberStatusCheck(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild,
			String title, Role discordRole, String searchClantag, String buttonId) {

		if (guild == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Dieser Befehl kann nur auf einem Server ausgeführt werden.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Load all members with the specified role
		guild.loadMembers().onSuccess(allMembers -> {
			List<Member> membersWithRole = allMembers.stream().filter(member -> member.getRoles().contains(discordRole))
					.toList();

			int totalMembers = membersWithRole.size();
			List<String> inClan = new ArrayList<>();
			List<String> notInClan = new ArrayList<>();
			List<String> notInClanUserIds = new ArrayList<>();
			List<User> notInClanUsers = new ArrayList<>();

			// Check each member
			for (Member member : membersWithRole) {
				User user = new User(member.getId());
				ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

				boolean foundInClan = false;

				// Check each linked account
				for (Player player : linkedAccounts) {
					// Now check if this account is in the specified clantag via API
					Clan playerClanAPI = player.getClanAPI();
					if (playerClanAPI != null) {
						String apiClanTag = normalizeClanTag(playerClanAPI.getTag());
						if (apiClanTag.equals(searchClantag)) {
							foundInClan = true;
							inClan.add(member.getAsMention() + " (" + player.getInfoStringDB() + ")");
							break;
						}
					}
				}

				if (!foundInClan) {
					notInClan.add(member.getAsMention());
					notInClanUserIds.add(member.getId());
					notInClanUsers.add(user);
				}
			}

			// Build result description
			StringBuilder description = new StringBuilder();

			Clan cwlclan = new Clan(searchClantag);

			description.append("**Rolle:** ").append(discordRole.getName()).append("\n");
			description.append("**CWL-Clan:** ").append(cwlclan.getInfoString()).append("\n\n");

			description.append("**Gesamtzahl der Mitglieder mit der Rolle:** ").append(totalMembers).append("\n\n");

			description.append("**Im Clan (").append(inClan.size()).append("):**\n");
			if (!inClan.isEmpty()) {
				for (String member : inClan) {
					description.append(member).append("\n");
				}
			} else {
				description.append("---\n");
			}
			description.append("\n");

			description.append("**Nicht im Clan (").append(notInClan.size()).append("):**\n");
			if (!notInClan.isEmpty()) {
				for (int i = 0; i < notInClan.size(); i++) {
					String memberMention = notInClan.get(i);
					User user = notInClanUsers.get(i);
					ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

					// Show all linked accounts for users not in clan
					for (Player player : linkedAccounts) {
						description.append(player.getInfoStringDB()).append(" (").append(memberMention).append(")\n");
					}
				}
			} else {
				description.append("---\n");
			}

			// Create refresh button
			Button refreshButton = Button.secondary(buttonId, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

			// Create ping button (only if there are users not in clan)
			List<Button> buttons = new ArrayList<>();
			buttons.add(refreshButton);

			String messageContent = "";

			if (!notInClanUserIds.isEmpty()) {
				Button pingButton = Button.primary("cwlmsping", "Nicht im Clan pingen");
				buttons.add(pingButton);
				// Encode user IDs and store in message content directly
				messageContent = encodeUserIds(notInClanUserIds);
			}

			// Add timestamp
			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			hook.editOriginal(messageContent).setEmbeds(MessageUtil.buildEmbed(title, description.toString(),
					MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert)).setActionRow(buttons).queue();
		});
	}

	/**
	 * Normalize a clan tag to the standard format: uppercase, replace O with 0, add
	 * # if missing
	 */
	private String normalizeClanTag(String tag) {
		if (tag == null || tag.isEmpty()) {
			return tag;
		}

		// Add # if not present
		if (!tag.startsWith("#")) {
			tag = "#" + tag;
		}

		// Replace O with 0 and convert to uppercase
		tag = tag.replace('O', '0').replace('o', '0').toUpperCase();

		return tag;
	}

	/**
	 * Encodes parameters into a compact Base64 string for use in button IDs.
	 */
	private String encodeButtonId(String roleId, String searchClantag) {
		// Format: roleId|searchClantag
		String data = roleId + "|" + searchClantag;
		return "cwlms_" + Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
	}

	/**
	 * Decodes a Base64-encoded button ID back into parameters.
	 */
	private String[] decodeButtonId(String buttonId, Guild guild) {
		// Remove "cwlms_" prefix
		String encoded = buttonId.substring(6);

		// Decode Base64
		String data = new String(Base64.getUrlDecoder().decode(encoded));

		// Split by |
		return data.split("\\|", -1);
	}

	/**
	 * Encodes user IDs into a compact Base64 string using the same logic as
	 * teamcheck.java
	 */
	private String encodeUserIds(List<String> userIds) {
		// Calculate buffer size: 8 bytes per user ID
		int bufferSize = userIds.size() * 8;
		ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

		// Write user IDs
		for (String userId : userIds) {
			buffer.putLong(Long.parseLong(userId));
		}

		// Base64 encode (URL-safe variant to avoid issues with special chars)
		return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
	}

	/**
	 * Extracts user IDs from message content that contains encoded user data
	 */
	private List<String> extractUserIdsFromMessage(String messageContent) {
		// Message content now contains the encoded data directly (no HTML comment)
		if (messageContent == null || messageContent.isEmpty()) {
			return new ArrayList<>();
		}

		try {
			// Decode Base64
			byte[] data = Base64.getUrlDecoder().decode(messageContent);
			ByteBuffer buffer = ByteBuffer.wrap(data);

			List<String> userIds = new ArrayList<>();

			// Read user IDs
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
