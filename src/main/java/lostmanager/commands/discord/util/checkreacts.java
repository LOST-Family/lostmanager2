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
import java.util.stream.Collectors;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class checkreacts extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("checkreacts"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Check-Reacts";

			OptionMapping roleOption = event.getOption("role");
			OptionMapping messagelinkOption = event.getOption("message_link");
			OptionMapping emojiOption = event.getOption("emoji");
			OptionMapping emoji2Option = event.getOption("emoji2");

			if (roleOption == null || messagelinkOption == null || emojiOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Die Parameter Rolle, Message-Link und Emoji sind pflicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Role role = roleOption.getAsRole();
			String messagelink = messagelinkOption.getAsString();
			String emoji = emojiOption.getAsString();
			String emoji2 = emoji2Option != null ? emoji2Option.getAsString() : null;

			String[] linkParts = messagelink.split("/");
			String channelId = linkParts[linkParts.length - 2];
			String messageId = linkParts[linkParts.length - 1];

			// Encode numeric IDs + emoji(s) to stay under 100 char button ID limit
			String buttonId = encodeButtonId(role.getId(), channelId, messageId, emoji, emoji2);

			performCheckreacts(event.getHook(), event.getGuild(), title, role, messagelink, emoji, emoji2, buttonId);

		}, "CheckreactsCommand-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("cr_") && !id.equals("crping"))
			return;

		event.deferEdit().queue();

		String title = "Check-Reacts";

		// Handle ping button
		if (id.equals("crping")) {
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
			}, "CheckreactsPing-" + event.getUser().getId()).start();
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
				if (params == null || params.length < 4) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String roleId = params[0];
				String channelId = params[1];
				String messageId = params[2];
				String emoji = params[3];
				String emoji2 = params.length > 4 ? params[4] : null;

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

				performCheckreacts(event.getHook(), guild, title, discordRole, messagelink, emoji, emoji2, id);

			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "CheckreactsRefresh-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	private void performCheckreacts(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild,
			String title, Role role, String messagelink, String emoji, String emoji2, String buttonId) {

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
			MessageReaction reaction = findReaction(message, emoji);

			if (reaction == null) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Reaktion mit dem Emoji " + emoji
						+ " auf der Nachricht " + messagelink + " gefunden.", MessageUtil.EmbedType.INFO)).queue();
				return;
			}

			// Use actual formatted string from reaction for embed display
			String displayEmoji = reaction.getEmoji().getFormatted();

			reaction.retrieveUsers().queue(users -> {
				Set<String> reactedUserIds = users.stream().map(net.dv8tion.jda.api.entities.User::getId).collect(Collectors.toSet());

				// Retrieve users for emoji2 if provided
				if (emoji2 != null) {
					MessageReaction reaction2 = findReaction(message, emoji2);
					if (reaction2 != null) {
						String displayEmoji2 = reaction2.getEmoji().getFormatted();
						reaction2.retrieveUsers().queue(users2 -> {
							Set<String> reactedEmoji2UserIds = users2.stream().map(net.dv8tion.jda.api.entities.User::getId).collect(Collectors.toSet());
							processMembers(hook, guild, title, role, messagelink, displayEmoji, displayEmoji2, buttonId, reactedUserIds, reactedEmoji2UserIds);
						});
					} else {
						processMembers(hook, guild, title, role, messagelink, displayEmoji, emoji2, buttonId, reactedUserIds, new HashSet<>());
					}
				} else {
					processMembers(hook, guild, title, role, messagelink, displayEmoji, null, buttonId, reactedUserIds, new HashSet<>());
				}
			});
		}, _ -> {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Nachricht mit dieser ID konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR)).queue();
		});
	}

	private MessageReaction findReaction(net.dv8tion.jda.api.entities.Message message, String emojiStr) {
		if (emojiStr == null)
			return null;
		return message.getReactions().stream()
				.filter(r -> {
					Emoji e = r.getEmoji();
					if (emojiStr.startsWith("id:")) {
						return e.getType() == Emoji.Type.CUSTOM &&
								String.valueOf(((CustomEmoji) e).getIdLong()).equals(emojiStr.substring(3));
					} else {
						return e.getFormatted().equals(emojiStr);
					}
				}).findFirst().orElse(null);
	}

	@SuppressWarnings("null")
	private void processMembers(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild, String title, Role role, 
			String messagelink, String emoji, String emoji2, String buttonId, Set<String> reactedUserIds, Set<String> reactedEmoji2UserIds) {
		
		// Resolve which clan this role belongs to (if any)
		String roleClanTag = getClanTagForRole(role.getId());

		guild.loadMembers().onSuccess(members -> {
			List<Member> missingMembers = members.stream()
					.filter(member -> member.getRoles().contains(role))
					.filter(member -> !reactedUserIds.contains(member.getId()))
					.collect(Collectors.toList());

			// Separate signed-off members
			List<Member> emoji2Reactors = new ArrayList<>();
			List<Member> signedOffMembers = new ArrayList<>();
			List<Member> regularMissingMembers = new ArrayList<>();
			List<String> regularMissingUserIds = new ArrayList<>();

			for (Member member : missingMembers) {
				String discordId = member.getId();
				
				// Priority 1: Emoji 2 reaction
				if (emoji2 != null && reactedEmoji2UserIds.contains(discordId)) {
					emoji2Reactors.add(member);
					continue;
				}

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
			description.append("**Emoji:** ").append(emoji).append("\n");
			if (emoji2 != null) {
				description.append("**Emoji 2:** ").append(emoji2).append("\n");
			}
			description.append("**Nachricht:** [Link](").append(messagelink).append(")\n\n");

			if (missingMembers.isEmpty()) {
				description.append("Alle Mitglieder der Rolle haben bereits reagiert. ✅");
			} else {
				description.append("**Fehlende Reaktionen (").append(regularMissingMembers.size())
						.append("):**\n");
				if (!regularMissingMembers.isEmpty()) {
					for (Member member : regularMissingMembers) {
						description.append(member.getAsMention()).append("\n");
					}
				} else {
					description.append("---\n");
				}
				description.append("\n");

				if (emoji2 != null) {
					description.append("**Reagiert mit ").append(emoji2).append(" (").append(emoji2Reactors.size())
							.append("):**\n");
					if (!emoji2Reactors.isEmpty()) {
						for (Member member : emoji2Reactors) {
							description.append(member.getEffectiveName()).append("\n");
						}
					} else {
						description.append("---\n");
					}
					description.append("\n");
				}

				description.append("**Abgemeldete Mitglieder (").append(signedOffMembers.size())
						.append("):**\n");
				if (!signedOffMembers.isEmpty()) {
					for (Member member : signedOffMembers) {
						description.append(member.getAsMention()).append("\n");
					}
				} else {
					description.append("---\n");
				}
			}

			// Create buttons
			Button refreshButton = Button.secondary(buttonId, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));
			List<Button> buttons = new ArrayList<>();
			buttons.add(refreshButton);

			String messageContent = "";

			if (!regularMissingUserIds.isEmpty()) {
				Button pingButton = Button.primary("crping", "Fehlende Mitglieder pingen");
				buttons.add(pingButton);
				messageContent = encodeUserIds(regularMissingUserIds);
			}

			// Add timestamp
			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			hook.editOriginal(messageContent)
					.setEmbeds(MessageUtil.buildEmbed(title, description.toString(),
							MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
					.setActionRow(buttons).queue();
		});
	}

	/**
	 * Encodes parameters into a compact Base64 string for use in button IDs.
	 * Stores 3 longs (24 bytes) for IDs + optimized emoji encoding.
	 */
	private String encodeButtonId(String roleId, String channelId, String messageId, String emoji, String emoji2) {
		byte[] e1Data = encodeEmojiData(emoji);
		byte[] e2Data = encodeEmojiData(emoji2);

		ByteBuffer buffer = ByteBuffer.allocate(24 + e1Data.length + e2Data.length);
		buffer.putLong(Long.parseLong(roleId));
		buffer.putLong(Long.parseLong(channelId));
		buffer.putLong(Long.parseLong(messageId));
		buffer.put(e1Data);
		buffer.put(e2Data);

		return "cr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
	}

	private byte[] encodeEmojiData(String emojiStr) {
		if (emojiStr == null)
			return new byte[] { 0 };
		try {
			Emoji e = Emoji.fromFormatted(emojiStr);
			if (e.getType() == Emoji.Type.CUSTOM && e instanceof CustomEmoji) {
				ByteBuffer b = ByteBuffer.allocate(9);
				b.put((byte) 1);
				b.putLong(((CustomEmoji) e).getIdLong());
				return b.array();
			} else if (e.getType() == Emoji.Type.UNICODE && e instanceof UnicodeEmoji) {
				byte[] bytes = ((UnicodeEmoji) e).getName().getBytes();
				ByteBuffer b = ByteBuffer.allocate(2 + bytes.length);
				b.put((byte) 2);
				b.put((byte) bytes.length);
				b.put(bytes);
				return b.array();
			} else {
				// Fallback for weird strings
				byte[] bytes = emojiStr.getBytes();
				ByteBuffer b = ByteBuffer.allocate(2 + bytes.length);
				b.put((byte) 2);
				b.put((byte) bytes.length);
				b.put(bytes);
				return b.array();
			}
		} catch (Exception e) {
			// Fallback for weird strings
			byte[] bytes = emojiStr.getBytes();
			ByteBuffer b = ByteBuffer.allocate(2 + bytes.length);
			b.put((byte) 2);
			b.put((byte) bytes.length);
			b.put(bytes);
			return b.array();
		}
	}

	/**
	 * Decodes a Base64-encoded button ID back into parameters.
	 * Returns [roleId, channelId, messageId, emoji, emoji2].
	 */
	private String[] decodeButtonId(String buttonId) {
		String encoded = buttonId.substring(3); // Remove "cr_" prefix
		byte[] data = Base64.getUrlDecoder().decode(encoded);
		ByteBuffer buffer = ByteBuffer.wrap(data);

		String roleId = String.valueOf(buffer.getLong());
		String channelId = String.valueOf(buffer.getLong());
		String messageId = String.valueOf(buffer.getLong());

		String emoji = decodeEmojiData(buffer);
		String emoji2 = decodeEmojiData(buffer);

		return new String[] { roleId, channelId, messageId, emoji, emoji2 };
	}

	private String decodeEmojiData(ByteBuffer buffer) {
		if (!buffer.hasRemaining())
			return null;
		byte type = buffer.get();
		if (type == 0)
			return null;
		if (type == 1) {
			return "id:" + buffer.getLong();
		} else {
			int len = buffer.get() & 0xFF;
			byte[] bytes = new byte[len];
			buffer.get(bytes);
			return new String(bytes);
		}
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
