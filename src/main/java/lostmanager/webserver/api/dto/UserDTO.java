package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.User;
import lostmanager.datawrapper.Player;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for User in REST API
 */
public class UserDTO {
    
    @JsonProperty("userId")
    private String userId;
    
    @JsonProperty("isAdmin")
    private boolean isAdmin;
    
    @JsonProperty("linkedPlayers")
    private List<String> linkedPlayers;
    
    public UserDTO() {
        // Default constructor for Jackson
        this.linkedPlayers = new ArrayList<>();
    }
    
    /**
     * Create UserDTO from User object
     * @param user The user to convert
     */
    public UserDTO(User user) {
        this.userId = user.getUserID();
        this.isAdmin = user.isAdmin();
        
        // Get list of linked player tags
        this.linkedPlayers = new ArrayList<>();
        ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();
        if (linkedAccounts != null) {
            for (Player player : linkedAccounts) {
                this.linkedPlayers.add(player.getTag());
            }
        }
    }
    
    // Getters and setters
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public boolean isAdmin() {
        return isAdmin;
    }
    
    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }
    
    public List<String> getLinkedPlayers() {
        return linkedPlayers;
    }
    
    public void setLinkedPlayers(List<String> linkedPlayers) {
        this.linkedPlayers = linkedPlayers;
    }
}
