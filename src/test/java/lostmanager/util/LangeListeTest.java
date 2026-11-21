package lostmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.EmbedBuilder;

class LangeListeTest {

	private static List<String> ids(int n) {
		List<String> l = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			l.add(String.valueOf(733857906117574717L + i));
		}
		return l;
	}

	@Test
	void kurzeListeBleibtUnveraendert() {
		StringBuilder sb = new StringBuilder("Kopf\n");
		int n = LangeListe.anhaengen(sb, List.of("a", "b"), LangeListe.BESCHREIBUNG_WEICH);
		assertEquals(2, n);
		assertEquals("Kopf\na\nb\n", sb.toString());
	}

	@Test
	void rolleMit700MitgliedernPasstInEinEmbed() {
		// „Turnier / Events“ hat 692 Mitglieder; jede Erwähnung sind 22 bis 23 Zeichen.
		List<String> zeilen = ids(692).stream().map(id -> "<@" + id + ">").toList();
		StringBuilder sb = new StringBuilder("**Fehlende Reaktionen (692):**\n");
		int n = LangeListe.anhaengen(sb, zeilen, LangeListe.BESCHREIBUNG_WEICH);
		assertTrue(n > 100 && n < 692);
		assertTrue(sb.toString().endsWith("… und " + (692 - n) + " weitere\n"));
		// Zwei weitere Abschnitte dahinter dürfen jeweils nur noch ihre Restzeile anhängen.
		LangeListe.anhaengen(sb, zeilen, LangeListe.BESCHREIBUNG_WEICH);
		LangeListe.anhaengen(sb, zeilen, LangeListe.BESCHREIBUNG_WEICH);
		assertTrue(sb.length() <= 4096);
		assertDoesNotThrow(() -> new EmbedBuilder().setDescription(sb.toString()).build());
	}

	@Test
	void idsLassenSichZuruecklesen() {
		List<String> ein = ids(187);
		String text = LangeListe.kodiereIds(ein);
		assertNotNull(text);
		assertTrue(text.length() <= 2000);
		ByteBuffer b = ByteBuffer.wrap(Base64.getUrlDecoder().decode(text));
		List<String> aus = new ArrayList<>();
		while (b.remaining() >= 8) {
			aus.add(String.valueOf(b.getLong()));
		}
		assertEquals(ein, aus);
	}

	@Test
	void zuVieleIdsGebenKeinenNachrichtentext() {
		assertNull(LangeListe.kodiereIds(ids(188)));
		assertEquals("", LangeListe.kodiereIds(List.of()));
	}

	@Test
	void pingsWerdenAufNachrichtenVerteilt() {
		List<String> ein = ids(187);
		List<String> nachrichten = LangeListe.pingNachrichten(ein);
		assertTrue(nachrichten.size() >= 3);
		List<String> wieder = new ArrayList<>();
		for (String n : nachrichten) {
			assertTrue(n.length() <= 2000, "Nachricht zu lang: " + n.length());
			for (String teil : n.split(" ")) {
				assertTrue(teil.matches("<@\\d+>"), teil);
				wieder.add(teil.substring(2, teil.length() - 1));
			}
		}
		assertEquals(ein, wieder);
		assertEquals(1, LangeListe.pingNachrichten(ids(50)).size());
		assertTrue(LangeListe.pingNachrichten(List.of()).isEmpty());
	}
}
