package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Kriegstage nachtragen, deren CWL-Gruppe schon weg ist (Team 1 im September
 * und Oktober 2026: Tag 7 blieb für immer auf "inWar").
 *
 * Gegen die WEGWERF-Datenbank (LEGEND_TEST_DB_URL, muss "legendtest" im Namen
 * tragen – nie die echte). Überspringt sich ohne.
 */
class F2PCwlNachtragTest {

	static boolean verbinden() {
		String url = System.getenv("LEGEND_TEST_DB_URL");
		if (url == null || !url.contains("legendtest")) {
			return false;
		}
		Connection.url = url;
		Connection.user = System.getenv("LOST_MANAGER_DB_USER");
		Connection.password = System.getenv("LOST_MANAGER_DB_PASSWORD");
		try {
			Connection.getConnection().close();
			String db = DBUtil.getValueFromSQL("SELECT current_database()", String.class);
			if (db == null || !db.contains("legendtest")) {
				throw new IllegalStateException("Test hängt an der falschen Datenbank: " + db);
			}
		} catch (java.sql.SQLException e) {
			return false;
		}
		return true;
	}

	@org.junit.jupiter.api.AfterAll
	static void trennen() throws java.sql.SQLException {
		if (System.getenv("LEGEND_TEST_DB_URL") != null) {
			for (String t : TABELLEN) {
				DBUtil.executeUpdate("DROP TABLE IF EXISTS " + t);
			}
			Connection.getConnection().close();
		}
	}

	static final List<String> TABELLEN = List.of("f2pcwl_war_tags", "f2pcwl_day_results", "f2pcwl_teams",
			"f2pcwl_status", "f2pcwl_seasons");

	@BeforeEach
	void anlegen() {
		assumeTrue(verbinden(), "keine Wegwerf-Datenbank (LEGEND_TEST_DB_URL)");
		// Aufbau wie in der echten Datenbank (pg_dump -s vom 21.11.2026).
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_war_tags (season text NOT NULL, team_no smallint NOT NULL, "
				+ "day smallint NOT NULL, war_tag text NOT NULL, end_time timestamp, state text, "
				+ "PRIMARY KEY (season, team_no, day))");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_day_results (season text NOT NULL, team_no smallint NOT NULL, "
				+ "day smallint NOT NULL, player_tag text NOT NULL, war_tag text, in_lineup boolean DEFAULT false NOT NULL, "
				+ "attacked boolean DEFAULT false NOT NULL, stars smallint DEFAULT 0 NOT NULL, destruction numeric(5,2), "
				+ "donor boolean DEFAULT false NOT NULL, recorded_at timestamp DEFAULT CURRENT_TIMESTAMP, "
				+ "PRIMARY KEY (season, team_no, day, player_tag))");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_teams (team_no smallint PRIMARY KEY, host_clan_tag text NOT NULL, "
				+ "role_id text, chat_channel_id text, plan_channel_id text, start_time time, "
				+ "size_target smallint DEFAULT 15 NOT NULL, default_soll_stars smallint DEFAULT 3 NOT NULL, "
				+ "manager_discord_id text, min_th smallint DEFAULT 1 NOT NULL, max_roster smallint DEFAULT 16 NOT NULL, "
				+ "vize_channel_id text)");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_status (team_no smallint PRIMARY KEY, last_run timestamp, "
				+ "group_state text, season text, days_seen smallint, last_error text)");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS f2pcwl_seasons (season text PRIMARY KEY, state text DEFAULT 'PLANUNG' NOT NULL, "
				+ "signup_roster varchar(255), created_at timestamp DEFAULT CURRENT_TIMESTAMP, closed_at timestamp, "
				+ "dry_run boolean DEFAULT true NOT NULL)");
		for (String t : TABELLEN) {
			DBUtil.executeUpdate("DELETE FROM " + t);
		}
	}

	private static void tag(String season, int team, int day, String warTag, String endeSql, String state) {
		DBUtil.executeUpdate("INSERT INTO f2pcwl_war_tags (season, team_no, day, war_tag, end_time, state) VALUES (?, ?, ?, ?, "
				+ endeSql + ", ?)", season, team, day, warTag, state);
	}

	@Test
	void nurAbgelaufeneUnfertigeTageDerLetztenZweiWochen() {
		tag("2026-11", 1, 5, "#FERTIG", "NOW() - interval '2 days'", "warEnded");
		tag("2026-11", 1, 6, "#OFFEN", "NOW() - interval '1 hour'", "inWar");
		tag("2026-11", 1, 7, "#LAEUFT", "NOW() + interval '3 hours'", "inWar");
		tag("2026-10", 1, 7, "#ALT", "NOW() - interval '20 days'", "inWar");
		tag("2026-11", 2, 6, "#ANDERES", "NOW() - interval '1 hour'", "inWar");
		tag("2026-11", 1, 4, "#OHNEENDE", "NULL", "preparation");

		List<F2PCwlSeason.OffenerTag> offen = F2PCwlSeason.offeneTage(1);
		assertEquals(1, offen.size(), offen.toString());
		assertEquals(new F2PCwlSeason.OffenerTag("2026-11", 6, "#OFFEN"), offen.get(0));
		assertEquals("#ANDERES", F2PCwlSeason.offeneTage(2).get(0).warTag());
		assertTrue(F2PCwlSeason.offeneTage(3).isEmpty());
	}

	/**
	 * Der ganze Weg gegen die echte API (nur lesend): ein Clan, dessen Gruppe
	 * nicht mehr abrufbar ist, und ein Krieg, der noch über seinen War-Tag
	 * abrufbar ist. Läuft nur mit F2PCWL_PROBE_CLAN und F2PCWL_PROBE_WARTAG.
	 */
	@Test
	void nachtragOhneGruppeGegenDieEchteApi() {
		String clan = System.getenv("F2PCWL_PROBE_CLAN");
		String warTag = System.getenv("F2PCWL_PROBE_WARTAG");
		assumeTrue(clan != null && warTag != null, "keine Probe-Angaben (F2PCWL_PROBE_CLAN/_WARTAG)");

		DBUtil.executeUpdate("INSERT INTO f2pcwl_teams (team_no, host_clan_tag) VALUES (1, ?)", clan);
		DBUtil.executeUpdate("INSERT INTO f2pcwl_seasons (season, state) VALUES ('2026-11', 'LAUFEND')");
		tag("2026-11", 1, 7, warTag, "NOW() - interval '1 hour'", "inWar");

		lostmanager.commands.coc.util.automation.F2PCwlRecorder.recordAll();

		String gruppe = DBUtil.getValueFromSQL("SELECT group_state FROM f2pcwl_status WHERE team_no = 1", String.class);
		assumeTrue("groupnotfound".equals(gruppe) || "notInWar".equals(gruppe),
				"Gruppe des Probe-Clans ist abrufbar (" + gruppe + ") - der Fall lässt sich gerade nicht nachstellen");
		assertEquals("warEnded", DBUtil.getValueFromSQL(
				"SELECT state FROM f2pcwl_war_tags WHERE season = '2026-11' AND team_no = 1 AND day = 7", String.class));
		Long zeilen = DBUtil.getValueFromSQL(
				"SELECT count(*) FROM f2pcwl_day_results WHERE season = '2026-11' AND team_no = 1 AND day = 7 AND in_lineup",
				Long.class);
		assertNotNull(zeilen);
		assertTrue(zeilen >= 15, "Aufstellung des Tages fehlt: " + zeilen);
		assertTrue(F2PCwlSeason.offeneTage(1).isEmpty(), "Tag gilt weiter als offen");
	}
}
