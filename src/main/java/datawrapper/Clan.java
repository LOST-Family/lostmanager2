package datawrapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;
import lostmanager.Bot;

public class Clan {

	// Identifier
	private String clan_tag;

	// Names
	private String namedb;
	private String nameapi;

	// Playerlists
	private ArrayList<Player> playerlistdb;
	private ArrayList<Player> playerlistapi;

	// CW
	private Boolean cwactive;
	private ArrayList<Player> clanwarmembers;
	private Long CWEndTimeMillis;

	// Raid
	private Boolean raidactive;
	private ArrayList<Player> raidmembers;
	private Long RaidEndTimeMillis;

	// CWL
	private Boolean cwlactive;
	private Long CWLDayEndTimeMillis;

	// CS
	private Boolean cgactive;
	private Long CGEndTimeMillis;

	// Settings
	private Long max_kickpoints;
	private Long min_season_wins;
	private Integer kickpoints_expire_after_days;
	private ArrayList<KickpointReason> kickpoint_reasons;

	//Roles	
	public enum Role {
		LEADER, COLEADER, ELDER, MEMBER
	}

	public Clan(String clantag) {
		clan_tag = clantag;
	}

	public Clan refreshData() {
		// Names
		namedb = null;
		nameapi = null;

		// Playerlists
		playerlistdb = null;
		playerlistapi = null;

		// CW
		cwactive = null;
		clanwarmembers = null;
		CWEndTimeMillis = null;

		// Raid
		raidactive = null;
		raidmembers = null;
		RaidEndTimeMillis = null;

		// CWL
		cwlactive = null;
		CWLDayEndTimeMillis = null;

		// CG
		cgactive = null;
		CGEndTimeMillis = null;

		// Settings
		max_kickpoints = null;
		min_season_wins = null;
		kickpoints_expire_after_days = null;
		kickpoint_reasons = null;
		return this;
	}

	//Identifier
	
	public boolean ExistsDB() {
		String sql = "SELECT 1 FROM clans WHERE tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, clan_tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public String getTag() {
		return clan_tag;
	}
	
	//Roles
	
	public String getRoleID(Role role) {
		switch (role) {
		case LEADER:
			return DBUtil.getValueFromSQL("SELECT leader_role_id FROM guilds WHERE clan_tag = ?", String.class,
					clan_tag);
		case COLEADER:
			return DBUtil.getValueFromSQL("SELECT co_leader_role_id FROM guilds WHERE clan_tag = ?", String.class,
					clan_tag);
		case ELDER:
			return DBUtil.getValueFromSQL("SELECT elder_role_id FROM guilds WHERE clan_tag = ?", String.class,
					clan_tag);
		case MEMBER:
			return DBUtil.getValueFromSQL("SELECT member_role_id FROM guilds WHERE clan_tag = ?", String.class,
					clan_tag);
		}
		return null;
	}
	
	//Names

	public String getInfoString() {
		return getNameAPI() + " (" + clan_tag + ")";
	}
	
	public String getNameDB() {
		if (namedb == null) {
			String sql = "SELECT name FROM clans WHERE tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, clan_tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						namedb = rs.getString("name");
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return namedb;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonobject = new JSONObject(APIUtil.getClanJson(clan_tag));
			nameapi = jsonobject.getString("name");
		}
		return nameapi;
	}
	
	//Playerlists

	public ArrayList<Player> getPlayersAPI() {
		if (playerlistapi == null) {
			JSONObject jsonobject = new JSONObject(APIUtil.getClanJson(clan_tag));
			JSONArray ClanMemberList = jsonobject.getJSONArray("memberList");
			playerlistapi = new ArrayList<>();
			for (int i = 0; i < ClanMemberList.length(); i++) {
				JSONObject member = ClanMemberList.getJSONObject(i);
				playerlistapi.add(new Player(member.getString("tag")).setNameAPI(member.getString("name")));
			}
		}
		return playerlistapi;
	}

	public ArrayList<Player> getPlayersDB() {
		if (playerlistdb == null) {
			String sql = "SELECT player_tag FROM clan_members WHERE clan_tag = ?";
			ArrayList<String> result = DBUtil.getArrayListFromSQL(sql, String.class, clan_tag);

			playerlistdb = new ArrayList<>();
			for (String tags : result) {
				playerlistdb.add(new Player(tags));
			}
		}
		return playerlistdb;
	}
	
	//Settings

	public Long getMaxKickpoints() {
		if (max_kickpoints == null) {
			String sql = "SELECT max_kickpoints FROM clan_settings WHERE clan_tag = ?";
			max_kickpoints = DBUtil.getValueFromSQL(sql, Long.class, clan_tag);
		}
		return max_kickpoints;
	}

	public Long getMinSeasonWins() {
		if (min_season_wins == null) {
			String sql = "SELECT min_season_wins FROM clan_settings WHERE clan_tag = ?";
			min_season_wins = DBUtil.getValueFromSQL(sql, Long.class, clan_tag);
		}
		return min_season_wins;
	}

	public Integer getDaysKickpointsExpireAfter() {
		if (kickpoints_expire_after_days == null) {
			String sql = "SELECT kickpoints_expire_after_days FROM clan_settings WHERE clan_tag = ?";
			kickpoints_expire_after_days = DBUtil.getValueFromSQL(sql, Integer.class, clan_tag);
		}
		return kickpoints_expire_after_days;
	}

	public ArrayList<KickpointReason> getKickpointReasons() {
		if (kickpoint_reasons == null) {
			kickpoint_reasons = new ArrayList<>();

			String sql = "SELECT name, clan_tag FROM kickpoint_reasons WHERE clan_tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				// Parameter setzen
				pstmt.setObject(1, clan_tag);

				try (ResultSet rs = pstmt.executeQuery()) {
					while (rs.next()) {
						kickpoint_reasons.add(new KickpointReason(rs.getString("name"), rs.getString("clan_tag")));
					}
					Statement stmt = rs.getStatement();
					rs.close();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return kickpoint_reasons;
	}

	// CG

	public Boolean isCGActive() {
		if (cgactive == null) {

		}
		return cwlactive;
	}

	public Long getCGEndTimeMillis() {
		if (CGEndTimeMillis == null) {

		}
		return CGEndTimeMillis;
	}

	// CWL

	public Boolean isCWLActive() {
		if (cwlactive == null) {

		}
		return cwlactive;
	}

	public Long getCWLDayEndTimeMillis() {
		if (CWLDayEndTimeMillis == null) {

		}
		return CWLDayEndTimeMillis;
	}

	// Raid

	public boolean RaidActive() {
		if (raidactive == null) {
			getRaidMemberList();
		}
		return raidactive;
	}

	public ArrayList<Player> getRaidMemberList() {
		if (raidmembers == null) {
			raidmembers = new ArrayList<>();

			String json;

			String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);

			String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/capitalraidseasons?limit=1";

			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
					.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET()
					.build();

			HttpResponse<String> response = null;
			try {
				response = client.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				json = null;
			}

			if (response.statusCode() == 200) {
				String responseBody = response.body();
				// Einfacher JSON-Name-Parser ohne Bibliotheken:
				json = responseBody;
			} else {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
				json = null;
			}

			JSONObject jsonObject = new JSONObject(json);
			JSONArray items = jsonObject.getJSONArray("items");
			JSONObject currentitem = items.getJSONObject(0);
			String state = currentitem.getString("state");
			raidactive = state.equals("ongoing") ? true : false;
			JSONArray members = currentitem.getJSONArray("members");

			for (int i = 0; i < members.length(); i++) {
				JSONObject member = members.getJSONObject(i);
				String tag = member.getString("tag");
				String name = member.getString("name");
				int attacks = member.getInt("attacks");
				int attackLimit = member.getInt("attackLimit");
				int bonusAttackLimit = member.getInt("bonusAttackLimit");
				int capitalResourcesLooted = member.getInt("capitalResourcesLooted");
				Player p = new Player(tag).setNameAPI(name).setCurrentRaidAttacks(attacks)
						.setCurrentRaidAttackLimit(attackLimit).setCurrentRaidBonusAttackLimit(bonusAttackLimit)
						.setCurrentGoldLooted(capitalResourcesLooted);
				raidmembers.add(p);
			}
		}
		return raidmembers;
	}

	public Long getRaidEndTimeMillis() {
		if (RaidEndTimeMillis == null) {

		}
		return RaidEndTimeMillis;
	}

	// CW

	public Boolean isCWActive() {
		if(cwactive == null) {
			
		}
		return cwactive;
	}
	
	public ArrayList<Player> getWarMemberList() {
		if (clanwarmembers == null) {
			clanwarmembers = new ArrayList<>();

			String json;

			String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);

			String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar";

			HttpClient client = HttpClient.newHttpClient();

			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
					.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET()
					.build();

			HttpResponse<String> response = null;
			try {
				response = client.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
				json = null;
			}

			if (response.statusCode() == 200) {
				String responseBody = response.body();
				// Einfacher JSON-Name-Parser ohne Bibliotheken:
				json = responseBody;
			} else {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
				json = null;
			}

			JSONObject jsonObject = new JSONObject(json);
			String state = jsonObject.getString("state");
			if (state.equals("notInWar")) {
				return null;
			}
			JSONObject WarClan = jsonObject.getJSONObject("clan");
			JSONArray ClanWarMemberList = WarClan.getJSONArray("members");

			for (int i = 0; i < ClanWarMemberList.length(); i++) {
				JSONObject member = ClanWarMemberList.getJSONObject(i);
				String tag = member.getString("tag");
				String name = member.getString("name");
				int warmapposition = member.getInt("mapPosition");
				Player p = new Player(tag).setNameAPI(name).setWarMapPosition(warmapposition);
				clanwarmembers.add(p);
			}
		}
		return clanwarmembers;
	}
	
	public Long getCWEndTimeMillis() {
		if(CWEndTimeMillis == null) {
			
		}
		return CWEndTimeMillis;
	}

}
