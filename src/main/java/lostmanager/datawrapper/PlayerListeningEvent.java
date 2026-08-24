package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lostmanager.Bot;
import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/**
 * A watcher on a single player value.
 *
 * Unlike {@link ListeningEvent}, which fires at a point in time that can be
 * computed in advance, a player event fires when a value actually moved. The
 * poller in {@link lostmanager.util.PlayerEventPoller} reads the value from the
 * API, compares it against {@code last_value} and calls {@link #fireEvent} on a
 * difference, so the stored value is the whole state of the watcher and it
 * survives a restart without re-reporting changes that were already reported.
 *
 * The report is always a DM to the user the watcher belongs to. There is
 * deliberately no way to point it anywhere else: a configurable channel would
 * let any member turn the bot into a ping machine in a channel they cannot
 * normally post in, and a configurable DM target would do the same to another
 * person. The only address is the creator's own DMs.
 */
public class PlayerListeningEvent {

	/**
	 * A watchable value of the {@code /players/{tag}} API response.
	 *
	 * Each constant carries the JSON path it lives under, so adding another
	 * watchable stat means adding a constant here and nothing else. The path is
	 * dotted for nested values (e.g.
	 * {@code legendStatistics.currentSeason.trophies}).
	 */
	public enum LISTENINGTYPE {

		TROPHIES("trophies", "trophies", "Trophäen", "🏆");

		private final String dbValue;
		private final String jsonPath;
		private final String label;
		private final String emoji;

		LISTENINGTYPE(String dbValue, String jsonPath, String label, String emoji) {
			this.dbValue = dbValue;
			this.jsonPath = jsonPath;
			this.label = label;
			this.emoji = emoji;
		}

		public String getDbValue() {
			return dbValue;
		}

		public String getLabel() {
			return label;
		}

		public String getEmoji() {
			return emoji;
		}

		/**
		 * Reads this value out of a player API response.
		 *
		 * @return the current value, or {@code null} if the response does not carry it
		 *         (the API omits fields a player has no data for)
		 */
		public Long readValue(JSONObject playerJson) {
			if (playerJson == null) {
				return null;
			}
			JSONObject current = playerJson;
			String[] parts = jsonPath.split("\\.");
			for (int i = 0; i < parts.length - 1; i++) {
				current = current.optJSONObject(parts[i]);
				if (current == null) {
					return null;
				}
			}
			String leaf = parts[parts.length - 1];
			if (!current.has(leaf) || current.isNull(leaf)) {
				return null;
			}
			try {
				return current.getLong(leaf);
			} catch (final org.json.JSONException e) {
				System.err.println("Player event: value " + jsonPath + " is not a number: " + e.getMessage());
				return null;
			}
		}

		public static LISTENINGTYPE fromString(String value) {
			if (value == null) {
				return null;
			}
			for (LISTENINGTYPE type : values()) {
				if (type.dbValue.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
					return type;
				}
			}
			return null;
		}
	}

	public enum ACTIONTYPE {

		/** DM the owner of the watcher. The only action there is, on purpose. */
		DM("dm", "DM");

		private final String dbValue;
		private final String label;

		ACTIONTYPE(String dbValue, String label) {
			this.dbValue = dbValue;
			this.label = label;
		}

		public String getDbValue() {
			return dbValue;
		}

		public String getLabel() {
			return label;
		}

		public static ACTIONTYPE fromString(String value) {
			if (value == null) {
				return null;
			}
			for (ACTIONTYPE type : values()) {
				if (type.dbValue.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
					return type;
				}
			}
			return null;
		}
	}

	private static final String SELECT_COLUMNS = "id, player_tag, listeningtype, actiontype, user_id, "
			+ "actionvalues, last_value, last_checked, created_at";

	private final long id;
	private final String playerTag;
	private final LISTENINGTYPE listeningType;
	private final ACTIONTYPE actionType;
	private final String userId;
	private final ArrayList<ActionValue> actionValues;
	private final Long lastValue;
	private final Timestamp lastChecked;
	private final Timestamp createdAt;

	private PlayerListeningEvent(long id, String playerTag, LISTENINGTYPE listeningType, ACTIONTYPE actionType,
			String userId, ArrayList<ActionValue> actionValues, Long lastValue, Timestamp lastChecked,
			Timestamp createdAt) {
		this.id = id;
		this.playerTag = playerTag;
		this.listeningType = listeningType;
		this.actionType = actionType;
		this.userId = userId;
		this.actionValues = actionValues;
		this.lastValue = lastValue;
		this.lastChecked = lastChecked;
		this.createdAt = createdAt;
	}

	public long getId() {
		return id;
	}

	public String getPlayerTag() {
		return playerTag;
	}

	public LISTENINGTYPE getListeningType() {
		return listeningType;
	}

	public ACTIONTYPE getActionType() {
		return actionType;
	}

	/** The Discord user this watcher belongs to and whose DMs it reports to. */
	public String getUserID() {
		return userId;
	}

	public ArrayList<ActionValue> getActionValues() {
		return actionValues;
	}

	/**
	 * @return the last observed value, or {@code null} while no baseline was taken
	 *         yet
	 */
	public Long getLastValue() {
		return lastValue;
	}

	public Timestamp getLastChecked() {
		return lastChecked;
	}

	public Timestamp getCreatedAt() {
		return createdAt;
	}

	// ============================================================
	// Loading
	// ============================================================

	private static PlayerListeningEvent fromResultSet(ResultSet rs) throws SQLException {
		ArrayList<ActionValue> values = new ArrayList<>();
		String actionValuesJson = rs.getString("actionvalues");
		if (actionValuesJson != null && !actionValuesJson.isBlank()) {
			try {
				values = new ObjectMapper().readValue(actionValuesJson, new TypeReference<ArrayList<ActionValue>>() {
				});
			} catch (final JsonProcessingException e) {
				System.err.println("Error parsing actionvalues JSON for player event " + rs.getLong("id") + ": "
						+ e.getMessage());
			}
		}

		long rawLastValue = rs.getLong("last_value");
		Long lastValue = rs.wasNull() ? null : rawLastValue;

		return new PlayerListeningEvent(
				rs.getLong("id"),
				rs.getString("player_tag"),
				LISTENINGTYPE.fromString(rs.getString("listeningtype")),
				ACTIONTYPE.fromString(rs.getString("actiontype")),
				rs.getString("user_id"),
				values,
				lastValue,
				rs.getTimestamp("last_checked"),
				rs.getTimestamp("created_at"));
	}

	private static ArrayList<PlayerListeningEvent> query(String sql, Object... params) {
		ArrayList<PlayerListeningEvent> events = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					events.add(fromResultSet(rs));
				}
			}
		} catch (final SQLException e) {
			System.err.println("Failed to load player listening events: " + e.getMessage());
		}
		return events;
	}

	/**
	 * Loads every configured watcher in a single query. The poller walks all of
	 * them on every tick, so they are read as full rows instead of one query per
	 * field.
	 */
	public static ArrayList<PlayerListeningEvent> getAll() {
		return query("SELECT " + SELECT_COLUMNS + " FROM player_listening_events ORDER BY id ASC");
	}

	public static ArrayList<PlayerListeningEvent> getForPlayer(String playerTag) {
		return query("SELECT " + SELECT_COLUMNS + " FROM player_listening_events WHERE player_tag = ? ORDER BY id ASC",
				playerTag);
	}

	public static ArrayList<PlayerListeningEvent> getForUser(String userId) {
		return query("SELECT " + SELECT_COLUMNS + " FROM player_listening_events WHERE user_id = ? ORDER BY id ASC",
				userId);
	}

	public static PlayerListeningEvent getById(long id) {
		ArrayList<PlayerListeningEvent> found = query(
				"SELECT " + SELECT_COLUMNS + " FROM player_listening_events WHERE id = ?", id);
		return found.isEmpty() ? null : found.get(0);
	}

	// ============================================================
	// Writing
	// ============================================================

	/**
	 * Creates a watcher.
	 *
	 * @param userId   the Discord user the watcher belongs to; the reports go to
	 *                 their DMs and nowhere else
	 * @param baseline the value observed at creation time, or {@code null} to let
	 *                 the first poll take the baseline. Passing it in means the
	 *                 watcher does not report everything that happened between
	 *                 creation and the first poll as one jump.
	 * @return the new event id, or {@code null} if the insert failed
	 */
	public static Long create(String playerTag, LISTENINGTYPE listeningType, ACTIONTYPE actionType, String userId,
			ArrayList<ActionValue> actionValues, Long baseline) {
		String actionValuesJson = "[]";
		if (actionValues != null && !actionValues.isEmpty()) {
			try {
				actionValuesJson = new ObjectMapper().writeValueAsString(actionValues);
			} catch (final JsonProcessingException e) {
				System.err.println("Failed to serialize actionvalues for player event: " + e.getMessage());
			}
		}

		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO player_listening_events (player_tag, listeningtype, actiontype, user_id, "
						+ "actionvalues, last_value, last_checked) "
						+ "VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)",
				playerTag, listeningType.getDbValue(), actionType.getDbValue(), userId, actionValuesJson, baseline,
				baseline == null ? null : new Timestamp(System.currentTimeMillis()));

		if (result == null || result.getSecond() == null || result.getSecond() == 0) {
			return null;
		}
		return result.getFirst();
	}

	public static boolean delete(long id) {
		Tuple<Long, Integer> result = DBUtil.executeUpdate("DELETE FROM player_listening_events WHERE id = ?", id);
		return result != null && result.getSecond() != null && result.getSecond() > 0;
	}

	/**
	 * Stores the value the watcher was last measured at. Called after every poll,
	 * also when nothing changed, so {@code last_checked} shows whether the watcher
	 * is actually running.
	 */
	public boolean storeObservation(long value) {
		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"UPDATE player_listening_events SET last_value = ?, last_checked = ? WHERE id = ?",
				value, new Timestamp(System.currentTimeMillis()), id);
		return result != null && result.getSecond() != null && result.getSecond() > 0;
	}

	// ============================================================
	// Firing
	// ============================================================

	/**
	 * Reports a change to the owner's DMs.
	 *
	 * @param oldValue the previously stored value
	 * @param newValue the value just read from the API
	 * @param player   the player the watcher belongs to, used for the display name
	 * @return true if the DM was delivered
	 */
	public boolean fireEvent(long oldValue, long newValue, Player player) {
		if (listeningType == null) {
			System.err.println("Player event " + id + " has an unknown listeningtype, skipping");
			return false;
		}
		if (actionType != ACTIONTYPE.DM) {
			System.err.println("Player event " + id + " has an unsupported actiontype, skipping");
			return false;
		}

		long delta = newValue - oldValue;
		String description = "**" + MessageUtil.unformat(displayName(player)) + "**\n"
				+ formatNumber(oldValue) + " → **" + formatNumber(newValue) + "** ("
				+ (delta > 0 ? "+" : "") + formatNumber(delta) + ")";

		MessageEmbed embed = MessageUtil.buildEmbed(
				listeningType.getEmoji() + " " + listeningType.getLabel() + " geändert",
				description,
				delta > 0 ? MessageUtil.EmbedType.SUCCESS : MessageUtil.EmbedType.WARNING,
				"Player Event #" + id);

		return sendDirectMessage(userId, embed);
	}

	/**
	 * Sends an embed to a user's DMs.
	 *
	 * Blocking, so the caller learns whether it actually arrived - a user with DMs
	 * closed would otherwise make the whole watcher fail silently. Only ever call
	 * this from the poll loop or a command thread, never from a JDA callback
	 * thread.
	 *
	 * @return true if the DM was delivered
	 */
	public static boolean sendDirectMessage(String userId, MessageEmbed embed) {
		JDA jda = Bot.getJda();
		if (jda == null || userId == null) {
			return false;
		}
		try {
			net.dv8tion.jda.api.entities.User discordUser = jda.retrieveUserById(userId).complete();
			discordUser.openPrivateChannel().complete().sendMessageEmbeds(embed).complete();
			return true;
		} catch (final ErrorResponseException e) {
			if (e.getErrorResponse() == ErrorResponse.CANNOT_SEND_TO_USER) {
				System.err.println("Player event: user " + userId + " does not accept DMs from the bot");
			} else {
				System.err.println("Player event: could not DM " + userId + ": " + e.getMessage());
			}
			return false;
		} catch (final Exception e) {
			System.err.println("Player event: could not DM " + userId + ": " + e.getMessage());
			return false;
		}
	}

	/**
	 * Name from the database, falling back to the bare tag - the poll loop must not
	 * spend an extra API request on a display name.
	 */
	private String displayName(Player player) {
		try {
			String dbName = player.getNameDB();
			if (dbName != null && !dbName.isBlank()) {
				return dbName + " (" + playerTag + ")";
			}
		} catch (final Exception e) {
			// fall through to the bare tag
		}
		return playerTag;
	}

	public static String formatNumber(long value) {
		return String.format(java.util.Locale.GERMANY, "%,d", value);
	}
}
