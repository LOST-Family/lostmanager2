package lostmanager.legend;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import lostmanager.legend.LegendRegeln.ZweiErgebnis;
import lostmanager.legend.LegendRegeln.ZweiStand;
import lostmanager.legend.LegendRegeln.Zweikampf;

/**
 * Legend II (Trophy Hunt) nach der Formel, ohne Datenbank und ohne API. Der
 * Nachspielteil bildet nach, was am 21.11.2026 an 51 echten Protokollen
 * gemessen wurde: höchstens 16 Angriffe und 16 Verteidigungen im Protokoll,
 * Spieler bis 30 s und Protokoll bis 60 s im Zwischenspeicher.
 */
class LegendZweiTest {

	static final Instant WOCHE = Instant.parse("2026-12-21T05:00:00Z");
	static final LocalDate T21 = LocalDate.of(2026, 12, 21), T22 = LocalDate.of(2026, 12, 22);

	static Zweikampf a(String zeit, int sterne, int prozent) {
		return new Zweikampf(zeit + "|A", true, sterne, prozent, Instant.parse(zeit));
	}

	static Zweikampf v(String zeit, int sterne, int prozent) {
		return new Zweikampf(zeit + "|V", false, sterne, prozent, Instant.parse(zeit));
	}

	static ZweiStand leer() {
		return new ZweiStand(0, 0, new HashSet<>(), null);
	}

	static ZweiStand nach(ZweiErgebnis e) {
		return e.stand();
	}

	@Test
	void einAngriffWirdNachDerFormelGebucht() {
		Instant jetzt = Instant.parse("2026-12-21T08:00:00Z");
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 26, () -> List.of(a("2026-12-21T07:55:00Z", 2, 80)), WOCHE, jetzt);
		assertEquals(Map.of(T21, 26), e.tage());
		assertEquals(Map.of(T21, 1), e.anzahl());
		assertEquals(26, e.stand().pokale());
		assertNull(e.stand().offenSeit());
		assertFalse(e.unvollstaendig());
		// derselbe Kampf noch einmal im Protokoll: nichts doppelt
		ZweiErgebnis e2 = LegendRegeln.legendZwei(nach(e), 26, () -> fail("ohne Änderung kein Protokollabruf"), WOCHE,
				jetzt.plusSeconds(30));
		assertTrue(e2.tage().isEmpty());
	}

	@Test
	void verteidigungBringtDenRestVonVierzigUndZaehltNichtAlsAngriff() {
		Instant jetzt = Instant.parse("2026-12-21T08:00:00Z");
		// Angriff 3 Sterne (40) und Verteidigung 1 Stern 50 % (Angreifer 10, wir 30) im selben Fenster
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 70,
				() -> List.of(a("2026-12-21T07:50:00Z", 3, 100), v("2026-12-21T07:55:00Z", 1, 50)), WOCHE, jetzt);
		assertEquals(Map.of(T21, 40), e.tage());
		assertEquals(70, e.stand().pokale());
		assertNull(e.stand().offenSeit());
		assertEquals(30, LegendRegeln.legendZweiVerteidigung(1, 50));
	}

	@Test
	void pokalstandVorDemProtokollWartetUndBuchtDannGenauEinmal() {
		Instant t = Instant.parse("2026-12-21T08:00:00Z");
		List<Zweikampf> log = new ArrayList<>();
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 40, () -> log, WOCHE, t);
		assertTrue(e.tage().isEmpty());
		assertEquals(40, e.stand().rest(), "unerklärte Pokale bleiben offen");
		assertEquals(t, e.stand().offenSeit());
		log.add(a("2026-12-21T07:59:00Z", 3, 100));
		e = LegendRegeln.legendZwei(nach(e), 40, () -> log, WOCHE, t.plusSeconds(30));
		assertEquals(Map.of(T21, 40), e.tage());
		assertNull(e.stand().offenSeit());
		assertEquals(40, e.stand().pokale());
	}

	@Test
	void protokollVorDemPokalstandBuchtSofortUndGleichtSpaeterAus() {
		Instant t = Instant.parse("2026-12-21T08:00:00Z");
		// Eine Verteidigung (wir bekommen 30) löst den Blick ins Protokoll aus – und dort
		// steht schon der nächste Angriff, den der Pokalstand noch nicht zeigt.
		List<Zweikampf> log = List.of(v("2026-12-21T07:50:00Z", 1, 50), a("2026-12-21T07:59:00Z", 3, 100));
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 30, () -> log, WOCHE, t);
		assertEquals(Map.of(T21, 40), e.tage(), "der Angriff zählt, sobald er im Protokoll steht");
		assertNotNull(e.stand().offenSeit());
		e = LegendRegeln.legendZwei(nach(e), 70, () -> log, WOCHE, t.plusSeconds(30));
		assertTrue(e.tage().isEmpty());
		assertNull(e.stand().offenSeit());
		assertEquals(0, e.unerklaert());
	}

	@Test
	void unerklaerterRestWirdNachSechsMinutenUebernommenOhneAngriffeZuErfinden() {
		Instant t = Instant.parse("2026-12-21T08:00:00Z");
		List<Zweikampf> log = List.of(a("2026-12-21T07:00:00Z", 3, 100));
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 40, () -> log, WOCHE, t);
		e = LegendRegeln.legendZwei(nach(e), 47, () -> log, WOCHE, t.plusSeconds(60));
		assertEquals(7, e.stand().rest());
		e = LegendRegeln.legendZwei(nach(e), 47, () -> log, WOCHE, t.plusSeconds(60 + 300));
		assertEquals(0, e.unerklaert(), "noch in der Frist");
		e = LegendRegeln.legendZwei(nach(e), 47, () -> log, WOCHE, t.plusSeconds(60 + 361));
		assertEquals(7, e.unerklaert());
		assertEquals(47, e.stand().pokale());
		assertEquals(0, e.stand().rest());
		assertTrue(e.tage().isEmpty());
		assertFalse(e.unvollstaendig(), "der bekannte Angriff steht noch im Protokoll: es fehlt keiner");
	}

	@Test
	void nachEinerLueckeOhneAnschlussUnvollstaendig() {
		Instant t = Instant.parse("2026-12-22T08:00:00Z");
		ZweiStand vorher = new ZweiStand(300, 0, new HashSet<>(List.of("2026-12-21T07:00:00Z|A")), null);
		// 16 neue Angriffe im Protokoll, der bekannte ist herausgefallen, der Pokalstand
		// liegt 80 über dem, was die 16 erklären: es fehlen Angriffe.
		List<Zweikampf> log = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			log.add(a("2026-12-22T06:" + String.format("%02d", 10 + i) + ":00Z", 3, 100));
		}
		ZweiErgebnis e = LegendRegeln.legendZwei(vorher, 300 + 16 * 40 + 80, () -> log, WOCHE, t);
		assertEquals(Map.of(T22, 640), e.tage());
		assertFalse(e.unvollstaendig(), "erst abwarten: das Protokoll könnte nur hinterherhinken");
		assertTrue(e.stand().ohneAnschluss());
		e = LegendRegeln.legendZwei(nach(e), 300 + 16 * 40 + 80, () -> log, WOCHE, t.plusSeconds(361));
		assertTrue(e.unvollstaendig(), "nach sechs Minuten Stillstand steht fest, dass etwas fehlt");
		assertEquals(80, e.unerklaert());
		assertTrue(e.tage().isEmpty());
		// Passt der Pokalstand genau, fehlt nichts – auch ohne Anschluss
		e = LegendRegeln.legendZwei(vorher, 300 + 16 * 40, () -> log, WOCHE, t);
		assertFalse(e.unvollstaendig());
		assertFalse(e.stand().ohneAnschluss());
	}

	@Test
	void neulingMitHinkendemProtokollIstNichtUnvollstaendig() {
		// Erste Ranked-Angriffe überhaupt, zwei kurz hintereinander: der Pokalstand zeigt
		// schon beide, das Protokoll erst einen. Kein Anschluss, aber es fehlt nichts.
		Instant t = Instant.parse("2026-12-21T08:00:00Z");
		List<Zweikampf> log = new ArrayList<>(List.of(a("2026-12-21T07:55:00Z", 3, 100)));
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 80, () -> log, WOCHE, t);
		assertFalse(e.unvollstaendig());
		assertEquals(40, e.stand().rest());
		log.add(a("2026-12-21T07:58:00Z", 3, 100));
		e = LegendRegeln.legendZwei(nach(e), 80, () -> log, WOCHE, t.plusSeconds(30));
		assertEquals(Map.of(T21, 40), e.tage());
		assertFalse(e.unvollstaendig());
		assertFalse(e.stand().ohneAnschluss());
		assertNull(e.stand().offenSeit());
	}

	@Test
	void ersteSichtungNimmtNurDieWocheUndMerktAlles() {
		Instant t = Instant.parse("2026-12-21T09:00:00Z");
		List<Zweikampf> log = List.of(a("2026-12-20T20:00:00Z", 3, 100), v("2026-12-21T04:59:00Z", 0, 30),
				a("2026-12-21T05:00:00Z", 2, 50), a("2026-12-21T08:00:00Z", 1, 99));
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 16 + 15, () -> log, WOCHE, t);
		assertEquals(Map.of(T21, 31), e.tage());
		assertEquals(Map.of(T21, 2), e.anzahl());
		assertEquals(2, e.stand().gesehen().size(), "gemerkt werden nur Kämpfe der Woche");
		assertFalse(e.unvollstaendig());
		// Mitten in der Woche dazugekommen, Protokoll reicht nicht bis Wochenbeginn
		List<Zweikampf> spaet = List.of(a("2026-12-23T08:00:00Z", 3, 100));
		e = LegendRegeln.legendZwei(leer(), 900, () -> spaet, WOCHE, Instant.parse("2026-12-23T09:00:00Z"));
		assertEquals(860, e.stand().rest());
		assertTrue(e.stand().ohneAnschluss());
		e = LegendRegeln.legendZwei(nach(e), 900, () -> spaet, WOCHE, Instant.parse("2026-12-23T09:07:00Z"));
		assertTrue(e.unvollstaendig());
	}

	/** Im Trockenlauf am 21.11.2026 gesehen: so wäre ein Angriff vom 17.11. doppelt gebucht worden. */
	@Test
	void kampfVerschwindetAusDemProtokollUndKommtWieder() {
		Instant t = Instant.parse("2026-12-22T08:00:00Z");
		Zweikampf alt = a("2026-12-21T09:51:00Z", 1, 71), v1 = v("2026-12-22T07:55:00Z", 2, 75),
				v2 = v("2026-12-22T08:20:00Z", 3, 100), neu = a("2026-12-22T08:25:00Z", 3, 100);
		ZweiErgebnis e = LegendRegeln.legendZwei(leer(), 12, () -> List.of(alt), WOCHE, t.minusSeconds(3600));
		assertEquals(Map.of(T21, 12), e.tage());
		// Verteidigung +16; in dieser Antwort fehlt der alte Angriff
		e = LegendRegeln.legendZwei(nach(e), 28, () -> List.of(v1), WOCHE, t);
		assertTrue(e.tage().isEmpty());
		assertTrue(e.stand().gesehen().contains(alt.schluessel()), "verbucht bleibt verbucht");
		// nächste Bewegung: der alte Angriff ist wieder da, dazu ein neuer
		e = LegendRegeln.legendZwei(nach(e), 68, () -> List.of(alt, v1, v2, neu), WOCHE, t.plusSeconds(1800));
		assertEquals(Map.of(T22, 40), e.tage(), "nur der neue Angriff");
		assertEquals(Map.of(T22, 1), e.anzahl());
		assertEquals(0, e.stand().rest());
	}

	@Test
	void uebergangVomAltenVerfahrenBuchtNichtsNach() {
		ZweiErgebnis e = LegendRegeln.legendZwei(new ZweiStand(500, 0, null, null), 500,
				() -> List.of(a("2026-12-21T07:00:00Z", 3, 100)), WOCHE, Instant.parse("2026-12-21T08:00:00Z"));
		assertTrue(e.tage().isEmpty());
		assertEquals(1, e.stand().gesehen().size());
	}

	@Test
	void protokollNichtAbrufbarLaesstDenStand() {
		assertNull(LegendRegeln.legendZwei(leer(), 40, () -> null, WOCHE, Instant.parse("2026-12-21T08:00:00Z")));
	}

	@Test
	void abschlussTraegtDieLetztenAngriffeDerWocheNach() {
		ZweiStand vorher = new ZweiStand(1000, 0, new HashSet<>(List.of("2026-12-27T20:00:00Z|A")), null);
		List<Zweikampf> log = List.of(a("2026-12-27T20:00:00Z", 3, 100), a("2026-12-28T04:50:00Z", 2, 71),
				v("2026-12-28T04:55:00Z", 3, 100), a("2026-12-28T05:01:00Z", 3, 100));
		ZweiErgebnis e = LegendRegeln.legendZweiAbschluss(vorher, log, WOCHE);
		assertEquals(Map.of(LocalDate.of(2026, 12, 27), 23), e.tage(), "04:50 gehört zum Legend-Tag 27.12., 05:01 zur neuen Woche");
		assertEquals(0, e.unerklaert());
		assertFalse(e.unvollstaendig());
		assertTrue(LegendRegeln.legendZweiAbschluss(new ZweiStand(1000, 0, null, null), log, WOCHE).tage().isEmpty());
	}

	// ------------------------------------------------------------------
	// Nachspiel einer ganzen Woche
	// ------------------------------------------------------------------

	record Sim(Zweikampf kampf, Instant wirkt, Instant imLog) {
	}

	/** Protokoll zu einem Zeitpunkt: nur die letzten 16 Angriffe und 16 Verteidigungen. */
	static List<Zweikampf> protokoll(List<Sim> sims, Instant jetzt) {
		List<Zweikampf> ang = new ArrayList<>(), ver = new ArrayList<>();
		for (Sim s : sims) {
			if (!s.imLog().isAfter(jetzt)) {
				(s.kampf().angriff() ? ang : ver).add(s.kampf());
			}
		}
		List<Zweikampf> log = new ArrayList<>(ang.subList(Math.max(0, ang.size() - 16), ang.size()));
		log.addAll(ver.subList(Math.max(0, ver.size() - 16), ver.size()));
		return log;
	}

	static int pokale(List<Sim> sims, Instant jetzt) {
		int p = 0;
		for (Sim s : sims) {
			if (!s.wirkt().isAfter(jetzt)) {
				int x = LegendRegeln.legendEinsPokale(s.kampf().sterne(), s.kampf().prozent());
				p += s.kampf().angriff() ? x : 40 - x;
			}
		}
		return p;
	}

	/** Eine Woche: jeden Tag eine Serie von 16 Angriffen im Minutentakt, Verteidigungen dazwischen. */
	static List<Sim> woche(long standVerzug, long logVerzug) {
		List<Sim> sims = new ArrayList<>();
		java.util.Random r = new java.util.Random(2026);
		for (int tag = 0; tag < 7; tag++) {
			Instant serie = WOCHE.plus(Duration.ofDays(tag)).plus(Duration.ofHours(3 + r.nextInt(18)));
			for (int i = 0; i < 16; i++) {
				Instant b = serie.plusSeconds(i * 75L + r.nextInt(20));
				int sterne = r.nextInt(4);
				Zweikampf k = new Zweikampf(b + "|A|" + i, true, sterne, sterne == 3 ? 100 : 20 + r.nextInt(79), b);
				sims.add(new Sim(k, b.plusSeconds(60 + r.nextInt((int) standVerzug + 1)),
						b.plusSeconds(60 + r.nextInt((int) logVerzug + 1))));
			}
			for (int i = 0; i < 10; i++) {
				Instant b = WOCHE.plus(Duration.ofDays(tag)).plusSeconds(r.nextInt(86000));
				int sterne = r.nextInt(4);
				Zweikampf k = new Zweikampf(b + "|V|" + i, false, sterne, sterne == 3 ? 100 : 20 + r.nextInt(79), b);
				sims.add(new Sim(k, b.plusSeconds(120 + r.nextInt((int) standVerzug + 1)),
						b.plusSeconds(120 + r.nextInt((int) logVerzug + 1))));
			}
		}
		sims.sort((x, y) -> x.kampf().beginn().compareTo(y.kampf().beginn()));
		return sims;
	}

	record Lauf(Map<LocalDate, Integer> gebucht, int angriffe, int unerklaert, boolean unvollstaendig) {
	}

	static Lauf nachspielen(List<Sim> sims, int taktSek, Instant pauseVon, Instant pauseBis) {
		Map<LocalDate, Integer> gebucht = new TreeMap<>();
		int angriffe = 0, unerklaert = 0;
		boolean unvoll = false;
		ZweiStand stand = leer();
		for (Instant t = WOCHE.plusSeconds(11); t.isBefore(WOCHE.plus(Duration.ofDays(7)).plus(Duration.ofMinutes(20))); t = t
				.plusSeconds(taktSek)) {
			if (pauseVon != null && t.isAfter(pauseVon) && t.isBefore(pauseBis)) {
				continue;
			}
			final Instant jetzt = t;
			ZweiErgebnis e = LegendRegeln.legendZwei(stand, pokale(sims, jetzt), () -> protokoll(sims, jetzt), WOCHE, jetzt);
			e.tage().forEach((d, w) -> gebucht.merge(d, w, Integer::sum));
			angriffe += e.anzahl().values().stream().mapToInt(Integer::intValue).sum();
			unerklaert += Math.abs(e.unerklaert());
			unvoll |= e.unvollstaendig();
			stand = e.stand();
		}
		return new Lauf(gebucht, angriffe, unerklaert, unvoll);
	}

	static Map<LocalDate, Integer> soll(List<Sim> sims) {
		Map<LocalDate, Integer> soll = new TreeMap<>();
		for (Sim s : sims) {
			if (s.kampf().angriff()) {
				soll.merge(LegendRegeln.legendTag(s.kampf().beginn()),
						LegendRegeln.legendEinsPokale(s.kampf().sterne(), s.kampf().prozent()), Integer::sum);
			}
		}
		return soll;
	}

	@Test
	void ganzeWocheMitSerienWirdExaktGebucht() {
		for (long[] verzug : new long[][] { { 0, 0 }, { 30, 60 }, { 60, 30 }, { 5, 170 }, { 170, 5 } }) {
			List<Sim> sims = woche(verzug[0], verzug[1]);
			for (int takt : new int[] { 30, 47, 120 }) {
				Lauf l = nachspielen(sims, takt, null, null);
				String fall = "Verzug Stand " + verzug[0] + " s, Protokoll " + verzug[1] + " s, Takt " + takt + " s";
				assertEquals(soll(sims), l.gebucht(), fall);
				assertEquals(7 * 16, l.angriffe(), fall);
				assertEquals(0, l.unerklaert(), fall);
				assertFalse(l.unvollstaendig(), fall);
			}
		}
	}

	@Test
	void ausfallWaehrendEinerSerieWirdAlsUnvollstaendigErkanntOderNachgeholt() {
		List<Sim> sims = woche(30, 60);
		Map<LocalDate, Integer> soll = soll(sims);
		// Kurzer Ausfall (20 Minuten, eine ganze Serie): die 16 Angriffe stehen danach noch im Protokoll
		Instant serie = sims.stream().filter(s -> s.kampf().angriff()).skip(32).findFirst().get().kampf().beginn();
		Lauf kurz = nachspielen(sims, 30, serie.minusSeconds(120), serie.plus(Duration.ofMinutes(25)));
		assertEquals(soll, kurz.gebucht(), "eine Serie von 16 übersteht einen Ausfall");
		assertFalse(kurz.unvollstaendig());
		// Langer Ausfall über zwei Serien (32 Angriffe): 16 fehlen – und das wird gemerkt
		Instant ende = sims.stream().filter(s -> s.kampf().angriff()).skip(63).findFirst().get().imLog();
		Lauf lang = nachspielen(sims, 30, serie.minusSeconds(120), ende.plusSeconds(600));
		int gebucht = lang.gebucht().values().stream().mapToInt(Integer::intValue).sum();
		int gesamt = soll.values().stream().mapToInt(Integer::intValue).sum();
		assertTrue(gebucht < gesamt, "es fehlen Angriffe");
		assertEquals(6 * 16, lang.angriffe());
		assertTrue(lang.unvollstaendig(), "die Lücke ist ausgewiesen");
	}
}
