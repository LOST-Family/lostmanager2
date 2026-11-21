package lostmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.utils.data.DataArray;
import net.dv8tion.jda.api.utils.data.DataObject;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

class PingSchutzTest {

	@Test
	void spielernameEveryonePingtNichtNutzerUndRollenSchon() {
		PingSchutz.setzeStandard();
		MessageCreateData m = new MessageCreateBuilder()
				.setContent("Spender heute: @everyone, @here, <@733857906117574717>, <@&1108449987827876022>")
				.build();
		assertFalse(m.getAllowedMentions().contains(MentionType.EVERYONE));
		assertFalse(m.getAllowedMentions().contains(MentionType.HERE));
		assertTrue(m.getAllowedMentions().contains(MentionType.USER));
		assertTrue(m.getAllowedMentions().contains(MentionType.ROLE));

		// Was wirklich an Discord geht:
		DataObject am = m.toData().getObject("allowed_mentions");
		DataArray parse = am.getArray("parse");
		boolean users = false, roles = false;
		for (int i = 0; i < parse.length(); i++) {
			String p = parse.getString(i);
			assertNotEquals("everyone", p, "everyone darf nicht in parse stehen");
			users |= p.equals("users");
			roles |= p.equals("roles");
		}
		assertTrue(users && roles, "Nutzer- und Rollen-Pings müssen erlaubt bleiben: " + am);
	}
}
