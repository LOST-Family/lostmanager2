package lostmanager.commands.coc.util;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.ImageMapCache;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class reloadmappings extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("reloadmappings"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Mappings neu laden";

			// Check permissions - must be at least co-leader
			User userExecuted = new User(event.getUser().getId());
			boolean hasPermission = false;
			for (String clantag : DBManager.getAllClans()) {
				Player.RoleType role = userExecuted.getClanRoles().get(clantag);
				if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
						|| role == Player.RoleType.COLEADER) {
					hasPermission = true;
					break;
				}
			}

			if (!hasPermission) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// Reload the mappings
			try {
				ImageMapCache.loadImageMap();
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Die Mappings wurden erfolgreich neu geladen.",
						MessageUtil.EmbedType.SUCCESS)).queue();
			} catch (Exception e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Fehler beim Laden der Mappings: " + e.getMessage(),
						MessageUtil.EmbedType.ERROR)).queue();
				System.err.println("Error reloading mappings: " + e.getMessage());
				e.printStackTrace();
			}

		}, "ReloadMappingsCommand-" + event.getUser().getId()).start();
	}
}
