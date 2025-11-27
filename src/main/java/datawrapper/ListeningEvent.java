package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import dbutil.DBUtil;
import lostmanager.Bot;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import util.MessageUtil;
import util.Tuple;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

			Clan c = new Clan(getClanTag());
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
			// Skip hidden co-leaders as they don't need to participate in clan games
			if (p.isHiddenColeader()) {
				continue;
			}

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

		// Check action values for parameters (backwards compatible)
		boolean useLists = false;
		boolean excludeLeaders = false;

		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null) {
				if (av.getValue() == 1L) {
					useLists = true;
				} else if (av.getValue() == 2L) {
					excludeLeaders = true;
				}
			}
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

		// If using lists, initialize/sync them
		if (useLists) {
			initializeAndSyncListsForEvent(getClanTag(), clan);
		}

		for (util.Tuple<Integer, Integer> map : currentmap) {
			Player chosen = null;

			if (useLists) {
				// Pick from list A
				chosen = pickPlayerFromListAForEvent(getClanTag(), warMemberList, map, excludeLeaders);
			} else {
				// Original random logic
				java.util.Collections.shuffle(warMemberList);
				chosen = warMemberList.get(0);
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
					// Check exclude_leaders if enabled
					if (excludeLeaders && isLeaderOrCoLeaderForEvent(chosen)) {
						i++;
						continue;
					}
					break;
				}
			}

			if (chosen == null && !warMemberList.isEmpty()) {
				chosen = warMemberList.get(0);
			}

			if (chosen == null) {
				continue;
			}

			int mapposition = chosen.getWarMapPosition();
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

		return mappings;
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
		message.append("## Filler in " + clan.getInfoString() + "\n\n");

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
					message.append("- ").append(player.getInfoStringAPI());
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
		} else {
			sendMessageToChannel("## Filler in " + clan.getInfoString() + "\n\nKeine Filler gefunden.");
		}
	}

	private void handleCWMissedAttacks(Clan clan, org.json.JSONObject cwJson) {
		// Get required attacks from action values (default to attacksPerMember from
		// API)
		int attacksPerMember = cwJson.getInt("attacksPerMember");
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

		// Build initial message with missed attacks data
		CWMissedAttacksResult result = buildCWMissedAttacksMessage(clan, cwJson, requiredAttacks, fillerTags, false);

		// Determine if this is an end-of-war event (duration = 0)
		boolean isEndOfWarEvent = getDurationUntilEnd() <= 0;

		if (isEndOfWarEvent && result.hasMissedAttacks) {
			// At end of war: send initial message, then schedule 5-minute verification
			// Don't process kickpoints yet - wait for verification
			Message sentMessage = sendMessageToChannelAndReturn(result.message);

			if (sentMessage != null) {
				// Store references needed for the delayed update
				final String clanTag = clan.getTag();
				final int finalRequiredAttacks = requiredAttacks;
				final java.sql.Timestamp finalEndTimeTs = endTimeTs;
				final ArrayList<String> finalFillerTags = fillerTags;
				final long messageId = sentMessage.getIdLong();
				final String channelId = getChannelID();
				final ListeningEvent thisEvent = this;
				final String originalMessage = result.message; // Store original message for fallback

				// Schedule 5-minute delayed verification using Bot's scheduler
				// Using a single-use scheduler that shuts down after execution
				ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
				scheduler.schedule(() -> {
					try {
						handleCWMissedAttacksDelayedVerification(clanTag, finalRequiredAttacks, finalEndTimeTs,
								finalFillerTags, messageId, channelId, thisEvent, originalMessage);
					} catch (Exception e) {
						System.err.println("Error in delayed CW verification: " + e.getMessage());
						e.printStackTrace();
					} finally {
						scheduler.shutdown();
					}
				}, 5, TimeUnit.MINUTES);

				System.out.println("Scheduled 5-minute CW missed attacks verification for clan " + clanTag);
			}
		} else if (isEndOfWarEvent && !result.hasMissedAttacks) {
			// End of war but no missed attacks - nothing to send or schedule
			// Clean up fillers
			DBUtil.executeUpdate("DELETE FROM cw_fillers WHERE clan_tag = ? AND war_end_time = ?", clan.getTag(),
					endTimeTs);
		} else {
			// Not end of war (e.g., reminder during war) - use original behavior
			if (result.hasMissedAttacks) {
				sendMessageInChunks(result.message);
			}
		}
	}

	/**
	 * Handles the delayed verification of CW missed attacks after 5 minutes.
	 * Fetches fresh data, updates the message, and processes kickpoints if
	 * appropriate.
	 */
	private void handleCWMissedAttacksDelayedVerification(String clanTag, int requiredAttacks,
			java.sql.Timestamp endTimeTs, ArrayList<String> fillerTags, long messageId, String channelId,
			ListeningEvent event, String originalMessage) {

		System.out.println("Starting 5-minute CW verification for clan " + clanTag);

		try {
			// Fetch fresh clan war data
			Clan clan = new Clan(clanTag);
			org.json.JSONObject cwJson = clan.getCWJson();
			String currentState = cwJson.getString("state");

			// Check if war data is still available (state is notInWar or warEnded)
			boolean dataIsReliable = currentState.equals("notInWar") || currentState.equals("warEnded");

			String updatedMessage;
			boolean shouldProcessKickpoints = false;
			CWMissedAttacksResult result = null;

			if (dataIsReliable) {
				// Data is reliable - build updated message with fresh data
				result = buildCWMissedAttacksMessage(clan, cwJson, requiredAttacks, fillerTags, true);
				updatedMessage = result.message + "\n\n*Daten nach 5min überprüft*";
				shouldProcessKickpoints = result.hasMissedAttacks && event.getActionType() == ACTIONTYPE.KICKPOINT;
			} else {
				// New war has already started - data is not reliable
				// Use the original message content and just append a warning
				// Don't try to build a new message as the API would return data for the new war
				updatedMessage = originalMessage
						+ "\n\n*Daten sind nicht zuverlässig, da Krieg direkt wieder gestartet wurde*";
				shouldProcessKickpoints = false; // Don't process kickpoints with unreliable data
			}

			// Edit the original message
			editMessageInChannel(channelId, messageId, updatedMessage);

			// Process kickpoints if appropriate
			if (shouldProcessKickpoints && result != null) {
				for (PlayerMissedAttacks pma : result.playersWithMissedAttacks) {
					addKickpointForPlayer(pma.player,
							"CW Angriffe verpasst (" + pma.attacks + "/" + requiredAttacks + ")");
				}
			}

			// Clean up fillers after processing
			DBUtil.executeUpdate("DELETE FROM cw_fillers WHERE clan_tag = ? AND war_end_time = ?", clanTag, endTimeTs);

			System.out.println("Completed 5-minute CW verification for clan " + clanTag + " (dataReliable="
					+ dataIsReliable + ", kickpoints=" + shouldProcessKickpoints + ")");

		} catch (Exception e) {
			System.err.println("Error in CW delayed verification for clan " + clanTag + ": " + e.getMessage());
			e.printStackTrace();

			// On error, try to update the message with an error note appended to original
			try {
				editMessageInChannel(channelId, messageId, originalMessage
						+ "\n\n*Fehler bei der 5-Minuten-Überprüfung. Daten möglicherweise nicht aktuell.*");
			} catch (Exception e2) {
				System.err.println("Failed to update message with error: " + e2.getMessage());
			}
		}
	}

	/**
	 * Helper class to store missed attacks result
	 */
	private static class CWMissedAttacksResult {
		String message;
		boolean hasMissedAttacks;
		ArrayList<PlayerMissedAttacks> playersWithMissedAttacks;

		CWMissedAttacksResult(String message, boolean hasMissedAttacks, ArrayList<PlayerMissedAttacks> players) {
			this.message = message;
			this.hasMissedAttacks = hasMissedAttacks;
			this.playersWithMissedAttacks = players;
		}
	}

	/**
	 * Helper class to store player missed attacks info
	 */
	private static class PlayerMissedAttacks {
		Player player;
		int attacks;

		PlayerMissedAttacks(Player player, int attacks) {
			this.player = player;
			this.attacks = attacks;
		}
	}

	/**
	 * Builds the CW missed attacks message from the war data.
	 * 
	 * @param clan                The clan
	 * @param cwJson              The clan war JSON data
	 * @param requiredAttacks     Required number of attacks
	 * @param fillerTags          List of filler player tags to exclude
	 * @param isVerificationPhase Whether this is the 5-minute verification phase
	 * @return CWMissedAttacksResult containing the message and list of players
	 */
	private CWMissedAttacksResult buildCWMissedAttacksMessage(Clan clan, org.json.JSONObject cwJson,
			int requiredAttacks, ArrayList<String> fillerTags, boolean isVerificationPhase) {

		org.json.JSONObject clanData = cwJson.getJSONObject("clan");
		org.json.JSONArray members = clanData.getJSONArray("members");

		StringBuilder message = new StringBuilder();
		message.append("## " + clan.getNameAPI() + " Clankrieg - ");

		if (!isVerificationPhase && getDurationUntilEnd() > 0) {
			int secondsLeft = (int) (getDurationUntilEnd() / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;

			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;

			if (hoursLeft > 0) {
				message.append(" **" + hoursLeft).append("h**");
			}
			if (minutesLeft > 0) {
				message.append(" **" + minutesLeft).append("m**");
			}
			if (secondsLeft > 0) {
				message.append(" **" + secondsLeft).append("s**");
			}
			message.append(" verbleibend\n");
		} else {
			message.append("**Krieg beendet.**\n");
		}
		message.append("*abzüglich Filler, wenn abgespeichert* \n\n");

		boolean hasMissedAttacks = false;
		ArrayList<PlayerMissedAttacks> playersWithMissedAttacks = new ArrayList<>();

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
				message.append("- ");

				if (!isVerificationPhase && getDurationUntilEnd() > 0) {
					if (p.getUser() != null) {
						message.append("(<@").append(p.getUser().getUserID()).append(">) ");
					}
				}
				message.append(name).append(" (").append(attacks).append("/").append(requiredAttacks).append(")");
				message.append("\n");

				playersWithMissedAttacks.add(new PlayerMissedAttacks(p, attacks));
			}
		}

		return new CWMissedAttacksResult(message.toString(), hasMissedAttacks, playersWithMissedAttacks);
	}

	/**
	 * Sends a message to the channel and returns the Message object for later
	 * editing.
	 */
	private Message sendMessageToChannelAndReturn(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = Bot.getJda().getChannelById(MessageChannelUnion.class, channelId);
				if (channel != null) {
					// Use complete() instead of queue() to get the message synchronously
					return channel.sendMessage(message).complete();
				}
			} catch (Exception e) {
				System.err.println("Failed to send message to channel " + channelId + ": " + e.getMessage());
			}
		}
		return null;
	}

	/**
	 * Edits an existing message in the channel.
	 */
	private void editMessageInChannel(String channelId, long messageId, String newContent) {
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = Bot.getJda().getChannelById(MessageChannelUnion.class, channelId);
				if (channel != null) {
					channel.editMessageById(messageId, newContent).queue(
							_ -> System.out.println("Successfully edited message " + messageId),
							error -> System.err
									.println("Failed to edit message " + messageId + ": " + error.getMessage()));
				}
			} catch (Exception e) {
				System.err.println("Failed to edit message in channel " + channelId + ": " + e.getMessage());
			}
		}
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

					// Build initial message with missed attacks data
					CWMissedAttacksResult result = buildCWLDayMissedAttacksMessage(clan, ourClanData, lastCompletedRound, false);

					// Determine if this is an end-of-war event (duration = 0)
					boolean isEndOfWarEvent = getDurationUntilEnd() <= 0;

					if (isEndOfWarEvent && result.hasMissedAttacks) {
						// At end of war: send initial message, then schedule 5-minute verification
						// Don't process kickpoints yet - wait for verification
						Message sentMessage = sendMessageToChannelAndReturn(result.message);

						if (sentMessage != null) {
							// Store references needed for the delayed update
							final String clanTag = clan.getTag();
							final int finalCompletedRound = lastCompletedRound;
							final String finalWarTag = warTag;
							final long messageId = sentMessage.getIdLong();
							final String channelId = getChannelID();
							final ListeningEvent thisEvent = this;
							final String originalMessage = result.message;

							// Schedule 5-minute delayed verification
							ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
							scheduler.schedule(() -> {
								try {
									handleCWLDayMissedAttacksDelayedVerification(clanTag, finalCompletedRound,
											finalWarTag, messageId, channelId, thisEvent, originalMessage);
								} catch (Exception e) {
									System.err.println("Error in delayed CWL day verification: " + e.getMessage());
									e.printStackTrace();
								} finally {
									scheduler.shutdown();
								}
							}, 5, TimeUnit.MINUTES);

							System.out.println("Scheduled 5-minute CWL day missed attacks verification for clan " + clanTag);
						}
					} else if (isEndOfWarEvent && !result.hasMissedAttacks) {
						// End of war but no missed attacks - nothing to send or schedule
						// Nothing to clean up for CWL (no fillers table)
					} else {
						// Not end of war (e.g., reminder during war) - use original behavior
						if (result.hasMissedAttacks) {
							sendMessageInChunks(result.message);
						}
					}

					break; // Found our war, no need to check other wars in this round
				}
			} catch (Exception e) {
				// If war data is not available, skip
				continue;
			}
		}
	}

	/**
	 * Builds the CWL day missed attacks message from the war data.
	 * 
	 * @param clan                The clan
	 * @param ourClanData         The JSON object containing our clan's war data
	 * @param roundNumber         The round number (0-indexed)
	 * @param isVerificationPhase Whether this is the 5-minute verification phase
	 * @return CWMissedAttacksResult containing the message and list of players
	 */
	private CWMissedAttacksResult buildCWLDayMissedAttacksMessage(Clan clan, org.json.JSONObject ourClanData,
			int roundNumber, boolean isVerificationPhase) {

		org.json.JSONArray members = ourClanData.getJSONArray("members");

		StringBuilder message = new StringBuilder();
		message.append("## CWL Day ").append(roundNumber + 1).append(" - ");

		if (isVerificationPhase) {
			message.append("**Krieg beendet.**\n\n");
		} else {
			message.append("Missed Attacks\n\n");
		}

		boolean hasMissedAttacks = false;
		ArrayList<PlayerMissedAttacks> playersWithMissedAttacks = new ArrayList<>();

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

				// Only include Discord mentions if not in verification phase
				if (!isVerificationPhase && p.getUser() != null) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append("\n");

				playersWithMissedAttacks.add(new PlayerMissedAttacks(p, attacks));
			}
		}

		return new CWMissedAttacksResult(message.toString(), hasMissedAttacks, playersWithMissedAttacks);
	}

	/**
	 * Handles the delayed verification of CWL day missed attacks after 5 minutes.
	 * Fetches fresh data, updates the message, and processes kickpoints if
	 * appropriate.
	 */
	private void handleCWLDayMissedAttacksDelayedVerification(String clanTag, int roundNumber, String warTag,
			long messageId, String channelId, ListeningEvent event, String originalMessage) {

		System.out.println("Starting 5-minute CWL day verification for clan " + clanTag + " round " + (roundNumber + 1));

		try {
			// Fetch fresh CWL war data
			org.json.JSONObject warData = Clan.getCWLDayJson(warTag);
			String currentState = warData.getString("state");

			// Check if war data is still available (state is warEnded)
			boolean dataIsReliable = currentState.equals("warEnded");

			String updatedMessage;
			boolean shouldProcessKickpoints = false;
			CWMissedAttacksResult result = null;

			if (dataIsReliable) {
				// Data is reliable - build updated message with fresh data
				Clan clan = new Clan(clanTag);

				// Determine which object contains our clan's data
				org.json.JSONObject clanData = warData.getJSONObject("clan");
				org.json.JSONObject opponentData = warData.getJSONObject("opponent");
				org.json.JSONObject ourClanData = clanData.getString("tag").equals(clanTag) ? clanData : opponentData;

				result = buildCWLDayMissedAttacksMessage(clan, ourClanData, roundNumber, true);
				updatedMessage = result.message + "\n*Daten nach 5min überprüft*";
				shouldProcessKickpoints = result.hasMissedAttacks && event.getActionType() == ACTIONTYPE.KICKPOINT;
			} else {
				// War state changed (shouldn't happen in CWL but handle anyway)
				updatedMessage = originalMessage
						+ "\n\n*Daten sind möglicherweise nicht zuverlässig*";
				shouldProcessKickpoints = false;
			}

			// Edit the original message
			editMessageInChannel(channelId, messageId, updatedMessage);

			// Process kickpoints if appropriate
			if (shouldProcessKickpoints && result != null) {
				for (PlayerMissedAttacks pma : result.playersWithMissedAttacks) {
					addKickpointForPlayer(pma.player, "CWL Angriff verpasst (Day " + (roundNumber + 1) + ")");
				}
			}

			System.out.println("Completed 5-minute CWL day verification for clan " + clanTag + " (dataReliable="
					+ dataIsReliable + ", kickpoints=" + shouldProcessKickpoints + ")");

		} catch (Exception e) {
			System.err.println("Error in CWL day delayed verification for clan " + clanTag + ": " + e.getMessage());
			e.printStackTrace();

			// On error, try to update the message with an error note appended to original
			try {
				editMessageInChannel(channelId, messageId, originalMessage
						+ "\n\n*Fehler bei der 5-Minuten-Überprüfung. Daten möglicherweise nicht aktuell.*");
			} catch (Exception e2) {
				System.err.println("Failed to update message with error: " + e2.getMessage());
			}
		}
	}

	private void handleRaidEvent(Clan clan) {
		// Get raid status - we need to handle both ongoing and recently ended raids
		org.json.JSONObject raidJson = clan.getRaidJsonFull();
		org.json.JSONArray items = raidJson.getJSONArray("items");
		if (items.length() == 0) {
			return;
		}

		org.json.JSONObject currentRaid = items.getJSONObject(0);
		String state = currentRaid.getString("state");
		boolean isRaidActive = state.equals("ongoing");
		boolean isRaidEnded = state.equals("ended");

		if (!isRaidActive && !isRaidEnded) {
			return; // No valid raid state
		}

		// Check if we have district thresholds configured
		boolean hasDistrictThresholds = false;
		Integer capitalPeakMax = null;
		Integer otherDistrictsMax = null;
		Integer penalizeBoth = null;

		ArrayList<ActionValue> actionValues = getActionValues();
		if (actionValues != null && actionValues.size() >= 3) {
			// Check if we have 3 consecutive VALUE types (district thresholds)
			// They would be after the kickpoint reason (if exists)
			int valueCount = 0;
			for (ActionValue av : actionValues) {
				if (av.getSaved() == ActionValue.kind.value) {
					valueCount++;
					if (valueCount == 1) {
						capitalPeakMax = av.getValue().intValue();
					} else if (valueCount == 2) {
						otherDistrictsMax = av.getValue().intValue();
					} else if (valueCount == 3) {
						penalizeBoth = av.getValue().intValue();
						hasDistrictThresholds = true;
					}
				}
			}
		}

		// If we have district thresholds and raid has ENDED, analyze districts
		// Only analyze districts at raid end, not during ongoing raid
		if (hasDistrictThresholds && isRaidEnded
				&& (getActionType() == ACTIONTYPE.INFOMESSAGE || getActionType() == ACTIONTYPE.KICKPOINT)) {
			handleRaidDistrictAnalysis(clan, capitalPeakMax, otherDistrictsMax, penalizeBoth);
		}

		// Continue with existing missed attacks logic (for both ongoing and ended
		// raids)
		ArrayList<Player> raidMembers = clan.getRaidMemberList();
		ArrayList<Player> dbMembers = clan.getPlayersDB();

		StringBuilder message = new StringBuilder();
		message.append("## Raid Weekend - Missed Attacks\n\n");

		boolean hasMissedAttacks = false;

		// Check members who didn't raid at all
		for (Player dbPlayer : dbMembers) {
			// Skip hidden co-leaders as they don't need to be in clan/raid
			if (dbPlayer.isHiddenColeader()) {
				continue;
			}

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

	private void handleRaidDistrictAnalysis(Clan clan, int capitalPeakMax, int otherDistrictsMax, int penalizeBoth) {
		try {
			// Fetch raid data with attackLog
			org.json.JSONObject raidJson = clan.getRaidJsonFull();
			org.json.JSONArray items = raidJson.getJSONArray("items");
			if (items.length() == 0) {
				return;
			}

			org.json.JSONObject currentRaid = items.getJSONObject(0);

			// Check if attackLog exists
			if (!currentRaid.has("attackLog") || currentRaid.isNull("attackLog")) {
				return;
			}

			org.json.JSONArray attackLog = currentRaid.getJSONArray("attackLog");

			// Process each defender (enemy clan) in the attack log
			for (int i = 0; i < attackLog.length(); i++) {
				org.json.JSONObject defenderEntry = attackLog.getJSONObject(i);

				if (!defenderEntry.has("districts") || defenderEntry.isNull("districts")) {
					continue;
				}

				org.json.JSONArray districts = defenderEntry.getJSONArray("districts");

				// Process each district
				for (int j = 0; j < districts.length(); j++) {
					org.json.JSONObject district = districts.getJSONObject(j);
					String districtName = district.getString("name");

					if (!district.has("attacks") || district.isNull("attacks")) {
						continue;
					}

					org.json.JSONArray attacks = district.getJSONArray("attacks");
					int totalAttacks = attacks.length();

					// Determine threshold based on district name
					int threshold = districtName.equals("Capital Peak") ? capitalPeakMax : otherDistrictsMax;

					// Check if attacks exceed threshold
					if (totalAttacks > threshold) {
						// Count attacks per player
						java.util.Map<String, Integer> attacksByPlayer = new java.util.HashMap<>();
						java.util.Map<String, String> playerNames = new java.util.HashMap<>();

						for (int k = 0; k < attacks.length(); k++) {
							org.json.JSONObject attack = attacks.getJSONObject(k);
							org.json.JSONObject attacker = attack.getJSONObject("attacker");
							String attackerTag = attacker.getString("tag");
							String attackerName = attacker.getString("name");

							attacksByPlayer.put(attackerTag, attacksByPlayer.getOrDefault(attackerTag, 0) + 1);
							playerNames.put(attackerTag, attackerName);
						}

						// Find max attacks
						int maxAttacks = attacksByPlayer.values().stream().max(Integer::compareTo).orElse(0);

						// Find players with max attacks
						java.util.List<String> topAttackers = new java.util.ArrayList<>();
						for (java.util.Map.Entry<String, Integer> entry : attacksByPlayer.entrySet()) {
							if (entry.getValue() == maxAttacks) {
								topAttackers.add(entry.getKey());
							}
						}

						// Build message
						StringBuilder message = new StringBuilder();
						message.append("## ").append(districtName).append(" - Zu viele Angriffe\n\n");
						message.append("**Schwellenwert:** ").append(threshold).append("\n");
						message.append("**Tatsächliche Angriffe:** ").append(totalAttacks).append("\n\n");

						if (getActionType() == ACTIONTYPE.INFOMESSAGE) {
							// List all attackers
							message.append("**Alle Angreifer:**\n");
							for (java.util.Map.Entry<String, String> entry : playerNames.entrySet()) {
								String tag = entry.getKey();
								String name = entry.getValue();
								int attackCount = attacksByPlayer.get(tag);
								message.append("- ").append(name).append(": ").append(attackCount).append(" Angriffe");

								// Try to find discord user
								try {
									Player p = new Player(tag);
									if (p.getUser() != null) {
										message.append(" (<@").append(p.getUser().getUserID()).append(">)");
									}
								} catch (Exception e) {
									// Player might not be in database
								}
								message.append("\n");
							}
						} else if (getActionType() == ACTIONTYPE.KICKPOINT) {
							// Penalize top attacker(s)

							// If multiple players tied and penalizeBoth is 2 (No), skip penalizing
							if (topAttackers.size() > 1 && penalizeBoth == 2) {
								message.append("**Mehrere Spieler mit gleicher Anzahl an Angriffen (")
										.append(maxAttacks).append("), keine Bestrafung gemäß Einstellung.**\n");
								for (String tag : topAttackers) {
									String name = playerNames.get(tag);
									message.append("- ").append(name).append(": ").append(maxAttacks)
											.append(" Angriffe");
									try {
										Player p = new Player(tag);
										if (p.getUser() != null) {
											message.append(" (<@").append(p.getUser().getUserID()).append(">)");
										}
									} catch (Exception e) {
										// Player might not be in database
									}
									message.append("\n");
								}
							} else {
								// Penalize all top attackers
								message.append("**Bestrafte Spieler (").append(maxAttacks).append(" Angriffe):**\n");
								for (String tag : topAttackers) {
									String name = playerNames.get(tag);
									message.append("- ").append(name);

									try {
										Player p = new Player(tag);
										if (p.getUser() != null) {
											message.append(" (<@").append(p.getUser().getUserID()).append(">)");
										}

										// Add kickpoint
										String reason = "Zu viele Angriffe auf " + districtName + " (" + maxAttacks
												+ "/" + threshold + ")";
										addKickpointForPlayer(p, reason);
									} catch (Exception e) {
										message.append(" (nicht in Datenbank gefunden)");
									}
									message.append("\n");
								}
							}
						}

						// Send message (respect 4000 character limit)
						String messageStr = message.toString();
						if (messageStr.length() > 3900) {
							// Split into multiple messages
							sendMessageInChunks(messageStr);
						} else {
							sendMessageToChannel(messageStr);
						}
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error analyzing raid districts: " + e.getMessage());
			e.printStackTrace();
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

		// Try to get the player's clan from DB first
		Clan clan = player.getClanDB();
		
		// If player's clan is not in DB, fall back to the event's configured clan
		// This supports external clans (e.g., CWL side clans) where players may not be in our main clan database
		if (clan == null) {
			String eventClanTag = getClanTag();
			if (eventClanTag != null) {
				Clan eventClan = new Clan(eventClanTag);
				// Only use the event's clan if it exists in our database (has settings configured)
				if (eventClan.ExistsDB()) {
					clan = eventClan;
				}
			}
		}
		
		if (clan != null) {
			Integer daysExpire = clan.getDaysKickpointsExpireAfter();
			// Default to 30 days if not configured
			if (daysExpire == null) {
				daysExpire = 30;
			}
			
			java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
			java.sql.Timestamp expires = java.sql.Timestamp
					.valueOf(now.toLocalDateTime().plusDays(daysExpire));

			Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(
					"INSERT INTO kickpoints (player_tag, date, amount, description, created_by_discord_id, created_at, expires_at, clan_tag, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
					player.getTag(), now, amount, reason, Bot.getJda().getSelfUser().getId(), now, expires,
					clan.getTag(), now);

			PreparedStatement stmt = result.getFirst();
			int rowsAffected = result.getSecond();

			Long id = null;

			if (rowsAffected > 0) {
				try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						id = generatedKeys.getLong(1);
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}

			String desc = "### Es wurde ein Kickpunkt automatisch hinzugefügt.\n";
			// Use API name for external clan players since they may not be in DB
			String playerName = player.getNameDB();
			if (playerName == null) {
				playerName = player.getNameAPI();
			}
			desc += "Spieler: " + MessageUtil.unformat(playerName + " (" + player.getTag() + ")") + "\n";
			desc += "Clan: " + clan.getInfoString() + "\n";
			desc += "Anzahl: " + amount + "\n";
			desc += "Grund: " + reason + "\n";
			desc += "ID: " + id + "\n";

			sendMessageToChannel(desc);
		} else {
			// Log warning when we can't add kickpoints because neither player's clan nor event's clan is in DB
			System.out.println("Warning: Cannot add kickpoint for player " + player.getTag() + 
					" - neither player's clan nor event's clan (" + getClanTag() + ") is configured in database");
		}
	}

	private void sendMessageToChannel(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = Bot.getJda().getChannelById(MessageChannelUnion.class, channelId);
				if (channel != null) {
					channel.sendMessage(message).queue();
				}
			} catch (Exception e) {
				System.err.println("Failed to send message to channel " + channelId + ": " + e.getMessage());
			}
		}
	}

	private void sendMessageInChunks(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = Bot.getJda().getChannelById(MessageChannelUnion.class, channelId);
				if (channel != null) {
					// Split message into chunks of max 3900 characters to be safe
					int chunkSize = 1900;
					for (int i = 0; i < message.length(); i += chunkSize) {
						int end = Math.min(message.length(), i + chunkSize);
						String chunk = message.substring(i, end);
						channel.sendMessage(chunk).queue();

						// Small delay between messages to avoid rate limiting
						try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
				}
			} catch (Exception e) {
				System.err.println("Failed to send chunked message to channel " + channelId + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Initialize and synchronize cwdonator lists for a clan (for listening events)
	 */
	private void initializeAndSyncListsForEvent(String clanTag, Clan clan) {
		try {
			// Check if lists exist
			String checkSql = "SELECT list_a, list_b FROM cwdonator_lists WHERE clan_tag = ?";
			try (java.sql.Connection conn = dbutil.Connection.getConnection();
					java.sql.PreparedStatement stmt = conn.prepareStatement(checkSql)) {
				stmt.setString(1, clanTag);
				java.sql.ResultSet rs = stmt.executeQuery();

				ArrayList<String> listA = new ArrayList<>();
				ArrayList<String> listB = new ArrayList<>();
				boolean exists = false;

				if (rs.next()) {
					exists = true;
					java.sql.Array listAArray = rs.getArray("list_a");
					java.sql.Array listBArray = rs.getArray("list_b");
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
					try (java.sql.PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
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
					try (java.sql.PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
						updateStmt.setArray(1, conn.createArrayOf("text", listA.toArray()));
						updateStmt.setArray(2, conn.createArrayOf("text", listB.toArray()));
						updateStmt.setString(3, clanTag);
						updateStmt.executeUpdate();
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Error initializing/syncing cwdonator lists for event: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Pick a player from List A for listening events
	 */
	private Player pickPlayerFromListAForEvent(String clanTag, ArrayList<Player> warMemberList,
			util.Tuple<Integer, Integer> map, boolean excludeLeaders) {
		try (java.sql.Connection conn = dbutil.Connection.getConnection()) {
			// Get current lists
			String selectSql = "SELECT list_a, list_b FROM cwdonator_lists WHERE clan_tag = ?";
			ArrayList<String> listA = new ArrayList<>();
			ArrayList<String> listB = new ArrayList<>();

			try (java.sql.PreparedStatement stmt = conn.prepareStatement(selectSql)) {
				stmt.setString(1, clanTag);
				java.sql.ResultSet rs = stmt.executeQuery();
				if (rs.next()) {
					java.sql.Array listAArray = rs.getArray("list_a");
					java.sql.Array listBArray = rs.getArray("list_b");
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

			// Build a list of eligible players
			ArrayList<Player> eligiblePlayers = new ArrayList<>();
			for (Player p : warMemberList) {
				if (listA.contains(p.getTag())) {
					int mapposition = p.getWarMapPosition();
					if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
						continue;
					}
					if (!p.getWarPreference()) {
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
				if (isLeaderOrCoLeaderForEvent(chosen) && excludeLeaders) {
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
					return pickPlayerFromListAForEvent(clanTag, warMemberList, map, excludeLeaders);
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
				return pickPlayerFromListAForEvent(clanTag, warMemberList, map, excludeLeaders);
			}

			if (chosen != null) {
				listA.remove(chosen.getTag());
				listB.add(chosen.getTag());

				String updateSql = "UPDATE cwdonator_lists SET list_a = ?::text[], list_b = ?::text[] WHERE clan_tag = ?";
				try (java.sql.PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
					updateStmt.setArray(1, conn.createArrayOf("text", listA.toArray()));
					updateStmt.setArray(2, conn.createArrayOf("text", listB.toArray()));
					updateStmt.setString(3, clanTag);
					updateStmt.executeUpdate();
				}
			}

			return chosen;
		} catch (Exception e) {
			System.err.println("Error picking player from List A for event: " + e.getMessage());
			e.printStackTrace();
			if (!warMemberList.isEmpty()) {
				java.util.Collections.shuffle(warMemberList);
				return warMemberList.get(0);
			}
			return null;
		}
	}

	/**
	 * Check if a player is a leader or co-leader (for listening events)
	 */
	private boolean isLeaderOrCoLeaderForEvent(Player player) {
		Player.RoleType roleDB = player.getRoleDB();
		return roleDB == Player.RoleType.LEADER || roleDB == Player.RoleType.COLEADER;
	}

}
