package lostmanager.commands.coc.util.jsonutils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Die Zuordnung Image-Map-Eintrag -> Stat-Typ, auf der {@code /stats missing}
 * steht. Die Einträge unten sind wörtlich aus der echten image_map.json
 * (Stand 26.11.2026) - jeder von ihnen stand in der Fehlend-Ansicht falsch:
 * Equips fand gar nichts, der Sky Wagon galt als Truppe, die BB-Szenerien als
 * Szenerien des Heimatdorfs, die B.O.B-Hütte als Gebäude der Bauarbeiterbasis.
 */
class StatTypZuordnungTest {

	private static JSONObject karte() {
		return new JSONObject("""
				{
				 "1000064": {"name": "B.O.B Hut", "levels": {"1": {"level-icon": "/builder-base/buildings/bob-hut/bob-hut-1.png"}}},
				 "1000008": {"name": "Cannon", "levels": {"1": {"level-icon": "/home-base/buildings/cannon/cannon-1.png"}}},
				 "1000034": {"name": "Builder Hall", "levels": {"1": {"level-icon": "/builder-base/buildings/builder-hall/builder-hall-1.png"}}},
				 "90000000": {"name": "Barbarian Puppet", "icon": "/home-base/equipments/0.png"},
				 "4000188": {"name": "Sky Wagon", "character": "/home-base/troops/sky-wagon/sky-wagon-character.png"},
				 "4000051": {"name": "Wall Wrecker", "character": "/home-base/troops/wall-wrecker/wall-wrecker-character.png"},
				 "4000000": {"name": "Barbarian", "character": "/home-base/troops/barbarian/barbarian-character.png"},
				 "60000047": {"name": "Crystal Caverns Scenery (BB, Jul. 2023)", "icon": "/home-base/sceneries/47.png"},
				 "60000000": {"name": "Classic Scenery", "icon": "/home-base/sceneries/0.png"},
				 "8000055": {"name": "Old Barbarian Statue", "icon": "/home-base/obstacles/55.png"},
				 "8000000": {"name": "Pine Tree", "icon": "/home-base/obstacles/0.png"},
				 "18000041": {"name": "Ancient Barbarian Statue (BB) (May 2017)", "icon": "/home-base/decorations/41.png"}
				}""");
	}

	private static Set<String> ids(String typ) {
		return stats.getAllIdsForStatType(typ, karte());
	}

	@Test
	void equipsWerdenGefunden() {
		assertEquals(Set.of("90000000"), ids("Equips"));
	}

	@Test
	void skyWagonIstBelagerungsmaschineUndKeineTruppe() {
		assertEquals(Set.of("4000188", "4000051"), ids("Sieges"));
		assertEquals(Set.of("4000000"), ids("Units"));
	}

	@Test
	void bbSzenerieMitDatumInDerKlammer() {
		assertEquals(Set.of("60000047"), ids("Sceneries (BB)"));
		assertEquals(Set.of("60000000"), ids("Sceneries"));
	}

	@Test
	void bobHuetteGehoertZumHeimatdorf() {
		assertEquals(Set.of("1000064", "1000008"), ids("Buildings"));
		assertEquals(Set.of("1000034"), ids("Defensive (BB)"));
	}

	@Test
	void alteBarbarenstatueGehoertZurBauarbeiterbasis() {
		assertEquals(Set.of("8000000"), ids("Obstacles"));
		assertEquals(Set.of("8000055", "18000041"), ids("Decorative (BB)"));
	}

	@Test
	void jederEintragGehoertZuGenauEinemTyp() {
		JSONObject karte = karte();
		for (String id : karte.keySet()) {
			int treffer = 0;
			for (String typ : new String[] { "Buildings", "Decorative (BB)", "Decos", "Defensive (BB)", "Emotes",
					"Equips", "Guardians", "Helpers", "Heroes", "Heroes (BB)", "House Parts", "Obstacles", "Pets",
					"Sceneries", "Sceneries (BB)", "Sieges", "Skins", "Skins (BB)", "Spells", "Squads", "Traps",
					"Units", "Units (BB)" }) {
				if (stats.getAllIdsForStatType(typ, karte).contains(id)) {
					treffer++;
				}
			}
			assertEquals(1, treffer, "Eintrag " + id);
		}
	}
}
