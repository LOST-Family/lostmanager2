package lostmanager.legend;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Regeln von Perfect Day und Trophy Hunt, ohne Datenbank und ohne API –
 * damit sie sich prüfen lassen.
 *
 * Abgestimmt mit Stefan im Kanal trophy-hunt-planung (16.11.2026):
 * <ul>
 * <li><b>Perfect Day</b>: alle Accounts in Legend League I aus den LOST-Clans,
 * ohne Anmeldung. Je Legend-Tag (Wechsel 05:00 UTC) zählen nur die
 * Angriffspokale; Punkte = abgerundet(Angriffspokale / 10). Gewertet wird die
 * ganze Legend-Saison. Wer absteigt, behält seine Punkte.</li>
 * <li><b>Trophy Hunt</b>: Legend League II, einmal im Monat in der dritten
 * Monatswoche. Das ist die Liga-Woche (Montag 05:00 UTC bis Montag 05:00 UTC),
 * die am dritten Montag des Monats beginnt – im November 2026 der 16.–22.11.
 * Es zählen nur Angriffspokale.</li>
 * </ul>
 *
 * Die Supercell-API kennt keine Pokale je Kampf, nur den Pokalstand und (im
 * Kampfprotokoll) welche Kämpfe Angriffe und welche Verteidigungen waren. Der
 * Bot fragt deshalb alle zwei Minuten den Stand ab und ordnet jede Änderung
 * den neuen Kämpfen im Protokoll zu. Legend I: eine Verteidigung kostet Pokale.
 * Legend II (Ranked): jeder Kampf ist 40 Pokale wert, der Verteidiger bekommt,
 * was der Angreifer liegen lässt – Verteidigungen bringen also auch Pokale
 * (Stefan: „1100 aus der Off + 260 aus der Def“). Seit 17.11.2026 (Legend I)
 * bzw. der Hunt-Woche Dezember 2026 (Legend II) rechnet der Bot die Pokale je
 * Angriff aus Sternen und Prozent ({@link #legendEinsPokale}); geschätzt wird
 * nur noch, wenn Pokalstand und Protokoll nicht zusammenfinden.
 */
public final class LegendRegeln {

	public static final String LIGA_1 = "Legend League I";
	public static final String LIGA_2 = "Legend League II";

	/** Mehr als 40 Pokale gibt es für keinen Kampf, in keiner der beiden Ligen. */
	public static final int MAX_JE_KAMPF = 40;

	/** Ein „Perfect Day“: acht Dreisterne, 320 Pokale (Gleichstandsregel). */
	public static final int PERFECT_DAY_POKALE = 320;

	private LegendRegeln() {
	}

	/** Der Legend-Tag, zu dem ein Zeitpunkt gehört (Tageswechsel 05:00 UTC). */
	public static LocalDate legendTag(Instant t) {
		return t.minus(5, ChronoUnit.HOURS).atZone(ZoneOffset.UTC).toLocalDate();
	}

	public static Instant tagesbeginn(LocalDate tag) {
		return tag.atTime(5, 0).toInstant(ZoneOffset.UTC);
	}

	public static int punkte(int angriffspokale) {
		return Math.max(0, angriffspokale) / 10;
	}

	/** Beginn der Trophy-Hunt-Woche eines Monats: dritter Montag, 05:00 UTC. */
	public static Instant huntStart(YearMonth monat) {
		LocalDate dritterMontag = monat.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.MONDAY));
		return tagesbeginn(dritterMontag);
	}

	public static Instant huntEnde(YearMonth monat) {
		return huntStart(monat).plus(7, ChronoUnit.DAYS);
	}

	/** Der Monat, dessen Trophy Hunt gerade läuft – oder leer. */
	public static Optional<YearMonth> laufenderHunt(Instant t) {
		YearMonth m = YearMonth.from(t.atZone(ZoneOffset.UTC));
		for (YearMonth k : List.of(m, m.minusMonths(1))) {
			if (!t.isBefore(huntStart(k)) && t.isBefore(huntEnde(k))) {
				return Optional.of(k);
			}
		}
		return Optional.empty();
	}

	/**
	 * Ob die Rolle „Trophyhunt“ zu diesem Zeitpunkt abgeglichen wird: von einer
	 * Woche vor dem Hunt bis zu seinem Ende. Außerhalb bleibt sie, wie sie ist
	 * (Stefan, 22.11.2026: wer seine Übersicht erst nach dem Event schickt und
	 * dann nach Legend I aufsteigt, soll die Rolle nicht verlieren und weiter
	 * gepingt werden).
	 */
	public static boolean rollenFenster(Instant t) {
		YearMonth m = YearMonth.from(t.atZone(ZoneOffset.UTC));
		for (YearMonth k : List.of(m.minusMonths(1), m, m.plusMonths(1))) {
			if (!t.isBefore(huntStart(k).minus(7, ChronoUnit.DAYS)) && t.isBefore(huntEnde(k))) {
				return true;
			}
		}
		return false;
	}

	private static final Pattern DISCORD_ID = Pattern.compile("(?<!\\d)\\d{17,20}(?!\\d)");

	/**
	 * Discord-IDs, die die Rolle „Trophyhunt“ nie automatisch bekommen (Stefan,
	 * 22.11.2026). Aus TROPHYHUNT_ROLLE_AUSNAHMEN; gezählt werden nur die
	 * Discord-IDs darin, alles andere (Namen, Trennzeichen) ist Beiwerk –
	 * etwa {@code 516665333671788547 (Marc), 123…}.
	 */
	public static Set<String> rollenAusnahmen(String konfig) {
		Set<String> ids = new LinkedHashSet<>();
		if (konfig != null) {
			Matcher m = DISCORD_ID.matcher(konfig);
			while (m.find()) {
				ids.add(m.group());
			}
		}
		return ids;
	}

	/**
	 * Ob eine Legend-Saison ohne Belohnung läuft (Stefan: die Saison über
	 * Weihnachten/Silvester wird weiter gezählt, aber nicht belohnt).
	 *
	 * @param konfig Inhalt von PERFECTDAY_OHNE_BELOHNUNG: leer = Regel „die
	 *               Saison, in der der 24.12. liegt“; „keine“ = nie; sonst eine
	 *               Liste von Saisonstarts (JJJJ-MM-TT, durch Komma getrennt).
	 */
	public static boolean ohneBelohnung(LocalDate saisonStart, LocalDate saisonEnde, String konfig) {
		if (konfig == null || konfig.isBlank()) {
			for (int jahr = saisonStart.getYear(); jahr <= saisonEnde.getYear(); jahr++) {
				LocalDate heiligabend = LocalDate.of(jahr, 12, 24);
				if (!heiligabend.isBefore(saisonStart) && heiligabend.isBefore(saisonEnde)) {
					return true;
				}
			}
			return false;
		}
		if (konfig.trim().equalsIgnoreCase("keine")) {
			return false;
		}
		for (String teil : konfig.split(",")) {
			if (teil.trim().equals(saisonStart.toString())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Spieler, die in der Perfect-Day-Rangliste dieses einen Legend-Tages nicht
	 * erscheinen sollen (PERFECTDAY_AUSSCHLUSS, Stefan 16.11.2026: am Starttag nur
	 * teilweise erfasst, keine Korrektur gewünscht). Format
	 * {@code 2026-11-16=#TAG1,#TAG2;2026-11-20=#TAG3}. Gilt nur für den Post dieses
	 * Tages; die Tageswerte bleiben und zählen an den Folgetagen mit. Ein Block
	 * {@code *=#TAG4} schließt an jedem Tag aus (Stefan 18.11.2026: Spieler, die
	 * nicht zum Event gehören, dauerhaft weglassen).
	 */
	public static java.util.Set<String> ausgeschlossen(LocalDate tag, String konfig) {
		java.util.Set<String> tags = new java.util.HashSet<>();
		if (konfig == null || konfig.isBlank()) {
			return tags;
		}
		for (String block : konfig.split(";")) {
			String[] kv = block.split("=", 2);
			String wann = kv[0].trim();
			if (kv.length == 2 && (wann.equals(tag.toString()) || wann.equals("*"))) {
				for (String t : kv[1].split(",")) {
					String n = t.trim().toUpperCase();
					if (!n.isEmpty()) {
						tags.add(n.startsWith("#") ? n : "#" + n);
					}
				}
			}
		}
		return tags;
	}

	// ------------------------------------------------------------------
	// Zuordnung einer Pokaländerung zu Angriffen und Verteidigungen
	// ------------------------------------------------------------------

	/** Ein Kampf aus dem Kampfprotokoll; {@code ende} = Beginn + Dauer. */
	public record Kampf(boolean angriff, int sterne, int prozent, Instant ende) {
	}

	/** Schätzt die Pokale des Angreifers für ein Ergebnis. */
	public interface Schaetzer {
		int angreiferPokale(boolean ranked, int sterne, int prozent);
	}

	/** Grobe Schätzung ohne gelernte Werte: 3 Sterne = 40, sonst Sterne und Prozent. */
	public static final Schaetzer GROB = (ranked, sterne, prozent) -> sterne >= 3 ? MAX_JE_KAMPF
			: Math.min(MAX_JE_KAMPF - 1, sterne * 10 + prozent / 10);

	/**
	 * Pokale eines Legend-I-Angriffs aus Sternen und Prozent (für die Verteidigung
	 * dasselbe mit umgekehrtem Vorzeichen). Am 17.11.2026 recherchiert und an acht
	 * ClashPerk-Werten bestätigt (betreuung/legend-gegenprobe.py): 3 Sterne 40;
	 * 2 Sterne 16 + (p−50)/3, höchstens 32; 1 Stern 5 + (p−1)/9, höchstens 15;
	 * 0 Sterne p/10, höchstens 4.
	 */
	public static int legendEinsPokale(int sterne, int prozent) {
		if (sterne >= 3) {
			return MAX_JE_KAMPF;
		}
		if (sterne == 2) {
			return Math.max(0, Math.min(32, 16 + (prozent - 50) / 3));
		}
		if (sterne == 1) {
			return Math.max(0, Math.min(15, 5 + (prozent - 1) / 9));
		}
		return Math.max(0, Math.min(4, prozent / 10));
	}

	/** Legend I nach der Formel; Legend II nach {@code sonst} (nur noch für alte Aufrufer, siehe {@link #legendZwei}). */
	public static Schaetzer formel(Schaetzer sonst) {
		return (ranked, sterne, prozent) -> ranked ? sonst.angreiferPokale(true, sterne, prozent)
				: legendEinsPokale(sterne, prozent);
	}

	/**
	 * Ergebnis einer Zuordnung.
	 *
	 * @param angriffspokale Pokale aus den Angriffen des Fensters (≥ 0)
	 * @param angriffe       Zahl der Angriffe im Fenster
	 * @param unsicher       geschätzt statt gemessen
	 * @param lernSterne     wenn das Fenster genau einen Kampf enthält: dessen
	 *                       Sterne, sonst -1 (dann taugt es als Stichprobe)
	 */
	public record Zuordnung(int angriffspokale, int angriffe, boolean unsicher, int lernSterne, int lernProzent,
			int lernPokale) {
	}

	/**
	 * Ordnet eine Pokaländerung den neuen Kämpfen zu.
	 *
	 * @param delta  Pokalstand jetzt minus Pokalstand vorher
	 * @param neue   die Kämpfe, die seitdem dazugekommen sind (nicht leer)
	 * @param ranked true für Legend II (Verteidigung bringt Pokale), false für
	 *               Legend I (Verteidigung kostet Pokale)
	 */
	public static Zuordnung zuordnen(int delta, List<Kampf> neue, boolean ranked, Schaetzer schaetzer) {
		List<Kampf> angriffe = new ArrayList<>();
		List<Kampf> verteidigungen = new ArrayList<>();
		for (Kampf k : neue) {
			(k.angriff() ? angriffe : verteidigungen).add(k);
		}
		int obergrenze = MAX_JE_KAMPF * angriffe.size();

		if (angriffe.isEmpty()) {
			if (verteidigungen.size() == 1) {
				Kampf v = verteidigungen.get(0);
				int angreifer = ranked ? MAX_JE_KAMPF - delta : -delta;
				if (angreifer >= 0 && angreifer <= MAX_JE_KAMPF) {
					return new Zuordnung(0, 0, false, v.sterne(), v.prozent(), angreifer);
				}
			}
			return new Zuordnung(0, 0, false, -1, 0, 0);
		}

		if (verteidigungen.isEmpty()) {
			int wert = Math.max(0, Math.min(obergrenze, delta));
			boolean unsicher = delta < 0 || delta > obergrenze;
			if (angriffe.size() == 1 && !unsicher) {
				Kampf a = angriffe.get(0);
				return new Zuordnung(wert, 1, false, a.sterne(), a.prozent(), wert);
			}
			return new Zuordnung(wert, angriffe.size(), unsicher, -1, 0, 0);
		}

		// Gemischtes Fenster: die Verteidigungen schätzen und herausrechnen.
		int verteidigungsAnteil = 0;
		for (Kampf v : verteidigungen) {
			int angreifer = Math.max(0, Math.min(MAX_JE_KAMPF, schaetzer.angreiferPokale(ranked, v.sterne(), v.prozent())));
			verteidigungsAnteil += ranked ? (MAX_JE_KAMPF - angreifer) : -angreifer;
		}
		int wert = Math.max(0, Math.min(obergrenze, delta - verteidigungsAnteil));
		return new Zuordnung(wert, angriffe.size(), true, -1, 0, 0);
	}

	/** Ein Kampf mit seinem Schlüssel im Protokoll (Zeitstempel, Seite, Dauer, Gegner). */
	public record Eintrag(String schluessel, Kampf kampf) {
	}

	/**
	 * Stand eines Legend-I-Spielers zwischen zwei Abfragen.
	 *
	 * @param pokale       zuletzt abgefragter Pokalstand
	 * @param gesehen      Schlüssel aller Kämpfe, die schon verbucht sind oder im Topf liegen
	 * @param blindBis     Kämpfe, die bis dahin geendet haben, gelten als verbucht
	 *                     (Übergang/erste Sichtung/Notbuchung; sonst null)
	 * @param topfPokale   Pokaländerung, die noch keinem Kampf zugeordnet ist
	 * @param topf         Schlüssel der Kämpfe, die noch keiner Änderung zugeordnet sind
	 * @param letzteRegung wann sich zuletzt Pokalstand oder Protokoll bewegt hat
	 * @param offenSeit    seit wann der Topf nicht leer ist
	 */
	public record EinsStand(int pokale, java.util.Set<String> gesehen, Instant blindBis, int topfPokale,
			java.util.Set<String> topf, Instant letzteRegung, Instant offenSeit) {
	}

	/** Ergebnis einer Abfrage: neuer Stand, zu buchende Pokale je Tag, Zuordnung (für Zahl und Stichprobe). */
	public record EinsErgebnis(EinsStand stand, Map<LocalDate, Integer> tage, Zuordnung zuordnung) {
	}

	/**
	 * Steht der Pokalstand so lange still und passt trotzdem nicht zu den
	 * gebuchten Kämpfen, wird die Abweichung aufgegeben (nur Journal und Stern,
	 * nichts gebucht).
	 */
	public static final java.time.Duration HOECHSTENS_OFFEN = java.time.Duration.ofMinutes(10);
	/** Verbuchte Schlüssel werden nach so langer Zeit vergessen (nicht nach dem Protokoll). */
	public static final java.time.Duration MERKEN = java.time.Duration.ofDays(3);

	/**
	 * Legend I, seit 23.11.2026: jeder neue Kampf im Protokoll (erkannt am
	 * Schlüssel) zählt einzeln nach der Formel {@link #legendEinsPokale}, auf den
	 * Legend-Tag seines Beginns (wie ClashPerk, {@code tagesSumme} und
	 * legend-gegenprobe.py). Die Formel trifft auch Verteidigungen (844 von 845
	 * Einzelkampf-Fenstern in legend_stichprobe, gemessen 23.11.2026).
	 *
	 * Der Pokalstand löst nur noch den Blick ins Protokoll aus und dient als
	 * Gegenprobe: {@code topfPokale} ist der Teil der Pokaländerung, den noch kein
	 * Kampf erklärt (positiv: Protokoll hinkt hinterher, negativ: Pokalstand
	 * hinkt hinterher). Bleibt er {@link #HOECHSTENS_OFFEN} ohne jede Bewegung
	 * stehen, wird er verworfen und nicht gebucht; {@code zuordnung().unsicher()}
	 * meldet das (Journal, Spalte unsicher), {@code stand().topfPokale()} ist dann 0
	 * und der verworfene Rest steht in {@code zuordnung().lernPokale()}.
	 *
	 * Bis 22.11.2026 verteilte der Bot stattdessen die Pokaländerung auf die
	 * Kämpfe im „Topf“ und buchte nach zehn Minuten ohne Passung höchstens 40 je
	 * Angriff im Topf. Bei einer Serie ohne Pause, in der Pokalstand und Protokoll
	 * auseinanderliefen, gingen so Pokale verloren (Stefan 22.11.: 200 statt 295).
	 *
	 * Verbucht bleibt verbucht: Schlüssel werden nach {@link #MERKEN} vergessen,
	 * nicht weil sie in einer Antwort fehlen (die API lässt ältere Kämpfe
	 * gelegentlich für eine Antwort weg und liefert sie dann wieder). Schlüssel im
	 * {@code topf} (Rest des alten Verfahrens beim Umstieg) gelten als noch nicht
	 * gebucht.
	 *
	 * @param protokoll liefert die Legend-Kämpfe des Protokolls, null = nicht abrufbar
	 * @param schaetzer wird nicht mehr gebraucht (Formel), bleibt für die Aufrufer
	 * @return null = Protokoll nicht abrufbar, Stand unverändert lassen
	 */
	public static EinsErgebnis legendEins(EinsStand vorher, int pokale, java.util.function.Supplier<List<Eintrag>> protokoll,
			Instant jetzt, Schaetzer schaetzer) {
		int delta = pokale - vorher.pokale();
		int rest = vorher.topfPokale() + delta;
		if (delta == 0 && rest == 0 && vorher.topf().isEmpty() && vorher.offenSeit() == null) {
			return new EinsErgebnis(new EinsStand(pokale, vorher.gesehen(), vorher.blindBis(), 0, vorher.topf(),
					vorher.letzteRegung(), null), Map.of(), null);
		}
		List<Eintrag> log = protokoll.get();
		if (log == null) {
			return null;
		}
		java.util.Set<String> gesehen = new java.util.HashSet<>(vorher.gesehen());
		Map<LocalDate, Integer> tage = new java.util.TreeMap<>();
		int angriffe = 0, erklaert = 0;
		boolean neu = false;
		for (Eintrag e : log) {
			boolean ausTopf = vorher.topf().contains(e.schluessel());
			if (!gesehen.add(e.schluessel()) && !ausTopf) {
				continue;
			}
			if (!ausTopf && vorher.blindBis() != null && !e.kampf().ende().isAfter(vorher.blindBis())) {
				continue;
			}
			neu = true;
			Kampf k = e.kampf();
			int x = legendEinsPokale(k.sterne(), k.prozent());
			if (k.angriff()) {
				tage.merge(legendTag(beginn(e)), x, Integer::sum);
				angriffe++;
				erklaert += x;
			} else {
				erklaert -= x;
			}
		}
		alteSchluesselWeg(gesehen, jetzt.minus(MERKEN));
		rest -= erklaert;
		Instant regung = (delta != 0 || neu || vorher.letzteRegung() == null) ? jetzt : vorher.letzteRegung();
		boolean unsicher = false;
		int verworfen = 0;
		Instant offen = null;
		if (rest != 0) {
			offen = (vorher.offenSeit() == null || delta != 0 || neu) ? jetzt : vorher.offenSeit();
			if (java.time.Duration.between(offen, jetzt).compareTo(HOECHSTENS_OFFEN) >= 0) {
				// Pokalstand und Kämpfe finden nicht zusammen: verwerfen, nur melden.
				unsicher = true;
				verworfen = rest;
				rest = 0;
				offen = null;
			}
		}
		Zuordnung z = new Zuordnung(Math.max(0, tage.values().stream().mapToInt(Integer::intValue).sum()), angriffe,
				unsicher, -1, 0, verworfen);
		return new EinsErgebnis(new EinsStand(pokale, gesehen, vorher.blindBis(), rest, new java.util.HashSet<>(),
				regung, offen), tage, (tage.isEmpty() && !unsicher) ? null : z);
	}

	/** Beginn eines Kampfes aus seinem Schlüssel (battleTimestamp vorn), sonst dessen Ende. */
	static Instant beginn(Eintrag e) {
		String s = e.schluessel();
		if (s.length() >= 15 && s.charAt(8) == 'T') {
			try {
				return java.time.LocalDateTime.parse(s.substring(0, 15),
						java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")).toInstant(ZoneOffset.UTC);
			} catch (Exception ex) {
				// anders gebauter Schlüssel (Tests): Ende nehmen
			}
		}
		return e.kampf().ende();
	}

	private static final java.time.format.DateTimeFormatter SCHLUESSELZEIT = java.time.format.DateTimeFormatter
			.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

	/**
	 * Entfernt Schlüssel von Kämpfen, die vor {@code vor} begonnen haben. Ein
	 * Schlüssel beginnt mit dem battleTimestamp der API (yyyyMMdd'T'HHmmss…), der
	 * sich als Text sortiert; anders gebaute Schlüssel bleiben.
	 */
	static void alteSchluesselWeg(java.util.Set<String> schluessel, Instant vor) {
		String grenze = SCHLUESSELZEIT.format(vor);
		schluessel.removeIf(k -> k.length() >= 15 && Character.isDigit(k.charAt(0)) && k.charAt(8) == 'T'
				&& k.compareTo(grenze) < 0);
	}

	// ------------------------------------------------------------------
	// Legend II (Trophy Hunt): jeder Kampf nach der Formel
	// ------------------------------------------------------------------

	/** Ein Ranked-Kampf aus dem Protokoll; {@code beginn} = battleTimestamp. */
	public record Zweikampf(String schluessel, boolean angriff, int sterne, int prozent, Instant beginn) {
	}

	/**
	 * Stand eines Legend-II-Spielers zwischen zwei Abfragen.
	 *
	 * @param pokale    zuletzt abgefragter Pokalstand
	 * @param rest      Teil davon, den noch kein Kampf im Protokoll erklärt (kann
	 *                  negativ sein: das Protokoll ist dem Pokalstand voraus)
	 * @param gesehen   Schlüssel aller schon verbuchten Kämpfe; {@code null} = noch
	 *                  nie nach diesem Verfahren erfasst (dann gilt alles im
	 *                  Protokoll als verbucht)
	 * @param offenSeit Pokalstand und Protokoll passen nicht zusammen – seit der letzten Bewegung
	 * @param ohneAnschluss während der Rest offen ist: das Protokoll schloss nicht an
	 *                  das schon Verbuchte an (es können Angriffe herausgefallen sein)
	 */
	public record ZweiStand(int pokale, int rest, java.util.Set<String> gesehen, Instant offenSeit,
			boolean ohneAnschluss) {
		public ZweiStand(int pokale, int rest, java.util.Set<String> gesehen, Instant offenSeit) {
			this(pokale, rest, gesehen, offenSeit, false);
		}
	}

	/**
	 * @param tage           Angriffspokale je Legend-Tag aus den neuen Angriffen
	 * @param anzahl         Zahl der neuen Angriffe je Legend-Tag
	 * @param unerklaert     aufgegebener Rest (Pokaländerung ohne passenden Kampf), sonst 0
	 * @param unvollstaendig es fehlen wohl Angriffe: das Protokoll schloss nicht an das
	 *                       Verbuchte an, und der Pokalstand blieb über dem, was die
	 *                       Kämpfe erklären (steht erst fest, wenn der Rest aufgegeben wird)
	 */
	public record ZweiErgebnis(ZweiStand stand, Map<LocalDate, Integer> tage, Map<LocalDate, Integer> anzahl,
			int unerklaert, boolean unvollstaendig) {
	}

	/** Bewegt sich so lange nichts und Pokalstand und Protokoll passen nicht zusammen, gilt der Pokalstand. */
	public static final java.time.Duration ZWEI_LUECKE = java.time.Duration.ofMinutes(6);

	/** Was der Verteidiger in Legend II bekommt: was der Angreifer von 40 liegen lässt. */
	public static int legendZweiVerteidigung(int sterne, int prozent) {
		return MAX_JE_KAMPF - legendEinsPokale(sterne, prozent);
	}

	/**
	 * Legend II, ab der Hunt-Woche Dezember 2026 (Stefan 17.11.2026). Die Formel
	 * von Legend I gilt auch hier (273 von 274 Einzelkampf-Fenstern der Woche
	 * 16.–22.11. exakt; Angreifer X, Verteidiger 40 − X). Deshalb wird nicht mehr
	 * der Pokalstand auf Kämpfe verteilt, sondern jeder neue Angriff im Protokoll
	 * (erkannt am Schlüssel) einzeln nach der Formel gebucht, auf den Legend-Tag
	 * seines Beginns. Der Pokalstand dient nur noch zwei Dingen: er löst den
	 * Blick ins Protokoll aus, und er zeigt, ob etwas fehlt.
	 *
	 * Das Protokoll hält nur etwa die letzten 16 Angriffe (gemessen 21.11.2026:
	 * 51 Spieler, nie mehr als 16 Ranked-Angriffe, auch bei unter 50 Einträgen).
	 * Wer in einer Lücke (Bot aus, API weg) mehr angreift, verliert Angriffe –
	 * das zeigt sich daran, dass kein schon bekannter Angriff mehr im Protokoll
	 * steht und der Pokalstand dauerhaft über dem Erklärten liegt: dann
	 * unvollständig. Entschieden wird das erst, wenn sich {@link #ZWEI_LUECKE} lang
	 * nichts mehr bewegt hat – vorher kann das Protokoll bloß hinterherhinken.
	 *
	 * Erste Sichtung: {@code vorher} = Stand 0 mit leerer Menge – die Woche
	 * beginnt bei 0 Pokalen, alles im Protokoll seit Wochenbeginn wird gebucht.
	 *
	 * @param protokoll liefert die Ranked-Kämpfe des Protokolls, null = nicht abrufbar
	 * @return null = Protokoll nicht abrufbar, Stand unverändert lassen
	 */
	public static ZweiErgebnis legendZwei(ZweiStand vorher, int pokale,
			java.util.function.Supplier<List<Zweikampf>> protokoll, Instant wochenBeginn, Instant jetzt) {
		int bewegung = pokale - vorher.pokale();
		int delta = bewegung + vorher.rest();
		if (delta == 0 && vorher.offenSeit() == null && vorher.gesehen() != null) {
			return new ZweiErgebnis(vorher, Map.of(), Map.of(), 0, false);
		}
		List<Zweikampf> log = protokoll.get();
		if (log == null) {
			return null;
		}
		Instant wochenEnde = wochenBeginn.plus(7, ChronoUnit.DAYS);
		// Verbucht bleibt verbucht: die Menge wächst über die Woche und wird nicht auf das
		// gekürzt, was gerade im Protokoll steht – die API lässt einzelne ältere Kämpfe
		// gelegentlich für eine Antwort weg und liefert sie dann wieder (21.11.2026, im
		// Trockenlauf gesehen: ein Angriff wäre doppelt gebucht worden). Gemerkt werden
		// nur Kämpfe der Woche, andere zählen ohnehin nie.
		java.util.Set<String> gesehen = vorher.gesehen() == null ? new java.util.HashSet<>()
				: new java.util.HashSet<>(vorher.gesehen());
		if (vorher.gesehen() == null) {
			// Übergang vom alten Verfahren: was im Protokoll steht, steckt schon im Stand.
			for (Zweikampf k : log) {
				if (!k.beginn().isBefore(wochenBeginn) && k.beginn().isBefore(wochenEnde)) {
					gesehen.add(k.schluessel());
				}
			}
			return new ZweiErgebnis(new ZweiStand(pokale, 0, gesehen, null), Map.of(), Map.of(), 0, false);
		}
		Map<LocalDate, Integer> tage = new java.util.TreeMap<>();
		Map<LocalDate, Integer> anzahl = new java.util.TreeMap<>();
		int erklaert = 0;
		boolean neu = false;
		// Reicht das Protokoll lückenlos an das schon Verbuchte heran? Ja, wenn noch ein
		// bekannter Angriff darin steht oder einer von vor Wochenbeginn (oder gar keiner).
		boolean angriffImLog = false, anschluss = false;
		for (Zweikampf k : log) {
			boolean inWoche = !k.beginn().isBefore(wochenBeginn) && k.beginn().isBefore(wochenEnde);
			boolean bekannt = vorher.gesehen().contains(k.schluessel());
			if (k.angriff()) {
				angriffImLog = true;
				anschluss |= bekannt || k.beginn().isBefore(wochenBeginn);
			}
			if (bekannt || !inWoche || !gesehen.add(k.schluessel())) {
				continue;
			}
			neu = true;
			int x = legendEinsPokale(k.sterne(), k.prozent());
			if (k.angriff()) {
				tage.merge(legendTag(k.beginn()), x, Integer::sum);
				anzahl.merge(legendTag(k.beginn()), 1, Integer::sum);
				erklaert += x;
			} else {
				erklaert += MAX_JE_KAMPF - x;
			}
		}
		int rest = delta - erklaert;
		boolean ohne = vorher.ohneAnschluss() || (angriffImLog && !anschluss);
		if (rest == 0) {
			return new ZweiErgebnis(new ZweiStand(pokale, 0, gesehen, null), tage, anzahl, 0, false);
		}
		// Die Frist läuft ab der letzten Bewegung: in einer langen Angriffsserie ist der
		// Pokalstand dem Protokoll dauernd ein Stück voraus, ohne dass etwas fehlt.
		Instant offen = (vorher.offenSeit() != null && bewegung == 0 && !neu) ? vorher.offenSeit() : jetzt;
		if (java.time.Duration.between(offen, jetzt).compareTo(ZWEI_LUECKE) >= 0) {
			return new ZweiErgebnis(new ZweiStand(pokale, 0, gesehen, null), tage, anzahl, rest, rest > 0 && ohne);
		}
		return new ZweiErgebnis(new ZweiStand(pokale, rest, gesehen, offen, ohne), tage, anzahl, 0, false);
	}

	/**
	 * Wochenende: Angriffe der Woche, die im Protokoll stehen, aber noch nicht
	 * verbucht sind (zwischen letzter Abfrage und Wochenwechsel).
	 */
	public static ZweiErgebnis legendZweiAbschluss(ZweiStand vorher, List<Zweikampf> log, Instant wochenBeginn) {
		if (vorher.gesehen() == null) {
			return new ZweiErgebnis(vorher, Map.of(), Map.of(), 0, false);
		}
		// Hier zählen nur die Schlüssel: den Pokalstand so ansetzen, dass nichts offen bleibt
		// (der Endstand der Woche ist nach dem Wechsel nicht mehr abrufbar).
		int summe = 0;
		Instant wochenEnde = wochenBeginn.plus(7, ChronoUnit.DAYS);
		for (Zweikampf k : log) {
			if (vorher.gesehen().contains(k.schluessel()) || k.beginn().isBefore(wochenBeginn)
					|| !k.beginn().isBefore(wochenEnde)) {
				continue;
			}
			int x = legendEinsPokale(k.sterne(), k.prozent());
			summe += k.angriff() ? x : MAX_JE_KAMPF - x;
		}
		return legendZwei(new ZweiStand(vorher.pokale(), 0, vorher.gesehen(), wochenEnde), vorher.pokale() + summe,
				() -> log, wochenBeginn, wochenEnde);
	}

	/**
	 * Verteilt die Angriffspokale eines Fensters auf Legend-Tage (falls die
	 * Angriffe über einen Tageswechsel reichen), gewichtet mit der Schätzung.
	 */
	public static Map<LocalDate, Integer> aufTageVerteilen(int angriffspokale, List<Kampf> neue, boolean ranked,
			Schaetzer schaetzer) {
		Map<LocalDate, Integer> gewichte = new LinkedHashMap<>();
		int summe = 0;
		for (Kampf k : neue) {
			if (!k.angriff()) {
				continue;
			}
			int g = Math.max(1, schaetzer.angreiferPokale(ranked, k.sterne(), k.prozent()));
			gewichte.merge(legendTag(k.ende()), g, Integer::sum);
			summe += g;
		}
		Map<LocalDate, Integer> ergebnis = new LinkedHashMap<>();
		if (gewichte.isEmpty()) {
			return ergebnis;
		}
		int verteilt = 0;
		LocalDate letzter = null;
		for (Map.Entry<LocalDate, Integer> e : gewichte.entrySet()) {
			int anteil = (int) Math.floor((double) angriffspokale * e.getValue() / summe);
			ergebnis.put(e.getKey(), anteil);
			verteilt += anteil;
			letzter = e.getKey();
		}
		ergebnis.merge(letzter, angriffspokale - verteilt, Integer::sum);
		return ergebnis;
	}
}
