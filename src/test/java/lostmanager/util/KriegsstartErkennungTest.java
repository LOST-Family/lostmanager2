package lostmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Kriegsstart-Erkennung: nur echte Starts, nie ein schon laufender Krieg nach einem Neustart. */
class KriegsstartErkennungTest {

	private static final long BOT_START = 1_000_000_000L;
	private static final String U = KriegsstartErkennung.UNBEKANNT;

	@Test
	void echterUebergangAusNotInWarIstStart() {
		assertTrue(KriegsstartErkennung.istKriegsstart("notInWar", "preparation", BOT_START + 5, BOT_START));
		assertTrue(KriegsstartErkennung.istKriegsstart("warEnded", "preparation", BOT_START + 5, BOT_START));
		// Auch wenn die Vorbereitung verpasst wurde (lange Pause im Lauf), war es ein Start
		assertTrue(KriegsstartErkennung.istKriegsstart("notInWar", "inWar", BOT_START - 5, BOT_START));
	}

	@Test
	void laufenderKriegNachNeustartIstKeinStart() {
		// Der Fall vom 18.11.2026: Start während API-Wartung, Zustand unbekannt, dann inWar
		assertFalse(KriegsstartErkennung.istKriegsstart(U, "inWar", BOT_START - 3_600_000, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart(U, "preparation", BOT_START - 60_000, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart(null, "inWar", BOT_START - 1, BOT_START));
	}

	@Test
	void ausUnbekanntOhneVorbereitungszeitKeinStart() {
		assertFalse(KriegsstartErkennung.istKriegsstart(U, "preparation", null, BOT_START));
	}

	@Test
	void kriegDerNachDemBotStartBeganIstStartAuchAusUnbekannt() {
		// Wartung über den Neustart hinweg, der Krieg beginnt erst danach
		assertTrue(KriegsstartErkennung.istKriegsstart(U, "preparation", BOT_START + 60_000, BOT_START));
	}

	@Test
	void innerhalbDesKriegsKeinStart() {
		assertFalse(KriegsstartErkennung.istKriegsstart("preparation", "inWar", BOT_START + 5, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart("inWar", "inWar", BOT_START + 5, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart("preparation", "preparation", BOT_START + 5, BOT_START));
	}

	@Test
	void ohneLaufendenKriegNieStart() {
		assertFalse(KriegsstartErkennung.istKriegsstart("notInWar", "notInWar", null, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart("inWar", "warEnded", null, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart(U, "notInWar", null, BOT_START));
		assertFalse(KriegsstartErkennung.istKriegsstart("notInWar", null, null, BOT_START));
	}

	@Test
	void zeitLesen() {
		assertEquals(0L, KriegsstartErkennung.zeitLesen("19700101T000000.000Z"));
		assertEquals(1_000L, KriegsstartErkennung.zeitLesen("19700101T000001.000Z"));
		assertNull(KriegsstartErkennung.zeitLesen(null));
		assertNull(KriegsstartErkennung.zeitLesen(""));
		assertNull(KriegsstartErkennung.zeitLesen("kaputt"));
	}
}
