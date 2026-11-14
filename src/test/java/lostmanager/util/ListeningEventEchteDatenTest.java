package lostmanager.util;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lostmanager.datawrapper.ActionValue;
import lostmanager.datawrapper.ListeningEvent;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Gegen die echte Datenbank, aber nur lesend (die Sitzung läuft mit
 * default_transaction_read_only). Überspringt sich ohne LOST_MANAGER_DB_URL.
 *
 * Prüft für jedes vorhandene Event, dass {@code edit} es so zurückliest, wie es
 * gespeichert ist: ein Bearbeiten, das nur den Kanal ändert, darf keinen anderen
 * Wert verschieben und nicht an einer Prüfung scheitern, die das Event beim
 * Anlegen bestanden hat.
 */
class ListeningEventEchteDatenTest {

	@Test
	void alleEventsLassenSichVerlustfreiZuruecklesen() throws Exception {
		String url = System.getenv("LOST_MANAGER_DB_URL");
		assumeTrue(url != null && !url.isBlank(), "keine Datenbank konfiguriert");
		Connection.url = url + (url.contains("?") ? "&" : "?") + "options=-c%20default_transaction_read_only=on";
		Connection.user = System.getenv("LOST_MANAGER_DB_USER");
		Connection.password = System.getenv("LOST_MANAGER_DB_PASSWORD");

		ArrayList<Long> ids = DBUtil.getArrayListFromSQL("SELECT id FROM listening_events ORDER BY id", Long.class);
		assertFalse(ids.isEmpty());
		ObjectMapper m = new ObjectMapper();
		List<String> ungueltig = new ArrayList<>();
		int geprueft = 0;
		int formular = 0;
		List<String> planFehler = new ArrayList<>();
		for (Long id : ids) {
			ListeningEventService.Spec s = ListeningEventService.specAusEvent(id);
			assertNotNull(s, "Event " + id);

			// Wieder kodieren und zurücklesen - ohne Grund, dessen Kodierung die
			// Datenbank nach dem Betrag fragt; der Grund selbst kommt aus einer
			// eigenen Spalte des Spec und wird oben schon gelesen.
			ListeningEventService.Spec ohneGrund = ListeningEventService.kopie(s);
			ohneGrund.kickpointReasonName = null;
			String aktion = ListeningEventService.gespeicherterActionType(s.actionType);
			String json = ListeningEventService.buildActionValues(ohneGrund, aktion);
			ListeningEventService.Spec zurueck = ListeningEventService.kopie(s);
			if (json.trim().startsWith("[")) {
				ArrayList<ActionValue> werte = m.readValue(json, new TypeReference<ArrayList<ActionValue>>() {
				});
				ListeningEvent le = new ListeningEvent(id).setListeningType(new ListeningEvent(id).getListeningType())
						.setActionType(new ListeningEvent(id).getActionType()).setActionValues(werte);
				ListeningEventService.wendeWerteAn(zurueck, ListeningEventService.decodeValues(le));
			}
			Map<String, String> vorher = ListeningEventService.beschreibe(s);
			Map<String, String> nachher = ListeningEventService.beschreibe(zurueck);
			assertEquals(vorher, nachher, "Event " + id + " verändert sich beim erneuten Speichern: " + json);

			// Nur den Kanal ändern: darf an nichts scheitern, was das Event schon hat -
			// höchstens fragt es nach einem Wert, der im alten Format nicht lesbar ist.
			ListeningEventBearbeitung.Aenderung a = new ListeningEventBearbeitung.Aenderung();
			a.channelId = "123456789012345678";
			ListeningEventBearbeitung.Plan p = ListeningEventBearbeitung.planen(s, a);
			if (p.fehler() != null) {
				planFehler.add(id + ": " + p.fehler());
			} else if (!p.formular()) {
				assertTrue(ListeningEventService.validateWerte(p.neu()) == null, "Event " + id);
				var u = ListeningEventService.unterschiede(ListeningEventService.beschreibe(s),
						ListeningEventService.beschreibe(p.neu()));
				assertEquals(1, u.size(), "Event " + id + ": " + u);
			} else {
				formular++;
			}

			ListeningEventService.Result r = ListeningEventService.validate(s);
			if (r != null) {
				ungueltig.add(id + " (" + s.type + "/" + s.actionType + "): " + r.error());
			}
			geprueft++;
		}
		System.out.println("Geprüft: " + geprueft + " Events");
		ungueltig.forEach(z -> System.out.println("Volle Prüfung schlägt fehl: " + z));
		System.out.println("Nur Kanal ändern: " + formular + " brauchen das Formular");
		planFehler.forEach(z -> System.out.println("Nur Kanal ändern abgelehnt: " + z));
		// Einzig erlaubter Grund: ein Kickpunkt-Event ganz ohne Grund (das feuert ohnehin ins Leere)
		for (String z : planFehler) {
			assertTrue(z.contains("Kickpoint-Grund ist erforderlich"), z);
		}
	}
}
