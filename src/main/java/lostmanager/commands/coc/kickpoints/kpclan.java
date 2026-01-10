package lostmanager.commands.coc.kickpoints;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Kickpoint;
import lostmanager.datawrapper.Player;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class kpclan extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpclan"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Aktive Kickpunkte des Clans";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter Clan ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();
		Clan c = new Clan(clantag);

		if (!c.ExistsDB()) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		HashMap<String, Integer> kpamounts = new HashMap<>();

		new Thread(new Runnable() {

			@Override
			public void run() {

				String desc = "### Kickpunkte aller Spieler des Clans " + c.getInfoString() + ":\n";

				for (Player p : c.getPlayersDB()) {
					ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

					int totalkps = 0;
					for (Kickpoint kpi : activekps) {
						totalkps += kpi.getAmount();
					}
					if (totalkps > 0) {
						kpamounts.put(p.getInfoStringDB(), totalkps);
					}
				}

				LinkedHashMap<String, Integer> sorted = kpamounts.entrySet().stream()
						.sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).collect(Collectors
								.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));

				// Ausgabe sortiert
				for (String key : sorted.keySet()) {
					String kp = sorted.get(key) == 1 ? "Kickpunkt" : "Kickpunkte";
					desc += key + ": " + sorted.get(key) + " " + kp + "\n\n";
				}

				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);


				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
						.setActionRow(
								Button.secondary("kpclan_" + clantag, "\u200B").withEmoji(Emoji.fromUnicode("🔁")))
						.queue();

			}
		}).start();
		}, "KpclanCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpclan"))
			return;

		new Thread(() -> {

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
		}, "KpclanAutocomplete-" + event.getUser().getId()).start();
	}
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("kpclan_"))
			return;

		event.deferEdit().queue();

		String clantag = id.substring("kpclan_".length());
		String title = "Aktive Kickpunkte des Clans";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		Clan c = new Clan(clantag);

		if (!c.ExistsDB()) {
			event.getInteraction().getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		HashMap<String, Integer> kpamounts = new HashMap<>();

		new Thread(new Runnable() {

			@Override
			public void run() {

				String desc = "### Kickpunkte aller Spieler des Clans " + c.getInfoString() + ":\n";

				for (Player p : c.getPlayersDB()) {
					ArrayList<Kickpoint> activekps = p.getActiveKickpoints();

					int totalkps = 0;
					for (Kickpoint kpi : activekps) {
						totalkps += kpi.getAmount();
					}
					if (totalkps > 0) {
						kpamounts.put(p.getInfoStringDB(), totalkps);
					}
				}

				LinkedHashMap<String, Integer> sorted = kpamounts.entrySet().stream()
						.sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).collect(Collectors
								.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, _) -> e1, LinkedHashMap::new));

				// Ausgabe sortiert
				for (String key : sorted.keySet()) {
					String kp = sorted.get(key) == 1 ? "Kickpunkt" : "Kickpunkte";
					desc += key + ": " + sorted.get(key) + " " + kp + "\n\n";
				}

				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);

				event.getInteraction().getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc,
						MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert)).queue();

			}
		}).start();
	}

}
