package commands.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.ActionValue;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import datawrapper.ListeningEvent;
import datawrapper.Player;
import datawrapper.User;
import lostmanager.Bot;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import util.MessageUtil;

public class listeningevent extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("listeningevent"))
			return;

		String title = "Listening Event";
		String subcommand = event.getSubcommandName();

		if (subcommand == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Bitte wähle einen Unterbefehl aus.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		User userexecuted = new User(event.getUser().getId());
		boolean isAuthorized = false;
		for (String clantag : DBManager.getAllClans()) {
			Player.RoleType role = userexecuted.getClanRoles().get(clantag);
			if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
					|| role == Player.RoleType.COLEADER) {
				isAuthorized = true;
				break;
			}
		}

		if (!isAuthorized) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		switch (subcommand) {
		case "add":
			handleAdd(event, title);
			break;
		case "list":
			handleList(event, title);
			break;
		case "remove":
			handleRemove(event, title);
			break;
		default:
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Unbekannter Unterbefehl.",
					MessageUtil.EmbedType.ERROR)).queue();
		}
	}

	private void handleAdd(SlashCommandInteractionEvent event, String title) {
		OptionMapping clanOption = event.getOption("clan");
		OptionMapping typeOption = event.getOption("type");
		OptionMapping durationOption = event.getOption("duration");
		OptionMapping actionTypeOption = event.getOption("actiontype");
		OptionMapping channelOption = event.getOption("channel");
		OptionMapping kickpointReasonOption = event.getOption("kickpoint_reason");

		if (clanOption == null || typeOption == null || durationOption == null || 
		    actionTypeOption == null || channelOption == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, 
				"Alle erforderlichen Parameter müssen angegeben werden!", 
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String clantag = clanOption.getAsString();
		String type = typeOption.getAsString();
		long duration = durationOption.getAsLong();
		String actionTypeStr = actionTypeOption.getAsString();
		String channelId = channelOption.getAsChannel().getId();
		String kickpointReasonName = kickpointReasonOption != null ? kickpointReasonOption.getAsString() : null;

		// Validate action type
		if (!actionTypeStr.equals("infomessage") && !actionTypeStr.equals("kickpoint") 
		    && !actionTypeStr.equals("cwdonator") && !actionTypeStr.equals("custommessage")
		    && !actionTypeStr.equals("filler")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
				"Ungültiger Aktionstyp. Erlaubt: infomessage, kickpoint, cwdonator, custommessage, filler",
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Check if kickpoint_reason is required
		if (actionTypeStr.equals("kickpoint") && kickpointReasonName == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
				"Kickpoint-Grund ist erforderlich, wenn actiontype=kickpoint!",
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// If custommessage, show modal
		if (actionTypeStr.equals("custommessage")) {
			TextInput messageInput = TextInput.create("custommessage", "Benutzerdefinierte Nachricht", TextInputStyle.PARAGRAPH)
					.setPlaceholder("Gib die Nachricht ein, die gesendet werden soll...")
					.setRequired(true)
					.setMinLength(1)
					.setMaxLength(2000)
					.build();
			
			Modal modal = Modal.create("listeningevent_custommessage_" + clantag + "_" + type + "_" + duration + "_" + channelId, 
					"Benutzerdefinierte Nachricht eingeben")
					.addActionRows(ActionRow.of(messageInput))
					.build();
			
			event.replyModal(modal).queue();
			return;
		}

		// Otherwise process normally
		event.deferReply().queue();
		processEventCreation(event.getHook(), title, clantag, type, duration, actionTypeStr, channelId, kickpointReasonName, null);
	}

	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title, 
			String clantag, String type, long duration, String actionTypeStr, String channelId, 
			String kickpointReasonName, String customMessage) {
		
		// Build action values
		ArrayList<ActionValue> actionValues = new ArrayList<>();
		if (actionTypeStr.equals("cwdonator") || actionTypeStr.equals("filler")) {
			actionValues.add(new ActionValue(ActionValue.ACTIONVALUETYPE.FILLER));
		} else if (actionTypeStr.equals("kickpoint") && kickpointReasonName != null) {
			// Create KickpointReason with name and clan tag
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		}

		// Convert action values to JSON
		String actionValuesJson = "[]";
		if (!actionValues.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionValuesJson = mapper.writeValueAsString(actionValues);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// For custom message, store it in actionvalues as a value type
		if (customMessage != null && !customMessage.isEmpty()) {
			// Store custom message text
			actionValuesJson = "[{\"value\":" + customMessage.length() + "}]";
			// We'll store the actual message separately in a custom_message column or in actionvalues
			// For simplicity, let's encode it in actionvalues as a JSON string
			try {
				ObjectMapper mapper = new ObjectMapper();
				ArrayList<ActionValue> customValues = new ArrayList<>();
				// We'll use the value field to store the message length as marker
				// and prepend the message to the JSON
				actionValuesJson = mapper.writeValueAsString(java.util.Collections.singletonMap("message", customMessage));
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// Insert into database
		DBUtil.executeUpdate(
			"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
			clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		String desc = "### Listening Event wurde hinzugefügt.\n";
		desc += "**Clan:** " + clantag + "\n";
		desc += "**Typ:** " + type + "\n";
		desc += "**Dauer:** " + duration + " ms\n";
		desc += "**Aktionstyp:** " + actionTypeStr + "\n";
		desc += "**Channel:** <#" + channelId + ">\n";
		if (kickpointReasonName != null) {
			desc += "**Kickpoint-Grund:** " + kickpointReasonName + "\n";
		}
		if (customMessage != null) {
			desc += "**Nachricht:** " + customMessage.substring(0, Math.min(100, customMessage.length())) + 
					(customMessage.length() > 100 ? "..." : "") + "\n";
		}

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
			.queue();

		// Restart all events to include the new one
		Bot.restartAllEvents();
	}

	private void handleList(SlashCommandInteractionEvent event, String title) {
		event.deferReply().queue();

		OptionMapping clanOption = event.getOption("clan");
		String clantag = clanOption != null ? clanOption.getAsString() : null;

		StringBuilder desc = new StringBuilder("## Listening Events\n\n");

		String sql;
		ArrayList<Long> ids;

		if (clantag != null) {
			sql = "SELECT id FROM listening_events WHERE clan_tag = ?";
			ids = DBUtil.getArrayListFromSQL(sql, Long.class, clantag);
		} else {
			sql = "SELECT id FROM listening_events";
			ids = DBUtil.getArrayListFromSQL(sql, Long.class);
		}

		if (ids.isEmpty()) {
			desc.append("Keine Events gefunden.");
		} else {
			for (Long id : ids) {
				ListeningEvent le = new ListeningEvent(id);
				Clan clan = new Clan(le.getClanTag());
				desc.append("**ID:** ").append(id).append("\n");
				desc.append("**Clan:** ").append(clan.getNameDB()).append(" (").append(le.getClanTag()).append(")\n");
				desc.append("**Typ:** ").append(le.getListeningType()).append("\n");
				desc.append("**Action:** ").append(le.getActionType()).append("\n");
				desc.append("**Channel:** <#").append(le.getChannelID()).append(">\n");
				desc.append("**Feuert in:** ")
						.append((le.getTimestamp() - System.currentTimeMillis()) / 1000 / 60).append(" Minuten\n");
				desc.append("\n");
			}
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc.toString(), MessageUtil.EmbedType.INFO))
				.queue();
	}

	private void handleRemove(SlashCommandInteractionEvent event, String title) {
		event.deferReply().queue();

		OptionMapping idOption = event.getOption("id");

		if (idOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Die ID ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		long id = idOption.getAsLong();

		// Check if event exists
		String checkSql = "SELECT 1 FROM listening_events WHERE id = ?";
		Long exists = DBUtil.getValueFromSQL(checkSql, Long.class, id);

		if (exists == null) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Event mit dieser ID existiert nicht.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Delete event
		DBUtil.executeUpdate("DELETE FROM listening_events WHERE id = ?", id);

		event.getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Event mit ID " + id + " wurde erfolgreich gelöscht.", MessageUtil.EmbedType.SUCCESS))
				.queue();

		// Restart all events to remove the deleted one from scheduler
		Bot.restartAllEvents();
	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		if (event.getModalId().startsWith("listeningevent_custommessage_")) {
			event.deferReply().queue();
			String title = "Listening Event";
			
			String[] parts = event.getModalId().split("_");
			if (parts.length < 6) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Verarbeiten der Modal-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			
			String clantag = parts[2];
			String type = parts[3];
			long duration = Long.parseLong(parts[4]);
			String channelId = parts[5];
			String customMessage = event.getValue("custommessage").getAsString();
			
			processEventCreation(event.getHook(), title, clantag, type, duration, "custommessage", channelId, null, customMessage);
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listeningevent"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
			event.replyChoices(choices).queue(success -> {
			}, error -> {
			});
		} else if (focused.equals("actiontype")) {
			// Provide autocomplete for action types
			List<Command.Choice> choices = new ArrayList<>();
			String[] actionTypes = {"infomessage", "kickpoint", "cwdonator", "filler", "custommessage"};
			String[] displayNames = {"Info-Nachricht", "Kickpoint", "CW Donator", "Filler", "Benutzerdefinierte Nachricht"};
			
			for (int i = 0; i < actionTypes.length; i++) {
				if (actionTypes[i].toLowerCase().contains(input.toLowerCase()) || 
				    displayNames[i].toLowerCase().contains(input.toLowerCase())) {
					choices.add(new Command.Choice(displayNames[i], actionTypes[i]));
					if (choices.size() >= 25) break;
				}
			}
			
			event.replyChoices(choices).queue(success -> {
			}, error -> {
			});
		} else if (focused.equals("kickpoint_reason")) {
			// Get the clan from the command to filter kickpoint reasons
			OptionMapping clanOption = event.getOption("clan");
			if (clanOption != null) {
				String clantag = clanOption.getAsString();
				List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input, clantag);
				event.replyChoices(choices).queue(success -> {
				}, error -> {
				});
			} else {
				event.replyChoices(new ArrayList<>()).queue();
			}
		}
	}
}
