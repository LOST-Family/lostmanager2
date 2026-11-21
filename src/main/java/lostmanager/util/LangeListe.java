package lostmanager.util;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Listen von Mitgliedern, die länger werden können, als Discord erlaubt.
 *
 * Anlass (20.11.2026): /checkreacts baute die Beschreibung ohne jede Grenze. Für Clan-Rollen
 * (höchstens gut 50 Leute) reicht das, aber auf LOST Family haben Rollen wie „Turnier / Events“
 * knapp 700 Mitglieder – dann wirft JDA beim Bau des Embeds (über 4096 Zeichen), der
 * Rückruf stirbt, und die Antwort bleibt für immer auf „Wird geladen...“ stehen. Dieselbe
 * Grenze gibt es für den Nachrichtentext (2000 Zeichen): dort liegen die IDs für den
 * Ping-Knopf, und die Ping-Nachricht selbst ist ab etwa 86 Erwähnungen zu lang.
 */
public final class LangeListe {

	/** Luft unter den 4096 Zeichen einer Embed-Beschreibung. */
	public static final int BESCHREIBUNG_WEICH = 3800;
	/** Discord-Grenze für den Text einer Nachricht. */
	public static final int NACHRICHT_MAX = 2000;

	private LangeListe() {
	}

	/**
	 * Hängt je Zeile einen Eintrag an und hört mit „… und N weitere“ auf, sobald die
	 * Beschreibung die Grenze erreichen würde. Gibt zurück, wie viele Zeilen angehängt wurden.
	 */
	public static int anhaengen(StringBuilder beschreibung, List<String> zeilen, int grenze) {
		for (int i = 0; i < zeilen.size(); i++) {
			String zeile = zeilen.get(i) + "\n";
			if (beschreibung.length() + zeile.length() > grenze) {
				beschreibung.append("… und ").append(zeilen.size() - i).append(" weitere\n");
				return i;
			}
			beschreibung.append(zeile);
		}
		return zeilen.size();
	}

	/**
	 * Nutzer-IDs kompakt (Base64 über je 8 Byte) für den Nachrichtentext, aus dem der
	 * Ping-Knopf sie wieder liest. {@code null}, wenn es nicht in eine Nachricht passt
	 * (ab 188 IDs) – dann gibt es keinen Ping-Knopf.
	 */
	public static String kodiereIds(List<String> userIds) {
		ByteBuffer puffer = ByteBuffer.allocate(userIds.size() * 8);
		for (String id : userIds) {
			puffer.putLong(Long.parseLong(id));
		}
		String text = Base64.getUrlEncoder().withoutPadding().encodeToString(puffer.array());
		return text.length() > NACHRICHT_MAX ? null : text;
	}

	/** Erwähnungen auf so viele Nachrichten verteilt, dass jede unter 2000 Zeichen bleibt. */
	public static List<String> pingNachrichten(List<String> userIds) {
		List<String> nachrichten = new ArrayList<>();
		StringBuilder aktuell = new StringBuilder();
		for (String id : userIds) {
			String erwaehnung = "<@" + id + ">";
			if (aktuell.length() > 0 && aktuell.length() + 1 + erwaehnung.length() > NACHRICHT_MAX) {
				nachrichten.add(aktuell.toString());
				aktuell.setLength(0);
			}
			if (aktuell.length() > 0) {
				aktuell.append(' ');
			}
			aktuell.append(erwaehnung);
		}
		if (aktuell.length() > 0) {
			nachrichten.add(aktuell.toString());
		}
		return nachrichten;
	}

	/**
	 * Teilt einen mehrzeiligen Text an Zeilengrenzen in Stücke von höchstens {@code max}
	 * Zeichen. Anlass: /raidping schickte seine Liste als eine Nachricht – am Raid-Freitag
	 * mit 50 offenen Mitgliedern sind das über 2000 Zeichen, JDA wirft, und es kommt gar
	 * nichts an. Eine einzelne überlange Zeile wird hart geschnitten.
	 */
	public static List<String> teileNachZeilen(String text, int max) {
		List<String> teile = new ArrayList<>();
		StringBuilder aktuell = new StringBuilder();
		for (String zeile : text.split("\n", -1)) {
			while (zeile.length() > max) {
				if (aktuell.length() > 0) {
					teile.add(aktuell.toString());
					aktuell.setLength(0);
				}
				teile.add(zeile.substring(0, max));
				zeile = zeile.substring(max);
			}
			if (aktuell.length() > 0 && aktuell.length() + 1 + zeile.length() > max) {
				teile.add(aktuell.toString());
				aktuell.setLength(0);
			}
			if (aktuell.length() > 0) {
				aktuell.append('\n');
			}
			aktuell.append(zeile);
		}
		if (!aktuell.toString().isBlank()) {
			teile.add(aktuell.toString());
		}
		return teile;
	}
}
