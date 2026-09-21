package lostmanager.util;

import java.util.ArrayList;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lostmanager.Bot;
import lostmanager.datawrapper.ActionValue;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.KickpointReason;
import lostmanager.datawrapper.ListeningEvent;
import lostmanager.dbutil.DBUtil;

/**
 * Everything about a listening event that is not Discord.
 *
 * The slash command used to be the only way in, so validation, the encoding of
 * the action values and the insert all lived inside it, tangled up with modals
 * and interaction hooks. The website needs the same operations without any of
 * that, and a second implementation of "what is a valid event" is the kind of
 * thing that drifts apart quietly - one side would start accepting an event the
 * other rejects, and the difference would only show up months later as an event
 * that never fires.
 *
 * So the rules live here, once. {@code listeningevent.java} keeps the Discord
 * side (options, modals, embeds) and hands the collected values over; the
 * management API does the same with values from JSON.
 *
 * <h2>Why every write ends in {@link Bot#restartAllEvents()}</h2>
 *
 * The poller re-reads {@code listening_events} every two minutes, so a plain
 * insert would be picked up on its own soon enough. Deleting is the case that
 * is not symmetric: an event whose fire time is less than five minutes away has
 * already been handed to the scheduler as a task holding its own
 * {@link ListeningEvent}, and that task does not care that the row is gone - it
 * would still fire, and for a kickpoint event that means punishing people for
 * an event somebody just switched off. {@code restartAllEvents} replaces the
 * scheduler and throws those pending tasks away. Editing has the same problem
 * for the old values, so it restarts too.
 */
public final class ListeningEventService {

	private ListeningEventService() {
	}

	/** Action types the user can pick. Two of them are collapsed on the way in. */
	private static final java.util.List<String> ACTION_TYPES = java.util.List.of(
			"infomessage", "kickpoint", "cwdonator", "custommessage", "filler", "raidfails",
			"raidfails_kickpoint", "starfails", "starfails_kickpoint", "cwcount", "cwcount_kickpoint");

	/** Event types, as written into {@code listeningtype}. */
	private static final java.util.List<String> LISTENING_TYPES = java.util.List.of(
			"cw", "cwlday", "cwlend", "raid", "cs", "seasonend", "fixtimeinterval");

	/**
	 * The collected parameters of one event. A plain value holder on purpose:
	 * the slash command fills it from options and modal fields, the API from
	 * JSON, and neither should have to know a builder.
	 */
	public static final class Spec {
		public String clanTag;
		public String type;
		public long duration;
		public String actionType;
		public String channelId;
		public String kickpointReasonName;
		public String customMessage;
		public Integer thresholdOrAttacks;
		public Integer starCount;
		public Integer punishmentMode;
		public Map<String, Integer> raidDistrictThresholds;
		public Map<String, Long> namedSettings;
		/** CW donator only: 1 enables list based distribution. */
		public Integer useLists;
		/** CW donator only: 1 excludes leaders. */
		public Integer excludeLeaders;
	}

	/**
	 * @param ok         whether the operation went through
	 * @param error      the reason it did not, in plain German - it is shown to
	 *                   whoever asked, in Discord as well as in the browser
	 * @param statusCode HTTP status for the API; the command ignores it
	 * @param id         the event id on success
	 */
	public record Result(boolean ok, String error, int statusCode, Long id) {

		public static Result fehler(String meldung, int status) {
			return new Result(false, meldung, status, null);
		}

		public static Result erfolg(Long id) {
			return new Result(true, null, 200, id);
		}
	}

	// ==================== Duration ====================

	/**
	 * Parses a duration string into milliseconds. Supports: 0, plain numbers (ms),
	 * h (hours), d (days), m (minutes), s (seconds) Examples: 0, 1h, 24h, 2d, 30m,
	 * 3600000
	 */
	public static long parseDuration(String durationStr) throws IllegalArgumentException {
		durationStr = durationStr.trim().toLowerCase();

		if (durationStr.equals("0") || durationStr.isEmpty()) {
			return 0;
		}

		try {
			return Long.parseLong(durationStr);
		} catch (final NumberFormatException e) {
			// Not a plain number, try parsing with units
		}

		long multiplier;
		String numPart;

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
			return Long.parseLong(numPart.trim()) * multiplier;
		} catch (final NumberFormatException e) {
			throw new IllegalArgumentException("Ungültige Zahl: " + numPart);
		}
	}

	/**
	 * Formats a duration in milliseconds into a human-readable string.
	 * 0 -> "0 (Sofort)", -1 -> "start", others -> e.g. "1h", "2d", "30m"
	 */
	public static String formatDuration(long duration) {
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

	// ==================== Clans ====================

	/**
	 * Side clans have no roles of their own - "LOST 4 CWL 2" is staffed by the
	 * leadership of LOST 4. Permission for an event on a side clan therefore has
	 * to be checked against the main clan, the same resolution
	 * {@link KickpointReason#getEffectiveClanTag()} does for reasons.
	 *
	 * @return the main clan tag if this is a side clan, otherwise the tag itself
	 */
	public static String effectiveClanTag(String clanTag) {
		String mainClanTag = DBUtil.getValueFromSQL("SELECT belongs_to FROM sideclans WHERE clan_tag = ?",
				String.class, clanTag);
		return (mainClanTag != null && !mainClanTag.isEmpty()) ? mainClanTag : clanTag;
	}

	// ==================== Validation ====================

	/**
	 * Every rule an event has to satisfy, in the order the slash command applied
	 * them. Called by {@link #create} and {@link #update}, so nothing can get in
	 * that the command would have refused.
	 *
	 * @return null if the spec is fine, otherwise the error to show
	 */
	public static Result validate(Spec s) {
		if (s.clanTag == null || s.type == null || s.actionType == null || s.channelId == null) {
			return Result.fehler("clanTag, type, actionType und channelId sind erforderlich.", 400);
		}

		if (!ClanTag.isValid(s.clanTag)) {
			return Result.fehler("'" + s.clanTag + "' ist kein gültiger Clan-Tag.", 400);
		}
		if (!LISTENING_TYPES.contains(s.type)) {
			return Result.fehler("Ungültiger Event-Typ. Erlaubt: " + String.join(", ", LISTENING_TYPES), 400);
		}
		if (!ACTION_TYPES.contains(s.actionType)) {
			return Result.fehler("Ungültiger Aktionstyp. Erlaubt: " + String.join(", ", ACTION_TYPES), 400);
		}

		// A channel id that is not a number can never be sent to, and the mistake is
		// invisible until the event fires into nothing.
		if (!s.channelId.matches("\\d{5,25}")) {
			return Result.fehler("'" + s.channelId + "' ist keine gültige Kanal-ID.", 400);
		}

		if (s.duration == -1 && !s.type.equals("cw")) {
			return Result.fehler("'start' kann nur bei Clan War Events verwendet werden!", 400);
		}
		if (s.duration < -1) {
			return Result.fehler("Die Dauer darf nicht negativ sein.", 400);
		}

		if ((s.actionType.equals("cwdonator") || s.actionType.equals("filler")) && !s.type.equals("cw")) {
			return Result.fehler("CW Donator und Filler können nur bei Clan War Events verwendet werden!", 400);
		}
		if ((s.actionType.equals("raidfails") || s.actionType.equals("raidfails_kickpoint"))
				&& !s.type.equals("raid")) {
			return Result.fehler("Raidfails kann nur bei Raid Events verwendet werden!", 400);
		}
		if ((s.actionType.equals("raidfails") || s.actionType.equals("raidfails_kickpoint")) && s.duration != 0) {
			return Result.fehler(
					"Raidfails-Events müssen mit Dauer 0 erstellt werden (Analyse läuft zum Raid-Ende)!", 400);
		}
		if ((s.actionType.equals("starfails") || s.actionType.equals("starfails_kickpoint"))
				&& !s.type.equals("cw") && !s.type.equals("cwlday")) {
			return Result.fehler(
					"starfails und starfails_kickpoint können nur bei CW oder CWL-Day Events verwendet werden!", 400);
		}
		if ((s.actionType.equals("cwcount") || s.actionType.equals("cwcount_kickpoint"))
				&& !s.type.equals("seasonend")) {
			return Result.fehler("CW-Anzahl kann nur bei Season Ende Events verwendet werden!", 400);
		}

		// The four action types that hand out kickpoints need to know which reason to
		// book them on - without one they would run and punish nobody.
		boolean brauchtGrund = s.actionType.equals("kickpoint") || s.actionType.equals("raidfails_kickpoint")
				|| s.actionType.equals("starfails_kickpoint") || s.actionType.equals("cwcount_kickpoint");
		if (brauchtGrund && (s.kickpointReasonName == null || s.kickpointReasonName.isBlank())) {
			return Result.fehler("Kickpoint-Grund ist erforderlich, wenn actiontype=" + s.actionType + "!", 400);
		}

		// The slash command never checked this because the option is an autocomplete
		// over the clan's own reasons. Typed in freely, a reason that does not exist
		// produces an event that fires and books nothing.
		if (s.kickpointReasonName != null && !s.kickpointReasonName.isBlank()
				&& !new KickpointReason(s.kickpointReasonName, s.clanTag).Exists()) {
			return Result.fehler("Den Kickpoint-Grund '" + s.kickpointReasonName + "' gibt es in diesem Clan nicht.",
					400);
		}

		if (s.actionType.equals("custommessage") && (s.customMessage == null || s.customMessage.isBlank())) {
			return Result.fehler("Für eine benutzerdefinierte Nachricht wird ein Text gebraucht.", 400);
		}
		if (s.customMessage != null && s.customMessage.length() > 2000) {
			return Result.fehler("Die Nachricht darf höchstens 2000 Zeichen lang sein.", 400);
		}

		// Values the modals ask for, with the bounds they enforce there
		if (s.type.equals("cw") && (s.actionType.equals("infomessage") || s.actionType.equals("kickpoint"))) {
			if (s.thresholdOrAttacks == null || s.thresholdOrAttacks < 1 || s.thresholdOrAttacks > 2) {
				return Result.fehler("Benötigte Angriffe müssen 1 oder 2 sein.", 400);
			}
		}
		if (s.type.equals("cs") && (s.actionType.equals("infomessage") || s.actionType.equals("kickpoint"))) {
			if (s.thresholdOrAttacks == null || s.thresholdOrAttacks < 0) {
				return Result.fehler("Für Clan Games wird ein Punkte-Threshold gebraucht.", 400);
			}
		}
		if (s.starCount != null && (s.starCount < 0 || s.starCount > 2)) {
			return Result.fehler("Die Sterne-Anzahl muss 0, 1 oder 2 sein.", 400);
		}
		if ((s.actionType.equals("starfails") || s.actionType.equals("starfails_kickpoint")) && s.starCount == null) {
			return Result.fehler("Für schlechte Angriffe wird eine Sterne-Anzahl gebraucht.", 400);
		}
		if (s.punishmentMode != null && (s.punishmentMode < 1 || s.punishmentMode > 3)) {
			return Result.fehler("Der Modus muss 1, 2 oder 3 sein.", 400);
		}
		if (s.raidDistrictThresholds != null && !s.raidDistrictThresholds.isEmpty()) {
			for (final String schluessel : new String[] { "capital_peak_max", "other_districts_max",
					"penalize_both" }) {
				if (s.raidDistrictThresholds.get(schluessel) == null) {
					return Result.fehler("Für die District-Analyse fehlt der Wert " + schluessel + ".", 400);
				}
			}
		}
		if (s.namedSettings != null) {
			Long freieVersuche = s.namedSettings.get(ListeningEvent.SETTING_STARFAILS_FREE_HITS);
			if (freieVersuche != null && (freieVersuche < 0 || freieVersuche > 20)) {
				return Result.fehler("Freie Fehlversuche müssen zwischen 0 und 20 liegen.", 400);
			}
			Long minCw = s.namedSettings.get(ListeningEvent.SETTING_CW_MIN_COUNT);
			if (minCw != null && (minCw < 1 || minCw > 999)) {
				return Result.fehler("Die Mindestanzahl CWs muss zwischen 1 und 999 liegen.", 400);
			}
		}

		return null;
	}

	// ==================== Action values ====================

	/**
	 * Encodes the spec into the {@code actionvalues} column.
	 *
	 * Positional entries are read back by their index, so the order below is part
	 * of the format and must not be rearranged. Named settings go last precisely
	 * because they are read by key and can therefore be added without shifting
	 * anything above them.
	 */
	private static String buildActionValues(Spec s, String gespeicherterActionType) {
		ArrayList<ActionValue> actionValues = new ArrayList<>();

		if (gespeicherterActionType.equals("cwdonator") || gespeicherterActionType.equals("filler")) {
			actionValues.add(new ActionValue(ActionValue.ACTIONVALUETYPE.FILLER));

			// CW donator encodes its two switches as bare positional values that are only
			// present when switched on - 1 for list based distribution, 2 for excluding
			// leaders. Odd, but changing it would silently reinterpret existing events.
			if (s.useLists != null && s.useLists == 1) {
				actionValues.add(new ActionValue(1L));
			}
			if (s.excludeLeaders != null && s.excludeLeaders == 1) {
				actionValues.add(new ActionValue(2L));
			}
		} else if (s.kickpointReasonName != null && !s.kickpointReasonName.isBlank()
				&& (gespeicherterActionType.equals("kickpoint") || gespeicherterActionType.equals("raidfails")
						|| gespeicherterActionType.equals("starfails_kickpoint")
						|| gespeicherterActionType.equals("cwcount_kickpoint"))) {
			actionValues.add(new ActionValue(new KickpointReason(s.kickpointReasonName, s.clanTag)));
		}

		if (s.thresholdOrAttacks != null) {
			actionValues.add(new ActionValue(s.thresholdOrAttacks.longValue()));
		}
		if (s.starCount != null) {
			actionValues.add(new ActionValue(s.starCount.longValue()));
		}
		if (s.punishmentMode != null) {
			actionValues.add(new ActionValue(s.punishmentMode.longValue()));
		}
		if (s.raidDistrictThresholds != null && !s.raidDistrictThresholds.isEmpty()) {
			actionValues.add(new ActionValue(s.raidDistrictThresholds.get("capital_peak_max").longValue()));
			actionValues.add(new ActionValue(s.raidDistrictThresholds.get("other_districts_max").longValue()));
			actionValues.add(new ActionValue(s.raidDistrictThresholds.get("penalize_both").longValue()));
		}
		if (s.namedSettings != null) {
			for (final Map.Entry<String, Long> setting : s.namedSettings.entrySet()) {
				if (setting.getValue() != null) {
					actionValues.add(new ActionValue(setting.getKey(), setting.getValue()));
				}
			}
		}

		ObjectMapper mapper = new ObjectMapper();

		// A custom message replaces the list entirely - that is how it has always been
		// stored, as an object with a single "message" key instead of an array.
		if (s.customMessage != null && !s.customMessage.isEmpty()) {
			try {
				return mapper.writeValueAsString(java.util.Collections.singletonMap("message", s.customMessage));
			} catch (final JsonProcessingException e) {
				return "[]";
			}
		}

		if (actionValues.isEmpty()) {
			return "[]";
		}
		try {
			return mapper.writeValueAsString(actionValues);
		} catch (final JsonProcessingException e) {
			return "[]";
		}
	}

	/**
	 * {@code raidfails_kickpoint} is a distinction the input makes and the storage
	 * does not: both are stored as {@code raidfails}, and whether kickpoints are
	 * handed out follows from whether a reason is attached.
	 */
	private static String gespeicherterActionType(String actionType) {
		return actionType.equals("raidfails_kickpoint") ? "raidfails" : actionType;
	}

	// ==================== Write operations ====================

	/** Creates an event. Validates first, so a bad spec never reaches the table. */
	public static Result create(Spec s) {
		Result fehler = validate(s);
		if (fehler != null) {
			return fehler;
		}

		String actionType = gespeicherterActionType(s.actionType);
		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) "
						+ "VALUES (?, ?, ?, ?, ?, ?::jsonb)",
				s.clanTag, s.type, s.duration, actionType, s.channelId, buildActionValues(s, actionType));

		if (result == null) {
			return Result.fehler("Fehler beim Anlegen des Listening Events.", 500);
		}

		Bot.restartAllEvents();
		return Result.erfolg(result.getFirst());
	}

	/**
	 * Replaces an existing event with new values.
	 *
	 * Deliberately keeps {@code last_fire_target}, {@code last_fired_at} and
	 * {@code last_fire_result}. Clearing them would look tidier and would be the
	 * bug: the poller uses last_fire_target to tell "already dealt with for this
	 * occasion" from "due", so an event edited an hour after it fired would fire a
	 * second time for the same war - and for a kickpoint event that means everyone
	 * gets punished twice. Keeping the record costs nothing: a change of duration
	 * moves the fire time far enough that the tolerance no longer matches, and the
	 * event fires at its new time anyway.
	 */
	public static Result update(long id, Spec s) {
		if (!exists(id)) {
			return Result.fehler("Event mit dieser ID existiert nicht.", 404);
		}
		Result fehler = validate(s);
		if (fehler != null) {
			return fehler;
		}

		String actionType = gespeicherterActionType(s.actionType);
		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"UPDATE listening_events SET clan_tag = ?, listeningtype = ?, listeningvalue = ?, actiontype = ?, "
						+ "channel_id = ?, actionvalues = ?::jsonb WHERE id = ?",
				s.clanTag, s.type, s.duration, actionType, s.channelId, buildActionValues(s, actionType), id);

		if (result == null) {
			return Result.fehler("Fehler beim Ändern des Listening Events.", 500);
		}

		Bot.restartAllEvents();
		return Result.erfolg(id);
	}

	/** Deletes an event and takes it out of the running scheduler. */
	public static Result delete(long id) {
		if (!exists(id)) {
			return Result.fehler("Event mit dieser ID existiert nicht.", 404);
		}

		DBUtil.executeUpdate("DELETE FROM listening_events WHERE id = ?", id);
		Bot.restartAllEvents();
		return Result.erfolg(id);
	}

	public static boolean exists(long id) {
		return DBUtil.getValueFromSQL("SELECT 1 FROM listening_events WHERE id = ?", Integer.class, id) != null;
	}

	// ==================== Reading back ====================

	/**
	 * The inverse of {@link #buildActionValues}: turns the stored entries back into
	 * named values, so an editor can show what an event was created with.
	 *
	 * The positional entries carry no names, only an order, which is why this has
	 * to know the action type to read them. Kept directly below the encoder on
	 * purpose - the two are one format and have to be changed together.
	 *
	 * @return canonical keys to values; keys absent when the event has no such value
	 */
	public static Map<String, Long> decodeValues(ListeningEvent le) {
		java.util.LinkedHashMap<String, Long> aus = new java.util.LinkedHashMap<>();
		ArrayList<ActionValue> actionValues = le.getActionValues();
		if (actionValues == null) {
			return aus;
		}

		ListeningEvent.ACTIONTYPE actionType = le.getActionType();
		ListeningEvent.LISTENINGTYPE type = le.getListeningType();
		String action = actionType == null ? "" : actionType.name().toLowerCase();
		String listening = type == null ? "" : type.name().toLowerCase();

		java.util.List<Long> positionen = new ArrayList<>();
		for (final ActionValue av : actionValues) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null) {
				positionen.add(av.getValue());
			} else if (av.getSaved() == ActionValue.kind.setting && av.getKey() != null && av.getValue() != null) {
				aus.put(av.getKey(), av.getValue());
			}
		}

		if (action.equals("cwdonator") || action.equals("filler")) {
			// Hier sind die Positionen keine Reihenfolge, sondern Schalter: die 1 steht
			// für listenbasierte Verteilung, die 2 für "Leader ausschließen".
			aus.put("useLists", positionen.contains(1L) ? 1L : 0L);
			aus.put("excludeLeaders", positionen.contains(2L) ? 1L : 0L);
			return aus;
		}

		int i = 0;
		if ((listening.equals("cw") || listening.equals("cs"))
				&& (action.equals("infomessage") || action.equals("kickpoint")) && i < positionen.size()) {
			aus.put("thresholdOrAttacks", positionen.get(i++));
		}
		if (action.equals("starfails") || action.equals("starfails_kickpoint")) {
			if (i < positionen.size()) {
				aus.put("starCount", positionen.get(i++));
			}
			if (i < positionen.size()) {
				aus.put("punishmentMode", positionen.get(i++));
			}
		}
		if (action.equals("raidfails")) {
			if (i < positionen.size()) {
				aus.put("capitalPeakMax", positionen.get(i++));
			}
			if (i < positionen.size()) {
				aus.put("otherDistrictsMax", positionen.get(i++));
			}
			if (i < positionen.size()) {
				aus.put("penalizeBoth", positionen.get(i++));
			}
		}

		return aus;
	}

	/**
	 * The message of a custommessage event, which is stored as an object with a
	 * single key instead of as a list of action values.
	 *
	 * @return the text, or null if this event has none
	 */
	public static String customMessage(ListeningEvent le) {
		String roh = DBUtil.getValueFromSQL("SELECT actionvalues::text FROM listening_events WHERE id = ?",
				String.class, le.getId());
		if (roh == null || !roh.trim().startsWith("{")) {
			return null;
		}
		try {
			com.fasterxml.jackson.databind.JsonNode knoten = new ObjectMapper().readTree(roh);
			return knoten.hasNonNull("message") ? knoten.get("message").asText() : null;
		} catch (final Exception e) {
			return null;
		}
	}

	// ==================== Description ====================

	/** Day and time in the format the rest of the bot's embeds use. */
	private static final java.time.format.DateTimeFormatter RUN_TIME_FORMAT = java.time.format.DateTimeFormatter
			.ofPattern("dd.MM.yyyy, HH:mm").withZone(java.time.ZoneId.of("Europe/Berlin"));

	public enum FireState {
		/** Has a fire time in the future. */
		SCHEDULED("Geplant"),
		/**
		 * Its fire time has passed. Says nothing about whether the event actually ran -
		 * the "Zuletzt gelaufen" line below it does.
		 */
		FIRED("Feuerzeit vorbei"),
		/** Fire time passed without the event running, and it is too late to catch up. */
		MISSED("Verpasst"),
		/** Has no fire time because the clan event it listens for is not running. */
		WAITING("Wartet auf Event");

		private final String label;

		FireState(String label) {
			this.label = label;
		}

		public String getLabel() {
			return label;
		}
	}

	public record FireInfo(FireState state, String text) {
	}

	/**
	 * Builds the "Feuert in" line of an event together with the state it is in.
	 *
	 * A clan war event whose war has ended counts as waiting rather than as fired:
	 * the timestamp it missed belonged to a war that no longer exists, and the next
	 * war has not started yet.
	 */
	public static FireInfo describeFire(ListeningEvent le, ListeningEvent.LISTENINGTYPE listeningType) {
		Long timestamp = le.getTimestamp();

		if (timestamp == null || timestamp == Long.MAX_VALUE) {
			return new FireInfo(FireState.WAITING, getFireDescriptionForEvent(le));
		}

		long minutesUntilFire = (timestamp - System.currentTimeMillis()) / 1000 / 60;
		if (minutesUntilFire >= 0) {
			return new FireInfo(FireState.SCHEDULED, minutesUntilFire + " Minuten");
		}

		long minutesSinceFire = Math.abs(minutesUntilFire);
		if (listeningType == ListeningEvent.LISTENINGTYPE.CW) {
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

		// The poller writes down when it gives up on a fire time it can no longer
		// usefully catch up on. Wide tolerance on the comparison because the CoC API
		// nudges war end times by minutes while a war runs.
		String lastResult = le.getLastFireResult();
		Long lastTarget = le.getLastFireTarget();
		if (ListeningEvent.RESULT_LATE_SKIPPED.equals(lastResult) && lastTarget != null
				&& Math.abs(lastTarget - timestamp) < 6 * 60 * 60 * 1000L) {
			return new FireInfo(FireState.MISSED, "Feuerzeit vor " + minutesSinceFire + " Minuten verpasst");
		}

		return new FireInfo(FireState.FIRED, "Feuerzeit war vor " + minutesSinceFire + " Minuten");
	}

	/**
	 * What actually became of the last firing.
	 *
	 * The list used to derive everything from the calculated fire time alone, so an
	 * event the poller had quietly dropped was indistinguishable from one that had
	 * delivered - the question "it says it fired, so why is the channel empty?" had
	 * no answer anywhere in the bot. The poller now records every decision and the
	 * send helpers record every delivery, and this turns the two into one line.
	 */
	public static String describeLastRun(ListeningEvent le) {
		Long firedAt = le.getLastFiredAt();
		if (firedAt == null) {
			return "noch nie";
		}

		String when = RUN_TIME_FORMAT.format(java.time.Instant.ofEpochMilli(firedAt));
		String result = le.getLastFireResult();

		if (ListeningEvent.RESULT_LATE_SKIPPED.equals(result)) {
			return when + " - **ausgelassen**, Feuerzeit war zu lange her";
		}
		if (ListeningEvent.RESULT_CONDITION_GONE.equals(result)) {
			return when + " - nicht ausgeführt, das Clan-Event lief nicht mehr";
		}
		if (ListeningEvent.RESULT_ERROR.equals(result)) {
			return when + " - **fehlgeschlagen**, siehe Bot-Log";
		}
		if (ListeningEvent.RESULT_RUNNING.equals(result)) {
			return when + " - läuft gerade";
		}
		if (ListeningEvent.RESULT_PRE_DEPLOY.equals(result)) {
			return "vor der Umstellung - kein Protokoll, ab dem nächsten Mal wird mitgeschrieben";
		}

		// Ran normally: did anything actually reach the channel? A reminder with
		// nothing to remind about sends nothing, and that is a legitimate outcome
		// worth telling apart from a failure.
		Long messageAt = le.getLastMessageAt();
		if (messageAt != null && messageAt >= firedAt) {
			return when + " - Nachricht gesendet";
		}
		return when + " - gelaufen, aber nichts zu melden (keine Nachricht gesendet)";
	}

	/**
	 * Get a user-friendly description for when an event will fire when no valid
	 * timestamp is available
	 */
	public static String getFireDescriptionForEvent(ListeningEvent le) {
		ListeningEvent.LISTENINGTYPE type = le.getListeningType();

		if (type == null) {
			return "Fehler: Unbekannter Event-Typ";
		}

		if (le.getDurationUntilEnd() == -1) {
			return switch (type) {
				case CW -> "Feuert, wenn neuer CW startet";
				default -> "Feuert bei Event-Start";
			};
		}

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
	 * @return the name of the kickpoint reason stored on the event, or null if it
	 *         has none configured and therefore hands out no kickpoints
	 */
	public static String configuredKickpointReason(ListeningEvent le) {
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
	public static String describePunishedViolation(String type, String actionTypeStr) {
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
}
