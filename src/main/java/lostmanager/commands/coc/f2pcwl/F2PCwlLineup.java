package lostmanager.commands.coc.f2pcwl;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lostmanager.datawrapper.F2PCwlTeam;
import lostmanager.dbutil.Connection;

/**
 * Schlägt vor, wer an einem Kriegstag spielt und wer aussetzt.
 *
 * Nötig wird das, weil ein Team mehr Leute hat als Kriegsplätze: 15 spielen, der
 * Rest sitzt aus. Wer aussetzt, darf nicht willkürlich gewählt werden, denn an
 * den Einsätzen hängt die Medaillenausschüttung.
 *
 * <b>Die Regel dahinter:</b> Jeder bekommt 20 % der Clan-Medaillen fürs
 * Mitspielen und 10 % je Stern obendrauf, bis bei 8 Sternen das Maximum erreicht
 * ist - gezählt über die ganze Saison, nicht je Krieg. Acht Sterne sind also das
 * Ziel für jeden Einzelnen.
 *
 * Daraus ergibt sich eine Zweiteilung:
 *
 * <ol>
 * <li><b>Pflichtanteil.</b> Wer seine acht Sterne noch nicht hat, braucht bei
 * seiner Sternrate noch so und so viele Tage. Bleiben weniger Tage übrig als er
 * braucht, muss er spielen - sonst verliert er Medaillen.</li>
 * <li><b>Überschuss.</b> Was danach an Plätzen bleibt, geht an die Stärksten,
 * denn die gewinnen die Kriege.</li>
 * </ol>
 *
 * Die Rechnung geht bequem auf: ein Team hat 7 × 15 = 105 Einsätze. Selbst 18
 * Leute, die je vier Tage brauchen, binden nur 72 davon.
 *
 * Wer trotz Einteilung nicht angreift, rutscht nach hinten - er verschenkt einen
 * Platz, den jemand anders gebraucht hätte.
 */
@SuppressWarnings("null")
public class F2PCwlLineup {

	/** Ab hier gibt es die volle Medaillenausschüttung. */
	private static final int STERNE_FUER_VOLLEN_BONUS = 8;

	/** Ein CWL-Monat hat sieben Kampftage. */
	private static final int KRIEGSTAGE = 7;

	/**
	 * Wenn jemand noch keine eigene Sternrate hat, wird mit dieser gerechnet -
	 * bewusst vorsichtig, damit er eher zu viele als zu wenige Tage bekommt.
	 */
	private static final double RATE_FALLBACK = 2.0;

	/**
	 * Wie schnell die laufende Saison die Historie überstimmt.
	 *
	 * Ein Spieler kann seine Strategie wechseln und plötzlich deutlich besser
	 * oder schlechter angreifen als in den Monaten davor. Wer diesen Monat
	 * dreimal drei Sterne geholt hat, gehört nicht auf die Bank, nur weil er im
	 * letzten Monat schwach war.
	 *
	 * Gemischt wird deshalb nach Beleglage: {@code gewicht = n / (n + K)}, wobei
	 * n die Angriffe dieser Saison sind. Bei K = 2 zählt nach drei Angriffen
	 * bereits 60 % die aktuelle Form, nach sechs 75 %. Vor dem ersten Angriff
	 * zählt allein die Historie - anders ginge es auch nicht.
	 */
	private static final double FORM_GLAETTUNG = 2.0;

	public record Kandidat(String playerTag, String name, int sterneBisher, int tageGespielt,
			int nichtAngegriffen, double rate, int tageNoetig, boolean muss, double score,
			int angriffeBisher, double histRate) {

		/**
		 * Sternrate dieser Saison, oder -1 wenn noch nicht angegriffen wurde.
		 */
		public double aktuelleRate() {
			return angriffeBisher > 0 ? (double) sterneBisher / angriffeBisher : -1;
		}
	}

	public record Vorschlag(List<Kandidat> spielt, List<Kandidat> sitztAus, int tag, int plaetze) {
	}

	/**
	 * Stellt den Kader eines Teams für einen Kriegstag auf.
	 *
	 * @param day der Kriegstag 1..7, für den geplant wird
	 */
	public static Vorschlag fuerTag(String season, F2PCwlTeam team, int day) {
		List<Kandidat> kader = ladeKader(season, team, day);
		int tageUebrig = Math.max(1, KRIEGSTAGE - day + 1);

		List<Kandidat> bewertet = new ArrayList<>();
		for (Kandidat k : kader) {
			// Aktuelle Form schlägt Historie, sobald genug Angriffe vorliegen.
			// Sonst würde jemand aussortiert, der diesen Monat gerade drei mal
			// drei Sterne geholt hat, nur weil der Vormonat schwach war.
			double hist = k.histRate() > 0 ? k.histRate() : RATE_FALLBACK;
			double form;
			if (k.angriffeBisher() > 0) {
				double gewicht = k.angriffeBisher() / (k.angriffeBisher() + FORM_GLAETTUNG);
				form = gewicht * k.aktuelleRate() + (1 - gewicht) * hist;
			} else {
				form = hist;
			}

			int fehlend = Math.max(0, STERNE_FUER_VOLLEN_BONUS - k.sterneBisher());
			// Für die Pflichttage zählt dieselbe Schätzung - wer gerade stark
			// angreift, braucht weniger Tage als sein Durchschnitt vermuten lässt.
			int tageNoetig = (int) Math.ceil(fehlend / Math.max(form, 0.5));
			boolean muss = tageNoetig >= tageUebrig && fehlend > 0;
			bewertet.add(new Kandidat(k.playerTag(), k.name(), k.sterneBisher(), k.tageGespielt(),
					k.nichtAngegriffen(), form, tageNoetig, muss, form,
					k.angriffeBisher(), k.histRate()));
		}

		// Erst wer sonst seine acht Sterne verpasst, dann die Stärksten. Innerhalb
		// beider Gruppen zaehlt zuerst, wer bisher am wenigsten dran war - so
		// verteilen sich die Einsaetze von selbst.
		bewertet.sort(Comparator
				.comparing(Kandidat::muss).reversed()
				.thenComparing(Comparator.comparingInt(Kandidat::nichtAngegriffen))
				.thenComparing(Comparator.comparingDouble(Kandidat::score).reversed())
				.thenComparing(Comparator.comparingInt(Kandidat::tageGespielt))
				.thenComparing(Kandidat::name));

		List<Kandidat> spielt = new ArrayList<>();
		List<Kandidat> bank = new ArrayList<>();
		for (Kandidat k : bewertet) {
			if (spielt.size() < team.getSizeTarget()) {
				spielt.add(k);
			} else {
				bank.add(k);
			}
		}
		return new Vorschlag(spielt, bank, day, team.getSizeTarget());
	}

	/**
	 * Der Kader eines Teams samt bisherigem Saisonverlauf.
	 *
	 * Steht schon eine Aufstellung in f2pcwl_roster, gilt die. Solange nicht -
	 * etwa in einer laufenden Saison, die noch von Hand eingeteilt wurde - wird
	 * genommen, wer bisher in den Kriegen dieses Teams stand.
	 */
	private static List<Kandidat> ladeKader(String season, F2PCwlTeam team, int day) {
		List<Kandidat> out = new ArrayList<>();
		String sql = """
				WITH kader AS (
				  SELECT player_tag FROM f2pcwl_roster WHERE season = ? AND team_no = ?
				  UNION
				  SELECT DISTINCT d.player_tag FROM f2pcwl_day_results d
				   WHERE d.season = ? AND d.team_no = ? AND d.in_lineup
				     AND NOT EXISTS (SELECT 1 FROM f2pcwl_roster r WHERE r.season = d.season)
				),
				verlauf AS (
				  SELECT k.player_tag,
				         COALESCE(sum(d.stars) FILTER (WHERE d.day < ?), 0) AS sterne,
				         count(d.*) FILTER (WHERE d.day < ? AND d.in_lineup) AS tage,
				         count(d.*) FILTER (WHERE d.day < ? AND d.attacked) AS angriffe,
				         count(d.*) FILTER (WHERE d.day < ? AND d.in_lineup AND NOT d.attacked) AS ausgelassen
				  FROM kader k
				  LEFT JOIN f2pcwl_day_results d
				         ON d.player_tag = k.player_tag AND d.season = ? AND d.team_no = ?
				  GROUP BY k.player_tag
				),
				historie AS (
				  SELECT player_tag, avg(hitrate) AS rate
				  FROM f2pcwl_player_season WHERE hitrate IS NOT NULL GROUP BY player_tag
				)
				SELECT v.player_tag,
				       COALESCE(NULLIF(p.name, ''), v.player_tag) AS name,
				       v.sterne, v.tage, v.angriffe, v.ausgelassen,
				       COALESCE(h.rate, 0) AS rate
				FROM verlauf v
				LEFT JOIN players p ON p.coc_tag = v.player_tag
				LEFT JOIN historie h ON h.player_tag = v.player_tag
				""";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, season);
			pstmt.setInt(2, team.getTeamNo());
			pstmt.setString(3, season);
			pstmt.setInt(4, team.getTeamNo());
			pstmt.setInt(5, day);
			pstmt.setInt(6, day);
			pstmt.setInt(7, day);
			pstmt.setInt(8, day);
			pstmt.setString(9, season);
			pstmt.setInt(10, team.getTeamNo());
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					double histRate = rs.getDouble("rate");
					out.add(new Kandidat(rs.getString("player_tag"), rs.getString("name"),
							rs.getInt("sterne"), rs.getInt("tage"), rs.getInt("ausgelassen"),
							histRate, 0, false, histRate,
							rs.getInt("angriffe"), histRate));
				}
			}
		} catch (SQLException e) {
			System.err.println("Database error beim Aufstellen: " + e.getMessage());
		}
		return out;
	}
}
