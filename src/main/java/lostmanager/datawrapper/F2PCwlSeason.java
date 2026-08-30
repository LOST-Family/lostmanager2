package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Eine CWL-Saison des F2P-Verbunds, plus der Cache der aufgelösten War-Tags.
 *
 * Der Schlüssel ist der Monat in der Form '2026-09'. Er wird nicht aus der
 * Systemzeit abgeleitet, sondern aus dem Feld "season" der leaguegroup-Antwort:
 * die CWL läuft über einen Monatswechsel hinweg, und die API weiß selbst am
 * besten, zu welcher Saison eine laufende Runde gehört.
 */
public class F2PCwlSeason {

	public static final String STATE_PLANUNG = "PLANUNG";
	public static final String STATE_LAUFEND = "LAUFEND";
	public static final String STATE_ABGESCHLOSSEN = "ABGESCHLOSSEN";

	private final String season;
	private final String state;
	private final String signupRoster;

	public F2PCwlSeason(String season, String state, String signupRoster) {
		this.season = season;
		this.state = state;
		this.signupRoster = signupRoster;
	}

	public String getSeason() { return season; }
	public String getState() { return state; }
	public String getSignupRoster() { return signupRoster; }

	public static F2PCwlSeason get(String season) {
		try (PreparedStatement pstmt = Connection.getConnection()
				.prepareStatement("SELECT * FROM f2pcwl_seasons WHERE season = ?")) {
			pstmt.setString(1, season);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return new F2PCwlSeason(rs.getString("season"), rs.getString("state"),
							rs.getString("signup_roster"));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return null;
	}

	/** Legt die Saison an, falls sie noch nicht existiert. Bestehende bleiben unberührt. */
	public static void ensureExists(String season, String state) {
		DBUtil.executeUpdate("INSERT INTO f2pcwl_seasons (season, state) VALUES (?, ?) "
				+ "ON CONFLICT (season) DO NOTHING", season, state);
	}

	public static void setState(String season, String state) {
		DBUtil.executeUpdate("UPDATE f2pcwl_seasons SET state = ? WHERE season = ?", state, season);
	}

	public static List<String> getAllSeasons() {
		return DBUtil.getArrayListFromSQL("SELECT season FROM f2pcwl_seasons ORDER BY season DESC", String.class);
	}

	// ------------------------------------------------------------------
	// War-Tag-Cache
	// ------------------------------------------------------------------

	/**
	 * Der gespeicherte War-Tag eines Kriegstages, oder null wenn noch nicht
	 * aufgelöst.
	 *
	 * Die Auflösung ist teuer - in einer Runde muss jeder War-Tag einzeln geholt
	 * werden, bis der eigene Clan darin auftaucht - und ändert sich danach nie
	 * mehr. Genau deshalb dieser Cache: er nimmt zugleich der wiederholten
	 * Abfrage der Tagesendzeit ihre bis zu 28 Requests.
	 */
	public static String getWarTag(String season, int teamNo, int day) {
		return DBUtil.getValueFromSQL(
				"SELECT war_tag FROM f2pcwl_war_tags WHERE season = ? AND team_no = ? AND day = ?",
				String.class, season, teamNo, day);
	}

	public static void saveWarTag(String season, int teamNo, int day, String warTag, Timestamp endTime, String state) {
		DBUtil.executeUpdate(
				"INSERT INTO f2pcwl_war_tags (season, team_no, day, war_tag, end_time, state) "
						+ "VALUES (?, ?, ?, ?, ?, ?) "
						+ "ON CONFLICT (season, team_no, day) DO UPDATE SET "
						+ "war_tag = EXCLUDED.war_tag, end_time = EXCLUDED.end_time, state = EXCLUDED.state",
				season, teamNo, day, warTag, endTime, state);
	}

	/**
	 * Ob für diese Saison nur mitgeschrieben und nichts gepostet wird.
	 *
	 * Standard ist true: eine frisch angelegte Saison schweigt, bis jemand sie
	 * ausdrücklich scharf schaltet. Ein Bot, der ungefragt in Team-Kanäle
	 * schreibt, während die Vize dieselben Posts von Hand machen, richtet mehr
	 * Schaden an als er Arbeit spart. Unbekannte Saison zählt ebenfalls als
	 * Trockenlauf - im Zweifel lieber still.
	 */
	public static boolean isDryRun(String season) {
		Boolean v = DBUtil.getValueFromSQL("SELECT dry_run FROM f2pcwl_seasons WHERE season = ?",
				Boolean.class, season);
		return v == null || v;
	}

	public static void setDryRun(String season, boolean dryRun) {
		DBUtil.executeUpdate("UPDATE f2pcwl_seasons SET dry_run = ? WHERE season = ?", dryRun, season);
	}

	/**
	 * Ob der Kriegstag abgeschlossen ist. Danach ändert sich sein Ergebnis nicht
	 * mehr, er muss also weder erneut aufgelöst noch erneut abgerufen werden.
	 */
	public static boolean isDayFinished(String season, int teamNo, int day) {
		return "warEnded".equals(DBUtil.getValueFromSQL(
				"SELECT state FROM f2pcwl_war_tags WHERE season = ? AND team_no = ? AND day = ?",
				String.class, season, teamNo, day));
	}
}
