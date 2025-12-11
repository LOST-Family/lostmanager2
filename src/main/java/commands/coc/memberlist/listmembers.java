package commands.coc.memberlist;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import datawrapper.Clan;
import datawrapper.Player;
import dbutil.DBManager;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import util.MessageUtil;

public class listmembers extends ListenerAdapter {

	private static final Comparator<Player> NAME_COMPARATOR = (p1, p2) -> {
		String name1 = p1.getNameDB();
		String name2 = p2.getNameDB();
		if (name1 == null && name2 == null) return 0;
		if (name1 == null) return 1;
		if (name2 == null) return -1;
		return name1.compareToIgnoreCase(name2);
	};

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("listmembers"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Memberliste";

			OptionMapping clanOption = event.getOption("clan");

			if (clanOption == null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String clantag = clanOption.getAsString();

			Clan c = new Clan(clantag);

			ArrayList<Player> playerlist = c.getPlayersDB();

			ArrayList<Player> leaders = new ArrayList<>();
			ArrayList<Player> coleaders = new ArrayList<>();
			ArrayList<Player> elders = new ArrayList<>();
			ArrayList<Player> members = new ArrayList<>();
			int totalMembersCount = 0;

			// Separate players by role
			for (Player p : playerlist) {
				if (p.getRoleDB() == Player.RoleType.LEADER) {
					leaders.add(p);
					totalMembersCount++;
				} else if (p.getRoleDB() == Player.RoleType.COLEADER) {
					coleaders.add(p);
					if (!p.isHiddenColeader()) {
						totalMembersCount++;
					}
				} else if (p.getRoleDB() == Player.RoleType.ELDER) {
					elders.add(p);
					totalMembersCount++;
				} else if (p.getRoleDB() == Player.RoleType.MEMBER) {
					members.add(p);
					totalMembersCount++;
				}
			}

			// Sort each role list alphabetically by name
			leaders.sort(NAME_COMPARATOR);
			coleaders.sort(NAME_COMPARATOR);
			elders.sort(NAME_COMPARATOR);
			members.sort(NAME_COMPARATOR);

			// Build display strings
			String leaderlist = "";
			String coleaderlist = "";
			String elderlist = "";
			String memberlist = "";

			for (Player p : leaders) {
				leaderlist += p.getInfoStringDB() + "\n";
			}
			for (Player p : coleaders) {
				if (p.isHiddenColeader()) {
					coleaderlist += p.getInfoStringDB() + " (versteckt)\n";
				} else {
					coleaderlist += p.getInfoStringDB() + "\n";
				}
			}
			for (Player p : elders) {
				elderlist += p.getInfoStringDB() + "\n";
			}
			for (Player p : members) {
				memberlist += p.getInfoStringDB() + "\n";
			}
			String desc = "## " + c.getInfoString() + "\n";
			desc += "**Anführer:**\n";
			desc += leaderlist == "" ? "---\n\n" : MessageUtil.unformat(leaderlist) + "\n";
			desc += "**Vize-Anführer:**\n";
			desc += coleaderlist == "" ? "---\n\n" : MessageUtil.unformat(coleaderlist) + "\n";
			desc += "**Ältester:**\n";
			desc += elderlist == "" ? "---\n\n" : MessageUtil.unformat(elderlist) + "\n";
			desc += "**Mitglied:**\n";
			desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
			desc += "\nInsgesamte Mitglieder des Clans: " + totalMembersCount;

			Button refreshButton = Button.secondary("listmembers_" + clantag, "\u200B")
					.withEmoji(Emoji.fromUnicode("🔁"));


			ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
			String formatiert = jetzt.format(formatter);
			
			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
					.setActionRow(refreshButton).queue();
		}, "ListMembersCommand-" + event.getUser().getId()).start();

	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("listmembers"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);

				event.replyChoices(choices).queue(_ ->{}, _ -> {});
			}
		}, "ListMembersAutocomplete-" + event.getUser().getId()).start();
	}

	
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith("listmembers_"))
			return;

		event.deferEdit().queue();

		String clantag = id.substring("listmembers_".length());
		String title = "Memberliste";

		event.getInteraction().getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Wird geladen...", MessageUtil.EmbedType.LOADING))
				.queue();

		new Thread(new Runnable() {

			@Override
			public void run() {

				Clan c = new Clan(clantag);

				ArrayList<Player> playerlist = c.getPlayersDB();

				ArrayList<Player> leaders = new ArrayList<>();
				ArrayList<Player> coleaders = new ArrayList<>();
				ArrayList<Player> elders = new ArrayList<>();
				ArrayList<Player> members = new ArrayList<>();
				int totalMembersCount = 0;

				// Separate players by role
				for (Player p : playerlist) {
					if (p.getRoleDB() == Player.RoleType.LEADER) {
						leaders.add(p);
						totalMembersCount++;
					} else if (p.getRoleDB() == Player.RoleType.COLEADER) {
						coleaders.add(p);
						if (!p.isHiddenColeader()) {
							totalMembersCount++;
						}
					} else if (p.getRoleDB() == Player.RoleType.ELDER) {
						elders.add(p);
						totalMembersCount++;
					} else if (p.getRoleDB() == Player.RoleType.MEMBER) {
						members.add(p);
						totalMembersCount++;
					}
				}

				// Sort each role list alphabetically by name
				leaders.sort(NAME_COMPARATOR);
				coleaders.sort(NAME_COMPARATOR);
				elders.sort(NAME_COMPARATOR);
				members.sort(NAME_COMPARATOR);

				// Build display strings
				String leaderlist = "";
				String coleaderlist = "";
				String elderlist = "";
				String memberlist = "";

				for (Player p : leaders) {
					leaderlist += p.getInfoStringDB() + "\n";
				}
				for (Player p : coleaders) {
					if (p.isHiddenColeader()) {
						coleaderlist += p.getInfoStringDB() + " (versteckt)\n";
					} else {
						coleaderlist += p.getInfoStringDB() + "\n";
					}
				}
				for (Player p : elders) {
					elderlist += p.getInfoStringDB() + "\n";
				}
				for (Player p : members) {
					memberlist += p.getInfoStringDB() + "\n";
				}
				String desc = "## " + c.getInfoString() + "\n";
				desc += "**Anführer:**\n";
				desc += leaderlist == "" ? "---\n\n" : MessageUtil.unformat(leaderlist) + "\n";
				desc += "**Vize-Anführer:**\n";
				desc += coleaderlist == "" ? "---\n\n" : MessageUtil.unformat(coleaderlist) + "\n";
				desc += "**Ältester:**\n";
				desc += elderlist == "" ? "---\n\n" : MessageUtil.unformat(elderlist) + "\n";
				desc += "**Mitglied:**\n";
				desc += memberlist == "" ? "---\n\n" : MessageUtil.unformat(memberlist) + "\n";
				desc += "\nInsgesamte Mitglieder des Clans: " + totalMembersCount;

				Button refreshButton = Button.secondary("listmembers_" + clantag, "\u200B")
						.withEmoji(Emoji.fromUnicode("🔁"));


				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);
				
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
						.setActionRow(refreshButton).queue();

			}
		}).start();
	}
}
