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
	void aktionswerteWerdenGelesen() {
		assertEquals("Servus", ListeningEvent.parseCustomMessage("{\"message\":\"Servus\"}"));
		assertEquals("", ListeningEvent.parseCustomMessage(null));
		assertEquals("", ListeningEvent.parseCustomMessage("kaputt"));
	}
}
