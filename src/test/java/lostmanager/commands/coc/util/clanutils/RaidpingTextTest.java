package lostmanager.commands.coc.util.clanutils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lostmanager.util.LangeListe;

class RaidpingTextTest {

	@Test
	void offeneAngriffeBekommenIhreUeberschrift() {
		String t = raidping.baueText("<@1>", true, List.of(), List.of("A (<@2>)"), List.of("B (<@3>): 3/6"),
				List.of());
		int garNicht = t.indexOf("### Noch gar nicht angegriffen:");
		int offen = t.indexOf("### Noch offene Angriffe:");
		assertTrue(garNicht > 0 && offen > garNicht, t);
		assertTrue(t.indexOf("B (<@3>): 3/6") > offen);
		assertFalse(t.contains("Abgemeldet"));
		assertFalse(t.contains("anderen Lost-Clan"));
	}

	@Test
	void ohneRaidAndereUeberschriftenUndHinweis() {
		String t = raidping.baueText("<@1>", false, List.of("C in LOST 2: 6/6"), List.of("A"), List.of("B: 1/6"),
				List.of("D"));
		assertTrue(t.contains("Kein Ping"));
		assertTrue(t.contains("### Nicht angegriffen:\nA\n"));
		assertTrue(t.contains("### Teil der Angriffe gemacht:\nB: 1/6\n"));
		assertTrue(t.contains("### In einem anderen Lost-Clan angegriffen:\nC in LOST 2: 6/6\n"));
		assertTrue(t.contains("### Abgemeldet (keine Pings):\nD\n"));
	}

	@Test
	void fuenfzigOffeneWerdenGeteiltUndNichtsGehtVerloren() {
		List<String> garNicht = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			garNicht.add("Spielername_" + (10 + i) + " (<@" + (733857906117574717L + i) + ">)");
		}
		String t = raidping.baueText("<@1>", true, List.of(), garNicht, List.of(), List.of());
		assertTrue(t.length() > 2000, "der Fall, der früher eine Ausnahme warf");
		List<String> teile = LangeListe.teileNachZeilen(t, LangeListe.NACHRICHT_MAX);
		assertEquals(2, teile.size());
		for (String teil : teile) {
			assertTrue(teil.length() <= 2000 && !teil.isBlank());
		}
		String zusammen = String.join("\n", teile);
		for (String z : garNicht) {
			assertEquals(1, zusammen.split(java.util.regex.Pattern.quote(z + "\n"), -1).length - 1
					+ (zusammen.endsWith(z) ? 1 : 0), z);
		}
	}

	@Test
	void kurzerTextBleibtEineNachricht() {
		String t = raidping.baueText("<@1>", true, List.of(), List.of("A"), List.of(), List.of());
		List<String> teile = LangeListe.teileNachZeilen(t, 2000);
		assertEquals(1, teile.size());
		assertEquals(t.stripTrailing(), teile.get(0).stripTrailing());
	}

	@Test
	void ueberlangeZeileWirdHartGeschnitten() {
		List<String> teile = LangeListe.teileNachZeilen("kopf\n" + "x".repeat(4500) + "\nfuss", 2000);
		assertEquals(4, teile.size());
		for (String teil : teile) {
			assertTrue(teil.length() <= 2000);
		}
		assertEquals("kopf", teile.get(0));
		assertEquals("x".repeat(500) + "\nfuss", teile.get(3));
	}
}
