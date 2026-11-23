package lostmanager.commands.coc.memberlist;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import lostmanager.datawrapper.Player.RoleType;

/**
 * /transfermember: Rangregel und veraltete Anfragen. Bis 21.11.2026 galt die
 * Rangregel nur auf dem direkten Weg; über die Anfrage mit Bestätigung konnten
 * zwei Vize einen Anführer verschieben (er wurde dabei Mitglied), und ein alter
 * Knopf zog den Spieler auch aus einem ganz anderen Clan.
 */
class TransfermemberRegelTest {

	@Test
	void anfuehrerBewegtNurEinAdmin() {
		assertNull(transfermember.rangSperre(RoleType.LEADER, RoleType.ADMIN));
		assertNotNull(transfermember.rangSperre(RoleType.LEADER, RoleType.LEADER));
		assertNotNull(transfermember.rangSperre(RoleType.LEADER, RoleType.COLEADER));
		assertNotNull(transfermember.rangSperre(RoleType.LEADER, null));
	}

	@Test
	void vizeBewegtNurAdminOderAnfuehrer() {
		assertNull(transfermember.rangSperre(RoleType.COLEADER, RoleType.ADMIN));
		assertNull(transfermember.rangSperre(RoleType.COLEADER, RoleType.LEADER));
		assertNotNull(transfermember.rangSperre(RoleType.COLEADER, RoleType.COLEADER));
		assertNotNull(transfermember.rangSperre(RoleType.COLEADER, RoleType.NOTINCLAN));
		assertNotNull(transfermember.rangSperre(RoleType.COLEADER, null));
	}

	@Test
	void aeltesteUndMitgliederBleibenFrei() {
		// Wer hier ankommt, ist als Vize oder höher schon geprüft; der Rang im alten
		// Clan darf bei der Anfrage aus dem Zielclan auch unbekannt sein.
		for (RoleType wer : new RoleType[] { RoleType.COLEADER, RoleType.LEADER, RoleType.ADMIN, null }) {
			assertNull(transfermember.rangSperre(RoleType.ELDER, wer));
			assertNull(transfermember.rangSperre(RoleType.MEMBER, wer));
		}
	}

	@Test
	void anfrageVeraltetWennSpielerNichtMehrImAltenClan() {
		assertFalse(transfermember.veraltet("#ALT", "#ALT", "#NEU"));
		assertTrue(transfermember.veraltet(null, "#ALT", "#NEU"), "aus dem Clan entfernt");
		assertTrue(transfermember.veraltet("#DRITTER", "#ALT", "#NEU"), "inzwischen woanders");
		assertTrue(transfermember.veraltet("#NEU", "#ALT", "#NEU"), "schon verschoben (zweiter Klick)");
	}
}
