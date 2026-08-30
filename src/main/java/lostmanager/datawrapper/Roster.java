package lostmanager.datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import lostmanager.dbutil.Connection;
import lostmanager.dbutil.DBUtil;

public class Roster {

    private final String name;
    private final String clan;
    private final int minTh;
    private final Timestamp deleteAt;
    private final boolean onlySignoff;
    private final boolean isClosed;

    public Roster(String name, String clan, int minTh, Timestamp deleteAt, boolean onlySignoff, boolean isClosed) {
        this.name = name;
        this.clan = clan;
        this.minTh = minTh;
        this.deleteAt = deleteAt;
        this.onlySignoff = onlySignoff;
        this.isClosed = isClosed;
    }

    public String getName() { return name; }
    public String getClan() { return clan; }
    public int getMinTh() { return minTh; }
    public Timestamp getDeleteAt() { return deleteAt; }
    public boolean isOnlySignoff() { return onlySignoff; }
    public boolean isClosed() { return isClosed; }

    public static Roster getRoster(String name) {
        try {
            PreparedStatement pstmt = Connection.getConnection().prepareStatement("SELECT * FROM rosters WHERE name = ?");
            pstmt.setString(1, name);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Roster(
                    rs.getString("name"),
                    rs.getString("clan"),
                    rs.getInt("min_th"),
                    rs.getTimestamp("delete_at"),
                    rs.getBoolean("only_signoff"),
                    rs.getBoolean("is_closed")
                );
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return null;
    }

    public static List<Roster> getAllRosters() {
        List<Roster> result = new ArrayList<>();
        try {
            PreparedStatement pstmt = Connection.getConnection().prepareStatement("SELECT * FROM rosters");
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                result.add(new Roster(
                    rs.getString("name"),
                    rs.getString("clan"),
                    rs.getInt("min_th"),
                    rs.getTimestamp("delete_at"),
                    rs.getBoolean("only_signoff"),
                    rs.getBoolean("is_closed")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
        }
        return result;
    }

    public static void createRoster(String name, String clan, int minTh, Timestamp deleteAt, boolean onlySignoff) {
        DBUtil.executeUpdate("INSERT INTO rosters (name, clan, min_th, delete_at, only_signoff, is_closed) VALUES (?, ?, ?, ?, ?, ?)",
            name, clan, minTh, deleteAt, onlySignoff, false);
    }

    public static void deleteRoster(String name) {
        DBUtil.executeUpdate("DELETE FROM rosters WHERE name = ?", name);
    }

    public static void setClosed(String name, boolean closed) {
        DBUtil.executeUpdate("UPDATE rosters SET is_closed = ? WHERE name = ?", closed, name);
    }

    public static void setMinTh(String name, int minTh) {
        DBUtil.executeUpdate("UPDATE rosters SET min_th = ? WHERE name = ?", minTh, name);
    }

    // ------------------------------------------------------------------
    // Mehrere Clans je Roster
    //
    // Ursprünglich kannte ein Roster genau einen Clan. Der F2P-Verbund meldet
    // sich aber über zwei Clans gemeinsam an, und das ist nichts CWL-Eigenes -
    // deshalb liegt es hier und nicht in den f2pcwl-Klassen.
    //
    // rosters.clan bleibt als erster Clan und Anzeigewert erhalten. Steht in
    // roster_clans nichts, gilt er allein: Bestandsroster verhalten sich damit
    // unverändert.
    // ------------------------------------------------------------------

    /** Alle Clans dieses Rosters; niemals leer, solange rosters.clan gesetzt ist. */
    public static List<String> getClans(String rosterName) {
        List<String> tags = DBUtil.getArrayListFromSQL(
                "SELECT clan_tag FROM roster_clans WHERE roster_name = ? ORDER BY clan_tag",
                String.class, rosterName);
        if (tags != null && !tags.isEmpty()) {
            return tags;
        }
        Roster roster = getRoster(rosterName);
        List<String> fallback = new ArrayList<>();
        if (roster != null && roster.getClan() != null && !roster.getClan().isBlank()) {
            fallback.add(roster.getClan());
        }
        return fallback;
    }

    public static void addClan(String rosterName, String clanTag) {
        DBUtil.executeUpdate("INSERT INTO roster_clans (roster_name, clan_tag) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", rosterName, clanTag);
    }

    public static void removeClan(String rosterName, String clanTag) {
        DBUtil.executeUpdate("DELETE FROM roster_clans WHERE roster_name = ? AND clan_tag = ?",
                rosterName, clanTag);
    }
}
