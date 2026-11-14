package lostmanager.commands.coc.util.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

import lostmanager.util.ListeningEventFormular;
import lostmanager.util.ListeningEventFormular.Art;

/** Baut jedes Formular mit JDA, so wie Discord es bekäme - leer und vorausgefüllt. */
class ListeningeventModalTest {

	private static final String[][] KOMBINATIONEN = { { "cs", "kickpoint" }, { "cw", "infomessage" },
			{ "cw", "kickpoint" }, { "seasonend", "infomessage" }, { "seasonend", "cwcount_kickpoint" },
			{ "cwlday", "kickpoint" }, { "raid", "raidfails" }, { "raid", "raidfails_kickpoint" },
			{ "cw", "custommessage" }, { "cw", "cwdonator" }, { "cw", "starfails" },
			{ "cwlday", "starfails_kickpoint" } };

	@Test
	void jedesFormularLaesstSichBauen() {
		Map<String, String> voll = Map.ofEntries(Map.entry("threshold", "4000"), Map.entry("required_attacks", "2"),
				Map.entry("ignore_perfect_war", "1"), Map.entry("wins_threshold", "70"),
				Map.entry("cw_min_count", "8"), Map.entry("capital_peak_max", "10"),
				Map.entry("other_districts_max", "6"), Map.entry("penalize_both", "2"),
				Map.entry("raid_force_kickpoints", "1"), Map.entry("custommessage", "x".repeat(2000)),
				Map.entry("use_lists", "1"), Map.entry("exclude_leaders", "1"), Map.entry("star_count", "0"),
				Map.entry("punishment_mode", "3"), Map.entry("starfails_free_hits", "20"));
		for (String[] k : KOMBINATIONEN) {
			Art art = ListeningEventFormular.art(k[0], k[1]);
			assertNotNull(art, k[0] + "/" + k[1]);
			boolean grund = k[1].contains("kickpoint");
			var felder = ListeningEventFormular.felder(art, k[0], k[1], grund);
			String id = "listeningevent_edit_" + "0123456789abcdef0123456789abcdef";
			var leer = listeningevent.baueModal(art.praefix + "0123456789abcdef0123456789abcdef", art.titel, felder,
					null);
			var gefuellt = listeningevent.baueModal(id, art.titel, felder, voll);
			assertEquals(felder.size(), leer.getComponents().size());
			assertEquals(felder.size(), gefuellt.getComponents().size());
			assertTrue(id.length() <= 100);
		}
	}
}
