package lostmanager.datawrapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

/**
 * Saisonende-Event „CW-Anzahl“: ein Krieg, dessen Endzeit Supercell verschoben hat, darf nur
 * einmal zählen (35 doppelte Endzeiten bis 16.11.2026, z. B. #2QC0QQPQ2 13.10. 20:05:19 und
 * 20:39:46).
 */
class CwAnzahlTest {

	@Test
	void sqlZaehltNaheEndzeitenAlsEinenKrieg() {
		String sql = ListeningEvent.cwAnzahlSql("?,?");
		assertTrue(sql.contains("PARTITION BY clan_tag ORDER BY war_end_time"));
		assertTrue(sql.contains("war_end_time - vorige >= INTERVAL '12 hours'"));
		assertTrue(sql.contains("clan_tag IN (?,?)"));
		// Parameterreihenfolge wie im Aufrufer: player_tag, Clans, Beginn, Ende
		assertEquals(5, sql.chars().filter(c -> c == '?').count());
		assertTrue(sql.indexOf("player_tag = ?") < sql.indexOf("clan_tag IN"));
		assertTrue(sql.indexOf("clan_tag IN") < sql.indexOf("war_end_time >= ?"));
	}

	@Test
	void aufraeumenTrifftNurDenselbenKrieg() {
		String sql = lostmanager.Bot.CW_ALTE_ENDZEIT_AUFRAEUMEN;
		assertTrue(sql.startsWith("DELETE FROM cw_participation WHERE clan_tag = ?"));
		assertTrue(sql.contains("war_end_time <> ?"));
		assertTrue(sql.contains("- INTERVAL '12 hours'"));
		assertTrue(sql.contains("+ INTERVAL '12 hours'"));
		assertEquals(4, sql.chars().filter(c -> c == '?').count());
	}

	/**
	 * Gegen die echte Datenbank, nur lesend. Für jeden Spieler und jede Season mit Daten muss die
	 * Zählung des Events dasselbe ergeben wie eine unabhängige Zählung in Java (Endzeiten je Clan
	 * sortiert, neuer Krieg erst ab 12 h Abstand) – und nie mehr als die alte Zählung
	 * {@code COUNT(DISTINCT war_end_time)}.
	 */
	@Test
	void echteDatenGleichUnabhaengigerZaehlung() throws Exception {
		String url = System.getenv("LOST_MANAGER_DB_URL");
		assumeTrue(url != null && !url.isBlank(), "keine Datenbank konfiguriert");
		Connection.url = url + (url.contains("?") ? "&" : "?") + "options=-c%20default_transaction_read_only=on";
		Connection.user = System.getenv("LOST_MANAGER_DB_USER");
		Connection.password = System.getenv("LOST_MANAGER_DB_PASSWORD");

		// Spieler -> Clan -> sortierte Endzeiten
		Map<String, Map<String, List<Timestamp>>> roh = new HashMap<>();
		try (PreparedStatement st = Connection.getConnection().prepareStatement(
				"SELECT DISTINCT player_tag, clan_tag, war_end_time FROM cw_participation WHERE war_type = 'cw' "
						+ "ORDER BY war_end_time");
				ResultSet rs = st.executeQuery()) {
			while (rs.next()) {
				roh.computeIfAbsent(rs.getString(1), k -> new TreeMap<>())
						.computeIfAbsent(rs.getString(2), k -> new ArrayList<>()).add(rs.getTimestamp(3));
			}
		}
		assumeTrue(!roh.isEmpty(), "keine Teilnahmedaten");

		Timestamp von = Timestamp.valueOf("2000-01-01 00:00:00");
		Timestamp bis = Timestamp.valueOf("2100-01-01 00:00:00");
		String sql = ListeningEvent.cwAnzahlSql("?");
		int weniger = 0;
		int geprueft = 0;
		for (Map.Entry<String, Map<String, List<Timestamp>>> sp : roh.entrySet()) {
			for (Map.Entry<String, List<Timestamp>> cl : sp.getValue().entrySet()) {
				int erwartet = 0;
				Timestamp vorige = null;
				for (Timestamp t : cl.getValue()) {
					if (vorige == null || t.getTime() - vorige.getTime() >= 12L * 3600 * 1000) {
						erwartet++;
					}
					vorige = t;
				}
				Long neu = DBUtil.getValueFromSQL(sql, Long.class, sp.getKey(), cl.getKey(), von, bis);
				assertEquals(erwartet, neu.intValue(), sp.getKey() + " in " + cl.getKey());
				int alt = cl.getValue().size();
				assertTrue(neu <= alt);
				if (neu < alt) {
					weniger++;
				}
				geprueft++;
			}
		}
		System.out.println("CW-Anzahl: " + geprueft + " Spieler/Clan-Paare geprüft, " + weniger
				+ " zählten bisher einen verschobenen Krieg doppelt");
		assertTrue(geprueft > 0);
	}
}
