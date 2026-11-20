package lostmanager.util;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ein Durchlauf über alle Spieler zu einem Stichtag (Start und Ende der
 * Clanspiele), bei dem ein einzelner Spieler den Rest nicht mitreißen darf.
 *
 * Bis zum 20.11.2026 liefen diese Schleifen ungeschützt in einer eingeplanten
 * Aufgabe. Antwortet die CoC-API für einen Spieler nicht (Wartung, 503, Pause),
 * wirft {@code Player.addAchievementDataToDB} – und damit endete die Schleife
 * für alle folgenden Spieler, die Aufgabe plante sich nicht mehr für den
 * nächsten Monat ein, und im Journal stand nichts davon: ein
 * {@code ScheduledFuture}, das niemand abfragt, verschluckt seine Ausnahme.
 */
public final class Stichtagslauf {

	private Stichtagslauf() {
	}

	/**
	 * Ruft {@code jeSpieler} für jedes Tag auf und fängt je Spieler alles ab.
	 *
	 * @return Zahl der Spieler, bei denen es scheiterte
	 */
	public static int fuerAlle(List<String> tags, Consumer<String> jeSpieler, String wofuer) {
		int gescheitert = 0;
		String ersterFehler = null;
		for (String tag : tags) {
			try {
				jeSpieler.accept(tag);
			} catch (final Throwable t) {
				gescheitert++;
				if (ersterFehler == null) {
					ersterFehler = tag + ": " + t;
				}
			}
		}
		if (gescheitert > 0) {
			System.err.println(wofuer + ": " + gescheitert + " von " + tags.size()
					+ " Spielern ohne Stand (erster: " + ersterFehler + ")");
		} else {
			System.out.println(wofuer + ": " + tags.size() + " Spieler gesichert");
		}
		return gescheitert;
	}

	/**
	 * Führt {@code lauf} aus und danach in jedem Fall {@code neuEinplanen} – auch
	 * wenn der Lauf selbst wirft (etwa weil die Datenbank die Spielerliste nicht
	 * hergibt). Fehler landen im Journal statt im Future.
	 */
	public static Runnable mitNeueinplanung(String wofuer, Runnable lauf, Runnable neuEinplanen) {
		return () -> {
			try {
				lauf.run();
			} catch (final Throwable t) {
				System.err.println(wofuer + ": Lauf abgebrochen: " + t);
			} finally {
				try {
					neuEinplanen.run();
				} catch (final Throwable t) {
					System.err.println(wofuer + ": Neueinplanung gescheitert: " + t);
				}
			}
		};
	}
}
