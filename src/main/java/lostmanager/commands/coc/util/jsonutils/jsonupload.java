package lostmanager.commands.coc.util.jsonutils;

import lostmanager.Bot;
import lostmanager.util.MessageUtil;
import lostmanager.webserver.JsonUploadServer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class jsonupload extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("jsonupload"))
			return;

		// Reply ephemerally (only visible to the user)
		event.deferReply(true).queue();

		new Thread(() -> {
			String title = "JSON Upload Link";

			try {
				// Generate session key
				String sessionKey = JsonUploadServer.createUploadSession(event.getUser().getId());

				// Build URL
				String url = Bot.webserver_base_url + "/upload?key=" + sessionKey;

				// Build description
				String desc = "Dein persönlicher Upload-Link wurde erstellt!\n\n";
				desc += "**Link:** " + url + "\n\n";
				desc += "⏱️ **Gültigkeitsdauer:** 10 Minuten\n";
				desc += "🔒 **Hinweis:** Der Link kann nur einmal verwendet werden und ist nur für dich sichtbar.";

				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
						.queue();
			} catch (Exception e) {
				System.err.println("Error generating upload link: " + e.getMessage());
				e.printStackTrace();
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title,
								"Fehler beim Erstellen des Upload-Links: " + e.getMessage(),
								MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "JsonUploadCommand-" + event.getUser().getId()).start();
	}
}
