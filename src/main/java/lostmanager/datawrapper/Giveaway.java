package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

public class Giveaway {

    private final long id;
    private final String prize;
    private final int winnerCount;
    private final String hostDiscordId;
    private final String channelId;
    private String messageId;
    private final Timestamp endTime;
    private boolean isEnded;
    private String winners;
    private final Timestamp createdAt;

    public Giveaway(long id, String prize, int winnerCount, String hostDiscordId,
                    String channelId, String messageId, Timestamp endTime,
                    boolean isEnded, String winners, Timestamp createdAt) {
        this.id = id;
        this.prize = prize;
        this.winnerCount = winnerCount;
        this.hostDiscordId = hostDiscordId;
        this.channelId = channelId;
        this.messageId = messageId;
        this.endTime = endTime;
        this.isEnded = isEnded;
        this.winners = winners;
        this.createdAt = createdAt;
    }

    // Getters
    public long getId() { return id; }
    public String getPrize() { return prize; }
    public int getWinnerCount() { return winnerCount; }
    public String getHostDiscordId() { return hostDiscordId; }
    public String getChannelId() { return channelId; }
    public String getMessageId() { return messageId; }
    public Timestamp getEndTime() { return endTime; }
    public boolean isEnded() { return isEnded; }
    public String getWinners() { return winners; }
    public Timestamp getCreatedAt() { return createdAt; }

    // --- Static factory methods ---

    private static Giveaway fromResultSet(ResultSet rs) throws SQLException {
        return new Giveaway(
            rs.getLong("id"),
            rs.getString("prize"),
            rs.getInt("winner_count"),
            rs.getString("host_discord_id"),
            rs.getString("channel_id"),
            rs.getString("message_id"),
            rs.getTimestamp("end_time"),
            rs.getBoolean("is_ended"),
            rs.getString("winners"),
            rs.getTimestamp("created_at")
        );
    }

    /**
     * Load a giveaway by its ID.
     */
    public static Giveaway getById(long id) {
        try (PreparedStatement pstmt = Connection.getConnection()
                .prepareStatement("SELECT * FROM giveaways WHERE id = ?")) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return fromResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading giveaway " + id + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Create a new giveaway and return it with the generated ID.
     */
    public static Giveaway create(String prize, int winnerCount, String hostDiscordId,
                                   String channelId, Timestamp endTime) {
        var result = DBUtil.executeUpdate(
            "INSERT INTO giveaways (prize, winner_count, host_discord_id, channel_id, end_time, is_ended, created_at, message_id, winners) VALUES (?, ?, ?, ?, ?, false, NOW(), NULL, NULL)",
            prize, winnerCount, hostDiscordId, channelId, endTime
        );
        if (result != null) {
            if (result.getFirst() != null) {
                try (ResultSet rs = result.getFirst().getGeneratedKeys()) {
                    if (rs.next()) {
                        long id = rs.getLong(1);
                        return getById(id);
                    } else {
                        System.err.println("Giveaway.create: No generated keys returned.");
                    }
                } catch (SQLException e) {
                    System.err.println("Error getting generated giveaway ID: " + e.getMessage());
                }
            } else {
                System.err.println("Giveaway.create: result.getFirst() is null");
            }
        } else {
            System.err.println("Giveaway.create: DBUtil.executeUpdate returned null");
        }
        return null;
    }

    /**
     * Get all giveaways, optionally filtered by active/inactive status.
     * @param activeOnly null = all, true = active only, false = inactive only
     */
    public static List<Giveaway> getAll(Boolean activeOnly) {
        List<Giveaway> result = new ArrayList<>();
        String sql;
        Object[] params;

        if (activeOnly == null) {
            sql = "SELECT * FROM giveaways ORDER BY created_at DESC";
            params = new Object[]{};
        } else if (activeOnly) {
            sql = "SELECT * FROM giveaways WHERE is_ended = FALSE ORDER BY end_time ASC";
            params = new Object[]{};
        } else {
            sql = "SELECT * FROM giveaways WHERE is_ended = TRUE ORDER BY end_time DESC";
            params = new Object[]{};
        }

        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                pstmt.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    result.add(fromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error loading giveaways: " + e.getMessage());
        }
        return result;
    }

    /**
     * Get all giveaways that have passed their end time but haven't been ended yet.
     */
    public static List<Giveaway> getExpiredActive() {
        List<Giveaway> result = new ArrayList<>();
        String sql = "SELECT * FROM giveaways WHERE is_ended = FALSE AND end_time <= NOW()";
        try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                result.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error loading expired giveaways: " + e.getMessage());
        }
        return result;
    }

    // --- Instance mutators ---

    public void setMessageId(String messageId) {
        this.messageId = messageId;
        DBUtil.executeUpdate("UPDATE giveaways SET message_id = ? WHERE id = ?", messageId, id);
    }

    public void end(String winnersCSV) {
        this.isEnded = true;
        this.winners = winnersCSV;
        DBUtil.executeUpdate("UPDATE giveaways SET is_ended = TRUE, winners = ? WHERE id = ?", winnersCSV, id);
    }

    /**
     * Get all participant discord IDs for this giveaway.
     */
    public List<String> getEntryDiscordIds() {
        return GiveawayEntry.getEntries(id);
    }

    /**
     * Get participant count for this giveaway.
     */
    public int getEntryCount() {
        Long count = DBUtil.getValueFromSQL(
            "SELECT COUNT(*) FROM giveaway_entries WHERE giveaway_id = ?", Long.class, id);
        return count != null ? count.intValue() : 0;
    }
}
