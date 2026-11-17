package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * custommessage-Events speichern ein Objekt statt einer Liste. Das darf nicht als
 * Fehler im Journal landen, kaputtes JSON aber weiterhin schon.
 */
class ActionValuesLesenTest {

	@Test
	void objektEinerCustommessageIstLeereListe() throws JsonProcessingException {
		assertTrue(ListeningEvent.parseActionValues("{\"message\": \"Bitte neuen CW starten!\"}").isEmpty());
	}

	@Test
	void leerUndNullSindLeereListe() throws JsonProcessingException {
		assertTrue(ListeningEvent.parseActionValues(null).isEmpty());
		assertTrue(ListeningEvent.parseActionValues("  ").isEmpty());
		assertTrue(ListeningEvent.parseActionValues("[]").isEmpty());
	}

	@Test
	void listeWirdWieBisherGelesen() throws JsonProcessingException {
		ArrayList<ActionValue> werte = ListeningEvent.parseActionValues(
				"[{\"saved\": \"value\", \"value\": 3}, {\"saved\": \"setting\", \"key\": \"x\", \"value\": 7}]");
		assertEquals(2, werte.size());
		assertEquals(ActionValue.kind.value, werte.get(0).getSaved());
		assertEquals(3L, werte.get(0).getValue());
		assertEquals("x", werte.get(1).getKey());
		assertEquals(7L, werte.get(1).getValue());
	}

	@Test
	void kaputtesJsonWirftWeiterhin() {
		assertThrows(JsonProcessingException.class, () -> ListeningEvent.parseActionValues("[{\"saved\": "));
	}
}
