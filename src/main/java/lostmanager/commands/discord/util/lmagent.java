package lostmanager.commands.discord.util;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Tool;
import com.google.genai.types.UrlContext;

import lostmanager.Bot;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class lmagent extends ListenerAdapter {

	@SuppressWarnings("null")
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

			Client client = Bot.genaiClient;
			
			// URL Context Tool konfigurieren
			Tool urlContextTool = Tool.builder().urlContext(UrlContext.builder().build()).build();

			GenerateContentConfig config = GenerateContentConfig.builder().tools(urlContextTool).build();

			// GitHub Repository URL im Prompt angeben
			String gemprompt = "Kontextinformationen: " + Bot.systemInstructions + " Anfrage des Nutzers: " + prompt;

			GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", gemprompt, config);

			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, response.text(), MessageUtil.EmbedType.INFO))
					.queue();

		}, "LMAgentCommand-" + event.getUser().getId()).start();
	}
}
