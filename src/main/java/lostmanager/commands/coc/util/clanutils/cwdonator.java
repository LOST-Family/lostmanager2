package lostmanager.commands.coc.util.clanutils;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
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

			ArrayList<Player> originalList = clan.getWarMemberList();

			if (originalList == null) {
				originalList = clan.getCWLMemberList();
				if (originalList == null) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Dieser Clan ist gerade nicht in einem Clankrieg oder in der Clankriegsliga.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}
			}

			int cwsize = originalList.size();
			ArrayList<Player> warMemberList = new ArrayList<>(originalList);

			// Filter hidden co-leaders
			warMemberList.removeIf(p -> p.isHiddenColeader());

			// Filter signed-off members
			warMemberList.removeIf(p -> {
				MemberSignoff signoff = new MemberSignoff(p.getTag());
				return signoff.isActive();
			});

			if (warMemberList.isEmpty()) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Es sind keine Mitglieder verfügbar, die als Spender eingeteilt werden könnten.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			HashMap<Integer, ArrayList<Tuple<Integer, Integer>>> mappings = getMappings();

			ArrayList<Tuple<Integer, Integer>> currentmap = mappings.get(cwsize);

			if (currentmap == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Für die Kriegsgröße " + cwsize + " ist keine Spendereinteilung hinterlegt.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// If using lists, initialize/sync them
			if (useLists) {
				initializeAndSyncLists(clantag, clan);
			}

			for (Tuple<Integer, Integer> range : currentmap) {
				Player chosen;

				if (useLists) {
					// Pick from list A
					chosen = pickPlayerFromListA(clantag, warMemberList, range, excludeLeaders);
				} else {
					chosen = pickRandomEligible(warMemberList, range, excludeLeaders);
				}

				if (chosen == null) {
					desc += range.getFirst() + "-" + range.getSecond() + ": kein geeigneter Spender verfügbar\n";
					continue;
				}

				int mapposition = chosen.getWarMapPosition();
				warMemberList.remove(chosen);
				if (ping) {
					if (chosen.getUser() != null) {
						desc += range.getFirst() + "-" + range.getSecond() + ": " + chosen.getNameAPI() + "(<@"
								+ chosen.getUser().getUserID() + ">) (Nr. " + mapposition + ")\n";
					} else {
						desc += range.getFirst() + "-" + range.getSecond() + ": " + chosen.getNameAPI()
								+ "(nicht verlinkt) (Nr. " + mapposition + ")\n";
					}
				} else {
					if (chosen.getUser() != null) {
						desc += range.getFirst() + "-" + range.getSecond() + ": " + chosen.getNameAPI() + "(UserID: "
								+ chosen.getUser().getUserID() + ") (Nr. " + mapposition + ")\n";
					} else {
						desc += range.getFirst() + "-" + range.getSecond() + ": " + chosen.getNameAPI()
								+ "(nicht verlinkt) (Nr. " + mapposition + ")\n";
					}
				}
			}

			event.getHook().editOriginal(".").queue(message -> {
				message.delete().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
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
			size5.add(new Tuple<>(1, 3));
			size5.add(new Tuple<>(4, 5));
			ArrayList<Tuple<Integer, Integer>> size10 = new ArrayList<>();
			size10.add(new Tuple<>(1, 5));
			size10.add(new Tuple<>(6, 10));
			ArrayList<Tuple<Integer, Integer>> size15 = new ArrayList<>();
			size15.add(new Tuple<>(1, 7));
			size15.add(new Tuple<>(8, 15));
			ArrayList<Tuple<Integer, Integer>> size20 = new ArrayList<>();
			size20.add(new Tuple<>(1, 10));
			size20.add(new Tuple<>(11, 20));
			ArrayList<Tuple<Integer, Integer>> size25 = new ArrayList<>();
			size25.add(new Tuple<>(1, 9));
			size25.add(new Tuple<>(10, 17));
			size25.add(new Tuple<>(18, 25));
			ArrayList<Tuple<Integer, Integer>> size30 = new ArrayList<>();
			size30.add(new Tuple<>(1, 10));
			size30.add(new Tuple<>(11, 20));
			size30.add(new Tuple<>(21, 30));
			ArrayList<Tuple<Integer, Integer>> size35 = new ArrayList<>();
			size35.add(new Tuple<>(1, 9));
			size35.add(new Tuple<>(10, 18));
			size35.add(new Tuple<>(19, 27));
			size35.add(new Tuple<>(28, 35));
			ArrayList<Tuple<Integer, Integer>> size40 = new ArrayList<>();
			size40.add(new Tuple<>(1, 10));
			size40.add(new Tuple<>(11, 20));
			size40.add(new Tuple<>(21, 30));
			size40.add(new Tuple<>(31, 40));
			ArrayList<Tuple<Integer, Integer>> size45 = new ArrayList<>();
			size45.add(new Tuple<>(1, 9));
			size45.add(new Tuple<>(10, 18));
			size45.add(new Tuple<>(19, 27));
			size45.add(new Tuple<>(28, 36));
			size45.add(new Tuple<>(37, 45));
			ArrayList<Tuple<Integer, Integer>> size50 = new ArrayList<>();
			size50.add(new Tuple<>(1, 10));
			size50.add(new Tuple<>(11, 20));
			size50.add(new Tuple<>(21, 30));
			size50.add(new Tuple<>(31, 40));
			size50.add(new Tuple<>(41, 50));
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
						Collections.addAll(listA, listAData);
					}
					if (listBArray != null) {
						String[] listBData = (String[]) listBArray.getArray();
						Collections.addAll(listB, listBData);
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
		} catch (SQLException e) {
			System.err.println("Error initializing/syncing cwdonator lists: " + e.getMessage());
			System.err.println(e.getMessage());
		}
	}

	/**
	 * Pick a player from List A and move him to List B. If nobody in List A can
	 * donate for this range, List B is merged back into List A once to start a new
	 * cycle. Returns null if no eligible donator exists at all.
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
						Collections.addAll(listA, listAData);
					}
					if (listBArray != null) {
						String[] listBData = (String[]) listBArray.getArray();
						Collections.addAll(listB, listBData);
					}
				}
			}

			// Build a list of eligible players from warMemberList that are in List A
			ArrayList<Player> eligiblePlayers = collectEligibleFromList(warMemberList, listA, map, excludeLeaders);

			// Nobody in List A can donate for this range (list empty, everybody in the
			// range itself, opted out, signed off, ...) => merge List B back into List A
			// and start a new cycle. This happens at most once per pick, so it can never
			// loop, no matter how many ineligible tags are stuck in List A.
			if (eligiblePlayers.isEmpty() && !listB.isEmpty()) {
				listA.addAll(listB);
				listB.clear();
				eligiblePlayers = collectEligibleFromList(warMemberList, listA, map, excludeLeaders);
			}

			if (eligiblePlayers.isEmpty()) {
				return null; // no eligible donator, leave the lists untouched
			}

			Collections.shuffle(eligiblePlayers);
			Player chosen = eligiblePlayers.get(0);

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

			return chosen;
		} catch (Exception e) {
			System.err.println("Error picking player from List A: " + e.getMessage());
			// Fallback: ignore the lists and pick a random eligible player
			return pickRandomEligible(warMemberList, map, excludeLeaders);
		}
	}

	/**
	 * All war members that are still in List A and may donate for the given range
	 */
	private ArrayList<Player> collectEligibleFromList(ArrayList<Player> warMemberList, ArrayList<String> listA,
			Tuple<Integer, Integer> map, boolean excludeLeaders) {
		ArrayList<Player> eligiblePlayers = new ArrayList<>();
		for (Player p : warMemberList) {
			if (listA.contains(p.getTag()) && isEligibleDonator(p, map, excludeLeaders)) {
				eligiblePlayers.add(p);
			}
		}
		return eligiblePlayers;
	}

	/**
	 * Pick a random eligible donator without using the A/B lists. Returns null if
	 * nobody is eligible.
	 */
	private Player pickRandomEligible(ArrayList<Player> warMemberList, Tuple<Integer, Integer> map,
			boolean excludeLeaders) {
		ArrayList<Player> candidates = new ArrayList<>(warMemberList);
		Collections.shuffle(candidates);
		for (Player p : candidates) {
			if (isEligibleDonator(p, map, excludeLeaders)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * A player may donate for a range if he is not in that range himself, did not
	 * opt out of the war and - if requested - is no (co-)leader
	 */
	private boolean isEligibleDonator(Player player, Tuple<Integer, Integer> map, boolean excludeLeaders) {
		int mapposition = player.getWarMapPosition();
		if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
			return false; // would donate for himself
		}
		if (!player.getWarPreference()) {
			return false; // opted out of the war
		}
		return !(excludeLeaders && isLeaderOrCoLeader(player));
	}

	/**
	 * Check if a player is a leader or co-leader
	 */
	private boolean isLeaderOrCoLeader(Player player) {
		Player.RoleType roleDB = player.getRoleDB();
		return roleDB == Player.RoleType.LEADER || roleDB == Player.RoleType.COLEADER;
	}

}
