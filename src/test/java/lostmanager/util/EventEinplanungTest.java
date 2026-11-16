package lostmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EventEinplanungTest {

	@Test
	void verworfeneAufgabeLaeuftNicht() throws Exception {
		EventEinplanung e = new EventEinplanung();
		var pool = Executors.newScheduledThreadPool(1);
		AtomicInteger gelaufen = new AtomicInteger();
		var eintrag = e.einplanen(7, 1000L);
		eintrag.setFuture(pool.schedule(e.aufgabe(eintrag, gelaufen::incrementAndGet), 200, TimeUnit.MILLISECONDS));

		var ergebnis = e.verwerfen(7);
		assertEquals(EventEinplanung.Zustand.VERWORFEN, ergebnis.zustand());
		assertEquals(1000L, ergebnis.feuerzeit());

		Thread.sleep(400);
		assertEquals(0, gelaufen.get());
		assertEquals(EventEinplanung.Zustand.NICHT_EINGEPLANT, e.verwerfen(7).zustand());
		pool.shutdownNow();
	}

	@Test
	void laufenderDurchgangWirdNichtAngefasst() throws Exception {
		EventEinplanung e = new EventEinplanung();
		var pool = Executors.newScheduledThreadPool(1);
		CountDownLatch gestartet = new CountDownLatch(1);
		CountDownLatch weiter = new CountDownLatch(1);
		AtomicInteger fertig = new AtomicInteger();
		var eintrag = e.einplanen(8, 5L);
		eintrag.setFuture(pool.submit(e.aufgabe(eintrag, () -> {
			gestartet.countDown();
			try {
				weiter.await();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			fertig.incrementAndGet();
		})));

		assertTrue(gestartet.await(2, TimeUnit.SECONDS));
		assertTrue(e.laeuft(8));
		assertEquals(EventEinplanung.Zustand.LAEUFT, e.verwerfen(8).zustand());
		weiter.countDown();
		pool.shutdown();
		assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
		// Nicht abgebrochen, sondern zu Ende gelaufen
		assertEquals(1, fertig.get());
		assertFalse(e.laeuft(8));
		assertEquals(EventEinplanung.Zustand.NICHT_EINGEPLANT, e.verwerfen(8).zustand());
	}

	@Test
	void neueEinplanungErsetztAlte() throws Exception {
		EventEinplanung e = new EventEinplanung();
		var pool = Executors.newScheduledThreadPool(2);
		AtomicInteger alt = new AtomicInteger();
		AtomicInteger neu = new AtomicInteger();
		var a = e.einplanen(9, 1L);
		pool.schedule(e.aufgabe(a, alt::incrementAndGet), 100, TimeUnit.MILLISECONDS);
		var b = e.einplanen(9, 2L);
		pool.schedule(e.aufgabe(b, neu::incrementAndGet), 100, TimeUnit.MILLISECONDS);
		pool.shutdown();
		assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
		assertEquals(0, alt.get(), "die ersetzte Einplanung darf nicht feuern - sonst doppelt");
		assertEquals(1, neu.get());
	}

	/**
	 * Das Rennen, um das es geht: Verwerfen genau im Moment der Fälligkeit. Es
	 * darf nie beides passieren (gelaufen und als verworfen gemeldet) und nie
	 * keins von beidem (weder gelaufen noch als laufend/verworfen erkannt).
	 */
	@Test
	void rennenZwischenFeuernUndVerwerfen() throws Exception {
		var pool = Executors.newScheduledThreadPool(4);
		for (int i = 0; i < 3000; i++) {
			EventEinplanung e = new EventEinplanung();
			AtomicInteger gelaufen = new AtomicInteger();
			CountDownLatch ende = new CountDownLatch(1);
			var eintrag = e.einplanen(1, 0L);
			pool.execute(e.aufgabe(eintrag, gelaufen::incrementAndGet));
			EventEinplanung.Zustand z = e.verwerfen(1).zustand();
			// auf das Ende der Aufgabe warten
			pool.execute(ende::countDown);
			assertTrue(ende.await(2, TimeUnit.SECONDS));
			Thread.sleep(0, 1000);
			for (int w = 0; w < 100 && e.laeuft(1); w++) {
				Thread.sleep(1);
			}
			if (z == EventEinplanung.Zustand.VERWORFEN) {
				assertEquals(0, gelaufen.get(), "verworfen, aber trotzdem gelaufen (Durchgang " + i + ")");
			} else {
				assertEquals(1, gelaufen.get(), "nicht verworfen, aber auch nicht gelaufen (Durchgang " + i + ")");
			}
		}
		pool.shutdownNow();
	}

	/** So wie Bot.restartAllEventsInternal den Pool wegwirft. */
	@Test
	void neuaufbauVergisstAusstehendeLaufendeBleiben() throws Exception {
		EventEinplanung e = new EventEinplanung();
		var pool = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
		AtomicInteger ausstehend = new AtomicInteger();
		CountDownLatch gestartet = new CountDownLatch(1);
		CountDownLatch weiter = new CountDownLatch(1);
		var a = e.einplanen(1, 1L);
		pool.schedule(e.aufgabe(a, ausstehend::incrementAndGet), 300, TimeUnit.MILLISECONDS);
		var b = e.einplanen(2, 1L);
		pool.submit(e.aufgabe(b, () -> {
			gestartet.countDown();
			try {
				weiter.await();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}));
		assertTrue(gestartet.await(2, TimeUnit.SECONDS));

		pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		pool.shutdown();
		e.allesVergessen();

		assertNull(e.eingeplant(1));
		assertTrue(e.laeuft(2), "der laufende Durchgang bleibt vermerkt");
		assertEquals(EventEinplanung.Zustand.LAEUFT, e.verwerfen(2).zustand());
		weiter.countDown();
		assertTrue(pool.awaitTermination(2, TimeUnit.SECONDS));
		assertEquals(0, ausstehend.get());
		assertFalse(e.laeuft(2));
	}
}
