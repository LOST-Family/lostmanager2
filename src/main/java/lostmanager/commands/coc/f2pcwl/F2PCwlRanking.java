package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.dbutil.Connection;

/**
 * Bewertet Spieler für die CWL-Einteilung und verteilt sie auf die Teams.
 *
 * Der springende Punkt: <b>Hitrates sind über Teams hinweg nicht vergleichbar.</b>
 * Team 1 spielt in einer härteren Liga als Team 2, Team 2 in einer härteren als
 * Team 3. Drei Sterne gegen Champion II sind mehr wert als drei gegen Meister I.
 * Wer die rohen Quoten nebeneinanderlegt, hält den besten T2-Spieler für stärker
 * als die unteren T1er - und liegt damit falsch.
 *
 * Die Ligahärte ist deshalb gemessen statt geschätzt: über Spieler, die das Team
 * gewechselt haben, in beiden Richtungen. Aus 158 Wechselfällen der Saisons
 * 2024-12 bis 2026-08:
 *
 * <pre>
 *   T1 -> T2  +0,440 (10 Faelle)      T2 -> T1  -0,245 (21)
 *   T2 -> T3  +0,501 ( 7)             T3 -> T2  -0,172 (22)
 *   T3 -> T4  +0,322 (20)             T4 -> T3  -0,270 (35)
 *   T4 -> T5  +0,087 (11)             T5 -> T4  -0,404 (32)
 * </pre>
 *
 * Beide Richtungen zu mitteln hebt die Selektionsverzerrung weitgehend auf - wer
 * absteigt, tut das oft nach einem schwachen Monat, wer aufsteigt nach einem
 * starken. Es bleiben rund 0,25 bis 0,34 Hitrate je Stufe.
 *
 * Nachrechnen lässt sich das jederzeit; wenn sich die Ligen der Teams
 * verschieben, gehören die Werte hier angepasst.
 */
public class F2PCwlRanking {

	/**
	 * Aufschlag, den eine Hitrate in diesem Team gegenüber Team-1-Niveau hat.
	 * Index 0 = Team 1. Zum Vergleichen wird er abgezogen.
	 */
	private static final double[] LIGA_VERSATZ = { 0.000, 0.343, 0.680, 0.976, 1.222 };

	/** So viele Saisons zählen, die jüngste am stärksten. */
	private static final int SAISON_FENSTER = 6;

	/** Abzug je Fehltag. Fehltage zählen in halben Schritten, siehe Excel-Spalte "raus". */
	private static final double FEHLTAG_ABZUG = 0.15;

	/**
	 * So viele Saisons zurück muss jemand zuletzt gespielt haben, um als Kandidat
	 * zu gelten. Sonst stehen Leute in der Rangliste, die den Clan längst
	 * verlassen haben.
	 */
	private static final int AKTIV_FENSTER = 3;

	/**
	 * Wie weit ein Kader über seine Sollgröße hinaus aufgeweitet werden darf,
	 * damit niemand ohne Team bleibt.
	 *
	 * Bei sieben Kriegstagen und 15 Plätzen hat ein Team 105 Einsätze. Selbst ein
	 * Kader von 24 kommt damit auf gut vier Tage je Spieler - genug für die acht
	 * Sterne. Die Grenze ist trotzdem da: reicht sie nicht, stimmt etwas an der
	 * Konfiguration nicht, und das soll auffallen statt still zu wachsen.
	 */
	private static final int MAX_UEBERHANG = 8;

	/*
	 * Warum das Rathaus NICHT in den Score eingeht:
	 *
	 * Naheliegend wäre, hohe Rathäuser zu bevorzugen - über alle Teams hinweg
	 * sieht es auch danach aus (TH18: 2,53 / TH17: 2,37 / TH16: 2,14). Kontrolliert
	 * man aber das Team, verschwindet der Effekt praktisch: innerhalb desselben
	 * Teams bringt ein Rathaus mehr nur +0,077, eines weniger sogar +0,046. Der
	 * scheinbare Zusammenhang war die Liga, nicht der Ausbau - die Kriegspaarung
	 * setzt ohnehin vergleichbare Aufstellungen gegeneinander.
	 *
	 * Eine harte Mindestgrenze wäre zudem schädlich: wenn TH19 erscheint, müsste
	 * sie von Hand nachgezogen werden, und Ausnahmen - ein TH17, der stark
	 * angreift - blieben außen vor. Die Liga-Normalisierung erledigt es von
	 * selbst, denn wer in einer schwächeren Liga spielt, bekommt seine Quote
	 * abgewertet. Zur Kontrolle: ohne jede Rathaus-Schranke sind die besten 15
	 * des Rückblick-Tests trotzdem ausnahmslos TH18.
	 *
	 * f2pcwl_teams.min_th bleibt als freiwillige Untergrenze bestehen, steht aber
	 * standardmäßig auf 1 und damit aus.
	 */

	public record Scored(String playerTag, String name, int townhall, double normHitrate,
			double daysMissed, int seasons, double score) {
	}

	/**
	 * Ergebnis einer Einteilung.
	 *
	 * {@code uebrig} ist im Normalfall leer - es darf niemand ohne Team bleiben.
	 * Bleibt doch jemand übrig, reichen selbst die aufgeweiteten Kader nicht, und
	 * das gehört gemeldet statt verschluckt.
	 */
	public record Assignment(Map<Integer, List<Scored>> teams, List<Scored> uebrig,
			List<Scored> ohneHistorie) {
	}

	private static double versatz(int teamNo) {
		if (teamNo < 1 || teamNo > LIGA_VERSATZ.length) {
			return 0;
		}
		return LIGA_VERSATZ[teamNo - 1];
	}

	/**
	 * Bewertet alle Spieler mit Historie, absteigend nach Score.
	 *
	 * Bei Gleichstand entscheidet die Anzahl der Saisons: wer länger dabei ist,
	 * steht vorn. Kontinuität ist ausdrücklich <i>nur</i> Tiebreaker und geht
	 * nicht in den Score ein - rechnerisch besser schlägt länger dabei.
	 */
	public static List<Scored> rank(Collection<String> kandidaten) {
		List<Scored> alle = new ArrayList<>();
		String sql = """
				WITH norm AS (
				  SELECT s.player_tag, s.days_missed, s.team_no, s.hitrate,
				         row_number() OVER (PARTITION BY s.player_tag ORDER BY s.season DESC) AS rang
				  FROM f2pcwl_player_season s
				  WHERE s.hitrate IS NOT NULL
				)
				SELECT n.player_tag,
				       COALESCE(NULLIF(p.name, ''), n.player_tag) AS name,
				       COALESCE(p.townhall, 0) AS townhall,
				       sum(n.hitrate * (? - n.rang)) / NULLIF(sum(? - n.rang), 0) AS roh,
				       sum(n.team_no * (? - n.rang)) / NULLIF(sum(? - n.rang), 0) AS team_gew,
				       avg(n.days_missed) AS fehltage,
				       count(*) AS saisons
				FROM norm n
				LEFT JOIN players p ON p.coc_tag = n.player_tag
				WHERE n.rang <= ?
				  AND EXISTS (SELECT 1 FROM f2pcwl_player_season akt
				               WHERE akt.player_tag = n.player_tag
				                 AND akt.season >= to_char(
				                       (to_date(?, 'YYYY-MM') - make_interval(months => ?)), 'YYYY-MM'))
				GROUP BY n.player_tag, p.name, p.townhall
				""";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			int w = SAISON_FENSTER + 1;
			pstmt.setInt(1, w);
			pstmt.setInt(2, w);
			pstmt.setInt(3, w);
			pstmt.setInt(4, w);
			pstmt.setInt(5, SAISON_FENSTER);
			pstmt.setString(6, java.time.YearMonth.now().toString());
			pstmt.setInt(7, AKTIV_FENSTER);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("player_tag");
					if (kandidaten != null && !kandidaten.contains(tag)) {
						continue;
					}
					double roh = rs.getDouble("roh");
					// Der Versatz wird über das gewichtete mittlere Team bestimmt:
					// wer zwischen T2 und T3 pendelte, bekommt auch einen Wert
					// dazwischen, statt hart auf eine Stufe gerundet zu werden.
					double teamGew = rs.getDouble("team_gew");
					int unten = (int) Math.floor(teamGew);
					int oben = (int) Math.ceil(teamGew);
					double anteil = teamGew - unten;
					double versatz = versatz(unten) * (1 - anteil) + versatz(oben) * anteil;

					double normHitrate = roh - versatz;
					double fehltage = rs.getDouble("fehltage");
					int saisons = rs.getInt("saisons");
					alle.add(new Scored(tag, rs.getString("name"), rs.getInt("townhall"),
							round(normHitrate), round(fehltage), saisons,
							round(normHitrate - FEHLTAG_ABZUG * fehltage)));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error beim Ranking: " + e.getMessage());
		}

		alle.sort(Comparator.comparingDouble(Scored::score).reversed()
				.thenComparing(Comparator.comparingInt(Scored::seasons).reversed())
				.thenComparing(Scored::name));
		return alle;
	}

	/**
	 * Verteilt die bewerteten Spieler von oben nach unten auf die Teams.
	 *
	 * Sortiert wird allein nach Leistung. Eine Rathaus-Untergrenze wird nur
	 * beachtet, wenn jemand sie ausdrücklich gesetzt hat - siehe die Begründung
	 * oben; standardmäßig ist sie aus.
	 */
	public static Assignment assign(List<Scored> rangliste, List<F2PCwlTeam> teams,
			Collection<String> kandidatenOhneHistorie) {
		Map<Integer, List<Scored>> verteilt = new LinkedHashMap<>();
		for (F2PCwlTeam team : teams) {
			verteilt.put(team.getTeamNo(), new ArrayList<>());
		}
		List<Scored> offen = new ArrayList<>(rangliste);

		// Erster Durchgang: von oben nach unten bis zur regulären Kadergröße.
		for (F2PCwlTeam team : teams) {
			List<Scored> platz = verteilt.get(team.getTeamNo());
			for (java.util.Iterator<Scored> it = offen.iterator(); it.hasNext();) {
				if (platz.size() >= team.getMaxRoster()) {
					break;
				}
				Scored s = it.next();
				if (s.townhall() >= team.getMinTh()) {
					platz.add(s);
					it.remove();
				}
			}
		}

		// Wer jetzt noch übrig ist, muss trotzdem unterkommen: es darf niemand
		// ohne Team dastehen. Aufgefüllt wird von unten nach oben - ein Team mehr
		// aufzuweiten schmerzt unten am wenigsten. Oben muss die Aufstellung
		// eingespielt bleiben, weil man dort jemanden ersetzen können muss, der
		// einen Angriff verhaut.
		verteileVonUnten(offen, teams, verteilt);

		// Ohne Historie lässt sich niemand bewerten. Sie kommen in die unteren
		// Teams, wo Überhang am wenigsten wehtut, und werden ausdrücklich als
		// unbewertet ausgewiesen - ein starker Neuzugang gehört woandershin, das
		// kann aber nur jemand entscheiden, der ihn kennt.
		//
		// Name und Rathaus stehen in players - sie hier nicht aufzulösen hieße,
		// diese Leute als nackten Tag mit "TH0" anzuzeigen.
		List<Scored> ohne = new ArrayList<>(mitStammdaten(kandidatenOhneHistorie));
		if (!ohne.isEmpty() && !teams.isEmpty()) {
			// Ohne Quote bleibt allein das Rathaus als Anhaltspunkt. Es sagt
			// innerhalb eines Teams nichts über die Trefferquote - über die Teams
			// hinweg aber sehr wohl, T1 und T2 sind reine TH18-Runden. Absteigend
			// sortiert, weil die Verteilung von hinten nimmt: so landet der
			// niedrigste unten statt ein TH14 in Team 3.
			ohne.sort(Comparator.comparingInt(Scored::townhall).reversed()
					.thenComparing(Scored::name));
			verteileVonUnten(new ArrayList<>(ohne), teams, verteilt);
		}
		return new Assignment(verteilt, offen, ohne);
	}

	/**
	 * Verteilt von unten nach oben, bis alle untergekommen sind.
	 *
	 * Genommen wird jeweils der Letzte der Liste; die Aufrufer legen sie so, dass
	 * das der Schwächste ist. Aufgeweitet wird stufenweise - erst bekommt jedes
	 * Team einen über seiner Obergrenze, dann zwei. Reihum in einem Rutsch zu
	 * verteilen würde die Schwächsten bis nach Team 1 hochspülen, genau dorthin,
	 * wo die Aufstellung am verlässlichsten sein muss.
	 */
	private static void verteileVonUnten(List<Scored> offen, List<F2PCwlTeam> teams,
			Map<Integer, List<Scored>> verteilt) {
		if (offen.isEmpty()) {
			return;
		}
		List<F2PCwlTeam> vonUnten = new ArrayList<>(teams);
		vonUnten.sort(Comparator.comparingInt(F2PCwlTeam::getTeamNo).reversed());

		int toleranz = 1;
		while (!offen.isEmpty() && toleranz <= MAX_UEBERHANG) {
			for (F2PCwlTeam team : vonUnten) {
				List<Scored> platz = verteilt.get(team.getTeamNo());
				while (!offen.isEmpty() && platz.size() < team.getMaxRoster() + toleranz) {
					platz.add(offen.remove(offen.size() - 1));
				}
				if (offen.isEmpty()) {
					break;
				}
			}
			toleranz++;
		}
	}

	/** Spieler ohne CWL-Historie, aber mit Name und Rathaus aus {@code players}. */
	private static List<Scored> mitStammdaten(Collection<String> tags) {
		List<Scored> out = new ArrayList<>();
		if (tags == null) {
			return out;
		}
		String sql = "SELECT COALESCE(NULLIF(name, ''), coc_tag) AS name, COALESCE(townhall, 0) AS th "
				+ "FROM players WHERE coc_tag = ?";
		for (String tag : tags) {
			String name = tag;
			int th = 0;
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						name = rs.getString("name");
						th = rs.getInt("th");
					}
				}
			} catch (SQLException e) {
				System.err.println("Database error: " + e.getMessage());
			}
			out.add(new Scored(tag, name, th, 0, 0, 0, 0));
		}
		return out;
	}

	private static double round(double v) {
		return Math.round(v * 1000d) / 1000d;
	}
}
