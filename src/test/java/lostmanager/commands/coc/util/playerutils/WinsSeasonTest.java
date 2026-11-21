package lostmanager.commands.coc.util.playerutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.YearMonth;

import org.junit.jupiter.api.Test;

/** /wins: von Hand getippte Season darf den Befehl nicht im "denkt nach" hängen lassen. */
class WinsSeasonTest {

	@Test
	void wertDerVorschlagsliste() {
		assertEquals(YearMonth.of(2026, 11), wins.leseSeason("2026-11"));
		assertEquals(YearMonth.of(2026, 3), wins.leseSeason(" 2026-03 "));
	}

	@Test
	void anzeigetextVonHand() {
		assertEquals(YearMonth.of(2026, 11), wins.leseSeason("November 2026"));
		assertEquals(YearMonth.of(2026, 3), wins.leseSeason("märz 2026"));
		assertEquals(YearMonth.of(2026, 3), wins.leseSeason("MÄRZ 2026"));
	}

	@Test
	void unsinnErgibtNull() {
		assertNull(wins.leseSeason(null));
		assertNull(wins.leseSeason(""));
		assertNull(wins.leseSeason("November"));
		assertNull(wins.leseSeason("2026-13"));
		assertNull(wins.leseSeason("letzter Monat"));
	}
}
