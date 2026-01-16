package lostmanager.commands.coc.util;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
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

public class cwdonator extends ListenerAdapter {

	HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> map = null;

	@SuppressWarnings("null")
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

			// Get new optional parameters
			OptionMapping excludeLeadersOption = event.getOption("exclude_leaders");
			boolean excludeLeaders = excludeLeadersOption != null && "true".equals(excludeLeadersOption.getAsString());

			OptionMapping useListsOption = event.getOption("use_lists");
			boolean useLists = useListsOption != null && "true".equals(useListsOption.getAsString());

			ArrayList<String> allclantags = DBManager.getAllClans();

			if (!allclantags.contains(clantag) && useLists) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Listenfunktion kann nur auf registrierte Clans ausgeführt werden.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

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

			// If using lists, initialize/sync them
			if (useLists) {
				initializeAndSyncLists(clantag, clan);
			}

			for (Tuple<Integer, Integer> map : currentmap) {
				Player chosen = null;

				if (useLists) {
					// Pick from list A
					chosen = pickPlayerFromListA(clantag, warMemberList, map, excludeLeaders);
				} else {
					// Original random logic
					Collections.shuffle(warMemberList);
					chosen = warMemberList.get(0);
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
						// Check exclude_leaders if enabled
						if (excludeLeaders && isLeaderOrCoLeader(chosen)) {
							chosen = warMemberList.get(i);
							mapposition = chosen.getWarMapPosition();
							i++;
							continue;
						}
						break;
					}
				}

				if (chosen == null) {
					continue; // Should not happen but safety check
				}

				int mapposition = chosen.getWarMapPosition();
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

	@SuppressWarnings("null")
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
			} else if (focused.equals("exclude_leaders") || focused.equals("use_lists")) {
				List<Command.Choice> choices = new ArrayList<>();
				if ("true".startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice("true", "true"));
				}
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

	/**
	 * Initialize and synchronize cwdonator lists for a clan
	 */
	private void initializeAndSyncLists(String clanTag, Clan clan) {
		try {
			// Check if lists exist
			String checkSql = "SELECT list_a, list_b FROM cwdonator_lists WHERE clan_tag = ?";
			try (Connection conn = lostmanager.dbutil.Connection.getConnection();
					PreparedStatement stmt = conn.prepareStatement(checkSql)) {
				stmt.setString(1, clanTag);
				ResultSet rs = stmt.executeQuery();

				ArrayList<String> listA = new ArrayList<>();
				ArrayList<String> listB = new ArrayList<>();
				boolean exists = false;

				if (rs.next()) {
					exists = true;
					Array listAArray = rs.getArray("list_a");
					Array listBArray = rs.getArray("list_b");
					if (listAArray != null) {
						String[] listAData = (String[]) listAArray.getArray();
						for (String tag : listAData) {
							listA.add(tag);
						}
					}
					if (listBArray != null) {
						String[] listBData = (String[]) listBArray.getArray();
						for (String tag : listBData) {
							listB.add(tag);
						}
					}
				}

				// Get current clan members
				ArrayList<Player> clanMembers = clan.getPlayersDB();
				ArrayList<String> currentTags = new ArrayList<>();
				for (Player p : clanMembers) {
					if (!p.isHiddenColeader()) {
						currentTags.add(p.getTag());
					}
				}

				if (!exists) {
					// Create new lists with all current members in List A
					listA.addAll(currentTags);
					String insertSql = "INSERT INTO cwdonator_lists (clan_tag, list_a, list_b) VALUES (?, ?::text[], ?::text[])";
					try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
						insertStmt.setString(1, clanTag);
						insertStmt.setArray(2, conn.createArrayOf("text", listA.toArray()));
						insertStmt.setArray(3, conn.createArrayOf("text", new String[0]));
						insertStmt.executeUpdate();
					}
				} else {
					// Sync lists with current members
					// Add missing players to List A
					for (String tag : currentTags) {
						if (!listA.contains(tag) && !listB.contains(tag)) {
							listA.add(tag);
						}
					}

					// Remove players not in clan from both lists
					listA.removeIf(tag -> !currentTags.contains(tag));
					listB.removeIf(tag -> !currentTags.contains(tag));

					// Update database
					String updateSql = "UPDATE cwdonator_lists SET list_a = ?::text[], list_b = ?::text[] WHERE clan_tag = ?";
					try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
						updateStmt.setArray(1, conn.createArrayOf("text", listA.toArray()));
						updateStmt.setArray(2, conn.createArrayOf("text", listB.toArray()));
						updateStmt.setString(3, clanTag);
						updateStmt.executeUpdate();
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error initializing/syncing cwdonator lists: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Pick a player from List A, move to List B, handle list swap if needed
	 */
	private Player pickPlayerFromListA(String clanTag, ArrayList<Player> warMemberList, Tuple<Integer, Integer> map,
			boolean excludeLeaders) {
		try (Connection conn = lostmanager.dbutil.Connection.getConnection()) {
			// Get current lists
			String selectSql = "SELECT list_a, list_b FROM cwdonator_lists WHERE clan_tag = ?";
			ArrayList<String> listA = new ArrayList<>();
			ArrayList<String> listB = new ArrayList<>();

			try (PreparedStatement stmt = conn.prepareStatement(selectSql)) {
				stmt.setString(1, clanTag);
				ResultSet rs = stmt.executeQuery();
				if (rs.next()) {
					Array listAArray = rs.getArray("list_a");
					Array listBArray = rs.getArray("list_b");
					if (listAArray != null) {
						String[] listAData = (String[]) listAArray.getArray();
						for (String tag : listAData) {
							listA.add(tag);
						}
					}
					if (listBArray != null) {
						String[] listBData = (String[]) listBArray.getArray();
						for (String tag : listBData) {
							listB.add(tag);
						}
					}
				}
			}

			// If List A is empty, swap List B to List A
			if (listA.isEmpty()) {
				listA.addAll(listB);
				listB.clear();
			}

			// Build a list of eligible players from warMemberList that are in List A
			ArrayList<Player> eligiblePlayers = new ArrayList<>();
			for (Player p : warMemberList) {
				if (listA.contains(p.getTag())) {
					// Check constraints
					int mapposition = p.getWarMapPosition();
					if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
						continue; // Skip if in donation range
					}
					if (!p.getWarPreference()) {
						continue; // Skip if opted out
					}
					// Skip leaders if excludeLeaders is enabled
					if (excludeLeaders && isLeaderOrCoLeader(p)) {
						continue;
					}
					eligiblePlayers.add(p);
				}
			}

			// Pick a player
			Player chosen = null;
			if (!eligiblePlayers.isEmpty()) {
				Collections.shuffle(eligiblePlayers);

				chosen = eligiblePlayers.get(0);
				
				// Defensive check: should not happen since leaders are filtered upfront, but kept as safeguard
				if (isLeaderOrCoLeader(chosen) && excludeLeaders) {
					listA.remove(chosen.getTag());
					listB.add(chosen.getTag());

					// Update database
					String updateSql = "UPDATE cwdonator_lists SET list_a = ?::text[], list_b = ?::text[] WHERE clan_tag = ?";
					try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
						updateStmt.setArray(1, conn.createArrayOf("text", listA.toArray()));
						updateStmt.setArray(2, conn.createArrayOf("text", listB.toArray()));
						updateStmt.setString(3, clanTag);
						updateStmt.executeUpdate();
					}

					// Recursive call to pick again
					return pickPlayerFromListA(clanTag, warMemberList, map, excludeLeaders);
				}
			} else {
				listA.addAll(listB);
				listB.clear();

				String updateSql = "UPDATE cwdonator_lists SET list_a = ?::text[], list_b = ?::text[] WHERE clan_tag = ?";
				try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
					updateStmt.setArray(1, conn.createArrayOf("text", listA.toArray()));
					updateStmt.setArray(2, conn.createArrayOf("text", listB.toArray()));
					updateStmt.setString(3, clanTag);
					updateStmt.executeUpdate();
				}
				// Recursive call to pick again
				return pickPlayerFromListA(clanTag, warMemberList, map, excludeLeaders);
			}

			if (chosen != null) {
				// Move chosen player from List A to List B
				listA.remove(chosen.getTag());
				listB.add(chosen.getTag());

				// Update database
				String updateSql = "UPDATE cwdonator_lists SET list_a = ?::text[], list_b = ?::text[] WHERE clan_tag = ?";
				try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
					updateStmt.setArray(1, conn.createArrayOf("text", listA.toArray()));
					updateStmt.setArray(2, conn.createArrayOf("text", listB.toArray()));
					updateStmt.setString(3, clanTag);
					updateStmt.executeUpdate();
				}
			}

			return chosen;
		} catch (Exception e) {
			System.err.println("Error picking player from List A: " + e.getMessage());
			e.printStackTrace();
			// Fallback: find an eligible player, respecting excludeLeaders if enabled
			if (!warMemberList.isEmpty()) {
				Collections.shuffle(warMemberList);
				for (Player p : warMemberList) {
					// Skip leaders if excludeLeaders is enabled
					if (excludeLeaders && isLeaderOrCoLeader(p)) {
						continue;
					}
					// Skip players in donation range or opted out
					int mapposition = p.getWarMapPosition();
					if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
						continue;
					}
					if (!p.getWarPreference()) {
						continue;
					}
					return p;
				}
				// If no eligible player found, return any non-leader
				if (excludeLeaders) {
					for (Player p : warMemberList) {
						if (!isLeaderOrCoLeader(p)) {
							return p;
						}
					}
				}
				// Last resort: return first player
				return warMemberList.get(0);
			}
			return null;
		}
	}

	/**
	 * Check if a player is a leader or co-leader
	 */
	private boolean isLeaderOrCoLeader(Player player) {
		Player.RoleType roleDB = player.getRoleDB();
		return roleDB == Player.RoleType.LEADER || roleDB == Player.RoleType.COLEADER;
	}

}
