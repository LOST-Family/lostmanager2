package lostmanager.commands.coc.memberlist;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.Player.RoleType;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class memberstatus extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("memberstatus"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Memberstatus";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();
		
		OptionMapping disableRolecheckOption = event.getOption("disable_rolecheck");
		boolean disableRolecheck = disableRolecheckOption != null && "true".equals(disableRolecheckOption.getAsString());

		Clan c = new Clan(clantag);

		ArrayList<Player> playerlistdb = c.getPlayersDB();

		ArrayList<String> taglistdb = new ArrayList<>();
		playerlistdb.forEach(p -> taglistdb.add(p.getTag()));

		ArrayList<Player> playerlistapi = c.getPlayersAPI();

		ArrayList<String> taglistapi = new ArrayList<>();
		playerlistapi.forEach(p -> taglistapi.add(p.getTag()));

		ArrayList<Player> membernotinclan = new ArrayList<>();
		ArrayList<Player> inclannotmember = new ArrayList<>();

		for (String s : taglistdb) {
			if (!taglistapi.contains(s)) {
				Player p = new Player(s);
				if (!p.isHiddenColeader()) {
					membernotinclan.add(p);
				}
			}
		}

		for (String s : taglistapi) {
			if (!taglistdb.contains(s)) {
				inclannotmember.add(new Player(s));
			}
		}

		String wrongrolestr = "";

		if (!disableRolecheck) {
			for (String tag : taglistapi) {
				if (taglistdb.contains(tag)) {
					Player p = new Player(tag);
					RoleType roleapi = p.getRoleAPI();
					RoleType roledb = p.getRoleDB();
					if (roledb == RoleType.LEADER) {
						roledb = RoleType.COLEADER;
					}
					if (roleapi == RoleType.LEADER) {
						continue;
					}
					if (roledb != roleapi) {
						wrongrolestr += p.getInfoStringDB() + ": \n";
						wrongrolestr += "- Ingame: " + getRoleString(roleapi) + "\n- Datenbank: " + getRoleString(roledb)
								+ "\n\n";
					}
				}
			}
		}

		String membernotinclanstr = "";

		for (Player p : membernotinclan) {
			membernotinclanstr += p.getInfoStringDB() + "\n";
		}

		String inclannotmemberstr = "";

		for (Player p : inclannotmember) {
			inclannotmemberstr += p.getInfoStringAPI() + "\n";
		}

		String desc = "## " + c.getInfoString() + "\n";

		desc += "**Mitglied, ingame nicht im Clan:**\n";
		desc += membernotinclanstr == "" ? "---\n\n" : MessageUtil.unformat(membernotinclanstr) + "\n";
		desc += "**Kein Mitglied, ingame im Clan:**\n";
		desc += inclannotmemberstr == "" ? "---\n\n" : MessageUtil.unformat(inclannotmemberstr) + "\n";
		
		if (!disableRolecheck) {
			desc += "**Im Clan, falsche Rolle:**\n";
			desc += wrongrolestr == "" ? "---\n\n" : MessageUtil.unformat(wrongrolestr) + "\n";
		}

		// Add signed-off members section
		String signedOffStr = "";
		for (Player player : playerlistdb) {
			MemberSignoff signoff = new MemberSignoff(player.getTag());
			if (signoff.isActive()) {
				signedOffStr += player.getInfoStringDB() + " - ";
				if (signoff.isUnlimited()) {
					signedOffStr += "unbegrenzt";
				} else {
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
					signedOffStr += "bis " + signoff.getEndDate().toLocalDateTime().format(formatter);
				}
				signedOffStr += "\n";
			}
		}
		desc += "**Abgemeldete Mitglieder:**\n";
		desc += signedOffStr == "" ? "---\n\n" : MessageUtil.unformat(signedOffStr) + "\n";

		String buttonId = "memberstatus_" + clantag + (disableRolecheck ? "_norolecheck" : "");
		Button refreshButton = Button.secondary(buttonId, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

		ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
		String formatiert = jetzt.format(formatter);

		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
				"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();

		}, "MemberstatusCommand-" + event.getUser().getId()).start();

	}
	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("memberstatus"))
			return;

		new Thread(() -> {

		String focused = event.getFocusedOption().getName();
		String input = event.getFocusedOption().getValue();

		if (focused.equals("clan")) {
			List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		} else if (focused.equals("disable_rolecheck")) {
			List<Command.Choice> choices = new ArrayList<>();
			if ("true".startsWith(input.toLowerCase())) {
				choices.add(new Command.Choice("true", "true"));
			}
			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}
		}, "MemberstatusAutocomplete-" + event.getUser().getId()).start();
	}
	public String getRoleString(RoleType role) {
		switch (role) {
		case LEADER:
			return "Leader";
		case COLEADER:
			return "CoLeader";
		case ELDER:
			return "Elder";
		case MEMBER:
			return "Member";
		default:
			return null;
		// egal
		}
	}

	@SuppressWarnings("null")
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("memberstatus_"))
			return;

		event.deferEdit().queue();

		// Parse button ID: memberstatus_<clantag> or memberstatus_<clantag>_norolecheck
		String idAfterPrefix = id.substring("memberstatus_".length());
		boolean disableRolecheck = idAfterPrefix.endsWith("_norolecheck");
		String clantag;
		if (disableRolecheck) {
			clantag = idAfterPrefix.substring(0, idAfterPrefix.length() - "_norolecheck".length());
		} else {
			clantag = idAfterPrefix;
		}
		
		String title = "Memberstatus";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		new Thread(new Runnable() {

			@Override
			public void run() {

				Clan c = new Clan(clantag);

				ArrayList<Player> playerlistdb = c.getPlayersDB();

				ArrayList<String> taglistdb = new ArrayList<>();
				playerlistdb.forEach(p -> taglistdb.add(p.getTag()));

				ArrayList<Player> playerlistapi = c.getPlayersAPI();

				ArrayList<String> taglistapi = new ArrayList<>();
				playerlistapi.forEach(p -> taglistapi.add(p.getTag()));

				ArrayList<Player> membernotinclan = new ArrayList<>();
				ArrayList<Player> inclannotmember = new ArrayList<>();

				for (String s : taglistdb) {
					if (!taglistapi.contains(s)) {
						Player p = new Player(s);
						if (!p.isHiddenColeader()) {
							membernotinclan.add(p);
						}
					}
				}

				for (String s : taglistapi) {
					if (!taglistdb.contains(s)) {
						inclannotmember.add(new Player(s));
					}
				}

				String wrongrolestr = "";

				if (!disableRolecheck) {
					for (String tag : taglistapi) {
						if (taglistdb.contains(tag)) {
							Player p = new Player(tag);
							RoleType roleapi = p.getRoleAPI();
							RoleType roledb = p.getRoleDB();
							if (roledb == RoleType.LEADER) {
								roledb = RoleType.COLEADER;
							}
							if (roleapi == RoleType.LEADER) {
								continue;
							}
							if (roledb != roleapi) {
								wrongrolestr += p.getInfoStringDB() + ": \n";
								wrongrolestr += "- Ingame: " + getRoleString(roleapi) + "\n- Datenbank: "
										+ getRoleString(roledb) + "\n\n";
							}
						}
					}
				}

				String membernotinclanstr = "";

				for (Player p : membernotinclan) {
					membernotinclanstr += p.getInfoStringDB() + "\n";
				}

				String inclannotmemberstr = "";

				for (Player p : inclannotmember) {
					inclannotmemberstr += p.getInfoStringAPI() + "\n";
				}

				String desc = "## " + c.getInfoString() + "\n";

				desc += "**Mitglied, ingame nicht im Clan:**\n";
				desc += membernotinclanstr == "" ? "---\n\n" : MessageUtil.unformat(membernotinclanstr) + "\n";
				desc += "**Kein Mitglied, ingame im Clan:**\n";
				desc += inclannotmemberstr == "" ? "---\n\n" : MessageUtil.unformat(inclannotmemberstr) + "\n";
				
				if (!disableRolecheck) {
					desc += "**Im Clan, falsche Rolle:**\n";
					desc += wrongrolestr == "" ? "---\n\n" : MessageUtil.unformat(wrongrolestr) + "\n";
				}

				// Add signed-off members section
				String signedOffStr = "";
				for (Player player : playerlistdb) {
					MemberSignoff signoff = new MemberSignoff(player.getTag());
					if (signoff.isActive()) {
						signedOffStr += player.getInfoStringDB() + " - ";
						if (signoff.isUnlimited()) {
							signedOffStr += "unbegrenzt";
						} else {
							DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
							signedOffStr += "bis " + signoff.getEndDate().toLocalDateTime().format(formatter);
						}
						signedOffStr += "\n";
					}
				}
				desc += "**Abgemeldete Mitglieder:**\n";
				desc += signedOffStr == "" ? "---\n\n" : MessageUtil.unformat(signedOffStr) + "\n";

				String buttonId = "memberstatus_" + clantag + (disableRolecheck ? "_norolecheck" : "");
				Button refreshButton = Button.secondary(buttonId, "\u200B")
						.withEmoji(Emoji.fromUnicode("🔁"));

				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);

				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO,
						"Zuletzt aktualisiert am " + formatiert)).setActionRow(refreshButton).queue();

			}
		}).start();
	}

}
