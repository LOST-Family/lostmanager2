package lostmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/** Kriegsstart-Auslöser: ein Fehlschlag (API gedrosselt) darf das Event nicht still verlieren. */
class StartAusloeserTest {

	private static final long[] ZEITEN = { 10L, 20L, 30L };

	@Test
	void klapptSofortOhneWarten() {
		final List<Long> gewartet = new ArrayList<>();
		final AtomicInteger laeufe = new AtomicInteger();
		assertEquals(1, StartAusloeser.mitWiederholung("t", laeufe::incrementAndGet, ZEITEN, gewartet::add));
		assertEquals(1, laeufe.get());
		assertTrue(gewartet.isEmpty());
	}

	@Test
	void wiederholtNachFehlerAuchBeiNullMeldung() {
		// Der Fall vom 18.11.2026: NullPointerException ohne Meldung beim ersten Versuch
		final List<Long> gewartet = new ArrayList<>();
		final AtomicInteger laeufe = new AtomicInteger();
		final int versuche = StartAusloeser.mitWiederholung("t", () -> {
			if (laeufe.incrementAndGet() < 3) {
				throw new NullPointerException();
			}
		}, ZEITEN, gewartet::add);
		assertEquals(3, versuche);
		assertEquals(List.of(10L, 20L), gewartet);
	}

	@Test
	void gibtNachAllenVersuchenAuf() {
		final List<Long> gewartet = new ArrayList<>();
		final AtomicInteger laeufe = new AtomicInteger();
		final int versuche = StartAusloeser.mitWiederholung("t", () -> {
			laeufe.incrementAndGet();
			throw new IllegalStateException("API-Ausfall");
		}, ZEITEN, gewartet::add);
		assertEquals(0, versuche);
		assertEquals(4, laeufe.get());
		assertEquals(List.of(10L, 20L, 30L), gewartet);
	}

	@Test
	void unterbrechungBeendetOhneWeiterenVersuch() {
		final AtomicInteger laeufe = new AtomicInteger();
		final int versuche = StartAusloeser.mitWiederholung("t", () -> {
			laeufe.incrementAndGet();
			throw new IllegalStateException("x");
		}, ZEITEN, _ -> {
			throw new InterruptedException();
		});
		assertEquals(0, versuche);
		assertEquals(1, laeufe.get());
		assertTrue(Thread.interrupted(), "Unterbrechung muss am Thread stehen bleiben");
	}
}
