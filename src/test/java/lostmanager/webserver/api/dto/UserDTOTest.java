package lostmanager.webserver.api.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import lostmanager.datawrapper.Player.RoleType;

/**
 * Der höchste Rang einer Person über alle Clans – daraus macht die Website
 * ihre Sichtbarkeit. Bis 18.11.2026 gewann der Rang, der zufällig zuletzt kam.
 */
class UserDTOTest {

	@Test
	void hoechsterRangGewinntUnabhaengigVonDerReihenfolge() {
		List<RoleType> raenge = Arrays.asList(RoleType.NOTINCLAN, RoleType.COLEADER, RoleType.MEMBER,
				RoleType.ELDER, RoleType.NOTINCLAN);
		assertEquals(RoleType.COLEADER, UserDTO.hoechsteRolle(raenge));
		Collections.reverse(raenge);
		assertEquals(RoleType.COLEADER, UserDTO.hoechsteRolle(raenge));
	}

	@Test
	void vizeMitZweitaccountAlsMitgliedBleibtVize() {
		// Genau der Fall, der vorher schiefging: Mitglied kommt nach Vize.
		LinkedHashMap<String, RoleType> jeClan = new LinkedHashMap<>();
		jeClan.put("#A", RoleType.COLEADER);
		jeClan.put("#B", RoleType.MEMBER);
		assertEquals(RoleType.COLEADER, UserDTO.hoechsteRolle(jeClan.values()));
	}

	@Test
	void anfuehrerVorVizeUndAdminVorAllem() {
		assertEquals(RoleType.LEADER, UserDTO.hoechsteRolle(Arrays.asList(RoleType.ELDER, RoleType.LEADER, RoleType.COLEADER)));
		assertEquals(RoleType.ADMIN, UserDTO.hoechsteRolle(Arrays.asList(RoleType.LEADER, RoleType.ADMIN, RoleType.MEMBER)));
	}

	@Test
	void ohneClanKeinRang() {
		assertNull(UserDTO.hoechsteRolle(Arrays.asList(RoleType.NOTINCLAN, RoleType.NOTINCLAN)));
		assertNull(UserDTO.hoechsteRolle(Collections.emptyList()));
	}
}
