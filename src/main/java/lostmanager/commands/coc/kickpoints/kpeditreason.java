package lostmanager.commands.coc.kickpoints;

import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.KickpointReason;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class kpeditreason extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpeditreason"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Kickpunkt-Grund Vorlage";

			OptionMapping clanOption = event.getOption("clan");
			OptionMapping reasonoption = event.getOption("reason");
			OptionMapping amountoption = event.getOption("amount");

			if (clanOption == null || reasonoption == null || amountoption == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String reason = reasonoption.getAsString();
			String clantag = clanOption.getAsString();
			int amount = amountoption.getAsInt();

			User userexecuted = new User(event.getUser().getId());
			if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			Clan clan = new Clan(clantag);

			if (!clan.ExistsDB()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			KickpointReason kpreason = new KickpointReason(reason, clantag);

			if (!kpreason.Exists()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Diese Begründung existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			DBUtil.executeUpdate("UPDATE kickpoint_reasons SET amount = ? WHERE name = ? AND clan_tag = ?", amount,
					reason, clantag);

			String desc = "Der Kickpunkt-Grund wurde bearbeitet.\n";
			desc += "Grund: " + reason + "\n";
			desc += "Clan: " + clan.getInfoString() + "\n";
			desc += "Anzahl: " + amount + "\n";

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
					.queue();

		}, "KpeditreasonCommand-" + event.getUser().getId()).start();

	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpeditreason"))
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
			if (focused.equals("reason")) {
				if (event.getOption("clan") != null) {
					List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input,
							event.getOption("clan").getAsString());

					event.replyChoices(choices).queue(_ -> {
					}, _ -> {
					});
				}
			}
		}, "KpeditreasonAutocomplete-" + event.getUser().getId()).start();
	}
}
