package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lostmanager.Bot;
import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Teilt die Spender eines Kriegstages ein und sagt ihnen Bescheid.
 *
 * Bisher schreibt das jemand von Hand: pro Team und Kriegstag ein Post mit drei
 * Pings, über fünf Teams und sieben Tage also 35 Posts je CWL. In der Excel
 * stehen dieselben drei Namen als grün gefärbtes Zellenpaar - nachgezählt exakt
 * drei pro Tag.
 *
 * Wer spendet, ist eine Einteilung und keine Messung: die Clash-API kann
 * Spenden nicht pro Krieg ausweisen, sie muss es aber auch nicht. Der Bot
 * würfelt reihum und hält fest, wen er benannt hat.
 */
public class F2PCwlDonors {

	private static final int DONORS_PER_DAY = 3;

	/** Kanal, in dem steht, was zu spenden ist - so verlinkt ihr es auch heute. */
	private static final String HINWEIS_KANAL = "1530990205929259008";

	/**
	 * Benennt die Spender für einen Kampftag, falls das noch nicht geschehen ist.
	 *
	 * Wird aus dem Recorder aufgerufen, sobald ein Tag in den Kampf geht. Ein
	 * erneuter Aufruf ändert nichts - die Einteilung eines Tages steht einmal.
	 */
	public static void assignForDay(String season, F2PCwlTeam team, int day) {
		Long already = DBUtil.getValueFromSQL(
				"SELECT count(*) FROM f2pcwl_day_results WHERE season = ? AND team_no = ? AND day = ? AND donor",
				Long.class, season, team.getTeamNo(), day);
		if (already != null && already > 0) {
			return;
		}

		List<String> chosen = pickDonors(season, team.getTeamNo(), day);
		if (chosen.isEmpty()) {
			return;
		}

		for (String tag : chosen) {
			DBUtil.executeUpdate("UPDATE f2pcwl_day_results SET donor = TRUE "
					+ "WHERE season = ? AND team_no = ? AND day = ? AND player_tag = ?",
					season, team.getTeamNo(), day, tag);
		}

		// Im Trockenlauf wird die Einteilung festgehalten, aber niemand gepingt -
		// so lässt sich nachsehen, wen der Bot genommen hätte, ohne dass er neben
		// den Vize her in die Kanäle schreibt.
		if (lostmanager.datawrapper.F2PCwlSeason.isDryRun(season)) {
			System.out.println("F2P-CWL Trockenlauf: Spender für Team " + team.getTeamNo() + " Tag " + day
					+ " eingeteilt, aber nicht gepostet (" + String.join(", ", chosen) + ")");
			return;
		}
		post(team, day, chosen);
	}

	/**
	 * Wählt die Spender des Tages.
	 *
	 * Reihum: wer diese Saison am seltensten dran war, kommt zuerst; bei
	 * Gleichstand entscheidet der Zufall. So ist niemand zweimal dran, bevor
	 * alle einmal waren.
	 *
	 * Gäste bleiben außen vor - sie helfen einen Monat aus, die Spendenpflicht
	 * ist Sache der Mitglieder. Erkennbar sind sie daran, dass clan_members die
	 * verwaltete Zugehörigkeit führt und nicht den Live-Clan: wer dort nicht bei
	 * einem der F2P-Clans steht, ist zu Gast, auch wenn er gerade im CWL-Clan
	 * sitzt.
	 */
	private static List<String> pickDonors(String season, int teamNo, int day) {
		List<String> result = new ArrayList<>();
		String sql = "SELECT d.player_tag, "
				+ "  (SELECT count(*) FROM f2pcwl_day_results x "
				+ "     WHERE x.season = d.season AND x.team_no = d.team_no "
				+ "       AND x.player_tag = d.player_tag AND x.donor) AS bisher "
				+ "FROM f2pcwl_day_results d "
				+ "WHERE d.season = ? AND d.team_no = ? AND d.day = ? AND d.in_lineup "
				// Mitglied ist, wer in einem Gastgeberclan steht, der zugleich ein
				// gepflegter Clan ist - also LOST F2P oder LOST F2P 2. Die drei
				// reinen CWL-Clans führen keine Mitglieder; der Join über clans
				// hält das auch dann richtig, wenn das später jemand ändert.
				+ "  AND EXISTS (SELECT 1 FROM clan_members cm "
				+ "               JOIN clans c ON c.tag = cm.clan_tag "
				+ "               WHERE cm.player_tag = d.player_tag "
				+ "                 AND cm.clan_tag IN (SELECT host_clan_tag FROM f2pcwl_teams)) "
				+ "ORDER BY bisher ASC, random() LIMIT " + DONORS_PER_DAY;
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			pstmt.setInt(2, teamNo);
			pstmt.setInt(3, day);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(rs.getString("player_tag"));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error beim Spender-Auslosen: " + e.getMessage());
		}
		return result;
	}

	private static void post(F2PCwlTeam team, int day, List<String> tags) {
		String channelId = team.getPlanChannelId();
		if (channelId == null || Bot.getJda() == null) {
			return;
		}
		TextChannel channel = Bot.getJda().getTextChannelById(channelId);
		if (channel == null) {
			System.err.println("Spender-Post: Kanal " + channelId + " für Team " + team.getTeamNo()
					+ " nicht gefunden");
			return;
		}

		StringBuilder sb = new StringBuilder();
		sb.append("__**Spender für Tag ").append(day).append(":**__\n");
		for (String tag : tags) {
			Player player = new Player(tag);
			User user = player.getUser();
			String id = user != null ? user.getUserID() : null;
			// Ohne verknüpften Discord-Account bleibt nur der Spielername.
			sb.append(id != null ? "<@" + id + ">" : player.getNameDB()).append(" ");
		}
		sb.append("\n-# wenn ihr nicht wisst, was ihr spenden sollt, orientiert euch an <#")
				.append(HINWEIS_KANAL).append(">");

		channel.sendMessage(sb.toString()).queue(_ -> {
		}, err -> System.err.println("Spender-Post für Team " + team.getTeamNo() + " fehlgeschlagen: "
				+ err.getMessage()));
	}
}
