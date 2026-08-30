package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import lostmanager.Bot;
import lostmanager.datawrapper.F2PCwlSeason;
import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Die zeitgebundenen Meldungen während der CWL.
 *
 * Löst die CWL-Reminder ab, die bisher in ClashPerk konfiguriert sind - dort
 * steht der Zuständige als Mention im Freitext jedes einzelnen Reminders, und
 * ein Wechsel bedeutet zehn Einträge nachziehen. Hier steht er an einer Stelle.
 *
 * <b>Nicht angefasst werden die CW-Reminder</b> (Burgen, CW-Ende, Einzelpings):
 * die gehören zum regulären Clankrieg, laufen das ganze Jahr und haben mit der
 * CWL nichts zu tun.
 *
 * Läuft in kurzem Takt, kostet aber nichts: die Endzeiten stehen gecacht in
 * f2pcwl_war_tags, es wird also nur die Datenbank befragt und kein API-Request
 * gestellt.
 */
public class F2PCwlNotifier {

	/** Wie lange vor Tagesende die offenen Angriffe gemeldet werden. */
	private static final long ESKALATION_VORLAUF_MS = 30 * 60 * 1000L;

	/**
	 * Wie lange nach der ersten Meldung an alle Vize eskaliert wird, wenn der
	 * Zuständige nicht reagiert hat.
	 */
	private static final long ESKALATION_STUFE2_MS = 10 * 60 * 1000L;

	/** Rolle der Vize, für die zweite Eskalationsstufe. */
	private static final String VIZE_ROLLE = "1086732949501788240";

	/**
	 * Wie lange nach Kriegsende der Tagesbericht noch nachgereicht wird.
	 *
	 * Ohne dieses Fenster würde beim ersten Lauf die gesamte mitgeschriebene
	 * Historie auf einmal in die Kanäle laufen. Der Recorder läuft alle zwei
	 * Stunden, zwölf sind also reichlich Luft - und war der Bot länger weg,
	 * ist ein Bericht von gestern ohnehin nichts wert.
	 */
	private static final long BERICHT_FENSTER_MS = 12 * 60 * 60 * 1000L;

	public static void tick() {
		List<F2PCwlTeam> teams = F2PCwlTeam.getAll();
		if (teams.isEmpty() || Bot.getJda() == null) {
			return;
		}
		for (F2PCwlTeam team : teams) {
			try {
				pruefeTeam(team);
			} catch (final Exception e) {
				System.err.println("Fehler bei den CWL-Meldungen für Team " + team.getTeamNo() + ": "
						+ e.getMessage());
			}
		}
	}

	private static void pruefeTeam(F2PCwlTeam team) {
		String sql = "SELECT season, day, state, end_time FROM f2pcwl_war_tags "
				+ "WHERE team_no = ? AND end_time IS NOT NULL AND state <> 'warEnded' "
				+ "ORDER BY day";
		List<Object[]> tage = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setInt(1, team.getTeamNo());
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					tage.add(new Object[] { rs.getString("season"), rs.getInt("day"), rs.getString("state"),
							rs.getTimestamp("end_time") });
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
			return;
		}

		for (Object[] t : tage) {
			String season = (String) t[0];
			int day = (Integer) t[1];
			String state = (String) t[2];
			Timestamp ende = (Timestamp) t[3];

			if ("preparation".equals(state)) {
				melde(season, team, day, "PREP",
						"Vorbereitungstag " + day + " läuft. Aufstellung und Burgen prüfen.");
			} else if ("inWar".equals(state)) {
				melde(season, team, day, "BATTLE", "Kampftag " + day + " hat begonnen.");
				pruefeEskalation(season, team, day, ende);
			}
		}

		pruefeTagesbericht(team);
	}

	/**
	 * Postet den Abendbericht, sobald ein Kampftag abgerechnet ist.
	 *
	 * Ausgelöst wird über {@code state = 'warEnded'} und nicht über die
	 * Endzeit: der Zustand wird erst gesetzt, wenn der Recorder den beendeten
	 * Krieg gesehen hat, und damit stehen auch die letzten Angriffe fest. Über
	 * die Endzeit ausgelöst käme der Bericht mitunter zu früh und wäre falsch.
	 */
	private static void pruefeTagesbericht(F2PCwlTeam team) {
		String sql = "SELECT season, day FROM f2pcwl_war_tags "
				+ "WHERE team_no = ? AND state = 'warEnded' AND end_time IS NOT NULL "
				+ "AND end_time > ? ORDER BY day";
		List<Object[]> tage = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setInt(1, team.getTeamNo());
			pstmt.setTimestamp(2, new Timestamp(System.currentTimeMillis() - BERICHT_FENSTER_MS));
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					tage.add(new Object[] { rs.getString("season"), rs.getInt("day") });
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
			return;
		}

		for (Object[] t : tage) {
			String season = (String) t[0];
			int day = (Integer) t[1];

			String fuerMember = F2PCwlReport.tagesbericht(season, team, day, false);
			if (fuerMember != null) {
				melde(season, team, day, "TAGESBERICHT", fuerMember);
			}

			// Die Vize-Fassung hängt den Aufstellungsvorschlag für morgen an.
			// Sie geht in den Planungschat, damit die Entscheidung dort fällt
			// und nicht im Teamkanal vorweggenommen wird.
			if (team.getVizeChannelId() != null) {
				String fuerVize = F2PCwlReport.tagesbericht(season, team, day, true);
				if (fuerVize != null) {
					meldeIn(team.getVizeChannelId(), season, team, day, "TAGESBERICHT_VIZE", fuerVize);
				}
			}
		}
	}

	/**
	 * Meldet offene Angriffe, bevor der Tag vorbei ist.
	 *
	 * Erst an den Zuständigen des Teams, dann - wenn niemand reagiert hat - an
	 * alle Vize. "Reagiert" heißt hier schlicht: die offenen Angriffe sind
	 * weniger geworden. Ein Knopf wäre genauer, aber das Ergebnis zählt.
	 */
	private static void pruefeEskalation(String season, F2PCwlTeam team, int day, Timestamp ende) {
		long bisEnde = ende.getTime() - System.currentTimeMillis();
		if (bisEnde <= 0 || bisEnde > ESKALATION_VORLAUF_MS) {
			return;
		}

		List<String> offen = offeneAngriffe(season, team.getTeamNo(), day);
		if (offen.isEmpty()) {
			return;
		}

		String liste = String.join(", ", offen);
		int minuten = (int) (bisEnde / 60000);

		if (!schonGemeldet(season, team.getTeamNo(), day, "ESKALATION")) {
			String ping = team.getManagerDiscordId() != null ? "<@" + team.getManagerDiscordId() + "> " : "";
			melde(season, team, day, "ESKALATION", ping + "**" + offen.size()
					+ " offene Angriffe**, noch " + minuten + " Minuten: " + liste);
			return;
		}

		// Zweite Stufe: der Zuständige hat es nicht aufgelöst.
		Timestamp erste = gemeldetAm(season, team.getTeamNo(), day, "ESKALATION");
		if (erste != null && System.currentTimeMillis() - erste.getTime() >= ESKALATION_STUFE2_MS) {
			melde(season, team, day, "ESKALATION_ALLE", "<@&" + VIZE_ROLLE + "> noch immer **"
					+ offen.size() + " offene Angriffe** in Team " + team.getTeamNo() + ", "
					+ minuten + " Minuten übrig: " + liste);
		}
	}

	private static List<String> offeneAngriffe(String season, int teamNo, int day) {
		List<String> out = new ArrayList<>();
		String sql = "SELECT COALESCE(NULLIF(p.name, ''), d.player_tag) AS name, d.player_tag "
				+ "FROM f2pcwl_day_results d LEFT JOIN players p ON p.coc_tag = d.player_tag "
				+ "WHERE d.season = ? AND d.team_no = ? AND d.day = ? AND d.in_lineup AND NOT d.attacked "
				// Abgemeldete bleiben außen vor - sie haben Bescheid gesagt.
				+ "AND NOT EXISTS (SELECT 1 FROM member_signoffs s WHERE s.player_tag = d.player_tag "
				+ "                 AND (s.end_date IS NULL OR s.end_date > NOW()))";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			pstmt.setInt(2, teamNo);
			pstmt.setInt(3, day);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("player_tag");
					Player p = new Player(tag);
					User u = p.getUser();
					String id = u != null ? u.getUserID() : null;
					out.add(id != null ? "<@" + id + ">" : rs.getString("name"));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return out;
	}

	/**
	 * Postet eine Meldung genau einmal.
	 *
	 * Die Sperre wird <i>vor</i> dem Senden gesetzt: schlägt das Senden fehl,
	 * bleibt es bei einem Versuch statt bei einer Schleife, die es im Minutentakt
	 * erneut probiert.
	 */
	private static void melde(String season, F2PCwlTeam team, int day, String kind, String text) {
		meldeIn(team.getPlanChannelId(), season, team, day, kind, text);
	}

	private static void meldeIn(String channelId, String season, F2PCwlTeam team, int day, String kind,
			String text) {
		if (channelId == null) {
			return;
		}
		int neu = DBUtil.executeUpdate(
				"INSERT INTO f2pcwl_notifications (season, team_no, day, kind) VALUES (?, ?, ?, ?) "
						+ "ON CONFLICT DO NOTHING",
				season, team.getTeamNo(), day, kind).getSecond();
		if (neu == 0) {
			return; // schon gemeldet
		}

		if (F2PCwlSeason.isDryRun(season)) {
			System.out.println("F2P-CWL Trockenlauf [" + kind + "] Team " + team.getTeamNo() + " Tag " + day
					+ ": " + text);
			return;
		}
		TextChannel ch = Bot.getJda().getTextChannelById(channelId);
		if (ch == null) {
			System.err.println("CWL-Meldung: Kanal " + channelId + " nicht gefunden");
			return;
		}
		ch.sendMessage(text).queue(_ -> {
		}, err -> System.err.println("CWL-Meldung fehlgeschlagen: " + err.getMessage()));
	}

	private static boolean schonGemeldet(String season, int teamNo, int day, String kind) {
		return gemeldetAm(season, teamNo, day, kind) != null;
	}

	private static Timestamp gemeldetAm(String season, int teamNo, int day, String kind) {
		return DBUtil.getValueFromSQL(
				"SELECT sent_at FROM f2pcwl_notifications "
						+ "WHERE season = ? AND team_no = ? AND day = ? AND kind = ?",
				Timestamp.class, season, teamNo, day, kind);
	}
}
