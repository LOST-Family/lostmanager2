package lostmanager.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class StichtagslaufTest {

	@Test
	void einScheiternderSpielerReisstDieAnderenNichtMit() {
		List<String> gesichert = new ArrayList<>();
		int gescheitert = Stichtagslauf.fuerAlle(List.of("#A", "#B", "#C", "#D"), tag -> {
			if (tag.equals("#B")) {
				// genau das wirft Player.getAchievementAPI, wenn die API nicht antwortet
				throw new NullPointerException("keine Antwort");
			}
			if (tag.equals("#C")) {
				throw new AssertionError("auch ein Error bricht nicht ab");
			}
			gesichert.add(tag);
		}, "Probe");
		assertEquals(2, gescheitert);
		assertEquals(List.of("#A", "#D"), gesichert);
	}

	@Test
	void ohneFehlerNullGescheiterte() {
		assertEquals(0, Stichtagslauf.fuerAlle(List.of("#A"), tag -> {
		}, "Probe"));
	}

	@Test
	void neuEingeplantWirdAuchWennDerLaufWirft() {
		AtomicInteger eingeplant = new AtomicInteger();
		Stichtagslauf.mitNeueinplanung("Probe", () -> {
			throw new IllegalStateException("Datenbank weg");
		}, eingeplant::incrementAndGet).run();
		assertEquals(1, eingeplant.get());
	}

	@Test
	void scheiterndeNeueinplanungWirftNichtNachAussen() {
		AtomicInteger gelaufen = new AtomicInteger();
		Stichtagslauf.mitNeueinplanung("Probe", gelaufen::incrementAndGet, () -> {
			throw new IllegalStateException("Pool heruntergefahren");
		}).run();
		assertTrue(gelaufen.get() == 1);
	}
}
