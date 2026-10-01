package lostmanager.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.json.JSONObject;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Ruft Claude auf dem Homeserver in einen Kanal, wenn Jonas den Bot erwaehnt.
 *
 * Nur eine Zeile JSON an eine Datei - alles Weitere (Verlauf lesen, antworten,
 * die Betreuung beenden) macht ~/lost/betreuung/betreuer.py, angestossen von
 * einer systemd-Path-Unit. Erwaehnungen von allen anderen werden ignoriert: wer
 * den Bot pingt, bekommt damit keinen Zugriff auf den Server.
 */
public class Betreuungsruf extends ListenerAdapter {

	private static final String JONAS = env("BETREUUNG_NUTZER", "326398089298444290");
	private static final Path DATEI = Path.of(env("BETREUUNG_RUFE_DATEI",
			System.getProperty("user.home") + "/lost/betreuung/rufe.jsonl"));

	private static String env(String name, String standard) {
		String wert = System.getenv(name);
		return (wert != null && !wert.isEmpty()) ? wert : standard;
	}

	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (!event.isFromGuild() || !event.getAuthor().getId().equals(JONAS))
			return;
		Message m = event.getMessage();
		if (!m.getMentions().isMentioned(event.getJDA().getSelfUser(), Message.MentionType.USER))
			return;

		JSONObject ruf = new JSONObject()
				.put("guild", event.getGuild().getId())
				.put("kanal", event.getChannel().getId())
				.put("kanalname", event.getChannel().getName())
				.put("nachricht", m.getId())
				.put("text", m.getContentRaw())
				.put("zeit", m.getTimeCreated().toString());
		try {
			Files.createDirectories(DATEI.getParent());
			Files.writeString(DATEI, ruf.toString() + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			System.err.println("Betreuungsruf: konnte nicht schreiben: " + e.getMessage());
		}
	}
}
