package lostmanager.commands.coc.util.clanutils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import lostmanager.datawrapper.Player;

/**
 * /cwdonator in der CWL: Aufstellung des eigenen Clans am aktuellen Kriegstag,
 * Kartenplätze lückenlos 1..N (die API liefert in der CWL Lücken).
 */
class CwdonatorCwlTest {

	static final String WIR = "#LOST";

	static JSONObject seite(String tag, int... plaetze) {
		JSONArray m = new JSONArray();
		for (int p : plaetze) {
			m.put(new JSONObject().put("tag", tag + "-" + p).put("name", "N" + p).put("mapPosition", p));
		}
		return new JSONObject().put("tag", tag).put("members", m);
	}

	static JSONObject krieg(String state, JSONObject clan, JSONObject gegner) {
		return new JSONObject().put("state", state).put("clan", clan).put("opponent", gegner);
	}

	static JSONObject liga(String state, List<List<String>> runden) {
		JSONArray r = new JSONArray();
		for (List<String> tags : runden) {
			r.put(new JSONObject().put("warTags", new JSONArray(tags)));
		}
		return new JSONObject().put("state", state).put("rounds", r);
	}

	static List<Integer> plaetze(ArrayList<Player> l) {
		List<Integer> p = new ArrayList<>();
		for (Player x : l)
			p.add(x.getWarMapPosition());
		return p;
	}

	@Test
	void vorbereitungstagGehtVorLaufendemKrieg() {
		Map<String, JSONObject> kriege = new HashMap<>();
		kriege.put("#A1", krieg("warEnded", seite("#X"), seite(WIR, 1, 2, 3)));
		kriege.put("#A2", krieg("inWar", seite(WIR, 1, 2, 3), seite("#Y")));
		kriege.put("#A3", krieg("preparation", seite("#Z"), seite(WIR, 18, 1, 4, 2, 9)));
		kriege.put("#B3", krieg("preparation", seite("#Q", 1), seite("#R", 1)));
		JSONObject l = liga("inWar", List.of(List.of("#A1"), List.of("#A2"), List.of("#B3", "#A3"),
				List.of("#0", "#0")));
		ArrayList<Player> a = cwdonator.cwlTagesaufstellung(WIR, l, kriege::get);
		assertNotNull(a);
		assertEquals(5, a.size());
		assertEquals(List.of(1, 2, 3, 4, 5), plaetze(a));
		// Reihenfolge nach der echten mapPosition: 1, 2, 4, 9, 18
		assertEquals(WIR + "-1", a.get(0).getTag());
		assertEquals(WIR + "-4", a.get(2).getTag());
		assertEquals(WIR + "-18", a.get(4).getTag());
	}

	@Test
	void letzterTagNimmtDenLaufendenKrieg() {
		Map<String, JSONObject> kriege = new HashMap<>();
		kriege.put("#A6", krieg("warEnded", seite(WIR, 1, 2), seite("#X")));
		kriege.put("#A7", krieg("inWar", seite("#X"), seite(WIR, 3, 7)));
		ArrayList<Player> a = cwdonator.cwlTagesaufstellung("lost", liga("inWar",
				List.of(List.of("#A6"), List.of("#A7"))), kriege::get);
		assertEquals(List.of(1, 2), plaetze(a));
	}

	@Test
	void nachDemLetztenKriegNichtsMehr() {
		Map<String, JSONObject> kriege = new HashMap<>();
		kriege.put("#A7", krieg("warEnded", seite(WIR, 1, 2), seite("#X")));
		assertNull(cwdonator.cwlTagesaufstellung(WIR, liga("inWar", List.of(List.of("#A7"))), kriege::get));
		assertNull(cwdonator.cwlTagesaufstellung(WIR, liga("ended", List.of(List.of("#A7"))), kriege::get));
		assertNull(cwdonator.cwlTagesaufstellung(WIR, new JSONObject().put("state", "notInWar"), kriege::get));
		assertNull(cwdonator.cwlTagesaufstellung(WIR, null, kriege::get));
	}

	@Test
	void fehlenderKriegWirdUebersprungen() {
		Map<String, JSONObject> kriege = new HashMap<>();
		kriege.put("#A1", krieg("inWar", seite(WIR, 1, 2, 3), seite("#X")));
		// #A2 liefert die API gerade nicht (null) -> zurück zur Runde davor
		ArrayList<Player> a = cwdonator.cwlTagesaufstellung(WIR, liga("inWar",
				List.of(List.of("#A1"), List.of("#A2"))), kriege::get);
		assertEquals(3, a.size());
	}
}
