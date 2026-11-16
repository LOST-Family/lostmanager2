package lostmanager.commands.coc.util.jsonutils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import lostmanager.commands.coc.util.jsonutils.UploadedPlayerChoices.Entry;
import lostmanager.datawrapper.Player;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Spielervorschläge von /stats und /f2pcheck. Erfundene Tags im reinen Teil;
 * der Datenbankteil liest nur (default_transaction_read_only) und überspringt
 * sich ohne LOST_MANAGER_DB_URL.
 */
class UploadedPlayerChoicesTest {

	private static List<String> namen(List<Command.Choice> c) {
		return c.stream().map(Command.Choice::getName).toList();
	}

	@Test
	void textMitNameTagUndClan() {
		List<Entry> e = List.of(new Entry("#AAA", "Anna", "LOST 1"), new Entry("#BBB", "Bert", null),
				new Entry("#CCC", "Cleo", ""));
		assertEquals(List.of("Anna (#AAA) - LOST 1", "Bert (#BBB)", "Cleo (#CCC)"),
				namen(UploadedPlayerChoices.choices(e, "", t -> null)));
	}

	@Test
	void ohneNameStehtDerTagUndNieNull() {
		List<Entry> e = List.of(new Entry("#AAA", null, "LOST 1"), new Entry("#BBB", " ", null));
		List<String> n = namen(UploadedPlayerChoices.choices(e, "", t -> null));
		assertEquals(List.of("#AAA - LOST 1", "#BBB"), n);
		assertEquals(List.of("#AAA - LOST 1", "#BBB"), namen(UploadedPlayerChoices.choices(e, "", null)));
		assertFalse(n.toString().contains("null"));
	}

	@Test
	void ersatznameWirdNurOhneDatenbanknameGefragt() {
		List<String> gefragt = new ArrayList<>();
		List<Entry> e = List.of(new Entry("#AAA", "Anna", null), new Entry("#BBB", null, null));
		List<String> n = namen(UploadedPlayerChoices.choices(e, "", t -> {
			gefragt.add(t);
			return "AusDerApi";
		}));
		assertEquals(List.of("Anna (#AAA)", "AusDerApi (#BBB)"), n);
		assertEquals(List.of("#BBB"), gefragt);
	}

	@Test
	void filterTextEnthaeltOderTagBeginntOhneGrossKlein() {
		List<Entry> e = List.of(new Entry("#AAA", "Anna", "LOST 1"), new Entry("#ABB", "Bert", "Lost 2"),
				new Entry("#CCC", "Cleo", null));
		assertEquals(List.of("Anna (#AAA) - LOST 1"), namen(UploadedPlayerChoices.choices(e, "ANN", t -> null)));
		assertEquals(List.of("Anna (#AAA) - LOST 1", "Bert (#ABB) - Lost 2"),
				namen(UploadedPlayerChoices.choices(e, "lost", t -> null)));
		assertEquals(List.of("Anna (#AAA) - LOST 1", "Bert (#ABB) - Lost 2"),
				namen(UploadedPlayerChoices.choices(e, "#a", t -> null)));
		assertEquals(List.of("Cleo (#CCC)"), namen(UploadedPlayerChoices.choices(e, "ccc", t -> null)));
		assertTrue(UploadedPlayerChoices.choices(e, "gibtsnicht", t -> null).isEmpty());
		assertEquals(3, UploadedPlayerChoices.choices(e, null, t -> null).size());
	}

	@Test
	void hoechstens25UndWertIstDerTag() {
		List<Entry> e = new ArrayList<>();
		for (int i = 0; i < 60; i++) {
			e.add(new Entry("#T" + String.format("%02d", i), "Spieler" + i, null));
		}
		List<Command.Choice> c = UploadedPlayerChoices.choices(e, "", t -> null);
		assertEquals(25, c.size());
		assertEquals("#T00", c.get(0).getAsString());
		assertEquals("#T24", c.get(24).getAsString());
		// Der Filter greift vor der Grenze: Treffer hinter den ersten 25 kommen an.
		assertEquals(List.of("#T59"), UploadedPlayerChoices.choices(e, "spieler59", t -> null).stream()
				.map(Command.Choice::getAsString).toList());
	}

	@Test
	void ueberlangerTextWirdGekapptUndLeereTagsFallenWeg() {
		List<Entry> e = new ArrayList<>();
		e.add(new Entry("#AAA", "x".repeat(150), "Clan"));
		e.add(new Entry(null, "Niemand", null));
		e.add(new Entry(" ", "Niemand", null));
		List<Command.Choice> c = UploadedPlayerChoices.choices(e, "", t -> null);
		assertEquals(1, c.size());
		assertEquals(100, c.get(0).getName().length());
	}

	/**
	 * Die eine Abfrage muss für jeden Spieler denselben Text liefern wie der
	 * frühere Weg über Player/Clan-Objekte (drei bis vier Abfragen je Tag).
	 */
	@Test
	void eineAbfrageLiefertDasselbeWieDerAlteWeg() {
		String url = System.getenv("LOST_MANAGER_DB_URL");
		assumeTrue(url != null && !url.isBlank(), "keine Datenbank konfiguriert");
		Connection.url = url + (url.contains("?") ? "&" : "?") + "options=-c%20default_transaction_read_only=on";
		Connection.user = System.getenv("LOST_MANAGER_DB_USER");
		Connection.password = System.getenv("LOST_MANAGER_DB_PASSWORD");

		long t0 = System.nanoTime();
		List<Entry> neu = UploadedPlayerChoices.load();
		long msNeu = (System.nanoTime() - t0) / 1_000_000;
		assertFalse(neu.isEmpty());

		t0 = System.nanoTime();
		List<String> tags = DBUtil.getArrayListFromSQL("SELECT DISTINCT tag FROM userjsons ORDER BY tag",
				String.class);
		List<String> alt = new ArrayList<>();
		int ohneName = 0;
		for (String tag : tags) {
			Player p = new Player(tag);
			String clan = p.getClanDB() != null ? p.getClanDB().getNameDB() : null;
			String name = p.getNameDB();
			if (name == null || name.isBlank()) {
				ohneName++;
			}
			String text = name == null || name.isBlank() ? tag : name + " (" + tag + ")";
			if (clan != null && !clan.isEmpty()) {
				text += " - " + clan;
			}
			alt.add(text);
		}
		long msAlt = (System.nanoTime() - t0) / 1_000_000;

		assertEquals(tags, neu.stream().map(Entry::tag).toList(), "gleiche Tags in gleicher Reihenfolge");
		assertEquals(alt, neu.stream().map(e -> UploadedPlayerChoices.display(e, t -> null)).toList());
		System.out.println("UploadedPlayerChoices: " + neu.size() + " Tags, " + ohneName + " ohne Namen, eine Abfrage "
				+ msNeu + " ms, alter Weg " + msAlt + " ms");
	}
}
