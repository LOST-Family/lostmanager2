package lostmanager.commands.discord.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lostmanager.util.LangeListe;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** /teamcheck mit großen Rollen (LOST Family: „Turnier / Events“ hat ~700 Mitglieder). */
class TeamcheckKuerzenTest {

	private static String teamZeile(int anzahl) {
		StringBuilder sb = new StringBuilder("**Team 1:** ").append(anzahl).append("\n");
		for (int i = 0; i < anzahl; i++) {
			if (i > 0)
				sb.append(", ");
			sb.append("<@").append(100000000000000000L + i).append(">");
		}
		return sb.append("\n").toString();
	}

	@Test
	void kurzerTextBleibtUnveraendert() {
		String text = "**Gesamtzahl der Mitglieder:** 3\n\n" + teamZeile(3);
		assertEquals(text, teamcheck.kuerzen(text, LangeListe.BESCHREIBUNG_WEICH));
	}

	@Test
	void riesigeTeamzeileWirdAnListengrenzeGekuerztUndEmbedBautSich() {
		String text = "**Gesamtzahl der Mitglieder:** 692\n\n**Teamverteilung:**\n" + teamZeile(692)
				+ "\n**Mitglieder ohne Team:** 0\n";
		String gekuerzt = teamcheck.kuerzen(text, LangeListe.BESCHREIBUNG_WEICH);
		assertTrue(gekuerzt.length() <= LangeListe.BESCHREIBUNG_WEICH, "Länge " + gekuerzt.length());
		assertTrue(gekuerzt.contains("gekürzt"));
		// keine halbe Erwähnung vor dem Hinweis
		String vorHinweis = gekuerzt.substring(0, gekuerzt.indexOf("\n… gekürzt"));
		assertTrue(vorHinweis.endsWith(">"), vorHinweis.substring(vorHinweis.length() - 30));
		MessageEmbed embed = MessageUtil.buildEmbed("Team-Check", gekuerzt, MessageUtil.EmbedType.INFO,
				"Zuletzt aktualisiert am 20.11.2026 um 23:00 Uhr");
		assertTrue(embed.getDescription().length() <= 4096);
	}

	@Test
	void zweiClansPassenUngekuerztInsEmbed() {
		// realistischer Fall: zwei Mitgliederrollen à ~50, vier Teams
		StringBuilder sb = new StringBuilder("**Gesamtzahl der Mitglieder:** 100\n\n**Teamverteilung:**\n");
		for (int t = 0; t < 4; t++)
			sb.append(teamZeile(16)).append("\n");
		sb.append("**Mitglieder ohne Team:** 36\n").append(teamZeile(36));
		String text = sb.toString();
		assertTrue(text.length() > LangeListe.NACHRICHT_MAX, "Fall soll den Embed-Weg nehmen");
		assertEquals(text, teamcheck.kuerzen(text, LangeListe.BESCHREIBUNG_WEICH));
	}
}
