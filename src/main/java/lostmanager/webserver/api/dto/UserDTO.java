package lostmanager.webserver.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lostmanager.datawrapper.User;
import lostmanager.datawrapper.Player;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * Data Transfer Object for User in REST API
 */
public class UserDTO {

	@JsonProperty("isAdmin")
	private boolean isAdmin;

	@JsonProperty("linkedPlayers")
	private List<String> linkedPlayers;

	@JsonProperty("highestRole")
	private String highestRole;

	@JsonProperty("nickname")
	private String nickname;

	public UserDTO() {
		// Default constructor for Jackson
		this.linkedPlayers = new ArrayList<>();
	}

	/**
	 * Create UserDTO from User object
	 * 
	 * @param user The user to convert
	 */
	public UserDTO(User user) {
		this.isAdmin = user.isAdmin();

		// Get list of linked player tags
		this.linkedPlayers = new ArrayList<>();
		ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();
		if (linkedAccounts != null) {
			for (Player player : linkedAccounts) {
				this.linkedPlayers.add(player.getTag());
			}
		}

		HashMap<String, Player.RoleType> roles = user.getClanRoles();
		Collection<Player.RoleType> rolestypes = roles.values();

		Player.RoleType highest = Player.RoleType.NOTINCLAN;

		for (Player.RoleType role : rolestypes) {
			switch (role) {
			case ADMIN:
				highestRole = role.toString();
				break;
			case LEADER:
				if (highest != Player.RoleType.ADMIN)
					highestRole = role.toString();
				break;
			case COLEADER:
				if (highest != Player.RoleType.ADMIN || highest != Player.RoleType.LEADER)
					highestRole = role.toString();
				break;
			case ELDER:
				if (highest != Player.RoleType.ADMIN || highest != Player.RoleType.LEADER
						|| highest != Player.RoleType.COLEADER)
					highestRole = role.toString();
				break;
			case MEMBER:
				if (highest != Player.RoleType.ADMIN || highest != Player.RoleType.LEADER
						|| highest != Player.RoleType.COLEADER || highest != Player.RoleType.ELDER)
					highestRole = role.toString();
				break;
			default:
				break;
			}
		}

		this.nickname = user.getNickname();

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

	public String getHighestRole() {
		return highestRole;
	}

	public void setHighestRole(String role) {
		highestRole = role;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}
}
