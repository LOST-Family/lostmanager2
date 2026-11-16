package lostmanager.apiutil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

/** Die Pause darf nie sperren, solange die API antwortet, und muss von selbst wieder aufgehen. */
class ApiPauseTest {

	private final AtomicLong uhr = new AtomicLong(1_000_000);
	private final ApiPause pause = new ApiPause(uhr::get);

	@Test
	void ohneAusfallNieGesperrt() {
		assertFalse(pause.gesperrt());
		pause.erfolg();
		assertFalse(pause.gesperrt());
		assertFalse(pause.aktiv());
	}

	@Test
	void nachAusfallGesperrtBisZumAblauf() {
		pause.ausfall(503);
		assertTrue(pause.aktiv());
		assertTrue(pause.gesperrt());
		uhr.addAndGet(ApiPause.PAUSE_MS - 1);
		assertTrue(pause.gesperrt());
	}

	@Test
	void nachAblaufDarfGenauEinerProben() {
		pause.ausfall(503);
		uhr.addAndGet(ApiPause.PAUSE_MS);
		assertFalse(pause.gesperrt(), "der erste nach Ablauf ist der Prober");
		assertTrue(pause.gesperrt(), "alle weiteren warten auf sein Ergebnis");
		assertTrue(pause.aktiv());
	}

	@Test
	void proberScheitertDannNeuePause() {
		pause.ausfall(503);
		uhr.addAndGet(ApiPause.PAUSE_MS);
		assertFalse(pause.gesperrt());
		uhr.addAndGet(7_000);
		pause.ausfall(503);
		uhr.addAndGet(ApiPause.PAUSE_MS - 1);
		assertTrue(pause.gesperrt());
		uhr.addAndGet(1);
		assertFalse(pause.gesperrt());
	}

	@Test
	void proberErfolgreichDannSofortOffen() {
		pause.ausfall(503);
		uhr.addAndGet(ApiPause.PAUSE_MS);
		assertFalse(pause.gesperrt());
		pause.erfolg();
		assertFalse(pause.aktiv());
		assertFalse(pause.gesperrt());
		assertFalse(pause.gesperrt());
	}

	@Test
	void verschwindetDerProberDarfNachFristEinWeiterer() {
		pause.ausfall(503);
		uhr.addAndGet(ApiPause.PAUSE_MS);
		assertFalse(pause.gesperrt());
		uhr.addAndGet(ApiPause.PROBE_MS - 1);
		assertTrue(pause.gesperrt());
		uhr.addAndGet(1);
		assertFalse(pause.gesperrt());
	}

	@Test
	void unterLastGenauEinProber() throws Exception {
		pause.ausfall(503);
		uhr.addAndGet(ApiPause.PAUSE_MS);
		int n = 64;
		AtomicInteger durch = new AtomicInteger();
		CountDownLatch start = new CountDownLatch(1), ende = new CountDownLatch(n);
		for (int i = 0; i < n; i++) {
			new Thread(() -> {
				try {
					start.await();
				} catch (InterruptedException e) {
					return;
				}
				if (!pause.gesperrt())
					durch.incrementAndGet();
				ende.countDown();
			}).start();
		}
		start.countDown();
		ende.await();
		assertEquals(1, durch.get());
	}
}
