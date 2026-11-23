package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Abmeldungen: Eingaben/Anzeigen sind Berliner Zeit, gespeichert wird UTC-Wanduhr
 * (wie NOW() der Datenbank). Bis 21.11.2026 wurde die Berliner Wanduhr ungerechnet
 * gespeichert – Enden lagen eine (Sommer: zwei) Stunde(n) zu spät, Anzeigen eine zu früh.
 * Dazu: eine geplante Abmeldung (Start in der Zukunft) muss sich ansehen und beenden lassen.
 *
 * Der Datenbankteil läuft nur gegen die WEGWERF-Datenbank (LEGEND_TEST_DB_URL).
 */
class MemberSignoffZeitTest {

	@AfterAll
	static void trennen() throws java.sql.SQLException {
		if (System.getenv("LEGEND_TEST_DB_URL") != null && F2PCwlNachtragTest.verbinden()) {
			DBUtil.executeUpdate("DROP TABLE IF EXISTS member_signoffs");
			Connection.getConnection().close();
		}
	}

	@Test
	void berlinerZeitHinUndZurueck() {
		// Winter: Berlin = UTC+1
		LocalDateTime mitternacht = LocalDateTime.of(2026, 11, 23, 0, 0);
		Timestamp ts = MemberSignoff.ausBerlin(mitternacht);
		assertEquals(Instant.parse("2026-11-22T23:00:00Z"), ts.toInstant());
		assertEquals(mitternacht, MemberSignoff.inBerlin(ts));
		// Sommer: UTC+2
		Timestamp sommer = MemberSignoff.ausBerlin(LocalDateTime.of(2026, 7, 1, 12, 0));
		assertEquals(Instant.parse("2026-07-01T10:00:00Z"), sommer.toInstant());
		assertEquals("01.07.2026 um 12:00 Uhr", MemberSignoff.berlin(sommer, "dd.MM.yyyy 'um' HH:mm 'Uhr'"));
	}

	@Test
	void geplanteAbmeldungIstSichtbar() {
		assumeTrue(F2PCwlNachtragTest.verbinden(), "keine Wegwerf-Datenbank (LEGEND_TEST_DB_URL)");
		// Aufbau wie in lostapp (information_schema vom 21.11.2026)
		DBUtil.executeUpdate("CREATE TABLE IF NOT EXISTS member_signoffs (id bigserial PRIMARY KEY, player_tag text UNIQUE NOT NULL, "
				+ "start_date timestamp, end_date timestamp, reason text, created_by_discord_id text, "
				+ "created_at timestamp DEFAULT NOW(), receive_pings boolean DEFAULT false)");
		DBUtil.executeUpdate("DELETE FROM member_signoffs");
		LocalDateTime jetzt = LocalDateTime.now(MemberSignoff.BERLIN);

		// geplant: ab übermorgen, 3 Tage
		LocalDateTime start = jetzt.plusDays(2).truncatedTo(ChronoUnit.DAYS);
		assertTrue(MemberSignoff.create("#GEPLANT", MemberSignoff.ausBerlin(start), MemberSignoff.ausBerlin(start.plusDays(3)), null, "1", false));
		MemberSignoff g = new MemberSignoff("#GEPLANT");
		assertFalse(g.isActive());
		assertTrue(g.isPlanned());
		assertTrue(g.isActiveOrPlanned());
		assertFalse(MemberSignoff.isSignedOff("#GEPLANT"));
		assertEquals(start, MemberSignoff.inBerlin(g.getStartDate()));

		// laufend, ab jetzt 1 Tag: Ende liegt aus Sicht der Datenbank 24 h in der Zukunft (nicht 25)
		assertTrue(MemberSignoff.create("#LAEUFT", MemberSignoff.ausBerlin(jetzt), MemberSignoff.ausBerlin(jetzt.plusDays(1)), null, "1", false));
		MemberSignoff l = new MemberSignoff("#LAEUFT");
		assertTrue(l.isActive());
		assertFalse(l.isPlanned());
		assertTrue(MemberSignoff.isSignedOff("#LAEUFT"));
		Double stunden = DBUtil.getValueFromSQL(
				"SELECT (extract(epoch FROM end_date - NOW()) / 3600.0)::float8 FROM member_signoffs WHERE player_tag = ?", Double.class, "#LAEUFT");
		assertTrue(stunden > 23.9 && stunden <= 24.0, "Ende in " + stunden + " h");
		Double start0 = DBUtil.getValueFromSQL(
				"SELECT abs(extract(epoch FROM start_date - NOW()))::float8 FROM member_signoffs WHERE player_tag = ?", Double.class, "#LAEUFT");
		assertTrue(start0 < 60, "Start weicht " + start0 + " s von NOW() ab");

		// abgelaufen
		assertTrue(MemberSignoff.create("#ALT", MemberSignoff.ausBerlin(jetzt.minusDays(5)), MemberSignoff.ausBerlin(jetzt.minusDays(1)), null, "1", false));
		MemberSignoff a = new MemberSignoff("#ALT");
		assertFalse(a.isActive());
		assertFalse(a.isActiveOrPlanned());
	}
}
