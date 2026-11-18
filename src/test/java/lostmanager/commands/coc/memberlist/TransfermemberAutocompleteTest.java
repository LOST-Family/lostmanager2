package lostmanager.commands.coc.memberlist;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * /transfermember: Clan-Feld vor dem Spieler ausgefüllt. Bis 18.11.2026 starb dabei
 * der Autocomplete-Thread an einer NullPointerException (11.11. im Journal), Discord
 * zeigte „Optionen konnten nicht geladen werden“.
 */
class TransfermemberAutocompleteTest {

	@Test
	void ohneSpielerKeinClanUndKeinAbsturz() {
		assertNull(transfermember.aktuellerClan(null));
	}
}
