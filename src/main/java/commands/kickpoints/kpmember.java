package commands.kickpoints;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Kickpoint;
import datawrapper.Player;
import lostmanager.Bot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class kpmember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpmember"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Aktive Kickpunkte";

		OptionMapping playerOption = event.getOption("player");

		if (playerOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Der Parameter Player ist erforderlich!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String playertag = playerOption.getAsString();

		new Thread(new Runnable() {

			@Override
			public void run() {

				Player p = new Player(playertag);
				Clan c = p.getClanDB();

				if (c == null) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler existiert nicht oder ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				if (!c.ExistsDB()) {
					event.getHook().editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

				String desc = "Aktive Kickpunkte von " + MessageUtil.unformat(p.getInfoStringDB()) + " in "
						+ c.getInfoString() + ":\n";
				if (activekps.size() > 0) {
					int totalkps = 0;
					for (Kickpoint kpi : activekps) {
						totalkps += kpi.getAmount();
					}
					desc += "**Gesamt: " + totalkps + "/" + c.getMaxKickpoints() + " Kickpunkte**";

					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
					DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

					for (Kickpoint kp : activekps) {
						desc += "\n";
						desc += "### Kickpunkt #" + kp.getID() + ":\n";
						desc += "Grund: " + kp.getDescription() + "\n";
						desc += "Anzahl Kickpunkte: " + kp.getAmount() + "\n";
						desc += "Erhalten am: "
								+ kp.getDate().atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(formatter) + "\n";

						Duration duration1 = Duration.between(OffsetDateTime.now(), kp.getExpirationDate());

						long totalSeconds1 = duration1.getSeconds();

						long days1 = totalSeconds1 / (24 * 3600);
						long hours1 = (totalSeconds1 % (24 * 3600)) / 3600;
						long minutes1 = (totalSeconds1 % 3600) / 60;
						long seconds1 = totalSeconds1 % 60;

						desc += "Läuft ab in: " + String.format("%dd %dh %dm %ds", days1, hours1, minutes1, seconds1)
								+ "\n";

						Duration duration2 = Duration.between(kp.getDate(), OffsetDateTime.now());

						long totalSeconds2 = duration2.getSeconds();

						long days2 = totalSeconds2 / (24 * 3600);
						long hours2 = (totalSeconds2 % (24 * 3600)) / 3600;
						long minutes2 = (totalSeconds2 % 3600) / 60;
						long seconds2 = totalSeconds2 % 60;

						desc += "Aktiv seit: " + String.format("%dd %dh %dm %ds", days2, hours2, minutes2, seconds2)
								+ "\n";

						Member createdby = Bot.getJda().getGuildById(Bot.guild_id)
								.getMemberById(kp.getUserGivenBy().getUserID());
						String createdbyname = createdby.getEffectiveName();

						desc += "Erstellt: von " + createdbyname + " am "
								+ kp.getGivenDate().atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(formatter2)
								+ "\n";

					}
				} else {
					desc += "Dieser Spieler hat keine aktiven Kickpunkte.\n";
				}
				desc += "\n";
				desc += "**Gesamtanzahl (Vergangene und aktuelle Kickpunkte):**" + "\n";
				long kptotal = p.getTotalKickpoints();
				desc += "" + kptotal;
				Button refreshButton = Button.secondary("kpmember_" + playertag, "\u200B")
						.withEmoji(Emoji.fromUnicode("🔁"));

				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);

				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
						"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();

			}
		}).start();

		}, "KpmemberCommand-" + event.getUser().getId()).start();

	}
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
		if (!id.startsWith("kpmember_"))
			return;

		event.deferEdit().queue();

		String playertag = id.substring("kpmember_".length());
		String title = "Aktive Kickpunkte";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		new Thread(new Runnable() {

			@Override
			public void run() {

				Player p = new Player(playertag);
				Clan c = p.getClanDB();

				if (c == null) {
					event.replyEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler existiert nicht oder ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				if (!c.ExistsDB()) {
					event.getHook().editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

				String desc = "Aktive Kickpunkte von " + MessageUtil.unformat(p.getInfoStringDB()) + " in "
						+ c.getInfoString() + ":\n";
				if (activekps.size() > 0) {
					int totalkps = 0;
					for (Kickpoint kpi : activekps) {
						totalkps += kpi.getAmount();
					}
					desc += "**Gesamt: " + totalkps + "/" + c.getMaxKickpoints() + " Kickpunkte**";

					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
					DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm");

					for (Kickpoint kp : activekps) {
						desc += "\n";
						desc += "### Kickpunkt #" + kp.getID() + ":\n";
						desc += "Grund: " + kp.getDescription() + "\n";
						desc += "Anzahl Kickpunkte: " + kp.getAmount() + "\n";
						desc += "Erhalten am: "
								+ kp.getDate().atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(formatter) + "\n";

						Duration duration1 = Duration.between(OffsetDateTime.now(), kp.getExpirationDate());

						long totalSeconds1 = duration1.getSeconds();

						long days1 = totalSeconds1 / (24 * 3600);
						long hours1 = (totalSeconds1 % (24 * 3600)) / 3600;
						long minutes1 = (totalSeconds1 % 3600) / 60;
						long seconds1 = totalSeconds1 % 60;

						desc += "Läuft ab in: " + String.format("%dd %dh %dm %ds", days1, hours1, minutes1, seconds1)
								+ "\n";

						Duration duration2 = Duration.between(kp.getDate(), OffsetDateTime.now());

						long totalSeconds2 = duration2.getSeconds();

						long days2 = totalSeconds2 / (24 * 3600);
						long hours2 = (totalSeconds2 % (24 * 3600)) / 3600;
						long minutes2 = (totalSeconds2 % 3600) / 60;
						long seconds2 = totalSeconds2 % 60;

						desc += "Aktiv seit: " + String.format("%dd %dh %dm %ds", days2, hours2, minutes2, seconds2)
								+ "\n";

						Member createdby = Bot.getJda().getGuildById(Bot.guild_id)
								.getMemberById(kp.getUserGivenBy().getUserID());
						String createdbyname = createdby.getEffectiveName();

						desc += "Erstellt: von " + createdbyname + " am "
								+ kp.getGivenDate().atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(formatter2)
								+ "\n";

					}
				} else {
					desc += "Dieser Spieler hat keine aktiven Kickpunkte.\n";
				}
				desc += "\n";
				desc += "**Gesamtanzahl (Vergangene und aktuelle Kickpunkte):**" + "\n";
				long kptotal = p.getTotalKickpoints();
				desc += "" + kptotal;

				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);

				event.getInteraction().getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc.toString(),
						MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert)).queue();

			}
		}).start();
	}

}
