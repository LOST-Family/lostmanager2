package lostmanager.legend;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lostmanager.legend.LegendTexte.HuntZeile;
import lostmanager.legend.LegendTexte.PdZeile;

/** Das Format, das Stefan am 16.11.2026 abgesegnet hat. */
class LegendTexteTest {

	@Test
	void perfectDayWieAbgestimmt() {
		List<String> t = LegendTexte.perfectDay(List.of(new PdZeile("Spieler A", 393, 29, false),
				new PdZeile("Spieler B", 376, 28, false), new PdZeile("Spieler C", 372, 27, false),
				new PdZeile("Spieler D", 371, 26, false)), LocalDate.of(2026, 11, 17), false, false);
		assertEquals(1, t.size());
		String s = t.get(0);
		assertTrue(s.startsWith("# 🏆 LOST Family · Perfect Day Battle 🏆\n-# Punktestand 17.11.2026\n### 🏆 Rangliste\n"
				+ "> 🥇 **Spieler A** – 393 (+29)\n> 🥈 **Spieler B** – 376 (+28)\n"
				+ "> 🥉 **Spieler C** – 372 (+27)\n> 4. **Spieler D** – 371 (+26)\n"
				+ "-# Gesamtpunkte inklusive gestern, in der Klammer die Punkte von gestern. Nur Legend 1, "
				+ "Angriffspokale ÷ 10, abgerundet.\n"), s);
		assertTrue(s.endsWith("━━━━━━━━━━━━━━━━━━━━━━\nWeiter pushen und dranbleiben! ⚔️"));
		assertTrue(s.contains("Näherung"));
		assertFalse(s.contains("* = Tag"));
		assertTrue(s.contains("\n-# Automatisch ermittelt (Näherung), bitte Screenshots der Tagesübersicht aufheben.\n"), s);
	}

	@Test
	void perfectDayMarkiertTeilerfassungEndstandUndOhneBelohnung() {
		String s = String.join("\n", LegendTexte.perfectDay(List.of(new PdZeile("A_b*c", 10, 3, true)),
				LocalDate.of(2026, 11, 30), true, true));
		assertTrue(s.contains("Endstand der Legend-Saison · 30.11.2026"));
		assertTrue(s.contains("ohne Belohnung"));
		assertTrue(s.contains("– 10 (+3*)"));
		assertTrue(s.contains("**A\\_b\\*c**"), s);
		assertTrue(s.contains("-# Automatisch ermittelt (Näherung), bitte Screenshots der Tagesübersicht aufheben."
				+ " * = Tag nicht vollständig erfasst, fehlende Angriffe bitte per Screenshot melden."), s);
	}

	@Test
	void langeListenWerdenGeteiltNichtGekappt() {
		List<PdZeile> z = new ArrayList<>();
		for (int i = 0; i < 80; i++) {
			z.add(new PdZeile("Spieler mit langem Namen " + i, 400 - i, 20, false));
		}
		List<String> t = LegendTexte.perfectDay(z, LocalDate.of(2026, 11, 17), false, false);
		assertTrue(t.size() > 1);
		for (String n : t) {
			assertTrue(n.length() <= 2000);
		}
		String alles = String.join("\n", t);
		assertTrue(alles.contains("Spieler mit langem Namen 79"));
	}

	@Test
	void trophyHuntWieAbgestimmt() {
		String s = String.join("\n", LegendTexte.trophyHunt(List.of(new HuntZeile("Spieler A", 1100, false, false),
				new HuntZeile("Spieler B", 1040, false, false), new HuntZeile("Spieler C", 985, false, false),
				new HuntZeile("Spieler D", 940, false, false)), List.of(0), LocalDate.of(2026, 11, 16),
				LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 29)));
		assertTrue(s.startsWith("# 🏆 LOST Family · Trophy Hunt Legend 2 🏆\n-# Auswertung Woche 16.–22.11.2026\n"
				+ "### 🥇 Gewinner: **Spieler A** mit 1100 Angriffspokalen\n> 🥈 **Spieler B** – 1040\n"
				+ "> 🥉 **Spieler C** – 985\n> 4. **Spieler D** – 940\n"
				+ "-# Es zählen nur Angriffspokale, keine aus Verteidigungen. Einspruchsfrist bis 29.11.2026: "
				+ "Stimmen eure Werte nicht, schickt uns bis dahin Screenshots von eurer tatsächlichen Off.\n"), s);
		assertTrue(s.endsWith("━━━━━━━━━━━━━━━━━━━━━━\nGlückwunsch an den Gewinner und danke an alle fürs Mitmachen! 🎉 "
				+ "Wir sehen uns beim nächsten Trophy Hunt wieder. ⚔️"));
	}

	@Test
	void trophyHuntSperreUndGleichstand() {
		String s = String.join("\n", LegendTexte.trophyHunt(List.of(new HuntZeile("Alt", 1200, true, false),
				new HuntZeile("B", 1100, false, false), new HuntZeile("C", 1000, false, false)), List.of(1),
				LocalDate.of(2026, 11, 30), LocalDate.of(2026, 12, 6), LocalDate.of(2026, 12, 13)));
		assertTrue(s.contains("Woche 30.11.–06.12.2026"));
		assertTrue(s.contains("Gewinner: **B** mit 1100"));
		assertTrue(s.contains("> 🥇 **Alt** – 1200 · Vormonatssieger, für die Belohnung gesperrt"));
		assertTrue(s.contains("> 🥉 **C** – 1000"));
		s = String.join("\n", LegendTexte.trophyHunt(List.of(new HuntZeile("B", 1100, false, false),
				new HuntZeile("C", 1100, false, false)), List.of(0, 1), LocalDate.of(2026, 11, 16),
				LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 29)));
		assertTrue(s.contains("Gleichstand: **B** und **C** mit 1100 Angriffspokalen – ein 1vs1 (Best of 3) entscheidet"));
		assertFalse(s.contains("Glückwunsch an den Gewinner"));
	}
}
