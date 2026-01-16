package lostmanager.commands.coc.kickpoints;

import lostmanager.datawrapper.Kickpoint;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class kpremove extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpremove"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Kickpunkte";

		OptionMapping idOption = event.getOption("id");

		if (idOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter id ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		int id = event.getOption("id").getAsInt();

		Kickpoint kp = new Kickpoint(id);

		if (kp.getPlayer() == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Kickpunkt existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = kp.getPlayer().getClanDB().getTag();

		User userexecuted = new User(event.getUser().getId());
		if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String desc = "";

		if (kp.getDescription() != null) {
			DBUtil.executeUpdate("DELETE FROM kickpoints WHERE id = ?", id);
			desc += "Der Kickpunkt mit der ID " + id + " wurde gelöscht.";
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
					.queue();
		} else {
			desc += "Ein Kickpunkt mit dieser ID existiert nicht.";
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
					.queue();
		}

		}, "KpremoveCommand-" + event.getUser().getId()).start();

	}
}
