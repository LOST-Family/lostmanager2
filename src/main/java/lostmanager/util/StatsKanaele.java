package lostmanager.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;

import lostmanager.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

/**
 * Die Zähler-Kanäle in der Kategorie STATS oben auf LOST Family.
 *
 * Das hat bis Oktober 2025 ClashKing gemacht. Seitdem ist der Bot offline, und
 * die Namen stehen still. Die Regeln hier stammen aus dem Dyno-Log
 * (1405817410069467186), in dem jede Umbenennung von ClashKing protokolliert
 * ist. Ausgewertet wurden rund 5800 Umbenennungen aus September und Oktober 2025
 * (Jonas, 06.10.2026: „recherchieren wie wann die daten sich da oben ändern und
 * das im lost manager dementsprechend nachbauen“):
 *
 * <ul>
 * <li><b>EOS</b>: Saisonende, letzter Montag im Monat um 05:00 UTC (29.09.2025,
 * 27.10.2025).</li>
 * <li><b>CWL</b>: ab dem 1. um 08:00 UTC „CWL ends“ mit Ziel 11. um 08:00 UTC.
 * Schon um Mitternacht des 11. schaltet es auf „CWL in“ zum nächsten Monat um,
 * genau wie ClashKing es tat.</li>
 * <li><b>CG</b> (Clanspiele): vom 22. um 08:00 UTC bis zum 28. um 08:00 UTC.</li>
 * <li><b>Raids</b>: Clanstadt-Raids, Freitag 07:00 UTC bis Montag 07:00 UTC.</li>
 * <li><b>Members</b>: Mitgliederzahl des Discord-Servers.</li>
 * </ul>
 *
 * Das Format ist dasselbe wie bei ClashKing: ab einem Tag „5D 13H“, darunter
 * „23H 47M“, unter einer Stunde „45M“. Abgerundet wird immer. Aktualisiert wird
 * alle 15 Minuten, wie bei ClashKing. Discord erlaubt je Kanal nur zwei
 * Umbenennungen in zehn Minuten. Umbenannt wird nur, wenn sich der Name
 * tatsächlich ändert, im Tagesbereich also einmal pro Stunde.
 *
 * CR-EOS gehört nicht hierher, das pflegt der CR Manager.
 */
public class StatsKanaele {

	private static final String MEMBERS = env("STATS_KANAL_MEMBERS", "1207343909555019807");
	private static final String EOS = env("STATS_KANAL_EOS", "1232404469065715844");
	private static final String CWL = env("STATS_KANAL_CWL", "1232404854123528272");
	private static final String CG = env("STATS_KANAL_CG", "1232404856871059599");
	private static final String RAIDS = env("STATS_KANAL_RAIDS", "1232404859915993190");

	private static String env(String name, String standard) {
		String wert = System.getenv(name);
		return (wert != null && !wert.isEmpty()) ? wert : standard;
	}

	/** Ein Durchlauf: alle fünf Namen berechnen und, wo nötig, umbenennen. */
	public static void aktualisiere() {
		if (Bot.getJda() == null) {
			return;
		}
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		if (guild == null) {
			return;
		}
		ZonedDateTime jetzt = ZonedDateTime.now(ZoneOffset.UTC);
		umbenennen(guild, MEMBERS, "Members: " + guild.getMemberCount());
		umbenennen(guild, EOS, eosName(jetzt));
		umbenennen(guild, CWL, cwlName(jetzt));
		umbenennen(guild, CG, cgName(jetzt));
		umbenennen(guild, RAIDS, raidsName(jetzt));
	}

	private static void umbenennen(Guild guild, String kanalId, String name) {
		GuildChannel kanal = guild.getGuildChannelById(kanalId);
		if (kanal == null || kanal.getName().equals(name)) {
			return;
		}
		kanal.getManager().setName(name).queue(null,
				fehler -> System.err.println("Stats-Kanal " + kanalId + " nicht umbenannt: " + fehler.getMessage()));
	}

	static String eosName(ZonedDateTime jetzt) {
		ZonedDateTime ende = letzterMontag(jetzt);
		if (!jetzt.isBefore(ende)) {
			ende = letzterMontag(jetzt.plusMonths(1));
		}
		return "EOS in " + dauer(jetzt, ende);
	}

	private static ZonedDateTime letzterMontag(ZonedDateTime im) {
		return im.with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)).withHour(5).withMinute(0).withSecond(0)
				.withNano(0);
	}

	static String cwlName(ZonedDateTime jetzt) {
		ZonedDateTime start = monatstag(jetzt, 1);
		ZonedDateTime ende = monatstag(jetzt, 11);
		// Wie bei ClashKing: der Countdown läuft auf den 11. um 08:00, umgeschaltet
		// auf „CWL in“ wird aber schon um Mitternacht des 11. (bei 8H Rest).
		if (!jetzt.isBefore(start) && jetzt.getDayOfMonth() <= 10) {
			return "CWL ends " + dauer(jetzt, ende);
		}
		ZonedDateTime naechster = jetzt.isBefore(start) ? start : monatstag(jetzt.plusMonths(1), 1);
		return "CWL in " + dauer(jetzt, naechster);
	}

	static String cgName(ZonedDateTime jetzt) {
		ZonedDateTime start = monatstag(jetzt, 22);
		ZonedDateTime ende = monatstag(jetzt, 28);
		if (!jetzt.isBefore(start) && jetzt.isBefore(ende)) {
			return (Duration.between(jetzt, ende).toHours() < 1 ? "CG ends in " : "CG ends ") + dauer(jetzt, ende);
		}
		ZonedDateTime naechster = jetzt.isBefore(start) ? start : monatstag(jetzt.plusMonths(1), 22);
		return "CG in " + dauer(jetzt, naechster);
	}

	static String raidsName(ZonedDateTime jetzt) {
		// Der Raid dieser Woche: Freitag 07:00 bis Montag 07:00 UTC. Montag vor
		// 07:00 gehört noch zum Raid, der am Freitag davor begann.
		ZonedDateTime start = jetzt.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)).withHour(7)
				.withMinute(0).withSecond(0).withNano(0);
		if (start.isAfter(jetzt)) {
			start = start.minusWeeks(1);
		}
		ZonedDateTime ende = start.plusDays(3);
		if (jetzt.isBefore(ende)) {
			return (Duration.between(jetzt, ende).toHours() < 1 ? "Raids end in " : "Raids end ")
					+ dauer(jetzt, ende);
		}
		return "Raids in " + dauer(jetzt, start.plusWeeks(1));
	}

	private static ZonedDateTime monatstag(ZonedDateTime im, int tag) {
		return im.withDayOfMonth(tag).withHour(8).withMinute(0).withSecond(0).withNano(0);
	}

	/** „5D 13H“, „23H 47M“ oder „45M“, abgerundet wie bei ClashKing. */
	static String dauer(ZonedDateTime von, ZonedDateTime bis) {
		long minuten = Math.max(0, Duration.between(von, bis).toMinutes());
		long tage = minuten / (24 * 60);
		long stunden = (minuten / 60) % 24;
		long rest = minuten % 60;
		if (tage > 0) {
			return tage + "D " + stunden + "H";
		}
		if (stunden > 0) {
			return stunden + "H " + rest + "M";
		}
		return rest + "M";
	}
}
