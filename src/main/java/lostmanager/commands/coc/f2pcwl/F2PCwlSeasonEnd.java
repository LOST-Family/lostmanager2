package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Saisonabschluss: verdichtet die Tagesdaten und schlägt die Bonusvergabe vor.
 *
 * Die Verdichtung schreibt {@code f2pcwl_player_season} fort - dieselbe Tabelle,
 * in der auch die 21 aus der Excel importierten Monate liegen. Damit die alten
 * und die neuen Zahlen vergleichbar bleiben, muss der Nenner derselbe sein:
 *
 * <b>Gezählt werden Aufstellungstage, nicht Angriffe.</b> Das klingt falsch, ist
 * aber die einzig konsistente Wahl. Die Excel-Spalte "Ein" war das Soll und blieb
 * stehen, auch wenn jemand gar nicht angriff - sie kann "angegriffen für null
 * Sterne" und "nicht angegriffen" nicht unterscheiden. Ein Vergleich der
 * API-Daten mit der Tabelle hat das bestätigt: bei zehn Spielern wichen genau die
 * Angriffszahlen ab, die Sterne stimmten. Wer hier Angriffe zählte, bekäme für
 * dieselbe Leistung eine höhere Quote als in jedem Monat davor.
 *
 * Aus demselben Grund zählt ein Tag ohne Stern <b>immer halb</b>, egal ob jemand
 * gar nicht angriff oder ohne Erfolg. Der ganze Fehltag der Excel galt nur, wenn
 * jemand an dem Tag überhaupt nicht aufgestellt war - solche Zeilen liefert die
 * API nicht, weil sie nur die 15 Aufgestellten kennt. Erst mit einer gespeicherten
 * Aufstellung ließe sich das unterscheiden.
 */
public class F2PCwlSeasonEnd {

	/** Ab hier gibt es die volle Medaillenausschüttung. */
	private static final int STERNE_FUER_VOLLEN_BONUS = 8;

	/**
	 * Verdichtet eine Saison nach {@code f2pcwl_player_season}.
	 *
	 * @return wie viele Spielerzeilen geschrieben wurden
	 */
	public static int verdichte(String season) {
		String sql = """
				WITH gespielt AS (
				  SELECT d.player_tag, d.season, max(d.team_no) AS team_no,
				         count(*) FILTER (WHERE d.in_lineup)                AS tage,
				         sum(d.stars)                                       AS sterne,
				         count(*) FILTER (WHERE d.in_lineup AND d.stars = 0) AS ohne_stern
				  FROM f2pcwl_day_results d
				  WHERE d.season = ?
				  GROUP BY d.player_tag, d.season
				  HAVING count(*) FILTER (WHERE d.in_lineup) > 0
				),
				-- Wie viele Kampftage bisher stattgefunden haben. Ohne das wuerde eine
				-- laufende Saison so aussehen, als haetten alle die restlichen Tage
				-- auf der Bank verbracht.
				tage_gesamt AS (
				  SELECT team_no, count(DISTINCT day) AS tage FROM f2pcwl_day_results
				   WHERE season = ? GROUP BY team_no
				),
				-- Banktage: wer zum Kader gehoerte, an einem Tag aber nicht aufgestellt
				-- war. Zaehlt wie in der Excel voll. Sichtbar wird das nur mit einer
				-- gespeicherten Aufstellung - die API kennt immer nur die 15, die
				-- gespielt haben.
				bank AS (
				  SELECT r.player_tag, GREATEST(0, t.tage - COALESCE(g.tage, 0)) AS banktage
				  FROM f2pcwl_roster r
				  JOIN tage_gesamt t ON t.team_no = r.team_no
				  LEFT JOIN gespielt g ON g.player_tag = r.player_tag
				  WHERE r.season = ?
				)
				INSERT INTO f2pcwl_player_season
				  (player_tag, season, team_no, attacks, stars, hitrate, days_missed, bonus_eligible)
				SELECT g.player_tag, g.season, g.team_no, g.tage, g.sterne,
				       round(g.sterne::numeric / g.tage, 3),
				       g.ohne_stern * 0.5 + COALESCE(b.banktage, 0) * 1.0,
				       g.sterne >= ?
				FROM gespielt g
				LEFT JOIN bank b ON b.player_tag = g.player_tag
				ON CONFLICT (player_tag, season) DO UPDATE SET
				  team_no = EXCLUDED.team_no, attacks = EXCLUDED.attacks, stars = EXCLUDED.stars,
				  hitrate = EXCLUDED.hitrate, days_missed = EXCLUDED.days_missed,
				  bonus_eligible = EXCLUDED.bonus_eligible
				""";
		return DBUtil.executeUpdate(sql, season, season, season, STERNE_FUER_VOLLEN_BONUS).getSecond();
	}

	public record BonusPlatz(String playerTag, String name, int stars, double hitrate, boolean gast,
			boolean gelost) {
	}

	/**
	 * Rangliste für die Bonusvergabe eines Teams.
	 *
	 * Sortiert nach Hitrate; bei Gleichstand entscheidet das Los, so wie ihr es
	 * bisher per Glücksrad gemacht habt. Gelost markiert, wer nur durch den
	 * Zufall vor jemand anderem steht - dann ist sichtbar, dass die Reihenfolge
	 * an der Stelle beliebig ist.
	 *
	 * Gäste stehen mit drin und sind gekennzeichnet. Ob sie einen Bonus bekommen,
	 * entscheidet der Bot nicht - siehe {@link #ohneGaeste}.
	 */
	public static List<BonusPlatz> rangliste(String season, int teamNo) {
		List<BonusPlatz> roh = new ArrayList<>();
		String sql = """
				SELECT s.player_tag,
				       COALESCE(NULLIF(p.name, ''), s.player_tag) AS name,
				       s.stars, COALESCE(s.hitrate, 0) AS hitrate,
				       COALESCE(r.origin, 'ANMELDUNG') AS origin
				FROM f2pcwl_player_season s
				LEFT JOIN players p ON p.coc_tag = s.player_tag
				LEFT JOIN f2pcwl_roster r ON r.season = s.season AND r.player_tag = s.player_tag
				WHERE s.season = ? AND s.team_no = ?
				ORDER BY s.hitrate DESC NULLS LAST, s.stars DESC
				""";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			pstmt.setInt(2, teamNo);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					roh.add(new BonusPlatz(rs.getString("player_tag"), rs.getString("name"),
							rs.getInt("stars"), rs.getDouble("hitrate"),
							"GAST".equals(rs.getString("origin")), false));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error bei der Bonus-Rangliste: " + e.getMessage());
			return roh;
		}

		// Gleichstände mischen, damit nicht immer derselbe vorn steht, nur weil
		// sein Name alphabetisch früher kommt oder er zufällig zuerst in der
		// Tabelle stand. Das Los ist Teil eurer Regel, nicht ein Notbehelf.
		List<BonusPlatz> out = new ArrayList<>();
		Random rnd = new Random();
		int i = 0;
		while (i < roh.size()) {
			int j = i;
			while (j + 1 < roh.size() && Math.abs(roh.get(j + 1).hitrate() - roh.get(i).hitrate()) < 0.0005) {
				j++;
			}
			List<BonusPlatz> gruppe = new ArrayList<>(roh.subList(i, j + 1));
			boolean gelost = gruppe.size() > 1;
			if (gelost) {
				Collections.shuffle(gruppe, rnd);
			}
			for (BonusPlatz b : gruppe) {
				out.add(new BonusPlatz(b.playerTag(), b.name(), b.stars(), b.hitrate(), b.gast(), gelost));
			}
			i = j + 1;
		}
		return out;
	}

	/** Dieselbe Rangliste ohne Gäste - die Gegenprobe für eure Entscheidung. */
	public static List<BonusPlatz> ohneGaeste(List<BonusPlatz> liste) {
		List<BonusPlatz> out = new ArrayList<>();
		for (BonusPlatz b : liste) {
			if (!b.gast()) {
				out.add(b);
			}
		}
		return out;
	}

	/**
	 * Ob ein Gast überhaupt in den Bonusbereich fiele.
	 *
	 * Nur dann muss die Entweder-oder-Ansicht gezeigt werden; sonst ist die Frage
	 * gegenstandslos.
	 */
	public static boolean gastImBonusbereich(List<BonusPlatz> liste, int anzahl) {
		for (int i = 0; i < Math.min(anzahl, liste.size()); i++) {
			if (liste.get(i).gast()) {
				return true;
			}
		}
		return false;
	}
}
