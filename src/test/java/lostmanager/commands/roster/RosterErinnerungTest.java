package lostmanager.commands.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class RosterErinnerungTest {

	private static List<String> ids(int n) {
		List<String> ids = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			ids.add(String.valueOf(1000000000000000000L + i));
		}
		return ids;
	}

	@Test
	void wenigePassenInEineNachricht() {
		List<String> n = RosterCommand.erinnerung("CWL 2026-12", ids(3));
		assertEquals(1, n.size());
		assertEquals("**Roster Erinnerung (CWL 2026-12)**\nBitte tragt euch ein oder meldet euch ab!\n"
				+ "<@1000000000000000000> <@1000000000000000001> <@1000000000000000002>", n.get(0));
	}

	@Test
	void verbundMit86NutzernWirdGeteiltUndNiemandFehlt() {
		List<String> ids = ids(86);
		List<String> n = RosterCommand.erinnerung("CWL 2026-12", ids);
		assertEquals(2, n.size());
		String alles = String.join(" ", n);
		for (String nachricht : n) {
			assertTrue(nachricht.length() <= 2000, "Länge " + nachricht.length());
			assertTrue(!nachricht.startsWith(" ") && !nachricht.endsWith(" "));
		}
		for (String id : ids) {
			assertEquals(alles.indexOf("<@" + id + ">"), alles.lastIndexOf("<@" + id + ">"));
			assertTrue(alles.contains("<@" + id + ">"));
		}
		assertTrue(n.get(1).startsWith("<@"));
	}

	@Test
	void alsEineNachrichtWaereEsZuLangGewesen() {
		// Gegenprobe zum Anlass: so wurde der Text bisher gebaut.
		StringBuilder alt = new StringBuilder("**Roster Erinnerung (CWL 2026-12)**\nBitte tragt euch ein oder meldet euch ab!\n");
		for (String id : ids(86)) alt.append("<@").append(id).append("> ");
		assertTrue(alt.toString().trim().length() > 2000);
	}
}
