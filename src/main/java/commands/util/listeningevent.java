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
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
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
		OptionMapping clanOption = event.getOption("clan");
		OptionMapping typeOption = event.getOption("type");

		if (clanOption == null || typeOption == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Clan und Typ sind erforderlich!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String clantag = clanOption.getAsString();
		String type = typeOption.getAsString();

		// Create modal with appropriate fields based on type
		TextInput durationInput = TextInput.create("duration", "Zeit bis Event-Ende (ms)", TextInputStyle.SHORT)
				.setPlaceholder("z.B. 3600000 für 1 Stunde").setRequired(true).build();

		TextInput actionTypeInput = TextInput.create("actiontype", "Aktionstyp", TextInputStyle.SHORT)
				.setPlaceholder("infomessage, custommessage, kickpoint, cwdonator").setRequired(true).build();

		TextInput channelIdInput = TextInput.create("channelid", "Discord Channel ID", TextInputStyle.SHORT)
				.setPlaceholder("z.B. 1234567890").setRequired(true).build();

		TextInput actionValuesInput = TextInput.create("actionvalues", "Action Values (JSON)", TextInputStyle.PARAGRAPH)
				.setPlaceholder("[{\"type\":\"FILLER\"}, {\"reason\":...}]").setRequired(false).build();

		Modal modal = Modal.create("listeningevent_add_" + clantag + "_" + type, "Listening Event hinzufügen")
				.addActionRows(ActionRow.of(durationInput), ActionRow.of(actionTypeInput),
						ActionRow.of(channelIdInput), ActionRow.of(actionValuesInput))
				.build();

		event.replyModal(modal).queue();
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
	public void onModalInteraction(ModalInteractionEvent event) {
		if (event.getModalId().startsWith("listeningevent_add")) {
			event.deferReply().queue();
			String title = "Listening Event";

			String[] parts = event.getModalId().split("_");
			String clantag = parts[2];
			String type = parts[3];

			String durationStr = event.getValue("duration").getAsString();
			String actionTypeStr = event.getValue("actiontype").getAsString();
			String channelId = event.getValue("channelid").getAsString();
			String actionValuesStr = event.getValue("actionvalues") != null
					? event.getValue("actionvalues").getAsString()
					: "[]";

			// Validate duration
			long duration;
			try {
				duration = Long.parseLong(durationStr);
			} catch (NumberFormatException e) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Die Dauer muss eine Zahl sein.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// Validate action type
			if (!actionTypeStr.equals("infomessage") && !actionTypeStr.equals("custommessage")
					&& !actionTypeStr.equals("kickpoint") && !actionTypeStr.equals("cwdonator")) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Ungültiger Aktionstyp. Erlaubt: infomessage, custommessage, kickpoint, cwdonator",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Parse action values
			ArrayList<ActionValue> actionValues = new ArrayList<>();
			if (!actionValuesStr.trim().isEmpty() && !actionValuesStr.equals("[]")) {
				ObjectMapper mapper = new ObjectMapper();
				try {
					actionValues = mapper.readValue(actionValuesStr,
							mapper.getTypeFactory().constructCollectionType(ArrayList.class, ActionValue.class));
				} catch (JsonProcessingException e) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Ungültiges JSON-Format für Action Values.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}
			}

			// Convert action values back to JSON
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

			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

			// Restart all events to include the new one
			Bot.restartAllEvents();
		}
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
		}
	}
}
