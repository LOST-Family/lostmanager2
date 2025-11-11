package commands.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import datautil.DBManager;
import datawrapper.Clan;
import datawrapper.Player;
import datawrapper.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;
import util.Tuple;

public class cwdonator extends ListenerAdapter {

	HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> map = null;

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("cwdonator"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "CW-Spender";

		OptionMapping clanOption = event.getOption("clan");

		if (clanOption == null) {
			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, "Der Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		String clantag = clanOption.getAsString();

		User userexecuted = new User(event.getUser().getId());
		HashMap<String, Player.RoleType> clanroles = userexecuted.getClanRoles();
		boolean ping = false;
		for (String key : clanroles.keySet()) {
			if (clanroles.get(key) == Player.RoleType.ADMIN || clanroles.get(key) == Player.RoleType.LEADER
					|| clanroles.get(key) == Player.RoleType.COLEADER) {
				ping = true;
				break;
			}
		}

		String desc = "";
		desc += "ausgeführt von " + event.getUser().getAsMention() + "\n";
		desc += "## " + title + "\n";
		desc += "Folgende Mitglieder wurden zufällig als Spender ausgewählt: \n\n";

		Clan clan = new Clan(clantag);

		ArrayList<Player> warMemberList = clan.getWarMemberList();

		if (warMemberList == null) {
			warMemberList = clan.getCWLMemberList();
			if (warMemberList == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Dieser Clan ist gerade nicht in einem Clankrieg oder in der Clankriegsliga.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}
		}

		int cwsize = warMemberList.size();

		HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> mappings = getMappings();

		ArrayList<Tuple<Integer, Integer>> currentmap = mappings.get(cwsize);

		for (Tuple<Integer, Integer> map : currentmap) {
			Collections.shuffle(warMemberList);
			Player chosen = warMemberList.get(0);
			int mapposition = chosen.getWarMapPosition();
			int i = 0;
			while (true) {
				if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
					chosen = warMemberList.get(i);
					mapposition = chosen.getWarMapPosition();
					i++;
					continue;
				}
				if (chosen.getWarPreference() == false) {
					chosen = warMemberList.get(i);
					mapposition = chosen.getWarMapPosition();
					i++;
					continue;
				}
				break;
			}
			warMemberList.remove(chosen);
			if (ping) {
				if (chosen.getUser() != null) {
					desc += map.getFirst() + "-" + map.getSecond() + ": " + chosen.getNameAPI() + "(<@"
							+ chosen.getUser().getUserID() + ">) (Nr. " + mapposition + ")\n";
				} else {
					desc += map.getFirst() + "-" + map.getSecond() + ": " + chosen.getNameAPI()
							+ "(nicht verlinkt) (Nr. " + mapposition + ")\n";
				}
			} else {
				if (chosen.getUser() != null) {
					desc += map.getFirst() + "-" + map.getSecond() + ": " + chosen.getNameAPI() + "(UserID: "
							+ chosen.getUser().getUserID() + ") (Nr. " + mapposition + ")\n";
				} else {
					desc += map.getFirst() + "-" + map.getSecond() + ": " + chosen.getNameAPI()
							+ "(nicht verlinkt) (Nr. " + mapposition + ")\n";
				}
			}
		}

		event.getHook().editOriginal(".").queue(message -> {
			message.delete().queue();
		});
		event.getChannel().sendMessage(desc).queue();

		}, "CwdonatorCommand-" + event.getUser().getId()).start();

	}
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("cwdonator"))
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
		}, "CwdonatorAutocomplete-" + event.getUser().getId()).start();
	}
	private HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> getMappings() {
		if (map == null) {
			HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> mappings = new HashMap<>();
			ArrayList<Tuple<Integer, Integer>> size5 = new ArrayList<>();
			size5.add(new Tuple<Integer, Integer>(1, 3));
			size5.add(new Tuple<Integer, Integer>(4, 5));
			ArrayList<Tuple<Integer, Integer>> size10 = new ArrayList<>();
			size10.add(new Tuple<Integer, Integer>(1, 5));
			size10.add(new Tuple<Integer, Integer>(6, 10));
			ArrayList<Tuple<Integer, Integer>> size15 = new ArrayList<>();
			size15.add(new Tuple<Integer, Integer>(1, 7));
			size15.add(new Tuple<Integer, Integer>(8, 15));
			ArrayList<Tuple<Integer, Integer>> size20 = new ArrayList<>();
			size20.add(new Tuple<Integer, Integer>(1, 10));
			size20.add(new Tuple<Integer, Integer>(11, 20));
			ArrayList<Tuple<Integer, Integer>> size25 = new ArrayList<>();
			size25.add(new Tuple<Integer, Integer>(1, 9));
			size25.add(new Tuple<Integer, Integer>(10, 17));
			size25.add(new Tuple<Integer, Integer>(18, 25));
			ArrayList<Tuple<Integer, Integer>> size30 = new ArrayList<>();
			size30.add(new Tuple<Integer, Integer>(1, 10));
			size30.add(new Tuple<Integer, Integer>(11, 20));
			size30.add(new Tuple<Integer, Integer>(21, 30));
			ArrayList<Tuple<Integer, Integer>> size35 = new ArrayList<>();
			size35.add(new Tuple<Integer, Integer>(1, 9));
			size35.add(new Tuple<Integer, Integer>(10, 18));
			size35.add(new Tuple<Integer, Integer>(19, 27));
			size35.add(new Tuple<Integer, Integer>(28, 35));
			ArrayList<Tuple<Integer, Integer>> size40 = new ArrayList<>();
			size40.add(new Tuple<Integer, Integer>(1, 10));
			size40.add(new Tuple<Integer, Integer>(11, 20));
			size40.add(new Tuple<Integer, Integer>(21, 30));
			size40.add(new Tuple<Integer, Integer>(31, 40));
			ArrayList<Tuple<Integer, Integer>> size45 = new ArrayList<>();
			size45.add(new Tuple<Integer, Integer>(1, 9));
			size45.add(new Tuple<Integer, Integer>(10, 18));
			size45.add(new Tuple<Integer, Integer>(19, 27));
			size45.add(new Tuple<Integer, Integer>(28, 36));
			size45.add(new Tuple<Integer, Integer>(37, 45));
			ArrayList<Tuple<Integer, Integer>> size50 = new ArrayList<>();
			size50.add(new Tuple<Integer, Integer>(1, 10));
			size50.add(new Tuple<Integer, Integer>(11, 20));
			size50.add(new Tuple<Integer, Integer>(21, 30));
			size50.add(new Tuple<Integer, Integer>(31, 40));
			size50.add(new Tuple<Integer, Integer>(41, 50));
			mappings.put(5, size5);
			mappings.put(10, size10);
			mappings.put(15, size15);
			mappings.put(20, size20);
			mappings.put(25, size25);
			mappings.put(30, size30);
			mappings.put(35, size35);
			mappings.put(40, size40);
			mappings.put(45, size45);
			mappings.put(50, size50);
			map = mappings;
		}
		return map;
	}

}
