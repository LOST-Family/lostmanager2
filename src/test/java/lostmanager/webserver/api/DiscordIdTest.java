package lostmanager.webserver.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * GET /api/users/{id} legte für jede unbekannte ID eine Zeile in users an
 * (20.11.2026: "abc" und "1" aus einer eigenen Probe). Nur echte Discord-IDs
 * dürfen bis zur Datenbank kommen.
 */
class DiscordIdTest {

	@Test
	void nurSnowflakesGeltenAlsDiscordId() {
		assertTrue(RestApiServer.istDiscordId("733857906117574717"));
		assertTrue(RestApiServer.istDiscordId("1547314036646084628"));
		assertFalse(RestApiServer.istDiscordId("abc"));
		assertFalse(RestApiServer.istDiscordId("1"));
		assertFalse(RestApiServer.istDiscordId(""));
		assertFalse(RestApiServer.istDiscordId(null));
		assertFalse(RestApiServer.istDiscordId("73385790611757471x"));
		assertFalse(RestApiServer.istDiscordId("733857906117574717 "));
		assertFalse(RestApiServer.istDiscordId("733857906117574717\n"));
		assertFalse(RestApiServer.istDiscordId("１２３４５６７８９０１２３４５６７"));
	}
}
