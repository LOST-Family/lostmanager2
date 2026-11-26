package lostmanager.commands.coc.util.jsonutils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Laufende Ausbauten in einer Gruppe gleicher Gebäude. Vorher zählte nur der
 * erste Eintrag der Gruppe: Stand das untätige Gebäude vorn, fehlte der Timer
 * ganz; stand das bauende vorn, galt sein Timer scheinbar für alle; ein zweiter
 * laufender Ausbau auf demselben Level ging immer verloren (26.11.2026: bei 37
 * von 68 Uploads kam mindestens einer der Fälle vor).
 */
class RestzeitenTest {

	@Test
	void einzelnesGebaeudeWieBisher() {
		assertEquals("1d verbleibend", stats.restzeitenText(List.of(86_400L), 0, 1));
	}

	@Test
	void einerVonVierenBaut() {
		assertEquals("1h verbleibend (1 von 4)", stats.restzeitenText(List.of(7_200L), 3_600, 4));
	}

	@Test
	void zweiLaufendeAusbautenKuerzesterZuerst() {
		assertEquals("2h verbleibend, 1d verbleibend (2 von 3)",
				stats.restzeitenText(List.of(86_400L, 7_200L), 0, 3));
	}

	@Test
	void alleBauenDannOhneZusatz() {
		assertEquals("30m verbleibend, 1h verbleibend", stats.restzeitenText(List.of(3_600L, 1_800L), 0, 2));
	}

	@Test
	void abgelaufeneTimerFallenWeg() {
		assertEquals("", stats.restzeitenText(List.of(600L, 0L, -5L), 600, 3));
		assertEquals("10m verbleibend (1 von 3)", stats.restzeitenText(List.of(600L, 1_200L), 600, 3));
		assertEquals("", stats.restzeitenText(List.of(), 0, 2));
	}
}
