package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

import lostmanager.dbutil.DBUtil;

public class MemberSignoff {
    private Long id;
    private String playerTag;
    private Timestamp startDate;
    private Timestamp endDate; // null = unlimited
    private String reason;
    private String createdByDiscordId;
    private Timestamp createdAt;

    public MemberSignoff(String playerTag) {
        this.playerTag = playerTag;
        loadFromDB();
    }

    private void loadFromDB() {
        String sql = "SELECT id, start_date, end_date, reason, created_by_discord_id, created_at FROM member_signoffs WHERE player_tag = ?";
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
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean exists() {
        return id != null;
    }

    public boolean isActive() {
        if (!exists()) {
            return false;
        }
        // If end_date is null, it's unlimited/permanent
        if (endDate == null) {
            return true;
        }
        // Otherwise check if current time is before end date
        return Timestamp.from(Instant.now()).before(endDate);
    }

    /**
     * Static method to check if a player is signed off without creating a full instance.
     * More efficient for quick checks.
     */
    public static boolean isSignedOff(String playerTag) {
        // Query database directly without loading full object
        String sql = "SELECT COUNT(*) FROM member_signoffs WHERE player_tag = ? AND (end_date IS NULL OR end_date > NOW())";
        Integer count = DBUtil.getValueFromSQL(sql, Integer.class, playerTag);
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

    public boolean isUnlimited() {
        return exists() && endDate == null;
    }

    public static boolean create(String playerTag, Timestamp endDate, String reason, String createdByDiscordId) {
        String sql = "INSERT INTO member_signoffs (player_tag, end_date, reason, created_by_discord_id) VALUES (?, ?, ?, ?)";
        return DBUtil.executeUpdate(sql, playerTag, endDate, reason, createdByDiscordId) != null;
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
