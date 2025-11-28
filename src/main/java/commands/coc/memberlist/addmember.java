package commands.coc.memberlist;

import java.util.ArrayList;
import java.util.List;

import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import dbutil.DBManager;
import dbutil.DBUtil;
import lostmanager.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class addmember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("addmember"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Memberverwaltung";

			OptionMapping clanOption = event.getOption("clan");
			OptionMapping playeroption = event.getOption("player");
			OptionMapping roleoption = event.getOption("role");

			if (clanOption == null || playeroption == null || roleoption == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Alle Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String playertag = playeroption.getAsString();
			String clantag = clanOption.getAsString();
			String role = roleoption.getAsString();
			Clan c = new Clan(clantag);

			if (!c.ExistsDB()) {
				event.getHook()
						.editOriginalEmbeds(
								MessageUtil.buildEmbed(title, "Dieser Clan existiert nicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			User userexecuted = new User(event.getUser().getId());
			if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (!(role.equals("leader") || role.equals("coLeader") || role.equals("hiddencoleader") || role.equals("admin") || role.equals("member"))) {
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
			if (role.equals("hiddencoleader") && !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als versteckten Vize-Anführer hinzuzufügen, musst du Admin oder Anführer sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			Player p = new Player(playertag);

			if (!p.IsLinked()) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (new Player(playertag).getClanDB() != null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Dieser Spieler ist bereits in einem Clan.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			DBUtil.executeUpdate("INSERT INTO clan_members (player_tag, clan_tag, clan_role) VALUES (?, ?, ?)", playertag,
					clantag, role);
			String rolestring = role.equals("leader") ? "Anführer"
					: role.equals("coLeader") ? "Vize-Anführer"
							: role.equals("hiddencoleader") ? "Vize-Anführer (versteckt)"
									: role.equals("admin") ? "Ältester" : role.equals("member") ? "Mitglied" : null;

			String desc = "";
			try {
				desc += "Der Spieler " + MessageUtil.unformat(p.getInfoStringDB()) + " wurde erfolgreich dem Clan "
						+ new Clan(clantag).getInfoString() + " als " + rolestring + " hinzugefügt.";
			} catch (Exception e) {
				e.printStackTrace();
			}

			String userid = p.getUser().getUserID();
			Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
			if (guild == null) {
				desc += "\n\n**Fehler: Der Discord-Server wurde nicht gefunden.**";
			} else {
				Member member = guild.getMemberById(userid);
				String memberroleid = c.getRoleID(Clan.Role.MEMBER);
				Role memberrole = guild.getRoleById(memberroleid);
				String elderroleid = c.getRoleID(Clan.Role.ELDER);
				Role elderrole = guild.getRoleById(elderroleid);
				if (member != null) {
					// Don't assign member role if player is added as hiddencoleader
					if (!role.equals("hiddencoleader")) {
						if (memberrole != null) {
							if (member.getRoles().contains(memberrole)) {
								desc += "\n\n**Der User <@" + userid + "> hat bereits die Rolle <@&" + memberroleid + ">.**";
							} else {
								guild.addRoleToMember(member, memberrole).queue();
								desc += "\n\n**Dem User <@" + userid + "> wurde die Rolle <@&" + memberroleid + "> hinzugefügt.**";
							}
						} else {
							desc += "\n\n**Die Member-Rolle für diesen Clan ist nicht konfiguriert.**";
						}
					}
					
					// Handle elder role assignment for elder or higher roles
					if (Player.isElderOrHigherString(role)) {
						if (elderrole != null) {
							if (member.getRoles().contains(elderrole)) {
								desc += "\n\n**Der User <@" + userid + "> hat bereits die Rolle <@&" + elderroleid + ">.**";
							} else {
								guild.addRoleToMember(member, elderrole).queue();
								desc += "\n\n**Dem User <@" + userid + "> wurde die Rolle <@&" + elderroleid + "> hinzugefügt.**";
							}
						} else {
							desc += "\n\n**Die Elder-Rolle für diesen Clan ist nicht konfiguriert.**";
						}
					}
				} else {
					desc += "\n\n**Der User <@" + userid
							+ "> existiert nicht auf dem Server. Ihm wurde somit keine Rolle hinzugefügt.**";
				}
			}

			MessageChannelUnion channel = event.getChannel();
			MessageUtil.sendUserPingHidden(channel, userid);

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
		}, "AddMemberCommand-" + event.getUser().getId()).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("addmember"))
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
			if (focused.equals("player")) {
				List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.NOTINCLAN);

				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
			if (focused.equals("role")) {
				List<Command.Choice> choices = new ArrayList<>();
				choices.add(new Command.Choice("Anführer", "leader"));
				choices.add(new Command.Choice("Vize-Anführer", "coLeader"));
				choices.add(new Command.Choice("Vize-Anführer (versteckt)", "hiddencoleader"));
				choices.add(new Command.Choice("Ältester", "admin"));
				choices.add(new Command.Choice("Mitglied", "member"));
				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
		}, "AddMemberAutocomplete-" + event.getUser().getId()).start();
	}
}
