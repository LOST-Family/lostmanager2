package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.KickpointReason;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Clan in REST API
 */
public class ClanDTO {
    
    @JsonProperty("tag")
    private String tag;
    
    @JsonProperty("index")
    private Long index;
    
    @JsonProperty("nameDB")
    private String nameDB;
    
    @JsonProperty("badgeUrl")
    private String badgeUrl;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("maxKickpoints")
    private Long maxKickpoints;
    
    @JsonProperty("minSeasonWins")
    private Long minSeasonWins;
    
    @JsonProperty("kickpointsExpireAfterDays")
    private Integer kickpointsExpireAfterDays;
    
    @JsonProperty("kickpointReasons")
    private List<KickpointReasonDTO> kickpointReasons;
    
    @JsonProperty("cwEndTimeMillis")
    private Long cwEndTimeMillis;
    
    @JsonProperty("raidEndTimeMillis")
    private Long raidEndTimeMillis;
    
    @JsonProperty("cwlDayEndTimeMillis")
    private Long cwlDayEndTimeMillis;
    
    @JsonProperty("cgEndTimeMillis")
    private Long cgEndTimeMillis;
    
    public ClanDTO() {
        // Default constructor for Jackson
    }
    
    /**
     * Create ClanDTO from Clan object
     * @param clan The clan to convert
     */
    public ClanDTO(Clan clan) {
        this.tag = clan.getTag();
        
        try {
            this.index = clan.getIndex();
        } catch (Exception e) {
            this.index = null;
        }
        
        try {
            this.nameDB = clan.getNameDB();
        } catch (Exception e) {
            this.nameDB = null;
        }
        
        try {
            this.badgeUrl = clan.getIconDB();
        } catch (Exception e) {
            this.badgeUrl = null;
        }
        
        try {
            this.description = clan.getDescriptionDB();
        } catch (Exception e) {
            this.description = null;
        }
        
        try {
            this.maxKickpoints = clan.getMaxKickpoints();
        } catch (Exception e) {
            this.maxKickpoints = null;
        }
        
        try {
            this.minSeasonWins = clan.getMinSeasonWins();
        } catch (Exception e) {
            this.minSeasonWins = null;
        }
        
        try {
            this.kickpointsExpireAfterDays = clan.getDaysKickpointsExpireAfter();
        } catch (Exception e) {
            this.kickpointsExpireAfterDays = null;
        }
        
        try {
            ArrayList<KickpointReason> reasons = clan.getKickpointReasons();
            if (reasons != null) {
                this.kickpointReasons = new ArrayList<>();
                for (KickpointReason reason : reasons) {
                    this.kickpointReasons.add(new KickpointReasonDTO(reason));
                }
            }
        } catch (Exception e) {
            this.kickpointReasons = null;
        }
        
        try {
            this.cwEndTimeMillis = clan.getCWEndTimeMillis();
        } catch (Exception e) {
            this.cwEndTimeMillis = null;
        }
        
        try {
            this.raidEndTimeMillis = clan.getRaidEndTimeMillis();
        } catch (Exception e) {
            this.raidEndTimeMillis = null;
        }
        
        try {
            this.cwlDayEndTimeMillis = clan.getCWLDayEndTimeMillis();
        } catch (Exception e) {
            this.cwlDayEndTimeMillis = null;
        }
        
        try {
            this.cgEndTimeMillis = clan.getCGEndTimeMillis();
        } catch (Exception e) {
            this.cgEndTimeMillis = null;
        }
    }
    
    // Getters and setters
    public String getTag() {
        return tag;
    }
    
    public void setTag(String tag) {
        this.tag = tag;
    }
    
    public Long getIndex() {
        return index;
    }
    
    public void setIndex(Long index) {
        this.index = index;
    }
    
    public String getNameDB() {
        return nameDB;
    }
    
    public void setNameDB(String nameDB) {
        this.nameDB = nameDB;
    }
    
    public String getBadgeUrl() {
        return badgeUrl;
    }
    
    public void setBadgeUrl(String badgeUrl) {
        this.badgeUrl = badgeUrl;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Long getMaxKickpoints() {
        return maxKickpoints;
    }
    
    public void setMaxKickpoints(Long maxKickpoints) {
        this.maxKickpoints = maxKickpoints;
    }
    
    public Long getMinSeasonWins() {
        return minSeasonWins;
    }
    
    public void setMinSeasonWins(Long minSeasonWins) {
        this.minSeasonWins = minSeasonWins;
    }
    
    public Integer getKickpointsExpireAfterDays() {
        return kickpointsExpireAfterDays;
    }
    
    public void setKickpointsExpireAfterDays(Integer kickpointsExpireAfterDays) {
        this.kickpointsExpireAfterDays = kickpointsExpireAfterDays;
    }
    
    public List<KickpointReasonDTO> getKickpointReasons() {
        return kickpointReasons;
    }
    
    public void setKickpointReasons(List<KickpointReasonDTO> kickpointReasons) {
        this.kickpointReasons = kickpointReasons;
    }
    
    public Long getCwEndTimeMillis() {
        return cwEndTimeMillis;
    }
    
    public void setCwEndTimeMillis(Long cwEndTimeMillis) {
        this.cwEndTimeMillis = cwEndTimeMillis;
    }
    
    public Long getRaidEndTimeMillis() {
        return raidEndTimeMillis;
    }
    
    public void setRaidEndTimeMillis(Long raidEndTimeMillis) {
        this.raidEndTimeMillis = raidEndTimeMillis;
    }
    
    public Long getCwlDayEndTimeMillis() {
        return cwlDayEndTimeMillis;
    }
    
    public void setCwlDayEndTimeMillis(Long cwlDayEndTimeMillis) {
        this.cwlDayEndTimeMillis = cwlDayEndTimeMillis;
    }
    
    public Long getCgEndTimeMillis() {
        return cgEndTimeMillis;
    }
    
    public void setCgEndTimeMillis(Long cgEndTimeMillis) {
        this.cgEndTimeMillis = cgEndTimeMillis;
    }
}
