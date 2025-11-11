package commands.kickpoints;

import java.util.ArrayList;
import java.util.List;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class kpinfo extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpinfo"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Einstellungen";

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

		String desc = "";

		ArrayList<KickpointReason> kpreasons = c.getKickpointReasons();

		int maximumwidth = 0;
		for (KickpointReason reason : kpreasons) {
			String r = reason.getReason();
			maximumwidth = maximumwidth > r.length() ? maximumwidth : r.length();
		}

		if (maximumwidth != 0) {
			maximumwidth = maximumwidth < 5 ? 5 : maximumwidth;

			StringBuilder sb = new StringBuilder();
			sb.append("```");
			sb.append(String.format(" %-" + maximumwidth + "s %12s\n", "Grund", "Kickpunkte"));
			for (int i = 0; i < maximumwidth; i++) {
				sb.append("-");
			}
			sb.append("--");
			sb.append(" ------------\n");
			for (KickpointReason reason : kpreasons) {
				sb.append(String.format(" %-" + maximumwidth + "s %12d\n", reason.getReason(), reason.getAmount()));
			}
			sb.append("```");
			String embedText = sb.toString();
			desc += embedText;
		} else {
			desc += "Keine anzuzeigenden Kickpunkt-Gründe.\n\n";
		}

		Integer daysexpire = c.getDaysKickpointsExpireAfter();
		Long maxpoints = c.getMaxKickpoints();
		Long minwins = c.getMinSeasonWins();
		Field days;
		Field max;
		Field wins;
		if (daysexpire != null) {
			days = new Field("**Gültigkeitsdauer von Kickpunkten:**", daysexpire + " Tage", true);
		} else {
			days = new Field("**Gültigkeitsdauer von Kickpunkten:**", "/", true);
		}

		if (maxpoints != null) {
			max = new Field("**Maximale Anzahl an Kickpunkten:**", maxpoints + " Kickpunkte", true);
		} else {
			max = new Field("**Maximale Anzahl an Kickpunkten:**", "/", true);
		}

		if (minwins != null) {
			wins = new Field("**Minimale Anzahl an Season Wins:**", minwins + " Wins", true);
		} else {
			wins = new Field("**Minimale Anzahl an Season Wins:**", "/", true);
		}

		event.getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO, days, max, wins))
				.queue();

		}, "KpinfoCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpinfo"))
			return;

		new Thread(() -> {

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

			event.replyChoices(choices).queue(_ ->{}, _ -> {});
		}
		}, "KpinfoAutocomplete-" + event.getUser().getId()).start();
	}
}
