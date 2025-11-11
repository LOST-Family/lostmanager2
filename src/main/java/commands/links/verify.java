package commands.links;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import datautil.DBUtil;
import datawrapper.Player;
import datawrapper.User;
import lostmanager.Bot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class verify extends ListenerAdapter {

	private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("verify"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Verifizierung";

			OptionMapping tagOption = event.getOption("tag");
			OptionMapping apitokenoption = event.getOption("apitoken");

			if (tagOption == null || apitokenoption == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Beide Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue(sentMessage -> {
							scheduler.schedule(() -> {
								sentMessage.delete().queue();
							}, 120, TimeUnit.SECONDS);
						});
				return;
			}

			String tag = tagOption.getAsString().toUpperCase().replaceAll("O", "0");
			if (!tag.startsWith("#")) {
				tag = "#" + tag;
			}
			String apitoken = apitokenoption.getAsString();
			User userexecuted = new User(event.getUser().getId());
			String userid = userexecuted.getUserID();
			Player p = new Player(tag);

			if (p.AccExists()) {
				String playername = p.getNameAPI();
				if (!p.IsLinked()) {
					if (p.verifyCocTokenAPI(apitoken)) {
						DBUtil.executeUpdate("INSERT INTO players (coc_tag, discord_id, name) VALUES (?, ?, ?)", tag,
								userid, playername);
						Player player = new Player(tag);
						String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB())
								+ " wurde erfolgreich mit dem User <@" + userid + "> verknüpft.";
						ArrayList<Player> linkedaccs = userexecuted.refreshData().getAllLinkedAccounts();
						if (linkedaccs.size() == 1) {
							Member member = event.getGuild().getMemberById(userid);
							member.modifyNickname(playername).queue();
							if (!member.getRoles().contains(event.getGuild().getRoleById(Bot.verified_roleid)))
								event.getGuild().addRoleToMember(member, event.getGuild().getRoleById(Bot.verified_roleid))
										.queue();
						}
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
								.queue(sentMessage -> {
									scheduler.schedule(() -> {
										sentMessage.delete().queue();
									}, 120, TimeUnit.SECONDS);
								});
					} else {
						String desc = "Der API-Token passt nicht zu dem Account.";
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
								.queue(sentMessage -> {
									scheduler.schedule(() -> {
										sentMessage.delete().queue();
									}, 120, TimeUnit.SECONDS);
								});
					}
				} else {
					Player player = new Player(tag);
					String linkeduserid = player.getUser().getUserID();
					String desc = "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " ist bereits mit <@"
							+ linkeduserid + "> verknüpft. Bitte einen Vize-Anführer, den Account zu entlinken.";
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
							.queue(sentMessage -> {
								scheduler.schedule(() -> {
									sentMessage.delete().queue();
								}, 120, TimeUnit.SECONDS);
							});
				}
			} else {
				String desc = "Der Spieler mit dem Tag " + tag + " existiert nicht oder es ist ein API-Fehler aufgetreten.";
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue(sentMessage -> {
							scheduler.schedule(() -> {
								sentMessage.delete().queue();
							}, 120, TimeUnit.SECONDS);
						});
			}
		}, "VerifyCommand-" + event.getUser().getId()).start();

	}

}
