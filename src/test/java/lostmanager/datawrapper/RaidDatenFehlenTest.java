package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Ein API-Ausfall darf nicht wie „niemand hat geraidet“ aussehen. */
class RaidDatenFehlenTest {

	@Test
	void ersatzantwortBeiAusfallWirdErkannt() {
		assertTrue(ListeningEvent.raidDatenFehlen(new JSONObject("{\"items\":[{\"state\":\"ended\"}]}")));
		assertTrue(ListeningEvent.raidDatenFehlen(null));
	}

	@Test
	void echterRaidGiltAlsVorhanden() {
		assertFalse(ListeningEvent.raidDatenFehlen(new JSONObject(
				"{\"items\":[{\"state\":\"ongoing\",\"startTime\":\"20261113T070000.000Z\",\"endTime\":\"20261116T070000.000Z\",\"members\":[]}]}")));
		assertFalse(ListeningEvent.raidDatenFehlen(new JSONObject(
				"{\"items\":[{\"state\":\"ended\",\"startTime\":\"20261106T070000.000Z\",\"endTime\":\"20261109T070000.000Z\"}]}")));
	}

	@Test
	void clanOhneRaidsIstKeinAusfall() {
		assertFalse(ListeningEvent.raidDatenFehlen(new JSONObject("{\"items\":[]}")));
		assertFalse(ListeningEvent.raidDatenFehlen(new JSONObject("{}")));
	}

	@Test
	void fillerAufraeumenLoeschtNieDenLaufendenKrieg() {
		// Nur Älteres als 24 h vor dem Kriegsende; kein „+ INTERVAL“ mehr, das den eigenen Krieg träfe.
		assertTrue(ListeningEvent.CW_FILLER_AUFRAEUMEN.contains("war_end_time < ?::timestamp - INTERVAL '24 hours'"));
		assertFalse(ListeningEvent.CW_FILLER_AUFRAEUMEN.contains("+ INTERVAL"));
	}
}
