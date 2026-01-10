package lostmanager.commands.discord.admin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class deletemessages extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("deletemessages"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Delete-Messages";

			User userexecuted = new User(event.getUser().getId());
			if (!userexecuted.isAdmin()) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst Admin sein, um diesen Befehl ausführen zu können.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			OptionMapping amountOption = event.getOption("amount");

			if (amountOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Parameter Amount ist verpflichtend!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			int amount = amountOption.getAsInt();

			MessageChannelUnion channel = event.getChannel();

			if (channel == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Channel konnte nicht geladen werden.", MessageUtil.EmbedType.ERROR)).queue(message -> {
							message.delete().queueAfter(10, TimeUnit.SECONDS);
						});
				return;
			}

			channel.getIterableHistory().takeAsync(amount).thenAccept(messages -> {
				List<Message> recent = new ArrayList<>();
				List<Message> old = new ArrayList<>();

				for (Message msg : messages) {
					if (msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14))) {
						recent.add(msg);
					} else {
						old.add(msg);
					}
				}

				// Bulk-Delete für bis zu 100 Messages unter 14 Tage
				for (int i = 0; i < recent.size(); i += 100) {
					int end = Math.min(i + 100, recent.size());
					List<Message> batch = recent.subList(i, end);
					channel.purgeMessages(batch);
				}

				// Einzeln löschen für >14 Tage
				for (Message msg : old) {
					msg.delete().queue();
				}

				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // Kurze Pause, um sicherzustellen, dass alle Nachrichten gelöscht wurden

				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								amount + " Nachrichten wurden gelöscht. Diese Nachricht wird auch wieder gelöscht.",
								MessageUtil.EmbedType.SUCCESS))
						.queue(message -> {
							message.delete().queueAfter(10, TimeUnit.SECONDS);
						});
			});

		}, "DeletemessagesCommand-" + event.getUser().getId()).start();

	}
}
