package lostmanager.util;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * Buchführung darüber, welches Listening Event gerade im Speicher-Scheduler
 * liegt und welches gerade läuft.
 *
 * <h2>Wozu</h2>
 *
 * Der Poller in {@code Bot.startEventPolling} übergibt jedes Event, das binnen
 * fünf Minuten fällig wird, als Aufgabe an den Scheduler. Diese Aufgabe hält
 * ihr eigenes {@code ListeningEvent}-Objekt - wird das Event danach geändert,
 * feuert sie trotzdem, und zwar mit einer Mischung aus alten Werten (Typ, Dauer,
 * Clan, beim Einplanen schon gelesen) und neuen (die Aktionswerte liest sie erst
 * beim Feuern nach). Bisher gab es dagegen nur den groben Weg
 * {@code restartAllEvents}, und zwischen Speichern und Neuaufbau blieb ein
 * Fenster, in dem die alte Aufgabe noch losgehen konnte.
 *
 * <h2>Wie</h2>
 *
 * Jede eingeplante Aufgabe bekommt hier einen Eintrag, und die Aufgabe läuft nur,
 * wenn sie ihren <em>eigenen</em> Eintrag beim Start atomar entfernen kann.
 * {@link #verwerfen} entfernt den Eintrag von der anderen Seite. Wer zuerst
 * kommt, gewinnt - und genau einer gewinnt:
 *
 * <ul>
 * <li>{@link #verwerfen} bekommt den Eintrag: die Aufgabe findet ihn beim Start
 * nicht mehr und tut nichts. Das Event kann gefahrlos geändert werden.</li>
 * <li>Die Aufgabe bekommt ihn: sie steht vorher schon in {@link #laeuft}, und
 * {@link #verwerfen} meldet {@link Zustand#LAEUFT}. Ein laufender Durchgang wird
 * nie angefasst.</li>
 * </ul>
 *
 * Auf {@link Future#cancel} allein ist dafür kein Verlass: eine
 * {@code FutureTask}, deren {@code run()} schon begonnen hat, meldet beim
 * Abbrechen trotzdem Erfolg und läuft einfach weiter.
 *
 * Ohne JDA und ohne Datenbank, damit es sich prüfen lässt.
 */
public final class EventEinplanung {

	/** Was {@link #verwerfen} vorgefunden hat. */
	public enum Zustand {
		/** Lag nicht im Scheduler - nichts zu tun. */
		NICHT_EINGEPLANT,
		/** Lag im Scheduler und wird jetzt garantiert nicht mehr ausgeführt. */
		VERWORFEN,
		/** Wird gerade ausgeführt; daran ändert sich nichts. */
		LAEUFT
	}

	/** @param feuerzeit die berechnete Feuerzeit, null bei Start-Auslösern */
	public record Ergebnis(Zustand zustand, Long feuerzeit) {
	}

	/** Ein eingeplanter Durchgang. Identität zählt, nicht Gleichheit. */
	public static final class Eintrag {
		private final long eventId;
		private final Long feuerzeit;
		private volatile Future<?> future;

		private Eintrag(long eventId, Long feuerzeit) {
			this.eventId = eventId;
			this.feuerzeit = feuerzeit;
		}

		public Long feuerzeit() {
			return feuerzeit;
		}

		/** Nur zum Aufräumen nachgereicht; die Entscheidung hängt nicht daran. */
		public void setFuture(Future<?> future) {
			this.future = future;
		}
	}

	private final ConcurrentHashMap<Long, Eintrag> eingeplant = new ConcurrentHashMap<>();
	/** Zähler statt Menge: zwei gleichzeitige Läufe desselben Events sind denkbar (Start-Auslöser). */
	private final ConcurrentHashMap<Long, Integer> laufend = new ConcurrentHashMap<>();

	/** Die eine Instanz des Bots. Tests legen sich eigene an. */
	public static final EventEinplanung BOT = new EventEinplanung();

	/**
	 * Meldet einen Durchgang an und gibt die Aufgabe zurück, die an den Scheduler
	 * geht. Erst {@code einplanen}, dann {@code schedule} - der Eintrag muss
	 * stehen, bevor die Aufgabe laufen kann, auch bei Verzögerung 0.
	 *
	 * Ein älterer Eintrag desselben Events wird dabei ersetzt; seine Aufgabe
	 * findet ihren Eintrag dann nicht mehr und läuft nicht.
	 */
	public Eintrag einplanen(long eventId, Long feuerzeit) {
		Eintrag eintrag = new Eintrag(eventId, feuerzeit);
		eingeplant.put(eventId, eintrag);
		return eintrag;
	}

	/** Die Aufgabe für den Scheduler: läuft nur, wenn ihr Eintrag noch ihr gehört. */
	public Runnable aufgabe(Eintrag eintrag, Runnable arbeit) {
		return () -> {
			// Erst als laufend eintragen, dann den Eintrag beanspruchen. Andersherum
			// gäbe es einen Augenblick, in dem das Event weder eingeplant noch laufend
			// aussieht - verwerfen() würde dann "nicht eingeplant" melden, während es
			// gleich losgeht.
			laufend.merge(eintrag.eventId, 1, Integer::sum);
			try {
				if (!eingeplant.remove(eintrag.eventId, eintrag)) {
					return; // verworfen oder ersetzt
				}
				arbeit.run();
			} finally {
				laufend.computeIfPresent(eintrag.eventId, (_, n) -> n <= 1 ? null : n - 1);
			}
		};
	}

	/**
	 * Nimmt ein Event aus dem Scheduler, sofern es dort liegt und noch nicht läuft.
	 * Nach {@link Zustand#VERWORFEN} ist sicher, dass der alte Durchgang nicht mehr
	 * ausgeführt wird; neu eingeplant wird über den nächsten Poll.
	 */
	public Ergebnis verwerfen(long eventId) {
		Eintrag eintrag = eingeplant.remove(eventId);
		if (eintrag != null) {
			Future<?> f = eintrag.future;
			if (f != null) {
				f.cancel(false);
			}
			return new Ergebnis(Zustand.VERWORFEN, eintrag.feuerzeit);
		}
		if (laeuft(eventId)) {
			return new Ergebnis(Zustand.LAEUFT, null);
		}
		return new Ergebnis(Zustand.NICHT_EINGEPLANT, null);
	}

	public boolean laeuft(long eventId) {
		return laufend.containsKey(eventId);
	}

	/** @return die Feuerzeit, wenn das Event gerade eingeplant ist, sonst null */
	public Eintrag eingeplant(long eventId) {
		return eingeplant.get(eventId);
	}

	/**
	 * Beim Neuaufbau des Schedulers: die ausstehenden Aufgaben des alten Pools
	 * laufen nicht mehr (siehe {@code restartAllEventsInternal}), ihre Einträge
	 * sind also Altlast. Laufende Durchgänge bleiben vermerkt - die laufen im alten
	 * Pool zu Ende.
	 */
	public void allesVergessen() {
		eingeplant.clear();
	}

	/** Für Tests. */
	Set<Long> eingeplanteIds() {
		return eingeplant.keySet();
	}
}
