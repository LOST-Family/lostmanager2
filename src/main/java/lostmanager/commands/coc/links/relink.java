package lostmanager.commands.coc.links;

import java.util.List;

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

public class relink extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("relink"))
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
			OptionMapping useroption = event.getOption("user");
			OptionMapping useridoption = event.getOption("userid");

			if (tagOption == null || (useroption == null && useridoption == null)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Der Tag und einer der anderen Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String tag = tagOption.getAsString().toUpperCase();
			if (!tag.startsWith("#")) {
				tag = "#" + tag;
			}
			String userid;
			if (useroption != null) {
				userid = useroption.getAsMentionable().getId();
			} else {
				userid = useridoption.getAsString();
			}

			Player p = new Player(tag);

			if (p.AccExists()) {
				if (p.IsLinked()) {
					DBUtil.executeUpdate("UPDATE players SET discord_id = ? WHERE coc_tag = ?", userid, tag);
					Player player = new Player(tag);
					String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringAPI())
							+ " wurde erfolgreich mit dem User <@" + userid + "> verknüpft.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
							.queue();
					MessageUtil.sendUserPingHidden(event.getChannel(), userid);
				} else {
					String desc = "Der Spieler ist nicht verknüpft. Bitte verwende normal ``/link``.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
							.queue();
				}
			} else {
				String desc = "Der Spieler mit dem Tag " + tag + " existiert nicht oder es ist ein API-Fehler aufgetreten.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue();
			}
		}, "RelinkCommand-" + event.getUser().getId()).start();

	}
	
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("relink"))
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
		}, "RelinkAutocomplete-" + event.getUser().getId()).start();
	}

}
