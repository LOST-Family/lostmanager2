package datawrapper;

import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import datautil.DBUtil;

public class ListeningEvent {

	public enum LISTENINGTYPE {
		CW, RAID, CWLDAY, CS, FIXTIMEINTERVAL, CWLEND
	}

	public enum ACTIONTYPE {
		INFOMESSAGE, CUSTOMMESSAGE, KICKPOINT, CWDONATOR
	}

	private Long id;
	private String clan_tag;
	private LISTENINGTYPE listeningtype;
	private Long durationuntilend; // in ms
	private ACTIONTYPE actiontype;
	private String channelid;
	private ArrayList<ActionValue> actionvalues;

	private Long timestamptofire;

	public ListeningEvent refreshData() {
		clan_tag = null;
		listeningtype = null;
		durationuntilend = null;
		actiontype = null;
		channelid = null;
		actionvalues = null;
		timestamptofire = null;
		return this;
	}

	public ListeningEvent(long id) {
		this.id = id;
	}

	public ListeningEvent setClanTag(String clan_tag) {
		this.clan_tag = clan_tag;
		return this;
	}

	public ListeningEvent setListeningType(LISTENINGTYPE type) {
		this.listeningtype = type;
		return this;
	}

	public ListeningEvent setDurationUntilEnd(Long l) {
		this.durationuntilend = l;
		return this;
	}

	public ListeningEvent setActionType(ACTIONTYPE type) {
		this.actiontype = type;
		return this;
	}

	public ListeningEvent setChannelID(String channelid) {
		this.channelid = channelid;
		return this;
	}

	public ListeningEvent setActionValues(ArrayList<ActionValue> list) {
		this.actionvalues = list;
		return this;
	}

	public long getID() {
		return id;
	}

	public String getClanTag() {
		if (clan_tag == null) {
			clan_tag = DBUtil.getValueFromSQL("SELECT clan_tag FROM listening_events WHERE id = ?", String.class, id);
		}
		return clan_tag;
	}

	public LISTENINGTYPE getListeningType() {
		if (listeningtype == null) {
			String type = DBUtil.getValueFromSQL("SELECT listeningtype FROM listening_events WHERE id = ?",
					String.class, id);
			listeningtype = type.equals("cw") ? LISTENINGTYPE.CW
					: type.equals("raid") ? LISTENINGTYPE.RAID
							: type.equals("cwl") ? LISTENINGTYPE.CWLDAY : type.equals("cs") ? LISTENINGTYPE.CS : null;
		}
		return listeningtype;
	}

	public long getDurationUntilEnd() {
		if (durationuntilend == null) {
			durationuntilend = DBUtil.getValueFromSQL("SELECT listeningvalue FROM listening_events WHERE id = ?",
					Long.class, id);
		}
		return durationuntilend;
	}

	public ACTIONTYPE getActionType() {
		if (actiontype == null) {
			String type = DBUtil.getValueFromSQL("SELECT actiontype FROM listening_events WHERE id = ?", String.class,
					id);
			actiontype = type.equals("infomessage") ? ACTIONTYPE.INFOMESSAGE
					: type.equals("custommessage") ? ACTIONTYPE.CUSTOMMESSAGE
							: type.equals("kickpoint") ? ACTIONTYPE.KICKPOINT : null;
		}
		return actiontype;
	}

	public String getChannelID() {
		if (channelid == null) {
			channelid = DBUtil.getValueFromSQL("SELECT channel_id FROM listening_events WHERE id = ?", String.class,
					id);
		}
		return channelid;
	}

	public ArrayList<ActionValue> getActionValues() {
		if (actionvalues == null) {
			String json = DBUtil.getValueFromSQL("SELECT actionvalues FROM listening_events WHERE id = ?", String.class,
					id);
			ObjectMapper mapper = new ObjectMapper();
			try {
				actionvalues = mapper.readValue(json, new TypeReference<ArrayList<ActionValue>>() {
				});
			} catch (JsonProcessingException e) {
				e.printStackTrace();
			}
		}
		return actionvalues;
	}

	public Long getTimestamp() {
		if (timestamptofire == null) {
			Clan c = new Clan(clan_tag);
			switch (getListeningType()) {
			case CS:
				timestamptofire = c.getCGEndTimeMillis() - getDurationUntilEnd();
				break;
			case CW:
				timestamptofire = c.getCWEndTimeMillis() - getDurationUntilEnd();
				break;
			case CWLDAY:
				timestamptofire = c.getCWLDayEndTimeMillis() - getDurationUntilEnd();
				break;
			case RAID:
				timestamptofire = c.getRaidEndTimeMillis() - getDurationUntilEnd();
				break;
			case FIXTIMEINTERVAL:
				timestamptofire = getDurationUntilEnd();
				break;
			case CWLEND:
				
				break;
			default:
				break;
			}
		}
		return timestamptofire;
	}

	public void fireEvent() {
		Clan clan = new Clan(getClanTag());
		
		switch (getListeningType()) {
		case CS:
			handleClanGamesEvent(clan);
			break;
			
		case CW:
			handleClanWarEvent(clan);
			break;
			
		case CWLDAY:
			handleCWLDayEvent(clan);
			break;
			
		case RAID:
			handleRaidEvent(clan);
			break;
			
		case FIXTIMEINTERVAL:
			// For custom timed events
			break;
			
		default:
			break;
		}
	}
	
	private void handleClanGamesEvent(Clan clan) {
		// Get before/after values from achievements database
		java.sql.Timestamp startTime = java.sql.Timestamp.from(
			lostmanager.Bot.getPrevious22thAt7am().toInstant()
		);
		java.sql.Timestamp endTime = java.sql.Timestamp.from(
			lostmanager.Bot.getPrevious28thAt12pm().toInstant()
		);
		
		// Get all players in clan
		ArrayList<Player> players = clan.getPlayersDB();
		StringBuilder message = new StringBuilder();
		message.append("## Clan Games Results\n\n");
		
		boolean hasViolations = false;
		for (Player p : players) {
			// Get achievement data at start and end
			String sql = "SELECT data FROM achievement_data WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? ORDER BY time LIMIT 1";
			Integer pointsStart = DBUtil.getValueFromSQL(sql, Integer.class, p.getTag(), startTime);
			Integer pointsEnd = DBUtil.getValueFromSQL(sql, Integer.class, p.getTag(), endTime);
			
			if (pointsStart != null && pointsEnd != null) {
				int difference = pointsEnd - pointsStart;
				if (difference < 4000) { // Example threshold
					hasViolations = true;
					message.append(p.getNameAPI()).append(": ").append(difference).append(" points");
					if (p.getUser() != null) {
						message.append(" (<@").append(p.getUser().getUserID()).append(">)");
					}
					message.append("\n");
					
					// Handle action type
					if (getActionType() == ACTIONTYPE.KICKPOINT) {
						addKickpointForPlayer(p, "Clan Games nicht erreicht (" + difference + " points)");
					}
				}
			}
		}
		
		if (hasViolations || getActionType() == ACTIONTYPE.INFOMESSAGE) {
			sendMessageToChannel(message.toString());
		}
	}
	
	private void handleClanWarEvent(Clan clan) {
		if (!clan.isCWActive()) {
			return;
		}
		
		org.json.JSONObject cwJson = clan.getCWJson();
		String state = cwJson.getString("state");
		
		// Check if it's a "filler" action at start
		boolean isFillerAction = false;
		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.type && 
			    av.getType() == ActionValue.ACTIONVALUETYPE.FILLER) {
				isFillerAction = true;
				break;
			}
		}
		
		if (isFillerAction && state.equals("preparation")) {
			handleCWFiller(clan, cwJson);
		} else if (state.equals("inWar") || state.equals("warEnded")) {
			handleCWMissedAttacks(clan, cwJson);
		}
	}
	
	private void handleCWFiller(Clan clan, org.json.JSONObject cwJson) {
		// Get war members and check preferences
		org.json.JSONObject clanData = cwJson.getJSONObject("clan");
		org.json.JSONArray members = clanData.getJSONArray("members");
		
		StringBuilder message = new StringBuilder();
		message.append("## CW War Preferences Check\n\n");
		message.append("The following members are opted OUT of war:\n\n");
		
		ArrayList<Player> dbMembers = clan.getPlayersDB();
		boolean hasOptedOut = false;
		
		for (Player dbPlayer : dbMembers) {
			boolean inWar = false;
			for (int i = 0; i < members.length(); i++) {
				org.json.JSONObject member = members.getJSONObject(i);
				if (member.getString("tag").equals(dbPlayer.getTag())) {
					inWar = true;
					break;
				}
			}
			
			if (!inWar) {
				hasOptedOut = true;
				message.append("- ").append(dbPlayer.getNameAPI());
				if (dbPlayer.getUser() != null) {
					message.append(" (<@").append(dbPlayer.getUser().getUserID()).append(">)");
				}
				message.append("\n");
			}
		}
		
		if (hasOptedOut) {
			sendMessageToChannel(message.toString());
		}
	}
	
	private void handleCWMissedAttacks(Clan clan, org.json.JSONObject cwJson) {
		org.json.JSONObject clanData = cwJson.getJSONObject("clan");
		org.json.JSONArray members = clanData.getJSONArray("members");
		int attacksPerMember = cwJson.getInt("attacksPerMember");
		
		StringBuilder message = new StringBuilder();
		message.append("## Clan War - Missed Attacks\n\n");
		
		boolean hasMissedAttacks = false;
		for (int i = 0; i < members.length(); i++) {
			org.json.JSONObject member = members.getJSONObject(i);
			String tag = member.getString("tag");
			String name = member.getString("name");
			
			int attacks = 0;
			if (member.has("attacks")) {
				attacks = member.getJSONArray("attacks").length();
			}
			
			if (attacks < attacksPerMember) {
				hasMissedAttacks = true;
				Player p = new Player(tag);
				message.append("- ").append(name).append(": ").append(attacks).append("/")
						.append(attacksPerMember).append(" attacks");
				if (p.getUser() != null) {
					message.append(" (<@").append(p.getUser().getUserID()).append(">)");
				}
				message.append("\n");
				
				// Handle kickpoint action
				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(p, "CW Angriffe verpasst (" + attacks + "/" + attacksPerMember + ")");
				}
			}
		}
		
		if (hasMissedAttacks) {
			sendMessageToChannel(message.toString());
		}
	}
	
	private void handleCWLDayEvent(Clan clan) {
		if (!clan.isCWLActive()) {
			return;
		}
		
		// Get CWL group data
		org.json.JSONObject cwlJson = clan.getCWLJson();
		org.json.JSONArray rounds = cwlJson.getJSONArray("rounds");
		
		StringBuilder message = new StringBuilder();
		message.append("## CWL Day - Missed Attacks\n\n");
		boolean hasMissedAttacks = false;
		
		// Find the current/most recent war
		for (int r = 0; r < rounds.length(); r++) {
			org.json.JSONArray warTags = rounds.getJSONObject(r).getJSONArray("warTags");
			
			for (int w = 0; w < warTags.length(); w++) {
				String warTag = warTags.getString(w);
				org.json.JSONObject warData = clan.getCWLDayJson(warTag);
				
				if (warData.getString("state").equals("warEnded") || 
				    warData.getString("state").equals("inWar")) {
					
					org.json.JSONObject clanData = warData.getJSONObject("clan");
					if (clanData.getString("tag").equals(clan.getTag())) {
						org.json.JSONArray members = clanData.getJSONArray("members");
						
						for (int i = 0; i < members.length(); i++) {
							org.json.JSONObject member = members.getJSONObject(i);
							String tag = member.getString("tag");
							String name = member.getString("name");
							
							int attacks = 0;
							if (member.has("attacks")) {
								attacks = member.getJSONArray("attacks").length();
							}
							
							if (attacks < 1) { // CWL has 1 attack per member
								hasMissedAttacks = true;
								Player p = new Player(tag);
								message.append("- ").append(name);
								if (p.getUser() != null) {
									message.append(" (<@").append(p.getUser().getUserID()).append(">)");
								}
								message.append("\n");
								
								if (getActionType() == ACTIONTYPE.KICKPOINT) {
									addKickpointForPlayer(p, "CWL Angriff verpasst");
								}
							}
						}
					}
				}
			}
		}
		
		if (hasMissedAttacks) {
			sendMessageToChannel(message.toString());
		}
	}
	
	private void handleRaidEvent(Clan clan) {
		if (!clan.RaidActive()) {
			return;
		}
		
		ArrayList<Player> raidMembers = clan.getRaidMemberList();
		ArrayList<Player> dbMembers = clan.getPlayersDB();
		
		StringBuilder message = new StringBuilder();
		message.append("## Raid Weekend - Missed Attacks\n\n");
		
		boolean hasMissedAttacks = false;
		
		// Check members who didn't raid at all
		for (Player dbPlayer : dbMembers) {
			boolean foundInRaid = false;
			for (Player raidPlayer : raidMembers) {
				if (raidPlayer.getTag().equals(dbPlayer.getTag())) {
					foundInRaid = true;
					int attacks = raidPlayer.getCurrentRaidAttacks();
					int maxAttacks = raidPlayer.getCurrentRaidAttackLimit() + 
									raidPlayer.getCurrentRaidbonusAttackLimit();
					
					if (attacks < maxAttacks) {
						hasMissedAttacks = true;
						message.append("- ").append(raidPlayer.getNameAPI())
								.append(": ").append(attacks).append("/").append(maxAttacks);
						if (dbPlayer.getUser() != null) {
							message.append(" (<@").append(dbPlayer.getUser().getUserID()).append(">)");
						}
						message.append("\n");
						
						if (getActionType() == ACTIONTYPE.KICKPOINT) {
							addKickpointForPlayer(dbPlayer, 
								"Raid Angriffe verpasst (" + attacks + "/" + maxAttacks + ")");
						}
					}
					break;
				}
			}
			
			if (!foundInRaid) {
				hasMissedAttacks = true;
				message.append("- ").append(dbPlayer.getNameAPI()).append(": 0 attacks");
				if (dbPlayer.getUser() != null) {
					message.append(" (<@").append(dbPlayer.getUser().getUserID()).append(">)");
				}
				message.append("\n");
				
				if (getActionType() == ACTIONTYPE.KICKPOINT) {
					addKickpointForPlayer(dbPlayer, "Raid nicht teilgenommen");
				}
			}
		}
		
		if (hasMissedAttacks) {
			sendMessageToChannel(message.toString());
		}
	}
	
	private void addKickpointForPlayer(Player player, String reason) {
		// Get kickpoint reason from action values if specified
		KickpointReason kpReason = null;
		for (ActionValue av : getActionValues()) {
			if (av.getSaved() == ActionValue.kind.reason) {
				kpReason = av.getReason();
				break;
			}
		}
		
		int amount = 1; // Default
		if (kpReason != null && kpReason.Exists()) {
			amount = (int) kpReason.getAmount();
			reason = kpReason.getName();
		}
		
		Clan clan = player.getClanDB();
		if (clan != null) {
			java.sql.Timestamp now = java.sql.Timestamp.from(java.time.Instant.now());
			java.sql.Timestamp expires = java.sql.Timestamp.valueOf(
				now.toLocalDateTime().plusDays(clan.getDaysKickpointsExpireAfter())
			);
			
			DBUtil.executeUpdate(
				"INSERT INTO kickpoints (player_tag, date, amount, description, created_by_discord_id, created_at, expires_at, clan_tag, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				player.getTag(), now, amount, reason, "0", now, expires, clan.getTag(), now
			);
		}
	}
	
	private void sendMessageToChannel(String message) {
		String channelId = getChannelID();
		if (channelId != null && !channelId.isEmpty()) {
			try {
				net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel = 
					lostmanager.Bot.getJda().getTextChannelById(channelId);
				if (channel != null) {
					channel.sendMessage(message).queue();
				}
			} catch (Exception e) {
				System.err.println("Failed to send message to channel " + channelId + ": " + e.getMessage());
			}
		}
	}

}
