package lostmanager.commands.discord.util;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Tool;
import com.google.genai.types.UrlContext;

import lostmanager.Bot;
import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class lmagent extends ListenerAdapter {

	/** Discord embed descriptions are limited to 4096 characters. */
	private static final int MAX_RESPONSE_LENGTH = 4000;
	/** Guard against abusive / very expensive prompts. */
	private static final int MAX_PROMPT_LENGTH = 2000;

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("lmagent"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "LM Agent";

			// This command invokes an LLM with web (URL-context) access and incurs real cost,
			// so it is restricted to admins. Loosen this gate if it should be available to more users.
			if (!new User(event.getUser().getId()).isAdmin()) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst Admin sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			OptionMapping promptOption = event.getOption("prompt");

			if (promptOption == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Der Parameter 'prompt' ist erforderlich.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String prompt = promptOption.getAsString();
			if (prompt.length() > MAX_PROMPT_LENGTH) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Der Prompt ist zu lang (max. " + MAX_PROMPT_LENGTH + " Zeichen).",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			try {
				Client client = Bot.genaiClient;

				// URL Context Tool konfigurieren
				Tool urlContextTool = Tool.builder().urlContext(UrlContext.builder().build()).build();

				GenerateContentConfig config = GenerateContentConfig.builder().tools(urlContextTool).build();

				String gemprompt = "Kontextinformationen: " + Bot.systemInstructions + " Anfrage des Nutzers: " + prompt;

				GenerateContentResponse response = client.models.generateContent("gemini-2.5-flash", gemprompt, config);

				String text = response != null ? response.text() : null;
				if (text == null || text.isBlank()) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Es kam keine Antwort vom Modell zurück.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}
				if (text.length() > MAX_RESPONSE_LENGTH) {
					text = text.substring(0, MAX_RESPONSE_LENGTH) + "…";
				}

				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title, text, MessageUtil.EmbedType.INFO))
						.queue();
			} catch (final Exception e) {
				System.err.println("Fehler im LM Agent: " + e.getMessage());
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Beim Verarbeiten der Anfrage ist ein Fehler aufgetreten.",
								MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "LMAgentCommand-" + event.getUser().getId()).start();
	}
}
