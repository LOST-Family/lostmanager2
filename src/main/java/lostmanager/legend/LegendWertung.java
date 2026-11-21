package lostmanager.legend;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.Bot;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;
import lostmanager.legend.LegendRegeln.Kampf;
import lostmanager.legend.LegendRegeln.Schaetzer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

/**
 * Perfect Day (Legend I), Trophy Hunt (Legend II) und die Rolle „Trophyhunt“.
 * Regeln in {@link LegendRegeln}, Posts in {@link LegendTexte}.
 *
 * Ablauf, alle zwei Minuten auf einem eigenen Takt (nicht auf
 * Bot.schedulertasks, den restartAllEvents neu aufbaut):
 * <ol>
 * <li>Alle 15 Minuten: Mitgliederlisten der LOST-Clans (ein Abruf je Clan) plus
 * die in der Datenbank eingetragenen Mitglieder, die gerade woanders spielen
 * (ein Abruf je Spieler) – daraus die Liga jedes Accounts
 * ({@code legend_spieler}).</li>
 * <li>Häufig abgefragt werden nur, wen es betrifft: Legend I immer, Legend II
 * nur in der Trophy-Hunt-Woche. Ändert sich der Pokalstand, holt der Bot das
 * Kampfprotokoll und bucht jeden neuen Angriff nach der Pokalformel.</li>
 * <li>Nach dem Legend-Tageswechsel (05:00 UTC) die Perfect-Day-Rangliste, nach
 * Ende der Trophy-Hunt-Woche die Auswertung. Jeder Post genau einmal
 * ({@code legend_meldung}).</li>
 * <li>Täglich ab 05:30 UTC (und gleich nach dem Start) die Rolle abgleichen.</li>
 * </ol>
 *
 * Umgebung: PERFECTDAY_KANAL, TROPHYHUNT_KANAL, TROPHYHUNT_ROLLE_ID (ohne: kein
 * Abgleich), PERFECTDAY_OHNE_BELOHNUNG, PERFECTDAY_AUSSCHLUSS (Tag=#A,#B;… nur
 * im Post dieses Tages weglassen, *=#C;… an jedem Tag), PERFECTDAY_ERSTER_TAG,
 * TROPHYHUNT_ERSTER_MONAT, LEGEND_TROCKEN=1 (nichts posten, keine Rollen, nur
 * Journal), LEGEND_AUS=1.
 */
public final class LegendWertung {

	static final String PD_KANAL = env("PERFECTDAY_KANAL", "1564398086321602580");
	static final String HUNT_KANAL = env("TROPHYHUNT_KANAL", "1564398226222747810");
	static final String ROLLE = env("TROPHYHUNT_ROLLE_ID", "");
	static final String OHNE_BELOHNUNG = env("PERFECTDAY_OHNE_BELOHNUNG", "");
	static final String AUSSCHLUSS = env("PERFECTDAY_AUSSCHLUSS", "");
	static final LocalDate ERSTER_TAG = LocalDate.parse(env("PERFECTDAY_ERSTER_TAG", "2026-11-16"));
	static final YearMonth ERSTER_HUNT = YearMonth.parse(env("TROPHYHUNT_ERSTER_MONAT", "2026-11"));
	static final boolean TROCKEN = "1".equals(env("LEGEND_TROCKEN", ""));
	static final boolean AUS = "1".equals(env("LEGEND_AUS", ""));

	static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
	private static final DateTimeFormatter KAMPFZEIT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX");

	private static final Duration SUCHE_ALLE = Duration.ofMinutes(15);

	private static final ScheduledExecutorService TAKT = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "legend-wertung");
		t.setDaemon(true);
		return t;
	});
	private static boolean gestartet = false;
	private static Instant letzteSuche = Instant.EPOCH;
	private static Instant letzteErfolgreicheSuche = Instant.EPOCH;
	private static Instant letzterRollenabgleich = Instant.EPOCH;
	private static boolean rolleFehltGemeldet = false;
	private static final Set<String> trockenGemeldet = new HashSet<>();

	private LegendWertung() {
	}

	private static String env(String name, String standard) {
		String wert = System.getenv(name);
		return (wert != null && !wert.isBlank()) ? wert.trim() : standard;
	}

	public static synchronized void start() {
		if (gestartet || AUS) {
			if (AUS) {
				System.out.println("Perfect Day / Trophy Hunt: abgeschaltet (LEGEND_AUS=1)");
			}
			return;
		}
		gestartet = true;
		tabellen();
		// Alle 30 Sekunden (bis 17.11.2026 alle 2 Minuten): Legend I bucht jede
		// Pokaländerung einzeln, dafür muss ein Fenster möglichst nur einen Kampf
		// enthalten (LegendRegeln.legendEinsSchritt).
		TAKT.scheduleWithFixedDelay(LegendWertung::tickSicher, 30, 30, TimeUnit.SECONDS);
		System.out.println("Perfect Day / Trophy Hunt: Takt alle 30 Sekunden gestartet"
				+ (TROCKEN ? " (TROCKEN: nichts wird gepostet, keine Rollen)" : "")
				+ (ROLLE.isEmpty() ? ", Trophyhunt-Rolle nicht konfiguriert" : ", Trophyhunt-Rolle " + ROLLE));
	}

	// ------------------------------------------------------------------
	// Tabellen
	// ------------------------------------------------------------------

	static void tabellen() {
		String[] sql = {
				"CREATE TABLE IF NOT EXISTS legend_spieler (player_tag TEXT PRIMARY KEY, name TEXT, liga TEXT, "
						+ "clan_tag TEXT, gesehen TIMESTAMPTZ NOT NULL)",
				"CREATE TABLE IF NOT EXISTS legend_beobachtung (player_tag TEXT PRIMARY KEY, liga TEXT NOT NULL, "
						+ "saison BIGINT NOT NULL, pokale INT NOT NULL, zeit TIMESTAMPTZ NOT NULL, kampf_stand TIMESTAMPTZ, "
						+ "offen_seit TIMESTAMPTZ)",
				"CREATE TABLE IF NOT EXISTS legend_tageswert (legend_tag DATE NOT NULL, player_tag TEXT NOT NULL, "
						+ "liga TEXT NOT NULL, saison BIGINT NOT NULL, name TEXT, angriffspokale INT NOT NULL DEFAULT 0, "
						+ "angriffe INT NOT NULL DEFAULT 0, unsicher INT NOT NULL DEFAULT 0, "
						+ "unvollstaendig BOOLEAN NOT NULL DEFAULT FALSE, PRIMARY KEY (legend_tag, player_tag, liga))",
				// Woher der Tageswert stammt (seit 17.11.2026): NULL = vom Bot gemessen,
				// 'korrektur' = belegter Gesamtwert (betreuung/legend-korrektur.py), den
				// der Bot nicht mehr anfasst. Siehe buchen().
				"ALTER TABLE legend_tageswert ADD COLUMN IF NOT EXISTS quelle TEXT",
				// Legend I: Schlüssel der schon verbuchten Kämpfe (durch \n getrennt), siehe
				// LegendRegeln.legendEins. NULL = noch nicht nach dem neuen Verfahren erfasst.
				"ALTER TABLE legend_beobachtung ADD COLUMN IF NOT EXISTS kaempfe TEXT",
				"ALTER TABLE legend_beobachtung ADD COLUMN IF NOT EXISTS topf TEXT",
				"ALTER TABLE legend_beobachtung ADD COLUMN IF NOT EXISTS topf_pokale INT NOT NULL DEFAULT 0",
				"ALTER TABLE legend_beobachtung ADD COLUMN IF NOT EXISTS letzte_regung TIMESTAMPTZ",
				"CREATE TABLE IF NOT EXISTS legend_stichprobe (ranked BOOLEAN NOT NULL, sterne INT NOT NULL, "
						+ "prozent INT NOT NULL, pokale_summe BIGINT NOT NULL, anzahl INT NOT NULL, "
						+ "PRIMARY KEY (ranked, sterne, prozent))",
				"CREATE TABLE IF NOT EXISTS legend_korrektur (id BIGSERIAL PRIMARY KEY, wertung TEXT NOT NULL, "
						+ "schluessel TEXT NOT NULL, player_tag TEXT NOT NULL, name TEXT, wert INT NOT NULL, grund TEXT, "
						+ "erstellt TIMESTAMPTZ NOT NULL DEFAULT now())",
				"CREATE TABLE IF NOT EXISTS legend_meldung (art TEXT NOT NULL, schluessel TEXT NOT NULL, "
						+ "gepostet TIMESTAMPTZ NOT NULL DEFAULT now(), nachrichten TEXT, PRIMARY KEY (art, schluessel))",
				"CREATE TABLE IF NOT EXISTS trophyhunt_sieger (monat TEXT PRIMARY KEY, player_tag TEXT NOT NULL, "
						+ "discord_id TEXT, name TEXT, pokale INT, eingetragen TIMESTAMPTZ NOT NULL DEFAULT now())" };
		try (Statement st = Connection.getConnection().createStatement()) {
			for (String s : sql) {
				st.executeUpdate(s);
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day / Trophy Hunt: Tabellen nicht angelegt: " + e.getMessage());
		}
	}

	// ------------------------------------------------------------------
	// Takt
	// ------------------------------------------------------------------

	private static void tickSicher() {
		try {
			tick(Instant.now());
		} catch (Throwable t) {
			System.err.println("Perfect Day / Trophy Hunt: Fehler im Takt: " + t);
		}
	}

	static void tick(Instant jetzt) {
		if (Bot.getJda() == null) {
			return;
		}
		if (Duration.between(letzteSuche, jetzt).compareTo(SUCHE_ALLE) >= 0) {
			letzteSuche = jetzt;
			suche(jetzt);
		}

		Schaetzer schaetzer = ladeSchaetzer();
		List<String[]> ziele = ziele(jetzt);
		Set<String> zielTags = new HashSet<>();
		for (String[] z : ziele) {
			zielTags.add(z[0]);
			try {
				beobachten(z[0], jetzt, schaetzer);
			} catch (Exception e) {
				System.err.println("Perfect Day / Trophy Hunt: " + z[0] + " nicht ausgewertet: " + e);
			}
		}
		aufraeumen(zielTags);

		try {
			perfectDayPruefen(jetzt);
		} catch (Exception e) {
			System.err.println("Perfect Day: Rangliste nicht erstellt: " + e);
		}
		try {
			huntPruefen(jetzt);
		} catch (Exception e) {
			System.err.println("Trophy Hunt: Auswertung nicht erstellt: " + e);
		}
		try {
			rollePruefen(jetzt);
		} catch (Exception e) {
			System.err.println("Trophyhunt-Rolle: Abgleich gescheitert: " + e);
		}
	}

	// ------------------------------------------------------------------
	// Suche: welche LOST-Accounts sind in welcher Liga
	// ------------------------------------------------------------------

	static void suche(Instant jetzt) {
		Set<String> gesehen = new HashSet<>();
		int clansOk = 0;
		ArrayList<String> clans = DBUtil.getArrayListFromSQL("SELECT tag FROM clans", String.class);
		for (String clanTag : clans) {
			String json = new Clan(clanTag).getJson();
			if (json == null) {
				continue;
			}
			JSONArray liste = new JSONObject(json).optJSONArray("memberList");
			if (liste == null) {
				continue;
			}
			clansOk++;
			for (int i = 0; i < liste.length(); i++) {
				JSONObject m = liste.getJSONObject(i);
				String tag = m.getString("tag");
				gesehen.add(tag);
				spielerMerken(tag, m.optString("name"), liga(m), clanTag, jetzt);
			}
		}
		// In der Datenbank einem LOST-Clan zugeordnet, spielt aber gerade woanders
		// (etwa 幸Relaxo走, LOST 3, im Spiel in einem Nebenclan).
		ArrayList<String> dbMitglieder = DBUtil.getArrayListFromSQL(
				"SELECT player_tag FROM clan_members WHERE clan_tag IN (SELECT tag FROM clans)", String.class);
		for (String tag : dbMitglieder) {
			if (gesehen.contains(tag)) {
				continue;
			}
			String json = new Player(tag).getJson();
			if (json == null) {
				continue;
			}
			JSONObject p = new JSONObject(json);
			spielerMerken(tag, p.optString("name"), liga(p), null, jetzt);
		}
		if (clansOk == clans.size() && clansOk > 0) {
			letzteErfolgreicheSuche = jetzt;
		}
	}

	static String liga(JSONObject spieler) {
		JSONObject lt = spieler.optJSONObject("leagueTier");
		return lt == null ? null : lt.optString("name", null);
	}

	private static void spielerMerken(String tag, String name, String liga, String clan, Instant jetzt) {
		DBUtil.executeUpdate("INSERT INTO legend_spieler (player_tag, name, liga, clan_tag, gesehen) VALUES (?,?,?,?,?) "
				+ "ON CONFLICT (player_tag) DO UPDATE SET name = EXCLUDED.name, liga = EXCLUDED.liga, "
				+ "clan_tag = COALESCE(EXCLUDED.clan_tag, legend_spieler.clan_tag), gesehen = EXCLUDED.gesehen", tag,
				name, liga, clan, Timestamp.from(jetzt));
	}

	/** Wer jetzt häufig abgefragt wird: [tag, liga]. */
	static List<String[]> ziele(Instant jetzt) {
		boolean hunt = LegendRegeln.laufenderHunt(jetzt).isPresent()
				|| LegendRegeln.laufenderHunt(jetzt.minus(Duration.ofMinutes(30))).isPresent();
		List<String[]> ziele = new ArrayList<>();
		String sql = "SELECT player_tag, liga FROM legend_spieler WHERE gesehen > ? AND liga IN (?, ?)";
		try (PreparedStatement ps = Connection.getConnection().prepareStatement(sql)) {
			ps.setTimestamp(1, Timestamp.from(jetzt.minus(Duration.ofHours(6))));
			ps.setString(2, LegendRegeln.LIGA_1);
			ps.setString(3, hunt ? LegendRegeln.LIGA_2 : LegendRegeln.LIGA_1);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					ziele.add(new String[] { rs.getString(1), rs.getString(2) });
				}
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day / Trophy Hunt: Zielliste nicht lesbar: " + e.getMessage());
		}
		return ziele;
	}

	private static void aufraeumen(Set<String> zielTags) {
		ArrayList<String> beobachtet = DBUtil.getArrayListFromSQL("SELECT player_tag FROM legend_beobachtung",
				String.class);
		for (String tag : beobachtet) {
			if (!zielTags.contains(tag)) {
				DBUtil.executeUpdate("DELETE FROM legend_beobachtung WHERE player_tag = ?", tag);
			}
		}
	}

	// ------------------------------------------------------------------
	// Beobachten: Pokalstand gegen den letzten Stand
	// ------------------------------------------------------------------

	record Beobachtung(String liga, long saison, int pokale, Instant zeit, Instant kampfStand, Instant offenSeit) {
	}

	static Beobachtung ladeBeobachtung(String tag) {
		String sql = "SELECT liga, saison, pokale, zeit, kampf_stand, offen_seit FROM legend_beobachtung WHERE player_tag = ?";
		try (PreparedStatement ps = Connection.getConnection().prepareStatement(sql)) {
			ps.setString(1, tag);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return new Beobachtung(rs.getString(1), rs.getLong(2), rs.getInt(3), zeit(rs.getTimestamp(4)),
							zeit(rs.getTimestamp(5)), zeit(rs.getTimestamp(6)));
				}
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day / Trophy Hunt: Beobachtung " + tag + " nicht lesbar: " + e.getMessage());
		}
		return null;
	}

	private static Instant zeit(Timestamp t) {
		return t == null ? null : t.toInstant();
	}

	private static Timestamp ts(Instant t) {
		return t == null ? null : Timestamp.from(t);
	}

	static void speichern(String tag, String liga, long saison, int pokale, Instant zeit, Instant kampfStand,
			Instant offenSeit) {
		DBUtil.executeUpdate("INSERT INTO legend_beobachtung (player_tag, liga, saison, pokale, zeit, kampf_stand, offen_seit) "
				+ "VALUES (?,?,?,?,?,?,?) ON CONFLICT (player_tag) DO UPDATE SET liga = EXCLUDED.liga, "
				+ "saison = EXCLUDED.saison, pokale = EXCLUDED.pokale, zeit = EXCLUDED.zeit, "
				+ "kampf_stand = EXCLUDED.kampf_stand, offen_seit = EXCLUDED.offen_seit", tag, liga, saison, pokale,
				ts(zeit), ts(kampfStand), ts(offenSeit));
	}

	static void beobachten(String tag, Instant jetzt, Schaetzer schaetzer) {
		String json = new Player(tag).getJson();
		if (json == null) {
			return;
		}
		JSONObject p = new JSONObject(json);
		String name = p.optString("name");
		String liga = liga(p);
		int pokale = p.optInt("trophies");
		long saison = p.optLong("currentLeagueSeasonId", 0);
		boolean ranked = LegendRegeln.LIGA_2.equals(liga);
		Beobachtung vorher = ladeBeobachtung(tag);
		// Nach Ende der Hunt-Woche zeigt die API die alte Woche oft noch ein paar
		// Minuten: so lange weiter verfolgen, bis der Wechsel kommt und die Woche
		// mit dem Endstand abgeschlossen werden kann.
		boolean nachlauf = ranked && vorher != null && LegendRegeln.LIGA_2.equals(vorher.liga())
				&& vorher.saison() == saison && istHuntSaison(saison);
		boolean verfolgt = LegendRegeln.LIGA_1.equals(liga)
				|| (ranked && (LegendRegeln.laufenderHunt(jetzt).isPresent() || nachlauf));

		boolean wechsel = vorher != null && (!vorher.liga().equals(liga) || vorher.saison() != saison);
		if (wechsel && LegendRegeln.LIGA_2.equals(vorher.liga())) {
			wocheAbschliessen(tag, name, vorher, schaetzer);
		}
		if (!verfolgt) {
			DBUtil.executeUpdate("DELETE FROM legend_beobachtung WHERE player_tag = ?", tag);
			return;
		}
		if (vorher == null || wechsel) {
			ersteSichtung(tag, name, liga, saison, pokale, jetzt, schaetzer);
			return;
		}

		if (!ranked) {
			legendEinsBeobachten(tag, name, liga, saison, pokale, jetzt, vorher, schaetzer);
			return;
		}
		legendZweiBeobachten(tag, name, liga, saison, pokale, jetzt, ladeZweiStand(tag, vorher), false);
	}

	/**
	 * Legend II: jeden neuen Angriff im Protokoll nach der Formel buchen
	 * (LegendRegeln.legendZwei). {@code erste}: erste Sichtung – dann wird der
	 * Tageswert gesetzt statt addiert, damit eine wiederholte erste Sichtung
	 * (Spieler war länger nicht abrufbar) nichts doppelt zählt.
	 */
	static void legendZweiBeobachten(String tag, String name, String liga, long saison, int pokale, Instant jetzt,
			LegendRegeln.ZweiStand vorher, boolean erste) {
		legendZweiBeobachten(tag, name, liga, saison, pokale, jetzt, vorher, erste, () -> {
			JSONArray p = protokoll(tag);
			return p == null ? null : zweikaempfe(p);
		});
	}

	static void legendZweiBeobachten(String tag, String name, String liga, long saison, int pokale, Instant jetzt,
			LegendRegeln.ZweiStand vorher, boolean erste, java.util.function.Supplier<List<LegendRegeln.Zweikampf>> protokoll) {
		LegendRegeln.ZweiErgebnis erg = LegendRegeln.legendZwei(vorher, pokale, protokoll, Instant.ofEpochSecond(saison),
				jetzt);
		if (erg == null) {
			return; // API antwortet nicht – nächster Takt
		}
		zweiBuchen(tag, name, liga, saison, erg, erste, jetzt);
		LegendRegeln.ZweiStand neu = erg.stand();
		if (neu == vorher && !erste) {
			speichern(tag, liga, saison, neu.pokale(), jetzt, null, null);
			return; // nichts bewegt: die Schlüssel bleiben, wie sie sind
		}
		// Erste Sichtung bei 0 Pokalen (Wochenbeginn): die leere Schlüsselmenge trotzdem
		// schreiben – eine leere Spalte hieße „altes Verfahren“, und der erste Angriff
		// der Woche gälte beim nächsten Takt als schon verbucht.
		speichern(tag, liga, saison, neu.pokale(), jetzt, null, neu.offenSeit());
		// topf_pokale trägt in Legend II den noch nicht erklärten Rest des Pokalstands.
		// (topf: Merker, dass das Protokoll nicht ans Verbuchte anschloss.)
		DBUtil.executeUpdate("UPDATE legend_beobachtung SET kaempfe = ?, topf = ?, topf_pokale = ?, letzte_regung = NULL "
				+ "WHERE player_tag = ?", String.join("\n", new java.util.TreeSet<>(neu.gesehen())),
				neu.ohneAnschluss() ? OHNE_ANSCHLUSS : null, neu.rest(), tag);
	}

	private static final String OHNE_ANSCHLUSS = "ohne-anschluss";

	private static void zweiBuchen(String tag, String name, String liga, long saison, LegendRegeln.ZweiErgebnis erg,
			boolean erste, Instant jetzt) {
		int summe = 0, angriffe = 0;
		for (Map.Entry<LocalDate, Integer> e : erg.tage().entrySet()) {
			int n = erg.anzahl().getOrDefault(e.getKey(), 0);
			summe += e.getValue();
			angriffe += n;
			if (erste) {
				mindestens(tag, name, liga, saison, e.getKey(), e.getValue(), n, erg.unvollstaendig());
			} else {
				buchen(tag, name, liga, saison, Map.of(e.getKey(), e.getValue()), n, 0, erg.unvollstaendig());
			}
		}
		if (erg.unvollstaendig() && erg.tage().isEmpty()) {
			buchen(tag, name, liga, saison, Map.of(LegendRegeln.legendTag(jetzt), 0), 0, 0, true);
		}
		if (erste && angriffe > 0) {
			System.out.println("Trophy Hunt: " + name + " " + tag + " erstmals gesehen, " + angriffe
					+ " Angriffe der Woche aus dem Protokoll = " + summe + " Pokale");
		}
		if (erg.unvollstaendig()) {
			System.out.println("Trophy Hunt: " + name + " " + tag + " unvollständig – das Protokoll schloss nicht an "
					+ "die verbuchten Angriffe an, " + erg.unerklaert() + " Pokale liegen über den erfassten Kämpfen");
		} else if (erg.unerklaert() != 0) {
			System.out.println("Trophy Hunt: " + name + " " + tag + ": " + erg.unerklaert()
					+ " Pokale ohne passenden Kampf im Protokoll (Stand übernommen, Angriffe unberührt)");
		}
	}

	/** Legend-II-Stand aus der Datenbank; Schlüssel null = Spalte leer (noch nie nach Schlüsseln erfasst). */
	static LegendRegeln.ZweiStand ladeZweiStand(String tag, Beobachtung vorher) {
		Set<String> gesehen = null;
		int rest = 0;
		boolean ohne = false;
		try (PreparedStatement ps = Connection.getConnection()
				.prepareStatement("SELECT kaempfe, topf_pokale, topf FROM legend_beobachtung WHERE player_tag = ?")) {
			ps.setString(1, tag);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next() && rs.getString(1) != null) {
					gesehen = schluessel(rs.getString(1));
					rest = rs.getInt(2);
					ohne = OHNE_ANSCHLUSS.equals(rs.getString(3));
				}
			}
		} catch (SQLException e) {
			System.err.println("Trophy Hunt: Stand " + tag + " nicht lesbar: " + e.getMessage());
		}
		return new LegendRegeln.ZweiStand(vorher.pokale(), rest, gesehen, vorher.offenSeit(), ohne);
	}

	/** Die Ranked-Kämpfe des Protokolls mit Schlüssel, älteste zuerst. */
	static List<LegendRegeln.Zweikampf> zweikaempfe(JSONArray protokoll) {
		List<LegendRegeln.Zweikampf> liste = new ArrayList<>();
		for (int i = 0; i < protokoll.length(); i++) {
			JSONObject k = protokoll.getJSONObject(i);
			if (!"ranked".equals(k.optString("battleType"))) {
				continue;
			}
			Instant beginn;
			try {
				beginn = java.time.ZonedDateTime.parse(k.getString("battleTimestamp"), KAMPFZEIT).toInstant();
			} catch (Exception e) {
				continue;
			}
			liste.add(new LegendRegeln.Zweikampf(schluesselVon(k), k.optBoolean("attack"), k.optInt("stars"),
					k.optInt("destructionPercentage"), beginn));
		}
		liste.sort((x, y) -> x.beginn().compareTo(y.beginn()));
		return liste;
	}

	private static String schluesselVon(JSONObject k) {
		return k.optString("battleTimestamp") + "|" + (k.optBoolean("attack") ? "A" : "V") + "|" + k.optInt("battleTime")
				+ "|" + k.optString("opponentPlayerTag");
	}

	/** Legend I: jede Pokaländerung sofort zuordnen (LegendRegeln.legendEins). */
	static void legendEinsBeobachten(String tag, String name, String liga, long saison, int pokale, Instant jetzt,
			Beobachtung vorher, Schaetzer schaetzer) {
		String gespeichert = null, topfText = null;
		int topfPokale = 0;
		Instant regung = null;
		try (PreparedStatement ps = Connection.getConnection()
				.prepareStatement("SELECT kaempfe, topf, topf_pokale, letzte_regung FROM legend_beobachtung WHERE player_tag = ?")) {
			ps.setString(1, tag);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					gespeichert = rs.getString(1);
					topfText = rs.getString(2);
					topfPokale = rs.getInt(3);
					regung = zeit(rs.getTimestamp(4));
				}
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day: Stand " + tag + " nicht lesbar: " + e.getMessage());
			return;
		}
		Instant blindBis = vorher.kampfStand();
		Instant offenSeit = vorher.offenSeit();
		if (gespeichert == null) {
			// Übergang vom alten Verfahren: was bis zur letzten Abfrage geendet hat, steckt
			// schon im Stand – außer der alte Takt wartete gerade auf eine Serie.
			Instant bis = vorher.offenSeit() != null ? vorher.kampfStand() : vorher.zeit();
			if (bis == null || (vorher.kampfStand() != null && vorher.kampfStand().isAfter(bis))) {
				bis = vorher.kampfStand() != null ? vorher.kampfStand() : vorher.zeit();
			}
			blindBis = bis;
			offenSeit = null;
		}
		LegendRegeln.EinsStand stand = new LegendRegeln.EinsStand(vorher.pokale(), schluessel(gespeichert),
				blindBis, topfPokale, schluessel(topfText), regung, offenSeit);
		LegendRegeln.EinsErgebnis erg = LegendRegeln.legendEins(stand, pokale, () -> {
			JSONArray p = protokoll(tag);
			return p == null ? null : eintraege(p);
		}, jetzt, LegendRegeln.formel(schaetzer));
		if (erg == null) {
			return;
		}
		LegendRegeln.Zuordnung z = erg.zuordnung();
		if (z != null && z.lernSterne() >= 0) {
			DBUtil.executeUpdate("INSERT INTO legend_stichprobe (ranked, sterne, prozent, pokale_summe, anzahl) "
					+ "VALUES (?,?,?,?,1) ON CONFLICT (ranked, sterne, prozent) DO UPDATE SET "
					+ "pokale_summe = legend_stichprobe.pokale_summe + EXCLUDED.pokale_summe, "
					+ "anzahl = legend_stichprobe.anzahl + 1", false, z.lernSterne(), z.lernProzent(),
					(long) z.lernPokale());
		}
		if (!erg.tage().isEmpty()) {
			buchen(tag, name, liga, saison, erg.tage(), z.angriffe(), z.unsicher() ? 1 : 0, false);
		}
		LegendRegeln.EinsStand neu = erg.stand();
		speichern(tag, liga, saison, neu.pokale(), jetzt, neu.blindBis(), neu.offenSeit());
		DBUtil.executeUpdate("UPDATE legend_beobachtung SET kaempfe = ?, topf = ?, topf_pokale = ?, letzte_regung = ? "
				+ "WHERE player_tag = ?", String.join("\n", new java.util.TreeSet<>(neu.gesehen())),
				String.join("\n", new java.util.TreeSet<>(neu.topf())), neu.topfPokale(), ts(neu.letzteRegung()), tag);
	}

	private static Set<String> schluessel(String text) {
		Set<String> s = new HashSet<>();
		if (text != null && !text.isEmpty()) {
			s.addAll(List.of(text.split("\n")));
		}
		return s;
	}

	/** Die Legend-Kämpfe des Protokolls mit Schlüssel. */
	static List<LegendRegeln.Eintrag> eintraege(JSONArray protokoll) {
		List<LegendRegeln.Eintrag> liste = new ArrayList<>();
		for (int i = 0; i < protokoll.length(); i++) {
			JSONObject k = protokoll.getJSONObject(i);
			if (!"legend".equals(k.optString("battleType"))) {
				continue;
			}
			Instant ende = kampfEnde(k);
			if (ende == null) {
				continue;
			}
			liste.add(new LegendRegeln.Eintrag(schluesselVon(k),
					new Kampf(k.optBoolean("attack"), k.optInt("stars"), k.optInt("destructionPercentage"), ende)));
		}
		return liste;
	}

	/**
	 * Erste Sichtung (Start, Auf-/Abstieg, neue Woche/Saison). Legend II: die Woche
	 * beginnt bei 0 Pokalen, die Angriffe seit Wochenbeginn stehen im Protokoll
	 * (soweit es zurückreicht, siehe LegendRegeln.legendZwei). Legend I: der Stand
	 * zu Tagesbeginn ist unbekannt, die Angriffe des Tages stehen im Protokoll.
	 */
	static void ersteSichtung(String tag, String name, String liga, long saison, int pokale, Instant jetzt,
			Schaetzer schaetzer) {
		boolean ranked = LegendRegeln.LIGA_2.equals(liga);
		if (ranked) {
			boolean huntWoche = LegendRegeln.laufenderHunt(jetzt).map(m -> LegendRegeln.huntStart(m).getEpochSecond())
					.orElse(-1L) == saison;
			if (huntWoche) {
				legendZweiBeobachten(tag, name, liga, saison, pokale, jetzt,
						new LegendRegeln.ZweiStand(0, 0, new HashSet<>(), null), true);
			} else {
				// Noch keine Saison genannt (frisch aufgestiegen) oder Nachlauf: nur merken.
				speichern(tag, liga, saison, pokale, jetzt, null, null);
				DBUtil.executeUpdate("UPDATE legend_beobachtung SET kaempfe = NULL, topf = NULL, topf_pokale = 0, "
						+ "letzte_regung = NULL WHERE player_tag = ?", tag);
			}
			return;
		}
		Instant ab = LegendRegeln.tagesbeginn(LegendRegeln.legendTag(jetzt));
		JSONArray protokoll = protokoll(tag);
		if (protokoll == null) {
			return; // ohne Protokoll keine sichere Grundlage, nächster Takt
		}
		List<Kampf> seitBeginn = kaempfe(protokoll, false, ab);
		Instant stand = seitBeginn.isEmpty() ? ab : seitBeginn.get(seitBeginn.size() - 1).ende();
		// Mitten am Tag dazugekommen (Bot-Start, Aufstieg, neu im Clan): die Angriffe des
		// Tages stehen im Protokoll, ihre Pokale ergeben sich aus Sternen und Prozent.
		// Vorher startete so jemand bei 0 (Maxi am 17.11.: 0 statt 234).
		LocalDate heute = LegendRegeln.legendTag(jetzt);
		int[] summe = tagesSumme(protokoll, heute);
		if (summe[1] > 0) {
			boolean unvollstaendig = protokoll.length() >= 50 && aeltestes(protokoll).isAfter(ab);
			mindestens(tag, name, liga, saison, heute, summe[0], summe[1], unvollstaendig);
			System.out.println("Perfect Day: " + name + " " + tag + " erstmals gesehen, " + summe[1]
					+ " Angriffe heute aus dem Protokoll = " + summe[0] + " Pokale"
					+ (unvollstaendig ? " (Protokoll reicht nicht bis Tagesbeginn)" : ""));
		}
		speichern(tag, liga, saison, pokale, jetzt, stand, null);
		// Legend I beginnt danach neu: was bis jetzt geendet hat, steckt im Stand.
		DBUtil.executeUpdate("UPDATE legend_beobachtung SET kaempfe = NULL, topf = NULL, topf_pokale = 0, "
				+ "letzte_regung = NULL WHERE player_tag = ?", tag);
	}

	/**
	 * Legend I: Angriffspokale und Zahl der Angriffe eines Legend-Tages aus dem
	 * Protokoll (nach battleTimestamp, wie ClashPerk und legend-gegenprobe.py).
	 */
	static int[] tagesSumme(JSONArray protokoll, LocalDate legendTag) {
		int pokale = 0, angriffe = 0;
		for (int i = 0; i < protokoll.length(); i++) {
			JSONObject k = protokoll.getJSONObject(i);
			if (!"legend".equals(k.optString("battleType")) || !k.optBoolean("attack")) {
				continue;
			}
			try {
				Instant beginn = java.time.ZonedDateTime.parse(k.getString("battleTimestamp"), KAMPFZEIT).toInstant();
				if (LegendRegeln.legendTag(beginn).equals(legendTag)) {
					pokale += LegendRegeln.legendEinsPokale(k.optInt("stars"), k.optInt("destructionPercentage"));
					angriffe++;
				}
			} catch (Exception e) {
				// unlesbarer Zeitstempel: nicht zählen
			}
		}
		return new int[] { Math.min(pokale, LegendRegeln.PERFECT_DAY_POKALE), angriffe };
	}

	/**
	 * Setzt den Tageswert auf mindestens diesen Stand (nie addieren: wer am selben
	 * Tag schon gezählt wurde und erneut „erstmals“ gesehen wird, bekäme ihn sonst
	 * doppelt). Korrigierte Tage bleiben.
	 */
	static void mindestens(String tag, String name, String liga, long saison, LocalDate legendTag, int pokale,
			int angriffe, boolean unvollstaendig) {
		if (saison == 0) {
			Long haeufigste = DBUtil.getValueFromSQL("SELECT saison FROM legend_beobachtung WHERE liga = ? AND saison > 0 "
					+ "GROUP BY saison ORDER BY count(*) DESC LIMIT 1", Long.class, liga);
			saison = haeufigste == null ? 0 : haeufigste;
		}
		DBUtil.executeUpdate("INSERT INTO legend_tageswert (legend_tag, player_tag, liga, saison, name, angriffspokale, "
				+ "angriffe, unvollstaendig) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (legend_tag, player_tag, liga) DO UPDATE "
				+ "SET name = EXCLUDED.name, angriffspokale = GREATEST(legend_tageswert.angriffspokale, EXCLUDED.angriffspokale), "
				+ "angriffe = GREATEST(legend_tageswert.angriffe, EXCLUDED.angriffe), "
				+ "unvollstaendig = legend_tageswert.unvollstaendig OR EXCLUDED.unvollstaendig "
				+ "WHERE legend_tageswert.quelle IS NULL", legendTag, tag, liga, saison, name, pokale, angriffe,
				unvollstaendig);
	}

	/**
	 * Eine Legend-II-Woche ist vorbei (oder der Spieler hat die Liga gewechselt).
	 * War es die Trophy-Hunt-Woche, werden die Angriffe nachgetragen, die zwischen
	 * der letzten Abfrage und dem Wechsel noch ins Protokoll gekommen sind.
	 */
	static void wocheAbschliessen(String tag, String name, Beobachtung vorher, Schaetzer schaetzer) {
		if (!istHuntSaison(vorher.saison())) {
			return;
		}
		JSONArray protokoll = protokoll(tag);
		if (protokoll == null) {
			System.err.println("Trophy Hunt: Protokoll von " + tag + " nicht abrufbar, letzte Angriffe der Woche fehlen");
			return;
		}
		LegendRegeln.ZweiErgebnis erg = LegendRegeln.legendZweiAbschluss(ladeZweiStand(tag, vorher),
				zweikaempfe(protokoll), Instant.ofEpochSecond(vorher.saison()));
		zweiBuchen(tag, name, LegendRegeln.LIGA_2, vorher.saison(), erg, false,
				Instant.ofEpochSecond(vorher.saison()).plus(Duration.ofDays(7)).minusSeconds(1));
	}

	/** Ist diese Liga-Woche (Saison-ID = Beginn in Sekunden) eine Trophy-Hunt-Woche? */
	static boolean istHuntSaison(long saison) {
		if (saison <= 0) {
			return false;
		}
		YearMonth m = YearMonth.from(Instant.ofEpochSecond(saison).atZone(java.time.ZoneOffset.UTC));
		return LegendRegeln.huntStart(m).getEpochSecond() == saison;
	}

	private static JSONArray protokoll(String tag) {
		String json = new Player(tag).getUnterpfadJson("battlelog");
		if (json == null) {
			return null;
		}
		JSONArray items = new JSONObject(json).optJSONArray("items");
		return items == null ? new JSONArray() : items;
	}

	/** Kämpfe der Liga (legend bzw. ranked), die nach {@code ab} geendet haben, älteste zuerst. */
	static List<Kampf> kaempfe(JSONArray protokoll, boolean ranked, Instant ab) {
		String typ = ranked ? "ranked" : "legend";
		List<Kampf> liste = new ArrayList<>();
		for (int i = 0; i < protokoll.length(); i++) {
			JSONObject k = protokoll.getJSONObject(i);
			if (!typ.equals(k.optString("battleType"))) {
				continue;
			}
			Instant ende = kampfEnde(k);
			if (ende == null || !ende.isAfter(ab)) {
				continue;
			}
			liste.add(new Kampf(k.optBoolean("attack"), k.optInt("stars"), k.optInt("destructionPercentage"), ende));
		}
		liste.sort((a, b) -> a.ende().compareTo(b.ende()));
		return liste;
	}

	static Instant kampfEnde(JSONObject k) {
		try {
			Instant beginn = java.time.ZonedDateTime.parse(k.getString("battleTimestamp"), KAMPFZEIT).toInstant();
			return beginn.plusSeconds(k.optInt("battleTime", 0));
		} catch (Exception e) {
			return null;
		}
	}

	private static Instant aeltestes(JSONArray protokoll) {
		Instant min = Instant.MAX;
		for (int i = 0; i < protokoll.length(); i++) {
			Instant t = kampfEnde(protokoll.getJSONObject(i));
			if (t != null && t.isBefore(min)) {
				min = t;
			}
		}
		return min;
	}

	/**
	 * Addiert auf den Tageswert. Ein belegter Wert ({@code quelle = 'korrektur'})
	 * bleibt, wie er ist (am 16.11. hatte der Bot auf Mölms korrigierte 291 noch
	 * 40 draufgebucht). In Legend I sind höchstens 320 je Tag möglich.
	 */
	static void buchen(String tag, String name, String liga, long saison, Map<LocalDate, Integer> tage, int angriffe,
			int unsicher, boolean unvollstaendig) {
		if (saison == 0) {
			// Frisch aufgestiegen: die API nennt noch keine Saison – die der anderen nehmen.
			Long haeufigste = DBUtil.getValueFromSQL("SELECT saison FROM legend_beobachtung WHERE liga = ? AND saison > 0 "
					+ "GROUP BY saison ORDER BY count(*) DESC LIMIT 1", Long.class, liga);
			saison = haeufigste == null ? 0 : haeufigste;
		}
		boolean erster = true;
		for (Map.Entry<LocalDate, Integer> e : tage.entrySet()) {
			DBUtil.executeUpdate("INSERT INTO legend_tageswert (legend_tag, player_tag, liga, saison, name, angriffspokale, "
					+ "angriffe, unsicher, unvollstaendig) VALUES (?,?,?,?,?,?,?,?,?) "
					+ "ON CONFLICT (legend_tag, player_tag, liga) DO UPDATE SET name = EXCLUDED.name, "
					+ "angriffspokale = CASE WHEN legend_tageswert.liga = ? "
					+ "THEN LEAST(?, legend_tageswert.angriffspokale + EXCLUDED.angriffspokale) "
					+ "ELSE legend_tageswert.angriffspokale + EXCLUDED.angriffspokale END, "
					+ "angriffe = legend_tageswert.angriffe + EXCLUDED.angriffe, "
					+ "unsicher = legend_tageswert.unsicher + EXCLUDED.unsicher, "
					+ "unvollstaendig = legend_tageswert.unvollstaendig OR EXCLUDED.unvollstaendig "
					+ "WHERE legend_tageswert.quelle IS NULL", e.getKey(), tag, liga, saison, name,
					LegendRegeln.LIGA_1.equals(liga) ? Math.min(e.getValue(), LegendRegeln.PERFECT_DAY_POKALE) : e.getValue(),
					erster ? angriffe : 0, erster ? unsicher : 0, unvollstaendig, LegendRegeln.LIGA_1,
					LegendRegeln.PERFECT_DAY_POKALE);
			erster = false;
		}
	}

	static Schaetzer ladeSchaetzer() {
		Map<String, int[]> werte = new HashMap<>();
		String sql = "SELECT ranked, sterne, prozent, pokale_summe, anzahl FROM legend_stichprobe";
		try (PreparedStatement ps = Connection.getConnection().prepareStatement(sql);
				ResultSet rs = ps.executeQuery()) {
			while (rs.next()) {
				werte.put(rs.getBoolean(1) + ":" + rs.getInt(2) + ":" + rs.getInt(3),
						new int[] { (int) Math.round((double) rs.getLong(4) / rs.getInt(5)) });
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day / Trophy Hunt: Stichproben nicht lesbar: " + e.getMessage());
		}
		return (ranked, sterne, prozent) -> {
			for (int abstand = 0; abstand <= 10; abstand++) {
				for (int p : new int[] { prozent - abstand, prozent + abstand }) {
					int[] w = werte.get(ranked + ":" + sterne + ":" + p);
					if (w != null) {
						return w[0];
					}
				}
			}
			return LegendRegeln.GROB.angreiferPokale(ranked, sterne, prozent);
		};
	}

	// ------------------------------------------------------------------
	// Perfect Day: Rangliste nach dem Tageswechsel
	// ------------------------------------------------------------------

	static void perfectDayPruefen(Instant jetzt) {
		LocalDate tag = LegendRegeln.legendTag(jetzt).minusDays(1);
		Instant wechsel = LegendRegeln.tagesbeginn(tag.plusDays(1));
		if (tag.isBefore(ERSTER_TAG) || jetzt.isBefore(wechsel.plus(Duration.ofMinutes(8)))
				|| jetzt.isAfter(wechsel.plus(Duration.ofHours(6)))) {
			return;
		}
		String schluessel = tag.toString();
		if (gemeldet("perfectday", schluessel)) {
			return;
		}
		Long saison = DBUtil.getValueFromSQL("SELECT saison FROM legend_tageswert WHERE liga = ? AND legend_tag = ? "
				+ "AND saison > 0 GROUP BY saison ORDER BY count(*) DESC LIMIT 1", Long.class, LegendRegeln.LIGA_1, tag);
		if (saison == null) {
			return;
		}
		List<LegendTexte.PdZeile> zeilen = perfectDayRangliste(saison, tag);
		if (zeilen.isEmpty()) {
			return;
		}
		Long aktuelle = DBUtil.getValueFromSQL("SELECT saison FROM legend_beobachtung WHERE liga = ? AND saison > 0 "
				+ "GROUP BY saison ORDER BY count(*) DESC LIMIT 1", Long.class, LegendRegeln.LIGA_1);
		boolean endstand = aktuelle != null && aktuelle > saison;
		LocalDate start = LegendRegeln.legendTag(Instant.ofEpochSecond(saison));
		boolean ohne = LegendRegeln.ohneBelohnung(start, start.plusDays(28), OHNE_BELOHNUNG);
		List<String> texte = LegendTexte.perfectDay(zeilen, tag.plusDays(1), endstand, ohne);
		posten("perfectday", schluessel, PD_KANAL, texte);
	}

	static List<LegendTexte.PdZeile> perfectDayRangliste(long saison, LocalDate tag) {
		return perfectDayRangliste(saison, tag, AUSSCHLUSS);
	}

	static List<LegendTexte.PdZeile> perfectDayRangliste(long saison, LocalDate tag, String ausschluss) {
		record Roh(String tag, String name, int gesamt, int gestern, int perfekt, int pokale, boolean unvoll) {
		}
		Map<String, Roh> roh = new LinkedHashMap<>();
		String sql = "SELECT player_tag, (array_agg(name ORDER BY legend_tag DESC))[1], SUM(angriffspokale / 10), "
				+ "COALESCE(SUM(angriffspokale / 10) FILTER (WHERE legend_tag = ?), 0), "
				+ "COUNT(*) FILTER (WHERE angriffspokale >= ?), SUM(angriffspokale), "
				+ "COALESCE(BOOL_OR(unvollstaendig) FILTER (WHERE legend_tag = ?), FALSE) "
				+ "FROM legend_tageswert WHERE liga = ? AND saison = ? AND legend_tag <= ? AND legend_tag >= ? "
				+ "GROUP BY player_tag";
		try (PreparedStatement ps = Connection.getConnection().prepareStatement(sql)) {
			ps.setObject(1, tag);
			ps.setInt(2, LegendRegeln.PERFECT_DAY_POKALE);
			ps.setObject(3, tag);
			ps.setString(4, LegendRegeln.LIGA_1);
			ps.setLong(5, saison);
			ps.setObject(6, tag);
			ps.setObject(7, ERSTER_TAG);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					roh.put(rs.getString(1), new Roh(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getInt(4),
							rs.getInt(5), rs.getInt(6), rs.getBoolean(7)));
				}
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day: Rangliste nicht lesbar: " + e.getMessage());
			return List.of();
		}
		// Korrekturen und Startstand (Punkte aus der Zeit vor dem Bot).
		for (Map.Entry<String, Integer> k : korrekturen("perfectday", String.valueOf(saison)).entrySet()) {
			Roh r = roh.get(k.getKey());
			String name = r != null ? r.name() : korrekturName(k.getKey());
			roh.put(k.getKey(), r == null ? new Roh(k.getKey(), name, k.getValue(), 0, 0, 0, false)
					: new Roh(r.tag(), r.name(), r.gesamt() + k.getValue(), r.gestern(), r.perfekt(), r.pokale(),
							r.unvoll()));
		}
		List<Roh> liste = new ArrayList<>();
		java.util.Set<String> raus = LegendRegeln.ausgeschlossen(tag, ausschluss);
		// Gewertet wird nur, wer mit Discord verknüpft ist (Stefan 18.11.2026: Boshi
		// und FL Maxi stehen in LOST-Clans in Legend I, gehören aber nicht zum Event).
		// Alle echten Teilnehmer waren zu dem Zeitpunkt verknüpft.
		java.util.Set<String> verknuepft = new HashSet<>(DBUtil.getArrayListFromSQL(
				"SELECT coc_tag FROM players WHERE discord_id IS NOT NULL AND discord_id <> ''", String.class));
		List<String> ohneVerknuepfung = new ArrayList<>();
		for (Roh r : roh.values()) {
			if (raus.contains(r.tag())) {
				continue;
			}
			if (!verknuepft.contains(r.tag())) {
				ohneVerknuepfung.add(r.name() + " " + r.tag());
				continue;
			}
			if (r.gesamt() > 0 || r.gestern() > 0) {
				liste.add(r);
			}
		}
		if (!ohneVerknuepfung.isEmpty()) {
			System.out.println("Perfect Day " + tag + ": ohne Discord-Verknüpfung nicht gewertet: "
					+ String.join(", ", ohneVerknuepfung));
		}
		// Gleichstand: mehr Perfect Days, dann mehr Angriffspokale (Regeln vom 26.10.2026).
		liste.sort((a, b) -> a.gesamt() != b.gesamt() ? Integer.compare(b.gesamt(), a.gesamt())
				: a.perfekt() != b.perfekt() ? Integer.compare(b.perfekt(), a.perfekt())
						: a.pokale() != b.pokale() ? Integer.compare(b.pokale(), a.pokale())
								: String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())));
		List<LegendTexte.PdZeile> zeilen = new ArrayList<>();
		for (Roh r : liste) {
			zeilen.add(new LegendTexte.PdZeile(r.name(), r.gesamt(), r.gestern(), r.unvoll()));
		}
		return zeilen;
	}

	static Map<String, Integer> korrekturen(String wertung, String schluessel) {
		Map<String, Integer> summe = new LinkedHashMap<>();
		String sql = "SELECT player_tag, SUM(wert) FROM legend_korrektur WHERE wertung = ? AND schluessel = ? GROUP BY player_tag";
		try (PreparedStatement ps = Connection.getConnection().prepareStatement(sql)) {
			ps.setString(1, wertung);
			ps.setString(2, schluessel);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					summe.put(rs.getString(1), rs.getInt(2));
				}
			}
		} catch (SQLException e) {
			System.err.println("Perfect Day / Trophy Hunt: Korrekturen nicht lesbar: " + e.getMessage());
		}
		return summe;
	}

	private static String korrekturName(String tag) {
		String name = DBUtil.getValueFromSQL("SELECT name FROM legend_spieler WHERE player_tag = ?", String.class, tag);
		if (name == null) {
			name = DBUtil.getValueFromSQL(
					"SELECT name FROM legend_korrektur WHERE player_tag = ? AND name IS NOT NULL ORDER BY id DESC LIMIT 1",
					String.class, tag);
		}
		return name != null ? name : tag;
	}

	// ------------------------------------------------------------------
	// Trophy Hunt: Auswertung nach der Woche
	// ------------------------------------------------------------------

	static void huntPruefen(Instant jetzt) {
		YearMonth m = YearMonth.from(jetzt.atZone(BERLIN));
		for (YearMonth k : List.of(m, m.minusMonths(1))) {
			Instant ende = LegendRegeln.huntEnde(k);
			if (k.isBefore(ERSTER_HUNT) || jetzt.isBefore(ende.plus(Duration.ofMinutes(35)))
					|| jetzt.isAfter(ende.plus(Duration.ofDays(2)))) {
				continue;
			}
			if (!gemeldet("trophyhunt", k.toString())) {
				huntAuswerten(k);
			}
		}
	}

	record HuntErgebnis(List<LegendTexte.HuntZeile> zeilen, List<Integer> sieger, List<String> tags) {
	}

	static HuntErgebnis huntErgebnis(YearMonth monat) {
		LocalDate von = LegendRegeln.legendTag(LegendRegeln.huntStart(monat));
		LocalDate bis = von.plusDays(6);
		record Roh(String tag, String name, int pokale, boolean unvoll) {
		}
		Map<String, Roh> roh = new LinkedHashMap<>();
		String sql = "SELECT player_tag, (array_agg(name ORDER BY legend_tag DESC))[1], SUM(angriffspokale), BOOL_OR(unvollstaendig) "
				+ "FROM legend_tageswert WHERE liga = ? AND legend_tag BETWEEN ? AND ? GROUP BY player_tag";
		try (PreparedStatement ps = Connection.getConnection().prepareStatement(sql)) {
			ps.setString(1, LegendRegeln.LIGA_2);
			ps.setObject(2, von);
			ps.setObject(3, bis);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					roh.put(rs.getString(1), new Roh(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getBoolean(4)));
				}
			}
		} catch (SQLException e) {
			System.err.println("Trophy Hunt: Wertung nicht lesbar: " + e.getMessage());
			return null;
		}
		for (Map.Entry<String, Integer> k : korrekturen("trophyhunt", monat.toString()).entrySet()) {
			Roh r = roh.get(k.getKey());
			roh.put(k.getKey(), r == null ? new Roh(k.getKey(), korrekturName(k.getKey()), k.getValue(), false)
					: new Roh(r.tag(), r.name(), r.pokale() + k.getValue(), r.unvoll()));
		}
		List<Roh> liste = new ArrayList<>();
		for (Roh r : roh.values()) {
			if (r.pokale() > 0) {
				liste.add(r);
			}
		}
		liste.sort((a, b) -> a.pokale() != b.pokale() ? Integer.compare(b.pokale(), a.pokale())
				: String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())));
		if (liste.size() > 10) {
			liste = new ArrayList<>(liste.subList(0, 10));
		}

		// Der Gewinner des Vormonats kann nicht gewinnen – als Person, also auch nicht
		// mit einem anderen Account.
		String sperrTag = DBUtil.getValueFromSQL("SELECT player_tag FROM trophyhunt_sieger WHERE monat = ?", String.class,
				monat.minusMonths(1).toString());
		String sperrDiscord = DBUtil.getValueFromSQL("SELECT discord_id FROM trophyhunt_sieger WHERE monat = ?",
				String.class, monat.minusMonths(1).toString());
		List<LegendTexte.HuntZeile> zeilen = new ArrayList<>();
		List<String> tags = new ArrayList<>();
		List<Integer> sieger = new ArrayList<>();
		for (int i = 0; i < liste.size(); i++) {
			Roh r = liste.get(i);
			String discord = discordVon(r.tag());
			boolean gesperrt = r.tag().equals(sperrTag) || (sperrDiscord != null && sperrDiscord.equals(discord));
			zeilen.add(new LegendTexte.HuntZeile(r.name(), r.pokale(), gesperrt, r.unvoll()));
			tags.add(r.tag());
			if (!gesperrt && (sieger.isEmpty() || zeilen.get(sieger.get(0)).pokale() == r.pokale())) {
				sieger.add(i);
			}
		}
		return new HuntErgebnis(zeilen, sieger, tags);
	}

	static void huntAuswerten(YearMonth monat) {
		HuntErgebnis e = huntErgebnis(monat);
		if (e == null) {
			return;
		}
		if (e.zeilen().isEmpty()) {
			System.out.println("Trophy Hunt " + monat + ": keine Angriffe erfasst, nichts gepostet");
			markieren("trophyhunt", monat.toString(), null);
			return;
		}
		LocalDate von = LegendRegeln.legendTag(LegendRegeln.huntStart(monat));
		LocalDate bis = von.plusDays(6);
		List<String> texte = LegendTexte.trophyHunt(e.zeilen(), e.sieger(), von, bis, bis.plusDays(7));
		if (posten("trophyhunt", monat.toString(), HUNT_KANAL, texte) && e.sieger().size() == 1 && !TROCKEN) {
			int i = e.sieger().get(0);
			String tag = e.tags().get(i);
			DBUtil.executeUpdate("INSERT INTO trophyhunt_sieger (monat, player_tag, discord_id, name, pokale) VALUES (?,?,?,?,?) "
					+ "ON CONFLICT (monat) DO NOTHING", monat.toString(), tag, discordVon(tag), e.zeilen().get(i).name(),
					e.zeilen().get(i).pokale());
		}
	}

	private static String discordVon(String tag) {
		return DBUtil.getValueFromSQL("SELECT discord_id FROM players WHERE coc_tag = ?", String.class, tag);
	}

	// ------------------------------------------------------------------
	// Posten (genau einmal)
	// ------------------------------------------------------------------

	static boolean gemeldet(String art, String schluessel) {
		Long n = DBUtil.getValueFromSQL("SELECT count(*) FROM legend_meldung WHERE art = ? AND schluessel = ?",
				Long.class, art, schluessel);
		return n != null && n > 0;
	}

	private static boolean markieren(String art, String schluessel, String nachrichten) {
		var r = DBUtil.executeUpdate("INSERT INTO legend_meldung (art, schluessel, nachrichten) VALUES (?,?,?) "
				+ "ON CONFLICT DO NOTHING", art, schluessel, nachrichten);
		return r != null && r.getSecond() != null && r.getSecond() > 0;
	}

	/** @return true, wenn (wirklich) gepostet wurde. */
	static boolean posten(String art, String schluessel, String kanalId, List<String> texte) {
		if (TROCKEN) {
			if (trockenGemeldet.add(art + ":" + schluessel)) {
				System.out.println("[TROCKEN] " + art + " " + schluessel + " -> Kanal " + kanalId + ":\n"
						+ String.join("\n---\n", texte));
			}
			return false;
		}
		TextChannel kanal = Bot.getJda().getTextChannelById(kanalId);
		if (kanal == null) {
			System.err.println("Perfect Day / Trophy Hunt: Kanal " + kanalId + " nicht gefunden");
			return false;
		}
		// Erst beanspruchen, dann senden: ein Neustart mitten im Senden darf nicht doppelt posten.
		if (!markieren(art, schluessel, null)) {
			return false;
		}
		List<String> ids = new ArrayList<>();
		for (String text : texte) {
			try {
				ids.add(kanal.sendMessage(text).setAllowedMentions(Collections.emptyList()).complete().getId());
			} catch (Exception e) {
				System.err.println("Perfect Day / Trophy Hunt: Post " + art + " " + schluessel + " gescheitert: " + e);
				break;
			}
		}
		DBUtil.executeUpdate("UPDATE legend_meldung SET nachrichten = ? WHERE art = ? AND schluessel = ?",
				String.join(",", ids), art, schluessel);
		System.out.println("Perfect Day / Trophy Hunt: " + art + " " + schluessel + " gepostet (" + ids.size()
				+ " Nachricht(en))");
		return !ids.isEmpty();
	}

	// ------------------------------------------------------------------
	// Rolle „Trophyhunt“
	// ------------------------------------------------------------------

	static void rollePruefen(Instant jetzt) {
		Instant heute = LegendRegeln.legendTag(jetzt).atTime(5, 30).toInstant(java.time.ZoneOffset.UTC);
		if (jetzt.isBefore(heute) || !letzterRollenabgleich.isBefore(heute)) {
			return;
		}
		if (ROLLE.isEmpty()) {
			if (!rolleFehltGemeldet) {
				rolleFehltGemeldet = true;
				System.out.println("Trophyhunt-Rolle: TROPHYHUNT_ROLLE_ID nicht gesetzt, kein Abgleich");
			}
			return;
		}
		// Nur auf frischer Grundlage abgleichen, sonst nähme ein API-Ausfall allen die Rolle.
		if (Duration.between(letzteErfolgreicheSuche, jetzt).compareTo(Duration.ofHours(1)) > 0) {
			return;
		}
		letzterRollenabgleich = jetzt;
		rolleAbgleichen();
	}

	/** Discord-IDs, deren verknüpfte LOST-Accounts gerade (mindestens einer) in Legend II sind. */
	static Set<String> trophyhuntBerechtigte() {
		return new HashSet<>(DBUtil.getArrayListFromSQL(
				"SELECT DISTINCT p.discord_id FROM players p JOIN legend_spieler l ON l.player_tag = p.coc_tag "
						+ "WHERE l.liga = ? AND l.gesehen > now() - interval '6 hours' AND p.discord_id IS NOT NULL",
				String.class, LegendRegeln.LIGA_2));
	}

	static void rolleAbgleichen() {
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		Role rolle = guild == null ? null : guild.getRoleById(ROLLE);
		if (rolle == null) {
			if (!rolleFehltGemeldet) {
				rolleFehltGemeldet = true;
				System.err.println("Trophyhunt-Rolle " + ROLLE + " auf dem Server nicht gefunden, kein Abgleich");
			}
			return;
		}
		if (!guild.getSelfMember().canInteract(rolle)) {
			System.err.println("Trophyhunt-Rolle: der Bot steht nicht über der Rolle, kein Abgleich");
			return;
		}
		Set<String> soll = trophyhuntBerechtigte();
		List<Member> weg = new ArrayList<>();
		Set<String> haben = new HashSet<>();
		for (Member m : guild.getMembersWithRoles(rolle)) {
			haben.add(m.getId());
			if (!soll.contains(m.getId())) {
				weg.add(m);
			}
		}
		List<Member> dazu = new ArrayList<>();
		for (String id : soll) {
			Member m = guild.getMemberById(id);
			if (m != null && !haben.contains(id)) {
				dazu.add(m);
			}
		}
		System.out.println("Trophyhunt-Rolle: " + soll.size() + " berechtigt, +" + dazu.size() + " −" + weg.size()
				+ (TROCKEN ? " (TROCKEN, nichts geändert)" : ""));
		if (TROCKEN) {
			return;
		}
		for (Member m : dazu) {
			guild.addRoleToMember(m, rolle).reason("Trophyhunt: Account in Legend League II").queue(null,
					f -> System.err.println("Trophyhunt-Rolle an " + m.getId() + " gescheitert: " + f.getMessage()));
		}
		for (Member m : weg) {
			guild.removeRoleFromMember(m, rolle).reason("Trophyhunt: kein Account mehr in Legend League II").queue(null,
					f -> System.err.println("Trophyhunt-Rolle von " + m.getId() + " gescheitert: " + f.getMessage()));
		}
	}
}
