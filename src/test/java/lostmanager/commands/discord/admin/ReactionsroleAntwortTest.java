package lostmanager.commands.discord.admin;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.EmbedBuilder;

class ReactionsroleAntwortTest {

	private static List<String> erwaehnungen(int n) {
		List<String> l = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			l.add("<@" + (733857906117574717L + i) + ">");
		}
		return l;
	}

	@Test
	void kleineListeVollstaendig() {
		String t = reactionsrole.baueAntwort("✅", "https://discord.com/channels/1/2/3", "<@&9>", erwaehnungen(3),
				erwaehnungen(1), List.of());
		assertTrue(t.contains("**Rolle vergeben an (3):**\n- <@733857906117574717>\n"));
		assertTrue(t.contains("**Hatte Rolle bereits (1):**"));
		assertFalse(t.contains("Nicht im Server"));
		assertFalse(t.contains("weitere"));
	}

	@Test
	void dreihundertReagierendePassenInEinEmbed() {
		String t = reactionsrole.baueAntwort("✅", "https://discord.com/channels/1/2/3", "<@&9>", erwaehnungen(300),
				erwaehnungen(40), erwaehnungen(5));
		assertTrue(t.contains("**Rolle vergeben an (300):**"), "die echte Zahl bleibt stehen");
		assertTrue(t.contains("… und "));
		assertTrue(t.contains("**Hatte Rolle bereits (40):**"));
		assertTrue(t.contains("**Nicht im Server oder Fehler (5):**"));
		// Früher warf genau das hier:
		assertDoesNotThrow(() -> new EmbedBuilder().setDescription(t).build());
		assertTrue(t.length() <= 4096);
	}
}
