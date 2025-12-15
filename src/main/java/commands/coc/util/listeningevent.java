package commands.coc.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import datawrapper.ActionValue;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import datawrapper.ListeningEvent;
import datawrapper.Player;
import datawrapper.User;
import dbutil.DBManager;
import dbutil.DBUtil;
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
			event.replyEmbeds(
					MessageUtil.buildEmbed(title, "Bitte wähle einen Unterbefehl aus.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		User userexecuted = new User(event.getUser().getId());
		boolean isAuthorized = false;
		for (String clantag : DBManager.getAllClans()) {
			Player.RoleType role = userexecuted.getClanRoles().get(clantag);
			if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER || role == Player.RoleType.COLEADER) {
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

		// Execute subcommand handling in a separate thread to avoid blocking with HTTP
		// requests
		new Thread(() -> {
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
				event.replyEmbeds(
						MessageUtil.buildEmbed(title, "Unbekannter Unterbefehl.", MessageUtil.EmbedType.ERROR)).queue();
			}
		}, "ListeningeventCommand-" + event.getUser().getId()).start();
	}

	private void handleAdd(SlashCommandInteractionEvent event, String title) {
		OptionMapping clanOption = event.getOption("clan");
		OptionMapping typeOption = event.getOption("type");
		OptionMapping durationOption = event.getOption("duration");
		OptionMapping actionTypeOption = event.getOption("actiontype");
		OptionMapping channelOption = event.getOption("channel");
		OptionMapping kickpointReasonOption = event.getOption("kickpoint_reason");

		if (clanOption == null || typeOption == null || durationOption == null || actionTypeOption == null
				|| channelOption == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Alle erforderlichen Parameter müssen angegeben werden!",
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
							"'start' kann nur bei Clan War Events verwendet werden!", MessageUtil.EmbedType.ERROR))
							.queue();
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
				&& !actionTypeStr.equals("filler") && !actionTypeStr.equals("raidfails")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Ungültiger Aktionstyp. Erlaubt: infomessage, kickpoint, cwdonator, custommessage, filler, raidfails",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}
		
		// Validate that cwdonator and filler are only used with CW type
		if ((actionTypeStr.equals("cwdonator") || actionTypeStr.equals("filler")) && !type.equals("cw")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"CW Donator und Filler können nur bei Clan War Events verwendet werden!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}
		
		// Validate that raidfails is only used with raid type
		if (actionTypeStr.equals("raidfails") && !type.equals("raid")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Raidfails kann nur bei Raid Events verwendet werden!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Check if kickpoint_reason is required
		if (actionTypeStr.equals("kickpoint") && kickpointReasonName == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Kickpoint-Grund ist erforderlich, wenn actiontype=kickpoint!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Determine if we need a modal based on event type and action type
		boolean needsModal = false;
		String modalId = "";
		Modal modal = null;

		// CS + (infomessage or kickpoint) => ask for threshold
		if (type.equals("cs") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_cs_threshold_" + clantag + "_" + duration + "_" + actionTypeStr + "_" + channelId
					+ "_" + (kickpointReasonName != null ? kickpointReasonName : "");

			TextInput thresholdInput = TextInput.create("threshold", "Threshold (Punkte)", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 4000").setRequired(true).setMinLength(1).setMaxLength(10).setValue("4000")
					.build();

			modal = Modal.create(modalId, "Clan Games Threshold eingeben").addActionRows(ActionRow.of(thresholdInput))
					.build();
		}
		// CW + (infomessage or kickpoint) => ask for required attacks
		else if (type.equals("cw") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_cw_attacks_" + clantag + "_" + duration + "_" + actionTypeStr + "_" + channelId
					+ "_" + (kickpointReasonName != null ? kickpointReasonName : "");

			TextInput attacksInput = TextInput.create("required_attacks", "Benötigte Angriffe (leer = API-Wert)", TextInputStyle.SHORT)
					.setPlaceholder("Leer lassen für automatisch oder 1/2 eingeben").setRequired(false).setMaxLength(1).build();

			modal = Modal.create(modalId, "Benötigte Angriffe eingeben").addActionRows(ActionRow.of(attacksInput))
					.build();
		}
		// RAID + raidfails => ask for district attack thresholds
		else if (type.equals("raid") && actionTypeStr.equals("raidfails")) {
			needsModal = true;
			modalId = "listeningevent_raidfails_" + clantag + "_" + duration + "_" + channelId
					+ "_" + (kickpointReasonName != null ? kickpointReasonName : "");

			TextInput capitalPeakInput = TextInput.create("capital_peak_max", "Maximale Angriffe auf Capital Peak", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 10").setRequired(true).setMinLength(1).setMaxLength(3).setValue("10").build();
			
			TextInput otherDistrictsInput = TextInput.create("other_districts_max", "Maximale Angriffe auf restliche Distrikte", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 6").setRequired(true).setMinLength(1).setMaxLength(3).setValue("6").build();
			
			// Only ask for penalize_both if kickpoint_reason is provided (kickpoint mode)
			if (kickpointReasonName != null) {
				TextInput penalizeBothInput = TextInput.create("penalize_both", "Beide Spieler bestrafen? 1->Ja; 2->Nein", TextInputStyle.SHORT)
						.setPlaceholder("1 oder 2").setRequired(true).setMinLength(1).setMaxLength(1).setValue("1").build();

				modal = Modal.create(modalId, "Raidfails Distrikt Einstellungen")
						.addActionRows(ActionRow.of(capitalPeakInput), ActionRow.of(otherDistrictsInput), ActionRow.of(penalizeBothInput))
						.build();
			} else {
				// Info mode - only ask for thresholds
				modal = Modal.create(modalId, "Raidfails Distrikt Einstellungen")
						.addActionRows(ActionRow.of(capitalPeakInput), ActionRow.of(otherDistrictsInput))
						.build();
			}
		}
		// custommessage => ask for custom message
		else if (actionTypeStr.equals("custommessage")) {
			needsModal = true;
			modalId = "listeningevent_custommessage_" + clantag + "_" + type + "_" + duration + "_" + channelId;

			TextInput messageInput = TextInput
					.create("custommessage", "Benutzerdefinierte Nachricht", TextInputStyle.PARAGRAPH)
					.setPlaceholder("Gib die Nachricht ein, die gesendet werden soll...").setRequired(true)
					.setMinLength(1).setMaxLength(2000).build();

			modal = Modal.create(modalId, "Benutzerdefinierte Nachricht eingeben")
					.addActionRows(ActionRow.of(messageInput)).build();
		}
		// cwdonator => asks for use_lists and exclude_leaders
		else if (actionTypeStr.equals("cwdonator")) {
			needsModal = true;
			modalId = "listeningevent_cwdonator_params_" + clantag + "_" + type + "_" + duration + "_" + actionTypeStr + "_" + channelId;

			TextInput useListsInput = TextInput.create("use_lists", "Listen-basierte Verteilung (1 oder 0)", TextInputStyle.SHORT)
					.setPlaceholder("1 = aktiviert, 0 = deaktiviert").setRequired(true).setMinLength(1).setMaxLength(1).setValue("0").build();

			TextInput excludeLeadersInput = TextInput.create("exclude_leaders", "Leader ausschließen (1 oder 0)", TextInputStyle.SHORT)
					.setPlaceholder("1 = aktiviert, 0 = deaktiviert").setRequired(true).setMinLength(1).setMaxLength(1).setValue("0").build();

			modal = Modal.create(modalId, "CW Donator Einstellungen")
					.addActionRows(ActionRow.of(useListsInput), ActionRow.of(excludeLeadersInput)).build();
		}

		if (needsModal) {
			event.replyModal(modal).queue();
			return;
		}

		// Otherwise process normally (no modal needed)
		event.deferReply().queue();
		processEventCreation(event.getHook(), title, clantag, type, duration, actionTypeStr, channelId,
				kickpointReasonName, null, null);
	}

	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String clantag, String type, long duration, String actionTypeStr, String channelId,
			String kickpointReasonName, String customMessage, Integer thresholdOrAttacks) {
		processEventCreation(hook, title, clantag, type, duration, actionTypeStr, channelId, kickpointReasonName, customMessage, thresholdOrAttacks, null);
	}

	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String clantag, String type, long duration, String actionTypeStr, String channelId,
			String kickpointReasonName, String customMessage, Integer thresholdOrAttacks, java.util.Map<String, Integer> raidDistrictThresholds) {

		// Build action values
		ArrayList<ActionValue> actionValues = new ArrayList<>();
		if (actionTypeStr.equals("cwdonator") || actionTypeStr.equals("filler")) {
			actionValues.add(new ActionValue(ActionValue.ACTIONVALUETYPE.FILLER));
		} else if (actionTypeStr.equals("kickpoint") && kickpointReasonName != null) {
			// Create KickpointReason with name and clan tag
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		} else if (actionTypeStr.equals("raidfails") && kickpointReasonName != null) {
			// raidfails with kickpoint reason - will add kickpoints
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		}

		// Add threshold or required attacks if provided
		if (thresholdOrAttacks != null) {
			ActionValue valueAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			valueAV.setValue((long) thresholdOrAttacks.intValue());
			actionValues.add(valueAV);
		}
		
		// Add raid district thresholds if provided
		if (raidDistrictThresholds != null && !raidDistrictThresholds.isEmpty()) {
			ActionValue capitalPeakAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			capitalPeakAV.setValue((long) raidDistrictThresholds.get("capital_peak_max").intValue());
			actionValues.add(capitalPeakAV);
			
			ActionValue otherDistrictsAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			otherDistrictsAV.setValue((long) raidDistrictThresholds.get("other_districts_max").intValue());
			actionValues.add(otherDistrictsAV);
			
			ActionValue penalizeBothAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			penalizeBothAV.setValue((long) raidDistrictThresholds.get("penalize_both").intValue());
			actionValues.add(penalizeBothAV);
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
				actionValuesJson = mapper
						.writeValueAsString(java.util.Collections.singletonMap("message", customMessage));
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// Insert into database and get generated ID
		Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
				clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		if (result == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Fehler beim Hinzufügen des Listening Events. Bitte versuche es erneut.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

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
			desc += "**Nachricht:** " + customMessage.substring(0, Math.min(100, customMessage.length()))
					+ (customMessage.length() > 100 ? "..." : "") + "\n";
		}
		if (thresholdOrAttacks != null) {
			if (type.equals("cs")) {
				desc += "**Threshold:** " + thresholdOrAttacks + " Punkte\n";
			} else if (type.equals("cw")) {
				desc += "**Benötigte Angriffe:** " + thresholdOrAttacks + "\n";
			}
		} else if (type.equals("cw") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			// For CW events without explicit required attacks, show that API value will be used
			// Note: Only infomessage and kickpoint action types check missed attacks; 
			// filler/cwdonator are for war start and don't check attacks
			desc += "**Benötigte Angriffe:** Automatisch (API-Wert wird verwendet)\n";
		}
		if (raidDistrictThresholds != null && !raidDistrictThresholds.isEmpty()) {
			desc += "**Maximale Angriffe auf Capital Peak:** " + raidDistrictThresholds.get("capital_peak_max") + "\n";
			desc += "**Maximale Angriffe auf restliche Distrikte:** " + raidDistrictThresholds.get("other_districts_max") + "\n";
			desc += "**Beide bestrafen bei Gleichstand:** " + (raidDistrictThresholds.get("penalize_both") == 1 ? "Ja" : "Nein") + "\n";
		}

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

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
				
				// Handle null listening type gracefully
				ListeningEvent.LISTENINGTYPE listeningType = le.getListeningType();
				if (listeningType == null) {
					desc.append("**Typ:** UNKNOWN (Fehler in Datenbank)\n");
				} else {
					desc.append("**Typ:** ").append(listeningType).append("\n");
				}
				
				desc.append("**Action:** ").append(le.getActionType()).append("\n");
				desc.append("**Channel:** <#").append(le.getChannelID()).append(">\n");

				// Display user-friendly message for events without valid timestamps
				Long timestamp = le.getTimestamp();
				if (timestamp == null || timestamp == Long.MAX_VALUE) {
					// Event doesn't have a valid timestamp - provide descriptive text
					String fireDescription = getFireDescriptionForEvent(le);
					desc.append("**Feuert in:** ").append(fireDescription).append("\n");
				} else {
					long minutesUntilFire = (timestamp - System.currentTimeMillis()) / 1000 / 60;

					// Check if the timestamp is in the past (negative minutes)
					if (minutesUntilFire < 0) {
						long minutesSinceFire = Math.abs(minutesUntilFire);

						// Format based on event type for better user experience
						if (listeningType == ListeningEvent.LISTENINGTYPE.CW) {
							// Check if war is actually ended
							try {
								Clan leclan = new Clan(le.getClanTag());
								if (leclan.isCWActive()) {
									// War is still active but event already fired
									desc.append("**Feuert in:** Event bereits gefeuert vor ").append(minutesSinceFire)
											.append(" Minuten\n");
								} else {
									// War has ended
									long hours = minutesSinceFire / 60;
									long days = hours / 24;

									if (days > 0) {
										desc.append("**Feuert in:** Letzter CW ist vor ").append(days)
												.append(" Tagen geendet und es wurde bisher keiner gestartet\n");
									} else if (hours > 0) {
										desc.append("**Feuert in:** Letzter CW ist vor ").append(hours)
												.append(" Stunden geendet und es wurde bisher keiner gestartet\n");
									} else {
										desc.append("**Feuert in:** Letzter CW ist vor ").append(minutesSinceFire)
												.append(" Minuten geendet und es wurde bisher keiner gestartet\n");
									}
								}
							} catch (Exception e) {
								// Fallback if we can't check war status
								desc.append("**Feuert in:** Event bereits gefeuert vor ").append(minutesSinceFire)
										.append(" Minuten\n");
							}
						} else {
							// For other event types, just show that it already fired
							desc.append("**Feuert in:** Event bereits gefeuert vor ").append(minutesSinceFire)
									.append(" Minuten\n");
						}
					} else {
						desc.append("**Feuert in:** ").append(minutesUntilFire).append(" Minuten\n");
					}
				}

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
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Die ID ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		long id = idOption.getAsLong();

		// Check if event exists
		String checkSql = "SELECT 1 FROM listening_events WHERE id = ?";
		Integer exists = DBUtil.getValueFromSQL(checkSql, Integer.class, id);

		if (exists == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Event mit dieser ID existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Delete event
		DBUtil.executeUpdate("DELETE FROM listening_events WHERE id = ?", id);

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
				"Event mit ID " + id + " wurde erfolgreich gelöscht.", MessageUtil.EmbedType.SUCCESS)).queue();

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

			processEventCreation(event.getHook(), title, clantag, type, duration, "custommessage", channelId, null,
					customMessage, null);
		} else if (modalId.startsWith("listeningevent_cs_threshold_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			// Parse:
			// listeningevent_cs_threshold_{clantag}_{duration}_{actiontype}_{channelid}_{kpreason}
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

			processEventCreation(event.getHook(), title, clantag, "cs", duration, actionTypeStr, channelId,
					kickpointReasonName, null, threshold);
		} else if (modalId.startsWith("listeningevent_cw_attacks_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			// Parse:
			// listeningevent_cw_attacks_{clantag}_{duration}_{actiontype}_{channelid}_{kpreason}
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

			String requiredAttacksStr = event.getValue("required_attacks").getAsString().trim();
			Integer requiredAttacks = null;
			
			// If user provided a value, validate and parse it
			if (!requiredAttacksStr.isEmpty()) {
				try {
					int attacks = Integer.parseInt(requiredAttacksStr);
					if (attacks < 1 || attacks > 2) {
						throw new NumberFormatException();
					}
					requiredAttacks = attacks;
				} catch (NumberFormatException e) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Ungültiger Wert für Angriffe: " + requiredAttacksStr + " (Erlaubt: 1 oder 2, oder leer lassen für automatisch)",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}
			}
			// If requiredAttacks is null, it means user left it blank and wants to use API value

			processEventCreation(event.getHook(), title, clantag, "cw", duration, actionTypeStr, channelId,
					kickpointReasonName, null, requiredAttacks);
		} else if (modalId.startsWith("listeningevent_raid_districts_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			// Parse:
			// listeningevent_raid_districts_{clantag}_{duration}_{actiontype}_{channelid}_{kpreason}
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

			// Parse the three threshold values
			String capitalPeakMaxStr = event.getValue("capital_peak_max").getAsString();
			String otherDistrictsMaxStr = event.getValue("other_districts_max").getAsString();
			String penalizeBothStr = event.getValue("penalize_both").getAsString();
			
			int capitalPeakMax, otherDistrictsMax, penalizeBoth;
			try {
				capitalPeakMax = Integer.parseInt(capitalPeakMaxStr);
				otherDistrictsMax = Integer.parseInt(otherDistrictsMaxStr);
				penalizeBoth = Integer.parseInt(penalizeBothStr);
				
				if (capitalPeakMax < 1 || otherDistrictsMax < 1) {
					throw new NumberFormatException("Thresholds must be at least 1");
				}
				
				if (penalizeBoth != 1 && penalizeBoth != 2) {
					throw new NumberFormatException("Penalize both must be 1 or 2");
				}
			} catch (NumberFormatException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Ungültige Werte eingegeben. Capital Peak und Distrikte müssen >= 1 sein, 'beide bestrafen' muss 1 oder 2 sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Create map with thresholds
			java.util.Map<String, Integer> raidDistrictThresholds = new java.util.HashMap<>();
			raidDistrictThresholds.put("capital_peak_max", capitalPeakMax);
			raidDistrictThresholds.put("other_districts_max", otherDistrictsMax);
			raidDistrictThresholds.put("penalize_both", penalizeBoth);

			processEventCreation(event.getHook(), title, clantag, "raid", duration, actionTypeStr, channelId,
					kickpointReasonName, null, null, raidDistrictThresholds);
		} else if (modalId.startsWith("listeningevent_raidfails_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			// Parse: listeningevent_raidfails_{clantag}_{duration}_{channelid}_{kpreason}
			String[] parts = modalId.split("_");
			if (parts.length < 5) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Fehler beim Verarbeiten der Modal-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = parts[2];
			long duration = Long.parseLong(parts[3]);
			String channelId = parts[4];
			String kickpointReasonName = parts.length > 5 && !parts[5].isEmpty() ? parts[5] : null;

			// Parse the threshold values (penalize_both is only present in kickpoint mode)
			String capitalPeakMaxStr = event.getValue("capital_peak_max").getAsString();
			String otherDistrictsMaxStr = event.getValue("other_districts_max").getAsString();
			
			int capitalPeakMax, otherDistrictsMax, penalizeBoth;
			try {
				capitalPeakMax = Integer.parseInt(capitalPeakMaxStr);
				otherDistrictsMax = Integer.parseInt(otherDistrictsMaxStr);
				
				// penalize_both is only present when kickpoint_reason is provided
				if (kickpointReasonName != null && event.getValue("penalize_both") != null) {
					String penalizeBothStr = event.getValue("penalize_both").getAsString();
					penalizeBoth = Integer.parseInt(penalizeBothStr);
					
					if (penalizeBoth != 1 && penalizeBoth != 2) {
						throw new NumberFormatException("Penalize both must be 1 or 2");
					}
				} else {
					// Info mode - default to 1 (but won't be used since no kickpoints)
					penalizeBoth = 1;
				}
				
				if (capitalPeakMax < 1 || otherDistrictsMax < 1) {
					throw new NumberFormatException("Thresholds must be at least 1");
				}
			} catch (NumberFormatException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Ungültige Werte eingegeben. Capital Peak und Distrikte müssen >= 1 sein, 'beide bestrafen' muss 1 oder 2 sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Create map with thresholds
			java.util.Map<String, Integer> raidDistrictThresholds = new java.util.HashMap<>();
			raidDistrictThresholds.put("capital_peak_max", capitalPeakMax);
			raidDistrictThresholds.put("other_districts_max", otherDistrictsMax);
			raidDistrictThresholds.put("penalize_both", penalizeBoth);

			processEventCreation(event.getHook(), title, clantag, "raid", duration, "raidfails", channelId,
					kickpointReasonName, null, null, raidDistrictThresholds);
		} else if (modalId.startsWith("listeningevent_cwdonator_params_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			// Parse: listeningevent_cwdonator_params_{clantag}_{type}_{duration}_{actiontype}_{channelid}
			String[] parts = modalId.split("_");
			if (parts.length < 8) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Fehler beim Verarbeiten der Modal-Daten.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = parts[3];
			String type = parts[4];
			long duration = Long.parseLong(parts[5]);
			String actionTypeStr = parts[6];
			String channelId = parts[7];

			String useListsStr = event.getValue("use_lists").getAsString();
			String excludeLeadersStr = event.getValue("exclude_leaders").getAsString();

			int useLists, excludeLeaders;
			try {
				useLists = Integer.parseInt(useListsStr);
				excludeLeaders = Integer.parseInt(excludeLeadersStr);

				if ((useLists != 0 && useLists != 1) || (excludeLeaders != 0 && excludeLeaders != 1)) {
					throw new NumberFormatException("Values must be 0 or 1");
				}
			} catch (NumberFormatException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Ungültige Werte eingegeben. Beide Felder müssen entweder 0 oder 1 sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Create map with parameters
			java.util.Map<String, Integer> cwdonatorParams = new java.util.HashMap<>();
			cwdonatorParams.put("use_lists", useLists);
			cwdonatorParams.put("exclude_leaders", excludeLeaders);

			processEventCreationWithCWDonatorParams(event.getHook(), title, clantag, type, duration, actionTypeStr, 
					channelId, cwdonatorParams);
		}
	}
	
	private void processEventCreationWithCWDonatorParams(net.dv8tion.jda.api.interactions.InteractionHook hook, 
			String title, String clantag, String type, long duration, String actionTypeStr, String channelId,
			java.util.Map<String, Integer> cwdonatorParams) {

		// Build action values with cwdonator parameters
		ArrayList<ActionValue> actionValues = new ArrayList<>();
		actionValues.add(new ActionValue(ActionValue.ACTIONVALUETYPE.FILLER));

		// Add use_lists parameter if enabled
		if (cwdonatorParams.get("use_lists") == 1) {
			ActionValue useListsAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			useListsAV.setValue(1L);
			actionValues.add(useListsAV);
		}

		// Add exclude_leaders parameter if enabled
		if (cwdonatorParams.get("exclude_leaders") == 1) {
			ActionValue excludeLeadersAV = new ActionValue(ActionValue.ACTIONVALUETYPE.VALUE);
			excludeLeadersAV.setValue(2L); // Use 2 to distinguish from use_lists
			actionValues.add(excludeLeadersAV);
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

		// Insert into database and get generated ID
		Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
				clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		if (result == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Fehler beim Hinzufügen des Listening Events. Bitte versuche es erneut.", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

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
		desc += "**Listen-basierte Verteilung:** " + (cwdonatorParams.get("use_lists") == 1 ? "Ja" : "Nein") + "\n";
		desc += "**Leader ausschließen:** " + (cwdonatorParams.get("exclude_leaders") == 1 ? "Ja" : "Nein") + "\n";

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

		// Restart all events to include the new one
		Bot.restartAllEvents();
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listeningevent"))
			return;

		new Thread(() -> {

			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			} else if (focused.equals("actiontype")) {
				// Get the event type to filter action types
				OptionMapping typeOption = event.getOption("type");
				String eventType = typeOption != null ? typeOption.getAsString() : "";
				
				// Provide autocomplete for action types based on listening type
				List<Command.Choice> choices = new ArrayList<>();
				
				// Common action types available for all listening types
				String[] commonActionTypes = { "infomessage", "kickpoint", "custommessage" };
				String[] commonDisplayNames = { "Info-Nachricht", "Kickpoint", "Benutzerdefinierte Nachricht" };
				
				// Raid-specific display names (different from common)
				String[] raidCommonDisplayNames = { "Fehlende Hits", "Kickpoints (Fehlende Hits)", "Benutzerdefinierte Nachricht" };
				
				// CW-specific action types
				String[] cwActionTypes = { "cwdonator", "filler" };
				String[] cwDisplayNames = { "CW Donator", "Filler" };
				
				// Raid-specific action types  
				String[] raidActionTypes = { "raidfails" };
				String[] raidDisplayNames = { "District-Analyse" };

				// Add common action types (use raid-specific names for raid type)
				String[] displayNames = "raid".equals(eventType) ? raidCommonDisplayNames : commonDisplayNames;
				for (int i = 0; i < commonActionTypes.length; i++) {
					if (commonActionTypes[i].toLowerCase().contains(input.toLowerCase())
							|| displayNames[i].toLowerCase().contains(input.toLowerCase())) {
						choices.add(new Command.Choice(displayNames[i], commonActionTypes[i]));
					}
				}
				
				// Add CW-specific action types only if type is "cw"
				if ("cw".equals(eventType)) {
					for (int i = 0; i < cwActionTypes.length; i++) {
						if (cwActionTypes[i].toLowerCase().contains(input.toLowerCase())
								|| cwDisplayNames[i].toLowerCase().contains(input.toLowerCase())) {
							choices.add(new Command.Choice(cwDisplayNames[i], cwActionTypes[i]));
						}
					}
				}
				
				// Add raid-specific action types only if type is "raid"
				if ("raid".equals(eventType)) {
					for (int i = 0; i < raidActionTypes.length; i++) {
						if (raidActionTypes[i].toLowerCase().contains(input.toLowerCase())
								|| raidDisplayNames[i].toLowerCase().contains(input.toLowerCase())) {
							choices.add(new Command.Choice(raidDisplayNames[i], raidActionTypes[i]));
						}
					}
				}
				
				// Limit to 25 choices
				if (choices.size() > 25) {
					choices = choices.subList(0, 25);
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

				// Check if the user's input is a valid duration format
				boolean isValidInput = false;
				if (!input.isEmpty()) {
					if (input.equalsIgnoreCase("start") || input.equalsIgnoreCase("cwstart")) {
						// "start" and "cwstart" are valid for CW events
						isValidInput = eventType.equals("cw");
					} else {
						// Try to validate as a regular duration
						try {
							parseDuration(input);
							isValidInput = true;
						} catch (IllegalArgumentException e) {
							// Input is not a valid duration, isValidInput remains false
						}
					}
				}

				// If the user's input is valid, add it as the first choice
				if (isValidInput) {
					choices.add(new Command.Choice("✓ " + input, input));
				}

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
					if (choice.getName().toLowerCase().contains(input.toLowerCase())
							|| choice.getAsString().toLowerCase().contains(input.toLowerCase())) {
						filtered.add(choice);
						if (filtered.size() >= 25)
							break;
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
		}, "ListeningeventAutocomplete-" + event.getUser().getId()).start();
	}

	/**
	 * Parses a duration string into milliseconds. Supports: 0, plain numbers (ms),
	 * h (hours), d (days), m (minutes), s (seconds) Examples: 0, 1h, 24h, 2d, 30m,
	 * 3600000
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

	/**
	 * Get a user-friendly description for when an event will fire when no valid
	 * timestamp is available
	 */
	private String getFireDescriptionForEvent(ListeningEvent le) {
		ListeningEvent.LISTENINGTYPE type = le.getListeningType();
		
		// Handle null type
		if (type == null) {
			return "Fehler: Unbekannter Event-Typ";
		}
		
		long duration = le.getDurationUntilEnd();

		// Check if this is a "start" trigger
		if (duration == -1) {
			switch (type) {
			case CW:
				return "Feuert, wenn neuer CW startet";
			default:
				return "Feuert bei Event-Start";
			}
		}

		// Otherwise, it's waiting for an active event
		switch (type) {
		case CW:
			return "Wartet auf aktiven CW";
		case RAID:
			return "Wartet auf aktives Raid Weekend";
		case CWLDAY:
			return "Wartet auf aktive CWL";
		case CS:
			return "Wartet auf aktive Clan Games";
		case FIXTIMEINTERVAL:
			return "Zeitbasiertes Event";
		case CWLEND:
			return "Wartet auf CWL Ende";
		default:
			return "Wartet auf Event";
		}
	}
}
