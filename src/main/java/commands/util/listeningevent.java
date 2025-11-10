package commands.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.ActionValue;
import datawrapper.Clan;
import datawrapper.KickpointReason;
import datawrapper.ListeningEvent;
import datawrapper.Player;
import datawrapper.User;
import lostmanager.Bot;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class listeningevent extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("listeningevent"))
			return;

		String title = "Listening Event";
		String subcommand = event.getSubcommandName();

		if (subcommand == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Bitte wähle einen Unterbefehl aus.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		User userexecuted = new User(event.getUser().getId());
		boolean isAuthorized = false;
		for (String clantag : DBManager.getAllClans()) {
			Player.RoleType role = userexecuted.getClanRoles().get(clantag);
			if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER
					|| role == Player.RoleType.COLEADER) {
				isAuthorized = true;
				break;
			}
		}

		if (!isAuthorized) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		switch (subcommand) {
		case "add":
			handleAdd(event, title);
			break;
		case "list":
			handleList(event, title);
			break;
		case "remove":
			handleRemove(event, title);
			break;
		default:
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Unbekannter Unterbefehl.",
					MessageUtil.EmbedType.ERROR)).queue();
		}
	}

	private void handleAdd(SlashCommandInteractionEvent event, String title) {
		event.deferReply().queue();
		
		OptionMapping clanOption = event.getOption("clan");
		OptionMapping typeOption = event.getOption("type");
		OptionMapping durationOption = event.getOption("duration");
		OptionMapping actionTypeOption = event.getOption("actiontype");
		OptionMapping channelOption = event.getOption("channel");
		OptionMapping kickpointReasonOption = event.getOption("kickpoint_reason");

		if (clanOption == null || typeOption == null || durationOption == null || 
		    actionTypeOption == null || channelOption == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, 
				"Alle erforderlichen Parameter müssen angegeben werden!", 
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String clantag = clanOption.getAsString();
		String type = typeOption.getAsString();
		long duration = durationOption.getAsLong();
		String actionTypeStr = actionTypeOption.getAsString();
		String channelId = channelOption.getAsChannel().getId();
		String kickpointReasonName = kickpointReasonOption != null ? kickpointReasonOption.getAsString() : null;

		// Validate action type
		if (!actionTypeStr.equals("infomessage") && !actionTypeStr.equals("kickpoint") 
		    && !actionTypeStr.equals("cwdonator")) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
				"Ungültiger Aktionstyp. Erlaubt: infomessage, kickpoint, cwdonator",
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Check if kickpoint_reason is required
		if (actionTypeStr.equals("kickpoint") && kickpointReasonName == null) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
				"Kickpoint-Grund ist erforderlich, wenn actiontype=kickpoint!",
				MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		// Build action values
		ArrayList<ActionValue> actionValues = new ArrayList<>();
		if (actionTypeStr.equals("cwdonator")) {
			actionValues.add(new ActionValue(ActionValue.ACTIONVALUETYPE.FILLER));
		} else if (actionTypeStr.equals("kickpoint") && kickpointReasonName != null) {
			// Create KickpointReason with name and clan tag
			KickpointReason kpReason = new KickpointReason(kickpointReasonName, clantag);
			actionValues.add(new ActionValue(kpReason));
		}

		// Convert action values to JSON
		String actionValuesJson = "[]";
		if (!actionValues.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionValuesJson = mapper.writeValueAsString(actionValues);
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}

		// Insert into database
		DBUtil.executeUpdate(
			"INSERT INTO listening_events (clan_tag, listeningtype, listeningvalue, actiontype, channel_id, actionvalues) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
			clantag, type, duration, actionTypeStr, channelId, actionValuesJson);

		String desc = "### Listening Event wurde hinzugefügt.\n";
		desc += "**Clan:** " + clantag + "\n";
		desc += "**Typ:** " + type + "\n";
		desc += "**Dauer:** " + duration + " ms\n";
		desc += "**Aktionstyp:** " + actionTypeStr + "\n";
		desc += "**Channel:** <#" + channelId + ">\n";
		if (kickpointReasonName != null) {
			desc += "**Kickpoint-Grund:** " + kickpointReasonName + "\n";
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
			.queue();

		// Restart all events to include the new one
		Bot.restartAllEvents();
	}

	private void handleList(SlashCommandInteractionEvent event, String title) {
		event.deferReply().queue();

		OptionMapping clanOption = event.getOption("clan");
		String clantag = clanOption != null ? clanOption.getAsString() : null;

		StringBuilder desc = new StringBuilder("## Listening Events\n\n");

		String sql;
		ArrayList<Long> ids;

		if (clantag != null) {
			sql = "SELECT id FROM listening_events WHERE clan_tag = ?";
			ids = DBUtil.getArrayListFromSQL(sql, Long.class, clantag);
		} else {
			sql = "SELECT id FROM listening_events";
			ids = DBUtil.getArrayListFromSQL(sql, Long.class);
		}

		if (ids.isEmpty()) {
			desc.append("Keine Events gefunden.");
		} else {
			for (Long id : ids) {
				ListeningEvent le = new ListeningEvent(id);
				Clan clan = new Clan(le.getClanTag());
				desc.append("**ID:** ").append(id).append("\n");
				desc.append("**Clan:** ").append(clan.getNameDB()).append(" (").append(le.getClanTag()).append(")\n");
				desc.append("**Typ:** ").append(le.getListeningType()).append("\n");
				desc.append("**Action:** ").append(le.getActionType()).append("\n");
				desc.append("**Channel:** <#").append(le.getChannelID()).append(">\n");
				desc.append("**Feuert in:** ")
						.append((le.getTimestamp() - System.currentTimeMillis()) / 1000 / 60).append(" Minuten\n");
				desc.append("\n");
			}
		}

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc.toString(), MessageUtil.EmbedType.INFO))
				.queue();
	}

	private void handleRemove(SlashCommandInteractionEvent event, String title) {
		event.deferReply().queue();

		OptionMapping idOption = event.getOption("id");

		if (idOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Die ID ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		long id = idOption.getAsLong();

		// Check if event exists
		String checkSql = "SELECT 1 FROM listening_events WHERE id = ?";
		Long exists = DBUtil.getValueFromSQL(checkSql, Long.class, id);

		if (exists == null) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Event mit dieser ID existiert nicht.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// Delete event
		DBUtil.executeUpdate("DELETE FROM listening_events WHERE id = ?", id);

		event.getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Event mit ID " + id + " wurde erfolgreich gelöscht.", MessageUtil.EmbedType.SUCCESS))
				.queue();

		// Restart all events to remove the deleted one from scheduler
		Bot.restartAllEvents();
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listeningevent"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
			event.replyChoices(choices).queue(success -> {
			}, error -> {
			});
		} else if (focused.equals("kickpoint_reason")) {
			// Get the clan from the command to filter kickpoint reasons
			OptionMapping clanOption = event.getOption("clan");
			if (clanOption != null) {
				String clantag = clanOption.getAsString();
				List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input, clantag);
				event.replyChoices(choices).queue(success -> {
				}, error -> {
				});
			} else {
				event.replyChoices(new ArrayList<>()).queue();
			}
		}
	}
}
