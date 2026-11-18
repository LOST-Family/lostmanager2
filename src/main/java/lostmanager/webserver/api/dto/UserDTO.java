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

		Player.RoleType hoechste = hoechsteRolle(rolestypes);
		highestRole = hoechste == null ? null : hoechste.toString();

		this.nickname = user.getNickname();

	}

	/**
	 * Der höchste Rang über alle Clans. Die Aufzählung ist absteigend sortiert
	 * (ADMIN zuerst), der kleinste Ordinalwert gewinnt. Bis 18.11.2026 wurde hier
	 * nie verglichen: es gewann der Rang des Clans, der in der HashMap zufällig
	 * zuletzt kam – ein Vize mit Zweitaccount als Mitglied in einem anderen Clan
	 * galt auf der Website je nach Reihenfolge nur als Mitglied.
	 *
	 * @return null, wenn die Person in keinem Clan steht
	 */
	static Player.RoleType hoechsteRolle(Collection<Player.RoleType> rollen) {
		Player.RoleType beste = null;
		for (Player.RoleType rolle : rollen) {
			if (rolle == null || rolle == Player.RoleType.NOTINCLAN)
				continue;
			if (beste == null || rolle.ordinal() < beste.ordinal())
				beste = rolle;
		}
		return beste;
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
