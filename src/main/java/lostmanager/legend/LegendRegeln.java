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

	/** Legend I nach der Formel, Legend II (dort unbekannt) nach {@code sonst}. */
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
	 * So lange müssen Pokalstand und Protokoll still stehen, bevor der Topf
	 * aufgelöst wird. Die API hält den Spieler bis 30 s, das Protokoll bis 60 s
	 * im Zwischenspeicher (Cache-Control, gemessen 17.11.2026) – eins kann dem
	 * anderen also bis eine Minute voraus sein.
	 */
	public static final java.time.Duration RUHE = java.time.Duration.ofSeconds(90);
	/** Passt der Topf so lange nicht zusammen, wird nach dem Vorzeichen gebucht. */
	public static final java.time.Duration HOECHSTENS_OFFEN = java.time.Duration.ofMinutes(10);

	/**
	 * Legend I, seit 17.11.2026 (Pixel: „mach das gleiche per api“ wie ClashPerk).
	 * Der Takt fragt alle 30 Sekunden. Pokaländerungen und neue Kämpfe (erkannt am
	 * Schlüssel im Protokoll, nicht an Zeitgrenzen) wandern in einen Topf; steht
	 * beides {@link #RUHE} lang still und passt zusammen (Plus nur mit neuem
	 * Angriff, höchstens 40 je Angriff und Verteidigung), ordnet {@link #zuordnen}
	 * den Topf zu. Weil zwischen zwei Angriffen eines Spielers Minuten liegen,
	 * enthält der Topf fast immer einen Kampf, sonst einen Angriff und eine
	 * Verteidigung (Dreistern-Verteidigung = genau 40, sonst gelernt geschätzt).
	 * Vorher filterte der Bot nach Zeitgrenzen und schätzte ganze Serien; am 17.11.
	 * ergab das bei Vendetta 120 statt 276.
	 *
	 * @param protokoll liefert die Legend-Kämpfe des Protokolls, null = nicht abrufbar
	 * @return null = Protokoll nicht abrufbar, Stand unverändert lassen
	 */
	public static EinsErgebnis legendEins(EinsStand vorher, int pokale, java.util.function.Supplier<List<Eintrag>> protokoll,
			Instant jetzt, Schaetzer schaetzer) {
		int delta = pokale - vorher.pokale();
		int topfPokale = vorher.topfPokale() + delta;
		if (delta == 0 && topfPokale == 0 && vorher.topf().isEmpty()) {
			return new EinsErgebnis(new EinsStand(pokale, vorher.gesehen(), vorher.blindBis(), 0, vorher.topf(),
					vorher.letzteRegung(), null), Map.of(), null);
		}
		List<Eintrag> log = protokoll.get();
		if (log == null) {
			return null;
		}
		java.util.Set<String> gesehen = new java.util.HashSet<>(vorher.gesehen());
		java.util.Set<String> topf = new java.util.HashSet<>(vorher.topf());
		java.util.Set<String> imLog = new java.util.HashSet<>();
		boolean neu = false;
		for (Eintrag e : log) {
			imLog.add(e.schluessel());
			if (gesehen.contains(e.schluessel())) {
				continue;
			}
			gesehen.add(e.schluessel());
			if (vorher.blindBis() != null && !e.kampf().ende().isAfter(vorher.blindBis())) {
				continue;
			}
			topf.add(e.schluessel());
			neu = true;
		}
		gesehen.retainAll(imLog);
		topf.retainAll(imLog);
		Instant regung = (delta != 0 || neu || vorher.letzteRegung() == null) ? jetzt : vorher.letzteRegung();
		Instant offen = vorher.offenSeit() != null ? vorher.offenSeit() : jetzt;

		List<Kampf> kaempfe = new ArrayList<>();
		for (Eintrag e : log) {
			if (topf.contains(e.schluessel())) {
				kaempfe.add(e.kampf());
			}
		}
		kaempfe.sort((x, y) -> x.ende().compareTo(y.ende()));
		long angriffe = kaempfe.stream().filter(Kampf::angriff).count();
		long verteidigungen = kaempfe.size() - angriffe;
		boolean passt = !kaempfe.isEmpty() && topfPokale <= MAX_JE_KAMPF * angriffe
				&& topfPokale >= -MAX_JE_KAMPF * verteidigungen;
		boolean ruhig = java.time.Duration.between(regung, jetzt).compareTo(RUHE) >= 0;

		boolean zuLange = java.time.Duration.between(offen, jetzt).compareTo(HOECHSTENS_OFFEN) >= 0;
		// Lange Angriffsserie ohne Pause: passt der Topf, trotzdem zuordnen.
		if (passt && (ruhig || zuLange)) {
			Zuordnung z = zuordnen(topfPokale, kaempfe, false, schaetzer);
			Map<LocalDate, Integer> tage = z.angriffe() > 0
					? aufTageVerteilen(z.angriffspokale(), kaempfe, false, schaetzer)
					: Map.of();
			return new EinsErgebnis(new EinsStand(pokale, gesehen, vorher.blindBis(), 0, new java.util.HashSet<>(),
					regung, null), tage, z);
		}
		if (kaempfe.isEmpty() && topfPokale == 0) {
			return new EinsErgebnis(new EinsStand(pokale, gesehen, vorher.blindBis(), 0, topf, regung, null), Map.of(),
					null);
		}
		if (zuLange) {
			// Passt nicht zusammen (Protokoll unvollständig?): nach dem Vorzeichen. Was bis
			// jetzt geendet hat, gilt als verbucht.
			int wert = Math.max(0, Math.min(topfPokale, MAX_JE_KAMPF * (int) Math.max(1, angriffe)));
			Map<LocalDate, Integer> tage = wert > 0 ? Map.of(legendTag(jetzt), wert) : Map.of();
			return new EinsErgebnis(new EinsStand(pokale, gesehen, jetzt, 0, new java.util.HashSet<>(), regung, null),
					tage, new Zuordnung(wert, wert > 0 ? (int) Math.max(1, angriffe) : 0, true, -1, 0, 0));
		}
		return new EinsErgebnis(new EinsStand(pokale, gesehen, vorher.blindBis(), topfPokale, topf, regung, offen),
				Map.of(), null);
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
