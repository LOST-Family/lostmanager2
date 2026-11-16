package lostmanager.apiutil;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Pause für die CoC-API, solange sie ausfällt (Wartung: HTTP 503 auf alles).
 *
 * Anlass 16.11.2026: Während einer Wartung von über einer Stunde stellte der Bot
 * rund 130 Abfragen je Minute, jede mit drei Wiederholungen (7 s Wartezeit je
 * Abfrage, der Thread steht so lange) – 24 000 Journalzeilen in einer Stunde,
 * und jede einzelne war von vornherein aussichtslos.
 *
 * Regel: Die Pause beginnt erst, wenn eine Abfrage ALLE Wiederholungen mit einem
 * 5xx verbraucht hat (ein einzelnes 503 ist Grundrauschen, rund 13-mal die Stunde,
 * und darf nichts sperren). Während der Pause kehren Abfragen sofort ohne Antwort
 * zurück – für die Aufrufer dasselbe wie „alle Versuche gescheitert“. Nach Ablauf
 * darf genau EIN Aufrufer proben; antwortet die API, ist die Pause vorbei.
 */
public final class ApiPause {

	static final long PAUSE_MS = 30_000;
	/** So lange hat der eine Prober Zeit, bevor ein weiterer es versuchen darf. */
	static final long PROBE_MS = 15_000;

	private final AtomicLong bis = new AtomicLong(0);
	private long seit = 0;
	private final LongSupplier uhr;

	public ApiPause() {
		this(System::currentTimeMillis);
	}

	ApiPause(LongSupplier uhr) {
		this.uhr = uhr;
	}

	/**
	 * Vor jeder Abfrage aufrufen. true = jetzt nicht fragen. Nach Ablauf der Pause
	 * bekommt genau ein Aufrufer false (der Prober), alle anderen weiter true.
	 */
	public boolean gesperrt() {
		long b = bis.get();
		if (b == 0)
			return false;
		long jetzt = uhr.getAsLong();
		if (jetzt < b)
			return true;
		return !bis.compareAndSet(b, jetzt + PROBE_MS);
	}

	/** Läuft gerade eine Pause? Ohne Nebenwirkung, zum Stummschalten von Folgezeilen. */
	public boolean aktiv() {
		return bis.get() != 0;
	}

	/** Eine Abfrage hat alle Wiederholungen mit einem 5xx verbraucht. */
	public synchronized void ausfall(int status) {
		long jetzt = uhr.getAsLong();
		bis.set(jetzt + PAUSE_MS);
		if (seit == 0) {
			seit = jetzt;
			System.err.println("CoC-API antwortet mit HTTP " + status
					+ " (Wartung?) - Abfragen pausieren, Probe alle " + (PAUSE_MS / 1000) + " s");
		}
	}

	/** Die API hat geantwortet (200 oder 4xx). */
	public void erfolg() {
		if (bis.get() == 0)
			return;
		synchronized (this) {
			bis.set(0);
			if (seit != 0) {
				System.err.println("CoC-API antwortet wieder (Pause nach " + ((uhr.getAsLong() - seit) / 1000) + " s beendet)");
				seit = 0;
			}
		}
	}
}
