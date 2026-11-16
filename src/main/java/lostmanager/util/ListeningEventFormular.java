package lostmanager.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import lostmanager.datawrapper.ListeningEvent;

/**
 * Die Zusatzwerte eines Listening Events - alles, was {@code /listeningevent add}
 * nach den Slash-Optionen noch in einem Formular (Modal) abfragt: Schwellen,
 * benötigte Angriffe, Sterne, der Text einer eigenen Nachricht und so weiter.
 *
 * Bis zum 14.11.2026 stand das zweimal im Befehl: einmal als Modal-Aufbau, einmal
 * als neun Zweige zum Auslesen. Mit dem Bearbeiten kam ein dritter Nutzer dazu,
 * und der braucht dieselben Felder, nur vorausgefüllt. Damit Anlegen und Ändern
 * nicht auseinanderlaufen, gibt es die Felder und ihre Prüfung nur noch hier:
 *
 * <ul>
 * <li>{@link #art} entscheidet, welches Formular eine Kombination aus Typ und
 * Aktion braucht (dieselbe Reihenfolge wie früher im Befehl).</li>
 * <li>{@link #felder} liefert seine Felder samt Beschriftung und Vorgabe.</li>
 * <li>{@link #lies} prüft die Eingaben und macht daraus {@link Werte}.</li>
 * <li>{@link #vorbelegung} geht den umgekehrten Weg, für das Bearbeiten.</li>
 * </ul>
 *
 * Ohne JDA, damit es sich ohne Discord prüfen lässt; das Modal baut der Befehl
 * aus den {@link Feld}ern.
 */
public final class ListeningEventFormular {

	private ListeningEventFormular() {
	}

	/** Welches Formular eine Kombination braucht. Der Präfix steckt in der Modal-ID. */
	public enum Art {
		CS_THRESHOLD("listeningevent_cs_threshold_", "Clan Games Threshold eingeben"),
		CW_ATTACKS("listeningevent_cw_attacks_", "Benötigte Angriffe eingeben"),
		SEASONWINS("listeningevent_seasonwins_", "Season Wins Einstellungen"),
		CWCOUNT("listeningevent_cwcount_", "CW-Teilnahme konfigurieren"),
		CWLDAYKP("listeningevent_cwldaykp_", "CWL Kickpoint Einstellungen"),
		RAIDFAILS("listeningevent_raidfails_", "Raidfails Distrikt Einstellungen"),
		CUSTOMMESSAGE("listeningevent_custommessage_", "Benutzerdefinierte Nachricht eingeben"),
		CWDONATOR("listeningevent_cwdonator_params_", "CW Donator Einstellungen"),
		STARFAILS("listeningevent_starfails_", "Schlechte Angriffe konfigurieren");

		public final String praefix;
		public final String titel;

		Art(String praefix, String titel) {
			this.praefix = praefix;
			this.titel = titel;
		}

		/** @return die Art zu einer Modal-ID des Anlegens, oder null */
		public static Art ausModalId(String modalId) {
			for (final Art a : values()) {
				if (modalId.startsWith(a.praefix)) {
					return a;
				}
			}
			return null;
		}
	}

	/**
	 * Ein Formularfeld.
	 *
	 * @param minLaenge 0 = keine Untergrenze
	 * @param vorgabe   Vorbelegung beim Anlegen, null = leer
	 */
	public record Feld(String id, String label, boolean absatz, String platzhalter, boolean pflicht, int minLaenge,
			int maxLaenge, String vorgabe) {
	}

	/**
	 * Was aus einem Formular in den {@link ListeningEventService.Spec} wandert.
	 * Nicht gesetzte Felder bleiben null.
	 */
	public static final class Werte {
		public Integer thresholdOrAttacks;
		public Integer starCount;
		public Integer punishmentMode;
		public Map<String, Integer> raidDistrictThresholds;
		public Map<String, Long> namedSettings = new HashMap<>();
		public String customMessage;
		public Integer useLists;
		public Integer excludeLeaders;

		/** Schreibt die Werte in einen Spec und ersetzt dabei alle bisherigen Zusatzwerte. */
		public void in(ListeningEventService.Spec s) {
			s.thresholdOrAttacks = thresholdOrAttacks;
			s.starCount = starCount;
			s.punishmentMode = punishmentMode;
			s.raidDistrictThresholds = raidDistrictThresholds;
			s.namedSettings = namedSettings.isEmpty() ? null : new HashMap<>(namedSettings);
			s.customMessage = customMessage;
			s.useLists = useLists;
			s.excludeLeaders = excludeLeaders;
		}
	}

	/** @param fehler null, wenn alles gültig war */
	public record Lesung(Werte werte, String fehler) {
	}

	/**
	 * Welches Formular {@code add} für diese Kombination zeigt. Reihenfolge und
	 * Bedingungen sind genau die des Befehls vor der Zusammenlegung.
	 *
	 * @param actionType wie eingegeben, also auch {@code raidfails_kickpoint}
	 * @return null, wenn keine Zusatzwerte gebraucht werden
	 */
	public static Art art(String type, String actionType) {
		if (type == null || actionType == null) {
			return null;
		}
		boolean infoOderKp = actionType.equals("infomessage") || actionType.equals("kickpoint");
		if (type.equals("cs") && infoOderKp) {
			return Art.CS_THRESHOLD;
		}
		if (type.equals("cw") && infoOderKp) {
			return Art.CW_ATTACKS;
		}
		if (type.equals("seasonend") && infoOderKp) {
			return Art.SEASONWINS;
		}
		if (actionType.equals("cwcount") || actionType.equals("cwcount_kickpoint")) {
			return Art.CWCOUNT;
		}
		if (type.equals("cwlday") && actionType.equals("kickpoint")) {
			return Art.CWLDAYKP;
		}
		if (type.equals("raid") && (actionType.equals("raidfails") || actionType.equals("raidfails_kickpoint"))) {
			return Art.RAIDFAILS;
		}
		if (actionType.equals("custommessage")) {
			return Art.CUSTOMMESSAGE;
		}
		if (actionType.equals("cwdonator")) {
			return Art.CWDONATOR;
		}
		if (actionType.equals("starfails") || actionType.equals("starfails_kickpoint")) {
			return Art.STARFAILS;
		}
		return null;
	}

	private static Feld perfekterKrieg() {
		return new Feld("ignore_perfect_war", "Kickpunkte auch bei perfektem Krieg?", false,
				"1 = Ja, 2 = Nein (Standard)", true, 1, 1, "2");
	}

	/**
	 * Die Felder des Formulars, in der Reihenfolge, in der sie erscheinen.
	 *
	 * @param mitGrund ob ein Kickpoint-Grund gesetzt ist - beim Raid-Distrikt-Check
	 *                 entscheidet das, ob nach Bestrafungsdetails gefragt wird
	 */
	public static List<Feld> felder(Art art, String type, String actionType, boolean mitGrund) {
		List<Feld> f = new ArrayList<>();
		if (art == null) {
			return f;
		}
		switch (art) {
			case CS_THRESHOLD -> f.add(new Feld("threshold", "Threshold (Punkte)", false, "z.B. 4000", true, 1, 10,
					"4000"));
			case CW_ATTACKS -> {
				f.add(new Feld("required_attacks", "Benötigte Angriffe", false, "1 oder 2 eingeben", true, 0, 1,
						null));
				// Nur Kickpunkt-Events betrifft die Ausnahme für perfekte Kriege
				if ("kickpoint".equals(actionType)) {
					f.add(perfekterKrieg());
				}
			}
			case SEASONWINS -> f.add(new Feld("wins_threshold", "Minimum Wins (leer = Clan-Einstellung)", false,
					"z.B. 70", false, 0, 5, null));
			case CWCOUNT -> f.add(new Feld("cw_min_count", "Minimum CWs pro Season", false,
					"z.B. 8 - gezählt wird die Teilnahme an der Aufstellung", true, 1, 3, null));
			case CWLDAYKP -> f.add(perfekterKrieg());
			case RAIDFAILS -> {
				f.add(new Feld("capital_peak_max", "Maximale Angriffe auf Capital Peak", false, "z.B. 10", true, 1, 3,
						"10"));
				f.add(new Feld("other_districts_max", "Maximale Angriffe auf restliche Distrikte", false, "z.B. 6",
						true, 1, 3, "6"));
				// Nur im Kickpunkt-Modus nach den Bestrafungsdetails fragen
				if (mitGrund) {
					f.add(new Feld("penalize_both", "Beide Spieler bestrafen? 1->Ja; 2->Nein", false, "1 oder 2",
							true, 1, 1, "1"));
					f.add(new Feld("raid_force_kickpoints", "Kickpunkte trotz unbestätigter Daten?", false,
							"1 = Ja, 2 = Nein (Standard)", true, 1, 1, "2"));
				}
			}
			case CUSTOMMESSAGE -> f.add(new Feld("custommessage", "Benutzerdefinierte Nachricht", true,
					"Gib die Nachricht ein, die gesendet werden soll...", true, 1, 2000, null));
			case CWDONATOR -> {
				f.add(new Feld("use_lists", "Listen-basierte Verteilung (1 oder 0)", false,
						"1 = aktiviert, 0 = deaktiviert", true, 1, 1, "0"));
				f.add(new Feld("exclude_leaders", "Leader ausschließen (1 oder 0)", false,
						"1 = aktiviert, 0 = deaktiviert", true, 1, 1, "0"));
			}
			case STARFAILS -> {
				f.add(new Feld("star_count", "Sterne-Anzahl (0, 1 oder 2)", false,
						"Angriffe mit genau X Sternen werden gemeldet/bestraft", true, 1, 1, null));
				// In der CWL hat jedes Mitglied genau einen Angriff, der Modus (der nur
				// mehrere Angriffe desselben Spielers unterscheidet) wirkt dort nicht.
				if (!"cwlday".equals(type)) {
					f.add(new Feld("punishment_mode", "Modus (1, 2 oder 3)", false,
							"1=Einmal pro Spieler, 2=Pro Angriff, 3=Nur wenn alle schlecht", true, 1, 1, "1"));
				}
				// 0 erhält das Verhalten aller Events von vor dieser Option
				f.add(new Feld("starfails_free_hits", "Freie Fehlversuche pro Spieler", false,
						"cwlday".equals(type) ? "0 = keine, 1 = der erste in der CWL ist frei"
								: "0 = keine, 1 = der erste im Krieg ist frei",
						true, 1, 2, "0"));
			}
		}
		return f;
	}

	/**
	 * Wie {@link #felder}, aber nur die IDs - zwei Kombinationen mit denselben IDs
	 * fragen dasselbe ab, und ihre Werte lassen sich übernehmen.
	 */
	public static List<String> feldIds(Art art, String type, String actionType, boolean mitGrund) {
		List<String> ids = new ArrayList<>();
		for (final Feld f : felder(art, type, actionType, mitGrund)) {
			ids.add(f.id());
		}
		return ids;
	}

	private static String text(Function<String, String> eingabe, String feld) {
		String wert = eingabe.apply(feld);
		return wert == null ? null : wert.trim();
	}

	/** "1" = ja, alles andere = nein (2). Fehlt das Feld, null. */
	private static Long jaNein(Function<String, String> eingabe, String feld) {
		String wert = text(eingabe, feld);
		if (wert == null) {
			return null;
		}
		return "1".equals(wert) ? 1L : 2L;
	}

	/**
	 * Prüft die Eingaben eines Formulars. Die Fehlermeldungen sind wörtlich die
	 * des Befehls vor der Zusammenlegung.
	 *
	 * @param eingabe Feld-ID → eingegebener Text, null wenn das Feld nicht im
	 *                Formular war
	 */
	public static Lesung lies(Art art, String type, boolean mitGrund, Function<String, String> eingabe) {
		Werte w = new Werte();
		if (art == null) {
			return new Lesung(w, null);
		}
		switch (art) {
			case CS_THRESHOLD -> {
				String roh = text(eingabe, "threshold");
				try {
					w.thresholdOrAttacks = Integer.parseInt(roh);
				} catch (final NumberFormatException e) {
					return new Lesung(null, "Ungültiger Threshold-Wert: " + roh);
				}
			}
			case CW_ATTACKS -> {
				String roh = text(eingabe, "required_attacks");
				if (roh == null || roh.isEmpty()) {
					return new Lesung(null, "Benötigte Angriffe müssen angegeben werden (1 oder 2)");
				}
				try {
					int angriffe = Integer.parseInt(roh);
					if (angriffe < 1 || angriffe > 2) {
						throw new NumberFormatException();
					}
					w.thresholdOrAttacks = angriffe;
				} catch (final NumberFormatException e) {
					return new Lesung(null, "Ungültiger Wert für Angriffe: " + roh + " (Erlaubt: 1 oder 2)");
				}
				Long perfekt = jaNein(eingabe, "ignore_perfect_war");
				if (perfekt != null) {
					w.namedSettings.put(ListeningEvent.SETTING_IGNORE_PERFECT_WAR, perfekt);
				}
			}
			case SEASONWINS -> {
				// Leer heißt: die clanweite Einstellung min_season_wins gilt
				String roh = text(eingabe, "wins_threshold");
				if (roh != null && !roh.isEmpty()) {
					try {
						long wins = Long.parseLong(roh);
						if (wins < 1) {
							throw new NumberFormatException();
						}
						w.namedSettings.put(ListeningEvent.SETTING_WINS_THRESHOLD, wins);
					} catch (final NumberFormatException e) {
						return new Lesung(null, "Ungültiger Wert für Minimum Wins: " + roh);
					}
				}
			}
			case CWCOUNT -> {
				try {
					long min = Long.parseLong(text(eingabe, "cw_min_count"));
					if (min < 1) {
						throw new NumberFormatException();
					}
					w.namedSettings.put(ListeningEvent.SETTING_CW_MIN_COUNT, min);
				} catch (final NumberFormatException e) {
					return new Lesung(null, "Ungültiger Wert für Minimum CWs.");
				}
			}
			case CWLDAYKP -> {
				Long perfekt = jaNein(eingabe, "ignore_perfect_war");
				if (perfekt != null) {
					w.namedSettings.put(ListeningEvent.SETTING_IGNORE_PERFECT_WAR, perfekt);
				}
			}
			case RAIDFAILS -> {
				int peak, rest, beide;
				try {
					peak = Integer.parseInt(text(eingabe, "capital_peak_max"));
					rest = Integer.parseInt(text(eingabe, "other_districts_max"));
					String beideRoh = text(eingabe, "penalize_both");
					if (mitGrund && beideRoh != null) {
						beide = Integer.parseInt(beideRoh);
						if (beide != 1 && beide != 2) {
							throw new NumberFormatException();
						}
					} else {
						// Info-Modus: 1 wie immer, wird ohne Kickpunkte nicht benutzt
						beide = 1;
					}
					if (peak < 1 || rest < 1) {
						throw new NumberFormatException();
					}
				} catch (final NumberFormatException e) {
					return new Lesung(null,
							"Ungültige Werte eingegeben. Capital Peak und Distrikte müssen >= 1 sein, 'beide bestrafen' muss 1 oder 2 sein.");
				}
				w.raidDistrictThresholds = new HashMap<>();
				w.raidDistrictThresholds.put("capital_peak_max", peak);
				w.raidDistrictThresholds.put("other_districts_max", rest);
				w.raidDistrictThresholds.put("penalize_both", beide);
				Long erzwingen = jaNein(eingabe, "raid_force_kickpoints");
				if (erzwingen != null) {
					w.namedSettings.put(ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS, erzwingen);
				}
			}
			case CUSTOMMESSAGE -> {
				// Nicht getrimmt: Zeilenumbrüche am Rand können gewollt sein
				String roh = eingabe.apply("custommessage");
				if (roh == null || roh.isBlank()) {
					return new Lesung(null, "Für eine benutzerdefinierte Nachricht wird ein Text gebraucht.");
				}
				w.customMessage = roh;
			}
			case CWDONATOR -> {
				try {
					int listen = Integer.parseInt(text(eingabe, "use_lists"));
					int leader = Integer.parseInt(text(eingabe, "exclude_leaders"));
					if ((listen != 0 && listen != 1) || (leader != 0 && leader != 1)) {
						throw new NumberFormatException();
					}
					w.useLists = listen;
					w.excludeLeaders = leader;
				} catch (final NumberFormatException e) {
					return new Lesung(null, "Ungültige Werte eingegeben. Beide Felder müssen entweder 0 oder 1 sein.");
				}
			}
			case STARFAILS -> {
				String modusRoh = text(eingabe, "punishment_mode");
				String freiRoh = text(eingabe, "starfails_free_hits");
				try {
					int sterne = Integer.parseInt(text(eingabe, "star_count"));
					if (sterne < 0 || sterne > 2) {
						throw new NumberFormatException();
					}
					// Fehlt in der CWL - dort wirkt der Modus nicht
					int modus = modusRoh != null ? Integer.parseInt(modusRoh) : 1;
					if (modus < 1 || modus > 3) {
						throw new NumberFormatException();
					}
					int frei = (freiRoh == null || freiRoh.isEmpty()) ? 0 : Integer.parseInt(freiRoh);
					if (frei < 0 || frei > 20) {
						throw new NumberFormatException();
					}
					w.starCount = sterne;
					w.punishmentMode = modus;
					w.namedSettings.put(ListeningEvent.SETTING_STARFAILS_FREE_HITS, (long) frei);
				} catch (final NumberFormatException e) {
					return new Lesung(null,
							"Ungültige Eingabe: Sterne-Anzahl muss 0-2 sein, Modus 1-3, freie Fehlversuche 0-20.");
				}
			}
		}
		return new Lesung(w, null);
	}

	/**
	 * Der umgekehrte Weg: welche Feldinhalte ein bestehendes Event hätte, wenn man
	 * es jetzt im Formular anlegen wollte. Nur Felder, zu denen das Event einen Wert
	 * hat - der Rest bekommt im Formular die übliche Vorgabe.
	 */
	public static Map<String, String> vorbelegung(ListeningEventService.Spec s) {
		Map<String, String> v = new LinkedHashMap<>();
		if (s == null) {
			return v;
		}
		if (s.thresholdOrAttacks != null) {
			v.put("threshold", String.valueOf(s.thresholdOrAttacks));
			v.put("required_attacks", String.valueOf(s.thresholdOrAttacks));
		}
		Map<String, Long> n = s.namedSettings != null ? s.namedSettings : Map.of();
		setze(v, "ignore_perfect_war", n.get(ListeningEvent.SETTING_IGNORE_PERFECT_WAR));
		setze(v, "wins_threshold", n.get(ListeningEvent.SETTING_WINS_THRESHOLD));
		setze(v, "cw_min_count", n.get(ListeningEvent.SETTING_CW_MIN_COUNT));
		setze(v, "raid_force_kickpoints", n.get(ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS));
		setze(v, "starfails_free_hits", n.get(ListeningEvent.SETTING_STARFAILS_FREE_HITS));
		if (s.raidDistrictThresholds != null) {
			setze(v, "capital_peak_max", s.raidDistrictThresholds.get("capital_peak_max"));
			setze(v, "other_districts_max", s.raidDistrictThresholds.get("other_districts_max"));
			setze(v, "penalize_both", s.raidDistrictThresholds.get("penalize_both"));
		}
		if (s.customMessage != null) {
			v.put("custommessage", s.customMessage);
		}
		setze(v, "use_lists", s.useLists);
		setze(v, "exclude_leaders", s.excludeLeaders);
		setze(v, "star_count", s.starCount);
		setze(v, "punishment_mode", s.punishmentMode);
		return v;
	}

	private static void setze(Map<String, String> v, String feld, Number wert) {
		if (wert != null) {
			v.put(feld, String.valueOf(wert));
		}
	}

	/** Der Wert, mit dem ein Feld im Formular erscheint: vorhandener Wert vor Vorgabe. */
	public static String startwert(Feld f, Map<String, String> vorbelegung) {
		String wert = vorbelegung != null ? vorbelegung.get(f.id()) : null;
		if (wert != null && !wert.isEmpty() && wert.length() <= f.maxLaenge()) {
			return wert;
		}
		return f.vorgabe();
	}
}
