package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.KickpointReason;

/**
 * Data Transfer Object for KickpointReason in REST API
 */
public class KickpointReasonDTO {
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("clanTag")
    private String clanTag;
    
    @JsonProperty("amount")
    private Long amount;
    
    public KickpointReasonDTO() {
        // Default constructor for Jackson
    }
    
    /**
     * Create KickpointReasonDTO from KickpointReason object
     * @param reason The kickpoint reason to convert
     */
    public KickpointReasonDTO(KickpointReason reason) {
        try {
            this.name = reason.getName();
        } catch (Exception e) {
            this.name = null;
        }
        
        try {
            this.clanTag = reason.getClanTag();
        } catch (Exception e) {
            this.clanTag = null;
        }
        
        try {
            this.amount = reason.getAmount();
        } catch (Exception e) {
            this.amount = null;
        }
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getClanTag() {
        return clanTag;
    }
    
    public void setClanTag(String clanTag) {
        this.clanTag = clanTag;
    }
    
    public Long getAmount() {
        return amount;
    }
    
    public void setAmount(Long amount) {
        this.amount = amount;
    }
}
