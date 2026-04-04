package lostmanager.datawrapper;

import java.util.ArrayList;

import lostmanager.dbutil.DBUtil;

public class GiveawayEntry {

    /**
     * Add an entry for a user to a giveaway.
     * @return true if the entry was added, false if the user already entered (duplicate PK).
     */
    public static boolean addEntry(long giveawayId, String discordId) {
        var result = DBUtil.executeUpdate(
            "INSERT INTO giveaway_entries (giveaway_id, discord_id) VALUES (?, ?)",
            giveawayId, discordId
        );
        return result != null && result.getSecond() != null && result.getSecond() > 0;
    }

    /**
     * Check if a user has already entered a giveaway.
     */
    public static boolean hasEntry(long giveawayId, String discordId) {
        Long count = DBUtil.getValueFromSQL(
            "SELECT COUNT(*) FROM giveaway_entries WHERE giveaway_id = ? AND discord_id = ?",
            Long.class, giveawayId, discordId
        );
        return count != null && count > 0;
    }

    /**
     * Get all discord IDs that entered a giveaway.
     */
    public static ArrayList<String> getEntries(long giveawayId) {
        return DBUtil.getArrayListFromSQL(
            "SELECT discord_id FROM giveaway_entries WHERE giveaway_id = ?",
            String.class, giveawayId
        );
    }
}
