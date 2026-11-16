package lostmanager.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lostmanager.util.ListeningEventFormular.Art;
import lostmanager.util.ListeningEventFormular.Feld;
import lostmanager.util.ListeningEventService.Spec;

/**
 * Führt ein bestehendes Event mit den Änderungen aus {@code /listeningevent edit}
 * zusammen: was angegeben ist, ändert sich, der Rest bleibt.
 *
 * Der knifflige Teil sind die Zusatzwerte aus dem Formular. Sie gehören zu einer
 * Kombination aus Typ und Aktion - "2 benötigte Angriffe" ergibt bei einem
 * Clan-Games-Event als "2 Punkte" keinen Sinn, und ein liegengebliebener
 * Schwellenwert würde beim Speichern als Positionswert mitgeschrieben und von
 * der neuen Aktion falsch gelesen. Deshalb:
 *
 * <ul>
 * <li>Fragt die neue Kombination genau dieselben Felder ab wie die alte, bleiben
 * die Werte unverändert. Ein Formular gibt es nur auf Wunsch.</li>
 * <li>Fragt sie eine Teilmenge ab (etwa Kickpoint → Info), werden die
 * passenden Werte übernommen und der Rest fällt weg.</li>
 * <li>Kommt ein Feld dazu oder wechselt die Art des Formulars, wird gefragt -
 * vorausgefüllt mit allem, was sich sinnvoll übernehmen lässt.</li>
 * <li>Braucht die neue Kombination keine Zusatzwerte, fallen alle weg.</li>
 * </ul>
 *
 * Ohne JDA und ohne Datenbank, damit es sich prüfen lässt.
 */
public final class ListeningEventBearbeitung {

	private ListeningEventBearbeitung() {
	}

	/** Was der Nutzer angegeben hat; null heißt "bleibt". */
	public static final class Aenderung {
		public String clanTag;
		public String type;
		public Long duration;
		public String actionType;
		public String channelId;
		public String kickpointReasonName;
		/** Zusatzwerte ausdrücklich im Formular bearbeiten. */
		public boolean werteBearbeiten;

		public boolean leer() {
			return clanTag == null && type == null && duration == null && actionType == null && channelId == null
					&& kickpointReasonName == null;
		}
	}

	/**
	 * @param neu         der zusammengeführte Stand; ohne Formular schon vollständig
	 * @param art         das Formular der neuen Kombination, null wenn keins
	 * @param felder      seine Felder
	 * @param vorbelegung was im Formular schon drinsteht
	 * @param formular    ob vor dem Speichern noch gefragt werden muss
	 * @param werteUebernommen ob die Zusatzwerte unverändert aus dem alten Stand stammen
	 * @param fehler      null, wenn es weitergehen kann
	 */
	public record Plan(Spec alt, Spec neu, Art art, List<Feld> felder, Map<String, String> vorbelegung,
			boolean formular, boolean werteUebernommen, String fehler) {

		static Plan fehler(Spec alt, String meldung) {
			return new Plan(alt, null, null, List.of(), Map.of(), false, false, meldung);
		}
	}

	public static Plan planen(Spec alt, Aenderung a) {
		return planen(alt, a, s -> {
			ListeningEventService.Result r = ListeningEventService.validateKern(s);
			return r == null ? null : r.error();
		});
	}

	/** Mit der echten Regelprüfung, nur die Frage "gibt es den Grund" ausgetauscht. */
	public static Plan planen(Spec alt, Aenderung a, java.util.function.BiPredicate<String, String> grundExistiert) {
		return planen(alt, a, (java.util.function.Function<Spec, String>) s -> {
			ListeningEventService.Result r = ListeningEventService.validateKern(s, grundExistiert);
			return r == null ? null : r.error();
		});
	}

	/**
	 * @param kernPruefung {@link ListeningEventService#validateKern}, für Tests
	 *                     austauschbar (die echte fragt die Datenbank nach dem Grund)
	 */
	public static Plan planen(Spec alt, Aenderung a, java.util.function.Function<Spec, String> kernPruefung) {
		Spec neu = ListeningEventService.kopie(alt);

		if (a.clanTag != null) {
			neu.clanTag = a.clanTag;
		}
		if (a.type != null) {
			neu.type = a.type;
		}
		if (a.duration != null) {
			neu.duration = a.duration;
		}
		if (a.channelId != null) {
			neu.channelId = a.channelId;
		}
		if (a.actionType != null) {
			neu.actionType = a.actionType;
		}

		// Der Grund: ausdrücklich angegeben gilt er, sonst bleibt der alte - aber nur,
		// solange die Aktion überhaupt einen verwendet. Wer Kickpoint auf Info
		// umstellt, will keinen Grund mitschleppen, der beim nächsten Clanwechsel
		// "gibt es nicht" meldet.
		if (a.kickpointReasonName != null) {
			// Wie bei add: ein Distrikt-Check mit Grund ist der Kickpunkt-Modus
			if ("raidfails".equals(neu.actionType)) {
				neu.actionType = "raidfails_kickpoint";
			}
			if (!ListeningEventService.nutztGrund(neu.actionType)) {
				return Plan.fehler(alt, "Ein Kickpoint-Grund passt nicht zum Aktionstyp '" + neu.actionType
						+ "' - der vergibt keine Kickpunkte.");
			}
			neu.kickpointReasonName = a.kickpointReasonName;
		} else if (!ListeningEventService.nutztGrund(neu.actionType)) {
			neu.kickpointReasonName = null;
		}

		// "start" gibt es nur bei CW. Wer den Typ wechselt und die Dauer stehen
		// lässt, bekäme sonst eine Meldung über eine Dauer, die er gar nicht
		// angefasst hat - also deutlich sagen, was zu tun ist.
		if (neu.duration == -1 && !"cw".equals(neu.type) && a.duration == null) {
			return Plan.fehler(alt, "Das Event feuert bisher bei CW-Start ('start'). Das gibt es nur bei Clan War "
					+ "Events - gib für den neuen Typ zusätzlich eine `duration` an.");
		}

		String kern = kernPruefung.apply(neu);
		if (kern != null) {
			if (a.clanTag != null && a.kickpointReasonName == null && neu.kickpointReasonName != null) {
				kern += " Gib mit `kickpoint_reason` einen Grund des neuen Clans an.";
			}
			return Plan.fehler(alt, kern);
		}

		Art altArt = ListeningEventFormular.art(alt.type, alt.actionType);
		Art neuArt = ListeningEventFormular.art(neu.type, neu.actionType);
		boolean altGrund = alt.kickpointReasonName != null;
		boolean neuGrund = neu.kickpointReasonName != null;
		List<String> altIds = ListeningEventFormular.feldIds(altArt, alt.type, alt.actionType, altGrund);
		List<Feld> felder = ListeningEventFormular.felder(neuArt, neu.type, neu.actionType, neuGrund);
		List<String> neuIds = ListeningEventFormular.feldIds(neuArt, neu.type, neu.actionType, neuGrund);

		Map<String, String> vorbelegung = altArt == neuArt ? ListeningEventFormular.vorbelegung(alt) : Map.of();

		if (neuArt == null) {
			// Keine Zusatzwerte mehr: alles weg, was die alte Kombination hatte
			new ListeningEventFormular.Werte().in(neu);
			boolean nichts = a.leer();
			return new Plan(alt, neu, null, felder, vorbelegung, false, altArt == null,
					nichts ? "Gib mindestens eine Option an, die geändert werden soll. Dieses Event hat keine "
							+ "Zusatzwerte, die sich im Formular bearbeiten ließen." : null);
		}

		if (altArt == neuArt && altIds.equals(neuIds)) {
			// Gleiche Felder: Werte bleiben, wie sie sind (kopie() hat sie schon).
			// Ganz ohne andere Angabe ist das Formular das Einzige, was zu ändern bleibt.
			boolean formular = a.werteBearbeiten || a.leer();
			// Ältere Events tragen Werte, die die heutigen Regeln nicht mehr annehmen
			// (etwa benötigte Angriffe im Format von 2025). Statt am Ende mit einer
			// Meldung über einen Wert zu scheitern, den niemand angefasst hat: gleich
			// das Formular, damit man ihn bei der Gelegenheit richtigstellt.
			if (!formular && ListeningEventService.validateWerte(neu) != null) {
				formular = true;
			}
			return new Plan(alt, neu, neuArt, felder, vorbelegung, formular, !formular, null);
		}

		if (altArt == neuArt && altIds.containsAll(neuIds)) {
			// Teilmenge: übernehmen, was noch gefragt ist. Lässt sich der alte Stand
			// nicht sauber lesen (Events aus der Zeit vor einer Option), doch fragen.
			// Nur die Felder, die das neue Formular hätte - wie beim echten Abschicken
			ListeningEventFormular.Lesung l = ListeningEventFormular.lies(neuArt, neu.type, neuGrund,
					feld -> neuIds.contains(feld) ? vorbelegung.get(feld) : null);
			if (l.fehler() == null && !a.werteBearbeiten) {
				anwenden(neu, alt, neuArt, neuIds, l.werte());
				return new Plan(alt, neu, neuArt, felder, vorbelegung, false, false, null);
			}
		}

		return new Plan(alt, neu, neuArt, felder, vorbelegung, true, false, null);
	}

	/**
	 * Setzt die Formularwerte in den neuen Stand. Benannte Einstellungen, die
	 * dieses Formular nicht abfragt (etwa von der Website gesetzte), bleiben bei
	 * gleicher Formular-Art erhalten.
	 */
	public static void anwenden(Spec neu, Spec alt, Art art, List<String> feldIds, ListeningEventFormular.Werte w) {
		w.in(neu);
		if (art != null && art == ListeningEventFormular.art(alt.type, alt.actionType) && alt.namedSettings != null) {
			Map<String, Long> n = neu.namedSettings != null ? new HashMap<>(neu.namedSettings) : new HashMap<>();
			for (final Map.Entry<String, Long> e : alt.namedSettings.entrySet()) {
				// Was ein Formular abfragt, bestimmt allein das Formular - eine
				// Einstellung, die die neue Kombination nicht mehr kennt, fällt weg.
				if (!FORMULAR_EINSTELLUNGEN.contains(e.getKey()) && !n.containsKey(e.getKey())) {
					n.put(e.getKey(), e.getValue());
				}
			}
			neu.namedSettings = n.isEmpty() ? null : n;
		}
	}

	/** Benannte Einstellungen, die irgendein Formular abfragt (Feld-ID = Schlüssel). */
	private static final java.util.Set<String> FORMULAR_EINSTELLUNGEN = java.util.Set.of(
			lostmanager.datawrapper.ListeningEvent.SETTING_IGNORE_PERFECT_WAR,
			lostmanager.datawrapper.ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS,
			lostmanager.datawrapper.ListeningEvent.SETTING_WINS_THRESHOLD,
			lostmanager.datawrapper.ListeningEvent.SETTING_STARFAILS_FREE_HITS,
			lostmanager.datawrapper.ListeningEvent.SETTING_CW_MIN_COUNT);

	/** Nach dem Formular: die eingegebenen Werte in den Plan übernehmen. */
	public static Spec mitFormular(Plan p, ListeningEventFormular.Werte w) {
		Spec neu = ListeningEventService.kopie(p.neu());
		List<String> ids = p.felder().stream().map(Feld::id).toList();
		anwenden(neu, p.alt(), p.art(), ids, w);
		return neu;
	}
}
