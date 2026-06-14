package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lostmanager.Bot;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;

public class ListeningEvent {

	public enum LISTENINGTYPE {
		CW, RAID, CWLDAY, CS, FIXTIMEINTERVAL, CWLEND
	}

	public enum ACTIONTYPE {
		INFOMESSAGE, CUSTOMMESSAGE, KICKPOINT, CWDONATOR, FILLER, RAIDFAILS, STARFAILS, STARFAILS_KICKPOINT
	}

	private final Long id;
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
			if (type == null) {
				System.err.println("Warning: Listening event " + id + " has null listeningtype in database");
				return null;
			}
			switch (type.toLowerCase()) {
				case "cw" -> listeningtype = LISTENINGTYPE.CW;
				case "raid" -> listeningtype = LISTENINGTYPE.RAID;
				case "cwl", "cwlday" -> listeningtype = LISTENINGTYPE.CWLDAY;
                                case "cs" -> listeningtype = LISTENINGTYPE.CS;
                                case "fixtimeinterval" -> listeningtype = LISTENINGTYPE.FIXTIMEINTERVAL;
                                case "cwlend" -> listeningtype = LISTENINGTYPE.CWLEND;
                                default -> {
                                    System.err.println("Warning: Unknown listeningtype '" + type + "' for event " + id);
                                    listeningtype = null;
                        }
			}
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
											: type.equals("filler") ? ACTIONTYPE.FILLER
													: type.equals("raidfails") ? ACTIONTYPE.RAIDFAILS
															: type.equals("starfails") ? ACTIONTYPE.STARFAILS
																	: type.equals("starfails_kickpoint") ? ACTIONTYPE.STARFAILS_KICKPOINT : null;
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
				System.err.println("Error parsing actionvalues JSON for event " + id + ": " + e.getMessage());
				actionvalues = new ArrayList<>();
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

			// Check if listening type is null
			LISTENINGTYPE type = getListeningType();
			if (type == null) {
				System.err.println("Warning: Cannot calculate timestamp for event " + id + " with null listeningtype");
				return Long.MAX_VALUE;
			}

			Clan c = new Clan(getClanTag());
			Long endTimeMillis;
			switch (type) {
				case CS -> {
                                    endTimeMillis = c.getCGEndTimeMillis();
                                    if (endTimeMillis != null) {
                                        timestamptofire = endTimeMillis - getDurationUntilEnd();
                                    }
                        }
				case CW -> {
                                    endTimeMillis = c.getCWEndTimeMillis();
                                    if (endTimeMillis != null) {
                                        timestamptofire = endTimeMillis - getDurationUntilEnd();
                                    }
                        }
				case CWLDAY -> {
                                    endTimeMillis = c.getCWLDayEndTimeMillis();
                                    if (endTimeMillis != null) {
                                        timestamptofire = endTimeMillis - getDurationUntilEnd();
                                    }
                        }
				case RAID -> {
                                    endTimeMillis = c.getRaidEndTimeMillis();
                                    if (endTimeMillis != null) {
                                        timestamptofire = endTimeMillis - getDurationUntilEnd();
                                    }
                        }
				case FIXTIMEINTERVAL -> timestamptofire = getDurationUntilEnd();
				case CWLEND -> {
                        }
				default -> {
                        }
			}

			// If timestamptofire is still null, return a far future time to prevent
			// scheduling errors (this is expected when no war/raid is active)
			if (timestamptofire == null) {
				return Long.MAX_VALUE;
			}
		}
		return timestamptofire;
	}

	public void fireEvent() {
		System.out.println("Starting fireEvent for event ID " + getId() + ", type: " + getListeningType() + ", clan: "
				+ getClanTag());

		try {
			LISTENINGTYPE type = getListeningType();
			if (type == null) {
				System.err.println("Error: Cannot fire event " + getId() + " with null listeningtype");
				return;
			}

			Clan clan = new Clan(getClanTag());

			switch (type) {
				case CS -> handleClanGamesEvent(clan);

				case CW -> handleClanWarEvent(clan);

				case CWLDAY -> handleCWLDayEvent(clan);

				case RAID -> handleRaidEvent(clan);

				case FIXTIMEINTERVAL -> {
                        }

				default -> {
                        }
			}
                    // For custom timed events

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
		message.append("## Clan Games Results (Threshold: ").append(threshold).append(")\n\n");

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
							// Cast JSONB data to integer using ::text::integer
							String sql = "SELECT data::text::integer FROM achievement_data WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? ORDER BY time LIMIT 1";
							Integer pointsStart = DBUtil.getValueFromSQL(sql, Integer.class, p.getTag(), startTime);

							if (pointsStart != null) {
								difference = currentPoints - pointsStart;
							}
							break;
						}
					}
				} catch (JSONException e) {
					System.err
							.println("Error fetching fresh API data for player " + p.getTag() + ": " + e.getMessage());
					continue;
				}
			} else {
				// Use stored data from database
				// Cast JSONB data to integer using ::text::integer
				String sql = "SELECT data::text::integer FROM achievement_data WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? ORDER BY time LIMIT 1";
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
				// Skip signed-off members
				MemberSignoff signoff = new MemberSignoff(p.getTag());
				if (signoff.isActive() && !signoff.isReceivePings()) {
					continue;
				}

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

		if (getActionType() == ACTIONTYPE.STARFAILS || getActionType() == ACTIONTYPE.STARFAILS_KICKPOINT) {
			if (state.equals("inWar") || state.equals("warEnded")) {
				handleCWBadAttacks(clan, cwJson);
			}
			return;
		}

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
				handleCWDonator(clan);
			} else {
				handleCWFiller(clan, cwJson);
			}
		} else if (state.equals("inWar") || state.equals("warEnded")) {
			handleCWMissedAttacks(clan, cwJson);
		}
	}

	private void handleCWDonator(Clan clan) {
		// Execute cwdonator command logic automatically
		ArrayList<Player> originalList = clan.getWarMemberList();

		if (originalList == null) {
			return; // Can't execute if no war members
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

		// Check action values for parameters (backwards compatible)
		boolean useLists = false;
		boolean excludeLeaders = false;

		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null) {
				if (av.getValue() == 1L) {
					useLists = true;
				}
				if (av.getValue() == 2L) {
					excludeLeaders = true;
				}
			}
		}

		// Use the same mapping logic as cwdonator command
		HashMap<Integer, ArrayList<lostmanager.util.Tuple<Integer, Integer>>> mappings = getCWDonatorMappings();
		ArrayList<lostmanager.util.Tuple<Integer, Integer>> currentmap = mappings.get(cwsize);

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

		for (lostmanager.util.Tuple<Integer, Integer> map : currentmap) {
			Player chosen;

			if (useLists) {
				// Pick from list A
				chosen = pickPlayerFromListAForEvent(getClanTag(), warMemberList, map, excludeLeaders);
			} else {
				// Original random logic
				java.util.Collections.shuffle(warMemberList);
				chosen = warMemberList.get(0);
				int mapposition;
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

	private HashMap<Integer, ArrayList<lostmanager.util.Tuple<Integer, Integer>>> getCWDonatorMappings() {
		// Same mapping logic as cwdonator command

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
		message.append("## Filler in ").append(clan.getInfoString()).append("\n\n");

		boolean hasOptedOut = false;
		ArrayList<String> fillerTags = new ArrayList<>();

		// Check each war member to see if they have opted out
		for (int i = 0; i < members.length(); i++) {
			org.json.JSONObject member = members.getJSONObject(i);
			String tag = member.getString("tag");

			try {
				Player player = new Player(tag);

				// Skip hidden co-leaders
				if (player.isHiddenColeader()) {
					continue;
				}

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
		int requiredAttacks = getRequiredAttacksFromConfig(cwJson);

		// Get war end time to match with fillers
		String endTimeStr = cwJson.getString("endTime");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
		Instant instant = Instant.from(formatter.parse(endTimeStr));
		java.time.OffsetDateTime endTime = java.time.OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
		java.sql.Timestamp endTimeTs = java.sql.Timestamp.from(endTime.toInstant());

		// Get list of fillers for this war (within 24 hours to handle API time shifts)
		String fillerSql = "SELECT player_tag FROM cw_fillers WHERE clan_tag = ? AND war_end_time > ?::timestamp - INTERVAL '24 hours' AND war_end_time < ?::timestamp + INTERVAL '24 hours'";
		ArrayList<String> fillerTags = DBUtil.getArrayListFromSQL(fillerSql, String.class, clan.getTag(), endTimeTs, endTimeTs);

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
				final java.sql.Timestamp finalEndTimeTs = endTimeTs;
				final ArrayList<String> finalFillerTags = fillerTags;
				final long messageId = sentMessage.getIdLong();
				final String channelId = getChannelID();
				final ListeningEvent thisEvent = this;
				final String originalMessage = result.message; // Store original message for fallback

				// Schedule 5-minute delayed verification using Bot's scheduler
				// Using a single-use scheduler that shuts down after execution
				lostmanager.Bot.activeVerificationTasks.incrementAndGet();
				ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
				scheduler.schedule(() -> {
					try {
						handleCWMissedAttacksDelayedVerification(clanTag, finalEndTimeTs,
								finalFillerTags, messageId, channelId, thisEvent, originalMessage);
					} catch (Exception e) {
						System.err.println("Error in delayed CW verification: " + e.getMessage());					} finally {
						lostmanager.Bot.activeVerificationTasks.decrementAndGet();
						scheduler.shutdown();
					}
				}, 5, TimeUnit.MINUTES);

				System.out.println("Scheduled 5-minute CW missed attacks verification for clan " + clanTag);
			}
		} else if (isEndOfWarEvent && !result.hasMissedAttacks) {
			// End of war but no missed attacks - nothing to send or schedule
			// Clean up fillers - safe to delete here since no verification will occur (within 24 hours)
			DBUtil.executeUpdate("DELETE FROM cw_fillers WHERE clan_tag = ? AND war_end_time > ?::timestamp - INTERVAL '24 hours' AND war_end_time < ?::timestamp + INTERVAL '24 hours'", clan.getTag(),
					endTimeTs, endTimeTs);
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
	private void handleCWMissedAttacksDelayedVerification(String clanTag,
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

			// Re-fetch required attacks from event's action values to ensure the configured
			// setting is preserved
			// This prevents the setting from being lost and reverting to the API's
			// attacksPerMember value
			int actualRequiredAttacks = event.getRequiredAttacksFromConfig(cwJson);

			String updatedMessage;
			boolean shouldProcessKickpoints;
			CWMissedAttacksResult result = null;

			if (dataIsReliable) {
				// Data is reliable - build updated message with fresh data
				result = buildCWMissedAttacksMessage(clan, cwJson, actualRequiredAttacks, fillerTags, true);
				
				org.json.JSONObject clanData = cwJson.getJSONObject("clan");
				boolean isPerfectWar = clanData.has("stars") && cwJson.has("teamSize") && 
									   clanData.getInt("stars") == cwJson.getInt("teamSize") * 3;
				
				if (isPerfectWar) {
					updatedMessage = result.message + "\n\n*Daten nach 5min überprüft*\n**Perfekter Krieg erreicht! Keine Kickpunkte verteilt.**";
					shouldProcessKickpoints = false;
				} else {
					updatedMessage = result.message + "\n\n*Daten nach 5min überprüft*";
					shouldProcessKickpoints = result.hasMissedAttacks && event.getActionType() == ACTIONTYPE.KICKPOINT;
				}
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
							"CW Angriffe verpasst (" + pma.attacks + "/" + actualRequiredAttacks + ")");
				}
			}

			// Clean up fillers after processing (within 24 hours to handle API time shifts)
			DBUtil.executeUpdate("DELETE FROM cw_fillers WHERE clan_tag = ? AND war_end_time > ?::timestamp - INTERVAL '24 hours' AND war_end_time < ?::timestamp + INTERVAL '24 hours'", clanTag, endTimeTs, endTimeTs);

			System.out.println("Completed 5-minute CW verification for clan " + clanTag + " (dataReliable="
					+ dataIsReliable + ", kickpoints=" + shouldProcessKickpoints + ")");

		} catch (JSONException e) {
			System.err.println("Error in CW delayed verification for clan " + clanTag + ": " + e.getMessage());
			// On error, try to update the message with an error note appended to original
			try {
				editMessageInChannel(channelId, messageId, originalMessage
						+ "\n\n*Fehler bei der 5-Minuten-Überprüfung. Daten möglicherweise nicht aktuell.*");
			} catch (Exception e2) {
				System.err.println("Failed to update message with error: " + e2.getMessage());
			}

			// Still clean up fillers even on error (within 24 hours)
			try {
				DBUtil.executeUpdate("DELETE FROM cw_fillers WHERE clan_tag = ? AND war_end_time > ?::timestamp - INTERVAL '24 hours' AND war_end_time < ?::timestamp + INTERVAL '24 hours'", clanTag,
						endTimeTs, endTimeTs);
			} catch (Exception e3) {
				System.err.println("Failed to delete fillers on error: " + e3.getMessage());
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
		message.append("## ").append(clan.getNameAPI()).append(" Clankrieg - ");

		if (!isVerificationPhase && getDurationUntilEnd() > 0) {
			int secondsLeft = (int) (getDurationUntilEnd() / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;

			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;

			if (hoursLeft > 0) {
				message.append(" **").append(hoursLeft).append("h**");
			}
			if (minutesLeft > 0) {
				message.append(" **").append(minutesLeft).append("m**");
			}
			if (secondsLeft > 0) {
				message.append(" **").append(secondsLeft).append("s**");
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

				Player p = new Player(tag);
				// Skip hidden co-leaders
				if (p.isHiddenColeader()) {
					continue;
				}

				hasMissedAttacks = true;
				message.append("- ");

				if (!isVerificationPhase && getDurationUntilEnd() > 0) {
					MemberSignoff signoff = new MemberSignoff(tag);
					if (p.getUser() != null && (!signoff.isActive() || signoff.isReceivePings())) {
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
	@SuppressWarnings("null")
	private Message sendMessageToChannelAndReturn(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = MessageUtil.getChannelById(channelId);
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
	@SuppressWarnings("null")
	private void editMessageInChannel(String channelId, long messageId, String newContent) {
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = MessageUtil.getChannelById(channelId);
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
		if (!cwlJson.has("rounds") || cwlJson.isNull("rounds")) {
			return;
		}
		org.json.JSONArray rounds = cwlJson.getJSONArray("rounds");

		// Find the target round to report
		// If a round is in progress (inWar), that's our target (for reminders)
		// If we find an active round, the selection is finalized
		int targetRound = -1;
		String targetWarTag = null;
		org.json.JSONObject cachedWarData = null;
		long minDiff = Long.MAX_VALUE;
		long targetTimeMillis = System.currentTimeMillis() + getDurationUntilEnd();

		for (int r = 0; r < rounds.length(); r++) {
			org.json.JSONArray warTags = rounds.getJSONObject(r).getJSONArray("warTags");

			for (int w = 0; w < warTags.length(); w++) {
				String warTag = warTags.getString(w);
				if (warTag.equals("#0"))
					continue;

				try {
					org.json.JSONObject warData = Clan.getCWLDayJson(warTag);
					if (warData == null || !warData.has("clan") || !warData.has("opponent"))
						continue;

					org.json.JSONObject clanData = warData.getJSONObject("clan");
					org.json.JSONObject opponentData = warData.getJSONObject("opponent");
					boolean isOurWar = clanData.getString("tag").equals(clan.getTag())
							|| opponentData.getString("tag").equals(clan.getTag());

					if (isOurWar) {
						String state = warData.getString("state");

						if ((state.equals("inWar") || state.equals("warEnded")) && warData.has("endTime") && !warData.isNull("endTime")) {
							String endTimeStr = warData.getString("endTime");
							DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
							Instant endInstant = Instant.from(formatter.parse(endTimeStr));
							long endTimeMillis = endInstant.toEpochMilli();
							
							long diff = Math.abs(endTimeMillis - targetTimeMillis);
							if (diff < minDiff) {
								minDiff = diff;
								targetRound = r;
								targetWarTag = warTag;
								cachedWarData = warData;
							}
						}
					}
				} catch (JSONException e) {
					// Skip war tags whose data can't be loaded
				}
			}
		}

		if (targetRound == -1 || cachedWarData == null) {
			return;
		}

		try {
			// Determine which object contains our clan's data
			org.json.JSONObject clanData = cachedWarData.getJSONObject("clan");
			org.json.JSONObject opponentData = cachedWarData.getJSONObject("opponent");
			org.json.JSONObject ourClanData = clanData.getString("tag").equals(clan.getTag()) ? clanData
					: opponentData;

			String finalState = cachedWarData.getString("state");

			if (getActionType() == ACTIONTYPE.STARFAILS || getActionType() == ACTIONTYPE.STARFAILS_KICKPOINT) {
				if (finalState.equals("inWar") || finalState.equals("warEnded")) {
					handleCWLDayBadAttacks(clan, ourClanData, cachedWarData, targetRound, targetWarTag);
				}
				return;
			}

			// Build initial message with missed attacks data
			CWMissedAttacksResult result = buildCWLDayMissedAttacksMessage(clan, ourClanData,
					cachedWarData, targetRound, false);

			// Determine if this is an end-of-war event (duration = 0)
			boolean isEndOfWarEvent = getDurationUntilEnd() <= 0;

			if (isEndOfWarEvent && result.hasMissedAttacks) {
				// At end of war: send initial message, then schedule 5-minute verification
				Message sentMessage = sendMessageToChannelAndReturn(result.message);

				if (sentMessage != null) {
					final String clanTag = clan.getTag();
					final int finalCompletedRound = targetRound;
					final String finalWarTag = targetWarTag;
					final long messageId = sentMessage.getIdLong();
					final String channelId = getChannelID();
					final ListeningEvent thisEvent = this;
					final String originalMessage = result.message;

					lostmanager.Bot.activeVerificationTasks.incrementAndGet();
					ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
					scheduler.schedule(() -> {
						try {
							handleCWLDayMissedAttacksDelayedVerification(clanTag, finalCompletedRound,
									finalWarTag, messageId, channelId, thisEvent, originalMessage);
						} catch (Exception e) {
							System.err.println("Error in delayed CWL day verification: " + e.getMessage());						} finally {
							lostmanager.Bot.activeVerificationTasks.decrementAndGet();
							scheduler.shutdown();
						}
					}, 5, TimeUnit.MINUTES);
				}
			} else {
				// It's a reminder (inWar or warEnded but not duration 0)
				if (result.hasMissedAttacks) {
					sendMessageInChunks(result.message);
				}
			}
		} catch (JSONException e) {
			System.err.println("Error processing target CWL round: " + e.getMessage());
		}
	}

	/**
	 * Builds the CWL day missed attacks message from the war data.
	 * 
	 * @param clan                The clan
	 * @param ourClanData         The JSON object containing our clan's war data
	 * @param warData             The full war JSON data (contains endTime)
	 * @param roundNumber         The round number (0-indexed)
	 * @param isVerificationPhase Whether this is the 5-minute verification phase
	 * @return CWMissedAttacksResult containing the message and list of players
	 */
	private CWMissedAttacksResult buildCWLDayMissedAttacksMessage(Clan clan, org.json.JSONObject ourClanData,
			org.json.JSONObject warData, int roundNumber, boolean isVerificationPhase) {

		org.json.JSONArray members = ourClanData.getJSONArray("members");

		// Calculate time remaining from war end time
		String endTimeStr = warData.getString("endTime");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
		Instant endInstant = Instant.from(formatter.parse(endTimeStr));
		long millisRemaining = endInstant.toEpochMilli() - System.currentTimeMillis();

		// Gather clan/sideclan info for footnotes
		String eventClanTag = clan.getTag();
		String warClanName = ourClanData.getString("name");
		String belongsTo1 = DBUtil.getValueFromSQL("SELECT belongs_to FROM sideclans WHERE clan_tag = ?", String.class, eventClanTag);
		String belongsTo2 = DBUtil.getValueFromSQL("SELECT belongs_to_2 FROM sideclans WHERE clan_tag = ?", String.class, eventClanTag);

		StringBuilder message = new StringBuilder();
		message.append("## CWL Day ").append(roundNumber + 1);

		if (isVerificationPhase || millisRemaining <= 0) {
			message.append(" - Verpasste Angriffe - **Krieg beendet.**\n\n");
		} else {
			message.append(" - Offene Hits - ");

			// Use configured duration for a cleaner display (e.g., "6h" instead of "5h 58m
			// 40s")
			long durationToShow = getDurationUntilEnd();
			if (durationToShow <= 0) {
				// Fallback to actual remaining time if duration is not available or -1
				durationToShow = millisRemaining;
			}

			int secondsLeft = (int) (durationToShow / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;

			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;

			if (hoursLeft > 0) {
				message.append("**").append(hoursLeft).append("h**");
			}
			if (minutesLeft > 0) {
				if (hoursLeft > 0) message.append(" ");
				message.append("**").append(minutesLeft).append("m**");
			}
			if (secondsLeft > 0 && hoursLeft == 0) {
				if (minutesLeft > 0) message.append(" ");
				message.append("**").append(secondsLeft).append("s**");
			}
			message.append(" verbleibend\n\n");
		}

		// Global footnote: clan name and sideclan parent(s)
		message.append("-# Clan: ").append(warClanName);
		if (belongsTo1 != null && !belongsTo1.isEmpty()) {
			Clan mainClan1 = new Clan(belongsTo1);
			String mainName1 = mainClan1.getNameDB();
			message.append(" | Gehört zu: ").append(mainName1 != null ? mainName1 : belongsTo1);
			if (belongsTo2 != null && !belongsTo2.isEmpty()) {
				Clan mainClan2 = new Clan(belongsTo2);
				String mainName2 = mainClan2.getNameDB();
				message.append(", ").append(mainName2 != null ? mainName2 : belongsTo2);
			}
		}
		message.append("\n\n");

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
				Player p = new Player(tag);
				// Skip hidden co-leaders
				if (p.isHiddenColeader()) {
					continue;
				}

				// Skip signed-off members
				MemberSignoff signoff = new MemberSignoff(tag);
				if (signoff.isActive() && !signoff.isReceivePings()) {
					continue;
				}

				hasMissedAttacks = true;
				message.append("- ").append(name).append(" (").append(tag).append(")");

				// Only include Discord mentions if not in verification phase
				if (!isVerificationPhase && p.getUser() != null) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append("\n");

				// Footnote: warn if player won't receive a kickpoint
				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					Clan playerClanDB = p.getClanDB();
					boolean matchesEventClan = false;
					if (playerClanDB != null) {
						String pct = playerClanDB.getTag();
						matchesEventClan = pct.equals(eventClanTag)
								|| (belongsTo1 != null && !belongsTo1.isEmpty() && pct.equals(belongsTo1))
								|| (belongsTo2 != null && !belongsTo2.isEmpty() && pct.equals(belongsTo2));
					}
					if (!matchesEventClan) {
						if (playerClanDB != null) {
							String foundName = playerClanDB.getNameDB();
							message.append("-# Kein Kickpunkt – gefunden in: ")
									.append(foundName != null ? foundName : playerClanDB.getTag()).append("\n");
						} else {
							message.append("-# Kein Kickpunkt – nicht in Datenbank\n");
						}
					}
				}

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

		System.out
				.println("Starting 5-minute CWL day verification for clan " + clanTag + " round " + (roundNumber + 1));

		try {
			// Fetch fresh CWL war data
			org.json.JSONObject warData = Clan.getCWLDayJson(warTag);
			String currentState = warData.getString("state");

			// Check if war data is still available (state is warEnded)
			boolean dataIsReliable = currentState.equals("warEnded");

			String updatedMessage;
			boolean shouldProcessKickpoints;
			CWMissedAttacksResult result = null;

			if (dataIsReliable) {
				// Data is reliable - build updated message with fresh data
				Clan clan = new Clan(clanTag);

				// Determine which object contains our clan's data
				org.json.JSONObject clanData = warData.getJSONObject("clan");
				org.json.JSONObject opponentData = warData.getJSONObject("opponent");
				org.json.JSONObject ourClanData = clanData.getString("tag").equals(clanTag) ? clanData : opponentData;

				result = buildCWLDayMissedAttacksMessage(clan, ourClanData, warData, roundNumber, true);
				
				boolean isPerfectWar = ourClanData.has("stars") && warData.has("teamSize") && 
									   ourClanData.getInt("stars") == warData.getInt("teamSize") * 3;
				
				if (isPerfectWar) {
					updatedMessage = result.message + "\n*Daten nach 5min überprüft*\n**Perfekter Krieg erreicht! Keine Kickpunkte verteilt.**";
					shouldProcessKickpoints = false;
				} else {
					updatedMessage = result.message + "\n*Daten nach 5min überprüft*";
					shouldProcessKickpoints = result.hasMissedAttacks && event.getActionType() == ACTIONTYPE.KICKPOINT;
				}
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

		} catch (JSONException e) {
			System.err.println("Error in CWL day delayed verification for clan " + clanTag + ": " + e.getMessage());
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

		// Handle CUSTOMMESSAGE action type - post custom message with raid header
		if (getActionType() == ACTIONTYPE.CUSTOMMESSAGE) {
			handleRaidCustomMessage(isRaidActive);
			return;
		}

		// Handle RAIDFAILS action type - district analysis only
		// The event fires exactly at raid end, so the API may still report "ongoing"
		// at this point. Post immediately with current data, then verify after 5
		// minutes with fresh data (kickpoints are only added after verification).
		if (getActionType() == ACTIONTYPE.RAIDFAILS) {
			// Legacy events may still be configured with a duration > 0 - the district
			// analysis needs final data, so it only runs for end-of-raid events
			// (creation now enforces duration 0)
			if (getDurationUntilEnd() != 0) {
				System.out.println("Skipping RAIDFAILS event " + getId() + " - duration must be 0 (configured: "
						+ getDurationUntilEnd() + ")");
				return;
			}

			// Parse district thresholds from action values
			Integer capitalPeakMax = null;
			Integer otherDistrictsMax = null;
			Integer penalizeBoth = null;

			ArrayList<ActionValue> actionValues = getActionValues();
			if (actionValues != null) {
				int valueCount = 0;
				for (ActionValue av : actionValues) {
					if (av.getSaved() == ActionValue.kind.value) {
						valueCount++;
						switch (valueCount) {
							case 1 -> capitalPeakMax = av.getValue().intValue();
							case 2 -> otherDistrictsMax = av.getValue().intValue();
							case 3 -> penalizeBoth = av.getValue().intValue();
							default -> { }
						}
					}
				}
			}

			// Use default values if not configured
			if (capitalPeakMax == null)
				capitalPeakMax = 10;
			if (otherDistrictsMax == null)
				otherDistrictsMax = 6;
			if (penalizeBoth == null)
				penalizeBoth = 1;

			handleRaidDistrictAnalysis(clan, capitalPeakMax, otherDistrictsMax, penalizeBoth);
			return; // RAIDFAILS only handles district analysis
		}

		// Handle INFOMESSAGE and KICKPOINT - missed attacks only ("Fehlende Hits")
		ArrayList<Player> raidMembers = clan.getRaidMemberList();
		ArrayList<Player> dbMembers = clan.getPlayersDB();

		StringBuilder message = new StringBuilder();
		message.append("## Raid Weekend - ");

		boolean ended = !(isRaidActive && getDurationUntilEnd() > 0);
		// Show time remaining if raid is active, or "ended" if not (like CW)
		if (isRaidActive && getDurationUntilEnd() > 0) {
			int secondsLeft = (int) (getDurationUntilEnd() / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;

			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;

			if (hoursLeft > 0) {
				message.append(" **").append(hoursLeft).append("h**");
			}
			if (minutesLeft > 0) {
				message.append(" **").append(minutesLeft).append("m**");
			}
			if (secondsLeft > 0) {
				message.append(" **").append(secondsLeft).append("s**");
			}
			message.append(" verbleibend\n");
		} else {
			message.append("**Raid beendet.**\n");
		}
		message.append("\n");

		boolean hasMissedAttacks = false;
		ArrayList<Player> notFinished = new ArrayList<>();
		ArrayList<Player> notDone = new ArrayList<>();

		// Check members who didn't raid at all or didn't finish
		for (Player dbPlayer : dbMembers) {
			// Skip hidden co-leaders as they don't need to be in clan/raid
			if (dbPlayer.isHiddenColeader()) {
				continue;
			}

			// Skip signed-off members
			MemberSignoff signoff = new MemberSignoff(dbPlayer.getTag());
			if (signoff.isActive() && !signoff.isReceivePings()) {
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
						notFinished.add(raidPlayer);
					}
					break;
				}
			}

			if (!foundInRaid) {
				notDone.add(dbPlayer);
			}
		}

		// Check if players not in current raid are raiding in other clans
		if (!notDone.isEmpty()) {
			ArrayList<String> allClantags = DBManager.getAllClans();
			ArrayList<Clan> allClans = new ArrayList<>();
			for (String s : allClantags) {
				Clan c = new Clan(s);
				c.getRaidMemberList(); // load from API
				allClans.add(c);
			}
			for (int i = 0; i < notDone.size(); i++) {
				Player p = notDone.get(i);
				for (Clan c : allClans) {
					ArrayList<Player> raidMemberList = c.getRaidMemberList();
					for (Player t : raidMemberList) {
						if (t.getTag().equals(p.getTag())) {
							if (!message.toString().contains("In")) {
								message.append("### In einem anderen Lost-Clan angegriffen:\n");
							}
							message.append(t.getNameAPI()).append(" in ").append(c.getNameDB()).append(": ")
									.append(t.getCurrentRaidAttacks()).append("/")
									.append(t.getCurrentRaidAttackLimit() + t.getCurrentRaidbonusAttackLimit())
									.append("\n");
							hasMissedAttacks = true;
							notDone.remove(p);
							i--;
							break;
						}
					}
				}
			}
		}

		// Report players who didn't raid at all
		if (!notDone.isEmpty()) {
			if (!message.toString().contains("icht angegriffen")) {
				message.append("### Nicht angegriffen:\n");
			}
			for (Player p : notDone) {
				hasMissedAttacks = true;
				message.append(p.getNameAPI());
				if (p.getUser() != null && !ended) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append("\n");

				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(p, "Raid nicht teilgenommen");
				}
			}
		}

		// Report players who didn't finish their attacks
		if (!notFinished.isEmpty()) {
			if (!message.toString().contains("Angriffe")) {
				message.append("### Noch offene Angriffe:\n");
			}
			for (Player p : notFinished) {
				hasMissedAttacks = true;
				int attacks = p.getCurrentRaidAttacks();
				int maxAttacks = p.getCurrentRaidAttackLimit() + p.getCurrentRaidbonusAttackLimit();
				message.append(p.getNameAPI());
				if (p.getUser() != null) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append(": ").append(attacks).append("/").append(maxAttacks).append("\n");

				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(p, "Raid Angriffe verpasst (" + attacks + "/" + maxAttacks + ")");
				}
			}
		}

		if (hasMissedAttacks) {
			sendMessageToChannel(message.toString());
		}
	}

	private void handleRaidCustomMessage(boolean isRaidActive) {
		// Get custom message from action values
		String customMessageJson = DBUtil.getValueFromSQL("SELECT actionvalues FROM listening_events WHERE id = ?",
				String.class, getId());

		String customMessage = "";
		if (customMessageJson != null && !customMessageJson.isEmpty()) {
			try {
				ObjectMapper mapper = new ObjectMapper();
				java.util.Map<String, String> messageMap = mapper.readValue(customMessageJson,
						new TypeReference<java.util.HashMap<String, String>>() {
						});
				customMessage = messageMap.getOrDefault("message", "");
			} catch (JsonProcessingException e) {
				System.err.println("Error parsing custom message: " + e.getMessage());
			}
		}

		// Build message with raid header (like CW format)
		StringBuilder message = new StringBuilder();
		message.append("## Raid - ");

		// Show time remaining if raid is active (same format as CW)
		if (isRaidActive && getDurationUntilEnd() > 0) {
			int secondsLeft = (int) (getDurationUntilEnd() / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;

			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;

			if (hoursLeft > 0) {
				message.append("**").append(hoursLeft).append("h** ");
			}
			if (minutesLeft > 0) {
				message.append("**").append(minutesLeft).append("m** ");
			}
			if (secondsLeft > 0) {
				message.append("**").append(secondsLeft).append("s** ");
			}
			message.append("verbleibend\n\n");
		} else {
			message.append("**Raid beendet.**\n\n");
		}

		// Append custom message
		message.append(customMessage);

		sendMessageToChannel(message.toString());
	}

	/**
	 * Helper class to store a single raid district violation (player to penalize)
	 */
	private static class RaidDistrictFail {
		Player player;
		String districtName;
		int attacks;
		int threshold;

		RaidDistrictFail(Player player, String districtName, int attacks, int threshold) {
			this.player = player;
			this.districtName = districtName;
			this.attacks = attacks;
			this.threshold = threshold;
		}
	}

	/**
	 * Helper class to store the raid district analysis result
	 */
	private static class RaidDistrictAnalysisResult {
		String message;
		boolean hasFails;
		ArrayList<RaidDistrictFail> penalizedPlayers;

		RaidDistrictAnalysisResult(String message, boolean hasFails, ArrayList<RaidDistrictFail> penalizedPlayers) {
			this.message = message;
			this.hasFails = hasFails;
			this.penalizedPlayers = penalizedPlayers;
		}
	}

	/**
	 * Builds the raid district analysis from the raid attackLog. Does NOT add
	 * kickpoints itself - candidates are collected in the result so they can be
	 * processed after the 5-minute verification.
	 *
	 * @return the analysis result, or null if no usable raid data is available
	 */
	private RaidDistrictAnalysisResult buildRaidDistrictAnalysisResult(Clan clan, int capitalPeakMax,
			int otherDistrictsMax, int penalizeBoth, boolean shouldAddKickpoints) {

		org.json.JSONObject raidJson = clan.getRaidJsonFull();
		org.json.JSONArray items = raidJson.getJSONArray("items");
		if (items.length() == 0) {
			return null;
		}

		org.json.JSONObject currentRaid = items.getJSONObject(0);

		// Check if attackLog exists
		if (!currentRaid.has("attackLog") || currentRaid.isNull("attackLog")) {
			return null;
		}

		org.json.JSONArray attackLog = currentRaid.getJSONArray("attackLog");

		StringBuilder message = new StringBuilder();
		message.append("## Raidfails - District-Analyse\n\n");

		boolean hasFails = false;
		ArrayList<RaidDistrictFail> penalizedPlayers = new ArrayList<>();

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

				if (totalAttacks <= threshold) {
					continue;
				}

				hasFails = true;

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

				message.append("### ").append(districtName).append("\n");
				message.append("**Schwellenwert:** ").append(threshold).append(" – **Tatsächliche Angriffe:** ")
						.append(totalAttacks).append("\n");

				if (!shouldAddKickpoints) {
					// Info mode - show all attackers on the over-attacked district
					message.append("**Alle Angreifer auf diesem Distrikt:**\n");
					for (java.util.Map.Entry<String, Integer> entry : attacksByPlayer.entrySet()) {
						String tag = entry.getKey();
						int playerAttacks = entry.getValue();
						String name = playerNames.get(tag);
						message.append("- ").append(name).append(": ").append(playerAttacks).append(" Angriffe");

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
				} else if (topAttackers.size() > 1 && penalizeBoth == 2) {
					// Multiple players tied and penalizeBoth is 2 (No) - skip penalizing
					message.append("**Mehrere Spieler mit gleicher Anzahl an Angriffen (").append(maxAttacks)
							.append("), keine Bestrafung gemäß Einstellung.**\n");
					for (String tag : topAttackers) {
						String name = playerNames.get(tag);
						message.append("- ").append(name).append(": ").append(maxAttacks).append(" Angriffe");
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
					// Penalize all top attackers (kickpoints are added after verification)
					message.append("**Bestrafte Spieler (").append(maxAttacks).append(" Angriffe):**\n");
					for (String tag : topAttackers) {
						String name = playerNames.get(tag);
						message.append("- ").append(name);

						try {
							Player p = new Player(tag);
							if (p.getUser() != null) {
								message.append(" (<@").append(p.getUser().getUserID()).append(">)");
							}
							penalizedPlayers.add(new RaidDistrictFail(p, districtName, maxAttacks, threshold));
						} catch (Exception e) {
							message.append(" (nicht in Datenbank gefunden)");
						}
						message.append("\n");
					}
				}
				message.append("\n");
			}
		}

		return new RaidDistrictAnalysisResult(message.toString(), hasFails, penalizedPlayers);
	}

	/**
	 * Truncates a message to fit into a single Discord message (2000 char limit)
	 * so it can be posted and later edited during verification.
	 */
	private static String truncateForDiscord(String message) {
		return truncateForDiscord(message, "");
	}

	/**
	 * Truncates a message to fit into a single Discord message (2000 char limit).
	 * The footer (e.g. verification status notes) is always kept - only the body
	 * is shortened.
	 */
	private static String truncateForDiscord(String message, String footer) {
		if (message.length() + footer.length() <= 1900) {
			return message + footer;
		}
		String marker = "\n*… gekürzt*";
		int bodyLimit = 1900 - footer.length() - marker.length();
		return message.substring(0, bodyLimit) + marker + footer;
	}

	private void handleRaidDistrictAnalysis(Clan clan, int capitalPeakMax, int otherDistrictsMax, int penalizeBoth) {
		try {
			// Determine if we should add kickpoints based on configured kickpoint reason
			boolean shouldAddKickpoints = false;
			for (ActionValue av : getActionValues()) {
				if (av.getSaved() == ActionValue.kind.reason && av.getReason() != null) {
					shouldAddKickpoints = true;
					break;
				}
			}

			RaidDistrictAnalysisResult result = buildRaidDistrictAnalysisResult(clan, capitalPeakMax,
					otherDistrictsMax, penalizeBoth, shouldAddKickpoints);
			if (result == null) {
				return;
			}

			// Capture the endTime of the analyzed raid to make sure the verification
			// still looks at the same raid
			org.json.JSONObject currentRaid = clan.getRaidJsonFull().getJSONArray("items").getJSONObject(0);
			final String raidEndTimeStr = currentRaid.optString("endTime", "");

			// Post immediately if there are fails; the verification can still post a new
			// message if fails only show up in the fresh data (API cache at raid end)
			Message sentMessage = null;
			if (result.hasFails) {
				sentMessage = sendMessageToChannelAndReturn(truncateForDiscord(result.message));
			}

			final Long messageId = sentMessage != null ? sentMessage.getIdLong() : null;
			final String channelId = getChannelID();
			final String clanTag = clan.getTag();
			final String originalMessage = result.message;
			final ListeningEvent thisEvent = this;
			final boolean finalShouldAddKickpoints = shouldAddKickpoints;

			// Schedule 5-minute delayed verification (kickpoints only after verification)
			lostmanager.Bot.activeVerificationTasks.incrementAndGet();
			ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
			scheduler.schedule(() -> {
				try {
					handleRaidDistrictAnalysisDelayedVerification(clanTag, messageId, channelId, thisEvent,
							originalMessage, raidEndTimeStr, capitalPeakMax, otherDistrictsMax, penalizeBoth,
							finalShouldAddKickpoints);
				} catch (Exception e) {
					System.err.println("Error in delayed raid district verification: " + e.getMessage());
				} finally {
					lostmanager.Bot.activeVerificationTasks.decrementAndGet();
					scheduler.shutdown();
				}
			}, 5, TimeUnit.MINUTES);

			System.out.println("Scheduled 5-minute raid district verification for clan " + clanTag);
		} catch (JSONException e) {
			System.err.println("Error analyzing raid districts: " + e.getMessage());
		}
	}

	/**
	 * Handles the delayed verification of the raid district analysis after 5
	 * minutes. Fetches fresh data, updates (or posts) the message, and adds
	 * kickpoints only when the fresh data confirms the violations.
	 */
	private void handleRaidDistrictAnalysisDelayedVerification(String clanTag, Long messageId, String channelId,
			ListeningEvent event, String originalMessage, String raidEndTimeStr, int capitalPeakMax,
			int otherDistrictsMax, int penalizeBoth, boolean shouldAddKickpoints) {

		System.out.println("Starting 5-minute raid district verification for clan " + clanTag);

		try {
			// Fetch fresh raid data
			Clan clan = new Clan(clanTag);
			org.json.JSONObject raidJson = clan.getRaidJsonFull();
			org.json.JSONArray items = raidJson.getJSONArray("items");
			if (items.length() == 0) {
				return;
			}

			org.json.JSONObject currentRaid = items.getJSONObject(0);
			String state = currentRaid.optString("state", "");
			String endTimeStr = currentRaid.optString("endTime", "");

			// Data is reliable once the raid is reported as ended and it is still the
			// same raid we analyzed at fire time
			boolean dataIsReliable = state.equals("ended")
					&& (raidEndTimeStr.isEmpty() || endTimeStr.equals(raidEndTimeStr));

			if (!dataIsReliable) {
				if (messageId != null) {
					editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
							"\n*Daten sind nicht zuverlässig - keine Kickpunkte vergeben*"));
				}
				System.out.println("Completed 5-minute raid district verification for clan " + clanTag
						+ " (dataReliable=false, kickpoints=false)");
				return;
			}

			RaidDistrictAnalysisResult result = buildRaidDistrictAnalysisResult(clan, capitalPeakMax,
					otherDistrictsMax, penalizeBoth, shouldAddKickpoints);
			if (result == null) {
				return;
			}

			if (messageId != null) {
				// Update the original message with verified data
				String updatedMessage = result.hasFails
						? truncateForDiscord(result.message, "\n*Daten nach 5min überprüft*")
						: "## Raidfails - District-Analyse\n\nKeine Verstöße nach Überprüfung gefunden.\n\n*Daten nach 5min überprüft*";
				editMessageInChannel(channelId, messageId, updatedMessage);
			} else if (result.hasFails) {
				// Nothing was posted at fire time, but the fresh data shows violations
				sendMessageToChannel(truncateForDiscord(result.message, "\n*Daten nach 5min überprüft*"));
			}

			boolean shouldProcessKickpoints = shouldAddKickpoints && result.hasFails
					&& event.getActionType() == ACTIONTYPE.RAIDFAILS;
			if (shouldProcessKickpoints) {
				for (RaidDistrictFail fail : result.penalizedPlayers) {
					addKickpointForPlayer(fail.player, "Zu viele Angriffe auf " + fail.districtName + " ("
							+ fail.attacks + "/" + fail.threshold + ")");
				}
			}

			System.out.println("Completed 5-minute raid district verification for clan " + clanTag
					+ " (dataReliable=true, kickpoints=" + shouldProcessKickpoints + ")");

		} catch (JSONException e) {
			System.err.println("Error in raid district delayed verification for clan " + clanTag + ": "
					+ e.getMessage());
			if (messageId != null) {
				try {
					editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
							"\n*Fehler bei der 5-Minuten-Überprüfung. Daten möglicherweise nicht aktuell.*"));
				} catch (Exception e2) {
					System.err.println("Failed to update message with error: " + e2.getMessage());
				}
			}
		}
	}

	private void addKickpointForPlayer(Player player, String reason) {
		// Check if player is signed off - skip automatic kickpoints
		if (MemberSignoff.isSignedOff(player.getTag())) {
			System.out
					.println("Skipping automatic kickpoint for player " + player.getTag() + " - player is signed off");
			return;
		}

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

		// Get the event's configured clan
		String eventClanTag = getClanTag();

		// Resolve potential sideclan mappings (belongs_to and belongs_to_2)
		String belongsTo1 = DBUtil.getValueFromSQL("SELECT belongs_to FROM sideclans WHERE clan_tag = ?", String.class,
				eventClanTag);
		String belongsTo2 = DBUtil.getValueFromSQL("SELECT belongs_to_2 FROM sideclans WHERE clan_tag = ?",
				String.class, eventClanTag);

		// Candidate clans in priority order: eventClanTag, belongsTo1, belongsTo2
		String chosenClanTag = null;

		Clan playerClanDB = player.getClanDB();
		if (playerClanDB != null) {
			String playerClanTag = playerClanDB.getTag();

			if (playerClanTag.equals(eventClanTag)) {
				chosenClanTag = eventClanTag;
			} else if (belongsTo1 != null && !belongsTo1.isEmpty() && playerClanTag.equals(belongsTo1)) {
				chosenClanTag = belongsTo1;
			} else if (belongsTo2 != null && !belongsTo2.isEmpty() && playerClanTag.equals(belongsTo2)) {
				chosenClanTag = belongsTo2;
			}
		}

		// If we couldn't determine a matching clan for the player, skip adding
		// kickpoint
		if (chosenClanTag == null) {
			System.out.println("Skipping kickpoint for player " + player.getTag() +
					" - player not found in belongs_to, belongs_to_2, or event clan DB (event clan: " + eventClanTag
					+ ")");
			return;
		}

		Clan clan = new Clan(chosenClanTag);

		// Verify the chosen clan exists in DB before proceeding
		if (!clan.ExistsDB()) {
			System.out.println("Cannot add kickpoint for player " + player.getTag() +
					" - chosen clan " + chosenClanTag + " does not exist in DB");
			return;
		}

		Integer daysExpire = clan.getDaysKickpointsExpireAfter();
		// Default to 30 days if not configured
		if (daysExpire == null) {
			daysExpire = 30;
		}

		java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
		java.sql.Timestamp expires = java.sql.Timestamp
				.valueOf(now.toLocalDateTime().plusDays(daysExpire));

		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO kickpoints (player_tag, date, amount, description, created_by_discord_id, created_at, expires_at, clan_tag, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				player.getTag(), now, amount, reason, Bot.getJda().getSelfUser().getId(), now, expires,
				clan.getTag(), now);

		if (result == null) {
			System.err.println("Error: Failed to add kickpoint for player " + player.getTag() +
					" in clan " + clan.getTag() + " - database error occurred");
			return;
		}

		Long kickpointId = result.getFirst();

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
		desc += "ID: " + kickpointId + "\n";

		sendMessageToChannel(desc);
	}

	@SuppressWarnings("null")
	private void sendMessageToChannel(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = MessageUtil.getChannelById(channelId);
				if (channel != null) {
					channel.sendMessage(message).queue();
				}
			} catch (Exception e) {
				System.err.println("Failed to send message to channel " + channelId + ": " + e.getMessage());
			}
		}
	}

	@SuppressWarnings("null")
	private void sendMessageInChunks(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				MessageChannelUnion channel = MessageUtil.getChannelById(channelId);
				if (channel != null) {
					// Split message into chunks of max 3900 characters to be safe
					int chunkSize = 1900;
					// Stagger sends with a small, non-blocking delay to avoid rate limiting
					long delayMs = 0;
					for (int i = 0; i < message.length(); i += chunkSize) {
						int end = Math.min(message.length(), i + chunkSize);
						String chunk = message.substring(i, end);
						channel.sendMessage(chunk).queueAfter(delayMs, TimeUnit.MILLISECONDS);
						delayMs += 100;
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
			try (java.sql.Connection conn = lostmanager.dbutil.Connection.getConnection();
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
                                                listA.addAll(Arrays.asList(listAData));
					}
					if (listBArray != null) {
						String[] listBData = (String[]) listBArray.getArray();
						listB.addAll(Arrays.asList(listBData));
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
		} catch (SQLException e) {
			System.err.println("Error initializing/syncing cwdonator lists for event: " + e.getMessage());		}
	}

	/**
	 * Pick a player from List A for listening events
	 */
	private Player pickPlayerFromListAForEvent(String clanTag, ArrayList<Player> warMemberList,
			lostmanager.util.Tuple<Integer, Integer> map, boolean excludeLeaders) {
		try (java.sql.Connection conn = lostmanager.dbutil.Connection.getConnection()) {
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
						listA.addAll(Arrays.asList(listAData));
					}
					if (listBArray != null) {
						String[] listBData = (String[]) listBArray.getArray();
						listB.addAll(Arrays.asList(listBData));
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
					// Skip leaders if excludeLeaders is enabled
					if (excludeLeaders && isLeaderOrCoLeaderForEvent(p)) {
						continue;
					}
					eligiblePlayers.add(p);
				}
			}

			// Pick a player
			Player chosen;
			if (!eligiblePlayers.isEmpty()) {
				Collections.shuffle(eligiblePlayers);

				chosen = eligiblePlayers.get(0);
				// Defensive check: should not happen since leaders are filtered upfront, but
				// kept as safeguard
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
			System.err.println("Error picking player from List A for event: " + e.getMessage());			// Fallback: find an eligible player, respecting excludeLeaders if enabled
			if (!warMemberList.isEmpty()) {
				java.util.Collections.shuffle(warMemberList);
				for (Player p : warMemberList) {
					// Skip leaders if excludeLeaders is enabled
					if (excludeLeaders && isLeaderOrCoLeaderForEvent(p)) {
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
						if (!isLeaderOrCoLeaderForEvent(p)) {
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
	 * Check if a player is a leader or co-leader (for listening events)
	 */
	private boolean isLeaderOrCoLeaderForEvent(Player player) {
		Player.RoleType roleDB = player.getRoleDB();
		return roleDB == Player.RoleType.LEADER || roleDB == Player.RoleType.COLEADER;
	}

	/**
	 * Get the required attacks count from the event's action values configuration.
	 * For CW events, this value is always configured via the modal and stored in
	 * action values.
	 * Falls back to the API's attacksPerMember only if no configured value is found
	 * (legacy events).
	 * 
	 * @param cwJson The clan war JSON containing the API's attacksPerMember value
	 * @return The configured required attacks count
	 */
	private int getRequiredAttacksFromConfig(org.json.JSONObject cwJson) {
		// Look for the configured required attacks in action values
		ArrayList<ActionValue> actionValues = getActionValues();
		if (actionValues != null) {
			for (ActionValue av : actionValues) {
				// Check saved field first (correct way), but also handle legacy/malformed data
				// by checking if we have a non-null value that isn't a reason or type
				boolean isValueField = av.getSaved() == ActionValue.kind.value;
				boolean looksLikeValue = av.getValue() != null && av.getReason() == null && av.getType() == null;

				if (isValueField && av.getValue() != null) {
					int configuredValue = av.getValue().intValue();
					System.out.println(
							"CW Event " + getId() + ": Using configured required attacks = " + configuredValue);
					return configuredValue;
				} else if (!isValueField && looksLikeValue) {
					// Handle legacy/malformed data where saved field is wrong but value is clearly
					// a numeric value
					int configuredValue = av.getValue().intValue();
					System.out.println("CW Event " + getId()
							+ ": WARNING - Using configured required attacks from malformed ActionValue = "
							+ configuredValue);
					return configuredValue;
				}
			}
		}

		// Fallback for legacy events that don't have a configured value
		// (this should not happen for newly created events)
		int attacksPerMember = cwJson.getInt("attacksPerMember");
		System.out.println("CW Event " + getId()
				+ ": WARNING - No configured required attacks found, falling back to API value = " + attacksPerMember);
		return attacksPerMember;
	}

	// Returns the configured target star count (0/1/2) for starfails events, or -1 if not set.
	private int getConfiguredStarCount() {
		ArrayList<ActionValue> avs = getActionValues();
		if (avs == null) return -1;
		for (ActionValue av : avs) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null)
				return av.getValue().intValue();
		}
		return -1;
	}

	// Returns the configured punishment mode (1/2/3) for starfails events, defaulting to 1.
	private int getConfiguredPunishmentMode() {
		ArrayList<ActionValue> avs = getActionValues();
		if (avs == null) return 1;
		int count = 0;
		for (ActionValue av : avs) {
			if (av.getSaved() == ActionValue.kind.value && av.getValue() != null) {
				count++;
				if (count == 2) return av.getValue().intValue();
			}
		}
		return 1;
	}

	private static class PlayerBadAttack {
		Player player;
		int stars;
		int attackIndex;
		int destructionPct;

		PlayerBadAttack(Player player, int stars, int attackIndex, int destructionPct) {
			this.player = player;
			this.stars = stars;
			this.attackIndex = attackIndex;
			this.destructionPct = destructionPct;
		}
	}

	private static class CWBadAttacksResult {
		String message;
		boolean hasBadAttacks;
		ArrayList<PlayerBadAttack> badAttacks;

		CWBadAttacksResult(String message, boolean hasBadAttacks, ArrayList<PlayerBadAttack> badAttacks) {
			this.message = message;
			this.hasBadAttacks = hasBadAttacks;
			this.badAttacks = badAttacks;
		}
	}

	private CWBadAttacksResult buildCWBadAttacksResult(Clan clan, org.json.JSONObject cwJson,
			int targetStars, int mode, boolean isVerificationPhase) {

		org.json.JSONObject clanData = cwJson.getJSONObject("clan");
		org.json.JSONArray members = clanData.getJSONArray("members");

		StringBuilder message = new StringBuilder();
		message.append("## ").append(clan.getNameAPI())
				.append(" Clankrieg – Schlechte Angriffe (").append(targetStars).append(" ★)\n");

		if (isVerificationPhase || getDurationUntilEnd() <= 0) {
			message.append("**Krieg beendet.**\n\n");
		} else {
			int secondsLeft = (int) (getDurationUntilEnd() / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;
			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;
			if (hoursLeft > 0) message.append("**").append(hoursLeft).append("h** ");
			if (minutesLeft > 0) message.append("**").append(minutesLeft).append("m** ");
			if (secondsLeft > 0 && hoursLeft == 0) message.append("**").append(secondsLeft).append("s** ");
			message.append("verbleibend\n\n");
		}

		boolean hasBadAttacks = false;
		ArrayList<PlayerBadAttack> punishableAttacks = new ArrayList<>();

		for (int i = 0; i < members.length(); i++) {
			org.json.JSONObject member = members.getJSONObject(i);
			String tag = member.getString("tag");
			String name = member.getString("name");

			if (!member.has("attacks")) continue;
			org.json.JSONArray attacks = member.getJSONArray("attacks");
			if (attacks.length() == 0) continue;

			// Collect this member's bad attacks
			ArrayList<PlayerBadAttack> memberBad = new ArrayList<>();
			Player p = null;
			boolean skipMember = false;

			for (int a = 0; a < attacks.length(); a++) {
				org.json.JSONObject attack = attacks.getJSONObject(a);
				int stars = attack.optInt("stars", 0);
				if (stars == targetStars) {
					if (p == null) {
						p = new Player(tag);
						if (p.isHiddenColeader()) {
							skipMember = true;
							break;
						}
					}
					memberBad.add(new PlayerBadAttack(p, stars,
							attack.optInt("order", a + 1),
							attack.optInt("destructionPercentage", 0)));
				}
			}

			if (skipMember || p == null || memberBad.isEmpty()) continue;

			// Mode 3: only punish if ALL attacks were bad (stars == targetStars)
			if (mode == 3) {
				boolean allBad = true;
				for (int a = 0; a < attacks.length(); a++) {
					if (attacks.getJSONObject(a).optInt("stars", 0) != targetStars) {
						allBad = false;
						break;
					}
				}
				if (!allBad) continue;
			}

			hasBadAttacks = true;
			for (PlayerBadAttack pba : memberBad) {
				message.append("- ").append(name).append(" (").append(tag).append(")")
						.append(" – Angriff ").append(pba.attackIndex).append(": ")
						.append(pba.stars).append(" ★ (").append(pba.destructionPct).append("%)\n");
			}

			// Determine which attacks to punish based on mode
			if (mode == 1) {
				// Once per player – only the first bad attack
				punishableAttacks.add(memberBad.get(0));
			} else {
				// Mode 2 or 3 – each bad attack (mode 3 already filtered above)
				punishableAttacks.addAll(memberBad);
			}
		}

		return new CWBadAttacksResult(message.toString(), hasBadAttacks, punishableAttacks);
	}

	private CWBadAttacksResult buildCWLDayBadAttacksResult(Clan clan, org.json.JSONObject ourClanData,
			org.json.JSONObject warData, int roundNumber, boolean isVerificationPhase, int targetStars) {

		org.json.JSONArray members = ourClanData.getJSONArray("members");

		// Gather clan/sideclan info for footnotes
		String eventClanTag = clan.getTag();
		String warClanName = ourClanData.getString("name");
		String belongsTo1 = DBUtil.getValueFromSQL("SELECT belongs_to FROM sideclans WHERE clan_tag = ?", String.class, eventClanTag);
		String belongsTo2 = DBUtil.getValueFromSQL("SELECT belongs_to_2 FROM sideclans WHERE clan_tag = ?", String.class, eventClanTag);

		StringBuilder message = new StringBuilder();
		message.append("## CWL Day ").append(roundNumber + 1)
				.append(" – Schlechte Angriffe (").append(targetStars).append(" ★)\n");

		String endTimeStr = warData.getString("endTime");
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
		Instant endInstant = Instant.from(formatter.parse(endTimeStr));
		long millisRemaining = endInstant.toEpochMilli() - System.currentTimeMillis();

		if (isVerificationPhase || millisRemaining <= 0) {
			message.append("**Krieg beendet.**\n\n");
		} else {
			long durationToShow = getDurationUntilEnd() > 0 ? getDurationUntilEnd() : millisRemaining;
			int secondsLeft = (int) (durationToShow / 1000);
			int minutesLeft = secondsLeft / 60;
			int hoursLeft = minutesLeft / 60;
			secondsLeft = secondsLeft % 60;
			minutesLeft = minutesLeft % 60;
			if (hoursLeft > 0) message.append("**").append(hoursLeft).append("h** ");
			if (minutesLeft > 0) message.append("**").append(minutesLeft).append("m** ");
			if (secondsLeft > 0 && hoursLeft == 0) message.append("**").append(secondsLeft).append("s** ");
			message.append("verbleibend\n\n");
		}

		// Global footnote: clan name and sideclan parent(s)
		message.append("-# Clan: ").append(warClanName);
		if (belongsTo1 != null && !belongsTo1.isEmpty()) {
			Clan mainClan1 = new Clan(belongsTo1);
			String mainName1 = mainClan1.getNameDB();
			message.append(" | Gehört zu: ").append(mainName1 != null ? mainName1 : belongsTo1);
			if (belongsTo2 != null && !belongsTo2.isEmpty()) {
				Clan mainClan2 = new Clan(belongsTo2);
				String mainName2 = mainClan2.getNameDB();
				message.append(", ").append(mainName2 != null ? mainName2 : belongsTo2);
			}
		}
		message.append("\n\n");

		boolean hasBadAttacks = false;
		ArrayList<PlayerBadAttack> punishableAttacks = new ArrayList<>();

		for (int i = 0; i < members.length(); i++) {
			org.json.JSONObject member = members.getJSONObject(i);
			String tag = member.getString("tag");
			String name = member.getString("name");

			if (!member.has("attacks")) continue;
			org.json.JSONArray attacks = member.getJSONArray("attacks");
			if (attacks.length() == 0) continue;

			// CWL has exactly 1 attack per member
			org.json.JSONObject attack = attacks.getJSONObject(0);
			int stars = attack.optInt("stars", 0);
			if (stars != targetStars) continue;

			Player p = new Player(tag);
			if (p.isHiddenColeader()) continue;

			hasBadAttacks = true;
			message.append("- ").append(name).append(" (").append(tag).append(")")
					.append(" – ").append(stars).append(" ★ (")
					.append(attack.optInt("destructionPercentage", 0)).append("%)\n");

			// Footnote: warn if player won't receive a kickpoint
			if (getActionType() == ACTIONTYPE.STARFAILS_KICKPOINT) {
				Clan playerClanDB = p.getClanDB();
				boolean matchesEventClan = false;
				if (playerClanDB != null) {
					String pct = playerClanDB.getTag();
					matchesEventClan = pct.equals(eventClanTag)
							|| (belongsTo1 != null && !belongsTo1.isEmpty() && pct.equals(belongsTo1))
							|| (belongsTo2 != null && !belongsTo2.isEmpty() && pct.equals(belongsTo2));
				}
				if (!matchesEventClan) {
					if (playerClanDB != null) {
						String foundName = playerClanDB.getNameDB();
						message.append("-# Kein Kickpunkt – gefunden in: ")
								.append(foundName != null ? foundName : playerClanDB.getTag()).append("\n");
					} else {
						message.append("-# Kein Kickpunkt – nicht in Datenbank\n");
					}
				}
			}

			punishableAttacks.add(new PlayerBadAttack(p, stars,
					attack.optInt("order", 1),
					attack.optInt("destructionPercentage", 0)));
		}

		return new CWBadAttacksResult(message.toString(), hasBadAttacks, punishableAttacks);
	}

	private void handleCWBadAttacks(Clan clan, org.json.JSONObject cwJson) {
		int targetStars = getConfiguredStarCount();
		if (targetStars < 0) return;
		int mode = getConfiguredPunishmentMode();

		CWBadAttacksResult result = buildCWBadAttacksResult(clan, cwJson, targetStars, mode, false);
		if (!result.hasBadAttacks) return;

		boolean isEndOfWarEvent = getDurationUntilEnd() <= 0;

		if (isEndOfWarEvent) {
			Message sentMessage = sendMessageToChannelAndReturn(result.message);
			if (sentMessage != null) {
				final String clanTag = clan.getTag();
				final long messageId = sentMessage.getIdLong();
				final String channelId = getChannelID();
				final ListeningEvent thisEvent = this;
				final String originalMessage = result.message;
				final String endTimeStr = cwJson.has("endTime") ? cwJson.getString("endTime") : "";

				Bot.activeVerificationTasks.incrementAndGet();
				ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
				scheduler.schedule(() -> {
					try {
						handleCWBadAttacksDelayedVerification(clanTag, messageId, channelId, thisEvent,
								originalMessage, endTimeStr);
					} catch (Exception e) {
						System.err.println("Error in delayed CW bad attacks verification: " + e.getMessage());					} finally {
						Bot.activeVerificationTasks.decrementAndGet();
						scheduler.shutdown();
					}
				}, 5, TimeUnit.MINUTES);
			}
		} else {
			sendMessageInChunks(result.message);
		}
	}

	private void handleCWBadAttacksDelayedVerification(String clanTag, long messageId, String channelId,
			ListeningEvent event, String originalMessage, String endTimeStr) {

		System.out.println("Starting 5-minute CW bad attacks verification for clan " + clanTag);

		try {
			Clan clan = new Clan(clanTag);
			org.json.JSONObject cwJson = clan.getCWJson();
			String currentState = cwJson.getString("state");
			boolean dataIsReliable = currentState.equals("warEnded");
			boolean sameWar = dataIsReliable && cwJson.has("endTime")
					&& cwJson.getString("endTime").equals(endTimeStr);

			int targetStars = event.getConfiguredStarCount();
			int mode = event.getConfiguredPunishmentMode();

			String updatedMessage;
			boolean shouldProcessKickpoints = false;
			CWBadAttacksResult result = null;

			if (dataIsReliable && sameWar) {
				result = buildCWBadAttacksResult(clan, cwJson, targetStars, mode, true);
				updatedMessage = result.message + "\n\n*Daten nach 5min überprüft*";
				shouldProcessKickpoints = result.hasBadAttacks
						&& event.getActionType() == ACTIONTYPE.STARFAILS_KICKPOINT;
			} else {
				updatedMessage = originalMessage
						+ "\n\n*Daten sind nicht zuverlässig, da Krieg direkt wieder gestartet wurde*";
			}

			editMessageInChannel(channelId, messageId, updatedMessage);

			if (shouldProcessKickpoints && result != null) {
				for (PlayerBadAttack pba : result.badAttacks) {
					addKickpointForPlayer(pba.player,
							"CW Angriff " + pba.attackIndex + ": " + pba.stars + "★");
				}
			}

			System.out.println("Completed 5-minute CW bad attacks verification for clan " + clanTag
					+ " (dataReliable=" + (dataIsReliable && sameWar) + ", kickpoints=" + shouldProcessKickpoints + ")");

		} catch (JSONException e) {
			System.err.println("Error in CW bad attacks delayed verification for clan " + clanTag + ": " + e.getMessage());			try {
				editMessageInChannel(channelId, messageId,
						originalMessage + "\n\n*Fehler bei der 5-Minuten-Überprüfung.*");
			} catch (Exception e2) {
				System.err.println("Failed to update message with error: " + e2.getMessage());
			}
		}
	}

	private void handleCWLDayBadAttacks(Clan clan, org.json.JSONObject ourClanData,
			org.json.JSONObject warData, int roundNumber, String warTag) {

		int targetStars = getConfiguredStarCount();
		if (targetStars < 0) return;

		CWBadAttacksResult result = buildCWLDayBadAttacksResult(clan, ourClanData, warData, roundNumber, false, targetStars);
		if (!result.hasBadAttacks) return;

		boolean isEndOfWarEvent = getDurationUntilEnd() <= 0;

		if (isEndOfWarEvent) {
			Message sentMessage = sendMessageToChannelAndReturn(result.message);
			if (sentMessage != null) {
				final String clanTag = clan.getTag();
				final long messageId = sentMessage.getIdLong();
				final String channelId = getChannelID();
				final ListeningEvent thisEvent = this;
				final String originalMessage = result.message;
				final int finalRound = roundNumber;
				final String finalWarTag = warTag;

				Bot.activeVerificationTasks.incrementAndGet();
				ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
				scheduler.schedule(() -> {
					try {
						handleCWLDayBadAttacksDelayedVerification(clanTag, finalRound, finalWarTag,
								messageId, channelId, thisEvent, originalMessage);
					} catch (Exception e) {
						System.err.println("Error in delayed CWL bad attacks verification: " + e.getMessage());					} finally {
						Bot.activeVerificationTasks.decrementAndGet();
						scheduler.shutdown();
					}
				}, 5, TimeUnit.MINUTES);
			}
		} else {
			sendMessageInChunks(result.message);
		}
	}

	private void handleCWLDayBadAttacksDelayedVerification(String clanTag, int roundNumber, String warTag,
			long messageId, String channelId, ListeningEvent event, String originalMessage) {

		System.out.println("Starting 5-minute CWL bad attacks verification for clan " + clanTag
				+ " round " + (roundNumber + 1));

		try {
			org.json.JSONObject warData = Clan.getCWLDayJson(warTag);
			String currentState = warData.getString("state");
			boolean dataIsReliable = currentState.equals("warEnded");

			int targetStars = event.getConfiguredStarCount();

			String updatedMessage;
			boolean shouldProcessKickpoints = false;
			CWBadAttacksResult result = null;

			if (dataIsReliable) {
				Clan clan = new Clan(clanTag);
				org.json.JSONObject clanData = warData.getJSONObject("clan");
				org.json.JSONObject opponentData = warData.getJSONObject("opponent");
				org.json.JSONObject ourClanData = clanData.getString("tag").equals(clanTag) ? clanData : opponentData;

				result = buildCWLDayBadAttacksResult(clan, ourClanData, warData, roundNumber, true, targetStars);
				updatedMessage = result.message + "\n*Daten nach 5min überprüft*";
				shouldProcessKickpoints = result.hasBadAttacks
						&& event.getActionType() == ACTIONTYPE.STARFAILS_KICKPOINT;
			} else {
				updatedMessage = originalMessage + "\n\n*Daten sind möglicherweise nicht zuverlässig*";
			}

			editMessageInChannel(channelId, messageId, updatedMessage);

			if (shouldProcessKickpoints && result != null) {
				for (PlayerBadAttack pba : result.badAttacks) {
					addKickpointForPlayer(pba.player,
							"CWL Angriff: " + pba.stars + "★ (Day " + (roundNumber + 1) + ")");
				}
			}

			System.out.println("Completed 5-minute CWL bad attacks verification for clan " + clanTag
					+ " (dataReliable=" + dataIsReliable + ", kickpoints=" + shouldProcessKickpoints + ")");

		} catch (JSONException e) {
			System.err.println("Error in CWL bad attacks delayed verification for clan " + clanTag + ": " + e.getMessage());			try {
				editMessageInChannel(channelId, messageId,
						originalMessage + "\n\n*Fehler bei der 5-Minuten-Überprüfung.*");
			} catch (Exception e2) {
				System.err.println("Failed to update message with error: " + e2.getMessage());
			}
		}
	}

}
