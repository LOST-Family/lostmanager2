package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Ein Team des F2P-CWL-Verbunds mit seiner dauerhaften Konfiguration.
 *
 * Rollen und Kanäle werden bewusst als IDs gehalten und nirgends aus Namen
 * abgeleitet: die Konventionen im Server sind uneinheitlich (F2P nummeriert die
 * Teamrollen, LOST 3 und 4 benennen sie nach dem Gastgeberclan), und ein Parser
 * darüber würde beim ersten umbenannten Kanal brechen.
 *
 * Was monatlich wechselt - Zuständigkeit, Liga, Boni - steht nicht hier,
 * sondern in {@link F2PCwlSeasonTeam}.
 */
public class F2PCwlTeam {

	private final int teamNo;
	private final String hostClanTag;
	private final String roleId;
	private final String chatChannelId;
	private final String planChannelId;
	private final String vizeChannelId;
	private final Time startTime;
	private final int sizeTarget;
	private final int defaultSollStars;
	private final int minTh;
	private final int maxRoster;
	private final String managerDiscordId;

	public F2PCwlTeam(int teamNo, String hostClanTag, String roleId, String chatChannelId, String planChannelId,
			String vizeChannelId, Time startTime, int sizeTarget, int defaultSollStars, int minTh, int maxRoster,
			String managerDiscordId) {
		this.teamNo = teamNo;
		this.hostClanTag = hostClanTag;
		this.roleId = roleId;
		this.chatChannelId = chatChannelId;
		this.planChannelId = planChannelId;
		this.vizeChannelId = vizeChannelId;
		this.startTime = startTime;
		this.sizeTarget = sizeTarget;
		this.defaultSollStars = defaultSollStars;
		this.minTh = minTh;
		this.maxRoster = maxRoster;
		this.managerDiscordId = managerDiscordId;
	}

	public int getTeamNo() { return teamNo; }
	public String getHostClanTag() { return hostClanTag; }
	public String getRoleId() { return roleId; }
	public String getChatChannelId() { return chatChannelId; }
	public String getPlanChannelId() { return planChannelId; }

	/**
	 * Kanal für die Fassung, die nur die Vize sehen - der Planungschat.
	 *
	 * Getrennt vom Ankündigungskanal, weil dort die Aufstellung für morgen
	 * steht: das ist ein Vorschlag zur Entscheidung, keine Ansage an die Member.
	 * Ist er nicht gesetzt, entfällt diese Fassung.
	 */
	public String getVizeChannelId() { return vizeChannelId; }
	public Time getStartTime() { return startTime; }
	public int getSizeTarget() { return sizeTarget; }
	public int getDefaultSollStars() { return defaultSollStars; }

	/** Mindest-Rathaus. T1/T2 sind reine TH18-Teams; darunter wird niemand vorgeschlagen. */
	public int getMinTh() { return minTh; }

	/**
	 * Obergrenze der Kadergröße einschließlich Bank. Liegt sie über
	 * {@link #getSizeTarget()}, wird durchgewechselt: pro Kriegstag spielen
	 * nur 15, die übrigen sitzen aus.
	 */
	public int getMaxRoster() { return maxRoster; }
	public String getManagerDiscordId() { return managerDiscordId; }

	private static F2PCwlTeam fromRow(ResultSet rs) throws SQLException {
		return new F2PCwlTeam(
				rs.getInt("team_no"),
				rs.getString("host_clan_tag"),
				rs.getString("role_id"),
				rs.getString("chat_channel_id"),
				rs.getString("plan_channel_id"),
				rs.getString("vize_channel_id"),
				rs.getTime("start_time"),
				rs.getInt("size_target"),
				rs.getInt("default_soll_stars"),
				rs.getInt("min_th"),
				rs.getInt("max_roster"),
				rs.getString("manager_discord_id"));
	}

	public static F2PCwlTeam get(int teamNo) {
		try (PreparedStatement pstmt = Connection.getConnection()
				.prepareStatement("SELECT * FROM f2pcwl_teams WHERE team_no = ?")) {
			pstmt.setInt(1, teamNo);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return fromRow(rs);
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return null;
	}

	public static List<F2PCwlTeam> getAll() {
		List<F2PCwlTeam> result = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection()
				.prepareStatement("SELECT * FROM f2pcwl_teams ORDER BY team_no")) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(fromRow(rs));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return result;
	}

	/**
	 * Schreibt die vollständige Zeile.
	 *
	 * Teilaktualisierungen laufen bewusst über {@link #merged}, nicht über
	 * COALESCE im SQL: DBUtil bindet Parameter mit setObject(), und ein
	 * untypisiertes NULL lässt sich von Postgres nicht in jedem Kontext auflösen.
	 * Bei fünf Zeilen ist das Zusammenführen in Java ohnehin das klarere Mittel.
	 */
	public void save() {
		DBUtil.executeUpdate(
				"INSERT INTO f2pcwl_teams (team_no, host_clan_tag, role_id, chat_channel_id, plan_channel_id, "
						+ "vize_channel_id, start_time, size_target, default_soll_stars, min_th, max_roster, "
						+ "manager_discord_id) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
						+ "ON CONFLICT (team_no) DO UPDATE SET "
						+ "host_clan_tag = EXCLUDED.host_clan_tag, role_id = EXCLUDED.role_id, "
						+ "chat_channel_id = EXCLUDED.chat_channel_id, plan_channel_id = EXCLUDED.plan_channel_id, "
						+ "vize_channel_id = EXCLUDED.vize_channel_id, "
						+ "start_time = EXCLUDED.start_time, size_target = EXCLUDED.size_target, "
						+ "default_soll_stars = EXCLUDED.default_soll_stars, min_th = EXCLUDED.min_th, "
							+ "max_roster = EXCLUDED.max_roster, "
						+ "manager_discord_id = EXCLUDED.manager_discord_id",
				teamNo, hostClanTag, roleId, chatChannelId, planChannelId, vizeChannelId, startTime, sizeTarget,
				defaultSollStars, minTh, maxRoster, managerDiscordId);
	}

	/**
	 * Kopie dieses Teams, in der nur die übergebenen Felder ersetzt sind. Was
	 * null ist, bleibt wie gehabt. Für ein noch nicht angelegtes Team liefert
	 * {@link #blank} die Ausgangszeile.
	 */
	public F2PCwlTeam merged(String hostClanTag, String roleId, String chatChannelId, String planChannelId,
			String vizeChannelId, Time startTime, Integer sizeTarget, Integer defaultSollStars, Integer minTh,
			Integer maxRoster, String managerDiscordId) {
		return new F2PCwlTeam(this.teamNo,
				hostClanTag      != null ? hostClanTag      : this.hostClanTag,
				roleId           != null ? roleId           : this.roleId,
				chatChannelId    != null ? chatChannelId    : this.chatChannelId,
				planChannelId    != null ? planChannelId    : this.planChannelId,
				vizeChannelId    != null ? vizeChannelId    : this.vizeChannelId,
				startTime        != null ? startTime        : this.startTime,
				sizeTarget       != null ? sizeTarget       : this.sizeTarget,
				defaultSollStars != null ? defaultSollStars : this.defaultSollStars,
				minTh            != null ? minTh            : this.minTh,
				maxRoster        != null ? maxRoster        : this.maxRoster,
				managerDiscordId != null ? managerDiscordId : this.managerDiscordId);
	}

	/** Leeres Team mit den Standardwerten, als Basis für ein neu angelegtes. */
	public static F2PCwlTeam blank(int teamNo) {
		return new F2PCwlTeam(teamNo, null, null, null, null, null, null, 15, 3, 1, 16, null);
	}
}
