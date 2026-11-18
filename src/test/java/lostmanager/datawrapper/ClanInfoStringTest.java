package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Der Clan-Kopf darf bei API-Ausfall nicht werfen, sondern nimmt den Namen aus der Datenbank. */
class ClanInfoStringTest {

	@Test
	void apiNameGehtVor() {
		assertEquals("LOST (#2ABC)", Clan.infoString(() -> "LOST", () -> "DB-Name", "#2ABC"));
	}

	@Test
	void apiAusfallNimmtDatenbank() {
		assertEquals("LOST F2P (#2ABC)", Clan.infoString(() -> {
			throw new NullPointerException("kein JSON");
		}, () -> "LOST F2P", "#2ABC"));
	}

	@Test
	void ohneJedenNamenFragezeichen() {
		assertEquals("? (#2ABC)", Clan.infoString(() -> {
			throw new NullPointerException("kein JSON");
		}, () -> null, "#2ABC"));
		assertEquals("? (#2ABC)", Clan.infoString(() -> " ", () -> "", "#2ABC"));
	}
}
