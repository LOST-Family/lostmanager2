package lostmanager.commands.coc.kickpoints;

import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

public class clanconfig extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("clanconfig"))
			return;
		String title = "Clanconfig";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.replyEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();

		User userexecuted = new User(event.getUser().getId());
		if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		Clan c = new Clan(clantag);

		if (c.getInfoString() != null) {

		} else {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Gib einen gültigen Clan an!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		TextInput kpdays;
		TextInput kpmax;
		TextInput wins;
		if (c.getDaysKickpointsExpireAfter() != null) {
			kpdays = TextInput.create("days", "Gültigkeitsdauer von Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 60").setMinLength(1).setValue(c.getDaysKickpointsExpireAfter() + "").build();
		} else {
			kpdays = TextInput.create("days", "Gültigkeitsdauer von Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 60").setMinLength(1).build();
		}

		if (c.getMaxKickpoints() != null) {
			kpmax = TextInput.create("max", "Maximale Anzahl an Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 9").setMinLength(1).setValue(c.getMaxKickpoints() + "").build();
		} else {
			kpmax = TextInput.create("max", "Maximale Anzahl an Kickpunkten", TextInputStyle.SHORT)
					.setPlaceholder("z.B. 9").setMinLength(1).build();
		}

		if (c.getMinSeasonWins() != null) {
			wins = TextInput.create("wins", "Minimum Season Wins", TextInputStyle.SHORT).setPlaceholder("z.B. 70")
					.setMinLength(1).setValue(c.getMinSeasonWins() + "").build();
		} else {
			wins = TextInput.create("wins", "Minimum Season Wins", TextInputStyle.SHORT).setPlaceholder("z.B. 70")
					.setMinLength(1).build();
		}

		Modal modal = Modal.create("clanconfig_" + c.getTag(), "Clanconfig bearbeiten")
				.addComponents(ActionRow.of(kpdays), ActionRow.of(kpmax), ActionRow.of(wins)).build();

		event.replyModal(modal).queue();

	}

	@SuppressWarnings("null")
	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		if (event.getModalId().startsWith("clanconfig")) {
			event.deferReply().queue();

			new Thread(() -> {
				String title = "Clanconfig";
				String winsstr = event.getValue("wins").getAsString();
				String daysstr = event.getValue("days").getAsString();
				String maxstr = event.getValue("max").getAsString();
				int wins = -1;
				int days = -1;
				int max = -1;
				try {
					wins = Integer.valueOf(winsstr);
					days = Integer.valueOf(daysstr);
					max = Integer.valueOf(maxstr);
				} catch (Exception ex) {
					event.getHook().editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Es müssen Zahlen sein.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String clantag = event.getModalId().split("_")[1];

				Clan c = new Clan(clantag);

				if (c.getDaysKickpointsExpireAfter() == null) {
					DBUtil.executeUpdate(
							"INSERT INTO clan_settings (clan_tag, max_kickpoints, kickpoints_expire_after_days, min_season_wins) VALUES (?, ?, ?, ?)",
							clantag, max, days, wins);
				} else {
					DBUtil.executeUpdate(
							"UPDATE clan_settings SET max_kickpoints = ?, kickpoints_expire_after_days = ?, min_season_wins = ? WHERE clan_tag = ?",
							max, days, wins, clantag);
				}

				String desc = "### Die Clan-Settings wurden bearbeitet.\n";
				desc += "Clan: " + c.getInfoString() + "\n";
				desc += "Gültigkeitsdauer von Kickpunkten: " + c.getDaysKickpointsExpireAfter() + " Tage\n";
				desc += "Maximale Anzahl an Kickpunkten: " + c.getMaxKickpoints() + "\n";

				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
						.queue();
			}, "ClanconfigModal-" + event.getUser().getId()).start();
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("clanconfig"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
		}, "ClanconfigAutocomplete-" + event.getUser().getId()).start();
	}

}
