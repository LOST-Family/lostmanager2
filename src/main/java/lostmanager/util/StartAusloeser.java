package lostmanager.util;

/**
 * Wiederholung für Kriegsstart-Auslöser.
 *
 * Ein Start-Event läuft genau einmal, in dem Poll-Durchlauf, der den Übergang sieht; danach
 * steht der neue Kriegszustand in der Datenbank und der Start wird nie wieder erkannt. Bis
 * 18.11.2026 nahm ein gescheiterter Lauf sich nur aus der „schon gefeuert“-Menge („retry in
 * next poll“) – der nächste Durchlauf sah aber keinen Start mehr, das Event (CW-Spender,
 * Filler) fiel für diesen Krieg still aus. Gesehen am 18.11.2026 13:35 UTC: nach der
 * API-Wartung 429 „requestThrottled“, „Error firing start trigger 2: null“.
 *
 * Deshalb wird hier an Ort und Stelle wiederholt, mit Abständen, die eine Drosselung
 * überdauern.
 */
public final class StartAusloeser {

	/** Wartezeiten vor dem 2., 3. und 4. Versuch. */
	public static final long[] WARTEZEITEN_MS = { 20_000L, 60_000L, 120_000L };

	@FunctionalInterface
	public interface Lauf {
		void laufen() throws Exception;
	}

	@FunctionalInterface
	public interface Warten {
		void warten(long ms) throws InterruptedException;
	}

	private StartAusloeser() {
	}

	/**
	 * Führt {@code lauf} aus und wiederholt ihn nach jeder Exception, höchstens
	 * {@code wartezeitenMs.length} Mal.
	 *
	 * @return Zahl der gebrauchten Versuche (1 = sofort geklappt), 0 wenn alle scheiterten
	 *         oder das Warten unterbrochen wurde
	 */
	public static int mitWiederholung(String name, Lauf lauf, long[] wartezeitenMs, Warten warten) {
		final int versuche = wartezeitenMs.length + 1;
		for (int versuch = 1; versuch <= versuche; versuch++) {
			try {
				lauf.laufen();
				return versuch;
			} catch (final Exception e) {
				// Die Klasse mit ausgeben: getMessage() einer NullPointerException ist oft null
				System.err.println("Error firing " + name + " (Versuch " + versuch + "/" + versuche + "): " + e);
				final StackTraceElement[] spur = e.getStackTrace();
				for (int i = 0; i < Math.min(4, spur.length); i++) {
					System.err.println("    at " + spur[i]);
				}
				if (versuch == versuche) {
					break;
				}
				try {
					warten.warten(wartezeitenMs[versuch - 1]);
				} catch (final InterruptedException ie) {
					Thread.currentThread().interrupt();
					System.err.println(name + ": Wiederholung unterbrochen");
					return 0;
				}
			}
		}
		System.err.println(name + " nach " + versuche + " Versuchen aufgegeben");
		return 0;
	}
}
