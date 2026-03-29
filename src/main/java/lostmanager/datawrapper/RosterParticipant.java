package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

public class RosterParticipant {

    private final String rosterName;
    private final String discordId;
    private final String accountTag;
    private final String status; // "signup" or "signoff"
    private final int thLevel;
    
    // extra info fetched dynamically
    private String accountName = "Unknown";

    public RosterParticipant(String rosterName, String discordId, String accountTag, String status, int thLevel) {
        this.rosterName = rosterName;
        this.discordId = discordId;
        this.accountTag = accountTag;
        this.status = status;
        this.thLevel = thLevel;
    }
    
    public void setAccountName(String name) { this.accountName = name; }
    public String getAccountName() { return accountName; }

    public String getRosterName() { return rosterName; }
    public String getDiscordId() { return discordId; }
    public String getAccountTag() { return accountTag; }
    public String getStatus() { return status; }
    public int getThLevel() { return thLevel; }

    public static List<RosterParticipant> getParticipants(String rosterName) {
        List<RosterParticipant> result = new ArrayList<>();
        try {
            // Join with players table to get the account name
            PreparedStatement pstmt = Connection.getConnection().prepareStatement(
                "SELECT rp.*, p.name as player_name FROM roster_participants rp " +
                "LEFT JOIN players p ON rp.account_tag = p.cr_tag " +
                "WHERE rp.roster_name = ?"
            );
            pstmt.setString(1, rosterName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                RosterParticipant rp = new RosterParticipant(
                    rs.getString("roster_name"),
                    rs.getString("discord_id"),
                    rs.getString("account_tag"),
                    rs.getString("status"),
                    rs.getInt("th_level")
                );
                String pName = rs.getString("player_name");
                if (pName != null && !pName.isEmpty()) {
                    rp.setAccountName(pName);
                } else {
                    rp.setAccountName(rp.getAccountTag());
                }
                result.add(rp);
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return result;
    }

    public static void setParticipantStatus(String rosterName, String discordId, String accountTag, String status, int thLevel) {
        // Upsert logic (insert or update)
        DBUtil.executeUpdate(
            "INSERT INTO roster_participants (roster_name, discord_id, account_tag, status, th_level) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON CONFLICT (roster_name, account_tag) DO UPDATE SET status = EXCLUDED.status, th_level = EXCLUDED.th_level",
            rosterName, discordId, accountTag, status, thLevel
        );
    }
}
