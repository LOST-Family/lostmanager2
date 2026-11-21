package lostmanager.legend;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import lostmanager.legend.LegendRegeln.EinsErgebnis;
import lostmanager.legend.LegendRegeln.EinsStand;
import lostmanager.legend.LegendRegeln.Eintrag;

/**
 * Spielt echte Kampfprotokolle (CoC-API, abgerufen 17.11.2026 18:40 UTC) durch
 * die Legend-I-Auswertung: eine nachgebaute API, die den Pokalstand nach jedem
 * Kampf ändert und den Kampf ins Protokoll schreibt, und der Takt fragt alle
 * 30 Sekunden (mit etwas Verzug).
 *
 * Pokale je Kampf nennt das Protokoll nicht. Für Vendetta am 16.11. sind sie
 * belegt (ClashPerk-Karte im Kanal trophy-hunt-planung, Nachricht
 * 1571951848741539960: +40 +32 +32 +40 +40 +31 +40 +40 = 295, Verteidigungen je
 * −40). Für den 17.11. ist die Summe aus ClashPerk belegt (Nachricht
 * 1572317806706434078); die Einzelwerte ergeben sich aus der Formel
 * (LegendRegeln.legendEinsPokale), deren Summe der Test gegen ClashPerk prüft.
 */
class LegendNachspielTest {

	static boolean zeigen = false;

	record Sim(Instant aenderung, Instant imLog, int pokale, Eintrag eintrag) {
	}

	static JSONArray protokoll(String tag) throws Exception {
		try (InputStream in = LegendNachspielTest.class
				.getResourceAsStream("/legend/battlelog-" + tag + "-2026-11-17.json")) {
			return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getJSONArray("items");
		}
	}

	/**
	 * Baut die Kämpfe eines Legend-Tages samt Pokalen. {@code angriffe} gibt die
	 * Pokale der Angriffe in Reihenfolge vor (null = Dreistern 40, Rest
	 * gleichmäßig auf die anderen verteilt, Summe {@code summe}).
	 *
	 * @param endeIstZeitstempel true: die Pokale ändern sich zum battleTimestamp
	 *                           (falls der schon das Kampfende ist), sonst nach
	 *                           battleTimestamp + battleTime
	 */
	static List<Sim> kaempfe(JSONArray p, LocalDate tag, int[] angriffe, int summe, int[] verteidigungen,
			boolean endeIstZeitstempel, long logVerzugSek) {
		List<JSONObject> amTag = new ArrayList<>();
		for (int i = 0; i < p.length(); i++) {
			JSONObject k = p.getJSONObject(i);
			Instant ende = LegendWertung.kampfEnde(k);
			if ("legend".equals(k.optString("battleType")) && ende != null && LegendRegeln.legendTag(ende).equals(tag)) {
				amTag.add(k);
			}
		}
		List<JSONObject> ang = amTag.stream().filter(k -> k.getBoolean("attack")).toList();
		int[] werte = angriffe;
		if (werte == null) {
			// Pokale je Angriff nach der Formel (LegendRegeln.legendEinsPokale)
			werte = new int[ang.size()];
			for (int i = 0; i < ang.size(); i++) {
				werte[i] = LegendRegeln.legendEinsPokale(ang.get(i).getInt("stars"), ang.get(i).getInt("destructionPercentage"));
			}
		}
		List<Sim> sims = new ArrayList<>();
		int a = 0, v = 0;
		JSONArray nurTag = new JSONArray(amTag);
		List<Eintrag> eintraege = LegendWertung.eintraege(nurTag);
		for (int i = 0; i < amTag.size(); i++) {
			JSONObject k = amTag.get(i);
			Instant ende = LegendWertung.kampfEnde(k);
			Instant aenderung = endeIstZeitstempel ? ende.minusSeconds(k.getInt("battleTime")) : ende;
			int pokale;
			if (k.getBoolean("attack")) {
				pokale = werte[a++];
			} else {
				pokale = verteidigungen != null ? verteidigungen[v++]
						: -LegendRegeln.legendEinsPokale(k.getInt("stars"), k.getInt("destructionPercentage"));
			}
			sims.add(new Sim(aenderung, aenderung.plusSeconds(logVerzugSek), pokale, eintraege.get(i)));
		}
		assertEquals(summe, sims.stream().filter(s -> s.eintrag().kampf().angriff()).mapToInt(Sim::pokale).sum());
		return sims;
	}

	/** Fragt den ganzen Tag im Takt ab und gibt die gebuchten Angriffspokale je Tag zurück. */
	static Map<LocalDate, Integer> nachspielen(List<Sim> sims, LocalDate tag, int taktSek, int verzugSek) {
		Instant beginn = LegendRegeln.tagesbeginn(tag);
		Instant ende = beginn.plus(Duration.ofDays(1)).minusSeconds(1);
		int start = 5000;
		// Erste Sichtung kurz vor Tagesbeginn: nichts vom Tag ist schon passiert.
		EinsStand stand = new EinsStand(start, new HashSet<>(), beginn.minusSeconds(60), 0, new HashSet<>(), null, null);
		Map<LocalDate, Integer> gebucht = new java.util.TreeMap<>();
		int schritt = 0;
		for (Instant t = beginn.plusSeconds(7); t.isBefore(ende.plus(Duration.ofMinutes(10))); t = t
				.plusSeconds(taktSek + (schritt++ * 7919L) % (verzugSek + 1))) {
			final Instant jetzt = t;
			int pokale = start + sims.stream().filter(s -> !s.aenderung().isAfter(jetzt)).mapToInt(Sim::pokale).sum();
			List<Eintrag> log = sims.stream().filter(s -> !s.imLog().isAfter(jetzt)).map(Sim::eintrag).toList();
			EinsErgebnis e = LegendRegeln.legendEins(stand, pokale, () -> log, jetzt,
					LegendRegeln.formel(LegendRegeln.GROB));
			if (e == null) {
				continue;
			}
			if (zeigen && (pokale != stand.pokale() || !e.stand().topf().isEmpty() || !e.tage().isEmpty())) {
				System.out.println(jetzt + " pokale " + pokale + " log " + log.size() + " -> " + e.tage() + " topf "
						+ e.stand().topfPokale() + " " + e.stand().topf() + " regung " + e.stand().letzteRegung());
			}
			e.tage().forEach((d, w) -> gebucht.merge(d, w, Integer::sum));
			stand = e.stand();
		}
		return gebucht;
	}

	static void pruefeAlleVarianten(String tag, LocalDate tagDatum, int[] angriffe, int summe, int[] verteidigungen) throws Exception {
		JSONArray p = protokoll(tag);
		for (boolean endeIstZeitstempel : new boolean[] { false, true }) {
			for (long logVerzug : new long[] { -30, -10, 0, 25, 60, 75 }) {
				for (int takt : new int[] { 30, 60 }) {
					List<Sim> sims = kaempfe(p, tagDatum, angriffe, summe, verteidigungen, endeIstZeitstempel, logVerzug);
					zeigen = System.getenv("LEGEND_NACHSPIEL_ZEIGEN") != null && takt == Integer.getInteger("zeigeTakt", 30)
							&& logVerzug == Long.getLong("zeigeVerzug", 0) && endeIstZeitstempel == Boolean.getBoolean("zeigeZs");
					Map<LocalDate, Integer> g = nachspielen(sims, tagDatum, takt, 8);
					assertEquals(summe, g.getOrDefault(tagDatum, 0), tag + " " + tagDatum + " Takt " + takt
							+ " s, Protokoll-Verzug " + logVerzug + " s, Änderung zum Zeitstempel " + endeIstZeitstempel);
				}
			}
		}
	}

	@Test
	void vendetta16NovMitBelegtenEinzelwerten() throws Exception {
		// Angriffe und eine Verteidigung enden hier binnen Sekunden (12:49:42 A, 12:49:45 V).
		pruefeAlleVarianten("U98YVCUG", LocalDate.of(2026, 11, 16), new int[] { 40, 32, 32, 40, 40, 31, 40, 40 }, 295,
				null);
	}

	@Test
	void vendetta17Nov276() throws Exception {
		pruefeAlleVarianten("U98YVCUG", LocalDate.of(2026, 11, 17), null, 276, null);
	}

	@Test
	void pyro17Nov281() throws Exception {
		pruefeAlleVarianten("LQ2QJQGUY", LocalDate.of(2026, 11, 17), null, 281, null);
	}

	@Test
	void nicholas17Nov265() throws Exception {
		pruefeAlleVarianten("YP2JVC882", LocalDate.of(2026, 11, 17), null, 265, null);
	}

	@Test
	void mulm17Nov296() throws Exception {
		pruefeAlleVarianten("2VCL0PQG0", LocalDate.of(2026, 11, 17), null, 296, null);
	}

	@Test
	void formelTrifftClashPerk() throws Exception {
		// Einzelwerte der ClashPerk-Karte Vendetta 16.11.
		assertEquals(32, LegendRegeln.legendEinsPokale(2, 98));
		assertEquals(32, LegendRegeln.legendEinsPokale(2, 99));
		assertEquals(31, LegendRegeln.legendEinsPokale(2, 96));
		assertEquals(40, LegendRegeln.legendEinsPokale(3, 100));
		// Tagessummen aus dem Protokoll (Erstsichtung mitten am Tag) gegen ClashPerk 17.11.
		LocalDate t17 = LocalDate.of(2026, 11, 17);
		assertArrayEquals(new int[] { 276, 8 }, LegendWertung.tagesSumme(protokoll("U98YVCUG"), t17));
		assertArrayEquals(new int[] { 281, 8 }, LegendWertung.tagesSumme(protokoll("LQ2QJQGUY"), t17));
		assertArrayEquals(new int[] { 265, 8 }, LegendWertung.tagesSumme(protokoll("YP2JVC882"), t17));
		assertArrayEquals(new int[] { 296, 8 }, LegendWertung.tagesSumme(protokoll("2VCL0PQG0"), t17));
		assertArrayEquals(new int[] { 295, 8 }, LegendWertung.tagesSumme(protokoll("U98YVCUG"), LocalDate.of(2026, 11, 16)));
	}

	@Test
	void topfWartetAufsProtokollUndBuchtNichtDoppelt() {
		Instant t0 = Instant.parse("2026-11-17T08:00:00Z");
		LocalDate d = LocalDate.of(2026, 11, 17);
		Eintrag a = new Eintrag("a", new LegendRegeln.Kampf(true, 2, 90, t0.plusSeconds(10)));
		Eintrag v = new Eintrag("v", new LegendRegeln.Kampf(false, 3, 100, t0.plusSeconds(15)));
		EinsStand s = new EinsStand(5000, new HashSet<>(), null, 0, new HashSet<>(), null, null);
		// Angriff +30 und Dreistern-Verteidigung −40 in derselben halben Minute: Saldo −10
		EinsErgebnis e1 = LegendRegeln.legendEins(s, 4990, List::of, t0.plusSeconds(30), LegendRegeln.GROB);
		assertTrue(e1.tage().isEmpty());
		assertEquals(-10, e1.stand().topfPokale());
		EinsErgebnis e2 = LegendRegeln.legendEins(e1.stand(), 4990, () -> List.of(a, v), t0.plusSeconds(60),
				LegendRegeln.GROB);
		assertTrue(e2.tage().isEmpty(), "noch keine Ruhe");
		EinsErgebnis e3 = LegendRegeln.legendEins(e2.stand(), 4990, () -> List.of(a, v), t0.plusSeconds(160),
				LegendRegeln.GROB);
		assertEquals(Map.of(d, 30), e3.tage());
		EinsErgebnis e4 = LegendRegeln.legendEins(e3.stand(), 4990, () -> List.of(a, v), t0.plusSeconds(190),
				LegendRegeln.GROB);
		assertTrue(e4.tage().isEmpty());
		// Protokoll bleibt aus: nach zehn Minuten nach dem Vorzeichen, danach nichts doppelt
		Eintrag b = new Eintrag("b", new LegendRegeln.Kampf(true, 3, 100, t0.plusSeconds(300)));
		EinsStand st = e4.stand();
		EinsErgebnis e = null;
		for (int t = 310; t <= 310 + 600; t += 30) {
			e = LegendRegeln.legendEins(st, 5030, () -> List.of(a, v), t0.plusSeconds(t), LegendRegeln.GROB);
			if (!e.tage().isEmpty()) {
				break;
			}
			st = e.stand();
		}
		assertEquals(Map.of(d, 40), e.tage());
		EinsErgebnis f = LegendRegeln.legendEins(e.stand(), 5030, () -> List.of(a, v, b), t0.plusSeconds(1000),
				LegendRegeln.GROB);
		assertTrue(f.tage().isEmpty());
		assertTrue(f.stand().topf().isEmpty());
	}

	/**
	 * Die API lässt einen älteren Kampf für eine Antwort weg und liefert ihn dann
	 * wieder (21.11.2026 gesehen; am 20.11. zählte Perfect Day so 9 statt 8
	 * Angriffe). Er darf nicht noch einmal in den Topf.
	 */
	@Test
	void kampfVerschwindetAusDemProtokollUndKommtWieder() {
		Instant t0 = Instant.parse("2026-11-17T08:00:00Z");
		Eintrag a = new Eintrag("20261117T080010.000Z|A|150|#X", new LegendRegeln.Kampf(true, 3, 100, t0.plusSeconds(10)));
		Eintrag v = new Eintrag("20261117T080400.000Z|V|150|#Y", new LegendRegeln.Kampf(false, 2, 60, t0.plusSeconds(250)));
		EinsStand s = new EinsStand(5000, new HashSet<>(), null, 0, new HashSet<>(), null, null);
		EinsErgebnis e = LegendRegeln.legendEins(s, 5040, () -> List.of(a), t0.plusSeconds(30), LegendRegeln.GROB);
		e = LegendRegeln.legendEins(e.stand(), 5040, () -> List.of(a), t0.plusSeconds(160), LegendRegeln.GROB);
		assertEquals(Map.of(LocalDate.of(2026, 11, 17), 40), e.tage());
		// Verteidigung −19; in dieser Antwort fehlt der Angriff, in der nächsten ist er wieder da
		e = LegendRegeln.legendEins(e.stand(), 5021, () -> List.of(v), t0.plusSeconds(300), LegendRegeln.GROB);
		e = LegendRegeln.legendEins(e.stand(), 5021, () -> List.of(a, v), t0.plusSeconds(330), LegendRegeln.GROB);
		assertEquals(Set_of(v.schluessel()), e.stand().topf(), "der alte Angriff ist nicht wieder im Topf");
		e = LegendRegeln.legendEins(e.stand(), 5021, () -> List.of(a, v), t0.plusSeconds(460), LegendRegeln.GROB);
		assertTrue(e.tage().isEmpty());
		assertEquals(0, e.zuordnung().angriffe(), "kein neunter Angriff");
		// nach drei Tagen wird vergessen (das Protokoll reicht nicht so weit zurück)
		e = LegendRegeln.legendEins(e.stand(), 5030, List::of, t0.plus(Duration.ofDays(4)), LegendRegeln.GROB);
		assertTrue(e.stand().gesehen().isEmpty());
	}

	private static java.util.Set<String> Set_of(String... s) {
		return new HashSet<>(List.of(s));
	}
}
