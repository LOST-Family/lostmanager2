package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.User;

/**
 * Data Transfer Object for Player in REST API
 */
public class PlayerDTO {
    
    @JsonProperty("tag")
    private String tag;
    
    @JsonProperty("nameDB")
    private String nameDB;
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("roleInClan")
    private String roleInClan;
    
    @JsonProperty("clanDB")
    private ClanDTO clanDB;
    
    public PlayerDTO() {
        // Default constructor for Jackson
    }
    
    /**
     * Create PlayerDTO from Player object
     * @param player The player to convert
     */
    public PlayerDTO(Player player) {
        this.tag = player.getTag();
        
        // Get nameDB
        try {
            this.nameDB = player.getNameDB();
        } catch (Exception e) {
            this.nameDB = null;
        }
        
        // Get user ID if player is linked
        User user = player.getUser();
        this.userId = user != null ? user.getUserID() : null;
        
        // Get role in DB clan (not checking admin status)
        Player.RoleType roleDB = player.getRoleDB();
        if (roleDB != null) {
            this.roleInClan = roleDB.name();
        } else {
            this.roleInClan = "NOTINCLAN";
        }
        
        // Get clan from DB
        Clan clanDbObj = player.getClanDB();
        this.clanDB = clanDbObj != null ? new ClanDTO(clanDbObj) : null;
    }
    
    // Getters and setters
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public String getNameDB() {
        return nameDB;
    }
    
    public void setNameDB(String nameDB) {
        this.nameDB = nameDB;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getRoleInClan() {
        return roleInClan;
    }
    
    public void setRoleInClan(String roleInClan) {
        this.roleInClan = roleInClan;
    }
    
    public ClanDTO getClanDB() {
        return clanDB;
    }
    
    public void setClanDB(ClanDTO clanDB) {
        this.clanDB = clanDB;
    }
}
