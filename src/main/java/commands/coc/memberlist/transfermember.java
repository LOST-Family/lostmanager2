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

public class transfermember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("transfermember"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Memberverwaltung";

		OptionMapping playeroption = event.getOption("player");
		OptionMapping clanoption = event.getOption("clan");

		if (playeroption == null || clanoption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Beide Parameter sind erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String playertag = playeroption.getAsString();
		String newclantag = clanoption.getAsString();
		Clan newclan = new Clan(newclantag);

		if (!newclan.ExistsDB()) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Dieser Clan ist existiert nicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

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
		if (!clantag.equals("warteliste")) {
			if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer des Clans sein, in dem der Spieler gerade ist, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		} else {
			boolean b = false;
			for (String clantags : DBManager.getAllClans()) {
				if (userexecuted.getClanRoles().get(clantags) == Player.RoleType.ADMIN
						|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.LEADER
						|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.COLEADER) {
					b = true;
					break;
				}
			}
			if (b == false) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		if (!(userexecuted.getClanRoles().get(newclantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(newclantag) == Player.RoleType.LEADER
				|| userexecuted.getClanRoles().get(newclantag) == Player.RoleType.COLEADER)) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer des Clans sein, in den du den Spieler transferieren möchtest, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		if (clantag.equals(newclantag)) {
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Du kannst einen Spieler nicht in den gleichen Clan verschieben.", MessageUtil.EmbedType.ERROR))
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

		DBUtil.executeUpdate("UPDATE clan_members SET clan_tag = ?, clan_role = ? WHERE player_tag = ?", newclantag,
				"member", playertag);

		String desc = "";
		if (!clantag.equals("warteliste")) {
			if (!newclantag.equals("warteliste")) {
				desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde vom Clan "
						+ playerclan.getInfoString() + " zum Clan " + newclan.getInfoString() + " verschoben.";
			} else {
				desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde vom Clan "
						+ playerclan.getInfoString() + " zur Warteliste verschoben.";
			}
		} else {
			desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB())
					+ " wurde von der Warteliste zum Clan " + newclan.getInfoString() + " verschoben.";
		}
		String userid = player.getUser().getUserID();
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		Member member = guild.getMemberById(userid);
		if (member != null) {
			ArrayList<Player> allaccs = player.getUser().getAllLinkedAccounts();
			boolean hasOtherAccountInOldClan = false;
			// Note: hiddencoleaders should not count as elder or higher
			boolean hasOtherElderOrHigherInOldClan = false;
			for (Player acc : allaccs) {
				if (acc.getClanDB() != null) {
					if (acc.getClanDB().getTag().equals(clantag)) {
						hasOtherAccountInOldClan = true;
						if (Player.isElderOrHigher(acc.getRoleDB()) && !acc.isHiddenColeader()) {
							hasOtherElderOrHigherInOldClan = true;
						}
					}
				}
			}
			String memberroleid = playerclan.getRoleID(Clan.Role.MEMBER);
			Role memberrole = guild.getRoleById(memberroleid);
			if (memberrole != null) {
				if (member.getRoles().contains(memberrole)) {
					if (!hasOtherAccountInOldClan) {
						guild.removeRoleFromMember(member, memberrole).queue();
						desc += "\n\n";
						desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + memberroleid + "> genommen.**\n";
					} else {
						desc += "\n\n";
						desc += "**Der User <@" + userid
								+ "> hat noch mindestens einen anderen Account in dem alten Clan, daher behält er die Rolle <@&"
								+ memberroleid + ">.**\n";
					}
				} else {
					if (!hasOtherAccountInOldClan) {
						desc += "\n\n";
						desc += "**Der User <@" + userid + "> hat die Rolle <@&" + memberroleid
								+ "> bereits nicht mehr.**\n";
					} else {
						desc += "\n\n";
						desc += "**Der User <@" + userid
								+ "> hat noch mindestens einen anderen Account in dem alten Clan, hat aber die Rolle <@&"
								+ memberroleid + "> nicht. Gebe sie ihm manuell, falls erwünscht.**\n";
					}
				}
			}
			// Handle elder role for old clan
			String elderroleid = playerclan.getRoleID(Clan.Role.ELDER);
			Role elderrole = guild.getRoleById(elderroleid);
			// Only handle elder role if the player was elder or higher in the old clan
			if (Player.isElderOrHigher(role)) {
				if (elderrole != null) {
					if (member.getRoles().contains(elderrole)) {
						if (!hasOtherElderOrHigherInOldClan) {
							guild.removeRoleFromMember(member, elderrole).queue();
							desc += "\n\n";
							desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + elderroleid + "> genommen.**\n";
						} else {
							desc += "\n\n";
							desc += "**Der User <@" + userid
									+ "> hat noch mindestens einen anderen Account als Ältester oder höher in dem alten Clan, daher behält er die Rolle <@&"
									+ elderroleid + ">.**\n";
						}
					} else {
						if (hasOtherElderOrHigherInOldClan) {
							desc += "\n\n";
							desc += "**Der User <@" + userid
									+ "> hat noch mindestens einen anderen Account als Ältester oder höher in dem alten Clan, hat aber die Rolle <@&"
									+ elderroleid + "> nicht. Gebe sie ihm manuell, falls erwünscht.**\n";
						}
					}
				}
			}
			String newmemberroleid = newclan.getRoleID(Clan.Role.MEMBER);
			Role newmemberrole = guild.getRoleById(newmemberroleid);
			if (newmemberrole != null) {
				if (member.getRoles().contains(newmemberrole)) {
					desc += "\n\n";
					desc += "**Der User <@" + userid + "> hat die Rolle <@&" + newmemberroleid + "> bereits.**\n";
				} else {
					guild.addRoleToMember(member, newmemberrole).queue();
					desc += "\n\n";
					desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + newmemberroleid + "> gegeben.**\n";
				}
			}
		} else {
			desc += "\n\n**Der User <@" + userid
					+ "> existiert nicht auf dem Server. Ihm wurde somit keine Rolle hinzugefügt.**";
		}
		MessageChannelUnion channel = event.getChannel();
		MessageUtil.sendUserPingHidden(channel, userid);

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();

		}, "TransfermemberCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("transfermember"))
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
		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
			Player p = new Player(event.getOption("player").getAsString());
			Clan c = p.getClanDB();
			Command.Choice todelete = null;
			if (c != null) {
				for (Command.Choice choice : choices) {
					if (choice.getAsString().equals(c.getTag())) {
						todelete = choice;
						break;
					}
				}
			}
			if (todelete != null) {
				choices.remove(todelete);
			}

			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
		}, "TransfermemberAutocomplete-" + event.getUser().getId()).start();
	}
}
