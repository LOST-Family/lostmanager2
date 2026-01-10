package lostmanager.commands.coc.memberlist;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

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
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Beide Parameter sind erforderlich!",
						MessageUtil.EmbedType.ERROR)).queue();
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
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Dieser Spieler ist nicht verlinkt.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			Player.RoleType role = player.getRoleDB();

			Clan playerclan = player.getClanDB();

			if (playerclan == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Dieser Spieler ist in keinem Clan.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String clantag = playerclan.getTag();

			User userexecuted = new User(event.getUser().getId());

			// Check permission for old clan
			boolean hasOldClanPermission = false;
			if (userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER) {
				hasOldClanPermission = true;
			}

			// Check permission for new clan
			boolean hasNewClanPermission = false;
			if (userexecuted.getClanRoles().get(newclantag) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(newclantag) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(newclantag) == Player.RoleType.COLEADER) {
				hasNewClanPermission = true;
			}

			// If user doesn't have permission in either clan, reject immediately
			if (!hasOldClanPermission && !hasNewClanPermission) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst mindestens Vize-Anführer in einem der beiden Clans sein, um diesen Befehl ausführen zu können.",
						MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			// If user has permission in only one clan, create approval request
			if (!hasOldClanPermission || !hasNewClanPermission) {
				// Determine which clan needs to approve (the one where initiator lacks
				// permission)
				String clanNeedingApproval = !hasOldClanPermission ? clantag : newclantag;

				// Create approval buttons with the clan that needs to approve
				String buttonData = encodeButtonData(playertag, clantag, newclantag, clanNeedingApproval);
				Button acceptButton = Button.success("tm_accept_" + buttonData, "Akzeptieren");
				Button declineButton = Button.danger("tm_decline_" + buttonData, "Ablehnen");

				String approvalMsg = "**Transfer-Anfrage:**\n\n";
				approvalMsg += "Spieler: " + MessageUtil.unformat(player.getInfoStringDB()) + "\n";
				approvalMsg += "Von: " + playerclan.getInfoString() + "\n";
				approvalMsg += "Nach: " + newclan.getInfoString() + "\n\n";

				if (!hasOldClanPermission) {
					approvalMsg += "Du hast keine Berechtigung im ursprünglichen Clan.\n";
					approvalMsg += "Ein Vize-Anführer oder höher aus " + playerclan.getInfoString()
							+ " muss dies genehmigen.";
				} else {
					approvalMsg += "Du hast keine Berechtigung im Ziel-Clan.\n";
					approvalMsg += "Ein Vize-Anführer oder höher aus " + newclan.getInfoString()
							+ " muss dies genehmigen.";
				}

				event.getHook().editOriginal(approvalMsg).setActionRow(acceptButton, declineButton).queue();
				return;
			}

			if (clantag.equals(newclantag)) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du kannst einen Spieler nicht in den gleichen Clan verschieben.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Additional role-based permission checks for immediate execution
			if (role == Player.RoleType.LEADER && userexecuted.getClanRoles().get(clantag) != Player.RoleType.ADMIN) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
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

			// User has permission in both clans, execute transfer immediately
			MessageChannelUnion channel = event.getChannel();
			MessageUtil.sendUserPingHidden(channel, player.getUser().getUserID());

			executeTransfer(event.getHook(), player, playerclan, newclan, playertag, clantag, newclantag, title, null);

		}, "TransfermemberCommand-" + event.getUser().getId()).start();

	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("tm_accept_") && !id.startsWith("tm_decline_"))
			return;

		event.deferReply().queue();

		new Thread(() -> {
			String title = "Memberverwaltung";
			boolean isAccept = id.startsWith("tm_accept_");

			// Decode button data
			String encodedData = id.substring(id.startsWith("tm_accept_") ? 10 : 11);
			String[] data;
			try {
				data = decodeButtonData(encodedData);
				// Backward compatibility: old buttons may only have 3 fields
				if (data.length < 4) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Diese Anfrage wurde mit einer älteren Version erstellt und ist abgelaufen. Bitte erstelle eine neue Transfer-Anfrage.",
							MessageUtil.EmbedType.ERROR)).queue();
					// Disable buttons
					event.getMessage().editMessageComponents().queue();
					return;
				}
			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String playertag = data[0];
			String oldclantag = data[1];
			String newclantag = data[2];
			String clanNeedingApproval = data[3];

			Player player = new Player(playertag);
			Clan oldclan = new Clan(oldclantag);
			Clan newclan = new Clan(newclantag);

			// Verify the approver has permission in the clan that needs approval
			User approver = new User(event.getUser().getId());
			boolean hasApprovalClanPermission = false;

			if (approver.getClanRoles().get(clanNeedingApproval) == Player.RoleType.ADMIN
					|| approver.getClanRoles().get(clanNeedingApproval) == Player.RoleType.LEADER
					|| approver.getClanRoles().get(clanNeedingApproval) == Player.RoleType.COLEADER) {
				hasApprovalClanPermission = true;
			}

			// Check if approver has permission in the clan that needs to approve
			if (!hasApprovalClanPermission) {
				Clan approvalClan = new Clan(clanNeedingApproval);
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "<@" + event.getUser().getId()
						+ ">:\nDu hast keine Berechtigung, diese Anfrage zu bearbeiten. Du musst mindestens Vize-Anführer in "
						+ approvalClan.getInfoString() + " sein.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			if (isAccept) {
				// Execute the transfer
				executeTransfer(event.getHook(), player, oldclan, newclan, playertag, oldclantag, newclantag, title,
						event.getUser().getId());
			} else {
				// Decline the transfer
				String desc = "Transfer abgelehnt von <@" + event.getUser().getId() + ">.\n\n";
				desc += "Spieler: " + MessageUtil.unformat(player.getInfoStringDB()) + "\n";
				desc += "Von: " + oldclan.getInfoString() + "\n";
				desc += "Nach: " + newclan.getInfoString();

				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.ERROR))
						.queue();
			}

			// Disable buttons after action
			event.getMessage().editMessageComponents().queue();

		}, "TransfermemberButton-" + event.getUser().getId()).start();
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

	/**
	 * Encodes transfer data for button IDs. Uses simple underscore-separated format
	 * if under 100 chars (Discord limit). Format:
	 * playertag_oldclantag_newclantag_clanNeedingApproval
	 */
	private String encodeButtonData(String playertag, String oldclantag, String newclantag,
			String clanNeedingApproval) {
		String basicData = playertag + "_" + oldclantag + "_" + newclantag + "_" + clanNeedingApproval;

		// Check if we need Base64 encoding (Discord button ID limit is 100 chars)
		// We add prefix "tm_accept_" (10 chars) or "tm_decline_" (11 chars), so check
		// against 89 chars
		if (basicData.length() > 89) {
			// Use Base64 encoding for long IDs
			String data = playertag + "|" + oldclantag + "|" + newclantag + "|" + clanNeedingApproval;
			return "b64_"
					+ Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes(StandardCharsets.UTF_8));
		}

		return basicData;
	}

	/**
	 * Decodes button data string. Returns array: [playertag, oldclantag,
	 * newclantag, clanNeedingApproval] For backward compatibility, may return only
	 * 3 elements if decoding old button data.
	 */
	private String[] decodeButtonData(String encoded) {
		// Check if it's Base64 encoded
		if (encoded.startsWith("b64_")) {
			byte[] decoded = Base64.getUrlDecoder().decode(encoded.substring(4));
			String data = new String(decoded, StandardCharsets.UTF_8);
			return data.split("\\|");
		}

		// Simple underscore-separated format
		// Split without limit to handle both old (3 parts) and new (4 parts) formats
		return encoded.split("_");
	}

	/**
	 * Executes the actual transfer of a player from one clan to another.
	 */
	private void executeTransfer(net.dv8tion.jda.api.interactions.InteractionHook hook, Player player, Clan playerclan,
			Clan newclan, String playertag, String clantag, String newclantag, String title, String approverId) {

		Player.RoleType role = player.getRoleDB();

		DBUtil.executeUpdate("UPDATE clan_members SET clan_tag = ?, clan_role = ? WHERE player_tag = ?", newclantag,
				"member", playertag);

		String desc = "";
		if (approverId != null) {
			desc += "Transfer genehmigt von <@" + approverId + ">.\n\n";
		}
		desc += "Der Spieler " + MessageUtil.unformat(player.getInfoStringDB()) + " wurde vom Clan "
				+ playerclan.getInfoString() + " zum Clan " + newclan.getInfoString() + " verschoben.";
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
							desc += "**Dem User <@" + userid + "> wurde die Rolle <@&" + elderroleid
									+ "> genommen.**\n";
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

		hook.editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS)).queue();
	}
}
