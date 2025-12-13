package commands.discord.util;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class lmagent extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("lmagent"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "LM Agent";

			OptionMapping promptOption = event.getOption("prompt");

			if (promptOption == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Der Parameter 'prompt' ist erforderlich.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String prompt = promptOption.getAsString();

			// Dummy response that echoes the prompt
			String response = "Du hast folgenden Prompt eingegeben:\n\n" + prompt;

			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, response, MessageUtil.EmbedType.INFO))
					.queue();

		}, "LMAgentCommand-" + event.getUser().getId()).start();
	}
}
