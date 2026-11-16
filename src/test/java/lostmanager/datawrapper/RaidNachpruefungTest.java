package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import lostmanager.datawrapper.ListeningEvent.RaidPruefEntscheid;
import lostmanager.datawrapper.ListeningEvent.RaidPruefung;
import lostmanager.datawrapper.ListeningEvent.RaidTeilnahme;

/**
 * Raid-Kickpunkte erst nach Raid-Ende: die Auswertung läuft in der Nachprüfung auf frischen
 * Daten, nicht auf dem Stand von 07:00:00. Nachgebaute API-Antworten unter
 * {@code src/test/resources/raid/} (Felder wie /clans/{tag}/capitalraidseasons?limit=1).
 */
class RaidNachpruefungTest {

	private static final String START = "20261113T070000.000Z";
	private static final String ENDE = "20261116T070000.000Z";
	private static final long ENDE_MS = Instant.parse("2026-11-16T07:00:00Z").toEpochMilli();
	private static final long MINUTE = 60_000L;
	private static final int VERSUCHE = ListeningEvent.RAID_VERIFICATION_DELAYS_MINUTES.length;

	/** Pflichtige Mitglieder laut Datenbank. */
	private static final List<String> MITGLIEDER = List.of("#AAA111", "#BBB222", "#CCC333", "#DDD444");

	private static JSONObject stand(String datei) throws IOException {
		try (InputStream in = RaidNachpruefungTest.class.getResourceAsStream("/raid/" + datei)) {
			return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	/** Wer einen Kickpunkt bekäme: nicht dabei oder Angriffe offen. */
	private static List<String> bestraft(RaidTeilnahme t) {
		java.util.ArrayList<String> l = new java.util.ArrayList<>(t.nichtDabei);
		l.addAll(t.offen.keySet());
		return l;
	}

	@Test
	void angriffInLetzterMinuteBekommtKeinenKickpunkt() throws IOException {
		// Stand 07:00:00 - so hat das Event bisher sofort vergeben
		RaidTeilnahme um0700 = ListeningEvent.raidTeilnahmeAuswerten(MITGLIEDER, stand("stand-0700-ongoing.json"));
		assertEquals(List.of("#DDD444"), um0700.nichtDabei);
		assertEquals(5, um0700.offen.get("#AAA111")[0]);
		assertEquals(6, um0700.offen.get("#AAA111")[1]);

		// Nachprüfung 07:05: derselbe Raid, die Uhr steht hinter Ende + Gnadenfrist
		JSONObject frisch = stand("stand-0705-ongoing.json");
		RaidPruefung p = ListeningEvent.raidPruefEntscheid(frisch, START, ENDE, ENDE_MS + 5 * MINUTE + 1000, 1,
				VERSUCHE);
		assertEquals(RaidPruefEntscheid.AUSWERTEN, p.entscheid());
		assertTrue(p.sameRaid());
		assertTrue(p.overByClock());

		RaidTeilnahme um0705 = ListeningEvent.raidTeilnahmeAuswerten(MITGLIEDER, frisch);
		// 6/6 und der Spätstarter in letzter Minute: kein Kickpunkt mehr
		assertFalse(um0705.offen.containsKey("#AAA111"));
		assertFalse(um0705.nichtDabei.contains("#DDD444"));
		// Wer wirklich nicht fertig ist, bleibt es
		assertEquals(List.of("#CCC333"), bestraft(um0705));
		assertEquals(3, um0705.offen.get("#CCC333")[0]);
	}

	@Test
	void zuFruehePruefungWirdWiederholt() throws IOException {
		// 07:03, API sagt noch "ongoing": Angriffe könnten noch auslaufen
		RaidPruefung p = ListeningEvent.raidPruefEntscheid(stand("stand-0705-ongoing.json"), START, ENDE,
				ENDE_MS + 3 * MINUTE, 1, VERSUCHE);
		assertEquals(RaidPruefEntscheid.WIEDERHOLEN, p.entscheid());
		assertTrue(p.sameRaid());
		assertFalse(p.overByClock());
		// Erster Versuch nie vor 5 Minuten
		assertEquals(5, ListeningEvent.RAID_VERIFICATION_DELAYS_MINUTES[0]);
		assertEquals(5 * MINUTE, ListeningEvent.RAID_END_GRACE_MS);
	}

	@Test
	void apiMeldetUm0700SchonEndedAuswertungLaeuftTrotzdem() throws IOException {
		JSONObject stand = stand("stand-0700-ended.json");
		Long apiEnde = ListeningEvent.parseRaidEndTime(stand.getJSONArray("items").getJSONObject(0)
				.getString("endTime"));

		// Event zum Raid-Ende (Dauer 0), Feuerzeit = Raid-Ende: darf feuern statt condition_gone
		assertTrue(ListeningEvent.raidEndauswertungZulassen(0, apiEnde, ENDE_MS));
		// auch bei leicht verschobener Feuerzeit
		assertTrue(ListeningEvent.raidEndauswertungZulassen(0, apiEnde, ENDE_MS - 2 * MINUTE));

		// "ended" zählt auch schon vor Ablauf der Gnadenfrist als endgültig
		RaidPruefung p = ListeningEvent.raidPruefEntscheid(stand, START, ENDE, ENDE_MS + 30_000, 1, VERSUCHE);
		assertEquals(RaidPruefEntscheid.AUSWERTEN, p.entscheid());
		assertEquals(List.of("#CCC333"), bestraft(ListeningEvent.raidTeilnahmeAuswerten(MITGLIEDER, stand)));
	}

	@Test
	void beendeterRaidZaehltNichtFuerErinnerungenOderDenLetztenRaid() {
		// Erinnerung (Dauer > 0) über einen schon beendeten Raid: bleibt condition_gone
		assertFalse(ListeningEvent.raidEndauswertungZulassen(2 * 60 * MINUTE, ENDE_MS, ENDE_MS - 2 * 60 * MINUTE));
		// Ende des Raids von letzter Woche passt nicht zur Feuerzeit dieser Woche
		assertFalse(ListeningEvent.raidEndauswertungZulassen(0, ENDE_MS - 7 * 24 * 60 * MINUTE, ENDE_MS));
		// Kein Ende bekannt (API-Ersatzantwort)
		assertFalse(ListeningEvent.raidEndauswertungZulassen(0, null, ENDE_MS));
	}

	@Test
	void andererRaidWirdNichtAusgewertet() throws IOException {
		// Nächster Raid hat schon begonnen
		JSONObject naechster = stand("stand-naechster-raid.json");
		RaidPruefung p = ListeningEvent.raidPruefEntscheid(naechster, START, ENDE, ENDE_MS + 5 * MINUTE + 1000, 1,
				VERSUCHE);
		assertFalse(p.sameRaid());
		assertEquals(RaidPruefEntscheid.WIEDERHOLEN, p.entscheid());
		assertEquals(RaidPruefEntscheid.AUFGEBEN, ListeningEvent
				.raidPruefEntscheid(naechster, START, ENDE, ENDE_MS + 30 * MINUTE, VERSUCHE, VERSUCHE).entscheid());

		// Gleiches Ende, aber falsche Startzeit: auch nicht derselbe Raid
		JSONObject falscherStart = stand("stand-0700-ended.json");
		falscherStart.getJSONArray("items").getJSONObject(0).put("startTime", "20261106T070000.000Z");
		RaidPruefung q = ListeningEvent.raidPruefEntscheid(falscherStart, START, ENDE, ENDE_MS + 5 * MINUTE + 1000,
				1, VERSUCHE);
		assertFalse(q.sameRaid());
		assertEquals(RaidPruefEntscheid.WIEDERHOLEN, q.entscheid());

		// API-Ausfall (Ersatzantwort ohne Zeiten) ist nie verlässlich
		RaidPruefung r = ListeningEvent.raidPruefEntscheid(new JSONObject("{\"items\":[{\"state\":\"ended\"}]}"),
				START, ENDE, ENDE_MS + 5 * MINUTE + 1000, 1, VERSUCHE);
		assertFalse(r.sameRaid());
		assertEquals(RaidPruefEntscheid.WIEDERHOLEN, r.entscheid());
	}

	@Test
	void mehrereVersucheVergebenHoechstensEinmal() throws IOException {
		long eventId = -186L; // eigene ID, damit kein anderer Test dieselbe Sperre trifft
		JSONObject[] staende = { stand("stand-naechster-raid.json"), new JSONObject("{\"items\":[{\"state\":\"ended\"}]}"),
				stand("stand-0705-ongoing.json"), stand("stand-0700-ended.json") };

		// Ablauf wie raidNachpruefen: weiter nur bei WIEDERHOLEN, Vergabe nur bei AUSWERTEN
		int auswertungen = 0;
		int vergaben = 0;
		for (int versuch = 1; versuch <= VERSUCHE; versuch++) {
			RaidPruefung p = ListeningEvent.raidPruefEntscheid(staende[versuch - 1], START, ENDE,
					ENDE_MS + (5L * versuch + 1) * MINUTE, versuch, VERSUCHE);
			if (p.entscheid() == RaidPruefEntscheid.WIEDERHOLEN) {
				continue;
			}
			if (p.entscheid() == RaidPruefEntscheid.AUSWERTEN) {
				auswertungen++;
				if (ListeningEvent.raidAuswertungBeanspruchen("vergabe", eventId, START, ENDE)) {
					vergaben++;
				}
			}
			break;
		}
		assertEquals(1, auswertungen);
		assertEquals(1, vergaben);

		// Ein zweiter Durchlauf für dasselbe Event und denselben Raid (Retry, doppelt
		// eingeplant) vergibt nichts mehr und plant auch nichts mehr ein
		assertFalse(ListeningEvent.raidAuswertungBeanspruchen("vergabe", eventId, START, ENDE));
		assertTrue(ListeningEvent.raidAuswertungBeanspruchen("plan", eventId, START, ENDE));
		assertFalse(ListeningEvent.raidAuswertungBeanspruchen("plan", eventId, START, ENDE));

		// Nächste Woche ist ein neuer Raid, ein anderes Event zählt für sich
		assertTrue(ListeningEvent.raidAuswertungBeanspruchen("vergabe", eventId, "20261120T070000.000Z",
				"20261123T070000.000Z"));
		assertTrue(ListeningEvent.raidAuswertungBeanspruchen("vergabe", -187L, START, ENDE));
	}

	@Test
	void teilnahmeOhneRaidDatenZaehltAlleAlsNichtDabei() {
		// Darum wirft baueRaidTeilnahme vorher bei raidDatenFehlen - hier nur die reine Auswertung
		RaidTeilnahme t = ListeningEvent.raidTeilnahmeAuswerten(MITGLIEDER,
				new JSONObject("{\"items\":[{\"state\":\"ended\"}]}"));
		assertEquals(MITGLIEDER, t.nichtDabei);
		assertTrue(t.offen.isEmpty());
	}
}
