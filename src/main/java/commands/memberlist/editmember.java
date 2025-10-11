package commands.memberlist;

import java.util.ArrayList;
import java.util.List;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class editmember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("editmember"))
			return;
		event.deferReply().queue();
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");
		OptionMapping roleoption = event.getOption("role");

		if (playeroption == null || roleoption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();
		String role = roleoption.getAsString();

		Player p = new Player(playertag);
		Clan c = p.getClanDB();

		if (!p.IsLinked()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		if (c == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Spieler ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = c.getTag();
		User userexecuted = new User(event.getUser().getId());
		if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
			event.getHook()
			.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		if (!(role.equals("leader") || role.equals("coLeader") || role.equals("admin") || role.equals("member"))) {
			event.getHook()
					.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Gib eine gültige Rolle an.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (role.equals("leader") && userexecuted.getClanRoles().get(clantag) != Player.RoleType.ADMIN) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Leader hinzuzufügen, musst du Admin sein.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (role.equals("coLeader") && !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Vize-Anführer hinzuzufügen, musst du Admin oder Anführer sein.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		DBUtil.executeUpdate("UPDATE clan_members SET clan_role = ? WHERE player_tag = ?", role, playertag);
		String rolestring = role.equals("leader") ? "Anführer"
				: role.equals("coLeader") ? "Vize-Anführer"
						: role.equals("admin") ? "Ältester" : role.equals("member") ? "Mitglied" : null;
		String desc = null;
		try {
			desc = "Der Spieler " + MessageUtil.unformat(p.getInfoStringDB()) + " im Clan " + c.getInfoString()
					+ " ist nun " + rolestring + ".";
		} catch (Exception e) {
			e.printStackTrace();
		}
		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("editmember"))
			return;

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);

			event.replyChoices(choices).queue(success ->{}, failure -> {});
		}
		if (focused.equals("role")) {
			List<Command.Choice> choices = new ArrayList<>();
			choices.add(new Command.Choice("Anführer", "leader"));
			choices.add(new Command.Choice("Vize-Anführer", "coLeader"));
			choices.add(new Command.Choice("Ältester", "admin"));
			choices.add(new Command.Choice("Mitglied", "member"));
			event.replyChoices(choices).queue(success ->{}, failure -> {});
		}
	}

}
