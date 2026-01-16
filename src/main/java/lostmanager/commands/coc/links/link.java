package lostmanager.commands.coc.links;

import java.sql.Timestamp;

import lostmanager.datawrapper.AchievementData;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class link extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("link"))
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

			String tag = tagOption.getAsString().toUpperCase().replaceAll("O", "0");
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
				String playername = null;
				try {
					playername = p.getNameAPI();
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (!p.IsLinked()) {
					DBUtil.executeUpdate("INSERT INTO players (coc_tag, discord_id, name) VALUES (?, ?, ?)", tag, userid,
							playername);
					Player player = new Player(tag);
					
					// Save initial wins data for newly linked player
					try {
						// Use current time as timestamp since player was just linked
						Timestamp now = new Timestamp(System.currentTimeMillis());
						player.addAchievementDataToDB(AchievementData.Type.WINS, now);
					} catch (Exception e) {
						System.err.println("Error saving initial wins for player " + tag + ": " + e.getMessage());
					}
					
					String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringAPI())
							+ " wurde erfolgreich mit dem User <@" + userid + "> verknüpft.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
							.queue();
					MessageUtil.sendUserPingHidden(event.getChannel(), userid);
				} else {
					Player player = new Player(tag);
					String linkeduserid = player.getUser().getUserID();
					String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringAPI()) + " ist bereits mit <@"
							+ linkeduserid + "> verknüpft. Bitte verwende ``/unlink`` oder ``/relink``.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
							.queue();
					MessageUtil.sendUserPingHidden(event.getChannel(), linkeduserid);
				}
			} else {
				String desc = "Der Spieler mit dem Tag " + tag + " existiert nicht oder es ist ein API-Fehler aufgetreten.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue();
			}
		}, "LinkCommand-" + event.getUser().getId()).start();

	}

}
