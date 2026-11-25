package lostmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import lostmanager.datawrapper.ListeningEvent;
import lostmanager.util.ListeningEventFormular.Art;

class ListeningEventFormularTest {

	@Test
	void artWieFrueherImBefehl() {
		assertEquals(Art.CS_THRESHOLD, ListeningEventFormular.art("cs", "infomessage"));
		assertEquals(Art.CS_THRESHOLD, ListeningEventFormular.art("cs", "kickpoint"));
		assertEquals(Art.CW_ATTACKS, ListeningEventFormular.art("cw", "kickpoint"));
		assertEquals(Art.SEASONWINS, ListeningEventFormular.art("seasonend", "infomessage"));
		assertEquals(Art.CWCOUNT, ListeningEventFormular.art("seasonend", "cwcount_kickpoint"));
		assertEquals(Art.CWLDAYKP, ListeningEventFormular.art("cwlday", "kickpoint"));
		assertEquals(Art.RAIDFAILS, ListeningEventFormular.art("raid", "raidfails_kickpoint"));
		assertEquals(Art.CUSTOMMESSAGE, ListeningEventFormular.art("cw", "custommessage"));
		assertEquals(Art.CWDONATOR, ListeningEventFormular.art("cw", "cwdonator"));
		assertEquals(Art.STARFAILS, ListeningEventFormular.art("cwlday", "starfails_kickpoint"));
		// Ohne Formular
		assertNull(ListeningEventFormular.art("cw", "filler"));
		assertNull(ListeningEventFormular.art("raid", "infomessage"));
		assertNull(ListeningEventFormular.art("cwlday", "infomessage"));
	}

	@Test
	void varianten() {
		assertEquals(List.of("required_attacks"),
				ListeningEventFormular.feldIds(Art.CW_ATTACKS, "cw", "infomessage", false));
		assertEquals(List.of("required_attacks", "ignore_perfect_war"),
				ListeningEventFormular.feldIds(Art.CW_ATTACKS, "cw", "kickpoint", true));
		assertEquals(List.of("capital_peak_max", "other_districts_max"),
				ListeningEventFormular.feldIds(Art.RAIDFAILS, "raid", "raidfails", false));
		assertEquals(4, ListeningEventFormular.feldIds(Art.RAIDFAILS, "raid", "raidfails_kickpoint", true).size());
		assertEquals(List.of("star_count", "starfails_free_hits"),
				ListeningEventFormular.feldIds(Art.STARFAILS, "cwlday", "starfails", false));
		assertEquals(List.of("star_count", "punishment_mode", "starfails_free_hits"),
				ListeningEventFormular.feldIds(Art.STARFAILS, "cw", "starfails", false));
	}

	@Test
	void modalPraefixeEindeutig() {
		for (Art a : Art.values()) {
			assertEquals(a, Art.ausModalId(a.praefix + "abc123"));
			assertTrue(a.titel.length() <= 45, "Discord erlaubt höchstens 45 Zeichen Titel: " + a.titel);
			for (var f : ListeningEventFormular.felder(a, "cw", "kickpoint", true)) {
				assertTrue(f.label().length() <= 45, f.label());
				assertTrue(f.platzhalter().length() <= 100, f.platzhalter());
			}
		}
		assertNull(Art.ausModalId("listeningevent_edit_abc"));
	}

	private static ListeningEventFormular.Lesung lies(Art art, String type, boolean grund, Map<String, String> m) {
		return ListeningEventFormular.lies(art, type, grund, m::get);
	}

	@Test
	void liesGueltig() {
		var w = lies(Art.CW_ATTACKS, "cw", true, Map.of("required_attacks", " 2 ", "ignore_perfect_war", "1")).werte();
		assertEquals(2, w.thresholdOrAttacks);
		assertEquals(1L, w.namedSettings.get(ListeningEvent.SETTING_IGNORE_PERFECT_WAR));

		w = lies(Art.SEASONWINS, "seasonend", false, Map.of("wins_threshold", "")).werte();
		assertTrue(w.namedSettings.isEmpty(), "leer = Clan-Einstellung");

		w = lies(Art.RAIDFAILS, "raid", false, Map.of("capital_peak_max", "10", "other_districts_max", "6")).werte();
		assertEquals(1, w.raidDistrictThresholds.get("penalize_both"));
		assertFalse(w.namedSettings.containsKey(ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS));

		w = lies(Art.STARFAILS, "cwlday", true, Map.of("star_count", "0", "starfails_free_hits", "1")).werte();
		assertEquals(0, w.starCount);
		assertEquals(1, w.punishmentMode, "in der CWL ohne Feld: Modus 1 wie bisher");
		assertEquals(1L, w.namedSettings.get(ListeningEvent.SETTING_STARFAILS_FREE_HITS));

		w = lies(Art.CWDONATOR, "cw", false, Map.of("use_lists", "1", "exclude_leaders", "0")).werte();
		assertEquals(1, w.useLists);
		assertEquals(0, w.excludeLeaders);
		assertFalse(w.namedSettings.containsKey(ListeningEvent.SETTING_CWDONATOR_MIN_TH),
				"ohne Mindest-RH bleibt alles wie bisher");

		w = lies(Art.CWDONATOR, "cw", false,
				Map.of("use_lists", "0", "exclude_leaders", "0", "cwdonator_min_th", " 15 ")).werte();
		assertEquals(15L, w.namedSettings.get(ListeningEvent.SETTING_CWDONATOR_MIN_TH));

		w = lies(Art.CWDONATOR, "cw", false,
				Map.of("use_lists", "0", "exclude_leaders", "0", "cwdonator_min_th", "")).werte();
		assertFalse(w.namedSettings.containsKey(ListeningEvent.SETTING_CWDONATOR_MIN_TH));
	}

	@Test
	void liesUngueltigMitDenAltenMeldungen() {
		assertEquals("Ungültiger Wert für Angriffe: 3 (Erlaubt: 1 oder 2)",
				lies(Art.CW_ATTACKS, "cw", false, Map.of("required_attacks", "3")).fehler());
		assertEquals("Ungültiger Threshold-Wert: abc",
				lies(Art.CS_THRESHOLD, "cs", false, Map.of("threshold", "abc")).fehler());
		assertEquals("Ungültiger Wert für Minimum CWs.",
				lies(Art.CWCOUNT, "seasonend", false, Map.of("cw_min_count", "0")).fehler());
		assertNotNull(lies(Art.RAIDFAILS, "raid", true,
				Map.of("capital_peak_max", "10", "other_districts_max", "6", "penalize_both", "3")).fehler());
		assertNotNull(lies(Art.STARFAILS, "cw", false,
				Map.of("star_count", "3", "punishment_mode", "1", "starfails_free_hits", "0")).fehler());
		assertNotNull(lies(Art.CWDONATOR, "cw", false, Map.of("use_lists", "2", "exclude_leaders", "0")).fehler());
		assertNotNull(lies(Art.CWDONATOR, "cw", false,
				Map.of("use_lists", "0", "exclude_leaders", "0", "cwdonator_min_th", "19")).fehler());
		assertNotNull(lies(Art.CWDONATOR, "cw", false,
				Map.of("use_lists", "0", "exclude_leaders", "0", "cwdonator_min_th", "zehn")).fehler());
		assertNotNull(lies(Art.CUSTOMMESSAGE, "cw", false, Map.of("custommessage", "   ")).fehler());
	}

	/** Was vorbelegt wird, muss beim Abschicken wieder genau dasselbe ergeben. */
	@Test
	void vorbelegungUndLesenSindUmkehrbar() {
		Object[][] faelle = {
				{ Art.CS_THRESHOLD, "cs", "kickpoint", true, Map.of("threshold", "4000") },
				{ Art.CW_ATTACKS, "cw", "kickpoint", true, Map.of("required_attacks", "1", "ignore_perfect_war", "1") },
				{ Art.SEASONWINS, "seasonend", "kickpoint", true, Map.of("wins_threshold", "70") },
				{ Art.CWCOUNT, "seasonend", "cwcount", false, Map.of("cw_min_count", "8") },
				{ Art.CWLDAYKP, "cwlday", "kickpoint", true, Map.of("ignore_perfect_war", "2") },
				{ Art.RAIDFAILS, "raid", "raidfails_kickpoint", true, Map.of("capital_peak_max", "9",
						"other_districts_max", "5", "penalize_both", "2", "raid_force_kickpoints", "1") },
				{ Art.CUSTOMMESSAGE, "cw", "custommessage", false, Map.of("custommessage", "Hallo\nWelt") },
				{ Art.CWDONATOR, "cw", "cwdonator", false, Map.of("use_lists", "1", "exclude_leaders", "1") },
				{ Art.CWDONATOR, "cw", "cwdonator", false,
						Map.of("use_lists", "0", "exclude_leaders", "1", "cwdonator_min_th", "16") },
				{ Art.STARFAILS, "cw", "starfails_kickpoint", true,
						Map.of("star_count", "1", "punishment_mode", "3", "starfails_free_hits", "2") }, };
		for (Object[] f : faelle) {
			Art art = (Art) f[0];
			String type = (String) f[1];
			boolean grund = (Boolean) f[3];
			@SuppressWarnings("unchecked")
			Map<String, String> eingabe = (Map<String, String>) f[4];
			var l = lies(art, type, grund, eingabe);
			assertNull(l.fehler(), art + ": " + l.fehler());
			ListeningEventService.Spec s = new ListeningEventService.Spec();
			s.type = type;
			s.actionType = (String) f[2];
			l.werte().in(s);
			Map<String, String> vor = ListeningEventFormular.vorbelegung(s);
			for (var feld : ListeningEventFormular.felder(art, type, (String) f[2], grund)) {
				assertEquals(eingabe.get(feld.id()), ListeningEventFormular.startwert(feld, vor),
						art + " / " + feld.id());
			}
		}
	}

	@Test
	void startwertNimmtVorgabeWennNichtsDa() {
		var feld = ListeningEventFormular.felder(Art.CS_THRESHOLD, "cs", "infomessage", false).get(0);
		assertEquals("4000", ListeningEventFormular.startwert(feld, null));
		assertEquals("123", ListeningEventFormular.startwert(feld, new HashMap<>(Map.of("threshold", "123"))));
		// Zu lang für das Feld: Discord würde das ganze Modal ablehnen
		assertEquals("4000", ListeningEventFormular.startwert(feld, Map.of("threshold", "12345678901")));
	}
}
