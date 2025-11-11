package commands.links;

import java.util.List;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class unlink extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("unlink"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "User-Link";

			boolean b = false;
			User userexecuted = new User(event.getUser().getId());
			for (String clantag : DBManager.getAllClans()) {
				if (userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
						|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
						|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER) {
					b = true;
					break;
				}
			}
			if (b == false) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			OptionMapping tagOption = event.getOption("tag");

			if (tagOption == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String tag = tagOption.getAsString();
			Player p = new Player(tag);

			if (p.IsLinked()) {
				if (p.getClanDB() == null) {
					DBUtil.executeUpdate("DELETE FROM players WHERE coc_tag = ?", tag);
					String desc = "Die Verknüpfung des Spielers mit dem Tag " + tag + " wurde erfolgreich gelöscht.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
							.queue();
				} else {
					String desc = "Der Spieler ist noch in einen Clan eingetragen. Entferne ihn dort erst.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
							.queue();
				}
			} else {
				String desc = "Der Spieler mit dem Tag " + tag + " ist bereits nicht mehr verknüpft.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue();
			}
		}, "UnlinkCommand-" + event.getUser().getId()).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("unlink"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("tag")) {
				List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);

				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
		}, "UnlinkAutocomplete-" + event.getUser().getId()).start();
	}

}
