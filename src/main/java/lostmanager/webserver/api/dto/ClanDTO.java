package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Clan;

/**
 * Data Transfer Object for Clan in REST API
 */
public class ClanDTO {
    
    @JsonProperty("tag")
    private String tag;
    
    @JsonProperty("nameDB")
    private String nameDB;
    
    
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
            this.nameDB = clan.getNameDB();
        } catch (Exception e) {
            this.nameDB = null;
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
}
