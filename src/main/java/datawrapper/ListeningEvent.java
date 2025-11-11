package datawrapper;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import datautil.DBUtil;
import net.dv8tion.jda.api.entities.TextChannel;

public class ListeningEvent {

	public enum LISTENINGTYPE {
		CW, RAID, CWLDAY, CS, FIXTIMEINTERVAL, CWLEND
	}

	public enum ACTIONTYPE {
		INFOMESSAGE, CUSTOMMESSAGE, KICKPOINT, CWDONATOR, FILLER
	}

	private Long id;
	private String clan_tag;
	private LISTENINGTYPE listeningtype;
	private Long durationuntilend; // in ms
	private ACTIONTYPE actiontype;
	private String channelid;
	private ArrayList<ActionValue> actionvalues;

	private Long timestamptofire;

	public ListeningEvent refreshData() {
		clan_tag = null;
		listeningtype = null;
		durationuntilend = null;
		actiontype = null;
		channelid = null;
		actionvalues = null;
		timestamptofire = null;
		return this;
	}

	public ListeningEvent(long id) {
		this.id = id;
	}

	public ListeningEvent setClanTag(String clan_tag) {
		this.clan_tag = clan_tag;
		return this;
	}

	public ListeningEvent setListeningType(LISTENINGTYPE type) {
		this.listeningtype = type;
		return this;
	}

	public ListeningEvent setDurationUntilEnd(Long l) {
		this.durationuntilend = l;
		return this;
	}

	public ListeningEvent setActionType(ACTIONTYPE type) {
		this.actiontype = type;
		return this;
	}

	public ListeningEvent setChannelID(String channelid) {
		this.channelid = channelid;
		return this;
	}

	public ListeningEvent setActionValues(ArrayList<ActionValue> list) {
		this.actionvalues = list;
		return this;
	}

	public long getID() {
		return id;
	}

	public Long getId() {
		return id;
	}

	public String getClanTag() {
		if (clan_tag == null) {
			clan_tag = DBUtil.getValueFromSQL("SELECT clan_tag FROM listening_events WHERE id = ?", String.class, id);
		}
		return clan_tag;
	}

	public LISTENINGTYPE getListeningType() {
		if (listeningtype == null) {
			String type = DBUtil.getValueFromSQL("SELECT listeningtype FROM listening_events WHERE id = ?",
					String.class, id);
			listeningtype = type.equals("cw") ? LISTENINGTYPE.CW
					: type.equals("raid") ? LISTENINGTYPE.RAID
							: type.equals("cwl") ? LISTENINGTYPE.CWLDAY : type.equals("cs") ? LISTENINGTYPE.CS : null;
		}
		return listeningtype;
	}

	public long getDurationUntilEnd() {
		if (durationuntilend == null) {
			durationuntilend = DBUtil.getValueFromSQL("SELECT listeningvalue FROM listening_events WHERE id = ?",
					Long.class, id);
		}
		return durationuntilend;
	}

	public ACTIONTYPE getActionType() {
		if (actiontype == null) {
			String type = DBUtil.getValueFromSQL("SELECT actiontype FROM listening_events WHERE id = ?", String.class,
					id);
			actiontype = type.equals("infomessage") ? ACTIONTYPE.INFOMESSAGE
					: type.equals("custommessage") ? ACTIONTYPE.CUSTOMMESSAGE
							: type.equals("kickpoint") ? ACTIONTYPE.KICKPOINT
									: type.equals("cwdonator") ? ACTIONTYPE.CWDONATOR
											: type.equals("filler") ? ACTIONTYPE.FILLER : null;
		}
		return actiontype;
	}

	public String getChannelID() {
		if (channelid == null) {
			channelid = DBUtil.getValueFromSQL("SELECT channel_id FROM listening_events WHERE id = ?", String.class,
					id);
		}
		return channelid;
	}

	public ArrayList<ActionValue> getActionValues() {
		if (actionvalues == null) {
			String json = DBUtil.getValueFromSQL("SELECT actionvalues FROM listening_events WHERE id = ?", String.class,
					id);
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionvalues = mapper.readValue(json, new TypeReference<ArrayList<ActionValue>>() {
				});
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
		return actionvalues;
	}

	public Long getTimestamp() {
		if (timestamptofire == null) {
			// Special case for "start" triggers (duration = -1)
			if (getDurationUntilEnd() == -1) {
				// Start triggers don't have a specific timestamp - they fire on state change
				return Long.MAX_VALUE; // Return far future to prevent scheduling
			}

			Clan c = new Clan(clan_tag);
			Long endTimeMillis = null;
			switch (getListeningType()) {
			case CS:
				endTimeMillis = c.getCGEndTimeMillis();
				if (endTimeMillis != null) {
					timestamptofire = endTimeMillis - getDurationUntilEnd();
				}
				break;
			case CW:
				endTimeMillis = c.getCWEndTimeMillis();
				if (endTimeMillis != null) {
					timestamptofire = endTimeMillis - getDurationUntilEnd();
				}
				break;
			case CWLDAY:
				endTimeMillis = c.getCWLDayEndTimeMillis();
				if (endTimeMillis != null) {
					timestamptofire = endTimeMillis - getDurationUntilEnd();
				}
				break;
			case RAID:
				endTimeMillis = c.getRaidEndTimeMillis();
				if (endTimeMillis != null) {
					timestamptofire = endTimeMillis - getDurationUntilEnd();
				}
				break;
			case FIXTIMEINTERVAL:
				timestamptofire = getDurationUntilEnd();
				break;
			case CWLEND:

				break;
			default:
				break;
			}

			// If timestamptofire is still null, return a far future time to prevent
			// scheduling errors
			if (timestamptofire == null) {
				System.err.println(
						"Warning: Unable to calculate timestamp for listening event. endTime may be missing from API response.");
				return Long.MAX_VALUE;
			}
		}
		return timestamptofire;
	}

	public void fireEvent() {
		System.out.println("Starting fireEvent for event ID " + getId() + ", type: " + getListeningType() + ", clan: "
				+ getClanTag());

		try {
			Clan clan = new Clan(getClanTag());

			switch (getListeningType()) {
			case CS:
				handleClanGamesEvent(clan);
				break;

			case CW:
				handleClanWarEvent(clan);
				break;

			case CWLDAY:
				handleCWLDayEvent(clan);
				break;

			case RAID:
				handleRaidEvent(clan);
				break;

			case FIXTIMEINTERVAL:
				// For custom timed events
				break;

			default:
				break;
			}

			System.out.println("Completed fireEvent for event ID " + getId());
		} catch (Exception e) {
			System.err.println("Error in fireEvent for event ID " + getId() + ": " + e.getMessage());
			throw e; // Re-throw to be caught by retry logic
		}
	}

	private void handleClanGamesEvent(Clan clan) {
		// Get threshold from action values (default 4000)
		int threshold = 4000;
		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null) {
				threshold = av.getValue().intValue();
				break;
			}
		}

		// Get before/after values from achievements database
		java.sql.Timestamp startTime = java.sql.Timestamp.from(lostmanager.Bot.getPrevious22thAt7am().toInstant());
		java.sql.Timestamp endTime = java.sql.Timestamp.from(lostmanager.Bot.getPrevious28thAt12pm().toInstant() // Actual
																													// end
																													// time
																													// (12:00)
		);

		// Check if we're firing before the actual end time (12:00)
		// If so, fetch fresh data from API instead of using stored data
		boolean beforeActualEnd = System.currentTimeMillis() < endTime.getTime();

		// Get all players in clan
		ArrayList<Player> players = clan.getPlayersDB();
		StringBuilder message = new StringBuilder();
		message.append("## Clan Games Results (Threshold: " + threshold + ")\n\n");

		boolean hasViolations = false;
		for (Player p : players) {
			int difference = 0;

			if (beforeActualEnd) {
				// Fetch fresh data from API
				try {
					org.json.JSONObject playerJson = new JSONObject(p.getJson());
					org.json.JSONArray achievements = playerJson.getJSONArray("achievements");

					// Find clan games achievement
					for (int i = 0; i < achievements.length(); i++) {
						org.json.JSONObject achievement = achievements.getJSONObject(i);
						if (achievement.getString("name").equals("Games Champion")) {
							int currentPoints = achievement.getInt("value");

							// Get start value from database
							String sql = "SELECT data FROM achievement_data WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? ORDER BY time LIMIT 1";
							Integer pointsStart = DBUtil.getValueFromSQL(sql, Integer.class, p.getTag(), startTime);

							if (pointsStart != null) {
								difference = currentPoints - pointsStart;
							}
							break;
						}
					}
				} catch (Exception e) {
					System.err
							.println("Error fetching fresh API data for player " + p.getTag() + ": " + e.getMessage());
					continue;
				}
			} else {
				// Use stored data from database
				String sql = "SELECT data FROM achievement_data WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? ORDER BY time LIMIT 1";
				Integer pointsStart = DBUtil.getValueFromSQL(sql, Integer.class, p.getTag(), startTime);
				Integer pointsEnd = DBUtil.getValueFromSQL(sql, Integer.class, p.getTag(), endTime);

				if (pointsStart != null && pointsEnd != null) {
					difference = pointsEnd - pointsStart;
				} else {
					continue; // Skip if no data
				}
			}

			// Check against threshold
			if (difference < threshold) {
				hasViolations = true;
				message.append(p.getNameAPI()).append(": ").append(difference).append(" points");
				if (p.getUser() != null) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append("\n");

				// Handle action type
				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(p, "Clan Games nicht erreicht (" + difference + " points)");
				}
			}
		}

		if (hasViolations || getActionType() == ACTIONTYPE.INFOMESSAGE) {
			sendMessageToChannel(message.toString());
		}
	}

	private void handleClanWarEvent(Clan clan) {
		if (!clan.isCWActive()) {
			return;
		}

		org.json.JSONObject cwJson = clan.getCWJson();
		String state = cwJson.getString("state");

		// Check if it's a "filler" or "cwdonator" action at start
		boolean isFillerAction = getActionType() == ACTIONTYPE.FILLER;
		boolean isCWDonatorAction = getActionType() == ACTIONTYPE.CWDONATOR;

		if (!isFillerAction && !isCWDonatorAction) {
			// Also check action values for backward compatibility
			for (ActionValue av : getActionValues()) {
				if (av.getSaved() == ActionValue.kind.type && av.getType() == ActionValue.ACTIONVALUETYPE.FILLER) {
					isFillerAction = true;
					break;
				}
			}
		}

		if ((isFillerAction || isCWDonatorAction)) {
			if (isCWDonatorAction) {
				handleCWDonator(clan, cwJson);
			} else {
				handleCWFiller(clan, cwJson);
			}
		} else if (state.equals("inWar") || state.equals("warEnded")) {
			handleCWMissedAttacks(clan, cwJson);
		}
	}

	private void handleCWDonator(Clan clan, org.json.JSONObject cwJson) {
		// Execute cwdonator command logic automatically
		ArrayList<Player> warMemberList = clan.getWarMemberList();

		if (warMemberList == null) {
			return; // Can't execute if no war members
		}

		int cwsize = warMemberList.size();

		// Use the same mapping logic as cwdonator command
		HashMap<Integer, ArrayList<util.Tuple<Integer, Integer>>> mappings = getCWDonatorMappings();
		ArrayList<util.Tuple<Integer, Integer>> currentmap = mappings.get(cwsize);

		if (currentmap == null) {
			sendMessageToChannel("CW-Donator kann nicht ausgeführt werden: Keine Zuordnung für Kriegsgröße " + cwsize);
			return;
		}

		StringBuilder message = new StringBuilder();
		message.append("## CW-Spender (automatisch)\n\n");
		message.append("Folgende Mitglieder wurden zufällig als Spender ausgewählt:\n\n");

		for (util.Tuple<Integer, Integer> map : currentmap) {
			java.util.Collections.shuffle(warMemberList);
			Player chosen = warMemberList.get(0);
			int mapposition = chosen.getWarMapPosition();
			int i = 0;
			while (i < warMemberList.size()) {
				chosen = warMemberList.get(i);
				mapposition = chosen.getWarMapPosition();

				// Skip if position is in the donation range
				if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
					i++;
					continue;
				}
				// Skip if opted out
				if (!chosen.getWarPreference()) {
					i++;
					continue;
				}
				break;
			}

			warMemberList.remove(chosen);
			message.append(map.getFirst()).append("-").append(map.getSecond()).append(": ").append(chosen.getNameAPI());
			if (chosen.getUser() != null) {
				message.append(" (<@").append(chosen.getUser().getUserID()).append(">)");
			} else {
				message.append(" (nicht verlinkt)");
			}
			message.append(" (Nr. ").append(mapposition).append(")\n");
		}

		sendMessageToChannel(message.toString());
	}

	private HashMap<Integer, ArrayList<util.Tuple<Integer, Integer>>> getCWDonatorMappings() {
		// Same mapping logic as cwdonator command
		HashMap<Integer, ArrayList<util.Tuple<Integer, Integer>>> map = new HashMap<>();

		map.put(50, new ArrayList<>(java.util.Arrays.asList(new util.Tuple<>(1, 5), new util.Tuple<>(6, 10),
				new util.Tuple<>(11, 15), new util.Tuple<>(16, 20), new util.Tuple<>(21, 25))));
		map.put(40, new ArrayList<>(java.util.Arrays.asList(new util.Tuple<>(1, 5), new util.Tuple<>(6, 10),
				new util.Tuple<>(11, 15), new util.Tuple<>(16, 20))));
		map.put(30, new ArrayList<>(
				java.util.Arrays.asList(new util.Tuple<>(1, 5), new util.Tuple<>(6, 10), new util.Tuple<>(11, 15))));
		map.put(25, new ArrayList<>(java.util.Arrays.asList(new util.Tuple<>(1, 5), new util.Tuple<>(6, 10))));
		map.put(20, new ArrayList<>(java.util.Arrays.asList(new util.Tuple<>(1, 5), new util.Tuple<>(6, 10))));
		map.put(15, new ArrayList<>(java.util.Arrays.asList(new util.Tuple<>(1, 5))));
		map.put(10, new ArrayList<>(java.util.Arrays.asList(new util.Tuple<>(1, 3))));

		return map;
	}

	private void handleCWFiller(Clan clan, org.json.JSONObject cwJson) {
		// Get war members and check preferences
		org.json.JSONObject clanData = cwJson.getJSONObject("clan");
		org.json.JSONArray members = clanData.getJSONArray("members");

		// Calculate war end time to associate fillers with this specific war
		String endTimeStr = cwJson.getString("endTime");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
		Instant instant = Instant.from(formatter.parse(endTimeStr));
		java.time.OffsetDateTime endTime = java.time.OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);

		StringBuilder message = new StringBuilder();
		message.append("## CW War Preferences Check\n\n");
		message.append("The following members are opted OUT but were still added to war:\n\n");

		boolean hasOptedOut = false;
		ArrayList<String> fillerTags = new ArrayList<>();

		// Check each war member to see if they have opted out
		for (int i = 0; i < members.length(); i++) {
			org.json.JSONObject member = members.getJSONObject(i);
			String tag = member.getString("tag");
			
			try {
				Player player = new Player(tag);
				
				// Check if this player has opted out (warPreference = "out")
				boolean isOptedOut = !player.getWarPreference();
				
				if (isOptedOut) {
					hasOptedOut = true;
					fillerTags.add(tag);
					message.append("- ").append(player.getNameAPI());
					if (player.getUser() != null) {
						message.append(" (<@").append(player.getUser().getUserID()).append(">)");
					}
					message.append("\n");
				}
			} catch (Exception e) {
				System.err.println("Error checking war preference for player " + tag + ": " + e.getMessage());
			}
		}

		// Save fillers to database for this war
		if (!fillerTags.isEmpty()) {
			java.sql.Timestamp endTimeTs = java.sql.Timestamp.from(endTime.toInstant());
			for (String tag : fillerTags) {
				// Store with war end time as identifier
				DBUtil.executeUpdate(
						"INSERT INTO cw_fillers (clan_tag, player_tag, war_end_time) VALUES (?, ?, ?) ON CONFLICT (clan_tag, player_tag, war_end_time) DO NOTHING",
						clan.getTag(), tag, endTimeTs);
			}
		}

		if (hasOptedOut) {
			sendMessageToChannel(message.toString());
		}
	}

	private void handleCWMissedAttacks(Clan clan, org.json.JSONObject cwJson) {
		org.json.JSONObject clanData = cwJson.getJSONObject("clan");
		org.json.JSONArray members = clanData.getJSONArray("members");
		int attacksPerMember = cwJson.getInt("attacksPerMember");

		// Get required attacks from action values (default to attacksPerMember from
		// API)
		int requiredAttacks = attacksPerMember;
		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null) {
				requiredAttacks = av.getValue().intValue();
				break;
			}
		}

		// Get war end time to match with fillers
		String endTimeStr = cwJson.getString("endTime");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
		Instant instant = Instant.from(formatter.parse(endTimeStr));
		java.time.OffsetDateTime endTime = java.time.OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
		java.sql.Timestamp endTimeTs = java.sql.Timestamp.from(endTime.toInstant());

		// Get list of fillers for this war
		String fillerSql = "SELECT player_tag FROM cw_fillers WHERE clan_tag = ? AND war_end_time = ?";
		ArrayList<String> fillerTags = DBUtil.getArrayListFromSQL(fillerSql, String.class, clan.getTag(), endTimeTs);

		StringBuilder message = new StringBuilder();
		message.append("## Clan War - Missed Attacks (Required: " + requiredAttacks + ")\n\n");

		boolean hasMissedAttacks = false;
		for (int i = 0; i < members.length(); i++) {
			org.json.JSONObject member = members.getJSONObject(i);
			String tag = member.getString("tag");
			String name = member.getString("name");

			int attacks = 0;
			if (member.has("attacks")) {
				attacks = member.getJSONArray("attacks").length();
			}

			if (attacks < requiredAttacks) {
				// Check if this player is a filler
				boolean isFiller = fillerTags.contains(tag);

				// Skip fillers from missed attacks reporting
				if (isFiller) {
					continue;
				}

				hasMissedAttacks = true;
				Player p = new Player(tag);
				message.append("- ").append(name).append(": ").append(attacks).append("/").append(requiredAttacks)
						.append(" attacks");
				if (p.getUser() != null) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append("\n");

				// Handle kickpoint action
				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(p, "CW Angriffe verpasst (" + attacks + "/" + requiredAttacks + ")");
				}
			}
		}

		if (hasMissedAttacks) {
			sendMessageToChannel(message.toString());
		}

		// Clean up old fillers after war ends
		DBUtil.executeUpdate("DELETE FROM cw_fillers WHERE clan_tag = ? AND war_end_time = ?", clan.getTag(),
				endTimeTs);
	}

	private void handleCWLDayEvent(Clan clan) {
		if (!clan.isCWLActive()) {
			return;
		}

		// Get CWL group data
		org.json.JSONObject cwlJson = clan.getCWLJson();
		org.json.JSONArray rounds = cwlJson.getJSONArray("rounds");

		// Find the last completed day by checking rounds in order
		// CWL has 7 days, each day has up to 4 wars (warTags)
		// We need to find the most recent completed war for our clan
		int lastCompletedRound = -1;
		String lastCompletedWarTag = null;
		org.json.JSONObject cachedWarData = null;

		// Iterate through all 7 rounds to find the last completed one
		for (int r = 0; r < rounds.length(); r++) {
			org.json.JSONArray warTags = rounds.getJSONObject(r).getJSONArray("warTags");

			// Check each war in this round to find our clan's war
			for (int w = 0; w < warTags.length(); w++) {
				String warTag = warTags.getString(w);
				try {
					org.json.JSONObject warData = Clan.getCWLDayJson(warTag);

					// Check if this war involves our clan (could be in "clan" or "opponent" field)
					org.json.JSONObject clanData = warData.getJSONObject("clan");
					org.json.JSONObject opponentData = warData.getJSONObject("opponent");
					boolean isOurWar = clanData.getString("tag").equals(clan.getTag())
							|| opponentData.getString("tag").equals(clan.getTag());

					if (isOurWar) {
						String state = warData.getString("state");

						// If we find an active war, the previous round was the last completed
						if (state.equals("inWar") || state.equals("preparation")) {
							// Current active round found, so last completed is r-1
							if (r > 0) {
								lastCompletedRound = r - 1;
							}
							break; // Found active war, no need to check further
						} else if (state.equals("warEnded")) {
							// This round is completed, update tracking
							lastCompletedRound = r;
							lastCompletedWarTag = warTag;
							cachedWarData = warData; // Cache the war data to avoid refetching
						}
					}
				} catch (Exception e) {
					// If war data is not available, skip
					continue;
				}
			}

			// If we found an active round, stop checking further rounds
			if (lastCompletedRound < r && lastCompletedRound >= 0) {
				break;
			}
		}

		// If no active wars found and we have completed wars, it means all 7 days are
		// done
		// In this case, lastCompletedRound should be the last round (day 7 = round 6)
		if (lastCompletedRound == -1 && rounds.length() == 7) {
			lastCompletedRound = 6; // Day 7 (0-indexed)
		}

		// If we couldn't determine the round, exit
		if (lastCompletedRound == -1 || lastCompletedRound >= rounds.length()) {
			return;
		}

		// Now find our clan's war in the last completed round
		org.json.JSONArray lastRoundWarTags = rounds.getJSONObject(lastCompletedRound).getJSONArray("warTags");

		for (int w = 0; w < lastRoundWarTags.length(); w++) {
			String warTag = lastRoundWarTags.getString(w);

			// Use cached data if available, otherwise fetch
			org.json.JSONObject warData;
			if (cachedWarData != null && warTag.equals(lastCompletedWarTag)) {
				warData = cachedWarData;
			} else {
				try {
					warData = Clan.getCWLDayJson(warTag);
				} catch (Exception e) {
					// If war data is not available, skip
					continue;
				}
			}

			try {

				// Check if this war involves our clan (could be in "clan" or "opponent" field)
				org.json.JSONObject clanData = warData.getJSONObject("clan");
				org.json.JSONObject opponentData = warData.getJSONObject("opponent");
				boolean isOurWar = clanData.getString("tag").equals(clan.getTag())
						|| opponentData.getString("tag").equals(clan.getTag());

				if (isOurWar && warData.getString("state").equals("warEnded")) {
					// Determine which object contains our clan's data
					org.json.JSONObject ourClanData = clanData.getString("tag").equals(clan.getTag()) ? clanData
							: opponentData;

					// Process missed attacks for this war
					StringBuilder message = new StringBuilder();
					message.append("## CWL Day ").append(lastCompletedRound + 1).append(" - Missed Attacks\n\n");
					boolean hasMissedAttacks = false;

					org.json.JSONArray members = ourClanData.getJSONArray("members");

					for (int i = 0; i < members.length(); i++) {
						org.json.JSONObject member = members.getJSONObject(i);
						String tag = member.getString("tag");
						String name = member.getString("name");

						int attacks = 0;
						if (member.has("attacks")) {
							attacks = member.getJSONArray("attacks").length();
						}

						if (attacks < 1) { // CWL has 1 attack per member
							hasMissedAttacks = true;
							Player p = new Player(tag);
							message.append("- ").append(name);
							if (p.getUser() != null) {
								message.append(" (<@").append(p.getUser().getUserID()).append(">)");
							}
							message.append("\n");

							if (getActionType() == ACTIONTYPE.KICKPOINT) {
								addKickpointForPlayer(p, "CWL Angriff verpasst");
							}
						}
					}

					if (hasMissedAttacks) {
						sendMessageToChannel(message.toString());
					}

					break; // Found our war, no need to check other wars in this round
				}
			} catch (Exception e) {
				// If war data is not available, skip
				continue;
			}
		}
	}

	private void handleRaidEvent(Clan clan) {
		if (!clan.RaidActive()) {
			return;
		}

		ArrayList<Player> raidMembers = clan.getRaidMemberList();
		ArrayList<Player> dbMembers = clan.getPlayersDB();

		StringBuilder message = new StringBuilder();
		message.append("## Raid Weekend - Missed Attacks\n\n");

		boolean hasMissedAttacks = false;

		// Check members who didn't raid at all
		for (Player dbPlayer : dbMembers) {
			boolean foundInRaid = false;
			for (Player raidPlayer : raidMembers) {
				if (raidPlayer.getTag().equals(dbPlayer.getTag())) {
					foundInRaid = true;
					int attacks = raidPlayer.getCurrentRaidAttacks();
					int maxAttacks = raidPlayer.getCurrentRaidAttackLimit()
							+ raidPlayer.getCurrentRaidbonusAttackLimit();

					if (attacks < maxAttacks) {
						hasMissedAttacks = true;
						message.append("- ").append(raidPlayer.getNameAPI()).append(": ").append(attacks).append("/")
								.append(maxAttacks);
						if (dbPlayer.getUser() != null) {
							message.append(" (<@").append(dbPlayer.getUser().getUserID()).append(">)");
						}
						message.append("\n");

						if (getActionType() == ACTIONTYPE.KICKPOINT) {
							addKickpointForPlayer(dbPlayer,
									"Raid Angriffe verpasst (" + attacks + "/" + maxAttacks + ")");
						}
					}
					break;
				}
			}

			if (!foundInRaid) {
				hasMissedAttacks = true;
				message.append("- ").append(dbPlayer.getNameAPI()).append(": 0 attacks");
				if (dbPlayer.getUser() != null) {
					message.append(" (<@").append(dbPlayer.getUser().getUserID()).append(">)");
				}
				message.append("\n");

				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(dbPlayer, "Raid nicht teilgenommen");
				}
			}
		}

		if (hasMissedAttacks) {
			sendMessageToChannel(message.toString());
		}
	}

	private void addKickpointForPlayer(Player player, String reason) {
		// Get kickpoint reason from action values if specified
		KickpointReason kpReason = null;
		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.reason) {
				kpReason = av.getReason();
				break;
			}
		}

		int amount = 1; // Default
		if (kpReason != null && kpReason.Exists()) {
			amount = (int) kpReason.getAmount();
			reason = kpReason.getName();
		}

		Clan clan = player.getClanDB();
		if (clan != null) {
			java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
			java.sql.Timestamp expires = java.sql.Timestamp
					.valueOf(now.toLocalDateTime().plusDays(clan.getDaysKickpointsExpireAfter()));

			DBUtil.executeUpdate(
					"INSERT INTO kickpoints (player_tag, date, amount, description, created_by_discord_id, created_at, expires_at, clan_tag, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
					player.getTag(), now, amount, reason, "0", now, expires, clan.getTag(), now);
		}
	}

	private void sendMessageToChannel(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				TextChannel channel = lostmanager.Bot.getJda().getTextChannelById(channelId);
				if (channel != null) {
					channel.sendMessage(message).queue();
				}
			} catch (Exception e) {
				System.err.println("Failed to send message to channel " + channelId + ": " + e.getMessage());
			}
		}
	}

}
