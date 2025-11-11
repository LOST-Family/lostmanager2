package commands.memberlist;

import java.util.ArrayList;
import java.util.List;

import datautil.DBManager;
import datautil.DBUtil;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
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

public class removemember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("removemember"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");

		if (playeroption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();

		Player player = new Player(playertag);

		if (!player.IsLinked()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Player.RoleType role = player.getRoleDB();

		Clan playerclan = player.getClanDB();

		if (playerclan == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Dieser Spieler ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = playerclan.getTag();

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

		if (role == Player.RoleType.LEADER && userexecuted.getClanRoles().get(clantag) != Player.RoleType.ADMIN) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Leader zu entfernen, musst du Admin sein.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}
		if (role == Player.RoleType.COLEADER && !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Um jemanden als Vize-Anführer zu entfernen, musst du Admin oder Anführer sein.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clanname = playerclan.getNameDB();

		DBUtil.executeUpdate("DELETE FROM clan_members WHERE player_tag = ?", playertag);
		String desc = "";
		try {
			desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde aus dem Clan " + clanname
					+ " entfernt.";
		} catch (Exception e) {
			e.printStackTrace();
		}

		String userid = player.getUser().getUserID();
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		Member member = guild.getMemberById(userid);
		String memberroleid = playerclan.getRoleID(Clan.Role.MEMBER);
		Role memberrole = guild.getRoleById(memberroleid);
		String elderroleid = playerclan.getRoleID(Clan.Role.ELDER);
		Role elderrole = guild.getRoleById(elderroleid);
		if (member != null) {
			ArrayList<Player> allaccs = player.getUser().getAllLinkedAccounts();
			boolean b = false;
			boolean othereldersameclan = false;
			for (Player acc : allaccs) {
				if (acc.getClanDB() != null) {
					if (acc.getClanDB().getTag().equals(clantag)) {
						b = true;
						if (acc.getRoleDB() == Player.RoleType.ELDER) {
							othereldersameclan = true;
						}
						break;
					}
				}
			}
			if (member.getRoles().contains(memberrole)) {
				if (!b) {
					guild.removeRoleFromMember(member, memberrole).queue();
					desc += "\n\n";
					desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + memberroleid + "> genommen.**\n";
				} else {
					desc += "\n\n";
					desc += "**Der User <@" + userid
							+ "> hat noch mindestens einen anderen Account in dem Clan, daher behält er die Rolle <@&"
							+ memberroleid + ">.**\n";
				}
			} else {
				if (!b) {
					desc += "\n\n";
					desc += "**Der User <@" + userid + "> hat die Rolle <@&" + memberroleid
							+ "> bereits nicht mehr.**\n";
				} else {
					desc += "\n\n";
					desc += "**Der User <@" + userid
							+ "> hat noch mindestens einen anderen Account in dem Clan, hat aber die Rolle <@&"
							+ memberroleid + "> nicht. Gebe sie ihm manuell, falls erwünscht.**\n";
				}
			}
			if (member.getRoles().contains(elderrole)) {
				if (!othereldersameclan) {
					guild.removeRoleFromMember(member, elderrole).queue();
					desc += "\n\n";
					desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + elderroleid + "> genommen.**\n";
				} else {
					desc += "\n\n";
					desc += "**Der User <@" + userid
							+ "> hat noch mindestens einen anderen Account als Ältester in dem Clan, daher behält er die Rolle <@&"
							+ elderroleid + ">.**\n";
				}
			} else {
				if (othereldersameclan) {
					desc += "\n\n";
					desc += "**Der User <@" + userid
							+ "> hat noch mindestens einen anderen Account als Ältester in dem Clan, hat aber die Rolle <@&"
							+ elderroleid + "> nicht. Gebe sie ihm manuell, falls erwünscht.**\n";
				}
			}

			String exmemberroleid = Bot.exmember_roleid;
			Role exmemberrole = guild.getRoleById(exmemberroleid);
			if (member.getRoles().contains(exmemberrole)) {
				desc += "\n\n";
				desc += "**Der User <@" + userid + "> hat die Rolle <@&" + exmemberroleid + "> bereits.**\n";
			} else {
				guild.addRoleToMember(member, exmemberrole).queue();
				desc += "\n\n**Dem User <@" + userid + "> wurde die Rolle <@&" + exmemberroleid + "> hinzugefügt.**";
			}
		} else {
			desc += "\n\n**Der User <@" + userid
					+ "> existiert nicht auf dem Server. Ihm wurde somit keine Rolle hinzugefügt oder entfernt.**";
		}

		MessageChannelUnion channel = event.getChannel();
		MessageUtil.sendUserPingHidden(channel, userid);

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

		}, "RemovememberCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("removemember"))
			return;

		new Thread(() -> {

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("player")) {
			List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);

			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
		}, "RemovememberAutocomplete-" + event.getUser().getId()).start();
	}
}
