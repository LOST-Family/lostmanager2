package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.User;
import lostmanager.datawrapper.Kickpoint;
import java.util.ArrayList;
import java.util.List;

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
    
    @JsonProperty("totalKickpoints")
    private Long totalKickpoints;
    
    @JsonProperty("activeKickpoints")
    private List<KickpointDTO> activeKickpoints;
    
    @JsonProperty("currentRaidAttacks")
    private Integer currentRaidAttacks;
    
    @JsonProperty("currentRaidGoldLooted")
    private Integer currentRaidGoldLooted;
    
    @JsonProperty("currentRaidAttackLimit")
    private Integer currentRaidAttackLimit;
    
    @JsonProperty("currentRaidBonusAttackLimit")
    private Integer currentRaidBonusAttackLimit;
    
    @JsonProperty("warPreference")
    private Boolean warPreference;
    
    @JsonProperty("warMapPosition")
    private Integer warMapPosition;
    
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
        
        // Get kickpoints data
        try {
            this.totalKickpoints = player.getTotalKickpoints();
        } catch (Exception e) {
            this.totalKickpoints = null;
        }
        
        try {
            ArrayList<Kickpoint> kickpoints = player.getActiveKickpoints();
            if (kickpoints != null && !kickpoints.isEmpty()) {
                this.activeKickpoints = new ArrayList<>();
                for (Kickpoint kp : kickpoints) {
                    this.activeKickpoints.add(new KickpointDTO(kp));
                }
            }
        } catch (Exception e) {
            this.activeKickpoints = null;
        }
        
        // Get raid data
        try {
            this.currentRaidAttacks = player.getCurrentRaidAttacks();
        } catch (Exception e) {
            this.currentRaidAttacks = null;
        }
        
        try {
            this.currentRaidGoldLooted = player.getCurrentRaidGoldLooted();
        } catch (Exception e) {
            this.currentRaidGoldLooted = null;
        }
        
        try {
            this.currentRaidAttackLimit = player.getCurrentRaidAttackLimit();
        } catch (Exception e) {
            this.currentRaidAttackLimit = null;
        }
        
        try {
            this.currentRaidBonusAttackLimit = player.getCurrentRaidbonusAttackLimit();
        } catch (Exception e) {
            this.currentRaidBonusAttackLimit = null;
        }
        
        // Get war data
        try {
            this.warPreference = player.getWarPreference();
        } catch (Exception e) {
            this.warPreference = null;
        }
        
        try {
            this.warMapPosition = player.getWarMapPosition();
        } catch (Exception e) {
            this.warMapPosition = null;
        }
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
    
    public Long getTotalKickpoints() {
        return totalKickpoints;
    }
    
    public void setTotalKickpoints(Long totalKickpoints) {
        this.totalKickpoints = totalKickpoints;
    }
    
    public List<KickpointDTO> getActiveKickpoints() {
        return activeKickpoints;
    }
    
    public void setActiveKickpoints(List<KickpointDTO> activeKickpoints) {
        this.activeKickpoints = activeKickpoints;
    }
    
    public Integer getCurrentRaidAttacks() {
        return currentRaidAttacks;
    }
    
    public void setCurrentRaidAttacks(Integer currentRaidAttacks) {
        this.currentRaidAttacks = currentRaidAttacks;
    }
    
    public Integer getCurrentRaidGoldLooted() {
        return currentRaidGoldLooted;
    }
    
    public void setCurrentRaidGoldLooted(Integer currentRaidGoldLooted) {
        this.currentRaidGoldLooted = currentRaidGoldLooted;
    }
    
    public Integer getCurrentRaidAttackLimit() {
        return currentRaidAttackLimit;
    }
    
    public void setCurrentRaidAttackLimit(Integer currentRaidAttackLimit) {
        this.currentRaidAttackLimit = currentRaidAttackLimit;
    }
    
    public Integer getCurrentRaidBonusAttackLimit() {
        return currentRaidBonusAttackLimit;
    }
    
    public void setCurrentRaidBonusAttackLimit(Integer currentRaidBonusAttackLimit) {
        this.currentRaidBonusAttackLimit = currentRaidBonusAttackLimit;
    }
    
    public Boolean getWarPreference() {
        return warPreference;
    }
    
    public void setWarPreference(Boolean warPreference) {
        this.warPreference = warPreference;
    }
    
    public Integer getWarMapPosition() {
        return warMapPosition;
    }
    
    public void setWarMapPosition(Integer warMapPosition) {
        this.warMapPosition = warMapPosition;
    }
}
