package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Clan;

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
}
