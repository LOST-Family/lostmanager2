package lostmanager.commands.discord.admin;

import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class restart extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("restart"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Restart";

		User userexecuted = new User(event.getUser().getId());
		if (!userexecuted.isAdmin()) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst Admin sein, um diesen Befehl ausführen zu können.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		event.getHook()
				.editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Der Bot wird neugestartet.", MessageUtil.EmbedType.SUCCESS))
				.queue();
		
		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		 System.exit(0);

		}, "RestartCommand-" + event.getUser().getId()).start();

	}}
