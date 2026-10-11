package lostmanager.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Tuerklingel: schreibt eine Zeile, wenn ein Berechtigter dem Bot eine
 * Direktnachricht schickt.
 *
 * Seit dem 11.10.2026 im LOST Manager statt im Ticket-Bot (Jonas: "für Claude
 * Sachen nurnoch Lost Manager"). <b>Standardmaessig aus</b>: Keksi braucht sie
 * gerade nicht, Jonas gibt den Call. Einschalten mit KLINGEL_AN=1 in der
 * Umgebung des Dienstes. Der Rueckweg ist ~/lost/dm-lm.py.
 *
 * Warum es das gibt: Keksis Pull Requests und Issues sollen den Assistenten
 * erreichen, ohne dass Jonas Bescheid sagt. Ueber GitHub allein dauert das bis
 * zur naechsten stuendlichen Wache. Der Bot haengt ohnehin an einer offenen
 * Gateway-Verbindung und erfaehrt eine DM in Echtzeit — er muss sie nur
 * weiterreichen.
 *
 * Bewusst schmal gehalten: diese Klasse startet nichts und fuehrt nichts aus.
 * Sie haengt eine Zeile an eine Datei. Alles Weitere entscheidet der Assistent,
 * der diese Datei beobachtet. Damit ist die Angriffsflaeche einer fremden
 * Nachricht genau eine Textzeile in einem Log.
 */
public class Klingel extends ListenerAdapter {

	/** Aus, solange KLINGEL_AN nicht gesetzt ist. */
	static final boolean AN = "1".equals(System.getenv("KLINGEL_AN"));

	/** Wer klingeln darf. Komma-getrennte Discord-IDs, sonst nur Keksi. */
	private static final Set<String> ERLAUBT = erlaubte();

	/** Wohin die Zeile geht. Der Assistent haengt mit tail -F an dieser Datei. */
	private static final Path DATEI = Paths.get(
			System.getenv().getOrDefault("KLINGEL_DATEI",
					System.getProperty("user.home") + "/lost/klingel.log"));

	/**
	 * Schutz vor einer Flut — aber keiner, der Nachrichten verschluckt.
	 *
	 * Die erste Fassung entprellte zwei Minuten lang und liess alles dazwischen
	 * lautlos fallen. Am 11.09.2026 sind so zwei echte Nachrichten verlorengegangen,
	 * ohne dass der Absender auch nur die Eingangsbestaetigung sah. Eine Klingel,
	 * die Nachrichten frisst, ist schlimmer als gar keine: man verlaesst sich auf
	 * sie.
	 *
	 * Jetzt klingelt jede Nachricht. Nur wer in einer Minute mehr als
	 * HOECHSTENS_JE_MINUTE schickt, bekommt eine einzelne Sammelzeile statt vieler
	 * — und auch die sagt, dass da noch etwas liegt.
	 */
	static final int HOECHSTENS_JE_MINUTE = 8;

	private static final long FENSTER_MS = 60_000L;

	/** Was mit dieser Nachricht geschehen soll. */
	enum Entscheidung {
		/** Normal klingeln. */
		KLINGELN,
		/** Einmalige Sammelzeile: ab hier wird unterdrueckt. */
		SAMMELN,
		/** Schon gemeldet, nichts weiter schreiben. */
		STILL
	}

	/** Zaehler je Nutzer: [Fensterbeginn, Anzahl im Fenster]. */
	private static final ConcurrentHashMap<String, long[]> FENSTER = new ConcurrentHashMap<>();

	private static final DateTimeFormatter UHRZEIT = DateTimeFormatter.ofPattern("HH:mm");

	private static Set<String> erlaubte() {
		final String roh = System.getenv().getOrDefault("KLINGEL_ERLAUBT", "362260317071343630");
		return Arrays.stream(roh.split(",")).map(String::trim).filter(s -> !s.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	static boolean darfKlingeln(String userId) {
		return userId != null && ERLAUBT.contains(userId);
	}

	/**
	 * Kuerzt die Nachricht auf ein Signal.
	 *
	 * Zeilenumbrueche muessen weg: der Beobachter liest zeilenweise, ein
	 * Umbruch im Text waere sonst ein zweites, gefaelschtes Ereignis. Aus
	 * demselben Grund fliegen Steuerzeichen raus.
	 */
	static String kurzfassung(String text) {
		if (text == null) {
			return "(leer)";
		}
		final StringBuilder sb = new StringBuilder(text.length());
		for (final char c : text.toCharArray()) {
			sb.append(Character.isISOControl(c) ? ' ' : c);
		}
		final String sauber = sb.toString().replaceAll("\\s+", " ").trim();
		if (sauber.isEmpty()) {
			return "(ohne Text)";
		}
		return sauber.length() <= 120 ? sauber : sauber.substring(0, 117) + "...";
	}

	/**
	 * Was in der Nachricht steckt — auch wenn kein Text drin ist.
	 *
	 * Ein Sticker oder ein Bild ohne Begleittext ergab sonst nur "(ohne Text)",
	 * und damit weiss der Empfaenger nicht, ob die Leitung klemmt oder ob
	 * wirklich nichts geschrieben wurde. Genau das ist bei der ersten echten
	 * Nachricht am 11.09.2026 passiert.
	 */
	static String inhalt(Message nachricht) {
		final String text = kurzfassung(nachricht.getContentDisplay());
		final StringBuilder dazu = new StringBuilder();
		if (!nachricht.getAttachments().isEmpty()) {
			dazu.append(nachricht.getAttachments().size()).append(" Anhang/Anhaenge");
		}
		if (!nachricht.getStickers().isEmpty()) {
			if (dazu.length() > 0) {
				dazu.append(", ");
			}
			dazu.append("Sticker ").append(nachricht.getStickers().get(0).getName());
		}
		if (!nachricht.getEmbeds().isEmpty()) {
			if (dazu.length() > 0) {
				dazu.append(", ");
			}
			dazu.append(nachricht.getEmbeds().size()).append(" Einbettung(en)");
		}
		if (dazu.length() == 0) {
			return text;
		}
		return "(ohne Text)".equals(text) ? "[" + dazu + "]" : text + " [" + dazu + "]";
	}

	static Entscheidung entscheide(String userId, long jetzt) {
		final long[] stand = FENSTER.compute(userId, (k, alt) -> {
			if (alt == null || jetzt - alt[0] >= FENSTER_MS) {
				return new long[] { jetzt, 1L };
			}
			return new long[] { alt[0], alt[1] + 1L };
		});
		if (stand[1] <= HOECHSTENS_JE_MINUTE) {
			return Entscheidung.KLINGELN;
		}
		return stand[1] == HOECHSTENS_JE_MINUTE + 1L ? Entscheidung.SAMMELN : Entscheidung.STILL;
	}

	static String zeile(String wer, String was, LocalTime zeit) {
		return UHRZEIT.format(zeit) + " | DM " + wer + ": " + kurzfassung(was);
	}

	@Override
	public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
		if (!AN || event.isFromGuild() || event.getAuthor().isBot()) {
			return;
		}
		final String id = event.getAuthor().getId();
		if (!darfKlingeln(id)) {
			return;
		}
		final Entscheidung was = entscheide(id, System.currentTimeMillis());
		if (was == Entscheidung.STILL) {
			return;
		}

		final String wer = event.getAuthor().getEffectiveName();
		final String inhalt = was == Entscheidung.SAMMELN
				? "schickt gerade sehr viel — ab hier ungelesen, bitte im DM-Verlauf nachsehen"
				: inhalt(event.getMessage());
		try {
			Files.writeString(DATEI, zeile(wer, inhalt, LocalTime.now()) + "\n",
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			System.err.println("Klingel: konnte " + DATEI + " nicht schreiben: " + e.getMessage());
			return;
		}

		// Ohne Rueckmeldung schreibt er ins Leere. Bewusst vage formuliert:
		// laeuft gerade keine Sitzung, liegt die Zeile bis zur naechsten Wache.
		event.getChannel()
				.sendMessage("Angekommen — ich hab's weitergereicht. Antwort kommt hier oder direkt am Issue/PR.")
				.queue(null, fehler -> System.err.println("Klingel: Antwort ging nicht raus: " + fehler.getMessage()));
	}
}
