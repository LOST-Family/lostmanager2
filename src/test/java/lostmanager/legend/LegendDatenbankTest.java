package lostmanager.legend;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Gegen eine WEGWERF-Datenbank (LEGEND_TEST_DB_URL, muss "legendtest" im Namen
 * tragen – nie die echte). Überspringt sich ohne.
 *
 * Anlegen: sudo -u postgres createdb -O $LOST_MANAGER_DB_USER lostmanager_legendtest
 */
class LegendDatenbankTest {

	static final long SAISON = 1793595600L; // 02.11.2026 05:00 UTC
	static final LocalDate T16 = LocalDate.of(2026, 11, 16), T17 = LocalDate.of(2026, 11, 17);

	static boolean verbinden() {
		String url = System.getenv("LEGEND_TEST_DB_URL");
		if (url == null || !url.contains("legendtest")) {
			return false;
		}
		Connection.url = url;
		Connection.user = System.getenv("LOST_MANAGER_DB_USER");
		Connection.password = System.getenv("LOST_MANAGER_DB_PASSWORD");
		// Connection hält EINE Verbindung statisch offen: ein anderer Test der Suite
		// kann sie schon zur echten Datenbank geöffnet haben. Schließen, damit
		// getConnection() mit der Wegwerf-Adresse neu verbindet – und prüfen.
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

	/** Verbindung schließen, damit die folgenden Tests nicht an der Wegwerf-DB hängen. */
	@org.junit.jupiter.api.AfterAll
	static void trennen() throws java.sql.SQLException {
		if (System.getenv("LEGEND_TEST_DB_URL") != null) {
			Connection.getConnection().close();
		}
	}

	@BeforeEach
	void leeren() {
		assumeTrue(verbinden(), "keine Wegwerf-Datenbank (LEGEND_TEST_DB_URL)");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS players (coc_tag TEXT PRIMARY KEY, discord_id TEXT, name TEXT, townhall INT)");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS clans (tag TEXT PRIMARY KEY, name TEXT, index BIGINT, badgeurl TEXT, description TEXT)");
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS clan_members (clan_tag TEXT, player_tag TEXT PRIMARY KEY, clan_role TEXT, added_by_discord_id TEXT, joined_at TIMESTAMP)");
		LegendWertung.tabellen();
		for (String t : List.of("legend_spieler", "legend_beobachtung", "legend_tageswert", "legend_stichprobe",
				"legend_korrektur", "legend_meldung", "trophyhunt_sieger", "players", "clans", "clan_members")) {
			DBUtil.executeUpdate("DELETE FROM " + t);
		}
	}

	@Test
	void buchenAddiertJeTag() {
		LegendWertung.buchen("#A", "A", LegendRegeln.LIGA_1, SAISON, Map.of(T16, 40), 1, 0, false);
		LegendWertung.buchen("#A", "A", LegendRegeln.LIGA_1, SAISON, Map.of(T16, 33), 1, 1, false);
		LegendWertung.buchen("#A", "A", LegendRegeln.LIGA_1, SAISON, Map.of(T16, 0), 0, 0, true);
		assertEquals(73, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#A'", Integer.class));
		assertEquals(2, DBUtil.getValueFromSQL("SELECT angriffe FROM legend_tageswert WHERE player_tag='#A'", Integer.class));
		assertEquals(Boolean.TRUE, DBUtil.getValueFromSQL("SELECT unvollstaendig FROM legend_tageswert WHERE player_tag='#A'", Boolean.class));
	}

	@Test
	void korrekturBleibtUndLegendEinsHoechstens320() {
		LegendWertung.buchen("#K", "K", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 200), 5, 0, false);
		DBUtil.executeUpdate("UPDATE legend_tageswert SET angriffspokale = 291, quelle = 'korrektur' WHERE player_tag = '#K'");
		LegendWertung.buchen("#K", "K", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 40), 1, 0, true);
		assertEquals(291, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#K'", Integer.class));
		assertEquals(Boolean.FALSE, DBUtil.getValueFromSQL("SELECT unvollstaendig FROM legend_tageswert WHERE player_tag='#K'", Boolean.class));
		LegendWertung.buchen("#M", "M", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 300), 7, 0, false);
		LegendWertung.buchen("#M", "M", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 40), 1, 0, false);
		assertEquals(320, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#M'", Integer.class));
		// Legend II ohne Tagesdeckel
		LegendWertung.buchen("#H", "H", LegendRegeln.LIGA_2, SAISON, Map.of(T17, 300), 8, 0, false);
		LegendWertung.buchen("#H", "H", LegendRegeln.LIGA_2, SAISON, Map.of(T17, 300), 8, 0, false);
		assertEquals(600, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#H'", Integer.class));
	}

	@Test
	void erstsichtungSetztMindestensUndAddiertNicht() {
		LegendWertung.mindestens("#E", "E", LegendRegeln.LIGA_1, SAISON, T17, 234, 8, false);
		LegendWertung.mindestens("#E", "E", LegendRegeln.LIGA_1, SAISON, T17, 234, 8, false);
		assertEquals(234, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#E'", Integer.class));
		assertEquals(8, DBUtil.getValueFromSQL("SELECT angriffe FROM legend_tageswert WHERE player_tag='#E'", Integer.class));
		LegendWertung.mindestens("#E", "E", LegendRegeln.LIGA_1, SAISON, T17, 200, 7, false);
		assertEquals(234, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#E'", Integer.class));
		DBUtil.executeUpdate("UPDATE legend_tageswert SET angriffspokale = 225, quelle = 'korrektur' WHERE player_tag = '#E'");
		LegendWertung.mindestens("#E", "E", LegendRegeln.LIGA_1, SAISON, T17, 300, 8, true);
		assertEquals(225, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#E'", Integer.class));
		assertEquals(Boolean.FALSE, DBUtil.getValueFromSQL("SELECT unvollstaendig FROM legend_tageswert WHERE player_tag='#E'", Boolean.class));
	}

	@Test
	void perfectDayRanglisteMitStartstandUndGleichstandsregel() {
		// A: 16.11. 300, 17.11. 320 (Perfect Day) -> 30 + 32 = 62
		LegendWertung.buchen("#A", "Anna", LegendRegeln.LIGA_1, SAISON, Map.of(T16, 300), 8, 0, false);
		LegendWertung.buchen("#A", "Anna", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 320), 8, 0, false);
		// B: 16.11. 319, 17.11. 309 -> 31 + 30 = 61, plus Startstand 1 -> 62, aber kein Perfect Day
		LegendWertung.buchen("#B", "Bert", LegendRegeln.LIGA_1, SAISON, Map.of(T16, 319), 8, 0, false);
		LegendWertung.buchen("#B", "Bert", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 309), 8, 0, true);
		DBUtil.executeUpdate("INSERT INTO legend_korrektur (wertung, schluessel, player_tag, name, wert, grund) VALUES "
				+ "('perfectday', ?, '#B', 'Bert', 1, 'Test')", String.valueOf(SAISON));
		// C: nur Startstand (abgestiegen) -> 50
		DBUtil.executeUpdate("INSERT INTO legend_korrektur (wertung, schluessel, player_tag, name, wert, grund) VALUES "
				+ "('perfectday', ?, '#C', 'Carl', 50, 'Startstand')", String.valueOf(SAISON));
		// andere Saison zählt nicht
		LegendWertung.buchen("#A", "Anna", LegendRegeln.LIGA_1, SAISON - 1, Map.of(LocalDate.of(2026, 11, 15), 320), 8, 0, false);

		// Nur mit Discord verknüpfte Spieler zählen (Stefan 18.11.2026); #D (Boshi-Fall)
		// hat Pokale, aber keine Verknüpfung, #E einen Eintrag ohne discord_id.
		DBUtil.executeUpdate("INSERT INTO players (coc_tag, discord_id, name) VALUES ('#A', '1', 'Anna'), ('#B', '2', 'Bert'), "
				+ "('#C', '3', 'Carl'), ('#E', NULL, 'Emil')");
		LegendWertung.buchen("#D", "Dora", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 320), 8, 0, false);
		LegendWertung.buchen("#E", "Emil", LegendRegeln.LIGA_1, SAISON, Map.of(T17, 320), 8, 0, false);
		DBUtil.executeUpdate("INSERT INTO legend_korrektur (wertung, schluessel, player_tag, name, wert, grund) VALUES "
				+ "('perfectday', ?, '#D', 'Dora', 5, 'Test')", String.valueOf(SAISON));

		List<LegendTexte.PdZeile> z = LegendWertung.perfectDayRangliste(SAISON, T17);
		assertEquals(3, z.size());
		assertEquals(new LegendTexte.PdZeile("Anna", 62, 32, false), z.get(0)); // mehr Perfect Days
		assertEquals(new LegendTexte.PdZeile("Bert", 62, 30, true), z.get(1));
		assertEquals(new LegendTexte.PdZeile("Carl", 50, 0, false), z.get(2));

		// Stand vom 16.11.: der 17.11. zählt noch nicht
		z = LegendWertung.perfectDayRangliste(SAISON, T16);
		assertEquals("Carl", z.get(0).name());
		assertEquals(new LegendTexte.PdZeile("Bert", 32, 31, false), z.get(1));

		// Ausschluss (PERFECTDAY_AUSSCHLUSS) gilt nur im Post des genannten Tages
		String raus = "2026-11-16=#A,#C";
		z = LegendWertung.perfectDayRangliste(SAISON, T16, raus);
		assertEquals(List.of(new LegendTexte.PdZeile("Bert", 32, 31, false)), z);
		z = LegendWertung.perfectDayRangliste(SAISON, T17, raus);
		assertEquals(3, z.size());
		assertEquals(new LegendTexte.PdZeile("Anna", 62, 32, false), z.get(0));

		// Dauerhafter Ausschluss (*=) gilt an jedem Tag
		z = LegendWertung.perfectDayRangliste(SAISON, T17, "*=#C");
		assertEquals(List.of("Anna", "Bert"), z.stream().map(LegendTexte.PdZeile::name).toList());
		z = LegendWertung.perfectDayRangliste(SAISON, T16, "*=#C");
		assertEquals(List.of("Bert", "Anna"), z.stream().map(LegendTexte.PdZeile::name).toList());
	}

	@Test
	void trophyHuntMitVormonatssperreAlsPerson() {
		YearMonth nov = YearMonth.of(2026, 11);
		DBUtil.executeUpdate("INSERT INTO players (coc_tag, discord_id, name) VALUES ('#ALT2', '111', 'Alt Zweitaccount')");
		DBUtil.executeUpdate("INSERT INTO trophyhunt_sieger (monat, player_tag, discord_id, name, pokale) VALUES "
				+ "('2026-10', '#ALT1', '111', 'Alt', 1150)");
		LegendWertung.buchen("#ALT2", "Alt Zweitaccount", LegendRegeln.LIGA_2, 1794805200L, Map.of(T16, 600, T17, 600), 30, 0, false);
		LegendWertung.buchen("#X", "Xaver", LegendRegeln.LIGA_2, 1794805200L, Map.of(LocalDate.of(2026, 11, 22), 1100), 30, 0, false);
		LegendWertung.buchen("#Y", "Yara", LegendRegeln.LIGA_2, 1794805200L, Map.of(T17, 1000), 30, 0, false);
		// außerhalb der Woche und Verteidigungs-Nullzeilen zählen nicht
		LegendWertung.buchen("#Y", "Yara", LegendRegeln.LIGA_2, 1794805200L, Map.of(LocalDate.of(2026, 11, 23), 500), 10, 0, false);
		LegendWertung.buchen("#Z", "Zoe", LegendRegeln.LIGA_2, 1794805200L, Map.of(T17, 0), 0, 0, true);
		DBUtil.executeUpdate("INSERT INTO legend_korrektur (wertung, schluessel, player_tag, name, wert, grund) VALUES "
				+ "('trophyhunt', '2026-11', '#Y', 'Yara', 50, 'Screenshot')");

		LegendWertung.HuntErgebnis e = LegendWertung.huntErgebnis(nov);
		assertEquals(3, e.zeilen().size());
		assertEquals(new LegendTexte.HuntZeile("Alt Zweitaccount", 1200, true, false), e.zeilen().get(0));
		assertEquals(new LegendTexte.HuntZeile("Xaver", 1100, false, false), e.zeilen().get(1));
		assertEquals(new LegendTexte.HuntZeile("Yara", 1050, false, false), e.zeilen().get(2));
		assertEquals(List.of(1), e.sieger());
	}

	@Test
	void meldungNurEinmal() {
		assertFalse(LegendWertung.gemeldet("perfectday", "2026-11-16"));
		DBUtil.executeUpdate("INSERT INTO legend_meldung (art, schluessel) VALUES ('perfectday', '2026-11-16')");
		assertTrue(LegendWertung.gemeldet("perfectday", "2026-11-16"));
	}

	@Test
	void rollenBerechtigteNurLegendZweiMitVerknuepfung() {
		DBUtil.executeUpdate("INSERT INTO players (coc_tag, discord_id, name) VALUES ('#L2', '1', 'a'), ('#L1', '2', 'b'), ('#L2B', '1', 'c')");
		DBUtil.executeUpdate("INSERT INTO legend_spieler (player_tag, name, liga, gesehen) VALUES "
				+ "('#L2', 'a', 'Legend League II', now()), ('#L1', 'b', 'Legend League I', now()), "
				+ "('#L2B', 'c', 'Legend League II', now()), ('#OHNE', 'd', 'Legend League II', now())");
		assertEquals(java.util.Set.of("1"), LegendWertung.trophyhuntBerechtigte());
	}

	@Test
	void legendZweiStandUeberlebtNeustartUndErsteSichtungZaehltNichtDoppelt() {
		long woche = 1797829200L; // 21.12.2026 05:00 UTC
		Instant t = Instant.parse("2026-12-21T09:00:00Z");
		LocalDate t21 = LocalDate.of(2026, 12, 21);
		List<LegendRegeln.Zweikampf> log = new java.util.ArrayList<>(List.of(LegendZweiTest.a("2026-12-21T08:00:00Z", 3, 100),
				LegendZweiTest.v("2026-12-21T08:10:00Z", 1, 50), LegendZweiTest.a("2026-12-21T08:20:00Z", 2, 80)));
		String sql = "SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#Z2'";
		// Erste Sichtung: 40 + 30 + 26 = 96, der Pokalstand zeigt den letzten Angriff noch nicht
		LegendWertung.legendZweiBeobachten("#Z2", "Zwei", LegendRegeln.LIGA_2, woche, 70, t,
				new LegendRegeln.ZweiStand(0, 0, new java.util.HashSet<>(), null), true, () -> log);
		assertEquals(66, DBUtil.getValueFromSQL(sql, Integer.class));
		assertEquals(2, DBUtil.getValueFromSQL("SELECT angriffe FROM legend_tageswert WHERE player_tag='#Z2'", Integer.class));
		// Stand aus der Datenbank (wie nach einem Neustart): Schlüssel, Rest und offen_seit sind da
		LegendWertung.Beobachtung b = LegendWertung.ladeBeobachtung("#Z2");
		LegendRegeln.ZweiStand stand = LegendWertung.ladeZweiStand("#Z2", b);
		assertEquals(70, stand.pokale());
		assertEquals(-26, stand.rest());
		assertEquals(3, stand.gesehen().size());
		assertNotNull(stand.offenSeit());
		// Pokalstand zieht nach, dazu ein neuer Angriff: nur der neue wird gebucht
		log.add(LegendZweiTest.a("2026-12-21T09:05:00Z", 3, 100));
		LegendWertung.legendZweiBeobachten("#Z2", "Zwei", LegendRegeln.LIGA_2, woche, 136, t.plusSeconds(600), stand, false,
				() -> log);
		assertEquals(106, DBUtil.getValueFromSQL(sql, Integer.class));
		b = LegendWertung.ladeBeobachtung("#Z2");
		stand = LegendWertung.ladeZweiStand("#Z2", b);
		assertEquals(0, stand.rest());
		assertNull(stand.offenSeit());
		// Ohne Änderung: kein Protokollabruf, nichts gebucht, Schlüssel bleiben
		LegendWertung.legendZweiBeobachten("#Z2", "Zwei", LegendRegeln.LIGA_2, woche, 136, t.plusSeconds(630), stand, false,
				() -> fail("kein Abruf ohne Änderung"));
		assertEquals(4, LegendWertung.ladeZweiStand("#Z2", LegendWertung.ladeBeobachtung("#Z2")).gesehen().size());
		// Noch einmal „erstmals“ gesehen (war länger nicht abrufbar): gesetzt, nicht addiert
		LegendWertung.legendZweiBeobachten("#Z2", "Zwei", LegendRegeln.LIGA_2, woche, 136, t.plusSeconds(900),
				new LegendRegeln.ZweiStand(0, 0, new java.util.HashSet<>(), null), true, () -> log);
		assertEquals(106, DBUtil.getValueFromSQL(sql, Integer.class));
		assertEquals(3, DBUtil.getValueFromSQL("SELECT angriffe FROM legend_tageswert WHERE player_tag='#Z2'", Integer.class));
		assertEquals(Boolean.FALSE, DBUtil.getValueFromSQL("SELECT unvollstaendig FROM legend_tageswert WHERE player_tag='#Z2'", Boolean.class));
		assertEquals(t21, DBUtil.getValueFromSQL("SELECT legend_tag FROM legend_tageswert WHERE player_tag='#Z2'", java.sql.Date.class).toLocalDate());
	}

	@Test
	void legendZweiWochenbeginnBeiNullPokalenVerliertDenErstenAngriffNicht() {
		long woche = 1797829200L;
		Instant t = Instant.parse("2026-12-21T05:00:30Z");
		// Wochenbeginn: 0 Pokale, im Protokoll nur die Vorwoche – kein Abruf nötig
		LegendWertung.legendZweiBeobachten("#W", "Woche", LegendRegeln.LIGA_2, woche, 0, t,
				new LegendRegeln.ZweiStand(0, 0, new java.util.HashSet<>(), null), true, () -> fail("kein Abruf bei 0 Pokalen"));
		LegendRegeln.ZweiStand stand = LegendWertung.ladeZweiStand("#W", LegendWertung.ladeBeobachtung("#W"));
		assertNotNull(stand.gesehen(), "leere Schlüsselmenge, nicht „altes Verfahren“");
		List<LegendRegeln.Zweikampf> log = List.of(LegendZweiTest.a("2026-12-20T20:00:00Z", 3, 100),
				LegendZweiTest.a("2026-12-21T05:03:00Z", 3, 100));
		LegendWertung.legendZweiBeobachten("#W", "Woche", LegendRegeln.LIGA_2, woche, 40, t.plusSeconds(300), stand, false,
				() -> log);
		assertEquals(40, DBUtil.getValueFromSQL("SELECT angriffspokale FROM legend_tageswert WHERE player_tag='#W'", Integer.class));
		assertEquals(Boolean.FALSE, DBUtil.getValueFromSQL("SELECT unvollstaendig FROM legend_tageswert WHERE player_tag='#W'", Boolean.class));
	}

	/**
	 * Trockenlauf gegen die echte API (nur lesend), solange eine Hunt-Woche läuft:
	 * LEGEND_LIVE_MINUTEN=30 LEGEND_LIVE_TAGS=#A,#B … Beobachtet die Spieler im
	 * 30-s-Takt in der Wegwerf-Datenbank; das Journal des Laufs zeigt, ob jede
	 * Pokaländerung durch Kämpfe erklärt wurde („ohne passenden Kampf“).
	 */
	@Test
	void legendZweiTrockenlaufGegenEchteApi() throws Exception {
		String minuten = System.getenv("LEGEND_LIVE_MINUTEN"), tags = System.getenv("LEGEND_LIVE_TAGS");
		assumeTrue(minuten != null && tags != null, "kein Trockenlauf verlangt");
		assumeTrue(LegendRegeln.laufenderHunt(Instant.now()).isPresent(), "gerade keine Hunt-Woche");
		Instant ende = Instant.now().plusSeconds(Long.parseLong(minuten) * 60);
		int runden = 0;
		while (Instant.now().isBefore(ende)) {
			Instant jetzt = Instant.now();
			for (String tag : tags.split(",")) {
				LegendWertung.beobachten(tag.trim(), jetzt, LegendRegeln.GROB);
			}
			if (runden++ % 10 == 0) {
				System.out.println("Trockenlauf " + Instant.now() + ": offen "
						+ DBUtil.getValueFromSQL("SELECT count(*) FROM legend_beobachtung WHERE offen_seit IS NOT NULL", Long.class)
						+ ", Angriffe gebucht " + DBUtil.getValueFromSQL("SELECT COALESCE(SUM(angriffe),0) FROM legend_tageswert", Long.class));
			}
			Thread.sleep(30_000);
		}
		try (java.sql.Statement st = Connection.getConnection().createStatement();
				java.sql.ResultSet rs = st.executeQuery("SELECT b.player_tag, b.pokale, b.topf_pokale, b.offen_seit, "
						+ "COALESCE((SELECT SUM(angriffspokale) FROM legend_tageswert t WHERE t.player_tag = b.player_tag),0), "
						+ "COALESCE((SELECT SUM(angriffe) FROM legend_tageswert t WHERE t.player_tag = b.player_tag),0), "
						+ "COALESCE((SELECT BOOL_OR(unvollstaendig) FROM legend_tageswert t WHERE t.player_tag = b.player_tag),FALSE) "
						+ "FROM legend_beobachtung b ORDER BY 1")) {
			while (rs.next()) {
				System.out.println("ENDE " + rs.getString(1) + " pokale " + rs.getInt(2) + " rest " + rs.getInt(3) + " offen "
						+ rs.getTimestamp(4) + " gebucht " + rs.getInt(5) + " (" + rs.getInt(6) + " A) unvoll " + rs.getBoolean(7));
				// Jeder gemerkte Angriff genau einmal gebucht (nichts doppelt, nichts vergessen)
				long gemerkt = LegendWertung.ladeZweiStand(rs.getString(1), LegendWertung.ladeBeobachtung(rs.getString(1)))
						.gesehen().stream().filter(k -> k.contains("|A|")).count();
				assertEquals(gemerkt, rs.getInt(6), "Angriffe von " + rs.getString(1));
			}
		}
	}

	@Test
	void ersteSichtungUndLaufGegenEchteApi() {
		assumeTrue(System.getenv("API_UMSCHALTER") != null || System.getenv("LOST_MANAGER_API_KEY") != null,
				"keine CoC-API konfiguriert");
		DBUtil.executeUpdate("INSERT INTO clans (tag, name, index) VALUES ('#2YUPV0UYC', 'LOST 3', 3)");
		Instant jetzt = Instant.now();
		LegendWertung.suche(jetzt);
		Long legend1 = DBUtil.getValueFromSQL("SELECT count(*) FROM legend_spieler WHERE liga = 'Legend League I'", Long.class);
		assertTrue(legend1 > 0, "LOST 3 hat Legend-I-Spieler");
		List<String[]> ziele = LegendWertung.ziele(jetzt);
		assertFalse(ziele.isEmpty());
		LegendRegeln.Schaetzer s = LegendWertung.ladeSchaetzer();
		for (String[] z : ziele) {
			LegendWertung.beobachten(z[0], jetzt, s);
		}
		Long beobachtet = DBUtil.getValueFromSQL("SELECT count(*) FROM legend_beobachtung", Long.class);
		assertEquals(ziele.size(), beobachtet.intValue(), "jeder Zielspieler hat eine Grundlage");
		// zweiter Durchlauf darf nichts kaputt machen
		for (String[] z : ziele) {
			LegendWertung.beobachten(z[0], Instant.now(), s);
		}
		System.out.println("Legend-Probe: " + ziele.size() + " Ziele, Tageswerte: "
				+ DBUtil.getValueFromSQL("SELECT count(*) FROM legend_tageswert", Long.class));
	}
}
