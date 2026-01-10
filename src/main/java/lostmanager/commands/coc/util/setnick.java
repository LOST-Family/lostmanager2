package lostmanager.commands.coc.util;

import java.util.ArrayList;
import java.util.List;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class setnick extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("setnick"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Nickname ändern";

		OptionMapping myplayerOption = event.getOption("my_player");
		OptionMapping aliasOption = event.getOption("alias");

		if (myplayerOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Der Parameter Player ist verpflichtend!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String my_playertag = myplayerOption.getAsString();
		String alias = null;

		if (aliasOption != null) {
			alias = aliasOption.getAsString();
		}

		Player p = new Player(my_playertag);
		if (!p.IsLinked()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Bitte verwende einen deiner Accounts!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (!p.getUser().getUserID().equals(event.getUser().getId())) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Der Account muss mit dir verlinkt sein!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String nick = null;
		if (alias != null) {
			nick = p.getNameAPI() + " | " + alias;
		} else {
			nick = p.getNameAPI();
		}

		try {
			event.getGuild().getMember(event.getUser()).modifyNickname(nick).queue();
		} catch (Exception ex) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Beim ändern deines Nicknamen ist ein Fehler aufgetreten. \nFür Entwickler: "
									+ ex.getClass().getSimpleName(),
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String desc = "Du hast deinen Nickname erfolgreich zu " + nick + " geändert.";
		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

		}, "SetnickCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("setnick"))
			return;

		new Thread(() -> {

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("my_player")) {
			List<Command.Choice> choices = new ArrayList<>();
			ArrayList<Player> linked = new User(event.getUser().getId()).getAllLinkedAccounts();
			for (Player l : linked) {
				if (l.getInfoStringAPI().toLowerCase().contains(input.toLowerCase())
						|| l.getTag().toLowerCase().startsWith(input.toLowerCase())) {
					choices.add(new Choice(l.getInfoStringAPI(), l.getTag()));
					if (choices.size() == 25) {
						break;
					}
				}
			}
			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
		}, "SetnickAutocomplete-" + event.getUser().getId()).start();
	}
}
