package lostmanager.commands.coc.util.automation;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.F2PCwlSeason;
import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.dbutil.DBUtil;

/**
 * Schreibt Angriffe und Sterne der F2P-CWL-Teams laufend mit.
 *
 * Hintergrund: die Clash-API kennt keine CWL-Historie. Abrufbar ist immer nur
 * die laufende Saison - ist sie vorbei, ist die Gruppe weg und mit ihr jede
 * Möglichkeit, die Tage nachträglich zu lesen. Was nicht währenddessen
 * festgehalten wird, existiert nicht mehr. Genau deshalb trägt ihr das bisher
 * von Hand in die Tabelle ein.
 *
 * Innerhalb einer laufenden Saison bleiben abgeschlossene Kriegstage dagegen
 * abrufbar. Ein verpasster Durchlauf ist also unkritisch: der nächste holt die
 * Tage nach, solange die Saison läuft.
 *
 * Läuft im 2-Stunden-Takt der übrigen Hintergrundarbeiten mit. Ein Kriegstag
 * dauert 24 Stunden, es kann also keiner zwischen zwei Durchläufen durchfallen.
 */
public class F2PCwlRecorder {

	private static final DateTimeFormatter API_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
			.withZone(ZoneOffset.UTC);

	/** Platzhalter für eine Runde, deren Paarungen noch nicht ausgelost sind. */
	private static final String UNSET_WAR_TAG = "#0";

	public static void recordAll() {
		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty()) {
			return; // noch nicht konfiguriert
		}

		for (F2PCwlTeam team : teams) {
			try {
				recordTeam(team);
			} catch (final Exception e) {
				System.err.println("Fehler beim Mitschreiben der CWL für Team " + team.getTeamNo() + ": "
						+ e.getMessage());
				writeStatus(team.getTeamNo(), null, null, null, e.getMessage());
			}
		}
	}

	/**
	 * Hält fest, was dieser Durchlauf gesehen hat.
	 *
	 * Der Bot schreibt nach journald, das der Deploy-User nicht lesen darf - ohne
	 * diese Spur wäre ein stiller Ausfall erst am Saisonende sichtbar, wenn die
	 * Daten nicht mehr nachholbar sind.
	 */
	private static void writeStatus(int teamNo, String groupState, String season, Integer daysSeen, String error) {
		DBUtil.executeUpdate(
				"INSERT INTO f2pcwl_status (team_no, last_run, group_state, season, days_seen, last_error) "
						+ "VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?) "
						+ "ON CONFLICT (team_no) DO UPDATE SET last_run = CURRENT_TIMESTAMP, "
						+ "group_state = EXCLUDED.group_state, season = EXCLUDED.season, "
						+ "days_seen = EXCLUDED.days_seen, last_error = EXCLUDED.last_error",
				teamNo, groupState, season, daysSeen, error);
	}

	private static void recordTeam(F2PCwlTeam team) {
		String hostTag = normalizeTag(team.getHostClanTag());
		if (hostTag == null) {
			writeStatus(team.getTeamNo(), null, null, null, "kein Gastgeberclan konfiguriert");
			return;
		}

		JSONObject group = new Clan(hostTag).getCWLJson();
		String groupState = group.optString("state", "");
		if (groupState.equals("groupnotfound") || groupState.equals("notInWar") || !group.has("rounds")) {
			// Der Normalfall außerhalb der CWL-Woche, kein Fehler.
			writeStatus(team.getTeamNo(), groupState, null, 0, null);
			return;
		}

		// Die Saison kommt aus der API, nicht aus der Systemzeit: die CWL läuft über
		// den Monatswechsel, und die Gruppe weiß selbst, wozu sie gehört.
		//
		// Auf YYYY-MM kürzen: die API liefert den Schlüssel datumsförmig als
		// "2026-08-01", die importierte Historie führt ihn als "2026-08". Ohne das
		// entstehen zwei Saisons für denselben Monat, und die Auswertung findet
		// die Vorgeschichte eines Spielers nicht mehr.
		String season = normalizeSeason(group.optString("season", ""));
		if (season == null) {
			System.err.println("CWL-Gruppe von " + hostTag + " ohne brauchbares Saison-Feld, wird übersprungen");
			return;
		}
		F2PCwlSeason.ensureExists(season, F2PCwlSeason.STATE_LAUFEND);

		JSONArray rounds = group.getJSONArray("rounds");
		int daysSeen = 0;
		for (int r = 0; r < rounds.length(); r++) {
			int day = r + 1;

			// Ein abgeschlossener Tag ändert sich nicht mehr - weder erneut auflösen
			// noch erneut abrufen.
			String cached = F2PCwlSeason.getWarTag(season, team.getTeamNo(), day);
			if (cached != null && F2PCwlSeason.isDayFinished(season, team.getTeamNo(), day)) {
				daysSeen++;
				continue;
			}

			JSONArray warTags = rounds.getJSONObject(r).optJSONArray("warTags");
			if (warTags == null) {
				continue;
			}

			if (cached != null) {
				if (recordDay(season, team, day, cached, hostTag)) {
					daysSeen++;
				}
				continue;
			}

			// Noch nicht aufgelöst: in dieser Runde jeden Krieg durchsehen, bis der
			// eigene Clan darin auftaucht. Das Ergebnis wird gecacht und nie wieder
			// gesucht.
			for (int w = 0; w < warTags.length(); w++) {
				String warTag = warTags.getString(w);
				if (UNSET_WAR_TAG.equals(warTag)) {
					continue; // Runde noch nicht ausgelost
				}
				if (recordDay(season, team, day, warTag, hostTag)) {
					daysSeen++;
					break;
				}
			}
		}

		writeStatus(team.getTeamNo(), groupState, season, daysSeen, null);
	}

	/**
	 * Wertet einen Krieg aus, sofern es der des Teams ist.
	 *
	 * @return true, wenn der Krieg zu diesem Team gehörte und verarbeitet wurde
	 */
	private static boolean recordDay(String season, F2PCwlTeam team, int day, String warTag, String hostTag) {
		JSONObject war = Clan.getCWLDayJson(warTag);
		String warState = war.optString("state", "");
		if (warState.equals("warNotFound")) {
			return false;
		}

		JSONObject ourSide = sideOf(war, hostTag);
		if (ourSide == null) {
			return false; // fremde Paarung derselben Runde
		}

		Timestamp endTime = null;
		if (war.has("endTime") && !war.isNull("endTime")) {
			try {
				endTime = Timestamp.from(Instant.from(API_TIME.parse(war.getString("endTime"))));
			} catch (final Exception e) {
				System.err.println("Unlesbare Endzeit im Krieg " + warTag + ": " + e.getMessage());
			}
		}
		F2PCwlSeason.saveWarTag(season, team.getTeamNo(), day, warTag, endTime, warState);

		// In der Vorbereitungsphase steht noch kein Angriff an, die Aufstellung wird
		// aber schon festgehalten - so ist später nachvollziehbar, wer eingeplant war.
		JSONArray members = ourSide.optJSONArray("members");
		if (members == null) {
			return true;
		}

		for (int i = 0; i < members.length(); i++) {
			JSONObject member = members.getJSONObject(i);
			JSONArray attacks = member.optJSONArray("attacks");
			boolean attacked = attacks != null && attacks.length() > 0;

			int stars = 0;
			double destruction = 0;
			if (attacks != null) {
				// In der CWL hat jeder genau einen Angriff; defensiv trotzdem der beste.
				for (int a = 0; a < attacks.length(); a++) {
					JSONObject attack = attacks.getJSONObject(a);
					int s = attack.optInt("stars", 0);
					if (s >= stars) {
						stars = s;
						destruction = attack.optDouble("destructionPercentage", 0);
					}
				}
			}

			saveResult(season, team.getTeamNo(), day, normalizeTag(member.getString("tag")), warTag, attacked, stars,
					destruction);
		}

		// Erst wenn der Kampftag läuft, steht die Aufstellung endgültig - vorher
		// könnte der Anführer noch tauschen und wir würden jemanden einteilen,
		// der gar nicht spielt.
		if (warState.equals("inWar")) {
			try {
				lostmanager.commands.coc.f2pcwl.F2PCwlDonors.assignForDay(season, team, day);
			} catch (final Exception e) {
				System.err.println("Spender-Einteilung für Team " + team.getTeamNo() + " Tag " + day
						+ " fehlgeschlagen: " + e.getMessage());
			}
		}
		return true;
	}

	/** Die Seite des Krieges, die dem eigenen Clan gehört - oder null. */
	private static JSONObject sideOf(JSONObject war, String hostTag) {
		for (String key : new String[] { "clan", "opponent" }) {
			if (!war.has(key) || war.isNull(key)) {
				continue;
			}
			JSONObject side = war.getJSONObject(key);
			if (hostTag.equals(normalizeTag(side.optString("tag", null)))) {
				return side;
			}
		}
		return null;
	}

	private static void saveResult(String season, int teamNo, int day, String playerTag, String warTag,
			boolean attacked, int stars, double destruction) {
		// donor wird bewusst nicht mitgeschrieben: die Spendereinteilung stammt aus
		// der Rotation des Bots, nicht aus der API. Stünde sie im UPDATE-Teil, würde
		// jeder Durchlauf sie wieder auf false zurücksetzen.
		DBUtil.executeUpdate(
				"INSERT INTO f2pcwl_day_results "
						+ "(season, team_no, day, player_tag, war_tag, in_lineup, attacked, stars, destruction) "
						+ "VALUES (?, ?, ?, ?, ?, TRUE, ?, ?, ?) "
						+ "ON CONFLICT (season, team_no, day, player_tag) DO UPDATE SET "
						+ "war_tag = EXCLUDED.war_tag, in_lineup = TRUE, attacked = EXCLUDED.attacked, "
						+ "stars = EXCLUDED.stars, destruction = EXCLUDED.destruction, "
						+ "recorded_at = CURRENT_TIMESTAMP",
				season, teamNo, day, playerTag, warTag, attacked, stars, destruction);
	}

	/** Saisonschlüssel auf YYYY-MM kürzen, oder null wenn unbrauchbar. */
	private static String normalizeSeason(String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim();
		return s.length() >= 7 && s.charAt(4) == '-' ? s.substring(0, 7) : null;
	}

	private static String normalizeTag(String tag) {
		if (tag == null || tag.isBlank()) {
			return null;
		}
		String t = tag.trim().toUpperCase();
		return t.startsWith("#") ? t : "#" + t;
	}
}
