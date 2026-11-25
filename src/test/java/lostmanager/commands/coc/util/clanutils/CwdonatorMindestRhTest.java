package lostmanager.commands.coc.util.clanutils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lostmanager.datawrapper.Player;
import lostmanager.util.Tuple;

/**
 * /cwdonator mit Mindest-Rathausstufe (25.11.2026): ab der Stufe wird gelost,
 * ohne Angabe wie bisher; die Stufe kommt aus der Kriegsaufstellung.
 */
class CwdonatorMindestRhTest {

	static Player spieler(int platz, int rh) {
		return new Player("#P" + platz).setNameAPI("N" + platz).setWarMapPosition(platz).setWarThLevel(rh)
				.setWarPreference(true).setRoleDB(Player.RoleType.MEMBER);
	}

	static ArrayList<Player> aufstellung(int... rhJePlatz) {
		ArrayList<Player> l = new ArrayList<>();
		for (int i = 0; i < rhJePlatz.length; i++) {
			l.add(spieler(i + 1, rhJePlatz[i]));
		}
		return l;
	}

	@Test
	void ohneMindestRhWieBisher() {
		Tuple<Integer, Integer> bereich = new Tuple<>(1, 3);
		assertTrue(cwdonator.isEligibleDonator(spieler(4, 9), bereich, false, null));
		assertFalse(cwdonator.isEligibleDonator(spieler(2, 17), bereich, false, null), "spendet nicht für sich");
	}

	@Test
	void mindestRhFiltert() {
		Tuple<Integer, Integer> bereich = new Tuple<>(1, 3);
		assertTrue(cwdonator.isEligibleDonator(spieler(4, 15), bereich, false, 15));
		assertFalse(cwdonator.isEligibleDonator(spieler(4, 14), bereich, false, 15));
		assertFalse(cwdonator.isEligibleDonator(spieler(2, 17), bereich, false, 15), "Bereich zählt weiter");
		Player abgemeldet = spieler(5, 17).setWarPreference(false);
		assertFalse(cwdonator.isEligibleDonator(abgemeldet, bereich, false, 15));
		Player vize = spieler(5, 17).setRoleDB(Player.RoleType.COLEADER);
		assertFalse(cwdonator.isEligibleDonator(vize, bereich, true, 15));
	}

	@Test
	void auslosungNimmtNurAbMindestRh() {
		// Plätze 1–5: RH 17, 16, 12, 11, 10 – für 4–5 kommen ab RH 16 nur 1 und 2 infrage
		ArrayList<Player> l = aufstellung(17, 16, 12, 11, 10);
		for (int i = 0; i < 50; i++) {
			Player p = cwdonator.pickRandomEligible(l, new Tuple<>(4, 5), false, 16);
			assertNotNull(p);
			assertTrue(List.of(1, 2).contains(p.getWarMapPosition()), "gezogen: " + p.getWarMapPosition());
		}
	}

	@Test
	void keinerAbMindestRhDannNull() {
		// Für 1–3 bleibt ab RH 16 niemand (1 und 2 liegen im Bereich) – der Befehl
		// füllt dann ohne Mindest-RH auf.
		ArrayList<Player> l = aufstellung(17, 16, 12, 11, 10);
		assertNull(cwdonator.pickRandomEligible(l, new Tuple<>(1, 3), false, 16));
		Player auffueller = cwdonator.pickRandomEligible(l, new Tuple<>(1, 3), false, null);
		assertNotNull(auffueller);
		assertTrue(auffueller.getWarMapPosition() >= 4);
	}

	@Test
	void cwlAufstellungTraegtRathausstufe() {
		org.json.JSONArray m = new org.json.JSONArray();
		m.put(new org.json.JSONObject().put("tag", "#A").put("name", "A").put("mapPosition", 3).put("townhallLevel", 16));
		m.put(new org.json.JSONObject().put("tag", "#B").put("name", "B").put("mapPosition", 1).put("townhallLevel", 17));
		org.json.JSONObject wir = new org.json.JSONObject().put("tag", "#LOST").put("members", m);
		org.json.JSONObject krieg = new org.json.JSONObject().put("state", "preparation").put("clan", wir)
				.put("opponent", new org.json.JSONObject().put("tag", "#X").put("members", new org.json.JSONArray()));
		org.json.JSONObject liga = new org.json.JSONObject().put("state", "inWar").put("rounds",
				new org.json.JSONArray().put(new org.json.JSONObject().put("warTags", new org.json.JSONArray().put("#K"))));
		ArrayList<Player> l = cwdonator.cwlTagesaufstellung("#LOST", liga, tag -> krieg);
		assertEquals(17, l.get(0).getWarThLevel());
		assertEquals(16, l.get(1).getWarThLevel());
	}
}
