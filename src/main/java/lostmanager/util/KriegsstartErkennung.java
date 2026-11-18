package lostmanager.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Entscheidet, ob ein beobachteter Kriegszustand ein Kriegsstart ist, an dem die
 * CW-Start-Auslöser feuern.
 *
 * Anlass (18.11.2026): Der Bot wurde während einer Wartung der CoC-API neu
 * gestartet. Die Initialisierung bekam keine Antwort und trug für alle Clans
 * "notInWar" ein; als die API eine Viertelstunde später wieder antwortete, sah das
 * wie "notInWar -> inWar" aus, und die Start-Auslöser feuerten mitten in laufenden
 * Kriegen erneut. Deshalb: Ohne Antwort bleibt der Zustand unbekannt (leerer
 * String), und aus "unbekannt" heraus zählt ein laufender Krieg nur dann als Start,
 * wenn seine Vorbereitung erst nach dem Start des Bots begonnen hat.
 */
public final class KriegsstartErkennung {

	/** Zustand, solange noch keine Antwort der API vorlag. */
	public static final String UNBEKANNT = "";

	private static final DateTimeFormatter COC_ZEIT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
			.withZone(ZoneOffset.UTC);

	private KriegsstartErkennung() {
	}

	public static boolean laeuft(String zustand) {
		return "preparation".equals(zustand) || "inWar".equals(zustand);
	}

	public static boolean ausserhalb(String zustand) {
		return "notInWar".equals(zustand) || "warEnded".equals(zustand);
	}

	/**
	 * @param letzterZustand      zuletzt gesehener Zustand, {@link #UNBEKANNT} wenn
	 *                            seit dem Start noch keine Antwort kam
	 * @param aktuellerZustand    Zustand laut API in diesem Durchlauf
	 * @param vorbereitungsBeginn Beginn der Vorbereitung laut API, null wenn
	 *                            nicht lesbar
	 * @param botStart            Startzeit des Bots (ms)
	 */
	public static boolean istKriegsstart(String letzterZustand, String aktuellerZustand, Long vorbereitungsBeginn,
			long botStart) {
		if (!laeuft(aktuellerZustand)) {
			return false;
		}
		if (letzterZustand == null || UNBEKANNT.equals(letzterZustand)) {
			// Erster Blick nach dem Start: nur ein Krieg, der nachweislich erst danach
			// begonnen hat, ist ein Start. Alles andere lief schon vorher.
			return vorbereitungsBeginn != null && vorbereitungsBeginn >= botStart;
		}
		return ausserhalb(letzterZustand);
	}

	/** Liest eine Zeitangabe der CoC-API ("20261118T132100.000Z"), null wenn nicht lesbar. */
	public static Long zeitLesen(String cocZeit) {
		if (cocZeit == null || cocZeit.isEmpty()) {
			return null;
		}
		try {
			return Instant.from(COC_ZEIT.parse(cocZeit)).toEpochMilli();
		} catch (final RuntimeException e) {
			return null;
		}
	}
}
