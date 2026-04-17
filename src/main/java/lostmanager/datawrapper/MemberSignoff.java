package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import lostmanager.dbutil.DBUtil;

public class MemberSignoff {
    private Long id;
    private final String playerTag;
    private Timestamp startDate;
    private Timestamp endDate; // null = unlimited
    private String reason;
    private String createdByDiscordId;
    private Timestamp createdAt;

    private boolean receivePings;

    public MemberSignoff(String playerTag) {
        this.playerTag = playerTag;
        loadFromDB();
    }

    private void loadFromDB() {
        String sql = "SELECT id, start_date, end_date, reason, created_by_discord_id, created_at, receive_pings FROM member_signoffs WHERE player_tag = ?";
        try (PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection().prepareStatement(sql)) {
            pstmt.setString(1, playerTag);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    this.id = rs.getLong("id");
                    this.startDate = rs.getTimestamp("start_date");
                    this.endDate = rs.getTimestamp("end_date");
                    this.reason = rs.getString("reason");
                    this.createdByDiscordId = rs.getString("created_by_discord_id");
                    this.createdAt = rs.getTimestamp("created_at");
                    this.receivePings = rs.getBoolean("receive_pings");
                }
            }
        } catch (final SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public boolean exists() {
        return id != null;
    }

    public boolean isActive() {
        if (!exists()) {
            return false;
        }
        
        Timestamp now = Timestamp.from(Instant.now());
        // Must have started already
        if (startDate != null && startDate.after(now)) {
            return false;
        }

        // If end_date is null, it's unlimited/permanent
        if (endDate == null) {
            return true;
        }
        // Otherwise check if current time is before end date
        return now.before(endDate);
    }

    /**
     * Static method to check if a player is signed off without creating a full
     * instance.
     * More efficient for quick checks.
     */
    public static boolean isSignedOff(String playerTag) {
        // Query database directly without loading full object
        String sql = "SELECT COUNT(*) FROM member_signoffs WHERE player_tag = ? AND start_date <= NOW() AND (end_date IS NULL OR end_date > NOW())";
        Long count = DBUtil.getValueFromSQL(sql, Long.class, playerTag);
        return count != null && count > 0;
    }

    public Long getId() {
        return id;
    }

    public String getPlayerTag() {
        return playerTag;
    }

    public Timestamp getStartDate() {
        return startDate;
    }

    public Timestamp getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public String getCreatedByDiscordId() {
        return createdByDiscordId;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isReceivePings() {
        return receivePings;
    }

    public boolean isUnlimited() {
        return exists() && endDate == null;
    }

    public static boolean create(String playerTag, Timestamp startDate, Timestamp endDate, String reason, String createdByDiscordId,
            boolean receivePings) {
        String sql = "INSERT INTO member_signoffs (player_tag, start_date, end_date, reason, created_by_discord_id, receive_pings) "
            + "VALUES (?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (player_tag) DO UPDATE SET "
            + "start_date = EXCLUDED.start_date, "
            + "end_date = EXCLUDED.end_date, "
            + "reason = EXCLUDED.reason, "
            + "created_by_discord_id = EXCLUDED.created_by_discord_id, "
            + "receive_pings = EXCLUDED.receive_pings, "
            + "created_at = NOW()";
        return DBUtil.executeUpdate(sql, playerTag, startDate, endDate, reason, createdByDiscordId, receivePings) != null;
    }

    public static boolean remove(String playerTag) {
        String sql = "DELETE FROM member_signoffs WHERE player_tag = ?";
        return DBUtil.executeUpdate(sql, playerTag) != null;
    }

    public boolean update(Timestamp newEndDate) {
        if (!exists()) {
            return false;
        }
        String sql = "UPDATE member_signoffs SET end_date = ? WHERE player_tag = ?";
        boolean result = DBUtil.executeUpdate(sql, newEndDate, playerTag) != null;
        if (result) {
            this.endDate = newEndDate;
        }
        return result;
    }
}
