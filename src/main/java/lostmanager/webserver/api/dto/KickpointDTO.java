package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.Kickpoint;
import lostmanager.datawrapper.User;
import java.time.OffsetDateTime;

/**
 * Data Transfer Object for Kickpoint in REST API
 */
public class KickpointDTO {
    
    @JsonProperty("id")
    private Long id;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("amount")
    private Long amount;
    
    @JsonProperty("date")
    private String date;
    
    @JsonProperty("givenDate")
    private String givenDate;
    
    @JsonProperty("expirationDate")
    private String expirationDate;
    
    @JsonProperty("givenByUserId")
    private String givenByUserId;
    
    public KickpointDTO() {
        // Default constructor for Jackson
    }
    
    /**
     * Create KickpointDTO from Kickpoint object
     * @param kickpoint The kickpoint to convert
     */
    public KickpointDTO(Kickpoint kickpoint) {
        try {
            this.id = kickpoint.getID();
        } catch (Exception e) {
            this.id = null;
        }
        
        try {
            this.description = kickpoint.getDescription();
        } catch (Exception e) {
            this.description = null;
        }
        
        try {
            this.amount = kickpoint.getAmount();
        } catch (Exception e) {
            this.amount = null;
        }
        
        try {
            OffsetDateTime kpDate = kickpoint.getDate();
            this.date = kpDate != null ? kpDate.toString() : null;
        } catch (Exception e) {
            this.date = null;
        }
        
        try {
            OffsetDateTime givenDt = kickpoint.getGivenDate();
            this.givenDate = givenDt != null ? givenDt.toString() : null;
        } catch (Exception e) {
            this.givenDate = null;
        }
        
        try {
            OffsetDateTime expirationDt = kickpoint.getExpirationDate();
            this.expirationDate = expirationDt != null ? expirationDt.toString() : null;
        } catch (Exception e) {
            this.expirationDate = null;
        }
        
        try {
            User givenBy = kickpoint.getUserGivenBy();
            this.givenByUserId = givenBy != null ? givenBy.getUserID() : null;
        } catch (Exception e) {
            this.givenByUserId = null;
        }
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public Long getAmount() {
        return amount;
    }
    
    public void setAmount(Long amount) {
        this.amount = amount;
    }
    
    public String getDate() {
        return date;
    }
    
    public void setDate(String date) {
        this.date = date;
    }
    
    public String getGivenDate() {
        return givenDate;
    }
    
    public void setGivenDate(String givenDate) {
        this.givenDate = givenDate;
    }
    
    public String getExpirationDate() {
        return expirationDate;
    }
    
    public void setExpirationDate(String expirationDate) {
        this.expirationDate = expirationDate;
    }
    
    public String getGivenByUserId() {
        return givenByUserId;
    }
    
    public void setGivenByUserId(String givenByUserId) {
        this.givenByUserId = givenByUserId;
    }
}
