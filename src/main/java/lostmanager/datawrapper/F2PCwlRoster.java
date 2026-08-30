package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Die Aufstellung einer CWL-Saison: wer spielt in welchem Team.
 *
 * Entsteht als Entwurf aus dem Einteilungsvorschlag und wird von den Vize
 * nachjustiert, bevor die Rollen vergeben werden.
 *
 * {@code origin} und {@code homeClanTag} werden beim Einteilen aus
 * {@code clan_members} bestimmt und dann festgeschrieben. Das ist kein
 * Redundanz-Fehler: nach dem Clanwechsel sitzt ein Gast aus L6 im CWL-Clan und
 * ist von einem Mitglied nicht mehr zu unterscheiden. Wer die Frage später
 * stellt, bekommt die falsche Antwort.
 */
public class F2PCwlRoster {

	public static final String ORIGIN_ANMELDUNG = "ANMELDUNG";
	public static final String ORIGIN_GAST = "GAST";

	private final String season;
	private final String playerTag;
	private final int teamNo;
	private final String origin;
	private final String homeClanTag;
	private final Integer prevTeamNo;
	private final String name;

	public F2PCwlRoster(String season, String playerTag, int teamNo, String origin, String homeClanTag,
			Integer prevTeamNo, String name) {
		this.season = season;
		this.playerTag = playerTag;
		this.teamNo = teamNo;
		this.origin = origin;
		this.homeClanTag = homeClanTag;
		this.prevTeamNo = prevTeamNo;
		this.name = name;
	}

	public String getSeason() { return season; }
	public String getPlayerTag() { return playerTag; }
	public int getTeamNo() { return teamNo; }
	public String getOrigin() { return origin; }
	public String getHomeClanTag() { return homeClanTag; }
	public Integer getPrevTeamNo() { return prevTeamNo; }
	public String getName() { return name; }

	public boolean isGuest() { return ORIGIN_GAST.equals(origin); }

	/**
	 * Schreibt einen Spieler in die Aufstellung.
	 *
	 * Herkunft und Heimatclan werden hier ermittelt, nicht später: wer nicht in
	 * clan_members eines F2P-Clans steht, ist Gast. Nach dem Clanwechsel wäre
	 * diese Prüfung wertlos.
	 *
	 * Das Vorteam kommt aus der letzten Saison und ergibt das "aus T2" der Excel.
	 */
	public static void set(String season, String playerTag, int teamNo) {
		String homeClan = DBUtil.getValueFromSQL(
				"SELECT cm.clan_tag FROM clan_members cm JOIN clans c ON c.tag = cm.clan_tag "
						+ "WHERE cm.player_tag = ? LIMIT 1",
				String.class, playerTag);
		boolean istMitglied = homeClan != null && Boolean.TRUE.equals(DBUtil.getValueFromSQL(
				"SELECT EXISTS (SELECT 1 FROM f2pcwl_teams WHERE host_clan_tag = ?)", Boolean.class, homeClan));

		Integer prev = DBUtil.getValueFromSQL(
				"SELECT team_no FROM f2pcwl_player_season WHERE player_tag = ? AND season < ? "
						+ "ORDER BY season DESC LIMIT 1",
				Integer.class, playerTag, season);

		DBUtil.executeUpdate(
				"INSERT INTO f2pcwl_roster (season, player_tag, team_no, origin, home_clan_tag, prev_team_no) "
						+ "VALUES (?, ?, ?, ?, ?, ?) "
						+ "ON CONFLICT (season, player_tag) DO UPDATE SET team_no = EXCLUDED.team_no",
				season, playerTag, teamNo, istMitglied ? ORIGIN_ANMELDUNG : ORIGIN_GAST, homeClan, prev);
	}

	/** Verschiebt jemanden in ein anderes Team, ohne Herkunft und Vorteam anzufassen. */
	public static boolean move(String season, String playerTag, int teamNo) {
		return DBUtil.executeUpdate("UPDATE f2pcwl_roster SET team_no = ? WHERE season = ? AND player_tag = ?",
				teamNo, season, playerTag).getSecond() > 0;
	}

	public static void clear(String season) {
		DBUtil.executeUpdate("DELETE FROM f2pcwl_roster WHERE season = ?", season);
	}

	public static List<F2PCwlRoster> get(String season) {
		List<F2PCwlRoster> out = new ArrayList<>();
		String sql = "SELECT r.*, COALESCE(NULLIF(p.name, ''), r.player_tag) AS name "
				+ "FROM f2pcwl_roster r LEFT JOIN players p ON p.coc_tag = r.player_tag "
				+ "WHERE r.season = ? ORDER BY r.team_no, name";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					Integer prev = rs.getObject("prev_team_no") == null ? null : rs.getInt("prev_team_no");
					out.add(new F2PCwlRoster(rs.getString("season"), rs.getString("player_tag"),
							rs.getInt("team_no"), rs.getString("origin"), rs.getString("home_clan_tag"),
							prev, rs.getString("name")));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return out;
	}

	public static int count(String season) {
		Long n = DBUtil.getValueFromSQL("SELECT count(*) FROM f2pcwl_roster WHERE season = ?", Long.class, season);
		return n == null ? 0 : n.intValue();
	}
}
