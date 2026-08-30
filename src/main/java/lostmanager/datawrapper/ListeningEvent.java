package lostmanager.datawrapper;

import java.sql.PreparedStatement;
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
import lostmanager.util.ClanGamesWindow;
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
		CW, RAID, CWLDAY, CS, FIXTIMEINTERVAL, CWLEND, SEASONEND
	}

	public enum ACTIONTYPE {
		INFOMESSAGE, CUSTOMMESSAGE, KICKPOINT, CWDONATOR, FILLER, RAIDFAILS, STARFAILS, STARFAILS_KICKPOINT,
		CWCOUNT, CWCOUNT_KICKPOINT
	}

	// Keys of the named settings stored in actionvalues (ActionValue.kind.setting).
	// Unlike the positional value entries these can be added without changing how
	// existing events are read.

	/** Hand out kickpoints for missed attacks even when the war was perfect. */
	public static final String SETTING_IGNORE_PERFECT_WAR = "ignore_perfect_war";
	/** Hand out raid district kickpoints even if the data could not be verified. */
	public static final String SETTING_RAID_FORCE_KICKPOINTS = "raid_force_kickpoints";
	/** Minimum season wins; falls back to the clan setting min_season_wins. */
	public static final String SETTING_WINS_THRESHOLD = "wins_threshold";
	/**
	 * Number of bad attacks a player may make before being punished. 0 punishes
	 * every bad attack, which is how star fail events behaved before this existed.
	 * The budget is counted per war for clan wars and across the whole CWL season
	 * for CWL.
	 */
	public static final String SETTING_STARFAILS_FREE_HITS = "starfails_free_hits";
	/** Minimum number of clan wars a member has to be in the lineup of per season. */
	public static final String SETTING_CW_MIN_COUNT = "cw_min_count";

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
                                case "seasonend" -> listeningtype = LISTENINGTYPE.SEASONEND;
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
																	: type.equals("starfails_kickpoint") ? ACTIONTYPE.STARFAILS_KICKPOINT
											: type.equals("cwcount") ? ACTIONTYPE.CWCOUNT
													: type.equals("cwcount_kickpoint") ? ACTIONTYPE.CWCOUNT_KICKPOINT : null;
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

	/**
	 * Reads a named setting ({@link ActionValue.kind#setting}) from the action
	 * values. Named settings are looked up by key instead of by position, so a new
	 * option can be added without shifting the meaning of the positional
	 * {@link ActionValue.kind#value} entries that older events rely on.
	 *
	 * @return the configured value, or {@code defaultValue} if the event has no
	 *         such setting
	 */
	public Long getSetting(String key, Long defaultValue) {
		ArrayList<ActionValue> avs = getActionValues();
		if (avs == null || key == null) {
			return defaultValue;
		}
		for (ActionValue av : avs) {
			if (av.getSaved() == ActionValue.kind.setting && key.equals(av.getKey()) && av.getValue() != null) {
				return av.getValue();
			}
		}
		return defaultValue;
	}

	/**
	 * Reads a named setting as a flag. The config modals use 1 -> yes and 2 -> no,
	 * so anything other than 1 counts as "off".
	 */
	public boolean getFlag(String key, boolean defaultValue) {
		Long raw = getSetting(key, null);
		if (raw == null) {
			return defaultValue;
		}
		return raw == 1L;
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
				case SEASONEND -> {
                                    // Season = calendar month, same definition the /wins command uses
                                    endTimeMillis = lostmanager.util.SeasonUtil.fetchSeasonEndTime().getTime();
                                    timestamptofire = endTimeMillis - getDurationUntilEnd();
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

				case SEASONEND -> {
					// The listening type says WHEN, the action type says WHAT is checked
					if (getActionType() == ACTIONTYPE.CWCOUNT
							|| getActionType() == ACTIONTYPE.CWCOUNT_KICKPOINT) {
						handleSeasonCWCountEvent(clan);
					} else {
						handleSeasonWinsEvent(clan);
					}
				}

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

	/**
	 * The baseline a player's clan games points are measured against: the earliest
	 * "Games Champion" snapshot belonging to a window.
	 */
	private static class ClanGamesBaseline {
		final int points;
		/** True when the snapshot was taken after the window had already started. */
		final boolean late;

		ClanGamesBaseline(int points, boolean late) {
			this.points = points;
			this.late = late;
		}
	}

	/**
	 * Looks up the baseline of a player for the given clan games window.
	 *
	 * The snapshot is matched against the whole window instead of one exact
	 * instant, so a baseline that was taken a bit late (bot restart, member joined
	 * mid-games) is still found. The earliest snapshot wins, because that is the
	 * one closest to the real starting value.
	 *
	 * @return the baseline, or null if the player has no snapshot for this window
	 */
	private static ClanGamesBaseline getClanGamesBaseline(String playerTag, ClanGamesWindow window) {
		String timeSql = "SELECT time FROM achievement_data "
				+ "WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time >= ? AND time < ? "
				+ "ORDER BY time ASC LIMIT 1";
		java.sql.Timestamp takenAt = DBUtil.getValueFromSQL(timeSql, java.sql.Timestamp.class, playerTag,
				window.getBaselineLookupStart(), window.getEndTimestamp());

		if (takenAt == null) {
			return null;
		}

		String pointsSql = "SELECT data::text::integer FROM achievement_data "
				+ "WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? LIMIT 1";
		Integer points = DBUtil.getValueFromSQL(pointsSql, Integer.class, playerTag, takenAt);

		if (points == null) {
			return null;
		}

		return new ClanGamesBaseline(points, takenAt.after(window.getBaselineLateAfter()));
	}

	/**
	 * Current "Games Champion" value of a player, or null if it cannot be read.
	 *
	 * The player's API name is filled in from the same response, because
	 * {@link Player#getNameAPI()} would otherwise trigger a second request for
	 * every single member.
	 */
	private static Integer getCurrentClanGamesPoints(Player player) {
		try {
			String json = player.getJson();
			if (json == null) {
				return null;
			}
			JSONObject playerJson = new JSONObject(json);
			if (playerJson.has("name")) {
				player.setNameAPI(playerJson.getString("name"));
			}

			org.json.JSONArray achievements = playerJson.getJSONArray("achievements");
			for (int i = 0; i < achievements.length(); i++) {
				org.json.JSONObject achievement = achievements.getJSONObject(i);
				if (achievement.getString("name").equals("Games Champion")) {
					return achievement.getInt("value");
				}
			}
		} catch (JSONException e) {
			System.err.println("Error reading clan games points for player " + player.getTag() + ": " + e.getMessage());
		}
		return null;
	}

	/**
	 * Formats a remaining duration in the style the war and raid messages use,
	 * e.g. "**2d** **6h** ". Clan games run for days, so days are included.
	 *
	 * @return the formatted value including a trailing space, ready to be followed
	 *         by "verbleibend"
	 */
	private static String formatRemaining(long millis) {
		int totalMinutes = (int) (millis / 60000);
		int days = totalMinutes / (60 * 24);
		int hours = (totalMinutes / 60) % 24;
		int minutes = totalMinutes % 60;

		StringBuilder sb = new StringBuilder();
		if (days > 0) {
			sb.append("**").append(days).append("d** ");
		}
		if (hours > 0) {
			sb.append("**").append(hours).append("h** ");
		}
		// Minutes only matter when the end is close enough for them to be useful
		if (minutes > 0 && days == 0) {
			sb.append("**").append(minutes).append("m** ");
		}
		if (sb.length() == 0) {
			sb.append("**<1m** ");
		}
		return sb.toString();
	}

	/** Result of one clan games evaluation. */
	private static class ClanGamesResult {
		final String message;
		final boolean hasViolations;
		final ArrayList<Tuple<Player, Integer>> playersToPenalize;
		/** No member could be rated at all - the baseline snapshot is missing. */
		final boolean baselineMissingForEveryone;

		ClanGamesResult(String message, boolean hasViolations, ArrayList<Tuple<Player, Integer>> playersToPenalize,
				boolean baselineMissingForEveryone) {
			this.message = message;
			this.hasViolations = hasViolations;
			this.playersToPenalize = playersToPenalize;
			this.baselineMissingForEveryone = baselineMissingForEveryone;
		}
	}

	/**
	 * Evaluates the clan games for the given window.
	 *
	 * Points earned are the difference between the current "Games Champion" value
	 * and the baseline taken at the start of the window. Members without a usable
	 * baseline are reported separately and never punished - their measured value
	 * would be too low through no fault of their own, either because the bot did
	 * not snapshot in time or because they joined the clan mid-games.
	 */
	private ClanGamesResult buildClanGamesResult(Clan clan, int threshold, ClanGamesWindow window,
			boolean isVerificationPhase) {

		StringBuilder violationLines = new StringBuilder();
		StringBuilder unratedLines = new StringBuilder();
		ArrayList<Tuple<Player, Integer>> playersToPenalize = new ArrayList<>();
		int rated = 0;
		int unrated = 0;

		for (Player p : clan.getPlayersDB()) {
			// Hidden co-leaders do not have to participate in clan games
			if (p.isHiddenColeader()) {
				continue;
			}

			// Skip signed-off members
			MemberSignoff signoff = new MemberSignoff(p.getTag());
			if (signoff.isActive() && !signoff.isReceivePings()) {
				continue;
			}

			ClanGamesBaseline baseline = getClanGamesBaseline(p.getTag(), window);
			Integer currentPoints = baseline != null ? getCurrentClanGamesPoints(p) : null;

			if (baseline == null || baseline.late || currentPoints == null) {
				unrated++;
				unratedLines.append("- ").append(p.getNameAPI());
				if (baseline == null) {
					unratedLines.append(" (kein Startwert)");
				} else if (baseline.late) {
					unratedLines.append(" (Startwert zu spät erfasst");
					if (currentPoints != null) {
						unratedLines.append(", seitdem ").append(Math.max(currentPoints - baseline.points, 0))
								.append(" Punkte");
					}
					unratedLines.append(")");
				} else {
					unratedLines.append(" (aktuelle Punkte nicht abrufbar)");
				}
				unratedLines.append("\n");
				continue;
			}

			rated++;
			int difference = Math.max(currentPoints - baseline.points, 0);

			if (difference < threshold) {
				violationLines.append("- ").append(p.getNameAPI()).append(": ").append(difference).append("/")
						.append(threshold).append(" Punkte");
				if (p.getUser() != null && !isVerificationPhase) {
					violationLines.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				violationLines.append("\n");
				playersToPenalize.add(new Tuple<>(p, difference));
			}
		}

		// A window where nobody could be rated means the baseline job did not run -
		// punishing the whole clan for that would be wrong, so nothing is handed out
		boolean baselineMissingForEveryone = rated == 0 && unrated > 0;

		// While the games are still running this is a reminder, not a verdict - the
		// wording has to reflect that nobody has failed anything yet
		long remainingMillis = window.getEndMillis() - System.currentTimeMillis();
		boolean isReminder = !isVerificationPhase && remainingMillis > 0;

		StringBuilder message = new StringBuilder();
		message.append("## Clan Games - ").append(clan.getInfoString()).append("\n");
		if (isReminder) {
			message.append(formatRemaining(remainingMillis)).append("verbleibend\n");
		} else {
			message.append("**Clan Games beendet.**\n");
		}
		message.append("**Ziel:** ").append(threshold).append(" Punkte\n\n");

		boolean hasViolations = violationLines.length() > 0 && !baselineMissingForEveryone;

		if (baselineMissingForEveryone) {
			message.append(
					"**Keine Auswertung möglich - für keinen Spieler existiert ein Startwert.**\n")
					.append("Es werden keine Kickpunkte vergeben.\n");
		} else if (hasViolations) {
			message.append(isReminder ? "### Noch offen\n" : "### Ziel nicht erreicht\n").append(violationLines);
		} else {
			message.append(isReminder
					? "Alle gewerteten Mitglieder haben das Ziel bereits erreicht.\n"
					: "Alle gewerteten Mitglieder haben das Ziel erreicht.\n");
		}

		if (unratedLines.length() > 0) {
			message.append("\n### Keine Wertung (kein Startwert / zu spät dazugekommen)\n").append(unratedLines);
		}

		return new ClanGamesResult(message.toString(), hasViolations,
				baselineMissingForEveryone ? new ArrayList<>() : playersToPenalize, baselineMissingForEveryone);
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

		ClanGamesWindow window = ClanGamesWindow.currentOrPrevious(System.currentTimeMillis());
		ClanGamesResult result = buildClanGamesResult(clan, threshold, window, false);

		// A missing baseline is always worth reporting - otherwise a kickpoint event
		// would silently do nothing and nobody would notice the data gap
		if (!result.hasViolations && !result.baselineMissingForEveryone
				&& getActionType() != ACTIONTYPE.INFOMESSAGE) {
			return;
		}

		// Reminders during the games are posted as-is; only the final evaluation gets
		// a verification pass before kickpoints are handed out
		boolean isEndOfGamesEvent = System.currentTimeMillis() >= window.getEndMillis();
		if (!isEndOfGamesEvent) {
			sendMessageInChunks(result.message);
			return;
		}

		Message sentMessage = sendMessageToChannelAndReturn(truncateForDiscord(result.message));
		if (sentMessage == null) {
			return;
		}

		final Long messageId = sentMessage.getIdLong();
		final String channelId = getChannelID();
		final String clanTag = clan.getTag();
		final String originalMessage = result.message;
		final ListeningEvent thisEvent = this;
		final int finalThreshold = threshold;
		final ClanGamesWindow finalWindow = window;

		// Verify after 5 minutes before handing out kickpoints, so a hiccup of the
		// CoC API at fire time cannot produce wrong kickpoints
		lostmanager.Bot.activeVerificationTasks.incrementAndGet();
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.schedule(() -> {
			try {
				handleClanGamesDelayedVerification(clanTag, messageId, channelId, thisEvent, originalMessage,
						finalThreshold, finalWindow);
			} catch (Exception e) {
				System.err.println("Error in delayed clan games verification: " + e.getMessage());
			} finally {
				lostmanager.Bot.activeVerificationTasks.decrementAndGet();
				scheduler.shutdown();
			}
		}, 5, TimeUnit.MINUTES);

		System.out.println("Scheduled 5-minute clan games verification for clan " + clanTag + " (window "
				+ window.getKey() + ")");
	}

	/**
	 * Re-evaluates the clan games with fresh data and hands out the kickpoints.
	 */
	private void handleClanGamesDelayedVerification(String clanTag, Long messageId, String channelId,
			ListeningEvent event, String originalMessage, int threshold, ClanGamesWindow window) {

		System.out.println("Starting 5-minute clan games verification for clan " + clanTag);

		try {
			Clan clan = new Clan(clanTag);
			ClanGamesResult result = buildClanGamesResult(clan, threshold, window, true);

			editMessageInChannel(channelId, messageId,
					truncateForDiscord(result.message, "\n*Daten nach 5min überprüft*"));

			boolean shouldProcessKickpoints = result.hasViolations && event.getActionType() == ACTIONTYPE.KICKPOINT;
			if (shouldProcessKickpoints) {
				for (Tuple<Player, Integer> entry : result.playersToPenalize) {
					addKickpointForPlayer(entry.getFirst(),
							"Clan Games nicht erreicht (" + entry.getSecond() + "/" + threshold + ")");
				}
			}

			System.out.println("Completed 5-minute clan games verification for clan " + clanTag + " (kickpoints="
					+ shouldProcessKickpoints + ")");

		} catch (Exception e) {
			System.err.println("Error in clan games delayed verification for clan " + clanTag + ": " + e.getMessage());
			try {
				editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
						"\n*Fehler bei der 5-Minuten-Überprüfung. Keine Kickpunkte vergeben.*"));
			} catch (Exception e2) {
				System.err.println("Failed to update message with error: " + e2.getMessage());
			}
		}
	}

	/**
	 * Reports every member below the required amount of season wins and - for
	 * kickpoint events - hands out kickpoints for it.
	 *
	 * The wins of a season are the difference of the "Conqueror" achievement
	 * between the start of the month and now, exactly like the /wins command
	 * computes it. Members without a usable baseline (no data, or linked after the
	 * season had already started) are listed separately and never punished, because
	 * their number would be too low through no fault of their own.
	 */
	private void handleSeasonWinsEvent(Clan clan) {
		// Threshold from the event itself, otherwise the clan-wide setting
		Long threshold = getSetting(SETTING_WINS_THRESHOLD, null);
		if (threshold == null) {
			threshold = clan.getMinSeasonWins();
		}
		if (threshold == null || threshold <= 0) {
			System.out.println("Skipping season wins event " + getId() + " for clan " + clan.getTag()
					+ " - no threshold configured (neither on the event nor as min_season_wins)");
			return;
		}

		// The season being judged - at the boundary the calendar month has already
		// rolled over, so "current month" would be the new, still empty season
		java.time.YearMonth season = lostmanager.util.SeasonUtil.evaluatedSeason(System.currentTimeMillis());
		long seasonEndMillis = lostmanager.util.SeasonUtil.seasonEnd(season).getTime();
		boolean seasonOver = System.currentTimeMillis() >= seasonEndMillis;

		java.time.ZoneId zone = java.time.ZoneId.systemDefault();
		java.time.ZonedDateTime startOfSeason = season.atDay(1).atStartOfDay(zone);
		java.time.ZonedDateTime startOfNextSeason = startOfSeason.plusMonths(1);

		StringBuilder violationLines = new StringBuilder();
		StringBuilder unratedLines = new StringBuilder();
		ArrayList<Player> playersToPenalize = new ArrayList<>();

		for (Player p : clan.getPlayersDB()) {
			// Hidden co-leaders are exempt from all requirements
			if (p.isHiddenColeader()) {
				continue;
			}

			// Skip signed-off members
			MemberSignoff signoff = new MemberSignoff(p.getTag());
			if (signoff.isActive() && !signoff.isReceivePings()) {
				continue;
			}

			Player.WinsData winsData;
			try {
				// Measured against the baseline of the judged season, not of whatever
				// month the clock happens to be in
				winsData = p.getMonthlyWins(season.getYear(), season.getMonthValue(), true, startOfSeason,
						startOfNextSeason, zone);
			} catch (Exception e) {
				System.err.println("Error reading season wins for player " + p.getTag() + ": " + e.getMessage());
				winsData = null;
			}

			// No baseline or linked mid-season - report only, never punish
			if (winsData == null || winsData.wins == null || winsData.hasWarning) {
				unratedLines.append("- ").append(p.getNameAPI());
				if (winsData != null && winsData.wins != null) {
					unratedLines.append(" (").append(winsData.wins).append(" seit Verlinkung)");
				}
				unratedLines.append("\n");
				continue;
			}

			if (winsData.wins < threshold) {
				violationLines.append("- ").append(p.getNameAPI()).append(": ").append(winsData.wins).append("/")
						.append(threshold);
				if (p.getUser() != null) {
					violationLines.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				violationLines.append("\n");
				playersToPenalize.add(p);
			}
		}

		boolean hasViolations = violationLines.length() > 0;
		if (!hasViolations && getActionType() != ACTIONTYPE.INFOMESSAGE) {
			return;
		}

		StringBuilder message = new StringBuilder();
		message.append("## Season Wins - ").append(clan.getInfoString()).append("\n");
		if (seasonOver) {
			message.append("**Season beendet.**\n");
		} else {
			message.append(formatRemaining(seasonEndMillis - System.currentTimeMillis()))
					.append("verbleibend\n");
		}
		message.append("**Minimum:** ").append(threshold).append(" Wins\n\n");

		if (hasViolations) {
			message.append(seasonOver ? "### Nicht erreicht\n" : "### Noch offen\n")
					.append(violationLines);
		} else {
			message.append("Alle Mitglieder haben das Minimum erreicht.\n");
		}

		if (unratedLines.length() > 0) {
			message.append("\n### Keine Wertung (zu spät verlinkt / keine Daten)\n").append(unratedLines);
		}

		sendMessageInChunks(message.toString());

		// Reminders during the season must never punish - the season is not over yet
		if (seasonOver && getActionType() == ACTIONTYPE.KICKPOINT) {
			for (Player p : playersToPenalize) {
				addKickpointForPlayer(p, "Season Wins nicht erreicht");
			}
		}
	}

	/**
	 * Reports every member who was in the lineup of fewer clan wars this season
	 * than required and - for kickpoint events - hands out kickpoints for it.
	 *
	 * Participation comes from cw_participation, which the background task fills
	 * while wars are running; the CoC API cannot be asked after the fact. Only
	 * regular clan wars count, CWL rounds are not recorded for this on purpose.
	 *
	 * Being in the lineup is what counts, not whether the attacks were used -
	 * missed attacks are already punished by the clan war events, and a member
	 * should not be hit twice for the same war.
	 */
	private void handleSeasonCWCountEvent(Clan clan) {
		Long required = getSetting(SETTING_CW_MIN_COUNT, null);
		if (required == null || required <= 0) {
			System.out.println("Skipping season CW count event " + getId() + " for clan " + clan.getTag()
					+ " - no minimum configured");
			return;
		}

		// The season being judged - see handleSeasonWinsEvent for why this is not
		// simply the current calendar month
		java.time.YearMonth season = lostmanager.util.SeasonUtil.evaluatedSeason(System.currentTimeMillis());
		java.sql.Timestamp seasonStart = lostmanager.util.SeasonUtil.seasonStart(season);
		java.sql.Timestamp seasonEnd = lostmanager.util.SeasonUtil.seasonEnd(season);
		boolean seasonOver = System.currentTimeMillis() >= seasonEnd.getTime();

		// Clans the player may have warred in - the event clan plus the clans it
		// belongs to, mirroring how kickpoints resolve sideclans
		ArrayList<String> clanTags = new ArrayList<>();
		clanTags.add(clan.getTag());
		for (String column : new String[] { "belongs_to", "belongs_to_2" }) {
			String parent = DBUtil.getValueFromSQL("SELECT " + column + " FROM sideclans WHERE clan_tag = ?",
					String.class, clan.getTag());
			if (parent != null && !parent.isEmpty() && !clanTags.contains(parent)) {
				clanTags.add(parent);
			}
		}
		String placeholders = String.join(",", java.util.Collections.nCopies(clanTags.size(), "?"));

		// If recording only started during the season, the counts are incomplete
		// through no fault of the members - report, but never punish
		java.sql.Timestamp firstRecorded = DBUtil.getValueFromSQL(
				"SELECT MIN(war_end_time) AS first_recorded FROM cw_participation WHERE clan_tag IN (" + placeholders + ")",
				java.sql.Timestamp.class, clanTags.toArray());
		boolean historyIncomplete = firstRecorded == null || firstRecorded.after(seasonStart);

		StringBuilder violationLines = new StringBuilder();
		StringBuilder unratedLines = new StringBuilder();
		ArrayList<Tuple<Player, Integer>> playersToPenalize = new ArrayList<>();

		for (Player p : clan.getPlayersDB()) {
			if (p.isHiddenColeader()) {
				continue;
			}

			MemberSignoff signoff = new MemberSignoff(p.getTag());
			if (signoff.isActive() && !signoff.isReceivePings()) {
				continue;
			}

			Object[] params = new Object[clanTags.size() + 3];
			params[0] = p.getTag();
			for (int i = 0; i < clanTags.size(); i++) {
				params[i + 1] = clanTags.get(i);
			}
			params[clanTags.size() + 1] = seasonStart;
			params[clanTags.size() + 2] = seasonEnd;

			Long count = DBUtil.getValueFromSQL(
					"SELECT COUNT(DISTINCT war_end_time) AS war_count FROM cw_participation "
							+ "WHERE player_tag = ? AND war_type = 'cw' AND clan_tag IN (" + placeholders + ") "
							+ "AND war_end_time >= ? AND war_end_time < ?",
					Long.class, params);
			int wars = count != null ? count.intValue() : 0;

			// Someone who joined after the season began could not have played them all
			java.sql.Timestamp joinedAt = DBUtil.getValueFromSQL(
					"SELECT joined_at FROM clan_members WHERE player_tag = ?", java.sql.Timestamp.class, p.getTag());
			boolean joinedMidSeason = joinedAt != null && joinedAt.after(seasonStart);

			if (joinedMidSeason) {
				unratedLines.append("- ").append(p.getNameAPI()).append(": ").append(wars).append(" CWs")
						.append(" (erst seit dem ")
						.append(new java.text.SimpleDateFormat("dd.MM.").format(joinedAt))
						.append(" im Clan)\n");
				continue;
			}

			if (wars < required) {
				violationLines.append("- ").append(p.getNameAPI()).append(": ").append(wars).append("/")
						.append(required).append(" CWs");
				if (p.getUser() != null) {
					violationLines.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				violationLines.append("\n");
				playersToPenalize.add(new Tuple<>(p, wars));
			}
		}

		boolean hasViolations = violationLines.length() > 0 && !historyIncomplete;

		if (!hasViolations && !historyIncomplete && getActionType() != ACTIONTYPE.CWCOUNT) {
			return;
		}

		StringBuilder message = new StringBuilder();
		message.append("## CW-Teilnahme - ").append(clan.getInfoString()).append("\n");
		if (seasonOver) {
			message.append("**Season beendet.**\n");
		} else {
			message.append(formatRemaining(seasonEnd.getTime() - System.currentTimeMillis()))
					.append("verbleibend\n");
		}
		message.append("**Minimum:** ").append(required).append(" CWs in dieser Season\n\n");

		if (historyIncomplete) {
			message.append("**Keine Auswertung möglich - die Aufzeichnung der CW-Teilnahme läuft erst seit ")
					.append(firstRecorded == null ? "kurzem"
							: new java.text.SimpleDateFormat("dd.MM.yyyy").format(firstRecorded))
					.append(".**\nDiese Season wird noch nicht gewertet, es werden keine Kickpunkte vergeben.\n");
			if (violationLines.length() > 0) {
				message.append("\n### Unter dem Minimum (nur zur Info)\n").append(violationLines);
			}
		} else if (hasViolations) {
			message.append(seasonOver ? "### Minimum nicht erreicht\n" : "### Noch offen\n")
					.append(violationLines);
		} else {
			message.append("Alle gewerteten Mitglieder haben das Minimum erreicht.\n");
		}

		if (unratedLines.length() > 0) {
			message.append("\n### Keine Wertung (mitten in der Season dazugekommen)\n").append(unratedLines);
		}

		sendMessageInChunks(message.toString());

		// Reminders during the season must never punish
		if (seasonOver && hasViolations && getActionType() == ACTIONTYPE.CWCOUNT_KICKPOINT) {
			for (Tuple<Player, Integer> entry : playersToPenalize) {
				addKickpointForPlayer(entry.getFirst(),
						"Zu wenige CWs (" + entry.getSecond() + "/" + required + ")");
			}
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

		if (warMemberList.isEmpty()) {
			sendMessageToChannel(
					"CW-Spender konnte nicht ausgelost werden: Es sind keine geeigneten Mitglieder verfügbar.");
			return;
		}

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
				chosen = pickRandomEligibleForEvent(warMemberList, map, excludeLeaders);
			}

			if (chosen == null) {
				message.append(map.getFirst()).append("-").append(map.getSecond())
						.append(": kein geeigneter Spender verfügbar\n");
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
				// Optional per-event override: normally a perfect war exempts everyone,
				// but a clan can choose to hand out kickpoints for missed attacks anyway.
				boolean ignorePerfectWar = event.getFlag(SETTING_IGNORE_PERFECT_WAR, false);

				if (isPerfectWar && !ignorePerfectWar) {
					updatedMessage = result.message + "\n\n*Daten nach 5min überprüft*\n**Perfekter Krieg erreicht! Keine Kickpunkte verteilt.**";
					shouldProcessKickpoints = false;
				} else {
					updatedMessage = result.message + "\n\n*Daten nach 5min überprüft*";
					if (isPerfectWar) {
						updatedMessage += "\n**Perfekter Krieg – Kickpunkte werden dennoch vergeben (Einstellung).**";
					}
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
				// Optional per-event override, same as for regular clan wars
				boolean ignorePerfectWar = event.getFlag(SETTING_IGNORE_PERFECT_WAR, false);

				if (isPerfectWar && !ignorePerfectWar) {
					updatedMessage = result.message + "\n*Daten nach 5min überprüft*\n**Perfekter Krieg erreicht! Keine Kickpunkte verteilt.**";
					shouldProcessKickpoints = false;
				} else {
					updatedMessage = result.message + "\n*Daten nach 5min überprüft*";
					if (isPerfectWar) {
						updatedMessage += "\n**Perfekter Krieg – Kickpunkte werden dennoch vergeben (Einstellung).**";
					}
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
	@SuppressWarnings("null")
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

			// Schedule the delayed verification (kickpoints only after verification).
			// The first check runs after 5 minutes because attacks can still resolve for
			// up to ~3 minutes past the raid end.
			scheduleRaidDistrictVerification(clanTag, messageId, channelId, thisEvent, originalMessage,
					raidEndTimeStr, capitalPeakMax, otherDistrictsMax, penalizeBoth, finalShouldAddKickpoints, 0);
		} catch (JSONException e) {
			System.err.println("Error analyzing raid districts: " + e.getMessage());
		}
	}

	/**
	 * Delays in minutes between the raid end and each verification attempt. The
	 * first attempt is never earlier than 5 minutes, because attacks that were
	 * started before the raid ended can still resolve for a few minutes afterwards.
	 * Later attempts exist because the API sometimes keeps reporting the raid as
	 * "ongoing" well past its own end time.
	 */
	private static final int[] RAID_VERIFICATION_DELAYS_MINUTES = { 5, 5, 10, 10 };

	/**
	 * Time after the raid's own end time from which no further attacks can land, so
	 * the attack log can be treated as final.
	 */
	private static final long RAID_END_GRACE_MS = 5 * 60 * 1000L;

	/**
	 * Schedules attempt number {@code attempt} of the raid district verification.
	 * Each attempt either finishes the verification or schedules the next one.
	 */
	private void scheduleRaidDistrictVerification(String clanTag, Long messageId, String channelId,
			ListeningEvent event, String originalMessage, String raidEndTimeStr, int capitalPeakMax,
			int otherDistrictsMax, int penalizeBoth, boolean shouldAddKickpoints, int attempt) {

		if (attempt >= RAID_VERIFICATION_DELAYS_MINUTES.length) {
			return;
		}

		int delayMinutes = RAID_VERIFICATION_DELAYS_MINUTES[attempt];

		lostmanager.Bot.activeVerificationTasks.incrementAndGet();
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.schedule(() -> {
			try {
				handleRaidDistrictAnalysisDelayedVerification(clanTag, messageId, channelId, event,
						originalMessage, raidEndTimeStr, capitalPeakMax, otherDistrictsMax, penalizeBoth,
						shouldAddKickpoints, attempt);
			} catch (Exception e) {
				System.err.println("Error in delayed raid district verification: " + e.getMessage());
			} finally {
				lostmanager.Bot.activeVerificationTasks.decrementAndGet();
				scheduler.shutdown();
			}
		}, delayMinutes, TimeUnit.MINUTES);

		System.out.println("Scheduled raid district verification attempt " + (attempt + 1) + "/"
				+ RAID_VERIFICATION_DELAYS_MINUTES.length + " for clan " + clanTag + " in " + delayMinutes
				+ " minutes");
	}

	/**
	 * Parses a raid end time as reported by the API.
	 *
	 * @return the end time in epoch millis, or null if it cannot be parsed
	 */
	private static Long parseRaidEndTime(String endTimeStr) {
		if (endTimeStr == null || endTimeStr.isEmpty()) {
			return null;
		}
		try {
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
					.withZone(ZoneOffset.UTC);
			return Instant.from(formatter.parse(endTimeStr)).toEpochMilli();
		} catch (Exception e) {
			System.err.println("Could not parse raid end time '" + endTimeStr + "': " + e.getMessage());
			return null;
		}
	}

	/**
	 * Handles the delayed verification of the raid district analysis after 5
	 * minutes. Fetches fresh data, updates (or posts) the message, and adds
	 * kickpoints only when the fresh data confirms the violations.
	 */
	private void handleRaidDistrictAnalysisDelayedVerification(String clanTag, Long messageId, String channelId,
			ListeningEvent event, String originalMessage, String raidEndTimeStr, int capitalPeakMax,
			int otherDistrictsMax, int penalizeBoth, boolean shouldAddKickpoints, int attempt) {

		int attemptNumber = attempt + 1;
		int totalAttempts = RAID_VERIFICATION_DELAYS_MINUTES.length;
		System.out.println("Starting raid district verification attempt " + attemptNumber + "/" + totalAttempts
				+ " for clan " + clanTag);

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

			// Still the raid that was analysed at fire time. An empty endTime means the
			// API answered with the "no data" fallback and cannot be trusted.
			boolean sameRaid = !endTimeStr.isEmpty()
					&& (raidEndTimeStr.isEmpty() || endTimeStr.equals(raidEndTimeStr));

			// The attack log is final once the raid is over. The API flips "state" to
			// "ended" only after its own refresh, which can lag well past the raid end,
			// so wall-clock time past the raid's own end time counts as well - no further
			// attacks can land at that point.
			Long raidEndMillis = parseRaidEndTime(endTimeStr);
			boolean raidIsOverByClock = raidEndMillis != null
					&& System.currentTimeMillis() >= raidEndMillis + RAID_END_GRACE_MS;
			boolean dataIsReliable = sameRaid && (state.equals("ended") || raidIsOverByClock);

			System.out.println("Raid district verification for clan " + clanTag + " (attempt " + attemptNumber + "/"
					+ totalAttempts + "): state='" + state + "', endTime='" + endTimeStr + "', expectedEndTime='"
					+ raidEndTimeStr + "', sameRaid=" + sameRaid + ", overByClock=" + raidIsOverByClock
					+ ", reliable=" + dataIsReliable);

			if (!dataIsReliable) {
				// Try again later - the API often catches up within the next few minutes
				if (attemptNumber < totalAttempts) {
					if (messageId != null) {
						editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
								"\n*Daten noch nicht bestätigt - weitere Überprüfung läuft*"));
					}
					scheduleRaidDistrictVerification(clanTag, messageId, channelId, event, originalMessage,
							raidEndTimeStr, capitalPeakMax, otherDistrictsMax, penalizeBoth, shouldAddKickpoints,
							attemptNumber);
					return;
				}

				// All attempts used up - fall back to the data from the raid end if the
				// event is configured to do so
				handleRaidDistrictVerificationGiveUp(clanTag, messageId, channelId, event, originalMessage,
						capitalPeakMax, otherDistrictsMax, penalizeBoth, shouldAddKickpoints);
				return;
			}

			RaidDistrictAnalysisResult result = buildRaidDistrictAnalysisResult(clan, capitalPeakMax,
					otherDistrictsMax, penalizeBoth, shouldAddKickpoints);
			if (result == null) {
				return;
			}

			String footer = "\n*Daten überprüft (" + attemptNumber + ". Versuch)*";
			if (messageId != null) {
				// Update the original message with verified data
				String updatedMessage = result.hasFails
						? truncateForDiscord(result.message, footer)
						: "## Raidfails - District-Analyse\n\nKeine Verstöße nach Überprüfung gefunden.\n" + footer;
				editMessageInChannel(channelId, messageId, updatedMessage);
			} else if (result.hasFails) {
				// Nothing was posted at fire time, but the fresh data shows violations
				sendMessageToChannel(truncateForDiscord(result.message, footer));
			}

			boolean shouldProcessKickpoints = shouldAddKickpoints && result.hasFails
					&& event.getActionType() == ACTIONTYPE.RAIDFAILS;
			if (shouldProcessKickpoints) {
				for (RaidDistrictFail fail : result.penalizedPlayers) {
					addKickpointForPlayer(fail.player, "Zu viele Angriffe auf " + fail.districtName + " ("
							+ fail.attacks + "/" + fail.threshold + ")");
				}
			}

			System.out.println("Completed raid district verification for clan " + clanTag + " on attempt "
					+ attemptNumber + " (dataReliable=true, kickpoints=" + shouldProcessKickpoints + ")");

		} catch (JSONException e) {
			System.err.println("Error in raid district delayed verification for clan " + clanTag + ": "
					+ e.getMessage());
			if (messageId != null) {
				try {
					editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
							"\n*Fehler bei der Überprüfung. Daten möglicherweise nicht aktuell.*"));
				} catch (Exception e2) {
					System.err.println("Failed to update message with error: " + e2.getMessage());
				}
			}
		}
	}

	/**
	 * Called when every verification attempt failed to confirm the raid data.
	 * Without the fallback setting nothing happens beyond a note on the message;
	 * with it enabled the kickpoints are handed out based on the analysis made at
	 * raid end.
	 */
	private void handleRaidDistrictVerificationGiveUp(String clanTag, Long messageId, String channelId,
			ListeningEvent event, String originalMessage, int capitalPeakMax, int otherDistrictsMax,
			int penalizeBoth, boolean shouldAddKickpoints) {

		boolean forceKickpoints = event.getFlag(SETTING_RAID_FORCE_KICKPOINTS, false);
		boolean shouldProcessKickpoints = forceKickpoints && shouldAddKickpoints
				&& event.getActionType() == ACTIONTYPE.RAIDFAILS;

		if (!shouldProcessKickpoints) {
			if (messageId != null) {
				editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
						"\n*Daten sind nicht zuverlässig - keine Kickpunkte vergeben*"));
			}
			System.out.println("Completed raid district verification for clan " + clanTag
					+ " (dataReliable=false, kickpoints=false)");
			return;
		}

		// Re-run the analysis on the data as it was at raid end. A fresh Clan instance
		// would fetch the same unverifiable data, so the fire-time result is rebuilt
		// from the already posted message instead.
		Clan clan = new Clan(clanTag);
		RaidDistrictAnalysisResult result = buildRaidDistrictAnalysisResult(clan, capitalPeakMax, otherDistrictsMax,
				penalizeBoth, shouldAddKickpoints);

		if (result == null || !result.hasFails) {
			if (messageId != null) {
				editMessageInChannel(channelId, messageId, truncateForDiscord(originalMessage,
						"\n*Daten sind nicht zuverlässig - keine Kickpunkte vergeben*"));
			}
			System.out.println("Completed raid district verification for clan " + clanTag
					+ " (dataReliable=false, fallback active but no fails found)");
			return;
		}

		if (messageId != null) {
			editMessageInChannel(channelId, messageId, truncateForDiscord(result.message,
					"\n*Daten konnten nicht bestätigt werden - Kickpunkte auf Basis der Daten bei Raid-Ende vergeben (Fallback aktiv)*"));
		}

		for (RaidDistrictFail fail : result.penalizedPlayers) {
			addKickpointForPlayer(fail.player,
					"Zu viele Angriffe auf " + fail.districtName + " (" + fail.attacks + "/" + fail.threshold + ")");
		}

		System.out.println("Completed raid district verification for clan " + clanTag
				+ " (dataReliable=false, kickpoints=true via fallback)");
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
	 * Pick a player from List A and move him to List B. If nobody in List A can
	 * donate for this range, List B is merged back into List A once to start a new
	 * cycle. Returns null if no eligible donator exists at all.
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

			// Build a list of eligible players from warMemberList that are in List A
			ArrayList<Player> eligiblePlayers = collectEligibleFromListForEvent(warMemberList, listA, map,
					excludeLeaders);

			// Nobody in List A can donate for this range (list empty, everybody in the
			// range itself, opted out, signed off, ...) => merge List B back into List A
			// and start a new cycle. This happens at most once per pick, so it can never
			// loop, no matter how many ineligible tags are stuck in List A.
			if (eligiblePlayers.isEmpty() && !listB.isEmpty()) {
				listA.addAll(listB);
				listB.clear();
				eligiblePlayers = collectEligibleFromListForEvent(warMemberList, listA, map, excludeLeaders);
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
			System.err.println("Error picking player from List A for event: " + e.getMessage());
			// Fallback: ignore the lists and pick a random eligible player
			return pickRandomEligibleForEvent(warMemberList, map, excludeLeaders);
		}
	}

	/**
	 * All war members that are still in List A and may donate for the given range
	 */
	private ArrayList<Player> collectEligibleFromListForEvent(ArrayList<Player> warMemberList, ArrayList<String> listA,
			lostmanager.util.Tuple<Integer, Integer> map, boolean excludeLeaders) {
		ArrayList<Player> eligiblePlayers = new ArrayList<>();
		for (Player p : warMemberList) {
			if (listA.contains(p.getTag()) && isEligibleDonatorForEvent(p, map, excludeLeaders)) {
				eligiblePlayers.add(p);
			}
		}
		return eligiblePlayers;
	}

	/**
	 * Pick a random eligible donator without using the A/B lists. Returns null if
	 * nobody is eligible.
	 */
	private Player pickRandomEligibleForEvent(ArrayList<Player> warMemberList,
			lostmanager.util.Tuple<Integer, Integer> map, boolean excludeLeaders) {
		ArrayList<Player> candidates = new ArrayList<>(warMemberList);
		Collections.shuffle(candidates);
		for (Player p : candidates) {
			if (isEligibleDonatorForEvent(p, map, excludeLeaders)) {
				return p;
			}
		}
		return null;
	}

	/**
	 * A player may donate for a range if he is not in that range himself, did not
	 * opt out of the war and - if requested - is no (co-)leader
	 */
	private boolean isEligibleDonatorForEvent(Player player, lostmanager.util.Tuple<Integer, Integer> map,
			boolean excludeLeaders) {
		int mapposition = player.getWarMapPosition();
		if (mapposition >= map.getFirst() && mapposition <= map.getSecond()) {
			return false; // would donate for himself
		}
		if (!player.getWarPreference()) {
			return false; // opted out of the war
		}
		return !(excludeLeaders && isLeaderOrCoLeaderForEvent(player));
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
		/** Covered by the free-hits budget, so reported but not punished. */
		boolean free;

		PlayerBadAttack(Player player, int stars, int attackIndex, int destructionPct) {
			this.player = player;
			this.stars = stars;
			this.attackIndex = attackIndex;
			this.destructionPct = destructionPct;
		}
	}

	/** Number of free bad attacks configured for this event (0 = punish all). */
	private int getConfiguredFreeHits() {
		Long value = getSetting(SETTING_STARFAILS_FREE_HITS, 0L);
		return value != null && value > 0 ? value.intValue() : 0;
	}

	/**
	 * Counts, per player tag, how many bad attacks were already made in the rounds
	 * of this CWL before {@code beforeRound}.
	 *
	 * The count is recomputed from the API rather than stored, so it stays correct
	 * even if an event was missed or runs twice - there is no counter that could
	 * drift out of sync with reality.
	 */
	private java.util.Map<String, Integer> countPreviousCWLBadAttacks(Clan clan, int beforeRound, int targetStars) {
		java.util.Map<String, Integer> counts = new java.util.HashMap<>();
		if (beforeRound <= 0) {
			return counts;
		}

		try {
			org.json.JSONObject cwlJson = clan.getCWLJson();
			if (!cwlJson.has("rounds") || cwlJson.isNull("rounds")) {
				return counts;
			}
			org.json.JSONArray rounds = cwlJson.getJSONArray("rounds");

			for (int r = 0; r < beforeRound && r < rounds.length(); r++) {
				org.json.JSONArray warTags = rounds.getJSONObject(r).getJSONArray("warTags");

				for (int w = 0; w < warTags.length(); w++) {
					String warTag = warTags.getString(w);
					if (warTag.equals("#0")) {
						continue;
					}

					try {
						org.json.JSONObject warData = Clan.getCWLDayJson(warTag);
						if (warData == null || !warData.has("clan") || !warData.has("opponent")) {
							continue;
						}

						org.json.JSONObject clanData = warData.getJSONObject("clan");
						org.json.JSONObject opponentData = warData.getJSONObject("opponent");
						org.json.JSONObject ourData;
						if (clanData.getString("tag").equals(clan.getTag())) {
							ourData = clanData;
						} else if (opponentData.getString("tag").equals(clan.getTag())) {
							ourData = opponentData;
						} else {
							continue; // Not our war
						}

						org.json.JSONArray members = ourData.getJSONArray("members");
						for (int i = 0; i < members.length(); i++) {
							org.json.JSONObject member = members.getJSONObject(i);
							if (!member.has("attacks")) {
								continue;
							}
							org.json.JSONArray attacks = member.getJSONArray("attacks");
							for (int a = 0; a < attacks.length(); a++) {
								if (attacks.getJSONObject(a).optInt("stars", 0) == targetStars) {
									String tag = member.getString("tag");
									counts.put(tag, counts.getOrDefault(tag, 0) + 1);
								}
							}
						}

						break; // Our war for this round was found, no need to check the rest
					} catch (JSONException e) {
						// Skip war tags whose data cannot be loaded
					}
				}
			}
		} catch (JSONException e) {
			System.err.println("Error counting previous CWL bad attacks for clan " + clan.getTag() + ": "
					+ e.getMessage());
		}

		return counts;
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

		int freeHits = getConfiguredFreeHits();

		StringBuilder message = new StringBuilder();
		message.append("## ").append(clan.getNameAPI())
				.append(" Clankrieg – Schlechte Angriffe (").append(targetStars).append(" ★)\n");
		if (freeHits > 0) {
			message.append("-# Freie Fehlversuche pro Spieler in diesem Krieg: ").append(freeHits).append("\n");
		}

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
						// Signed-off members cannot receive kickpoints anyway
						// (see addKickpointForPlayer), so they are left out of the
						// report entirely instead of being listed without consequence.
						if (p.isHiddenColeader() || MemberSignoff.isSignedOff(tag)) {
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

			// The free hits are used up by the earliest bad attacks of this war
			for (int b = 0; b < memberBad.size(); b++) {
				memberBad.get(b).free = b < freeHits;
			}

			hasBadAttacks = true;
			for (PlayerBadAttack pba : memberBad) {
				message.append("- ").append(name).append(" (").append(tag).append(")")
						.append(" – Angriff ").append(pba.attackIndex).append(": ")
						.append(pba.stars).append(" ★ (").append(pba.destructionPct).append("%)");
				if (pba.free) {
					message.append(" – *frei*");
				}
				message.append("\n");
			}

			// Only attacks beyond the free budget can be punished
			ArrayList<PlayerBadAttack> punishable = new ArrayList<>();
			for (PlayerBadAttack pba : memberBad) {
				if (!pba.free) punishable.add(pba);
			}
			if (punishable.isEmpty()) continue;

			// Determine which attacks to punish based on mode
			if (mode == 1) {
				// Once per player – only the first punishable bad attack
				punishableAttacks.add(punishable.get(0));
			} else {
				// Mode 2 or 3 – each bad attack (mode 3 already filtered above)
				punishableAttacks.addAll(punishable);
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

		int freeHits = getConfiguredFreeHits();
		// The budget spans the whole CWL, so the bad attacks of the previous rounds
		// decide how much of it is left today
		java.util.Map<String, Integer> previousBadAttacks = freeHits > 0
				? countPreviousCWLBadAttacks(clan, roundNumber, targetStars)
				: java.util.Collections.emptyMap();

		StringBuilder message = new StringBuilder();
		message.append("## CWL Day ").append(roundNumber + 1)
				.append(" – Schlechte Angriffe (").append(targetStars).append(" ★)\n");
		if (freeHits > 0) {
			message.append("-# Freie Fehlversuche pro Spieler in dieser CWL: ").append(freeHits).append("\n");
		}

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
			// Signed-off members cannot receive kickpoints anyway
			// (see addKickpointForPlayer), so they are left out of the report entirely
			// instead of being listed without consequence.
			if (p.isHiddenColeader() || MemberSignoff.isSignedOff(tag)) continue;

			// Today's bad attack is the (previous + 1)-th of this CWL, so it is still
			// covered as long as the earlier ones have not used up the budget
			int previousCount = previousBadAttacks.getOrDefault(tag, 0);
			boolean isFree = previousCount < freeHits;

			hasBadAttacks = true;
			message.append("- ").append(name).append(" (").append(tag).append(")")
					.append(" – ").append(stars).append(" ★ (")
					.append(attack.optInt("destructionPercentage", 0)).append("%)");
			if (isFree) {
				message.append(" – *frei* (").append(previousCount + 1).append("./").append(freeHits).append(")");
			}
			message.append("\n");

			if (isFree) {
				continue; // Reported, but within the free budget
			}

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
