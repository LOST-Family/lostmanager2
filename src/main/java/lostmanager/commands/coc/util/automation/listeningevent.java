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
import lostmanager.util.EventEinplanung;
import lostmanager.util.ListeningEventBearbeitung;
import lostmanager.util.ListeningEventFormular;
import lostmanager.util.ListeningEventService;
import lostmanager.util.ListeningEventService.FireInfo;
import lostmanager.util.ListeningEventService.FireState;
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
				case "edit" -> handleEdit(event, title);
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

		// The clan option is a free text field with autocomplete, and Discord submits
		// whatever was typed when the user does not pick a suggestion. Storing that
		// verbatim is how "2R8V8LYV9" (no #), "LOST 4 (#2LU2V2LPU)" (the autocomplete
		// label) and plain "Lost" ended up in the database as clan tags - each one an
		// event that could never fire, and none of them said so anywhere.
		String rawClantag = clanOption.getAsString();
		String clantag = lostmanager.util.ClanTag.parse(rawClantag);
		if (clantag == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"'" + rawClantag + "' ist kein gültiger Clan-Tag.\n"
							+ "Wähle den Clan aus der Autocomplete-Liste aus oder gib den Tag direkt an, z.B. `#2LU2V2LPU`.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

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
				duration = ListeningEventService.parseDuration(durationStr);
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

		// Welches Formular diese Kombination braucht, steht seit dem 14.11.2026 in
		// ListeningEventFormular - dieselben Felder nutzt auch /listeningevent edit.
		// Die Modal-ID trägt nur ein Token, siehe PendingEventCreation.
		ListeningEventFormular.Art art = ListeningEventFormular.art(type, actionTypeStr);
		if (art != null) {
			String pendingToken = newPendingToken();
			storePending(pendingToken, new PendingEventCreation(clantag, type, duration, actionTypeStr, channelId,
					kickpointReasonName));
			event.replyModal(baueModal(art.praefix + pendingToken, art.titel,
					ListeningEventFormular.felder(art, type, actionTypeStr, kickpointReasonName != null), null))
					.queue();
			return;
		}

		// Otherwise process normally (no modal needed)
		event.deferReply().queue();
		processEventCreation(event.getHook(), title, clantag, type, duration, actionTypeStr, channelId,
				kickpointReasonName, null, null);
	}

	/**
	 * Baut ein Modal aus den Feldern von {@link ListeningEventFormular}.
	 *
	 * @param vorbelegung vorhandene Werte (beim Bearbeiten), sonst null für die Vorgaben
	 */
	static Modal baueModal(String modalId, String titel, List<ListeningEventFormular.Feld> felder,
			java.util.Map<String, String> vorbelegung) {
		List<ActionRow> zeilen = new ArrayList<>();
		for (final ListeningEventFormular.Feld f : felder) {
			TextInput.Builder b = TextInput
					.create(f.id(), f.label(), f.absatz() ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT)
					.setPlaceholder(f.platzhalter()).setRequired(f.pflicht()).setMaxLength(f.maxLaenge());
			if (f.minLaenge() > 0) {
				b.setMinLength(f.minLaenge());
			}
			String start = ListeningEventFormular.startwert(f, vorbelegung);
			if (start != null) {
				b.setValue(start);
			}
			zeilen.add(ActionRow.of(b.build()));
		}
		return Modal.create(modalId, titel).addComponents(zeilen).build();
	}

	private void processEventCreation(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String clantag, String type, long duration, String actionTypeStr, String channelId,
			String kickpointReasonName, String customMessage, Integer thresholdOrAttacks) {
		processEventCreation(hook, title, clantag, type, duration, actionTypeStr, channelId, kickpointReasonName,
				customMessage, thresholdOrAttacks, null, null, null, null);
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

		// Anlegen, prüfen und in den Scheduler bringen macht der Service - hier
		// bleibt nur noch, das Ergebnis als Embed zu erzählen. Die Website geht
		// durch dieselbe Methode, damit beide Wege dieselben Regeln haben.
		ListeningEventService.Spec spec = new ListeningEventService.Spec();
		spec.clanTag = clantag;
		spec.type = type;
		spec.duration = duration;
		spec.actionType = actionTypeStr;
		spec.channelId = channelId;
		spec.kickpointReasonName = kickpointReasonName;
		spec.customMessage = customMessage;
		spec.thresholdOrAttacks = thresholdOrAttacks;
		spec.starCount = starCount;
		spec.punishmentMode = punishmentMode;
		spec.raidDistrictThresholds = raidDistrictThresholds;
		spec.namedSettings = namedSettings;

		ListeningEventService.Result ergebnis = ListeningEventService.create(spec);
		if (!ergebnis.ok()) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, ergebnis.error(), MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Long id = ergebnis.id();
		// Wie gespeichert, nicht wie eingegeben - raidfails_kickpoint wird zu
		// raidfails, und das soll die Rückmeldung auch sagen.
		if (actionTypeStr.equals("raidfails_kickpoint")) {
			actionTypeStr = "raidfails";
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
			String punished = ListeningEventService.describePunishedViolation(type, actionTypeStr);
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
	}


	// ==================== Bearbeiten ====================

	private static final String EDIT_PRAEFIX = "listeningevent_edit_";

	/** Ein Bearbeiten, das auf sein Formular wartet. */
	private record PendingEdit(long id, ListeningEventBearbeitung.Plan plan, String stand, String nutzer,
			long createdAt) {
	}

	private static final java.util.Map<String, PendingEdit> PENDING_EDITS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * {@code /listeningevent edit}: nur was angegeben ist, ändert sich.
	 *
	 * Zusammengeführt wird in {@link ListeningEventBearbeitung}, geprüft mit
	 * denselben Regeln wie beim Anlegen ({@link ListeningEventService#validate}),
	 * gespeichert über {@link ListeningEventService#bearbeite}, das auch eine schon
	 * eingeplante Ausführung sauber verwirft. Braucht die neue Kombination Werte,
	 * die es noch nicht gibt, kommt dasselbe Formular wie bei {@code add} -
	 * vorausgefüllt mit dem, was das Event schon hat.
	 */
	@SuppressWarnings("null")
	private void handleEdit(SlashCommandInteractionEvent event, String title) {
		OptionMapping idOption = event.getOption("id");
		if (idOption == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Die ID ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		long id = idOption.getAsLong();

		// Vor dem Lesen festhalten: was danach jemand anderes ändert, merkt
		// abschliessen() und überschreibt es nicht still.
		String stand = ListeningEventService.zeilenStand(id);
		ListeningEventService.Spec alt = stand == null ? null : ListeningEventService.specAusEvent(id);
		if (alt == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Event mit ID " + id + " existiert nicht.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		ListeningEventBearbeitung.Aenderung a = new ListeningEventBearbeitung.Aenderung();
		OptionMapping o;
		if ((o = event.getOption("clan")) != null) {
			a.clanTag = lostmanager.util.ClanTag.parse(o.getAsString());
			if (a.clanTag == null) {
				event.replyEmbeds(MessageUtil.buildEmbed(title, "'" + o.getAsString()
						+ "' ist kein gültiger Clan-Tag.\nWähle den Clan aus der Autocomplete-Liste aus oder gib den Tag direkt an, z.B. `#2LU2V2LPU`.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}
		if ((o = event.getOption("type")) != null) {
			a.type = o.getAsString();
		}
		if ((o = event.getOption("duration")) != null) {
			String roh = o.getAsString().trim();
			if (roh.equalsIgnoreCase("start") || roh.equalsIgnoreCase("cwstart")) {
				a.duration = -1L;
			} else {
				try {
					a.duration = ListeningEventService.parseDuration(roh);
				} catch (final IllegalArgumentException e) {
					event.replyEmbeds(MessageUtil.buildEmbed(title,
							"Ungültiges Dauer-Format: " + e.getMessage() + "\nBeispiele: 0, 1h, 2d, 24h, start",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}
		}
		if ((o = event.getOption("actiontype")) != null) {
			a.actionType = o.getAsString().trim();
		}
		if ((o = event.getOption("channel")) != null) {
			a.channelId = o.getAsChannel().getId();
		}
		if ((o = event.getOption("kickpoint_reason")) != null) {
			a.kickpointReasonName = o.getAsString();
		}
		if ((o = event.getOption("werte")) != null) {
			a.werteBearbeiten = o.getAsBoolean();
		}

		ListeningEventBearbeitung.Plan plan = ListeningEventBearbeitung.planen(alt, a);
		if (plan.fehler() != null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, plan.fehler(), MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		if (plan.formular()) {
			String token = newPendingToken();
			long jetzt = System.currentTimeMillis();
			PENDING_EDITS.values().removeIf(p -> jetzt - p.createdAt() > PENDING_TTL_MS);
			PENDING_EDITS.put(token, new PendingEdit(id, plan, stand, event.getUser().getId(), jetzt));
			event.replyModal(baueModal(EDIT_PRAEFIX + token, plan.art().titel, plan.felder(), plan.vorbelegung()))
					.queue();
			return;
		}

		event.deferReply().queue();
		abschliessen(event.getHook(), title, id, alt, plan.neu(), stand, event.getUser());
	}

	@SuppressWarnings("null")
	private void handleEditModal(ModalInteractionEvent event) {
		String title = "Listening Event";
		event.deferReply().queue();
		PendingEdit pending = PENDING_EDITS.remove(event.getModalId().substring(EDIT_PRAEFIX.length()));
		if (pending == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		ListeningEventBearbeitung.Plan plan = pending.plan();
		ListeningEventFormular.Lesung lesung = ListeningEventFormular.lies(plan.art(), plan.neu().type,
				plan.neu().kickpointReasonName != null, feld -> modalWert(event, feld));
		if (lesung.fehler() != null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, lesung.fehler(),
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}
		ListeningEventService.Spec neu = ListeningEventBearbeitung.mitFormular(plan, lesung.werte());
		// Fragt die CoC-API nach der neuen Feuerzeit - nicht im JDA-Thread
		new Thread(() -> abschliessen(event.getHook(), title, pending.id(), plan.alt(), neu, pending.stand(),
				event.getUser()), "ListeningeventEdit-" + pending.id()).start();
	}

	/** Längere Werte (eigene Nachrichten) in der Gegenüberstellung kürzen. */
	private static String kurz(String wert, int max) {
		if (wert == null) {
			return "–";
		}
		String einzeilig = wert.replace("\n", " ⏎ ");
		return einzeilig.length() > max ? einzeilig.substring(0, max) + "…" : einzeilig;
	}

	/** Speichert, wenn sich etwas ändert, und erzählt Vorher → Nachher. */
	private void abschliessen(net.dv8tion.jda.api.interactions.InteractionHook hook, String title, long id,
			ListeningEventService.Spec alt, ListeningEventService.Spec neu, String stand,
			net.dv8tion.jda.api.entities.User nutzer) {
		try {
			if (!java.util.Objects.equals(ListeningEventService.zeilenStand(id), stand)) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Event " + id
						+ " wurde inzwischen anderweitig geändert oder gelöscht. Es wurde nichts gespeichert - "
						+ "bitte `/listeningevent edit` erneut ausführen.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			List<ListeningEventService.Unterschied> unterschiede = ListeningEventService
					.unterschiede(ListeningEventService.beschreibe(alt), ListeningEventService.beschreibe(neu));
			if (unterschiede.isEmpty()) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Änderung - Event " + id
						+ " hat diese Werte schon. Es wurde nichts gespeichert.", MessageUtil.EmbedType.INFO)).queue();
				return;
			}

			ListeningEventService.Bearbeitung b = ListeningEventService.bearbeite(id, neu);
			if (!b.result().ok()) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, b.result().error(),
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// Kein eigener Log-Kanal für Listening Events - das Journal ist das Protokoll
			StringBuilder log = new StringBuilder("Listening Event " + id + " geändert von " + nutzer.getName()
					+ " (" + nutzer.getId() + "):");
			for (final ListeningEventService.Unterschied u : unterschiede) {
				log.append(" ").append(u.feld()).append(": ").append(kurz(u.vorher(), 60)).append(" -> ")
						.append(kurz(u.nachher(), 60)).append(";");
			}
			System.out.println(log);

			String clanName = new Clan(neu.clanTag).getDisplayName();
			StringBuilder desc = new StringBuilder("### Listening Event " + id + " geändert\n");
			desc.append("*").append(clanName != null ? clanName + " (" + neu.clanTag + ")" : neu.clanTag)
					.append(" · ").append(neu.type).append(" · ").append(neu.actionType).append("*\n\n");
			for (final ListeningEventService.Unterschied u : unterschiede) {
				desc.append("**").append(u.feld()).append(":** ").append(kurz(u.vorher(), 300)).append(" → ")
						.append(kurz(u.nachher(), 300)).append("\n");
			}

			EventEinplanung.Ergebnis e = b.einplanung();
			if (e != null && e.zustand() == EventEinplanung.Zustand.VERWORFEN && e.feuerzeit() != null) {
				desc.append("\n⏱️ Das Event war schon eingeplant (hätte <t:").append(e.feuerzeit() / 1000)
						.append(":R> gefeuert). Diese Einplanung ist verworfen, es wird mit den neuen Werten neu eingeplant.");
			}
			String hinweis = feuerHinweis(id);
			if (hinweis != null) {
				desc.append("\n").append(hinweis);
			}

			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc.toString(), MessageUtil.EmbedType.SUCCESS))
					.queue();
		} catch (final Exception ex) {
			System.err.println("Fehler beim Bearbeiten von Listening Event " + id + ": " + ex);
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Unerwarteter Fehler beim Bearbeiten: "
					+ ex.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
		}
	}

	/**
	 * Wann das geänderte Event als Nächstes feuert - und vor allem, wenn das nicht
	 * das ist, was man erwarten würde: schon gelaufen für diesen Anlass, oder die
	 * neue Feuerzeit liegt schon in der Vergangenheit.
	 */
	private static String feuerHinweis(long id) {
		try {
			ListeningEvent le = new ListeningEvent(id);
			if (le.getDurationUntilEnd() == -1) {
				return "▶️ Feuert beim nächsten CW-Start.";
			}
			Long ts = le.getTimestamp();
			if (ts == null || ts == Long.MAX_VALUE) {
				return "▶️ " + ListeningEventService.getFireDescriptionForEvent(le) + ".";
			}
			Long letztesZiel = le.getLastFireTarget();
			if (letztesZiel != null && Math.abs(letztesZiel - ts) < Bot.SAME_OCCASION_TOLERANCE) {
				Long am = le.getLastFiredAt();
				return "▶️ Für den aktuellen Anlass ist das Event schon gelaufen"
						+ (am != null ? " (<t:" + am / 1000 + ":f>)" : "")
						+ " - die neuen Werte gelten ab dem nächsten Mal.";
			}
			long jetzt = System.currentTimeMillis();
			if (ts > jetzt) {
				return "▶️ Nächste Ausführung: <t:" + ts / 1000 + ":f> (<t:" + ts / 1000 + ":R>).";
			}
			long minuten = (jetzt - ts) / 60000;
			if (jetzt - ts <= Bot.latenessToleranceFor(le)) {
				return "⚠️ Die neue Feuerzeit war vor " + minuten + " Minuten - das Event wird jetzt nachgeholt.";
			}
			return "⚠️ Die neue Feuerzeit ist für diesen Anlass schon vorbei (vor " + minuten
					+ " Minuten) - es wird diesmal ausgelassen und feuert beim nächsten Anlass.";
		} catch (final Exception e) {
			System.err.println("Feuerzeit von Event " + id + " nicht ermittelbar: " + e.getMessage());
			return null;
		}
	}

	/** Kurzname eines Event-Typs für die Auswahl. */
	private static String typLabel(String typ) {
		return switch (typ == null ? "" : typ.toLowerCase()) {
			case "cs" -> "Clan Games";
			case "cw" -> "CW";
			case "cwl", "cwlday" -> "CWL-Tag";
			case "raid" -> "Raid";
			case "seasonend" -> "Season-Ende";
			case "cwlend" -> "CWL-Ende";
			case "fixtimeinterval" -> "Fest";
			default -> String.valueOf(typ);
		};
	}

	/** Zeitpunkt eines Events in Worten, für die Auswahl. */
	private static String zeitpunktLabel(long dauer) {
		if (dauer == -1) {
			return "bei CW-Start";
		}
		if (dauer == 0) {
			return "zum Ende";
		}
		return ListeningEventService.formatDuration(dauer) + " vor Ende";
	}

	/**
	 * Die Events zur Auswahl für {@code edit} und {@code remove}: "#ID · Clan · Typ
	 * · Zeitpunkt · Aktion". Eine Abfrage für alles - je Event einzeln wären es bei
	 * 160 Events über 600 Abfragen pro Tastendruck.
	 */
	private static List<Command.Choice> eventAuswahl(String input) {
		ArrayList<String> zeilen = DBUtil.getArrayListFromSQL(
				"SELECT concat_ws(chr(31), le.id::text, coalesce(le.clan_tag, ''), "
						+ "coalesce(nullif(c.name, ''), nullif(s.name, ''), coalesce(le.clan_tag, '?')), "
						+ "coalesce(le.listeningtype, ''), coalesce(le.listeningvalue, 0)::text, coalesce(le.actiontype, '')) "
						+ "FROM listening_events le LEFT JOIN clans c ON c.tag = le.clan_tag "
						+ "LEFT JOIN sideclans s ON s.clan_tag = le.clan_tag "
						+ "ORDER BY 1",
				String.class);
		record Zeile(long id, String clanName, String label) {
		}
		List<Zeile> liste = new ArrayList<>();
		java.util.Set<Long> gesehen = new java.util.HashSet<>();
		for (final String z : zeilen) {
			String[] t = z.split("\u001f", -1);
			if (t.length < 6) {
				continue;
			}
			long id;
			long dauer;
			try {
				id = Long.parseLong(t[0]);
				dauer = Long.parseLong(t[4]);
			} catch (final NumberFormatException e) {
				continue;
			}
			if (!gesehen.add(id)) {
				continue;
			}
			String label = "#" + id + " · " + t[2] + " · " + typLabel(t[3]) + " · " + zeitpunktLabel(dauer) + " · "
					+ actionTypeDisplayName(t[5]);
			liste.add(new Zeile(id, t[2], label.length() > 100 ? label.substring(0, 99) + "…" : label));
		}
		liste.sort(java.util.Comparator.comparing(Zeile::clanName, String.CASE_INSENSITIVE_ORDER)
				.thenComparingLong(Zeile::id));

		String suche = input == null ? "" : input.trim().toLowerCase().replaceFirst("^#", "");
		List<Command.Choice> auswahl = new ArrayList<>();
		// Eine eingetippte Nummer zuerst als ID verstehen
		for (final Zeile z : liste) {
			if (!suche.isEmpty() && String.valueOf(z.id()).equals(suche)) {
				auswahl.add(new Command.Choice(z.label(), z.id()));
			}
		}
		for (final Zeile z : liste) {
			if (auswahl.size() >= 25) {
				break;
			}
			if (String.valueOf(z.id()).equals(suche)) {
				continue;
			}
			if (suche.isEmpty() || z.label().toLowerCase().contains(suche)) {
				auswahl.add(new Command.Choice(z.label(), z.id()));
			}
		}
		return auswahl;
	}

	/**
	 * Beim Bearbeiten fehlen Typ und Clan oft in den Optionen, weil sie sich nicht
	 * ändern sollen. Die Vorschläge für Aktion, Dauer und Grund richten sich dann
	 * nach dem gespeicherten Event.
	 */
	private static String gespeicherterWert(CommandAutoCompleteInteractionEvent event, String spalte) {
		if (!"edit".equals(event.getSubcommandName())) {
			return null;
		}
		OptionMapping idOption = event.getOption("id");
		if (idOption == null) {
			return null;
		}
		try {
			long id = Long.parseLong(idOption.getAsString().trim());
			return DBUtil.getValueFromSQL("SELECT " + spalte + " FROM listening_events WHERE id = ?", String.class,
					id);
		} catch (final NumberFormatException e) {
			return null;
		}
	}

	private static boolean darfVerwalten(String userId) {
		User u = new User(userId);
		for (String clantag : DBManager.getAllClans()) {
			Player.RoleType role = u.getClanRoles().get(clantag);
			if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER || role == Player.RoleType.COLEADER) {
				return true;
			}
		}
		return false;
	}

	/** Leaves room for the last entry under the 4096 character embed description limit. */
	private static final int EMBED_DESCRIPTION_BUDGET = 3600;



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

			FireInfo fire = ListeningEventService.describeFire(le, listeningType);
			if (statusFilter != null && fire.state() != statusFilter) {
				continue;
			}

			String eventClanTag = le.getClanTag();
			Clan clan = new Clan(eventClanTag);
			StringBuilder entry = new StringBuilder();
			entry.append("**ID:** ").append(id).append("\n");

			// Side clans live in their own table, so getNameDB alone printed "Clan: null"
			// for every CWL clan. A tag that is not even tag shaped is called out here
			// rather than left to fail silently forever in the poller.
			String clanName = clan.getDisplayName();
			entry.append("**Clan:** ").append(clanName != null ? clanName + " (" + eventClanTag + ")" : eventClanTag);
			if (!lostmanager.util.ClanTag.isValid(eventClanTag)) {
				entry.append(" ⚠️ **ungültiger Clan-Tag - dieses Event kann nie feuern**");
			}
			entry.append("\n");

			// Handle null listening type gracefully
			if (listeningType == null) {
				entry.append("**Typ:** UNKNOWN (Fehler in Datenbank)\n");
			} else {
				entry.append("**Typ:** ").append(listeningType).append("\n");
			}

			entry.append("**Dauer:** ").append(ListeningEventService.formatDuration(le.getDurationUntilEnd())).append("\n");
			entry.append("**Action:** ").append(actionType).append("\n");

			// Without the reason the list gives no way to spot an event wired to the
			// wrong one, which is only visible once kickpoints have been handed out
			String reasonName = ListeningEventService.configuredKickpointReason(le);
			if (reasonName != null) {
				entry.append("**Kickpoint-Grund:** ").append(reasonName).append("\n");
				String punished = listeningType != null && actionType != null
						? ListeningEventService.describePunishedViolation(listeningType.name().toLowerCase(),
								actionType.name().toLowerCase())
						: null;
				if (punished != null) {
					entry.append("**Vergeben für:** ").append(punished).append("\n");
				}
			}

			entry.append("**Channel:** <#").append(le.getChannelID()).append(">\n");
			entry.append("**Status:** ").append(fire.state().getLabel()).append("\n");
			entry.append("**Feuert in:** ").append(fire.text()).append("\n");
			entry.append("**Zuletzt gelaufen:** ").append(ListeningEventService.describeLastRun(le)).append("\n\n");
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

		ListeningEventService.Result ergebnis = ListeningEventService.delete(id);
		if (!ergebnis.ok()) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, ergebnis.error(), MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
				"Event mit ID " + id + " wurde erfolgreich gelöscht.", MessageUtil.EmbedType.SUCCESS)).queue();
	}

	@SuppressWarnings("null")
	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		String modalId = event.getModalId();

		if (modalId.startsWith(EDIT_PRAEFIX)) {
			handleEditModal(event);
			return;
		}

		ListeningEventFormular.Art art = ListeningEventFormular.Art.ausModalId(modalId);
		if (art == null) {
			return;
		}

		event.deferReply().queue();
		String title = "Listening Event";

		PendingEventCreation pending = takePending(modalId, art.praefix);
		if (pending == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Diese Eingabe ist abgelaufen. Bitte den Befehl erneut ausführen.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		ListeningEventFormular.Lesung lesung = ListeningEventFormular.lies(art, pending.type,
				pending.kickpointReasonName != null, feld -> modalWert(event, feld));
		if (lesung.fehler() != null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, lesung.fehler(),
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}
		ListeningEventFormular.Werte w = lesung.werte();

		if (art == ListeningEventFormular.Art.CWDONATOR) {
			java.util.Map<String, Integer> cwdonatorParams = new java.util.HashMap<>();
			cwdonatorParams.put("use_lists", w.useLists);
			cwdonatorParams.put("exclude_leaders", w.excludeLeaders);
			processEventCreationWithCWDonatorParams(event.getHook(), title, pending.clantag, pending.type,
					pending.duration, pending.actionType, pending.channelId, cwdonatorParams);
			return;
		}

		// Eine eigene Nachricht hat nie einen Grund mitgenommen, auch wenn einer
		// angegeben war - so bleibt es.
		String grund = art == ListeningEventFormular.Art.CUSTOMMESSAGE ? null : pending.kickpointReasonName;
		processEventCreation(event.getHook(), title, pending.clantag, pending.type, pending.duration,
				pending.actionType, pending.channelId, grund, w.customMessage, w.thresholdOrAttacks, w.starCount,
				w.punishmentMode, w.raidDistrictThresholds, w.namedSettings.isEmpty() ? null : w.namedSettings);
	}

	/** Inhalt eines Modal-Felds, null wenn es nicht im Modal war. */
	@SuppressWarnings("null")
	private static String modalWert(ModalInteractionEvent event, String feld) {
		net.dv8tion.jda.api.interactions.modals.ModalMapping m = event.getValue(feld);
		return m == null ? null : m.getAsString();
	}

	private void processEventCreationWithCWDonatorParams(net.dv8tion.jda.api.interactions.InteractionHook hook,
			String title, String clantag, String type, long duration, String actionTypeStr, String channelId,
			java.util.Map<String, Integer> cwdonatorParams) {

		ListeningEventService.Spec spec = new ListeningEventService.Spec();
		spec.clanTag = clantag;
		spec.type = type;
		spec.duration = duration;
		spec.actionType = actionTypeStr;
		spec.channelId = channelId;
		spec.useLists = cwdonatorParams.get("use_lists");
		spec.excludeLeaders = cwdonatorParams.get("exclude_leaders");

		ListeningEventService.Result ergebnis = ListeningEventService.create(spec);
		if (!ergebnis.ok()) {
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, ergebnis.error(), MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String desc = "### Listening Event wurde hinzugefügt.\n";
		if (ergebnis.id() != null) {
			desc += "**ID:** " + ergebnis.id() + "\n";
		}
		desc += "**Clan:** " + clantag + "\n";
		desc += "**Typ:** " + type + "\n";
		desc += "**Dauer:** " + duration + " ms\n";
		desc += "**Aktionstyp:** " + actionTypeStr + "\n";
		desc += "**Channel:** <#" + channelId + ">\n";
		desc += "**Listen-basierte Verteilung:** " + (cwdonatorParams.get("use_lists") == 1 ? "Ja" : "Nein") + "\n";
		desc += "**Leader ausschließen:** " + (cwdonatorParams.get("exclude_leaders") == 1 ? "Ja" : "Nein") + "\n";

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
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
                        case "id" -> {
                            // Wer die Events nicht verwalten darf, bekommt auch keine Liste
                            List<Command.Choice> choices = darfVerwalten(event.getUser().getId())
                                    ? eventAuswahl(input)
                                    : new ArrayList<>();
                            event.replyChoices(choices).queue(_ -> {
                            }, _ -> {
                            });
                        }
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
                                if (typeOption == null) {
                                    String gespeichert = gespeicherterWert(event, "listeningtype");
                                    if (gespeichert != null) {
                                        eventType = gespeichert.equals("cwl") ? "cwlday" : gespeichert;
                                    }
                                }
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
                                if (typeOption == null) {
                                    String gespeichert = gespeicherterWert(event, "listeningtype");
                                    if (gespeichert != null) {
                                        eventType = gespeichert;
                                    }
                                }
                                // Check if the user's input is a valid duration format
                                boolean isValidInput = false;
                                if (!input.isEmpty()) {
                                    if (input.equalsIgnoreCase("start") || input.equalsIgnoreCase("cwstart")) {
                                        // "start" and "cwstart" are valid for CW events
                                        isValidInput = eventType.equals("cw");
                                    } else {
                                        // Try to validate as a regular duration
                                        try {
                                            ListeningEventService.parseDuration(input);
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
                            String clantag = clanOption != null ? clanOption.getAsString()
                                    : gespeicherterWert(event, "clan_tag");
                            if (clantag != null) {
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



}

