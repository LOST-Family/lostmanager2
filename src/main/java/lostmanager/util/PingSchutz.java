package lostmanager.util;

import java.util.EnumSet;

import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.utils.messages.MessageRequest;

/**
 * Der Bot läuft mit Administrator und schreibt Spielernamen (frei wählbar im
 * Spiel, auch mit „@“) als Klartext in Kanäle – Tagesberichte, Spender,
 * Raid-Erinnerungen, Roster. Ein Name „@everyone“ würde dort den ganzen Server
 * pingen. Kein Befehl und kein gespeicherter Text will @everyone/@here
 * (geprüft 21.11.2026: Code und alle Textspalten der Datenbank), deshalb sind
 * die beiden für jede Nachricht des Bots abgeschaltet. Nutzer- und
 * Rollen-Pings bleiben erlaubt (Raid-, Roster-, Event-Pings brauchen sie).
 */
public final class PingSchutz {

	public static final EnumSet<MentionType> ERLAUBT = EnumSet.complementOf(
			EnumSet.of(MentionType.EVERYONE, MentionType.HERE));

	private PingSchutz() {
	}

	/** Vor dem Bau des JDA-Objekts aufrufen. */
	public static void setzeStandard() {
		MessageRequest.setDefaultMentions(ERLAUBT);
	}
}
