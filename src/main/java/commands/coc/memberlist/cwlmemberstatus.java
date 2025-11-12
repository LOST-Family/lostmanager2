package commands.coc.memberlist;

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
			OptionMapping clanAOption = event.getOption("origin_clan_1");
			OptionMapping clantagOption = event.getOption("cwl_clan_tag");
			OptionMapping clanBOption = event.getOption("origin_clan_2");

			if (roleOption == null || clanAOption == null || clantagOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Die Parameter 'team_role', 'origin_clan_1' und 'cwl_clan_tag' sind erforderlich.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			Role discordRole = roleOption.getAsRole();
			String clanATag = clanAOption.getAsString();
			String clanBTag = clanBOption != null ? clanBOption.getAsString() : null;
			String searchClantag = normalizeClanTag(clantagOption.getAsString());

			// Create button ID with encoded data
			String buttonId = encodeButtonId(discordRole.getId(), clanATag, clanBTag, searchClantag);

			performCWLMemberStatusCheck(event.getHook(), event.getGuild(), title, discordRole, clanATag, clanBTag,
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

			if (focused.equals("origin_clan_1") || focused.equals("origin_clan_2")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
				event.replyChoices(choices).queue(success -> {
				}, failure -> {
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
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Button zu nutzen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// Extract user mentions from embed description and send pings
			new Thread(() -> {
				try {
					String embedDescription = event.getMessage().getEmbeds().get(0).getDescription();
					List<String> userMentions = extractUserMentionsFromDescription(embedDescription);
					if (!userMentions.isEmpty()) {
						event.getInteraction().getMessageChannel().sendMessage(String.join(" ", userMentions))
								.queue();
					}
				} catch (Exception e) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler beim Extrahieren der Benutzer-Mentions.", MessageUtil.EmbedType.ERROR)).queue();
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
				if (params == null || params.length < 4) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String roleId = params[0];
				String clanATag = params[1];
				String clanBTag = params[2];
				String searchClantag = params[3];

				Role discordRole = guild.getRoleById(roleId);
				if (discordRole == null) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Rolle konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				performCWLMemberStatusCheck(event.getHook(), guild, title, discordRole, clanATag, clanBTag,
						searchClantag, id);

			} catch (Exception e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR)).queue();
			}

		}, "CWLMemberstatusRefresh-" + event.getUser().getId()).start();
	}

	private void performCWLMemberStatusCheck(net.dv8tion.jda.api.interactions.InteractionHook hook, Guild guild,
			String title, Role discordRole, String clanATag, String clanBTag, String searchClantag, String buttonId) {

		if (guild == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Dieser Befehl kann nur auf einem Server ausgeführt werden.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Load all members with the specified role
		guild.loadMembers().onSuccess(allMembers -> {
			List<Member> membersWithRole = allMembers.stream()
					.filter(member -> member.getRoles().contains(discordRole)).toList();

			int totalMembers = membersWithRole.size();
			List<String> inClan = new ArrayList<>();
			List<String> notInClan = new ArrayList<>();
			List<String> notInClanUserIds = new ArrayList<>();

			Clan clanA = new Clan(clanATag);
			Clan clanB = clanBTag != null && !clanBTag.isEmpty() ? new Clan(clanBTag) : null;

			// Check each member
			for (Member member : membersWithRole) {
				User user = new User(member.getId());
				ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

				boolean foundInClan = false;
				List<String> accountsNotInCWL = new ArrayList<>();

				// Check each linked account
				for (Player player : linkedAccounts) {
					// Check if player is in Clan A or Clan B
					Clan playerClanDB = player.getClanDB();
					if (playerClanDB == null)
						continue;

					String playerClanTag = playerClanDB.getTag();
					if (!playerClanTag.equals(clanATag) && (clanB == null || !playerClanTag.equals(clanBTag))) {
						continue;
					}

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
					
					// Account is in clan_a/clan_b but not in CWL clan
					accountsNotInCWL.add(player.getInfoStringDB());
				}

				if (!foundInClan && !accountsNotInCWL.isEmpty()) {
					// Add each account on a separate line
					for (String accountInfo : accountsNotInCWL) {
						notInClan.add(accountInfo + " (" + member.getAsMention() + ")");
					}
				}
			}

			// Build result description
			StringBuilder description = new StringBuilder();

			description.append("**Rolle:** ").append(discordRole.getName()).append("\n");
			description.append("**Clan A:** ").append(clanA.getInfoString()).append("\n");
			if (clanB != null) {
				description.append("**Clan B:** ").append(clanB.getInfoString()).append("\n");
			}
			description.append("**Gesuchter Clantag:** ").append(searchClantag).append("\n\n");

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
				for (String member : notInClan) {
					description.append(member).append("\n");
				}
				description.append("\n");
			} else {
				description.append("---\n");
			}

			// Create refresh button
			Button refreshButton = Button.secondary(buttonId, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

			// Create ping button (only if there are users not in clan)
			List<Button> buttons = new ArrayList<>();
			buttons.add(refreshButton);

			if (!notInClan.isEmpty()) {
				Button pingButton = Button.primary("cwlmsping", "Nicht im Clan pingen");
				buttons.add(pingButton);
			}

			// Add timestamp
			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);

			hook.editOriginal("")
					.setEmbeds(MessageUtil.buildEmbed(title, description.toString(), MessageUtil.EmbedType.INFO,
							"Zuletzt aktualisiert am " + formatiert))
					.setActionRow(buttons).queue();
		});
	}

	/**
	 * Normalize a clan tag to the standard format: uppercase, replace O with 0,
	 * add # if missing
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
	private String encodeButtonId(String roleId, String clanATag, String clanBTag, String searchClantag) {
		// Format: roleId|clanATag|clanBTag|searchClantag
		String data = roleId + "|" + clanATag + "|" + (clanBTag != null ? clanBTag : "") + "|" + searchClantag;
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
	 * Extracts user mentions from the "Nicht im Clan" section of the embed description
	 */
	private List<String> extractUserMentionsFromDescription(String description) {
		List<String> mentions = new ArrayList<>();
		
		// Find the "Nicht im Clan" section
		int startIndex = description.indexOf("**Nicht im Clan (");
		if (startIndex == -1) {
			return mentions;
		}
		
		// Find the end of the section (look for next "**" or end of string)
		String section = description.substring(startIndex);
		String[] lines = section.split("\n");
		
		// Skip the header line and process the rest
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i].trim();
			
			// Stop at next section (starts with **) or empty line after content
			if (line.startsWith("**") || (line.isEmpty() && i > 1)) {
				break;
			}
			
			// Extract user mention from line in format: "AccountInfo (<@userid>)"
			int mentionStart = line.indexOf("<@");
			int mentionEnd = line.indexOf(">", mentionStart);
			if (mentionStart != -1 && mentionEnd != -1) {
				String mention = line.substring(mentionStart, mentionEnd + 1);
				if (!mentions.contains(mention)) {
					mentions.add(mention);
				}
			}
		}
		
		return mentions;
	}
}
