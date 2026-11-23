package lostmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

/** Liste der IDs ohne Eingangsbestaetigung (KLINGEL_OHNE_BESTAETIGUNG). */
class KlingelTest {

	@Test
	void idListeKommagetrenntMitLeerzeichen() {
		assertEquals(Set.of("1", "22", "333"), Klingel.liesIds(" 1,22 ,,333 "));
	}

	@Test
	void leereListeErgibtNiemanden() {
		assertTrue(Klingel.liesIds("").isEmpty());
	}

	@Test
	void ohneVariableBekommtJederDieBestaetigung() {
		if (System.getenv("KLINGEL_OHNE_BESTAETIGUNG") == null) {
			assertTrue(Klingel.bekommtBestaetigung("362260317071343630"));
			assertTrue(Klingel.bekommtBestaetigung("304360856320213002"));
		}
	}
}
