package commands.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import util.Tuple;

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
		String durationStr = durationOption.getAsString();
		String actionTypeStr = actionTypeOption.getAsString();
		String channelId = channelOption.getAsChannel().getId();
		String kickpointReasonName = kickpointReasonOption != null ? kickpointReasonOption.getAsString() : null;

		// Parse duration
		long duration;
		try {
			if (durationStr.equalsIgnoreCase("start") || durationStr.equalsIgnoreCase("cwstart")) {
				// Special "start" value for CW start detection
				if (!type.equals("cw")) {
					event.replyEmbeds(MessageUtil.buildEmbed(title,
						"'start' kann nur bei Clan War Events verwendet werden!",
						MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
				duration = -1; // Special marker for start trigger
			} else {
				duration = parseDuration(durationStr);
			}
		} catch (IllegalArgumentException e) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
				"Ungültiges Dauer-Format: " + e.getMessage() + "\nBeispiele: 0, 1h, 2d, 24h, start",
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

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

		// Determine if we need a modal based on event type and action type
		boolean needsModal = false;
		String modalId = "";
		Modal modal = null;
		
		// CS + (infomessage or kickpoint) => ask for threshold
		if (type.equals("cs") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_cs_threshold_" + clantag + "_" + duration + "_" + actionTypeStr + "_" + channelId + "_" + (kickpointReasonName != null ? kickpointReasonName : "");
			
			TextInput thresholdInput = TextInput.create("threshold", "Threshold (Punkte)", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 4000")
					.setRequired(true)
					.setMinLength(1)
					.setMaxLength(10)
					.setValue("4000")
					.build();
			
			modal = Modal.create(modalId, "Clan Games Threshold eingeben")
					.addActionRows(ActionRow.of(thresholdInput))
					.build();
		}
		// CW + (infomessage or kickpoint) => ask for required attacks
		else if (type.equals("cw") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_cw_attacks_" + clantag + "_" + duration + "_" + actionTypeStr + "_" + channelId + "_" + (kickpointReasonName != null ? kickpointReasonName : "");
			
			TextInput attacksInput = TextInput.create("required_attacks", "Benötigte Angriffe", TextInputStyle.SHORT)
					.setPlaceholder("1 oder 2")
					.setRequired(true)
					.setMinLength(1)
					.setMaxLength(1)
					.setValue("2")
					.build();
			
			modal = Modal.create(modalId, "Benötigte Angriffe eingeben")
					.addActionRows(ActionRow.of(attacksInput))
					.build();
		}
		// custommessage => ask for custom message
		else if (actionTypeStr.equals("custommessage")) {
			needsModal = true;
			modalId = "listeningevent_custommessage_" + clantag + "_" + type + "_" + duration + "_" + channelId;
			
			TextInput messageInput = TextInput.create("custommessage", "Benutzerdefinierte Nachricht", TextInputStyle.PARAGRAPH)
					.setPlaceholder("Gib die Nachricht ein, die gesendet werden soll...")
					.setRequired(true)
					.setMinLength(1)
					.setMaxLength(2000)
					.build();
			
			modal = Modal.create(modalId, "Benutzerdefinierte Nachricht eingeben")
					.addActionRows(ActionRow.of(messageInput))
					.build();
		}
		
		if (needsModal) {
			event.replyModal(modal).queue();
			return;
		}

		// Otherwise process normally (no modal needed)
		event.deferReply().queue();
		processEventCreation(event.getHook(), title, clantag, type, duration, actionTypeStr, channelId, kickpointReasonName, null, null);
	}

	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title, 
			String clantag, String type, long duration, String actionTypeStr, String channelId, 
			String kickpointReasonName, String customMessage, Integer thresholdOrAttacks) {
		
		// Build action values
		ArrayList<ActionValue> actionValues = new ArrayList<>();
		if (actionTypeStr.equals("cwdonator") || actionTypeStr.equals("filler")) {
			actionValues.add(new ActionValue(ActionValue.ACTIONVALUETYPE.FILLER));
		} else if (actionTypeStr.equals("kickpoint") && kickpointReasonName != null) {
			// Create KickpointReason with name and clan tag
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		}
		
		// Add threshold or required attacks if provided
		if (thresholdOrAttacks != null) {
			ActionValue valueAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			valueAV.setValue((long) thresholdOrAttacks.intValue());
			actionValues.add(valueAV);
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
			try {
				ObjectMapper mapper = new ObjectMapper();
				actionValuesJson = mapper.writeValueAsString(java.util.Collections.singletonMap("message", customMessage));
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// Insert into database and get generated ID
		Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(
			"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
			clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		PreparedStatement stmt = result.getFirst();
		int rowsAffected = result.getSecond();

		Long id = null;

		if (rowsAffected > 0) {
			try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
				if (generatedKeys.next()) {
					id = generatedKeys.getLong(1);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		String desc = "### Listening Event wurde hinzugefügt.\n";
		if (id != null) {
			desc += "**ID:** " + id + "\n";
		}
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
		if (thresholdOrAttacks != null) {
			if (type.equals("cs")) {
				desc += "**Threshold:** " + thresholdOrAttacks + " Punkte\n";
			} else if (type.equals("cw")) {
				desc += "**Benötigte Angriffe:** " + thresholdOrAttacks + "\n";
			}
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
		String modalId = event.getModalId();
		
		if (modalId.startsWith("listeningevent_custommessage_")) {
			event.deferReply().queue();
			String title = "Listening Event";
			
			String[] parts = modalId.split("_");
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
			
			processEventCreation(event.getHook(), title, clantag, type, duration, "custommessage", channelId, null, customMessage, null);
		}
		else if (modalId.startsWith("listeningevent_cs_threshold_")) {
			event.deferReply().queue();
			String title = "Listening Event";
			
			// Parse: listeningevent_cs_threshold_{clantag}_{duration}_{actiontype}_{channelid}_{kpreason}
			String[] parts = modalId.split("_");
			if (parts.length < 7) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Verarbeiten der Modal-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			
			String clantag = parts[3];
			long duration = Long.parseLong(parts[4]);
			String actionTypeStr = parts[5];
			String channelId = parts[6];
			String kickpointReasonName = parts.length > 7 && !parts[7].isEmpty() ? parts[7] : null;
			
			String thresholdStr = event.getValue("threshold").getAsString();
			int threshold;
			try {
				threshold = Integer.parseInt(thresholdStr);
			} catch (NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Ungültiger Threshold-Wert: " + thresholdStr, MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			
			processEventCreation(event.getHook(), title, clantag, "cs", duration, actionTypeStr, channelId, kickpointReasonName, null, threshold);
		}
		else if (modalId.startsWith("listeningevent_cw_attacks_")) {
			event.deferReply().queue();
			String title = "Listening Event";
			
			// Parse: listeningevent_cw_attacks_{clantag}_{duration}_{actiontype}_{channelid}_{kpreason}
			String[] parts = modalId.split("_");
			if (parts.length < 7) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Verarbeiten der Modal-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			
			String clantag = parts[3];
			long duration = Long.parseLong(parts[4]);
			String actionTypeStr = parts[5];
			String channelId = parts[6];
			String kickpointReasonName = parts.length > 7 && !parts[7].isEmpty() ? parts[7] : null;
			
			String requiredAttacksStr = event.getValue("required_attacks").getAsString();
			int requiredAttacks;
			try {
				requiredAttacks = Integer.parseInt(requiredAttacksStr);
				if (requiredAttacks < 1 || requiredAttacks > 2) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Ungültiger Wert für Angriffe: " + requiredAttacksStr + " (Erlaubt: 1 oder 2)", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
			
			processEventCreation(event.getHook(), title, clantag, "cw", duration, actionTypeStr, channelId, kickpointReasonName, null, requiredAttacks);
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
			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
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
			
			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		} else if (focused.equals("duration")) {
			// Provide autocomplete for duration
			List<Command.Choice> choices = new ArrayList<>();
			
			// Get the event type to provide contextual suggestions
			OptionMapping typeOption = event.getOption("type");
			String eventType = typeOption != null ? typeOption.getAsString() : "";
			
			// Common suggestions
			choices.add(new Command.Choice("Sofort / Am Ende (0)", "0"));
			choices.add(new Command.Choice("1 Stunde vorher (1h)", "1h"));
			choices.add(new Command.Choice("2 Stunden vorher (2h)", "2h"));
			choices.add(new Command.Choice("3 Stunden vorher (3h)", "3h"));
			choices.add(new Command.Choice("6 Stunden vorher (6h)", "6h"));
			choices.add(new Command.Choice("12 Stunden vorher (12h)", "12h"));
			choices.add(new Command.Choice("24 Stunden vorher (24h/1d)", "24h"));
			choices.add(new Command.Choice("2 Tage vorher (2d)", "2d"));
			
			// Add CW-specific options
			if (eventType.equals("cw")) {
				choices.add(new Command.Choice("⭐ Bei CW Start (start)", "start"));
			}
			
			// Filter based on input
			List<Command.Choice> filtered = new ArrayList<>();
			for (Command.Choice choice : choices) {
				if (choice.getName().toLowerCase().contains(input.toLowerCase()) || 
				    choice.getAsString().toLowerCase().contains(input.toLowerCase())) {
					filtered.add(choice);
					if (filtered.size() >= 25) break;
				}
			}
			
			event.replyChoices(filtered).queue(_ -> {
			}, _ -> {
			});
		} else if (focused.equals("kickpoint_reason")) {
			// Get the clan from the command to filter kickpoint reasons
			OptionMapping clanOption = event.getOption("clan");
			if (clanOption != null) {
				String clantag = clanOption.getAsString();
				List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input, clantag);
				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			} else {
				event.replyChoices(new ArrayList<>()).queue();
			}
		}
	}
	
	/**
	 * Parses a duration string into milliseconds.
	 * Supports: 0, plain numbers (ms), h (hours), d (days), m (minutes), s (seconds)
	 * Examples: 0, 1h, 24h, 2d, 30m, 3600000
	 */
	private long parseDuration(String durationStr) throws IllegalArgumentException {
		durationStr = durationStr.trim().toLowerCase();
		
		// Handle 0 or empty
		if (durationStr.equals("0") || durationStr.isEmpty()) {
			return 0;
		}
		
		// Try to parse as plain number (milliseconds)
		try {
			return Long.parseLong(durationStr);
		} catch (NumberFormatException e) {
			// Not a plain number, try parsing with units
		}
		
		// Parse with units
		long multiplier = 1;
		String numPart = durationStr;
		
		if (durationStr.endsWith("ms")) {
			multiplier = 1;
			numPart = durationStr.substring(0, durationStr.length() - 2);
		} else if (durationStr.endsWith("s")) {
			multiplier = 1000;
			numPart = durationStr.substring(0, durationStr.length() - 1);
		} else if (durationStr.endsWith("m")) {
			multiplier = 60 * 1000;
			numPart = durationStr.substring(0, durationStr.length() - 1);
		} else if (durationStr.endsWith("h")) {
			multiplier = 60 * 60 * 1000;
			numPart = durationStr.substring(0, durationStr.length() - 1);
		} else if (durationStr.endsWith("d")) {
			multiplier = 24 * 60 * 60 * 1000;
			numPart = durationStr.substring(0, durationStr.length() - 1);
		} else {
			throw new IllegalArgumentException("Unbekannte Einheit. Verwende: ms, s, m, h, d");
		}
		
		try {
			long num = Long.parseLong(numPart.trim());
			return num * multiplier;
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Ungültige Zahl: " + numPart);
		}
	}
}
