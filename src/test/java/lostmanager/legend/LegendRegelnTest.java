package lostmanager.legend;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lostmanager.legend.LegendRegeln.Kampf;
import lostmanager.legend.LegendRegeln.Zuordnung;

class LegendRegelnTest {

	private static Kampf angriff(int sterne, int prozent, String zeit) {
		return new Kampf(true, sterne, prozent, Instant.parse(zeit));
	}

	private static Kampf verteidigung(int sterne, int prozent, String zeit) {
		return new Kampf(false, sterne, prozent, Instant.parse(zeit));
	}

	@Test
	void legendTagWechseltUmFuenfUhrUtc() {
		assertEquals(LocalDate.of(2026, 11, 16), LegendRegeln.legendTag(Instant.parse("2026-11-17T04:59:59Z")));
		assertEquals(LocalDate.of(2026, 11, 17), LegendRegeln.legendTag(Instant.parse("2026-11-17T05:00:00Z")));
		assertEquals(Instant.parse("2026-11-17T05:00:00Z"), LegendRegeln.tagesbeginn(LocalDate.of(2026, 11, 17)));
	}

	@Test
	void punkteSindAbgerundetesZehntel() {
		assertEquals(30, LegendRegeln.punkte(309));
		assertEquals(30, LegendRegeln.punkte(301));
		assertEquals(31, LegendRegeln.punkte(311));
		assertEquals(32, LegendRegeln.punkte(320));
		assertEquals(0, LegendRegeln.punkte(-5));
	}

	@Test
	void huntIstDieLigawocheAbDemDrittenMontag() {
		// Stefan: im November 16.–22.11.
		assertEquals(Instant.parse("2026-11-16T05:00:00Z"), LegendRegeln.huntStart(YearMonth.of(2026, 11)));
		assertEquals(Instant.parse("2026-11-23T05:00:00Z"), LegendRegeln.huntEnde(YearMonth.of(2026, 11)));
		assertEquals(Instant.parse("2026-12-21T05:00:00Z"), LegendRegeln.huntStart(YearMonth.of(2026, 12)));
		assertEquals(Instant.parse("2027-01-18T05:00:00Z"), LegendRegeln.huntStart(YearMonth.of(2027, 1)));
		assertTrue(LegendRegeln.laufenderHunt(Instant.parse("2026-11-16T15:00:00Z")).isPresent());
		assertTrue(LegendRegeln.laufenderHunt(Instant.parse("2026-11-23T04:59:00Z")).isPresent());
		assertFalse(LegendRegeln.laufenderHunt(Instant.parse("2026-11-23T05:00:00Z")).isPresent());
		assertFalse(LegendRegeln.laufenderHunt(Instant.parse("2026-11-16T04:59:00Z")).isPresent());
	}

	@Test
	void huntSaisonErkennen() {
		assertTrue(LegendWertung.istHuntSaison(1794805200L)); // 16.11.2026 05:00 UTC
		assertFalse(LegendWertung.istHuntSaison(1794200400L)); // 09.11.2026
		assertFalse(LegendWertung.istHuntSaison(0));
	}

	@Test
	void ausschlussGiltNurFuerDenGenanntenTag() {
		String k = "2026-11-16=#YP2JVC882, l8cu829q ;2026-11-20=#ABC";
		assertEquals(java.util.Set.of("#YP2JVC882", "#L8CU829Q"), LegendRegeln.ausgeschlossen(LocalDate.of(2026, 11, 16), k));
		assertEquals(java.util.Set.of("#ABC"), LegendRegeln.ausgeschlossen(LocalDate.of(2026, 11, 20), k));
		assertTrue(LegendRegeln.ausgeschlossen(LocalDate.of(2026, 11, 17), k).isEmpty());
		assertTrue(LegendRegeln.ausgeschlossen(LocalDate.of(2026, 11, 16), "").isEmpty());
		assertTrue(LegendRegeln.ausgeschlossen(LocalDate.of(2026, 11, 16), "kaputt").isEmpty());
	}

	@Test
	void ausschlussMitSternGiltJedenTag() {
		String k = "*=#22Y2ULG9U, 8vpp22u2;2026-11-16=#ABC";
		assertEquals(java.util.Set.of("#22Y2ULG9U", "#8VPP22U2", "#ABC"), LegendRegeln.ausgeschlossen(LocalDate.of(2026, 11, 16), k));
		assertEquals(java.util.Set.of("#22Y2ULG9U", "#8VPP22U2"), LegendRegeln.ausgeschlossen(LocalDate.of(2026, 12, 1), k));
	}

	@Test
	void ohneBelohnungStandardregelUndKonfiguration() {
		LocalDate nov = LocalDate.of(2026, 11, 30), dez = LocalDate.of(2026, 12, 28);
		assertTrue(LegendRegeln.ohneBelohnung(nov, nov.plusDays(28), ""));
		assertFalse(LegendRegeln.ohneBelohnung(dez, dez.plusDays(28), ""));
		assertFalse(LegendRegeln.ohneBelohnung(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 30), null));
		assertTrue(LegendRegeln.ohneBelohnung(dez, dez.plusDays(28), "2026-12-28"));
		assertFalse(LegendRegeln.ohneBelohnung(nov, nov.plusDays(28), "2026-12-28"));
		assertFalse(LegendRegeln.ohneBelohnung(nov, nov.plusDays(28), "keine"));
	}

	@Test
	void reineAngriffeZaehlenVoll() {
		Zuordnung z = LegendRegeln.zuordnen(78, List.of(angriff(3, 100, "2026-11-16T12:49:00Z"),
				angriff(2, 98, "2026-11-16T12:52:00Z")), false, LegendRegeln.GROB);
		assertEquals(78, z.angriffspokale());
		assertEquals(2, z.angriffe());
		assertFalse(z.unsicher());
		assertEquals(-1, z.lernSterne());
	}

	@Test
	void einzelnerAngriffWirdStichprobe() {
		Zuordnung z = LegendRegeln.zuordnen(33, List.of(angriff(2, 93, "2026-11-16T12:49:00Z")), false,
				LegendRegeln.GROB);
		assertEquals(33, z.angriffspokale());
		assertEquals(2, z.lernSterne());
		assertEquals(93, z.lernProzent());
		assertEquals(33, z.lernPokale());
	}

	@Test
	void verteidigungZaehltNie() {
		// Legend I: Verteidigung kostet
		Zuordnung z = LegendRegeln.zuordnen(-16, List.of(verteidigung(2, 76, "2026-11-16T12:49:00Z")), false,
				LegendRegeln.GROB);
		assertEquals(0, z.angriffspokale());
		assertEquals(16, z.lernPokale()); // so viel hat der Angreifer bekommen
		// Legend II: Verteidigung bringt dem Verteidiger 40 minus Angreiferanteil
		z = LegendRegeln.zuordnen(9, List.of(verteidigung(2, 90, "2026-11-16T12:49:00Z")), true, LegendRegeln.GROB);
		assertEquals(0, z.angriffspokale());
		assertEquals(31, z.lernPokale());
	}

	@Test
	void gemischtesFensterWirdGeschaetztUndMarkiert() {
		LegendRegeln.Schaetzer fest = (ranked, sterne, prozent) -> 30;
		// Legend II: +40 Angriff, +10 Verteidigung (Angreifer bekam 30) = +50
		Zuordnung z = LegendRegeln.zuordnen(50, List.of(angriff(3, 100, "2026-11-16T12:49:00Z"),
				verteidigung(2, 95, "2026-11-16T12:50:00Z")), true, fest);
		assertEquals(40, z.angriffspokale());
		assertTrue(z.unsicher());
		// Legend I: +40 Angriff, -30 Verteidigung = +10
		z = LegendRegeln.zuordnen(10, List.of(angriff(3, 100, "2026-11-16T12:49:00Z"),
				verteidigung(2, 95, "2026-11-16T12:50:00Z")), false, fest);
		assertEquals(40, z.angriffspokale());
	}

	@Test
	void unplausibleWerteWerdenBegrenzt() {
		Zuordnung z = LegendRegeln.zuordnen(500, List.of(angriff(3, 100, "2026-11-16T12:49:00Z")), false,
				LegendRegeln.GROB);
		assertEquals(40, z.angriffspokale());
		assertTrue(z.unsicher());
		z = LegendRegeln.zuordnen(-20, List.of(angriff(0, 30, "2026-11-16T12:49:00Z")), false, LegendRegeln.GROB);
		assertEquals(0, z.angriffspokale());
	}

	@Test
	void angriffeUeberDenTageswechselWerdenAufgeteilt() {
		LegendRegeln.Schaetzer fest = (ranked, sterne, prozent) -> 40;
		Map<LocalDate, Integer> t = LegendRegeln.aufTageVerteilen(80,
				List.of(angriff(3, 100, "2026-11-17T04:58:00Z"), angriff(3, 100, "2026-11-17T05:02:00Z")), false,
				fest);
		assertEquals(40, t.get(LocalDate.of(2026, 11, 16)));
		assertEquals(40, t.get(LocalDate.of(2026, 11, 17)));
		t = LegendRegeln.aufTageVerteilen(37, List.of(angriff(3, 100, "2026-11-16T20:00:00Z")), false, fest);
		assertEquals(Map.of(LocalDate.of(2026, 11, 16), 37), t);
	}
}
