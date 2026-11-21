package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import lostmanager.commands.coc.util.automation.F2PCwlRecorder;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Die Saisonbilanz schreibt sich nach dem letzten Kriegstag selbst fort
 * (September 2026 wurde nie verdichtet, Oktober zu früh), und
 * {@code /f2pcwl start} meint ohne Angabe die nächste CWL.
 *
 * Der Datenbankteil läuft gegen die WEGWERF-Datenbank (LEGEND_TEST_DB_URL, muss
 * "legendtest" im Namen tragen – nie die echte) und überspringt sich ohne.
 */
class F2PCwlVerdichtungTest {

	static final List<String> TABELLEN = List.of("f2pcwl_player_season", "f2pcwl_roster", "f2pcwl_day_results");

	@org.junit.jupiter.api.AfterAll
	static void trennen() throws java.sql.SQLException {
		if (System.getenv("LEGEND_TEST_DB_URL") != null && F2PCwlNachtragTest.verbinden()) {
			for (String t : TABELLEN) {
				DBUtil.executeUpdate("DROP TABLE IF EXISTS " + t);
			}
			Connection.getConnection().close();
		}
	}

	@Test
	void startMeintDieNaechsteCwl() {
		// So wurde bisher eröffnet: 23.09. für Oktober, 21.10. für November.
		assertEquals("2026-10", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 9, 23)));
		assertEquals("2026-11", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 10, 21)));
		assertEquals("2026-12", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 11, 21)));
		// Während die CWL läuft, ist der laufende Monat gemeint.
		assertEquals("2026-12", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 12, 1)));
		assertEquals("2026-12", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 12, 10)));
		assertEquals("2027-01", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 12, 11)));
		assertEquals("2027-01", F2PCwlSeason.standardFuerStart(LocalDate.of(2026, 12, 31)));
	}

	private void anlegen() {
		assumeTrue(F2PCwlNachtragTest.verbinden(), "keine Wegwerf-Datenbank (LEGEND_TEST_DB_URL)");
		// Aufbau wie in der echten Datenbank (information_schema vom 21.11.2026), ohne Fremdschlüssel.
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_day_results (season text NOT NULL, team_no smallint NOT NULL, "
				+ "day smallint NOT NULL, player_tag text NOT NULL, war_tag text, in_lineup boolean DEFAULT false NOT NULL, "
				+ "attacked boolean DEFAULT false NOT NULL, stars smallint DEFAULT 0 NOT NULL, destruction numeric(5,2), "
				+ "donor boolean DEFAULT false NOT NULL, recorded_at timestamp DEFAULT CURRENT_TIMESTAMP, "
				+ "PRIMARY KEY (season, team_no, day, player_tag))");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_roster (season text NOT NULL, player_tag text NOT NULL, "
				+ "team_no smallint NOT NULL, slot smallint, origin text DEFAULT 'ANMELDUNG' NOT NULL, home_clan_tag text, "
				+ "prev_team_no smallint, soll_stars smallint, strat text, note text, PRIMARY KEY (season, player_tag))");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_player_season (player_tag text NOT NULL, season text NOT NULL, "
				+ "team_no smallint, attacks smallint DEFAULT 0 NOT NULL, stars smallint DEFAULT 0 NOT NULL, hitrate numeric, "
				+ "days_missed numeric, bonus_eligible boolean DEFAULT false NOT NULL, "
				+ "bonus_awarded boolean DEFAULT false NOT NULL, bank_days numeric DEFAULT 0 NOT NULL, "
				+ "PRIMARY KEY (player_tag, season))");
		for (String t : TABELLEN) {
			DBUtil.executeUpdate("DELETE FROM " + t);
		}
	}

	private static void ergebnis(int team, int day, String tag, boolean aufgestellt, boolean angegriffen, int sterne) {
		DBUtil.executeUpdate("INSERT INTO f2pcwl_day_results (season, team_no, day, player_tag, in_lineup, attacked, stars) "
				+ "VALUES ('2026-12', ?, ?, ?, ?, ?, ?) ON CONFLICT (season, team_no, day, player_tag) DO UPDATE SET "
				+ "in_lineup = EXCLUDED.in_lineup, attacked = EXCLUDED.attacked, stars = EXCLUDED.stars",
				team, day, tag, aufgestellt, angegriffen, sterne);
	}

	private static Long bilanzen() {
		return DBUtil.getValueFromSQL("SELECT count(*) FROM f2pcwl_player_season WHERE season = '2026-12'", Long.class);
	}

	private static int sterne(String tag) {
		Integer s = DBUtil.getValueFromSQL(
				"SELECT stars::int FROM f2pcwl_player_season WHERE season = '2026-12' AND player_tag = ?", Integer.class, tag);
		return s == null ? -1 : s;
	}

	@Test
	void verdichtetErstNachDemLetztenTagUndZiehtNach() {
		anlegen();
		// #A spielt alle sieben Tage drei Sterne, #B sechs Tage zwei Sterne und setzt Tag 7 aus,
		// #C (Team 2) hat an Tag 7 noch nicht angegriffen.
		for (int day = 1; day <= 7; day++) {
			ergebnis(1, day, "#A", true, true, 3);
			ergebnis(1, day, "#B", day < 7, day < 7, day < 7 ? 2 : 0);
			ergebnis(2, day, "#C", true, day < 7, day < 7 ? 1 : 0);
		}
		DBUtil.executeUpdate("INSERT INTO f2pcwl_roster (season, player_tag, team_no) VALUES ('2026-12', '#B', 1)");

		// Vor dem letzten Tag, bei laufendem Krieg und bei einem längst fertigen Tag: nichts.
		assertFalse(F2PCwlRecorder.verdichteNachLetztemTag("2026-12", 1, 6, false, "warEnded"));
		assertFalse(F2PCwlRecorder.verdichteNachLetztemTag("2026-12", 1, 7, false, "inWar"));
		assertFalse(F2PCwlRecorder.verdichteNachLetztemTag("2026-12", 1, 7, true, "warEnded"));
		assertEquals(0L, bilanzen());

		// Team 1 ist fertig: die ganze Saison wird geschrieben.
		assertTrue(F2PCwlRecorder.verdichteNachLetztemTag("2026-12", 1, 7, false, "warEnded"));
		assertEquals(3L, bilanzen());
		assertEquals(21, sterne("#A"));
		assertEquals(12, sterne("#B"));
		assertEquals(0, new BigDecimal("2.000").compareTo(DBUtil.getValueFromSQL(
				"SELECT hitrate FROM f2pcwl_player_season WHERE season = '2026-12' AND player_tag = '#B'", BigDecimal.class)));
		assertEquals(0, BigDecimal.ONE.compareTo(DBUtil.getValueFromSQL(
				"SELECT bank_days FROM f2pcwl_player_season WHERE season = '2026-12' AND player_tag = '#B'", BigDecimal.class)));
		assertEquals(6, sterne("#C"));

		// Der Bonus wird von Hand vermerkt und darf das Nachziehen überleben.
		DBUtil.executeUpdate("UPDATE f2pcwl_player_season SET bonus_awarded = TRUE WHERE player_tag = '#A'");

		// Team 2 wird später fertig, #C hat in letzter Minute noch drei Sterne geholt -
		// genau der Fall vom Oktober (Team 4).
		ergebnis(2, 7, "#C", true, true, 3);
		assertTrue(F2PCwlRecorder.verdichteNachLetztemTag("2026-12", 2, 7, false, "warEnded"));
		assertEquals(3L, bilanzen());
		assertEquals(9, sterne("#C"));
		assertEquals(Boolean.TRUE, DBUtil.getValueFromSQL(
				"SELECT bonus_eligible FROM f2pcwl_player_season WHERE season = '2026-12' AND player_tag = '#C'", Boolean.class));
		assertEquals(Boolean.TRUE, DBUtil.getValueFromSQL(
				"SELECT bonus_awarded FROM f2pcwl_player_season WHERE season = '2026-12' AND player_tag = '#A'", Boolean.class));
	}
}
