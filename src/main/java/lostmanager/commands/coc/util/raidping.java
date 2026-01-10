package lostmanager.commands.coc.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class raidping extends ListenerAdapter {

	HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> map = null;

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("raidping"))
			return;
		String title = "Raid-Ping";
		event.deferReply().queue();

		new Thread(() -> {

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();

		User userexecuted = new User(event.getUser().getId());
		boolean bp = false;
		for (String clantags : DBManager.getAllClans()) {
			if (userexecuted.getClanRoles().get(clantags) == Player.RoleType.ADMIN
					|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.LEADER
					|| userexecuted.getClanRoles().get(clantags) == Player.RoleType.COLEADER) {
				bp = true;
				break;
			}
		}
		if (bp == false) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl ausführen zu können.",
							MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Clan clan = new Clan(clantag);

		ArrayList<Player> raidmembers = clan.getRaidMemberList();

		boolean raidactive = clan.RaidActive();

		String desc = "";
		desc += "ausgeführt von " + event.getUser().getAsMention() + "\n";

		if (!raidactive) {
			desc += "\n**Kein Ping, da Raid aktuell nicht aktiv ist.**\n";
		}

		desc += "## Fehlende Raid Angriffe:\n";

		ArrayList<Player> dbmemberlist = clan.getPlayersDB();

		ArrayList<Player> notfinished = new ArrayList<>();
		ArrayList<Player> notdone = new ArrayList<>();

		for (Player dbPlayer : dbmemberlist) {
			// Skip hidden co-leaders as they don't need to be in clan/raid
			if (dbPlayer.isHiddenColeader()) {
				continue;
			}
			
			boolean b = false;
			for (Player raidPlayer : raidmembers) {
				if (raidPlayer.getTag().equals(dbPlayer.getTag())) {
					int currentattacks = raidPlayer.getCurrentRaidAttacks();
					int max = raidPlayer.getCurrentRaidAttackLimit() + raidPlayer.getCurrentRaidbonusAttackLimit();
					if (currentattacks < max) {
						notfinished.add(raidPlayer);
					}
					b = true;
					break;
				}
			}
			if (!b) {
				notdone.add(dbPlayer);
			}
		}
		if (!notdone.isEmpty()) {
			ArrayList<String> allclantags = DBManager.getAllClans();
			ArrayList<Clan> allclans = new ArrayList<>();
			for (String s : allclantags) {
				Clan c = new Clan(s);
				c.getRaidMemberList(); // load from API
				allclans.add(c);
			}
			for (int i = 0; i < notdone.size(); i++) {
				Player p = notdone.get(i);
				for (Clan c : allclans) {
					ArrayList<Player> raidmemberlist = c.getRaidMemberList();
					for (Player t : raidmemberlist) {
						if (t.getTag().equals(p.getTag())) {
							if (!desc.contains("In")) {
								desc += "### In einem anderen Lost-Clan angegriffen:\n";
							}
							desc += t.getNameAPI() + " in " + c.getNameDB() + ": " + t.getCurrentRaidAttacks() + "/"
									+ (t.getCurrentRaidAttackLimit() + t.getCurrentRaidbonusAttackLimit()) + "\n";
							notdone.remove(p);
							i--;
							break;
						}
					}
				}
			}
			for (Player p : notdone) {
				if (!desc.contains("icht angegriffen")) {
					if (raidactive) {
						desc += "### Noch gar nicht angegriffen:\n";
					} else {
						desc += "### Nicht angegriffen:\n";
					}
				}
				desc += p.getNameAPI() + " (<@" + p.getUser().getUserID() + ">)\n";
			}
		}

		notfinished.sort(Comparator.comparingInt(Player::getCurrentRaidAttacks));
		for (Player p : notfinished) {
			int currentattacks = p.getCurrentRaidAttacks();
			int max = p.getCurrentRaidAttackLimit() + p.getCurrentRaidbonusAttackLimit();
			if (!desc.contains("Angriffe")) {
				if (raidactive) {
					desc += "### Noch offene Angriffe:\n";
				} else {
					desc += "### Teil der Angriffe gemacht:\n";
				}
			}
			desc += p.getNameAPI() + " (<@" + p.getUser().getUserID() + ">): " + currentattacks + "/" + max + "\n";
		}

		event.getHook().editOriginal(".").queue(message -> {
			message.delete().queue();
		});
		if (raidactive) {
			event.getChannel().sendMessage(desc).queue();
		} else {
			final String newmessage = desc;
			event.getChannel().sendMessage(".").queue(message -> {
				message.editMessage(newmessage).queue();
			});
		}
		}, "RaidpingCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("raidping"))
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
		}, "RaidpingAutocomplete-" + event.getUser().getId()).start();
	}
}
