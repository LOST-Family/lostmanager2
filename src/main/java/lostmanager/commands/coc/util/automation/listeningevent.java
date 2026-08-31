package lostmanager.commands.coc.util.automation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lostmanager.Bot;
import lostmanager.datawrapper.ActionValue;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.KickpointReason;
import lostmanager.datawrapper.ListeningEvent;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

public class listeningevent extends ListenerAdapter {

	/**
	 * Parameters of an event creation that is waiting for its modal to come back.
	 *
	 * Discord limits a modal id to 100 characters, so these values cannot be packed
	 * into the id itself - a kickpoint reason is named freely and uses up that
	 * budget on its own. The id carries nothing but a short token, and the values
	 * stay here until the modal is submitted. That also removes the need to parse
	 * the id back apart, which broke on any clan tag or reason containing the
	 * separator character.
	 */
	private static class PendingEventCreation {
		final String clantag;
		final String type;
		final long duration;
		final String actionType;
		final String channelId;
		final String kickpointReasonName;
		final long createdAt = System.currentTimeMillis();

		PendingEventCreation(String clantag, String type, long duration, String actionType, String channelId,
				String kickpointReasonName) {
			this.clantag = clantag;
			this.type = type;
			this.duration = duration;
			this.actionType = actionType;
			this.channelId = channelId;
			this.kickpointReasonName = kickpointReasonName;
		}
	}

	private static final java.util.Map<String, PendingEventCreation> PENDING_CREATIONS =
			new java.util.concurrent.ConcurrentHashMap<>();

	/** Discord closes an unsubmitted modal long before this. */
	private static final long PENDING_TTL_MS = 30 * 60 * 1000L;

	/** Token for a modal id: short, opaque and always the same length. */
	private static String newPendingToken() {
		return java.util.UUID.randomUUID().toString().replace("-", "");
	}

	/**
	 * Parks the creation parameters under the token carried by the modal id.
	 * Entries of modals that were opened but never submitted are pruned here.
	 */
	private static void storePending(String token, PendingEventCreation pending) {
		long now = System.currentTimeMillis();
		PENDING_CREATIONS.values().removeIf(p -> now - p.createdAt > PENDING_TTL_MS);
		PENDING_CREATIONS.put(token, pending);
	}

	/**
	 * Retrieves and consumes the parameters belonging to a submitted modal.
	 *
	 * @return the parameters, or null if the modal is older than the retention time
	 */
	private static PendingEventCreation takePending(String modalId, String prefix) {
		return PENDING_CREATIONS.remove(modalId.substring(prefix.length()));
	}

	@SuppressWarnings("null")
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
				case "add" -> handleAdd(event, title);
				case "list" -> handleList(event, title);
				case "remove" -> handleRemove(event, title);
				default -> event.replyEmbeds(
							MessageUtil.buildEmbed(title, "Unbekannter Unterbefehl.", MessageUtil.EmbedType.ERROR))
							.queue();
			}
		}, "ListeningeventCommand-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
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
		} catch (final IllegalArgumentException e) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Ungültiges Dauer-Format: " + e.getMessage() + "\nBeispiele: 0, 1h, 2d, 24h, start",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Validate action type
		if (!actionTypeStr.equals("infomessage") && !actionTypeStr.equals("kickpoint")
				&& !actionTypeStr.equals("cwdonator") && !actionTypeStr.equals("custommessage")
				&& !actionTypeStr.equals("filler") && !actionTypeStr.equals("raidfails")
				&& !actionTypeStr.equals("raidfails_kickpoint")
				&& !actionTypeStr.equals("starfails") && !actionTypeStr.equals("starfails_kickpoint")
				&& !actionTypeStr.equals("cwcount") && !actionTypeStr.equals("cwcount_kickpoint")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Ungültiger Aktionstyp. Erlaubt: infomessage, kickpoint, cwdonator, custommessage, filler, raidfails, raidfails_kickpoint, starfails, starfails_kickpoint, cwcount, cwcount_kickpoint",
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

		// Validate that raidfails and raidfails_kickpoint are only used with raid type
		if ((actionTypeStr.equals("raidfails") || actionTypeStr.equals("raidfails_kickpoint"))
				&& !type.equals("raid")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Raidfails kann nur bei Raid Events verwendet werden!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Raidfails analyzes final district data, so it must fire exactly at raid end
		if ((actionTypeStr.equals("raidfails") || actionTypeStr.equals("raidfails_kickpoint")) && duration != 0) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Raidfails-Events müssen mit Dauer 0 erstellt werden (Analyse läuft zum Raid-Ende)!",
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

		// Check if kickpoint_reason is required for raidfails_kickpoint
		if (actionTypeStr.equals("raidfails_kickpoint") && kickpointReasonName == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Kickpoint-Grund ist erforderlich, wenn actiontype=District-Analyse (Kickpoints)!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Validate that starfails / starfails_kickpoint are only used with cw or cwlday types
		if ((actionTypeStr.equals("starfails") || actionTypeStr.equals("starfails_kickpoint"))
				&& !type.equals("cw") && !type.equals("cwlday")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"starfails und starfails_kickpoint können nur bei CW oder CWL-Day Events verwendet werden!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// CW count is a season end check
		if ((actionTypeStr.equals("cwcount") || actionTypeStr.equals("cwcount_kickpoint"))
				&& !type.equals("seasonend")) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"CW-Anzahl kann nur bei Season Ende Events verwendet werden!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (actionTypeStr.equals("cwcount_kickpoint") && kickpointReasonName == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Kickpoint-Grund ist erforderlich, wenn actiontype=cwcount_kickpoint!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Check if kickpoint_reason is required for starfails_kickpoint
		if (actionTypeStr.equals("starfails_kickpoint") && kickpointReasonName == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Kickpoint-Grund ist erforderlich, wenn actiontype=starfails_kickpoint!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Determine if we need a modal based on event type and action type.
		// The modal id only carries a token - see PendingEventCreation for why.
		boolean needsModal = false;
		String modalId;
		Modal modal = null;
		String pendingToken = newPendingToken();
		PendingEventCreation pendingCreation = new PendingEventCreation(clantag, type, duration, actionTypeStr,
				channelId, kickpointReasonName);

		// CS + (infomessage or kickpoint) => ask for threshold
		if (type.equals("cs") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_cs_threshold_" + pendingToken;

			TextInput thresholdInput = TextInput.create("threshold", "Threshold (Punkte)", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 4000").setRequired(true).setMinLength(1).setMaxLength(10).setValue("4000")
					.build();

			modal = Modal.create(modalId, "Clan Games Threshold eingeben").addComponents(ActionRow.of(thresholdInput))
					.build();
		}
		// CW + (infomessage or kickpoint) => ask for required attacks
		else if (type.equals("cw") && (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_cw_attacks_" + pendingToken;

			TextInput attacksInput = TextInput.create("required_attacks", "Benötigte Angriffe", TextInputStyle.SHORT)
					.setPlaceholder("1 oder 2 eingeben").setRequired(true).setMaxLength(1).build();

			// Only kickpoint events can be affected by the perfect-war exemption
			if (actionTypeStr.equals("kickpoint")) {
				modal = Modal.create(modalId, "Benötigte Angriffe eingeben")
						.addComponents(ActionRow.of(attacksInput), ActionRow.of(buildPerfectWarInput())).build();
			} else {
				modal = Modal.create(modalId, "Benötigte Angriffe eingeben").addComponents(ActionRow.of(attacksInput))
						.build();
			}
		}
		// SEASONEND + (infomessage or kickpoint) => ask for the wins threshold
		else if (type.equals("seasonend")
				&& (actionTypeStr.equals("infomessage") || actionTypeStr.equals("kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_seasonwins_" + pendingToken;

			TextInput winsInput = TextInput
					.create("wins_threshold", "Minimum Wins (leer = Clan-Einstellung)", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 70").setRequired(false).setMaxLength(5).build();

			modal = Modal.create(modalId, "Season Wins Einstellungen").addComponents(ActionRow.of(winsInput)).build();
		}
		// SEASONEND + cwcount => ask for the minimum number of clan wars
		else if (actionTypeStr.equals("cwcount") || actionTypeStr.equals("cwcount_kickpoint")) {
			needsModal = true;
			modalId = "listeningevent_cwcount_" + pendingToken;

			TextInput minCwInput = TextInput
					.create("cw_min_count", "Minimum CWs pro Season", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 8 - gezählt wird die Teilnahme an der Aufstellung")
					.setRequired(true).setMinLength(1).setMaxLength(3).build();

			modal = Modal.create(modalId, "CW-Teilnahme konfigurieren").addComponents(ActionRow.of(minCwInput))
					.build();
		}
		// CWL day + kickpoint => ask whether a perfect war exempts everyone
		else if (type.equals("cwlday") && actionTypeStr.equals("kickpoint")) {
			needsModal = true;
			modalId = "listeningevent_cwldaykp_" + pendingToken;

			modal = Modal.create(modalId, "CWL Kickpoint Einstellungen")
					.addComponents(ActionRow.of(buildPerfectWarInput())).build();
		}
		// RAID + raidfails or raidfails_kickpoint => ask for district attack thresholds
		else if (type.equals("raid")
				&& (actionTypeStr.equals("raidfails") || actionTypeStr.equals("raidfails_kickpoint"))) {
			needsModal = true;
			modalId = "listeningevent_raidfails_" + pendingToken;

			TextInput capitalPeakInput = TextInput
					.create("capital_peak_max", "Maximale Angriffe auf Capital Peak", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 10").setRequired(true).setMinLength(1).setMaxLength(3).setValue("10").build();

			TextInput otherDistrictsInput = TextInput
					.create("other_districts_max", "Maximale Angriffe auf restliche Distrikte", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 6").setRequired(true).setMinLength(1).setMaxLength(3).setValue("6").build();

			// Only ask for penalize_both if kickpoint_reason is provided (kickpoint mode)
			if (kickpointReasonName != null) {
				TextInput penalizeBothInput = TextInput
						.create("penalize_both", "Beide Spieler bestrafen? 1->Ja; 2->Nein", TextInputStyle.SHORT)
						.setPlaceholder("1 oder 2").setRequired(true).setMinLength(1).setMaxLength(1).setValue("1")
						.build();

				TextInput forceKickpointsInput = TextInput
						.create("raid_force_kickpoints",
								"Kickpunkte trotz unbestätigter Daten?", TextInputStyle.SHORT)
						.setPlaceholder("1 = Ja, 2 = Nein (Standard)").setRequired(true).setMinLength(1)
						.setMaxLength(1).setValue("2").build();

				modal = Modal.create(modalId, "Raidfails Distrikt Einstellungen")
						.addComponents(ActionRow.of(capitalPeakInput), ActionRow.of(otherDistrictsInput),
								ActionRow.of(penalizeBothInput), ActionRow.of(forceKickpointsInput))
						.build();
			} else {
				// Info mode - only ask for thresholds
				modal = Modal.create(modalId, "Raidfails Distrikt Einstellungen")
						.addComponents(ActionRow.of(capitalPeakInput), ActionRow.of(otherDistrictsInput)).build();
			}
		}
		// custommessage => ask for custom message
		else if (actionTypeStr.equals("custommessage")) {
			needsModal = true;
			modalId = "listeningevent_custommessage_" + pendingToken;

			TextInput messageInput = TextInput
					.create("custommessage", "Benutzerdefinierte Nachricht", TextInputStyle.PARAGRAPH)
					.setPlaceholder("Gib die Nachricht ein, die gesendet werden soll...").setRequired(true)
					.setMinLength(1).setMaxLength(2000).build();

			modal = Modal.create(modalId, "Benutzerdefinierte Nachricht eingeben")
					.addComponents(ActionRow.of(messageInput)).build();
		}
		// cwdonator => asks for use_lists and exclude_leaders
		else if (actionTypeStr.equals("cwdonator")) {
			needsModal = true;
			modalId = "listeningevent_cwdonator_params_" + pendingToken;

			TextInput useListsInput = TextInput
					.create("use_lists", "Listen-basierte Verteilung (1 oder 0)", TextInputStyle.SHORT)
					.setPlaceholder("1 = aktiviert, 0 = deaktiviert").setRequired(true).setMinLength(1).setMaxLength(1)
					.setValue("0").build();

			TextInput excludeLeadersInput = TextInput
					.create("exclude_leaders", "Leader ausschließen (1 oder 0)", TextInputStyle.SHORT)
					.setPlaceholder("1 = aktiviert, 0 = deaktiviert").setRequired(true).setMinLength(1).setMaxLength(1)
					.setValue("0").build();

			modal = Modal.create(modalId, "CW Donator Einstellungen")
					.addComponents(ActionRow.of(useListsInput), ActionRow.of(excludeLeadersInput)).build();
		}
		// starfails / starfails_kickpoint => ask for target star count and punishment mode
		else if (actionTypeStr.equals("starfails") || actionTypeStr.equals("starfails_kickpoint")) {
			needsModal = true;
			modalId = "listeningevent_starfails_" + pendingToken;

			TextInput starsInput = TextInput.create("star_count",
					"Sterne-Anzahl (0, 1 oder 2)", TextInputStyle.SHORT)
					.setPlaceholder("Angriffe mit genau X Sternen werden gemeldet/bestraft")
					.setRequired(true).setMinLength(1).setMaxLength(1).build();

			// Free hits: how many bad attacks a player gets before being punished.
			// 0 keeps the behaviour of every event created before this option existed.
			TextInput freeHitsInput = TextInput
					.create("starfails_free_hits", "Freie Fehlversuche pro Spieler", TextInputStyle.SHORT)
					.setPlaceholder(type.equals("cwlday")
							? "0 = keine, 1 = der erste in der CWL ist frei"
							: "0 = keine, 1 = der erste im Krieg ist frei")
					.setRequired(true).setMinLength(1).setMaxLength(2).setValue("0").build();

			// CWL has exactly one attack per member, so the punishment mode (which only
			// distinguishes between multiple attacks of the same player) has no effect
			// there and is not asked for.
			if (type.equals("cwlday")) {
				modal = Modal.create(modalId, "Schlechte Angriffe konfigurieren")
						.addComponents(ActionRow.of(starsInput), ActionRow.of(freeHitsInput)).build();
			} else {
				TextInput modeInput = TextInput.create("punishment_mode", "Modus (1, 2 oder 3)",
						TextInputStyle.SHORT)
						.setPlaceholder("1=Einmal pro Spieler, 2=Pro Angriff, 3=Nur wenn alle schlecht")
						.setRequired(true).setMinLength(1).setMaxLength(1).setValue("1").build();

				modal = Modal.create(modalId, "Schlechte Angriffe konfigurieren")
						.addComponents(ActionRow.of(starsInput), ActionRow.of(modeInput),
								ActionRow.of(freeHitsInput))
						.build();
			}
		}

		if (needsModal) {
			// Only park the parameters once a modal is actually shown
			storePending(pendingToken, pendingCreation);
			event.replyModal(modal).queue();
			return;
		}

		// Otherwise process normally (no modal needed)
		event.deferReply().queue();
		processEventCreation(event.getHook(), title, clantag, type, duration, actionTypeStr, channelId,
				kickpointReasonName, null, null);
	}

	/**
	 * Shared input for the optional per-event override of the perfect-war
	 * exemption. Defaults to 2 (no), which is the behaviour of all events created
	 * before this option existed.
	 */
	private TextInput buildPerfectWarInput() {
		return TextInput.create("ignore_perfect_war", "Kickpunkte auch bei perfektem Krieg?",
				TextInputStyle.SHORT).setPlaceholder("1 = Ja, 2 = Nein (Standard)").setRequired(true).setMinLength(1)
				.setMaxLength(1).setValue("2").build();
	}

	/**
	 * Reads a "1 = yes / 2 = no" modal field into a named setting value.
	 *
	 * @return 1 or 2, or null when the field was not part of the modal
	 */
	@SuppressWarnings("null")
	private Long readYesNoField(ModalInteractionEvent event, String fieldId) {
		net.dv8tion.jda.api.interactions.modals.ModalMapping mapping = event.getValue(fieldId);
		if (mapping == null) {
			return null;
		}
		return "1".equals(mapping.getAsString().trim()) ? 1L : 2L;
	}

	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String clantag, String type, long duration, String actionTypeStr, String channelId,
			String kickpointReasonName, String customMessage, Integer thresholdOrAttacks) {
		processEventCreation(hook, title, clantag, type, duration, actionTypeStr, channelId, kickpointReasonName,
				customMessage, thresholdOrAttacks, null, null, null, null);
	}

	/**
	 * @return the name of the kickpoint reason stored on the event, or null if it
	 *         has none configured and therefore hands out no kickpoints
	 */
	private static String configuredKickpointReason(ListeningEvent le) {
		ArrayList<ActionValue> actionValues = le.getActionValues();
		if (actionValues == null) {
			return null;
		}
		for (final ActionValue av : actionValues) {
			if (av.getSaved() == ActionValue.kind.reason && av.getReason() != null) {
				return av.getReason().getName();
			}
		}
		return null;
	}

	/**
	 * Plain wording for the violation an event hands out kickpoints for. The reason
	 * is picked from the clan's free-form list, so nothing stops a raid event from
	 * being wired to the reason of a different raid violation - which is exactly
	 * how the district reason and the missing-attacks reason ended up swapped once.
	 * Spelling out what the event actually punishes makes that visible right away.
	 *
	 * @return the description, or null if the action type hands out no kickpoints
	 */
	private static String describePunishedViolation(String type, String actionTypeStr) {
		if (actionTypeStr.equals("raidfails") || actionTypeStr.equals("raidfails_kickpoint")) {
			return "zu viele Angriffe auf denselben Distrikt";
		}
		if (actionTypeStr.equals("starfails") || actionTypeStr.equals("starfails_kickpoint")) {
			return "Angriffe mit zu wenig Sternen";
		}
		if (actionTypeStr.equals("cwcount_kickpoint")) {
			return "zu wenige Clankriege in der Season";
		}
		if (actionTypeStr.equals("kickpoint")) {
			return switch (type) {
				case "raid" -> "fehlende oder nicht beendete Raid-Angriffe";
				case "cw" -> "nicht gemachte CW-Angriffe";
				case "cwlday" -> "nicht gemachte CWL-Angriffe";
				case "cs" -> "zu wenige Clan-Games-Punkte";
				case "seasonend" -> "zu wenige Season-Wins";
				default -> null;
			};
		}
		return null;
	}

	/**
	 * @param namedSettings optional settings stored by key instead of by position,
	 *                      so they can be read back without depending on the order
	 *                      of the positional value entries
	 */
	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String clantag, String type, long duration, String actionTypeStr, String channelId,
			String kickpointReasonName, String customMessage, Integer thresholdOrAttacks,
			Integer starCount, Integer punishmentMode,
			java.util.Map<String, Integer> raidDistrictThresholds,
			java.util.Map<String, Long> namedSettings) {

		// Convert raidfails_kickpoint to raidfails (it's a UI-only distinction)
		if (actionTypeStr.equals("raidfails_kickpoint")) {
			actionTypeStr = "raidfails";
		}

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
		} else if (actionTypeStr.equals("starfails_kickpoint") && kickpointReasonName != null) {
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		} else if (actionTypeStr.equals("cwcount_kickpoint") && kickpointReasonName != null) {
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		}

		// Add threshold or required attacks if provided
		if (thresholdOrAttacks != null) {
			ActionValue valueAV = new ActionValue(thresholdOrAttacks.longValue());
			actionValues.add(valueAV);
		}

		// Add star count and punishment mode for starfails events
		if (starCount != null) {
			actionValues.add(new ActionValue(starCount.longValue()));
		}
		if (punishmentMode != null) {
			actionValues.add(new ActionValue(punishmentMode.longValue()));
		}

		// Add raid district thresholds if provided
		if (raidDistrictThresholds != null && !raidDistrictThresholds.isEmpty()) {
			ActionValue capitalPeakAV = new ActionValue(
					raidDistrictThresholds.get("capital_peak_max").longValue());
			actionValues.add(capitalPeakAV);

			ActionValue otherDistrictsAV = new ActionValue(
					raidDistrictThresholds.get("other_districts_max").longValue());
			actionValues.add(otherDistrictsAV);

			ActionValue penalizeBothAV = new ActionValue(raidDistrictThresholds.get("penalize_both").longValue());
			actionValues.add(penalizeBothAV);
		}

		// Add named settings last - they are read by key, so their position in the
		// list does not matter and they never shift the positional values above
		if (namedSettings != null) {
			for (java.util.Map.Entry<String, Long> setting : namedSettings.entrySet()) {
				if (setting.getValue() != null) {
					actionValues.add(new ActionValue(setting.getKey(), setting.getValue()));
				}
			}
		}

		// Convert action values to JSON
		String actionValuesJson = "[]";
		if (!actionValues.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionValuesJson = mapper.writeValueAsString(actionValues);
			} catch (final JsonProcessingException e) {
			}
		}

		// For custom message, store it in actionvalues as a value type
		if (customMessage != null && !customMessage.isEmpty()) {
			// Store custom message text
			try {
				ObjectMapper mapper = new ObjectMapper();
				actionValuesJson = mapper
						.writeValueAsString(java.util.Collections.singletonMap("message", customMessage));
			} catch (final JsonProcessingException e) {
			}
		}

		// Insert into database and get generated ID
		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
				clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		if (result == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Hinzufügen des Listening Events. Bitte versuche es erneut.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Long id = result.getFirst();

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
			String punished = describePunishedViolation(type, actionTypeStr);
			if (punished != null) {
				desc += "**Vergeben für:** " + punished + "\n";
			}
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
		}
		if (raidDistrictThresholds != null && !raidDistrictThresholds.isEmpty()) {
			desc += "**Maximale Angriffe auf Capital Peak:** " + raidDistrictThresholds.get("capital_peak_max") + "\n";
			desc += "**Maximale Angriffe auf restliche Distrikte:** "
					+ raidDistrictThresholds.get("other_districts_max") + "\n";
			desc += "**Beide bestrafen bei Gleichstand:** "
					+ (raidDistrictThresholds.get("penalize_both") == 1 ? "Ja" : "Nein") + "\n";
		}
		if (starCount != null) {
			desc += "**Schlechte Angriffe bei:** " + starCount + " ★\n";
		}
		// The mode has no effect in CWL (one attack per member), so it is not shown
		if (punishmentMode != null && !type.equals("cwlday")) {
			String modeLabel = switch (punishmentMode) {
				case 1 -> "Einmal pro Spieler";
				case 2 -> "Pro schlechtem Angriff";
				case 3 -> "Nur wenn alle Angriffe schlecht";
				default -> String.valueOf(punishmentMode);
			};
			desc += "**Modus:** " + modeLabel + "\n";
		}
		if (namedSettings != null) {
			Long ignorePerfectWar = namedSettings.get(ListeningEvent.SETTING_IGNORE_PERFECT_WAR);
			if (ignorePerfectWar != null) {
				desc += "**Kickpunkte bei perfektem Krieg:** " + (ignorePerfectWar == 1L ? "Ja" : "Nein") + "\n";
			}
			Long forceKickpoints = namedSettings.get(ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS);
			if (forceKickpoints != null) {
				desc += "**Kickpunkte trotz unbestätigter Daten:** " + (forceKickpoints == 1L ? "Ja" : "Nein") + "\n";
			}
			Long freeHits = namedSettings.get(ListeningEvent.SETTING_STARFAILS_FREE_HITS);
			if (freeHits != null) {
				desc += "**Freie Fehlversuche:** " + (freeHits == 0L ? "keine"
						: freeHits + (type.equals("cwlday") ? " (pro CWL)" : " (pro Krieg)")) + "\n";
			}
		}
		if (namedSettings != null && namedSettings.get(ListeningEvent.SETTING_CW_MIN_COUNT) != null) {
			desc += "**Minimum CWs:** " + namedSettings.get(ListeningEvent.SETTING_CW_MIN_COUNT) + "\n";
		}
		if (type.equals("seasonend") && !actionTypeStr.startsWith("cwcount")) {
			Long winsThreshold = namedSettings != null
					? namedSettings.get(ListeningEvent.SETTING_WINS_THRESHOLD)
					: null;
			desc += "**Minimum Wins:** "
					+ (winsThreshold != null ? winsThreshold : "Clan-Einstellung (/clanconfig)") + "\n";
		}

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

		// Restart all events to include the new one
		Bot.restartAllEvents();
	}

	/** Leaves room for the last entry under the 4096 character embed description limit. */
	private static final int EMBED_DESCRIPTION_BUDGET = 3600;

	/**
	 * Where an event stands relative to its next firing. It is derived from the
	 * fire timestamp the list has always shown, so the status filter can never
	 * disagree with the "Feuert in" line printed next to it.
	 */
	private enum FireState {
		/** Has a fire time in the future. */
		SCHEDULED("Geplant"),
		/** Its fire time has passed. */
		FIRED("Bereits gefeuert"),
		/** Has no fire time because the clan event it listens for is not running. */
		WAITING("Wartet auf Event");

		private final String label;

		FireState(String label) {
			this.label = label;
		}

		String getLabel() {
			return label;
		}
	}

	/** The "Feuert in" line of an event together with the state it was derived from. */
	private record FireInfo(FireState state, String text) {
	}

	private void handleList(SlashCommandInteractionEvent event, String title) {
		event.deferReply().queue();

		OptionMapping clanOption = event.getOption("clan");
		OptionMapping typeOption = event.getOption("type");
		OptionMapping actionOption = event.getOption("actiontype");
		OptionMapping channelOption = event.getOption("channel");
		OptionMapping statusOption = event.getOption("status");

		String clantag = clanOption != null ? clanOption.getAsString() : null;
		String channelFilter = channelOption != null ? channelOption.getAsChannel().getId() : null;

		ListeningEvent.LISTENINGTYPE typeFilter = null;
		if (typeOption != null) {
			try {
				typeFilter = ListeningEvent.LISTENINGTYPE.valueOf(typeOption.getAsString().trim().toUpperCase());
			} catch (final IllegalArgumentException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Unbekannter Event-Typ: " + typeOption.getAsString(), MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		ListeningEvent.ACTIONTYPE actionFilter = null;
		if (actionOption != null) {
			try {
				actionFilter = ListeningEvent.ACTIONTYPE.valueOf(actionOption.getAsString().trim().toUpperCase());
			} catch (final IllegalArgumentException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Unbekannter Aktionstyp: " + actionOption.getAsString(), MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		FireState statusFilter = null;
		if (statusOption != null) {
			try {
				statusFilter = FireState.valueOf(statusOption.getAsString().trim().toUpperCase());
			} catch (final IllegalArgumentException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Unbekannter Status: " + statusOption.getAsString(), MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		// Clan and channel are stored verbatim, so the database narrows those two down.
		// Type and action are matched below on the parsed enums instead, which also
		// covers the legacy "cwl" spelling of CWLDAY, and the status is only known once
		// the fire time has been calculated.
		StringBuilder sql = new StringBuilder("SELECT id FROM listening_events");
		ArrayList<Object> params = new ArrayList<>();

		if (clantag != null) {
			sql.append(params.isEmpty() ? " WHERE " : " AND ").append("clan_tag = ?");
			params.add(clantag);
		}
		if (channelFilter != null) {
			sql.append(params.isEmpty() ? " WHERE " : " AND ").append("channel_id = ?");
			params.add(channelFilter);
		}
		sql.append(" ORDER BY clan_tag, id");

		ArrayList<Long> ids = DBUtil.getArrayListFromSQL(sql.toString(), Long.class, params.toArray());

		ArrayList<String> entries = new ArrayList<>();
		for (final Long id : ids) {
			ListeningEvent le = new ListeningEvent(id);

			ListeningEvent.LISTENINGTYPE listeningType = le.getListeningType();
			if (typeFilter != null && listeningType != typeFilter) {
				continue;
			}

			ListeningEvent.ACTIONTYPE actionType = le.getActionType();
			if (actionFilter != null && actionType != actionFilter) {
				continue;
			}

			FireInfo fire = describeFire(le, listeningType);
			if (statusFilter != null && fire.state() != statusFilter) {
				continue;
			}

			Clan clan = new Clan(le.getClanTag());
			StringBuilder entry = new StringBuilder();
			entry.append("**ID:** ").append(id).append("\n");
			entry.append("**Clan:** ").append(clan.getNameDB()).append(" (").append(le.getClanTag()).append(")\n");

			// Handle null listening type gracefully
			if (listeningType == null) {
				entry.append("**Typ:** UNKNOWN (Fehler in Datenbank)\n");
			} else {
				entry.append("**Typ:** ").append(listeningType).append("\n");
			}

			entry.append("**Dauer:** ").append(formatDuration(le.getDurationUntilEnd())).append("\n");
			entry.append("**Action:** ").append(actionType).append("\n");

			// Without the reason the list gives no way to spot an event wired to the
			// wrong one, which is only visible once kickpoints have been handed out
			String reasonName = configuredKickpointReason(le);
			if (reasonName != null) {
				entry.append("**Kickpoint-Grund:** ").append(reasonName).append("\n");
				String punished = listeningType != null && actionType != null
						? describePunishedViolation(listeningType.name().toLowerCase(),
								actionType.name().toLowerCase())
						: null;
				if (punished != null) {
					entry.append("**Vergeben für:** ").append(punished).append("\n");
				}
			}

			entry.append("**Channel:** <#").append(le.getChannelID()).append(">\n");
			entry.append("**Status:** ").append(fire.state().getLabel()).append("\n");
			entry.append("**Feuert in:** ").append(fire.text()).append("\n\n");
			entries.add(entry.toString());
		}

		List<String> activeFilters = new ArrayList<>();
		if (clantag != null) {
			activeFilters.add("Clan: " + new Clan(clantag).getNameDB() + " (" + clantag + ")");
		}
		if (typeFilter != null) {
			activeFilters.add("Typ: " + typeFilter);
		}
		if (actionFilter != null) {
			activeFilters.add("Action: " + actionFilter.name().toLowerCase());
		}
		if (channelFilter != null) {
			activeFilters.add("Channel: <#" + channelFilter + ">");
		}
		if (statusFilter != null) {
			activeFilters.add("Status: " + statusFilter.getLabel());
		}

		StringBuilder desc = new StringBuilder("## Listening Events\n\n");
		if (!activeFilters.isEmpty()) {
			desc.append("*Filter: ").append(String.join(" | ", activeFilters)).append("*\n\n");
		}

		if (entries.isEmpty()) {
			desc.append(activeFilters.isEmpty() ? "Keine Events gefunden."
					: "Keine Events gefunden, die zu den Filtern passen.");
		} else {
			int shown = 0;
			for (final String entry : entries) {
				// Discord rejects an embed whose description is over 4096 characters, so
				// stop before the whole reply is lost and say how many were left out.
				if (desc.length() + entry.length() > EMBED_DESCRIPTION_BUDGET) {
					desc.append("*... und ").append(entries.size() - shown)
							.append(" weitere. Grenze die Liste mit den Filter-Optionen ein.*");
					break;
				}
				shown++;
				desc.append(entry);
			}
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc.toString(), MessageUtil.EmbedType.INFO))
				.queue();
	}

	/**
	 * Builds the "Feuert in" line of an event together with the state it is in.
	 *
	 * A clan war event whose war has ended counts as waiting rather than as fired:
	 * the timestamp it missed belonged to a war that no longer exists, and the next
	 * war has not started yet.
	 */
	private FireInfo describeFire(ListeningEvent le, ListeningEvent.LISTENINGTYPE listeningType) {
		Long timestamp = le.getTimestamp();

		// Events without a valid timestamp are waiting for their clan event
		if (timestamp == null || timestamp == Long.MAX_VALUE) {
			return new FireInfo(FireState.WAITING, getFireDescriptionForEvent(le));
		}

		long minutesUntilFire = (timestamp - System.currentTimeMillis()) / 1000 / 60;
		if (minutesUntilFire >= 0) {
			return new FireInfo(FireState.SCHEDULED, minutesUntilFire + " Minuten");
		}

		long minutesSinceFire = Math.abs(minutesUntilFire);
		if (listeningType == ListeningEvent.LISTENINGTYPE.CW) {
			// Check if war is actually ended
			try {
				Clan leclan = new Clan(le.getClanTag());
				if (!leclan.isCWActive()) {
					long hours = minutesSinceFire / 60;
					long days = hours / 24;
					String ago = days > 0 ? days + " Tagen"
							: hours > 0 ? hours + " Stunden" : minutesSinceFire + " Minuten";
					return new FireInfo(FireState.WAITING,
							"Letzter CW ist vor " + ago + " geendet und es wurde bisher keiner gestartet");
				}
			} catch (final Exception e) {
				// Fallback if we can't check war status
			}
		}

		return new FireInfo(FireState.FIRED, "Event bereits gefeuert vor " + minutesSinceFire + " Minuten");
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

	@SuppressWarnings("null")
	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		String modalId = event.getModalId();

		if (modalId.startsWith("listeningevent_custommessage_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_custommessage_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			String type = pending.type;
			long duration = pending.duration;
			String channelId = pending.channelId;
			String customMessage = event.getValue("custommessage").getAsString();

			processEventCreation(event.getHook(), title, clantag, type, duration, "custommessage", channelId, null,
					customMessage, null);
		} else if (modalId.startsWith("listeningevent_cs_threshold_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_cs_threshold_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String actionTypeStr = pending.actionType;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

			String thresholdStr = event.getValue("threshold").getAsString();
			int threshold;
			try {
				threshold = Integer.parseInt(thresholdStr);
			} catch (final NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültiger Threshold-Wert: " + thresholdStr, MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			processEventCreation(event.getHook(), title, clantag, "cs", duration, actionTypeStr, channelId,
					kickpointReasonName, null, threshold);
		} else if (modalId.startsWith("listeningevent_cw_attacks_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_cw_attacks_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String actionTypeStr = pending.actionType;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

			String requiredAttacksStr = event.getValue("required_attacks").getAsString().trim();

			// Validate and parse the required attacks value (now required, not optional)
			if (requiredAttacksStr.isEmpty()) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Benötigte Angriffe müssen angegeben werden (1 oder 2)", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Integer requiredAttacks = null;
			try {
				int attacks = Integer.parseInt(requiredAttacksStr);
				if (attacks < 1 || attacks > 2) {
					throw new NumberFormatException();
				}
				requiredAttacks = attacks;
			} catch (final NumberFormatException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Ungültiger Wert für Angriffe: " + requiredAttacksStr + " (Erlaubt: 1 oder 2)",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			java.util.Map<String, Long> namedSettings = new java.util.HashMap<>();
			Long ignorePerfectWar = readYesNoField(event, "ignore_perfect_war");
			if (ignorePerfectWar != null) {
				namedSettings.put(ListeningEvent.SETTING_IGNORE_PERFECT_WAR, ignorePerfectWar);
			}

			processEventCreation(event.getHook(), title, clantag, "cw", duration, actionTypeStr, channelId,
					kickpointReasonName, null, requiredAttacks, null, null, null, namedSettings);
		} else if (modalId.startsWith("listeningevent_seasonwins_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_seasonwins_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String actionTypeStr = pending.actionType;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

			// Empty means "use the clan-wide min_season_wins setting"
			java.util.Map<String, Long> namedSettings = new java.util.HashMap<>();
			String winsStr = event.getValue("wins_threshold") != null
					? event.getValue("wins_threshold").getAsString().trim()
					: "";
			if (!winsStr.isEmpty()) {
				try {
					long wins = Long.parseLong(winsStr);
					if (wins < 1) {
						throw new NumberFormatException("threshold must be at least 1");
					}
					namedSettings.put(ListeningEvent.SETTING_WINS_THRESHOLD, wins);
				} catch (final NumberFormatException e) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Ungültiger Wert für Minimum Wins: " + winsStr, MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			processEventCreation(event.getHook(), title, clantag, "seasonend", duration, actionTypeStr, channelId,
					kickpointReasonName, null, null, null, null, null, namedSettings);
		} else if (modalId.startsWith("listeningevent_cwcount_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_cwcount_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String actionTypeStr = pending.actionType;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

			java.util.Map<String, Long> namedSettings = new java.util.HashMap<>();
			try {
				long minCount = Long.parseLong(event.getValue("cw_min_count").getAsString().trim());
				if (minCount < 1) {
					throw new NumberFormatException("minimum must be at least 1");
				}
				namedSettings.put(ListeningEvent.SETTING_CW_MIN_COUNT, minCount);
			} catch (final NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültiger Wert für Minimum CWs.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			processEventCreation(event.getHook(), title, clantag, "seasonend", duration, actionTypeStr, channelId,
					kickpointReasonName, null, null, null, null, null, namedSettings);
		} else if (modalId.startsWith("listeningevent_cwldaykp_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_cwldaykp_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

			java.util.Map<String, Long> namedSettings = new java.util.HashMap<>();
			Long ignorePerfectWar = readYesNoField(event, "ignore_perfect_war");
			if (ignorePerfectWar != null) {
				namedSettings.put(ListeningEvent.SETTING_IGNORE_PERFECT_WAR, ignorePerfectWar);
			}

			processEventCreation(event.getHook(), title, clantag, "cwlday", duration, "kickpoint", channelId,
					kickpointReasonName, null, null, null, null, null, namedSettings);
		} else if (modalId.startsWith("listeningevent_raidfails_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_raidfails_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

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
			} catch (final NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültige Werte eingegeben. Capital Peak und Distrikte müssen >= 1 sein, 'beide bestrafen' muss 1 oder 2 sein.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// Create map with thresholds
			java.util.Map<String, Integer> raidDistrictThresholds = new java.util.HashMap<>();
			raidDistrictThresholds.put("capital_peak_max", capitalPeakMax);
			raidDistrictThresholds.put("other_districts_max", otherDistrictsMax);
			raidDistrictThresholds.put("penalize_both", penalizeBoth);

			java.util.Map<String, Long> namedSettings = new java.util.HashMap<>();
			Long forceKickpoints = readYesNoField(event, "raid_force_kickpoints");
			if (forceKickpoints != null) {
				namedSettings.put(ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS, forceKickpoints);
			}

			processEventCreation(event.getHook(), title, clantag, "raid", duration, "raidfails", channelId,
					kickpointReasonName, null, null, null, null, raidDistrictThresholds, namedSettings);
		} else if (modalId.startsWith("listeningevent_cwdonator_params_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_cwdonator_params_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			String type = pending.type;
			long duration = pending.duration;
			String actionTypeStr = pending.actionType;
			String channelId = pending.channelId;

			String useListsStr = event.getValue("use_lists").getAsString();
			String excludeLeadersStr = event.getValue("exclude_leaders").getAsString();

			int useLists, excludeLeaders;
			try {
				useLists = Integer.parseInt(useListsStr);
				excludeLeaders = Integer.parseInt(excludeLeadersStr);

				if ((useLists != 0 && useLists != 1) || (excludeLeaders != 0 && excludeLeaders != 1)) {
					throw new NumberFormatException("Values must be 0 or 1");
				}
			} catch (final NumberFormatException e) {
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
		} else if (modalId.startsWith("listeningevent_starfails_")) {
			event.deferReply().queue();
			String title = "Listening Event";

			PendingEventCreation pending = takePending(modalId, "listeningevent_starfails_");
			if (pending == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = pending.clantag;
			long duration = pending.duration;
			String type = pending.type;
			String actionTypeStr = pending.actionType;
			String channelId = pending.channelId;
			String kickpointReasonName = pending.kickpointReasonName;

			String starCountStr = event.getValue("star_count").getAsString().trim();
			// Not present for CWL - the mode has no effect with a single attack per member
			String modeStr = event.getValue("punishment_mode") != null
					? event.getValue("punishment_mode").getAsString().trim()
					: "1";

			String freeHitsStr = event.getValue("starfails_free_hits") != null
					? event.getValue("starfails_free_hits").getAsString().trim()
					: "0";

			int starCount, punishmentMode, freeHits;
			try {
				starCount = Integer.parseInt(starCountStr);
				if (starCount < 0 || starCount > 2) throw new NumberFormatException("star_count must be 0-2");
				punishmentMode = Integer.parseInt(modeStr);
				if (punishmentMode < 1 || punishmentMode > 3) throw new NumberFormatException("mode must be 1-3");
				freeHits = freeHitsStr.isEmpty() ? 0 : Integer.parseInt(freeHitsStr);
				if (freeHits < 0 || freeHits > 20) throw new NumberFormatException("free hits must be 0-20");
			} catch (NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültige Eingabe: Sterne-Anzahl muss 0-2 sein, Modus 1-3, freie Fehlversuche 0-20.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			java.util.Map<String, Long> namedSettings = new java.util.HashMap<>();
			namedSettings.put(ListeningEvent.SETTING_STARFAILS_FREE_HITS, (long) freeHits);

			processEventCreation(event.getHook(), title, clantag, type, duration, actionTypeStr, channelId,
					kickpointReasonName, null, null, starCount, punishmentMode, null, namedSettings);
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
			ActionValue useListsAV = new ActionValue(1L);
			actionValues.add(useListsAV);
		}

		// Add exclude_leaders parameter if enabled
		if (cwdonatorParams.get("exclude_leaders") == 1) {
			ActionValue excludeLeadersAV = new ActionValue(2L);
			actionValues.add(excludeLeadersAV);
		}

		// Convert action values to JSON
		String actionValuesJson = "[]";
		if (!actionValues.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionValuesJson = mapper.writeValueAsString(actionValues);
			} catch (final JsonProcessingException e) {
			}
		}

		// Insert into database and get generated ID
		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
				clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		if (result == null) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Hinzufügen des Listening Events. Bitte versuche es erneut.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Long id = result.getFirst();

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

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listeningevent"))
			return;

		new Thread(() -> {

			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

                    switch (focused) {
                        case "clan" ->                             {
                                List<Command.Choice> choices = DBManager.getClansAutocompleteWithSideclans(input);
                                event.replyChoices(choices).queue(_ -> {
                                }, _ -> {
                                });
                            }
                        case "actiontype" ->                             {
                                // The list filter only offers action types that are actually
                                // configured somewhere, so a picked value never comes back empty.
                                if ("list".equals(event.getSubcommandName())) {
                                    event.replyChoices(storedActionTypeChoices(input)).queue(_ -> {
                                    }, _ -> {
                                    });
                                    return;
                                }
                                // Get the event type to filter action types
                                OptionMapping typeOption = event.getOption("type");
                                String eventType = typeOption != null ? typeOption.getAsString() : "";
                                // Provide autocomplete for action types based on listening type
                                List<Command.Choice> choices = new ArrayList<>();
                                // Common action types available for all listening types
                                String[] commonActionTypes = { "infomessage", "kickpoint", "custommessage" };
                                String[] commonDisplayNames = { "Info-Nachricht", "Kickpoint", "Benutzerdefinierte Nachricht" };
                                // Raid-specific display names (different from common)
                                String[] raidCommonDisplayNames = { "Hits (Info)", "Hits (Kickpoints)",
                                    "Benutzerdefinierte Nachricht" };
                                // CW-specific action types
                                String[] cwActionTypes = { "cwdonator", "filler" };
                                String[] cwDisplayNames = { "CW Donator", "Filler" };
                                // Raid-specific action types
                                String[] raidActionTypes = { "raidfails", "raidfails_kickpoint" };
                                String[] raidDisplayNames = { "Districts (Info)", "Districts (Kickpoints)" };
                                // War-specific action types (CW and CWL day both have attacks with stars)
                                String[] warActionTypes = { "starfails", "starfails_kickpoint" };
                                String[] warDisplayNames = { "Schlechte Angriffe (Info)",
                                    "Schlechte Angriffe (Kickpoints)" };
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
                                // Season end: the action type picks which check runs
                                if ("seasonend".equals(eventType)) {
                                    String[] seasonActionTypes = { "infomessage", "kickpoint", "cwcount",
                                        "cwcount_kickpoint" };
                                    String[] seasonDisplayNames = { "Season Wins (Info)", "Season Wins (Kickpoints)",
                                        "CW-Anzahl (Info)", "CW-Anzahl (Kickpoints)" };
                                    List<Command.Choice> seasonChoices = new ArrayList<>();
                                    for (int i = 0; i < seasonActionTypes.length; i++) {
                                        if (seasonActionTypes[i].toLowerCase().contains(input.toLowerCase())
                                                || seasonDisplayNames[i].toLowerCase().contains(input.toLowerCase())) {
                                            seasonChoices.add(new Command.Choice(seasonDisplayNames[i],
                                                    seasonActionTypes[i]));
                                        }
                                    }
                                    event.replyChoices(seasonChoices).queue(_ -> {
                                    }, _ -> {
                                    });
                                    return;
                                }
                                // Add war-specific action types for "cw" and "cwlday"
                                if ("cw".equals(eventType) || "cwlday".equals(eventType)) {
                                    for (int i = 0; i < warActionTypes.length; i++) {
                                        if (warActionTypes[i].toLowerCase().contains(input.toLowerCase())
                                                || warDisplayNames[i].toLowerCase().contains(input.toLowerCase())) {
                                            choices.add(new Command.Choice(warDisplayNames[i], warActionTypes[i]));
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
                            }
                        case "duration" ->                             {
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
                                        } catch (final IllegalArgumentException e) {
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
                            }
                        case "kickpoint_reason" -> {
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
                        default -> {
                        }
                    }
		}, "ListeningeventAutocomplete-" + event.getUser().getId()).start();
	}

	/**
	 * Action types the list filter can be set to: the ones that are actually stored
	 * on an event, labelled the way they were labelled when it was created.
	 */
	private static List<Command.Choice> storedActionTypeChoices(String input) {
		ArrayList<String> stored = DBUtil.getArrayListFromSQL(
				"SELECT DISTINCT actiontype FROM listening_events ORDER BY actiontype", String.class);

		List<Command.Choice> choices = new ArrayList<>();
		for (final String actionType : stored) {
			if (actionType == null || choices.size() >= 25) {
				continue;
			}
			String label = actionTypeDisplayName(actionType);
			if (actionType.toLowerCase().contains(input.toLowerCase())
					|| label.toLowerCase().contains(input.toLowerCase())) {
				choices.add(new Command.Choice(label, actionType));
			}
		}
		return choices;
	}

	/** German label of a stored action type, or the raw value if it is unknown. */
	private static String actionTypeDisplayName(String actionType) {
            return switch (actionType.toLowerCase()) {
                case "infomessage" -> "Info-Nachricht";
                case "custommessage" -> "Benutzerdefinierte Nachricht";
                case "kickpoint" -> "Kickpoint";
                case "cwdonator" -> "CW Donator";
                case "filler" -> "Filler";
                case "raidfails" -> "Districts (Raid)";
                case "starfails" -> "Schlechte Angriffe (Info)";
                case "starfails_kickpoint" -> "Schlechte Angriffe (Kickpoints)";
                case "cwcount" -> "CW-Anzahl (Info)";
                case "cwcount_kickpoint" -> "CW-Anzahl (Kickpoints)";
                default -> actionType;
            };
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
		} catch (final NumberFormatException e) {
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
		} catch (final NumberFormatException e) {
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
                    return switch (type) {
                        case CW -> "Feuert, wenn neuer CW startet";
                        default -> "Feuert bei Event-Start";
                    };
		}

            // Otherwise, it's waiting for an active event
            return switch (type) {
                case CW -> "Wartet auf aktiven CW";
                case RAID -> "Wartet auf aktives Raid Weekend";
                case CWLDAY -> "Wartet auf aktive CWL";
                case CS -> "Wartet auf aktive Clan Games";
                case FIXTIMEINTERVAL -> "Zeitbasiertes Event";
                case CWLEND -> "Wartet auf CWL Ende";
                case SEASONEND -> "Wartet auf Season-Ende";
                default -> "Wartet auf Event";
            };
	}

	/**
	 * Formats a duration in milliseconds into a human-readable string.
	 * 0 -> "0 (Sofort)"
	 * -1 -> "start"
	 * Others -> e.g. "1h", "2d", "30m"
	 */
	private String formatDuration(long duration) {
		if (duration == 0) {
			return "0 (Sofort)";
		}
		if (duration == -1) {
			return "start";
		}

		long absDuration = Math.abs(duration);
		if (absDuration % (24 * 60 * 60 * 1000) == 0) {
			return (duration / (24 * 60 * 60 * 1000)) + "d";
		}
		if (absDuration % (60 * 60 * 1000) == 0) {
			return (duration / (60 * 60 * 1000)) + "h";
		}
		if (absDuration % (60 * 1000) == 0) {
			return (duration / (60 * 1000)) + "m";
		}
		if (absDuration % 1000 == 0) {
			return (duration / 1000) + "s";
		}

		return duration + "ms";
	}
}

