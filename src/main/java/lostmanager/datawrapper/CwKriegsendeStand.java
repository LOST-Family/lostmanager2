package lostmanager.datawrapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Merkt sich je Clan den letzten vollständigen Stand eines Clankriegs rund ums Kriegsende.
 *
 * Anlass (12./13.11.2026): Sucht der Clan direkt nach Kriegsende den nächsten Krieg, liefert
 * {@code /currentwar} nur noch {@code notInWar} ohne Mitglieder. Die Prüfung fünf Minuten nach
 * Kriegsende hatte dann nichts mehr in der Hand und hat keine Kickpunkte vergeben.
 *
 * Supercell cacht {@code /currentwar} und {@code /warlog} je 120 Sekunden
 * ({@code Cache-Control: max-age=120}, gemessen am 14.11.2026). Öfter als etwa alle 20 Sekunden
 * zu fragen bringt deshalb nichts: man bekommt nur denselben Stand noch einmal. Daraus folgt
 * auch, dass der letzte {@code inWar}-Stand bis zu 120 Sekunden vor Kriegsende entstanden sein
 * kann. Ob danach noch jemand angegriffen hat, verrät der Kriegslog: er nennt die Gesamtzahl der
 * Angriffe. Nur wenn die mit dem gemerkten Stand übereinstimmt, ist dieser vollständig. Angriffe
 * kommen nur hinzu, nie weg - gleiche Zahl heißt also dieselben Angriffe.
 *
 * Bewusst nur im Speicher: die Prüfung, die den Stand braucht, lebt ebenfalls nur im Speicher
 * und fällt bei einem Neustart ohnehin weg.
 */
public final class CwKriegsendeStand {

	/** Abstand zwischen zwei Abfragen rund ums Kriegsende. Bei 120 s Cache höchstens 20 s Verzug. */
	static final long ABFRAGE_ABSTAND_MS = TimeUnit.SECONDS.toMillis(20);

	/**
	 * Zwei Endzeiten bis zu diesem Abstand gelten als derselbe Krieg. Die API verschiebt das
	 * Kriegsende während des Kriegs um Minuten (13.11.2026: 76 Sekunden); zwei Kriege desselben
	 * Clans liegen mindestens eine Vorbereitungs- plus Kriegsphase auseinander.
	 */
	static final long GLEICHER_KRIEG_MS = TimeUnit.MINUTES.toMillis(60);

	private static final DateTimeFormatter API_ZEIT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
			.withZone(ZoneOffset.UTC);

	/** Ein gemerkter Stand: die vollständige Antwort von /currentwar und wann sie abgerufen wurde. */
	public record Stand(JSONObject json, String state, long endTimeMillis, long abgerufenUm) {
		public boolean beendet() {
			return "warEnded".equals(state);
		}

		/** Angriffe des eigenen Clans, aus den Mitgliedern gezählt. */
		public int angriffe() {
			JSONArray mitglieder = json.getJSONObject("clan").getJSONArray("members");
			int summe = 0;
			for (int i = 0; i < mitglieder.length(); i++) {
				JSONObject m = mitglieder.getJSONObject(i);
				if (m.has("attacks")) {
					summe += m.getJSONArray("attacks").length();
				}
			}
			return summe;
		}
	}

	/** Was ein Stand für die Prüfung nach Kriegsende taugt. */
	public enum Urteil {
		/** Stand ist schon {@code warEnded}: endgültig, alle Angriffe drin. */
		ENDGUELTIG,
		/** {@code inWar}-Stand, aber der Kriegslog zählt genau so viele Angriffe. */
		VOLLSTAENDIG_LAUT_KRIEGSLOG,
		/** Der Kriegslog zählt mehr Angriffe: wer danach noch angegriffen hat, ist unbekannt. */
		UNVOLLSTAENDIG,
		/** Krieg (noch) nicht im Kriegslog, etwa weil der Log noch aus dem Cache kommt oder privat ist. */
		KRIEGSLOG_FEHLT,
		/** Nichts gemerkt. */
		KEIN_STAND
	}

	private static final Map<String, Stand> STAENDE = new ConcurrentHashMap<>();
	private static final Set<String> BEOBACHTET = ConcurrentHashMap.newKeySet();

	private CwKriegsendeStand() {
	}

	static Long endTimeMillis(JSONObject json) {
		if (json == null || !json.has("endTime") || json.isNull("endTime")) {
			return null;
		}
		try {
			return Instant.from(API_ZEIT.parse(json.getString("endTime"))).toEpochMilli();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Nur Stände mit Mitgliederliste aus einem laufenden oder gerade beendeten Krieg taugen. */
	static boolean vollstaendig(JSONObject json) {
		if (json == null) {
			return false;
		}
		String state = json.optString("state", "");
		if (!state.equals("inWar") && !state.equals("warEnded")) {
			return false;
		}
		JSONObject clan = json.optJSONObject("clan");
		return clan != null && clan.optJSONArray("members") != null && endTimeMillis(json) != null;
	}

	static boolean gleicherKrieg(long endA, long endB) {
		return Math.abs(endA - endB) <= GLEICHER_KRIEG_MS;
	}

	/** Merkt sich die Antwort, wenn sie vollständig ist und den bisherigen Stand nicht verschlechtert. */
	public static void merke(String clanTag, JSONObject json) {
		merke(clanTag, json, System.currentTimeMillis());
	}

	static void merke(String clanTag, JSONObject json, long jetzt) {
		if (clanTag == null || !vollstaendig(json)) {
			return;
		}
		Stand neu = new Stand(json, json.getString("state"), endTimeMillis(json), jetzt);
		STAENDE.compute(clanTag, (_, alt) -> {
			if (alt == null || !gleicherKrieg(alt.endTimeMillis(), neu.endTimeMillis())) {
				return neu;
			}
			// warEnded ist endgültig; ein späteres inWar kann nur ein älterer Cache-Stand sein.
			if (alt.beendet() && !neu.beendet()) {
				return alt;
			}
			// Weniger Angriffe als schon gesehen: ebenfalls ein älterer Cache-Stand.
			if (!neu.beendet() && neu.angriffe() < alt.angriffe()) {
				return alt;
			}
			return neu;
		});
	}

	/** Der gemerkte Stand des Kriegs mit dieser Endzeit, oder null. */
	public static Stand fuer(String clanTag, long endTimeMillis) {
		Stand s = clanTag == null ? null : STAENDE.get(clanTag);
		return s != null && gleicherKrieg(s.endTimeMillis(), endTimeMillis) ? s : null;
	}

	/**
	 * Angriffe des eigenen Clans laut Kriegslog für den Krieg mit dieser Endzeit, oder null,
	 * wenn er dort (noch) nicht steht.
	 */
	public static Integer angriffeLautKriegslog(JSONObject kriegslog, long endTimeMillis) {
		if (kriegslog == null || kriegslog.optJSONArray("items") == null) {
			return null;
		}
		JSONArray items = kriegslog.getJSONArray("items");
		for (int i = 0; i < items.length(); i++) {
			JSONObject eintrag = items.getJSONObject(i);
			Long ende = endTimeMillis(eintrag);
			JSONObject clan = eintrag.optJSONObject("clan");
			if (ende != null && gleicherKrieg(ende, endTimeMillis) && clan != null && clan.has("attacks")) {
				return clan.getInt("attacks");
			}
		}
		return null;
	}

	public static Urteil beurteile(Stand stand, Integer angriffeLautKriegslog) {
		if (stand == null) {
			return Urteil.KEIN_STAND;
		}
		if (stand.beendet()) {
			return Urteil.ENDGUELTIG;
		}
		if (angriffeLautKriegslog == null) {
			return Urteil.KRIEGSLOG_FEHLT;
		}
		return angriffeLautKriegslog == stand.angriffe() ? Urteil.VOLLSTAENDIG_LAUT_KRIEGSLOG : Urteil.UNVOLLSTAENDIG;
	}

	/**
	 * Fragt /currentwar dieses Clans alle {@link #ABFRAGE_ABSTAND_MS} ab und merkt sich jeden
	 * vollständigen Stand des Kriegs mit dieser Endzeit - bis {@code warEnded} kommt (endgültig),
	 * der Clan schon den nächsten Krieg sucht oder vorbereitet, oder {@code bisMillis} erreicht ist.
	 * Läuft je Clan höchstens einmal; ein zweites Event desselben Clans hängt sich nur an.
	 */
	public static void beobachte(String clanTag, long endTimeMillis, long bisMillis) {
		if (clanTag == null || !BEOBACHTET.add(clanTag)) {
			return;
		}
		ScheduledExecutorService takt = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "cw-kriegsende-" + clanTag);
			t.setDaemon(true);
			return t;
		});
		Runnable abfrage = () -> {
			boolean weiter = false;
			try {
				weiter = einmalAbfragen(clanTag, endTimeMillis) && System.currentTimeMillis() < bisMillis;
			} catch (Throwable e) {
				// Ein Fehler darf nur diese eine Abfrage kosten, nicht die ganze Beobachtung.
				System.err.println("CW-Kriegsende " + clanTag + ": Abfrage fehlgeschlagen: " + e);
				weiter = System.currentTimeMillis() < bisMillis;
			}
			if (!weiter) {
				BEOBACHTET.remove(clanTag);
				takt.shutdown();
			}
		};
		takt.scheduleWithFixedDelay(abfrage, ABFRAGE_ABSTAND_MS, ABFRAGE_ABSTAND_MS, TimeUnit.MILLISECONDS);
	}

	/** Eine Abfrage. @return true, wenn weiter beobachtet werden soll. */
	private static boolean einmalAbfragen(String clanTag, long endTimeMillis) {
		JSONObject json = new Clan(clanTag).getCWJson();
		if (json == null) {
			return true; // API gerade nicht erreichbar, nächster Takt
		}
		return verarbeite(clanTag, endTimeMillis, json, System.currentTimeMillis());
	}

	/** Wertet eine Antwort aus. @return true, wenn weiter beobachtet werden soll. */
	static boolean verarbeite(String clanTag, long endTimeMillis, JSONObject json, long jetzt) {
		String state = json.optString("state", "");
		Long ende = endTimeMillis(json);
		boolean dieserKrieg = ende != null && gleicherKrieg(ende, endTimeMillis);
		if (dieserKrieg && vollstaendig(json)) {
			merke(clanTag, json, jetzt);
			if (state.equals("warEnded")) {
				System.out.println("CW-Kriegsende " + clanTag + ": warEnded gesehen, Stand endgültig");
				return false;
			}
			return true;
		}
		// notInWar, preparation oder schon der nächste Krieg: hier gibt es nichts mehr zu holen.
		System.out.println("CW-Kriegsende " + clanTag + ": state=" + state + ", Beobachtung beendet");
		return false;
	}

	/** Nur für Proben. */
	static void vergiss() {
		STAENDE.clear();
		BEOBACHTET.clear();
	}
}
