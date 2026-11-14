package lostmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lostmanager.datawrapper.ActionValue;
import lostmanager.datawrapper.ListeningEvent;
import lostmanager.util.ListeningEventBearbeitung.Aenderung;
import lostmanager.util.ListeningEventBearbeitung.Plan;
import lostmanager.util.ListeningEventFormular.Art;
import lostmanager.util.ListeningEventService.Spec;

class ListeningEventBearbeitungTest {

	private static final String CLAN = "#2LU2V2LPU";
	private static final String KANAL = "1155421217763885056";

	private static Spec spec(String type, long dauer, String aktion) {
		Spec s = new Spec();
		s.clanTag = CLAN;
		s.type = type;
		s.duration = dauer;
		s.actionType = aktion;
		s.channelId = KANAL;
		return s;
	}

	/** Echte Regeln, nur jeder Grund gilt als vorhanden. */
	private static Plan planen(Spec alt, Aenderung a) {
		return ListeningEventBearbeitung.planen(alt, a, (grund, clan) -> true);
	}

	@Test
	void nurKanalAendernBehaeltAlleWerte() {
		Spec alt = spec("cw", 3600000, "infomessage");
		alt.thresholdOrAttacks = 2;
		Aenderung a = new Aenderung();
		a.channelId = "999999999999";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertFalse(p.formular());
		assertTrue(p.werteUebernommen());
		assertEquals("999999999999", p.neu().channelId);
		assertEquals(2, p.neu().thresholdOrAttacks);
		assertEquals(KANAL, alt.channelId, "das Original bleibt unberührt");

		var u = ListeningEventService.unterschiede(ListeningEventService.beschreibe(alt),
				ListeningEventService.beschreibe(p.neu()));
		assertEquals(1, u.size());
		assertEquals("Kanal", u.get(0).feld());
		assertEquals("<#" + KANAL + ">", u.get(0).vorher());
	}

	@Test
	void ohneOptionenKommtDasFormularVorausgefuellt() {
		Spec alt = spec("cw", 0, "kickpoint");
		alt.kickpointReasonName = "CW vergessen";
		alt.thresholdOrAttacks = 2;
		alt.namedSettings = new HashMap<>(Map.of(ListeningEvent.SETTING_IGNORE_PERFECT_WAR, 1L));
		Plan p = planen(alt, new Aenderung());
		assertNull(p.fehler());
		assertTrue(p.formular());
		assertEquals(Art.CW_ATTACKS, p.art());
		assertEquals("2", p.vorbelegung().get("required_attacks"));
		assertEquals("1", p.vorbelegung().get("ignore_perfect_war"));
	}

	@Test
	void altesEventMitUngueltigemWertBekommtDasFormular() {
		// Wie die Events 15-21: benötigte Angriffe im alten Format, nicht lesbar
		Spec alt = spec("cw", 1800000, "infomessage");
		Aenderung a = new Aenderung();
		a.channelId = "999999999999";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertTrue(p.formular());
	}

	@Test
	void ohneOptionenUndOhneZusatzwerteIstEinFehler() {
		Plan p = planen(spec("cw", 0, "filler"), new Aenderung());
		assertNotNull(p.fehler());
	}

	@Test
	void infoZuKickpointFragtNachDemNeuenFeld() {
		Spec alt = spec("cw", 0, "infomessage");
		alt.thresholdOrAttacks = 2;
		Aenderung a = new Aenderung();
		a.actionType = "kickpoint";
		a.kickpointReasonName = "CW vergessen";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertTrue(p.formular(), "perfekter Krieg ist neu und muss bewusst entschieden werden");
		assertEquals("2", p.vorbelegung().get("required_attacks"));
		assertNull(p.vorbelegung().get("ignore_perfect_war"));
	}

	@Test
	void kickpointZuInfoUebernimmtUndLaesstGrundFallen() {
		Spec alt = spec("cw", 0, "kickpoint");
		alt.kickpointReasonName = "CW vergessen";
		alt.thresholdOrAttacks = 1;
		alt.namedSettings = new HashMap<>(Map.of(ListeningEvent.SETTING_IGNORE_PERFECT_WAR, 1L));
		Aenderung a = new Aenderung();
		a.actionType = "infomessage";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertFalse(p.formular());
		assertNull(p.neu().kickpointReasonName);
		assertEquals(1, p.neu().thresholdOrAttacks);
		assertNull(p.neu().namedSettings, "perfekter Krieg gehört nur zu Kickpunkten");
	}

	@Test
	void ohneZusatzwerteFallenAlleWeg() {
		Spec alt = spec("cw", 0, "infomessage");
		alt.thresholdOrAttacks = 2;
		Aenderung a = new Aenderung();
		a.actionType = "filler";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertFalse(p.formular());
		assertNull(p.neu().thresholdOrAttacks, "sonst stünde eine 2 als Positionswert im Filler-Event");
	}

	@Test
	void andereArtUebernimmtNichtsFalsches() {
		Spec alt = spec("cw", 0, "infomessage");
		alt.thresholdOrAttacks = 2;
		Aenderung a = new Aenderung();
		a.type = "cs";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertTrue(p.formular());
		assertEquals(Art.CS_THRESHOLD, p.art());
		assertTrue(p.vorbelegung().isEmpty(), "2 Angriffe sind keine 2 Punkte");
	}

	@Test
	void grundZuAktionOhneKickpunkteIstEinFehler() {
		Aenderung a = new Aenderung();
		a.kickpointReasonName = "irgendwas";
		assertNotNull(planen(spec("cw", 0, "infomessage"), a).fehler());
	}

	@Test
	void distriktCheckMitGrundWirdKickpunktModus() {
		Spec alt = spec("raid", 0, "raidfails");
		alt.raidDistrictThresholds = new HashMap<>(
				Map.of("capital_peak_max", 10, "other_districts_max", 6, "penalize_both", 1));
		Aenderung a = new Aenderung();
		a.kickpointReasonName = "Distrikt";
		Plan p = planen(alt, a);
		assertNull(p.fehler());
		assertEquals("raidfails_kickpoint", p.neu().actionType);
		assertTrue(p.formular());
		assertEquals("10", p.vorbelegung().get("capital_peak_max"));
	}

	@Test
	void startNurBeiCw() {
		Spec alt = spec("cw", -1, "infomessage");
		alt.thresholdOrAttacks = 2;
		Aenderung a = new Aenderung();
		a.type = "raid";
		Plan p = planen(alt, a);
		assertNotNull(p.fehler());
		assertTrue(p.fehler().contains("duration"));
	}

	@Test
	void regelnVonAddGeltenAuchHier() {
		Aenderung a = new Aenderung();
		a.actionType = "raidfails";
		assertNotNull(planen(spec("cw", 0, "infomessage"), a).fehler(), "raidfails nur bei raid");

		a = new Aenderung();
		a.duration = 3600000L;
		Spec raid = spec("raid", 0, "raidfails");
		raid.raidDistrictThresholds = new HashMap<>(
				Map.of("capital_peak_max", 10, "other_districts_max", 6, "penalize_both", 1));
		assertNotNull(planen(raid, a).fehler(), "raidfails nur mit Dauer 0");
	}

	@Test
	void clanWechselMitFremdemGrundGibtHinweis() {
		Spec alt = spec("cwlday", 0, "kickpoint");
		alt.kickpointReasonName = "CWL vergessen";
		Aenderung a = new Aenderung();
		a.clanTag = "#2QC0QQPQ2";
		Plan p = ListeningEventBearbeitung.planen(alt, a, (grund, clan) -> clan.equals(CLAN));
		assertNotNull(p.fehler());
		assertTrue(p.fehler().contains("kickpoint_reason"));
	}

	@Test
	void werteAufWunschImFormular() {
		Spec alt = spec("cw", 0, "custommessage");
		alt.customMessage = "Alt";
		Aenderung a = new Aenderung();
		a.duration = 7200000L;
		assertFalse(planen(alt, a).formular());
		a.werteBearbeiten = true;
		Plan p = planen(alt, a);
		assertTrue(p.formular());
		assertEquals("Alt", p.vorbelegung().get("custommessage"));
		assertEquals(7200000L, p.neu().duration);

		var w = ListeningEventFormular.lies(p.art(), "cw", false, Map.of("custommessage", "Neu")::get).werte();
		Spec neu = ListeningEventBearbeitung.mitFormular(p, w);
		assertEquals("Neu", neu.customMessage);
		assertEquals(7200000L, neu.duration);
	}

	@Test
	void fremdeEinstellungenUeberlebenDasFormular() {
		Spec alt = spec("cw", 0, "kickpoint");
		alt.kickpointReasonName = "CW vergessen";
		alt.thresholdOrAttacks = 2;
		alt.namedSettings = new HashMap<>(Map.of("von_der_website", 5L));
		Plan p = planen(alt, new Aenderung());
		var w = ListeningEventFormular.lies(p.art(), "cw", true,
				Map.of("required_attacks", "1", "ignore_perfect_war", "2")::get).werte();
		Spec neu = ListeningEventBearbeitung.mitFormular(p, w);
		assertEquals(5L, neu.namedSettings.get("von_der_website"));
		assertEquals(2L, neu.namedSettings.get(ListeningEvent.SETTING_IGNORE_PERFECT_WAR));
		assertEquals(1, neu.thresholdOrAttacks);
	}

	@Test
	void nachrichtAenderungHinterZeichen100WirdErkannt() {
		Spec alt = spec("cw", 0, "custommessage");
		alt.customMessage = "x".repeat(150) + "A";
		Spec neu = ListeningEventService.kopie(alt);
		neu.customMessage = "x".repeat(150) + "B";
		assertEquals(1, ListeningEventService.unterschiede(ListeningEventService.beschreibe(alt),
				ListeningEventService.beschreibe(neu)).size());
		assertTrue(ListeningEventService.unterschiede(ListeningEventService.beschreibe(alt),
				ListeningEventService.beschreibe(ListeningEventService.kopie(alt))).isEmpty());
	}

	/**
	 * Speichern und Zurücklesen: was specAusEvent aus der gespeicherten Zeile
	 * macht, muss dasselbe Event beschreiben, das gespeichert wurde - sonst meldet
	 * edit Änderungen, die keiner gemacht hat, oder verliert Werte.
	 */
	@Test
	void speichernUndZuruecklesenSindVerlustfrei() throws Exception {
		List<Spec> faelle = new java.util.ArrayList<>();
		Spec s = spec("cw", 3600000, "infomessage");
		s.thresholdOrAttacks = 2;
		faelle.add(s);
		s = spec("cs", 0, "infomessage");
		s.thresholdOrAttacks = 4000;
		faelle.add(s);
		s = spec("cw", 0, "starfails");
		s.starCount = 1;
		s.punishmentMode = 3;
		s.namedSettings = new HashMap<>(Map.of(ListeningEvent.SETTING_STARFAILS_FREE_HITS, 2L));
		faelle.add(s);
		s = spec("raid", 0, "raidfails");
		s.raidDistrictThresholds = new HashMap<>(
				Map.of("capital_peak_max", 9, "other_districts_max", 4, "penalize_both", 1));
		faelle.add(s);
		s = spec("cw", -1, "cwdonator");
		s.useLists = 1;
		s.excludeLeaders = 0;
		faelle.add(s);
		s = spec("seasonend", 172800000, "infomessage");
		s.namedSettings = new HashMap<>(Map.of(ListeningEvent.SETTING_WINS_THRESHOLD, 80L));
		faelle.add(s);
		s = spec("seasonend", 0, "cwcount");
		s.namedSettings = new HashMap<>(Map.of(ListeningEvent.SETTING_CW_MIN_COUNT, 8L));
		faelle.add(s);

		ObjectMapper m = new ObjectMapper();
		for (Spec gespeichert : faelle) {
			String aktion = ListeningEventService.gespeicherterActionType(gespeichert.actionType);
			String json = ListeningEventService.buildActionValues(gespeichert, aktion);
			java.util.ArrayList<ActionValue> werte = m.readValue(json, new TypeReference<java.util.ArrayList<ActionValue>>() {
			});
			ListeningEvent le = new ListeningEvent(1)
					.setListeningType(ListeningEvent.LISTENINGTYPE.valueOf(gespeichert.type.toUpperCase()))
					.setActionType(ListeningEvent.ACTIONTYPE.valueOf(aktion.toUpperCase())).setActionValues(werte);

			Spec gelesen = spec(gespeichert.type, gespeichert.duration, gespeichert.actionType);
			ListeningEventService.wendeWerteAn(gelesen, ListeningEventService.decodeValues(le));
			assertEquals(ListeningEventService.beschreibe(gespeichert), ListeningEventService.beschreibe(gelesen),
					gespeichert.type + "/" + gespeichert.actionType + ": " + json);
		}
	}
}
