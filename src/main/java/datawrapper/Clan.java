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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import dbutil.Connection;
import dbutil.DBUtil;
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
	private ArrayList<Player> cwlmemberlist;

	// CS
	private Long CGEndTimeMillis;

	// Settings
	private Long max_kickpoints;
	private Long min_season_wins;
	private Integer kickpoints_expire_after_days;
	private ArrayList<KickpointReason> kickpoint_reasons;

	// Roles
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
		cwlmemberlist = null;

		// CG
		CGEndTimeMillis = null;

		// Settings
		max_kickpoints = null;
		min_season_wins = null;
		kickpoints_expire_after_days = null;
		kickpoint_reasons = null;
		return this;
	}

	// Identifier

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

	// Roles

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

	// Names

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
			JSONObject jsonobject = new JSONObject(getJson());
			nameapi = jsonobject.getString("name");
		}
		return nameapi;
	}

	// Playerlists

	public ArrayList<Player> getPlayersAPI() {
		if (playerlistapi == null) {
			JSONObject jsonobject = new JSONObject(getJson());
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

	// Settings

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

	// hardcoded since no api data
	public Long getCGEndTimeMillis() {
		if (CGEndTimeMillis == null) {
			LocalDateTime now = LocalDateTime.now();
			int year = now.getYear();
			int month = now.getMonthValue();
			int day = now.getDayOfMonth();
			int hour = now.getHour();

			// Wenn heute nach dem 28. ist oder genau am 28. nach 13 Uhr (1pm)
			// Use 13:00 for listening events to ensure API data has propagated (1 hour after actual end)
			if (day > 28 || (day == 28 && hour >= 13)) {
				month++;
				if (month > 12) {
					month = 1;
					year++;
				}
			}

			// Schedule for 13:00 (1pm) - 1 hour after actual clan games end
			LocalDateTime next28thAt1pm = LocalDateTime.of(year, month, 28, 13, 0);

			ZonedDateTime zdt = next28thAt1pm.atZone(ZoneId.systemDefault());
			return zdt.toInstant().toEpochMilli();
		}
		return CGEndTimeMillis;
	}

	// CWL

	public Boolean isCWLActive() {
		if (cwlactive == null) {
			JSONObject jsonObject = getCWLJson();
			String state = jsonObject.getString("state");
			if (state.equals("notInWar") || state.equalsIgnoreCase("groupnotfound")) {
				cwlactive = false;
			} else {
				cwlactive = true;
			}
		}
		return cwlactive;
	}

	public Long getCWLDayEndTimeMillis() {
		if (CWLDayEndTimeMillis == null) {
			if (isCWLActive()) {
				JSONObject jsonObject = getCWLJson();
				// Check if endTime exists and is not null
				if (jsonObject.has("endTime") && !jsonObject.isNull("endTime")) {
					String endTime = jsonObject.getString("endTime");
					DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
							.withZone(ZoneOffset.UTC);
					Instant instant = Instant.from(formatter.parse(endTime));
					CWLDayEndTimeMillis = instant.toEpochMilli();
				} else {
					System.err.println("Warning: endTime field is missing or null in CWL API response");
					CWLDayEndTimeMillis = null;
				}
			}
		}
		return CWLDayEndTimeMillis;
	}

	public Long getCWLEndTimeMillis() {
		if (CWLDayEndTimeMillis == null) {

		}
		return CWLDayEndTimeMillis;
	}

	public ArrayList<Player> getCWLMemberList() {
		if (cwlmemberlist == null) {
			cwlmemberlist = new ArrayList<>();
			JSONObject jsonObject = getCWLJson();
			String state = jsonObject.getString("state");
			if (state.equals("notInWar") || state.equalsIgnoreCase("groupnotfound")) {
				return null;
			}
			JSONArray clans = jsonObject.getJSONArray("clans");
			for (int i = 0; i < clans.length(); i++) {
				JSONObject clan = clans.getJSONObject(i);
				JSONArray ClanWarMemberList = clan.getJSONArray("members");
				for (int j = 0; j < ClanWarMemberList.length(); j++) {
					JSONObject member = ClanWarMemberList.getJSONObject(j);
					String tag = member.getString("tag");
					String name = member.getString("name");
					Player p = new Player(tag).setNameAPI(name);
					cwlmemberlist.add(p);
				}
			}
		}
		return cwlmemberlist;
	}

	// Raid

	public boolean RaidActive() {
		if (raidactive == null) {
			JSONObject jsonObject = getRaidJson();
			JSONArray items = jsonObject.getJSONArray("items");
			JSONObject currentitem = items.getJSONObject(0);
			String state = currentitem.getString("state");
			raidactive = state.equals("ongoing") ? true : false;

			// endtimelogic here to prevent double api requests if in same result
			// Check if endTime exists and is not null
			if (currentitem.has("endTime") && !currentitem.isNull("endTime")) {
				String endTime = currentitem.getString("endTime");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
						.withZone(ZoneOffset.UTC);
				Instant instant = Instant.from(formatter.parse(endTime));
				RaidEndTimeMillis = instant.toEpochMilli();
			} else {
				System.err.println("Warning: endTime field is missing or null in Raid API response");
				RaidEndTimeMillis = null;
			}
		}
		return raidactive;
	}

	public ArrayList<Player> getRaidMemberList() {
		if (raidmembers == null) {
			raidmembers = new ArrayList<>();
			JSONObject jsonObject = getRaidJson();
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
			RaidActive();
		}
		return RaidEndTimeMillis;
	}

	// CW

	/**
	 * Helper method to perform HTTP requests with retry logic
	 * @param url The URL to request
	 * @param maxRetries Maximum number of retry attempts
	 * @return HttpResponse or null if all retries failed
	 */
	private HttpResponse<String> performHttpRequestWithRetry(String url, int maxRetries) {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Authorization", "Bearer " + Bot.api_key)
				.header("Accept", "application/json")
				.GET()
				.build();
		
		int attempt = 0;
		while (attempt <= maxRetries) {
			try {
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				
				// If successful (200) or client error (4xx), return immediately (no retry for client errors)
				if (response.statusCode() == 200 || (response.statusCode() >= 400 && response.statusCode() < 500)) {
					return response;
				}
				
				// For server errors (5xx) or other errors, retry
				if (attempt < maxRetries) {
					long waitTime = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 1s, 2s, 4s
					System.err.println("Request failed with status " + response.statusCode() + ", retrying in " + waitTime + "ms (attempt " + (attempt + 1) + "/" + maxRetries + ")");
					Thread.sleep(waitTime);
				}
			} catch (IOException | InterruptedException e) {
				if (attempt < maxRetries) {
					long waitTime = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
					System.err.println("Request failed with exception: " + e.getMessage() + ", retrying in " + waitTime + "ms (attempt " + (attempt + 1) + "/" + maxRetries + ")");
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return null;
					}
				} else {
					e.printStackTrace();
					return null;
				}
			}
			attempt++;
		}
		
		System.err.println("All retry attempts failed for URL: " + url);
		return null;
	}

	public Boolean isCWActive() {
		if (cwactive == null) {
			String json;

			// Check if clan_tag is null before encoding
			if (clan_tag == null) {
				System.err.println("Clan tag is null, cannot check CW status");
				cwactive = false;
				return cwactive;
			}

			String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);
			String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar";

			// Use retry logic with up to 3 attempts
			HttpResponse<String> response = performHttpRequestWithRetry(url, 3);

			// Check if response is null before accessing it
			if (response != null && response.statusCode() == 200) {
				String responseBody = response.body();
				// Einfacher JSON-Name-Parser ohne Bibliotheken:
				json = responseBody;
			} else {
				if (response != null) {
					System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
					System.err.println("Antwort: " + response.body());
				} else {
					System.err.println("Fehler beim Abrufen: response is null");
				}
				json = null;
			}

			if (json != null) {
				JSONObject jsonObject = new JSONObject(json);
				String state = jsonObject.getString("state");
				if (state.equalsIgnoreCase("notInWar")) {
					cwactive = false;
				} else {
					cwactive = true;
				}

				if (cwactive) {
					// CW Endtime logic here to prevent double api request if in same result
					// Check if endTime exists and is not null
					if (jsonObject.has("endTime") && !jsonObject.isNull("endTime")) {
						String endTime = jsonObject.getString("endTime");
						DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
								.withZone(ZoneOffset.UTC);
						Instant instant = Instant.from(formatter.parse(endTime));
						CWEndTimeMillis = instant.toEpochMilli();
					} else {
						System.err.println("Warning: endTime field is missing or null in CW API response");
						CWEndTimeMillis = null;
					}
				}
			} else {
				// If json is null, set cwactive to false to prevent further errors
				cwactive = false;
			}
		}
		return cwactive;
	}

	public ArrayList<Player> getWarMemberList() {
		if (clanwarmembers == null) {
			clanwarmembers = new ArrayList<>();
			JSONObject jsonObject = getCWJson();
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
		if (CWEndTimeMillis == null) {
			isCWActive();
		}
		return CWEndTimeMillis;
	}

	public JSONObject getCWLJson() {
		String json;

		// Check if clan_tag is null before encoding
		if (clan_tag == null) {
			System.err.println("Clan tag is null, cannot retrieve CWL data");
			return new JSONObject("{\"state\":\"groupnotfound\"}");
		}

		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);
		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar/leaguegroup";

		// Use retry logic with up to 3 attempts
		HttpResponse<String> response = performHttpRequestWithRetry(url, 3);

		// Check if response is null before accessing it
		if (response != null && response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			json = responseBody;
		} else {
			if (response != null) {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
			} else {
				System.err.println("Fehler beim Abrufen: response is null");
			}
			json = null;
		}

		if (json != null) {
			JSONObject jsonObject = new JSONObject(json);
			return jsonObject;
		} else {
			// Return a default JSONObject indicating no war
			return new JSONObject("{\"state\":\"groupnotfound\"}");
		}
	}

	public JSONObject getCWJson() {
		String json;

		// Check if clan_tag is null before encoding
		if (clan_tag == null) {
			System.err.println("Clan tag is null, cannot retrieve CW data");
			return new JSONObject("{\"state\":\"notInWar\"}");
		}

		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);
		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar";

		// Use retry logic with up to 3 attempts
		HttpResponse<String> response = performHttpRequestWithRetry(url, 3);

		// Check if response is null before accessing it
		if (response != null && response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			json = responseBody;
		} else {
			if (response != null) {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
			} else {
				System.err.println("Fehler beim Abrufen: response is null");
			}
			json = null;
		}

		if (json != null) {
			JSONObject jsonObject = new JSONObject(json);
			return jsonObject;
		} else {
			// Return a default JSONObject indicating no war
			return new JSONObject("{\"state\":\"notInWar\"}");
		}
	}

	private JSONObject getRaidJson() {
		String json;

		// Check if clan_tag is null before encoding
		if (clan_tag == null) {
			System.err.println("Clan tag is null, cannot retrieve Raid data");
			return new JSONObject("{\"items\":[{\"state\":\"ended\"}]}");
		}

		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);
		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/capitalraidseasons?limit=1";

		// Use retry logic with up to 3 attempts
		HttpResponse<String> response = performHttpRequestWithRetry(url, 3);

		// Check if response is null before accessing it
		if (response != null && response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			json = responseBody;
		} else {
			if (response != null) {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
			} else {
				System.err.println("Fehler beim Abrufen: response is null");
			}
			json = null;
		}

		if (json != null) {
			JSONObject jsonObject = new JSONObject(json);
			return jsonObject;
		} else {
			// Return a default JSONObject indicating no active raid
			return new JSONObject("{\"items\":[{\"state\":\"ended\"}]}");
		}
	}

	public JSONObject getRaidJsonFull() {
		return getRaidJson();
	}

	public static JSONObject getCWLDayJson(String warTag) {
		String json;

		String url = "https://api.clashofclans.com/v1/clans/clanwarleagues/wars/" + warTag;

		HttpClient client = HttpClient.newHttpClient();

		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

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
		return jsonObject;
	}

	public String getJson() {
		// Check if clan_tag is null before encoding
		if (clan_tag == null) {
			System.err.println("Clan tag is null, cannot retrieve clan data");
			return null;
		}
		
		// URL-kodieren des Spieler-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);
		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag;

		// Use retry logic with up to 3 attempts
		HttpResponse<String> response = performHttpRequestWithRetry(url, 3);

		// Check if response is null before accessing it
		if (response != null && response.statusCode() == 200) {
			String responseBody = response.body();
			// Einfacher JSON-Name-Parser ohne Bibliotheken:
			return responseBody;
		} else {
			if (response != null) {
				System.err.println("Fehler beim Abrufen: HTTP " + response.statusCode());
				System.err.println("Antwort: " + response.body());
			} else {
				System.err.println("Fehler beim Abrufen: response is null");
			}
			return null;
		}
	}
	
}
