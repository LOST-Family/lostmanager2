package lostmanager.commands.coc.memberlist;

import java.util.ArrayList;
import java.util.List;

import lostmanager.Bot;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class editmember extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("editmember"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
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

			// Get old role before update - need the actual string value, not just RoleType
			// because hiddencoleader and coLeader both map to RoleType.COLEADER
			String oldRole = null;
			String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
			try (java.sql.PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, playertag);
				try (java.sql.ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						oldRole = rs.getString("clan_role");
					}
				}
			} catch (java.sql.SQLException e) {
				e.printStackTrace();
			}

			// Check if the executor has permission to modify the player's current role
			// This prevents co-leaders from modifying other co-leaders or leaders
			Player.RoleType oldRoleType = oldRole != null && oldRole.equals("leader") ? Player.RoleType.LEADER
					: oldRole != null && (oldRole.equals("coLeader") || oldRole.equals("hiddencoleader"))
							? Player.RoleType.COLEADER
							: oldRole != null && oldRole.equals("admin") ? Player.RoleType.ELDER
									: oldRole != null && oldRole.equals("member") ? Player.RoleType.MEMBER : null;

			if (oldRoleType == Player.RoleType.LEADER && userexecuted.getClanRoles().get(clantag) != Player.RoleType.ADMIN) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Leader zu bearbeiten, musst du Admin sein.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
			if (oldRoleType == Player.RoleType.COLEADER && !(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Um jemanden als Vize-Anführer zu bearbeiten, musst du Admin oder Anführer sein.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			DBUtil.executeUpdate("UPDATE clan_members SET clan_role = ? WHERE player_tag = ?", role, playertag);
			String rolestring = role.equals("leader") ? "Anführer"
					: role.equals("coLeader") ? "Vize-Anführer"
							: role.equals("hiddencoleader") ? "Vize-Anführer (versteckt)"
									: role.equals("admin") ? "Ältester" : role.equals("member") ? "Mitglied" : null;
			String desc = null;
			try {
				desc = "Der Spieler " + MessageUtil.unformat(p.getInfoStringDB()) + " im Clan " + c.getInfoString()
						+ " ist nun " + rolestring + ".";
			} catch (Exception e) {
				e.printStackTrace();
			}

			// Handle Discord role management
			String userid = p.getUser().getUserID();
			Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
			if (guild != null) {
				Member member = guild.getMemberById(userid);
				if (member != null) {
					String elderroleid = c.getRoleID(Clan.Role.ELDER);
					Role elderrole = guild.getRoleById(elderroleid);
					
					// wasElder tracks if old role was elder or higher (should have elder discord role)
					boolean wasElderOrHigher = Player.isElderOrHigherString(oldRole);
					boolean isNowElderOrHigher = Player.isElderOrHigherString(role);
					
					if (elderrole != null) {
						if (!wasElderOrHigher && isNowElderOrHigher) {
							// Adding elder role when promoting to elder or higher
							if (!member.getRoles().contains(elderrole)) {
								guild.addRoleToMember(member, elderrole).queue();
								desc += "\n\n**Dem User <@" + userid + "> wurde die Rolle <@&" + elderroleid + "> hinzugefügt.**";
							} else {
								desc += "\n\n**Der User <@" + userid + "> hat bereits die Rolle <@&" + elderroleid + ">.**";
							}
						} else if (wasElderOrHigher && !isNowElderOrHigher) {
							// Removing elder role only when demoting to member - check if user has other elder+ accounts in same clan
							// Note: hiddencoleaders should not count as elder or higher
							ArrayList<Player> allaccs = p.getUser().getAllLinkedAccounts();
							boolean otherElderOrHigherSameClan = false;
							for (Player acc : allaccs) {
								if (!acc.getTag().equals(playertag) && acc.getClanDB() != null) {
									if (acc.getClanDB().getTag().equals(clantag)) {
										if (Player.isElderOrHigher(acc.getRoleDB()) && !acc.isHiddenColeader()) {
											otherElderOrHigherSameClan = true;
										}
									}
								}
							}
							if (member.getRoles().contains(elderrole)) {
								if (!otherElderOrHigherSameClan) {
									guild.removeRoleFromMember(member, elderrole).queue();
									desc += "\n\n**Dem User <@" + userid + "> wurde die Rolle <@&" + elderroleid + "> genommen.**";
								} else {
									desc += "\n\n**Der User <@" + userid
											+ "> hat noch mindestens einen anderen Account als Ältester oder höher in dem Clan, daher behält er die Rolle <@&"
											+ elderroleid + ">.**";
								}
							} else {
								if (otherElderOrHigherSameClan) {
									desc += "\n\n**Der User <@" + userid
											+ "> hat noch mindestens einen anderen Account als Ältester oder höher in dem Clan, hat aber die Rolle <@&"
											+ elderroleid + "> nicht. Gebe sie ihm manuell, falls erwünscht.**";
								}
							}
						}
					} else {
						if (isNowElderOrHigher) {
							desc += "\n\n**Die Elder-Rolle für diesen Clan ist nicht konfiguriert.**";
						}
					}
				} else {
					desc += "\n\n**Der User <@" + userid + "> existiert nicht auf dem Server. Ihm wurde somit keine Rolle hinzugefügt oder entfernt.**";
				}
			} else {
				desc += "\n\n**Fehler: Der Discord-Server wurde nicht gefunden.**";
			}
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
		}, "EditMemberCommand-" + event.getUser().getId()).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("editmember"))
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
		}, "EditMemberAutocomplete-" + event.getUser().getId()).start();
	}

}
