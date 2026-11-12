package lostmanager.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import javax.annotation.Nonnull;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Jonas' Direktnachrichten an den LOST Manager als zweiter Chatweg (seit
 * 12.11.2026, Jonas: "du kannst mich zwar per Discord DM anschreiben, liest
 * meine Antworten aber nicht").
 *
 * Schreibt Jonas dem Bot eine DM, haengt diese Klasse eine Zeile an
 * ~/lost/discord-dm/klingel.log. discord-dm.path startet daraufhin
 * ~/lost/discord-dm/dm-lauf.py, das die Nachricht per REST holt und in die
 * Warteschlange der WhatsApp-Bruecke gibt. Geantwortet wird von dort, nicht
 * von hier.
 *
 * Die Zeile ist nur ein Signal: Uhrzeit und Nachrichten-ID, kein Text. Was
 * Jonas geschrieben hat, steht damit in keinem Log.
 *
 * Getrennt von der {@link Klingel} (Keksis Weg): die bleibt, wie sie ist.
 * Jonas steht nicht in deren Erlaubt-Liste, Keksi nicht in dieser Klasse –
 * die beiden ueberschneiden sich nicht.
 *
 * Wer hier durchkommt, dessen Nachricht wird ein Auftrag mit vollen Rechten.
 * Deshalb ist die ID eine Konstante im Code und nicht ueber die Umgebung
 * erweiterbar; dm-lauf.py prueft den Autor ein zweites Mal.
 */
public class JonasDm extends ListenerAdapter {

	/** Jonas. Genau diese eine ID, bewusst nicht konfigurierbar. */
	static final String JONAS = "326398089298444290";

	/** An, solange JONAS_DM_AUS nicht gesetzt ist. */
	static final boolean AUS = "1".equals(System.getenv("JONAS_DM_AUS"));

	/** Wohin das Signal geht; discord-dm.path beobachtet diese Datei. */
	private static final Path DATEI = Paths.get(
			System.getenv().getOrDefault("JONAS_DM_DATEI",
					System.getProperty("user.home") + "/lost/discord-dm/klingel.log"));

	private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
			.withZone(ZoneOffset.UTC);

	/**
	 * Ob diese Nachricht ein Signal ausloest: eingeschaltet, Direktnachricht,
	 * kein Bot, und der Absender ist genau Jonas.
	 */
	static boolean betrifft(boolean aus, boolean ausGuild, boolean istBot, String userId) {
		return !aus && !ausGuild && !istBot && JONAS.equals(userId);
	}

	/** Die Signalzeile: Zeit (UTC) und Nachrichten-ID, sonst nichts. */
	static String zeile(String nachrichtId, Instant zeit) {
		// Die ID kommt von Discord und ist eine Zahl; trotzdem alles andere
		// wegwerfen, damit hier nie ein Zeilenumbruch landen kann.
		final String id = nachrichtId == null ? "" : nachrichtId.replaceAll("[^0-9]", "");
		return ZEIT.format(zeit) + " | DM Jonas, id " + id;
	}

	@Override
	public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
		try {
			if (!betrifft(AUS, event.isFromGuild(), event.getAuthor().isBot(), event.getAuthor().getId())) {
				return;
			}
			final Path ordner = DATEI.getParent();
			if (ordner != null) {
				Files.createDirectories(ordner);
			}
			Files.writeString(DATEI, zeile(event.getMessageId(), Instant.now()) + "\n",
					StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException e) {
			// Nie werfen: ein kaputtes Signal darf den Bot nicht stoeren.
			System.err.println("JonasDm: konnte " + DATEI + " nicht schreiben: " + e.getMessage());
		}
	}
}
