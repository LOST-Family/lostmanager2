package commands.coc.links;

import java.util.ArrayList;
import java.util.List;

import datawrapper.Kickpoint;
import datawrapper.Player;
import datawrapper.User;
import dbutil.DBManager;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class playerinfo extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("playerinfo"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Spielerinformation";

			OptionMapping userOption = event.getOption("user");
			OptionMapping playerOption = event.getOption("player");

			if (userOption == null && playerOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Einer der beiden Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			if (userOption != null && playerOption != null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Bitte gib nur einen Parameter an!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			String userid = null;
			String playertag = null;
			ArrayList<Player> linkedaccs = new ArrayList<>();
			Player player = null;

			enum ConvertionType {
				USERTOACCS, ACCTOUSER
			}

			ConvertionType conv = null;

			if (userOption != null) {
				userid = userOption.getAsMentionable().getId();
				linkedaccs = new User(userid).getAllLinkedAccounts();
				conv = ConvertionType.USERTOACCS;
			}
			if (playerOption != null) {
				playertag = playerOption.getAsString();
				player = new Player(playertag);
				if (player.IsLinked()) {
					userid = player.getUser().getUserID();
					conv = ConvertionType.ACCTOUSER;
				} else {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler ist nicht verifiziert.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			String desc = "";

			if (conv == ConvertionType.ACCTOUSER) {
				try {
					desc += "## " + MessageUtil.unformat(player.getInfoStringDB()) + "\n";
				} catch (Exception e) {
					e.printStackTrace();
				}
				desc += "Verlinkter Discord Account: <@" + userid + ">\n";
				if (player.getClanDB() != null) {
					desc += "Eingetragen in Clan: " + player.getClanDB().getInfoString() + "\n";
				} else {
					desc += "Eingetragen in Clan: ---\n";
				}
				if (player.getClanAPI() != null) {
					desc += "Ingame in Clan: " + player.getClanAPI().getInfoString() + "\n";
				} else {
					desc += "Ingame in Clan: ---\n";
				}
				long kpatm = 0;
				for (Kickpoint kp : player.getActiveKickpoints()) {
					kpatm += kp.getAmount();
				}
				desc += "Aktuelle Anzahl Kickpunkte: " + kpatm + "\n";
				desc += "Ingesamte Anzahl Kickpunkte: " + player.getTotalKickpoints();

				MessageChannelUnion channel = event.getChannel();
				MessageUtil.sendUserPingHidden(channel, userid);
			}
			if (conv == ConvertionType.USERTOACCS) {
				try {
					desc += "## <@" + userid + "> \n";
				} catch (Exception e) {
					e.printStackTrace();
				}
				if (linkedaccs.isEmpty()) {
					desc += "	Keine verlinkten Accounts.\n";
				} else {
					desc += "Verlinkte Accounts: \n";
					for (Player p : linkedaccs) {
						desc += "   \\- " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
					}
				}
			}
			
			Button bellButton = Button.secondary("playerinfo_bell_" + userid, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔔"));
			
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
					.setActionRow(bellButton).queue();
		}, "PlayerInfoCommand-" + event.getUser().getId()).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("playerinfo"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("player")) {
				List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);

				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
		}, "PlayerInfoAutocomplete-" + event.getUser().getId()).start();
	}
	
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		
		// Handle bell button - send ping with trash button
		if (id.startsWith("playerinfo_bell_")) {
			String userid = id.substring("playerinfo_bell_".length());
			MessageChannelUnion channel = event.getChannel();
			MessageUtil.sendUserPingWithDelete(channel, userid);
			event.deferEdit().queue();
			return;
		}
		
		// Handle trash button - delete the ping message
		if (id.equals("playerinfo_trash")) {
			event.getMessage().delete().queue(null, error -> {
				// Silently ignore errors (e.g., message already deleted or insufficient permissions)
			});
			return;
		}
	}

}
