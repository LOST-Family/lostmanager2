package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Der Bericht zu einem Kriegstag.
 *
 * Bisher schreibt der Zuständige das jeden Abend von Hand in den
 * Ankündigungskanal seines Teams. Der Bot hat die Zahlen ohnehin schon
 * mitgeschrieben, also kann er den Text auch stellen.
 *
 * <b>Zwei Fassungen, zwei Adressaten.</b> Die Member sehen, was der Tag gebracht
 * hat und wer bei seinen Medaillen knapp wird - der Teil, auf den sie selbst
 * reagieren können. Die Vize bekommen zusätzlich den Vorschlag für morgen: wer
 * auf die Bank soll. Das ist eine Entscheidung, und Entscheidungen trifft der
 * Vize, nicht der Bot - deshalb steht sie im Planungschat und nicht im
 * Teamkanal.
 *
 * Der gemeinsame Rumpf ist Absicht: der Bericht auf Abruf
 * ({@code /f2pcwl tag}) und der automatische am Abend sollen nicht
 * unterschiedlich aussehen.
 */
public class F2PCwlReport {

	/** Ab hier gibt es die volle Medaillenausschüttung. */
	private static final int STERNE_FUER_VOLLEN_BONUS = 8;

	/** Ein CWL-Monat hat sieben Kampftage. */
	private static final int KRIEGSTAGE = 7;

	/** Discord nimmt 2000 Zeichen; darunter bleibt Luft für den Rahmen. */
	private static final int MAX_LAENGE = 1900;

	public record Zeile(String name, boolean inLineup, boolean attacked, int stars, double destruction,
			boolean donor) {
	}

	/**
	 * Baut den Bericht zu einem Kriegstag.
	 *
	 * @param fuerVize hängt den Aufstellungsvorschlag für den Folgetag an
	 * @return der fertige Text, oder null wenn zu dem Tag nichts erfasst ist
	 */
	public static String tagesbericht(String season, F2PCwlTeam team, int day, boolean fuerVize) {
		List<Zeile> zeilen = ladeTag(season, team.getTeamNo(), day);
		if (zeilen.isEmpty()) {
			return null;
		}

		int angegriffen = 0;
		int sterne = 0;
		List<String> ohne = new ArrayList<>();
		for (Zeile z : zeilen) {
			if (z.attacked()) {
				angegriffen++;
				sterne += z.stars();
			} else if (z.inLineup()) {
				ohne.add(z.name());
			}
		}
		int aufgestellt = angegriffen + ohne.size();

		StringBuilder sb = new StringBuilder();
		sb.append("**Team ").append(team.getTeamNo()).append(" · Tag ").append(day).append("** — ")
				.append(zustand(warState(season, team.getTeamNo(), day))).append("\n");
		sb.append(angegriffen).append(" von ").append(aufgestellt).append(" angegriffen · ")
				.append(sterne).append(" ★");
		if (angegriffen > 0) {
			sb.append(" · Ø ").append(String.format("%.2f", (double) sterne / angegriffen));
		}
		zeile(sb);

		if (angegriffen > 0) {
			absatz(sb, "**Angriffe**");
			for (Zeile z : zeilen) {
				if (!z.attacked()) {
					continue;
				}
				sb.append(z.stars()).append("★ ");
				if (z.destruction() > 0) {
					sb.append(Math.round(z.destruction())).append("% ");
				}
				sb.append(z.name());
				if (z.donor()) {
					sb.append(" · Spender");
				}
				zeile(sb);
			}
		}

		if (!ohne.isEmpty()) {
			absatz(sb, "**Ohne Angriff (" + ohne.size() + ")**");
			for (String name : ohne) {
				sb.append("• ").append(name);
				zeile(sb);
			}
		}

		// Am letzten Tag ist nichts mehr zu planen und nichts mehr aufzuholen.
		F2PCwlLineup.Vorschlag morgen = day < KRIEGSTAGE ? vorschlag(season, team, day + 1) : null;

		medaillenWarnung(sb, morgen, day);

		if (fuerVize && morgen != null) {
			vizeTeil(sb, morgen);
		}

		String text = sb.toString();
		return text.length() > MAX_LAENGE ? text.substring(0, MAX_LAENGE) + "\n" + "…" : text;
	}

	/**
	 * Wem die Medaillen wegzulaufen drohen.
	 *
	 * Nicht "wer liegt unter acht Sternen" - das sind nach zwei Tagen alle und
	 * sagt niemandem etwas. Gemeint ist, bei wem die verbleibenden Kriegstage
	 * bei seiner bisherigen Rate nicht mehr reichen. Das ist dieselbe Rechnung,
	 * nach der {@link F2PCwlLineup} die Pflichteinsätze vergibt, also nennen
	 * Bericht und Aufstellung auch dieselben Namen.
	 */
	private static void medaillenWarnung(StringBuilder sb, F2PCwlLineup.Vorschlag morgen, int day) {
		if (morgen == null) {
			return;
		}
		List<F2PCwlLineup.Kandidat> knapp = new ArrayList<>();
		for (F2PCwlLineup.Kandidat k : morgen.spielt()) {
			if (k.muss()) {
				knapp.add(k);
			}
		}
		for (F2PCwlLineup.Kandidat k : morgen.sitztAus()) {
			if (k.muss()) {
				knapp.add(k);
			}
		}
		if (knapp.isEmpty()) {
			return;
		}

		int uebrig = KRIEGSTAGE - day;
		absatz(sb, "**Knapp am vollen Bonus** (" + STERNE_FUER_VOLLEN_BONUS + "★ über die Saison)");
		for (F2PCwlLineup.Kandidat k : knapp) {
			sb.append("• ").append(k.name()).append(" — ").append(k.sterneBisher()).append("★, braucht noch ")
					.append(k.tageNoetig()).append(" von ").append(uebrig).append(" Tagen");
			zeile(sb);
		}
	}

	/**
	 * Der Aufstellungsvorschlag für den Folgetag, kurz gehalten.
	 *
	 * Nur die Bank - wer ohnehin spielt, muss nicht aufgezählt werden. Die
	 * vollständige Liste steht in {@code /f2pcwl aufstellung}.
	 */
	private static void vizeTeil(StringBuilder sb, F2PCwlLineup.Vorschlag v) {
		absatz(sb, "**Vorschlag Tag " + v.tag() + "** (" + v.plaetze() + " Plätze)");
		if (v.sitztAus().isEmpty()) {
			sb.append("Bank: niemand, der Kader passt genau.");
			zeile(sb);
			return;
		}
		List<String> namen = new ArrayList<>();
		for (F2PCwlLineup.Kandidat k : v.sitztAus()) {
			namen.add(k.name());
		}
		sb.append("Bank: ").append(String.join(", ", namen));
		zeile(sb);
	}

	private static F2PCwlLineup.Vorschlag vorschlag(String season, F2PCwlTeam team, int morgen) {
		try {
			F2PCwlLineup.Vorschlag v = F2PCwlLineup.fuerTag(season, team, morgen);
			return v == null || v.spielt().isEmpty() ? null : v;
		} catch (final Exception e) {
			System.err.println("Aufstellungsvorschlag für Team " + team.getTeamNo() + " Tag " + morgen
					+ " fehlgeschlagen: " + e.getMessage());
			return null;
		}
	}

	private static void zeile(StringBuilder sb) {
		sb.append("\n");
	}

	private static void absatz(StringBuilder sb, String ueberschrift) {
		sb.append("\n").append(ueberschrift).append("\n");
	}

	private static String zustand(String warState) {
		if (warState == null) {
			return "Status unbekannt";
		}
		return switch (warState) {
			case "preparation" -> "Vorbereitungstag, es wurde noch nicht angegriffen";
			case "inWar" -> "Kampftag läuft";
			case "warEnded" -> "Kampftag beendet";
			default -> "Status: " + warState;
		};
	}

	private static String warState(String season, int teamNo, int day) {
		return DBUtil.getValueFromSQL(
				"SELECT state FROM f2pcwl_war_tags WHERE season = ? AND team_no = ? AND day = ?",
				String.class, season, teamNo, day);
	}

	/**
	 * Die Zeilen eines Kriegstags, bester Angriff zuerst.
	 *
	 * Das ist keine Rangliste, sondern soll zeigen, was der Tag gebracht hat -
	 * wer nicht angegriffen hat, steht deshalb am Ende und nicht dazwischen.
	 */
	private static List<Zeile> ladeTag(String season, int teamNo, int day) {
		List<Zeile> rows = new ArrayList<>();
		String sql = "SELECT COALESCE(NULLIF(p.name, ''), d.player_tag) AS name, "
				+ "d.in_lineup, d.attacked, d.stars, COALESCE(d.destruction, 0) AS destruction, d.donor "
				+ "FROM f2pcwl_day_results d LEFT JOIN players p ON p.coc_tag = d.player_tag "
				+ "WHERE d.season = ? AND d.team_no = ? AND d.day = ? "
				+ "ORDER BY d.attacked DESC, d.stars DESC, d.destruction DESC";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			pstmt.setInt(2, teamNo);
			pstmt.setInt(3, day);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					rows.add(new Zeile(rs.getString("name"), rs.getBoolean("in_lineup"), rs.getBoolean("attacked"),
							rs.getInt("stars"), rs.getDouble("destruction"), rs.getBoolean("donor")));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error: " + e.getMessage());
		}
		return rows;
	}
}
