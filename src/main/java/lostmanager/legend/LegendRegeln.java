package lostmanager.legend;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * (Stefan: „1100 aus der Off + 260 aus der Def“). Fallen ein Angriff und eine
 * Verteidigung in dasselbe Fenster, wird geschätzt; das ist die Näherung, die
 * die Posts ausweisen.
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
	 * Tages; die Tageswerte bleiben und zählen an den Folgetagen mit.
	 */
	public static java.util.Set<String> ausgeschlossen(LocalDate tag, String konfig) {
		java.util.Set<String> tags = new java.util.HashSet<>();
		if (konfig == null || konfig.isBlank()) {
			return tags;
		}
		for (String block : konfig.split(";")) {
			String[] kv = block.split("=", 2);
			if (kv.length == 2 && kv[0].trim().equals(tag.toString())) {
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
