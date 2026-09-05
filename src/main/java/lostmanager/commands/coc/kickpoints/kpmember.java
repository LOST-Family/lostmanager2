package lostmanager.commands.coc.kickpoints;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import lostmanager.Bot;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Kickpoint;
import lostmanager.datawrapper.Player;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class kpmember extends ListenerAdapter {

	private static final String TITLE = "Aktive Kickpunkte";
	private static final String BUTTON_PREFIX = "kpmember_";

	// Platz, den der "und N weitere"-Hinweis am Ende der Kurzfassung braucht
	private static final int TRUNCATION_RESERVE = 80;

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");
	private static final DateTimeFormatter FOOTER_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpmember"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			OptionMapping playerOption = event.getOption("player");

			if (playerOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(TITLE,
						"Der Parameter Player ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			display(event.getHook(), playerOption.getAsString());
		}, "KpmemberCommand-" + event.getUser().getId()).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpmember"))
			return;

		new Thread(() -> {

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);

			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
		}, "KpmemberAutocomplete-" + event.getUser().getId()).start();
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith(BUTTON_PREFIX))
			return;

		event.deferEdit().queue();

		String playertag = id.substring(BUTTON_PREFIX.length());

		event.getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(TITLE, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		new Thread(() -> display(event.getHook(), playertag), "KpmemberButton-" + event.getUser().getId()).start();
	}

	/**
	 * Lädt die Kickpunkte des Spielers und schreibt sie in die bereits
	 * zurückgestellte Antwort.
	 */
	private void display(InteractionHook hook, String playertag) {
		try {
			Player p = new Player(playertag);
			Clan c = p.getClanDB();

			if (c == null) {
				hook.editOriginalEmbeds(MessageUtil.buildEmbed(TITLE,
						"Dieser Spieler existiert nicht oder ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (!c.ExistsDB()) {
				hook.editOriginalEmbeds(
						MessageUtil.buildEmbed(TITLE, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String desc = buildDescription(p, c);

			Button refreshButton = Button.secondary(BUTTON_PREFIX + playertag, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));

			String formatiert = ZonedDateTime.now(BERLIN).format(FOOTER_FORMAT);

			hook.editOriginalEmbeds(MessageUtil.buildEmbed(TITLE, desc, MessageUtil.EmbedType.INFO,
					"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();
		} catch (RuntimeException e) {
			System.err.println("kpmember fehlgeschlagen für " + playertag + ": " + e);
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(TITLE,
					"Beim Laden der Kickpunkte ist ein Fehler aufgetreten.", MessageUtil.EmbedType.ERROR)).queue();
		}
	}

	/**
	 * Baut die Beschreibung des Embeds. Normalerweise wird die ausführliche
	 * Auflistung verwendet; passt die nicht in ein Embed (Discord erlaubt nur
	 * {@link MessageEmbed#DESCRIPTION_MAX_LENGTH} Zeichen, sonst wird die Antwort
	 * gar nicht erst abgeschickt), wird auf eine Kurzfassung umgeschaltet, die pro
	 * Kickpunkt nur noch den Grund und die Restlaufzeit zeigt.
	 */
	private static String buildDescription(Player p, Clan c) {
		ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

		StringBuilder header = new StringBuilder("Aktive Kickpunkte von " + MessageUtil.unformat(p.getInfoStringDB())
				+ " in " + c.getInfoString() + ":\n");
		if (!activekps.isEmpty()) {
			long totalkps = 0;
			for (Kickpoint kpi : activekps) {
				totalkps += kpi.getAmount();
			}
			header.append("**Gesamt: ").append(totalkps).append("/").append(c.getMaxKickpoints())
					.append(" Kickpunkte**");
		} else {
			header.append("Dieser Spieler hat keine aktiven Kickpunkte.\n");
		}

		String footer = "\n**Gesamtanzahl (Vergangene und aktuelle Kickpunkte):**\n" + p.getTotalKickpoints();

		String detailed = header + renderDetailed(activekps) + footer;
		if (detailed.length() <= MessageEmbed.DESCRIPTION_MAX_LENGTH) {
			return detailed;
		}

		String compact = header + renderCompact(activekps, header.length() + footer.length()) + footer;
		if (compact.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
			// Notfallbremse, falls schon Kopf und Fuß das Limit sprengen: lieber
			// abgeschnitten als gar keine Antwort
			int cut = MessageEmbed.DESCRIPTION_MAX_LENGTH;
			if (Character.isHighSurrogate(compact.charAt(cut - 1))) {
				cut--;
			}
			compact = compact.substring(0, cut);
		}
		return compact;
	}

	/** Ausführliche Auflistung mit allen Angaben zu jedem Kickpunkt. */
	private static String renderDetailed(List<Kickpoint> kickpoints) {
		StringBuilder sb = new StringBuilder();
		for (Kickpoint kp : kickpoints) {
			sb.append("\n");
			sb.append("### Kickpunkt #").append(kp.getID()).append(":\n");
			sb.append("Grund: ").append(reason(kp)).append("\n");
			sb.append("Anzahl Kickpunkte: ").append(kp.getAmount()).append("\n");
			sb.append("Erhalten am: ").append(kp.getDate().atZoneSameInstant(BERLIN).format(DATE_FORMAT)).append("\n");
			sb.append("Läuft ab in: ").append(remainingRuntime(kp)).append("\n");
			sb.append("Aktiv seit: ").append(formatDuration(Duration.between(kp.getDate(), OffsetDateTime.now())))
					.append("\n");
			sb.append("Erstellt: von ").append(givenByName(kp)).append(" am ")
					.append(kp.getGivenDate().atZoneSameInstant(BERLIN).format(DATE_TIME_FORMAT)).append("\n");
		}
		return sb.toString();
	}

	/**
	 * Kurzfassung: nur Grund und Restlaufzeit pro Kickpunkt. Passen selbst die
	 * nicht alle in das Embed, werden die restlichen nur noch gezählt.
	 */
	private static String renderCompact(List<Kickpoint> kickpoints, int lengthOfHeaderAndFooter) {
		int budget = MessageEmbed.DESCRIPTION_MAX_LENGTH - lengthOfHeaderAndFooter - TRUNCATION_RESERVE;

		StringBuilder sb = new StringBuilder();
		int shown = 0;
		for (Kickpoint kp : kickpoints) {
			String line = "\n- " + reason(kp).replace("\n", " ") + " — Läuft ab in: " + remainingRuntime(kp);
			if (sb.length() + line.length() > budget) {
				sb.append("\n*… und ").append(kickpoints.size() - shown).append(" weitere*");
				break;
			}
			sb.append(line);
			shown++;
		}
		sb.append("\n");
		return sb.toString();
	}

	private static String reason(Kickpoint kp) {
		String description = kp.getDescription();
		return description == null ? "Kein Grund angegeben" : description;
	}

	private static String remainingRuntime(Kickpoint kp) {
		return formatDuration(Duration.between(OffsetDateTime.now(), kp.getExpirationDate()));
	}

	private static String formatDuration(Duration duration) {
		long totalSeconds = duration.getSeconds();

		long days = totalSeconds / (24 * 3600);
		long hours = (totalSeconds % (24 * 3600)) / 3600;
		long minutes = (totalSeconds % 3600) / 60;
		long seconds = totalSeconds % 60;

		return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
	}

	private static String givenByName(Kickpoint kp) {
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		Member createdby = guild == null ? null : guild.getMemberById(kp.getUserGivenBy().getUserID());
		return createdby == null ? "Unbekannt" : createdby.getEffectiveName();
	}

}
