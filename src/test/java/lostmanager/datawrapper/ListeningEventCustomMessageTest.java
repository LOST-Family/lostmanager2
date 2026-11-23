package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ListeningEventCustomMessageTest {

	@Test
	void kriegsendeEventPostetKopfMitEndetextUndNachricht() {
		String m = ListeningEvent.buildCustomMessage("## LOST 8 Clankrieg - ", true, 0, "**Krieg beendet.**", "GG!");
		assertEquals("## LOST 8 Clankrieg - **Krieg beendet.**\n\nGG!", m);
	}

	@Test
	void erinnerungZeigtRestzeit() {
		long dauer = (2 * 3600 + 5 * 60 + 7) * 1000L;
		String m = ListeningEvent.buildCustomMessage("## X Clankrieg - ", true, dauer, "**Krieg beendet.**", "Hallo");
		assertEquals("## X Clankrieg - **2h** **5m** **7s** verbleibend\n\nHallo", m);
	}

	@Test
	void ohneKopfNurText() {
		assertEquals("Hallo", ListeningEvent.buildCustomMessage(null, true, 0, "", "Hallo"));
	}

	@Test
	void raidBeendetBleibtWieBisher() {
		assertEquals("## Raid - **Raid beendet.**\n\nText",
				ListeningEvent.buildCustomMessage("## Raid - ", false, 5000, "**Raid beendet.**", "Text"));
	}

	@Test
	void startAusloeserSagtNichtBeendet() {
		String m = ListeningEvent.buildCustomMessage("## X Clankrieg - ", true, -1, "**Krieg beendet.**", "Los!");
		assertEquals("## X Clankrieg - **Krieg gestartet.**\n\nLos!", m);
	}

	@Test
	void langeMeldungWirdAnZeilenGeteiltUndErwaehnungenBleibenGanz() {
		StringBuilder sb = new StringBuilder("## CW-Teilnahme\n");
		for (int i = 0; i < 80; i++) {
			sb.append("- Spieler Nummer ").append(i).append(": 1/3 CWs (<@7339000000000000").append(10 + i).append(">)\n");
		}
		java.util.List<String> teile = ListeningEvent.teileFuerDiscord(sb.toString());
		org.junit.jupiter.api.Assertions.assertTrue(teile.size() >= 3);
		int zeilen = 0;
		for (String t : teile) {
			org.junit.jupiter.api.Assertions.assertTrue(t.length() <= ListeningEvent.TEIL_MAX);
			for (String z : t.split("\n")) {
				zeilen++;
				// jede Zeile ist ganz: Listenzeilen enden mit der geschlossenen Erwähnung
				org.junit.jupiter.api.Assertions.assertTrue(z.startsWith("## ") || (z.startsWith("- ") && z.endsWith(">)")), z);
			}
		}
		assertEquals(81, zeilen);
	}

	@Test
	void eigeneNachrichtMitKopfUeber2000ZeichenGehtInZweiTeilen() {
		String text = "x".repeat(1000) + "\n" + "y".repeat(1000);
		String m = ListeningEvent.buildCustomMessage("## LOST 8 Clankrieg - ", true, 0, "**Krieg beendet.**", text);
		org.junit.jupiter.api.Assertions.assertTrue(m.length() > 2000);
		java.util.List<String> teile = ListeningEvent.teileFuerDiscord(m);
		assertEquals(2, teile.size());
		org.junit.jupiter.api.Assertions.assertTrue(teile.get(1).startsWith("yyy"));
	}

	@Test
	void aktionswerteWerdenGelesen() {
		assertEquals("Servus", ListeningEvent.parseCustomMessage("{\"message\":\"Servus\"}"));
		assertEquals("", ListeningEvent.parseCustomMessage(null));
		assertEquals("", ListeningEvent.parseCustomMessage("kaputt"));
	}
}
