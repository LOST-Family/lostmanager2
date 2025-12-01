package datawrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import datawrapper.AchievementData.Type;
import dbutil.Connection;
import dbutil.DBUtil;
import lostmanager.Bot;

public class Player {

	public enum RoleType {
		ADMIN, LEADER, COLEADER, ELDER, MEMBER, NOTINCLAN
	};

	/**
	 * Checks if a role is elder or higher (ELDER, COLEADER, or LEADER).
	 * Note: RoleType.ELDER corresponds to the "admin" clan_role in the database.
	 * RoleType.ADMIN is used for bot administrators, not clan elders.
	 * @param role The role type to check
	 * @return true if the role is elder or higher, false otherwise
	 */
	public static boolean isElderOrHigher(RoleType role) {
		return role == RoleType.ELDER || role == RoleType.COLEADER || role == RoleType.LEADER;
	}

	/**
	 * Checks if a role string represents elder or higher (admin, coLeader, or leader).
	 * Note: hiddencoleader is NOT included as hidden coleaders should not have roles edited.
	 * @param role The role string to check
	 * @return true if the role is elder or higher, false otherwise
	 */
	public static boolean isElderOrHigherString(String role) {
		return role != null && (role.equals("admin") || role.equals("coLeader") || role.equals("leader"));
	}

	private String tag;
	private Integer currentRaidAttacks;
	private Integer currentRaidGoldLooted;
	private Integer currentRaidAttackLimit;
	private Integer currentRaidbonusAttackLimit;
	private Boolean warpreference;
	private Integer warmapposition;
	private String namedb;
	private String nameapi;
	private User user;
	private Clan clandb;
	private Clan clanapi;
	private ArrayList<Kickpoint> kickpoints;
	private Long kickpointstotal;
	private RoleType roledb;
	private RoleType roleapi;
	private AchievementData achievementDataAPI;
	private HashMap<AchievementData.Type, ArrayList<AchievementData>> achievementDatasInDB;

	public Player(String tag) {
		this.tag = tag;
	}

	// sets all Data except tag to null for reloading with getters

	public Player refreshData() {
		currentRaidAttacks = null;
		currentRaidGoldLooted = null;
		currentRaidAttackLimit = null;
		currentRaidbonusAttackLimit = null;
		warpreference = null;
		warmapposition = null;
		namedb = null;
		nameapi = null;
		user = null;
		clandb = null;
		clanapi = null;
		kickpoints = null;
		kickpointstotal = null;
		roledb = null;
		roleapi = null;
		achievementDataAPI = null;
		achievementDatasInDB = null;
		return this;
	}

	// setter; only use if already known -> better performance if not needed to be
	// requested
	// returns self, allows chaining

	public Player setCurrentRaidAttackLimit(Integer i) {
		this.currentRaidAttackLimit = i;
		return this;
	}

	public Player setCurrentRaidBonusAttackLimit(Integer i) {
		this.currentRaidbonusAttackLimit = i;
		return this;
	}

	public Player setCurrentRaidAttacks(Integer i) {
		this.currentRaidAttacks = i;
		return this;
	}

	public Player setCurrentGoldLooted(Integer i) {
		this.currentRaidGoldLooted = i;
		return this;
	}

	public Player setWarPreference(Boolean b) {
		this.warpreference = b;
		return this;
	}

	public Player setWarMapPosition(Integer i) {
		this.warmapposition = i;
		return this;
	}

	public Player setNameDB(String name) {
		this.namedb = name;
		return this;
	}

	public Player setNameAPI(String name) {
		this.nameapi = name;
		return this;
	}

	public Player setUser(User user) {
		this.user = user;
		return this;
	}

	public Player setClanDB(Clan clan) {
		this.clandb = clan;
		return this;
	}

	public Player setClanAPI(Clan clan) {
		this.clanapi = clan;
		return this;
	}

	public Player setKickpoints(ArrayList<Kickpoint> kickpoints) {
		this.kickpoints = kickpoints;
		return this;
	}

	public Player setKickpointsTotal(Long i) {
		this.kickpointstotal = i;
		return this;
	}

	public Player setRoleDB(RoleType role) {
		this.roledb = role;
		return this;
	}

	// getter; creates Data from API/DB if needed -> Null if not existant

	public Integer getCurrentRaidAttackLimit() {
		if (currentRaidAttackLimit == null) {
			Clan c = getClanAPI();
			if (c != null) {
				ArrayList<Player> raidmembers = c.getRaidMemberList();
				for (Player p : raidmembers) {
					if (p.getTag().equals(tag)) {
						currentRaidAttackLimit = p.getCurrentRaidAttackLimit();
						break;
					}
				}
			}
		}
		return currentRaidAttackLimit;
	}

	public Integer getCurrentRaidbonusAttackLimit() {
		if (currentRaidbonusAttackLimit == null) {
			Clan c = getClanAPI();
			if (c != null) {
				ArrayList<Player> raidmembers = c.getRaidMemberList();
				for (Player p : raidmembers) {
					if (p.getTag().equals(tag)) {
						currentRaidbonusAttackLimit = p.getCurrentRaidbonusAttackLimit();
						break;
					}
				}
			}
		}
		return currentRaidbonusAttackLimit;
	}

	public Integer getCurrentRaidAttacks() {
		if (currentRaidAttacks == null) {
			Clan c = getClanAPI();
			if (c != null) {
				ArrayList<Player> raidmembers = c.getRaidMemberList();
				for (Player p : raidmembers) {
					if (p.getTag().equals(tag)) {
						currentRaidAttacks = p.getCurrentRaidAttacks();
						break;
					}
				}
			}
		}
		return currentRaidAttacks;
	}

	public Integer getCurrentRaidGoldLooted() {
		if (currentRaidGoldLooted == null) {
			Clan c = getClanAPI();
			if (c != null) {
				ArrayList<Player> raidmembers = c.getRaidMemberList();
				for (Player p : raidmembers) {
					if (p.getTag().equals(tag)) {
						currentRaidGoldLooted = p.getCurrentRaidGoldLooted();
						break;
					}
				}
			}
		}
		return currentRaidGoldLooted;
	}

	public boolean IsLinked() {
		String sql = "SELECT 1 FROM players WHERE coc_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean AccExists() {
		try {
			String encodedTag = URLEncoder.encode(tag, "UTF-8");
			// Clash of Clans API-Endpunkt
			URL url = new URI("https://api.clashofclans.com/v1/players/" + encodedTag).toURL();

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setRequestProperty("Authorization", "Bearer " + Bot.api_key);
			connection.setRequestProperty("Accept", "application/json");

			int responseCode = connection.getResponseCode();

			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String line;
				StringBuilder responseContent = new StringBuilder();
				while ((line = in.readLine()) != null) {
					responseContent.append(line);
				}
				in.close();

				return true;
			} else {
				System.out.println("Verifizierung fehlgeschlagen. Fehlercode: " + responseCode);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean verifyCocTokenAPI(String playerApiToken) {
		try {
			String encodedTag = URLEncoder.encode(tag, "UTF-8");
			URL url = new URI("https://api.clashofclans.com/v1/players/" + encodedTag + "/verifytoken").toURL();

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setRequestProperty("Authorization", "Bearer " + Bot.api_key);
			connection.setRequestProperty("Accept", "application/json");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setDoOutput(true);

			// JSON-Body mit Token senden
			String requestBody = "{ \"token\": \"" + playerApiToken + "\" }";
			try (OutputStream os = connection.getOutputStream()) {
				byte[] input = requestBody.getBytes("utf-8");
				os.write(input, 0, input.length);
			}

			int responseCode = connection.getResponseCode();

			// Wenn 200: Antwort lesen und JSON prüfen (expect: { "status":"ok" } wenn
			// richtig)
			if (responseCode == 200) {
				BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
				String responseLine = in.readLine();
				in.close();
				return responseLine != null && responseLine.contains("\"status\":\"ok\"");
			} else {
				System.out.println("Verifizierung fehlgeschlagen. Fehlercode: " + responseCode);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	// all public getter Methods

	public boolean getWarPreference() {
		if (warpreference == null) {
			JSONObject jsonObject = new JSONObject(getJson());
			warpreference = jsonObject.getString("warPreference").equals("in");
		}
		return warpreference;
	}

	public Integer getWarMapPosition() {
		if (warmapposition == null) {
			Clan c = getClanAPI();
			if (c != null) {
				ArrayList<Player> warmemberlist = c.getWarMemberList();
				for (Player p : warmemberlist) {
					if (p.getTag().equals(tag)) {
						warmapposition = p.getWarMapPosition();
						break;
					}
				}
			}
		}
		return warmapposition;
	}

	public String getInfoStringDB() {
		try {
			return getNameDB() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getInfoStringAPI() {
		try {
			return getNameAPI() + " (" + tag + ")";
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public String getTag() {
		return tag;
	}

	public String getNameAPI() {
		if (nameapi == null) {
			JSONObject jsonObject = new JSONObject(getJson());
			nameapi = jsonObject.getString("name");
		}
		return nameapi;
	}

	public String getNameDB() {
		if (namedb == null) {
			namedb = DBUtil.getValueFromSQL("SELECT name FROM players WHERE coc_tag = ?", String.class, tag);
		}
		return namedb;
	}

	public User getUser() {
		if (user == null) {
			String value = DBUtil.getValueFromSQL("SELECT discord_id FROM players WHERE coc_tag = ?", String.class,
					tag);
			user = value == null ? null : new User(value);
		}
		return user;
	}

	public Clan getClanAPI() {
		if (clanapi == null) {
			JSONObject jsonObject = new JSONObject(getJson());
			if (jsonObject.has("clan") && !jsonObject.isNull("clan")) {
				JSONObject clanObject = jsonObject.getJSONObject("clan");
				if (clanObject.has("tag")) {
					clanapi = new Clan(clanObject.getString("tag"));
				}
			}
		}
		return clanapi;
	}

	public Clan getClanDB() {
		if (clandb == null) {
			String value = DBUtil.getValueFromSQL("SELECT clan_tag FROM clan_members WHERE player_tag = ?",
					String.class, tag);
			clandb = value == null ? null : new Clan(value);
		}
		return clandb;
	}

	public ArrayList<Kickpoint> getActiveKickpoints() {
		if (kickpoints == null) {
			kickpoints = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ?";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				Kickpoint kp = new Kickpoint(id);
				if (kp.getExpirationDate().isAfter(OffsetDateTime.now())) {
					kickpoints.add(kp);
				}
			}
		}
		return kickpoints;
	}

	public long getTotalKickpoints() {
		if (kickpointstotal == null) {
			ArrayList<Kickpoint> a = new ArrayList<>();
			String sql = "SELECT id FROM kickpoints WHERE player_tag = ?";
			for (Long id : DBUtil.getArrayListFromSQL(sql, Long.class, tag)) {
				a.add(new Kickpoint(id));
			}
			kickpointstotal = 0L;
			for (Kickpoint kp : a) {
				kickpointstotal = kickpointstotal + kp.getAmount();
			}
		}
		return kickpointstotal;
	}

	public RoleType getRoleDB() {
		if (roledb == null) {
			if (new Player(tag).getClanDB() == null) {
				return RoleType.NOTINCLAN;
			}
			String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						String rolestring = rs.getString("clan_role");
						roledb = rolestring.equals("leader") ? RoleType.LEADER
								: rolestring.equals("coLeader") || rolestring.equals("hiddencoleader")
										? RoleType.COLEADER
										: rolestring.equals("admin") ? RoleType.ELDER
												: rolestring.equals("member") ? RoleType.MEMBER : null;
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return roledb;
	}

	public boolean isHiddenColeader() {
		String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					String rolestring = rs.getString("clan_role");
					return rolestring != null && rolestring.equals("hiddencoleader");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public RoleType getRoleAPI() {
		if (roleapi == null) {
			JSONObject jsonObject = new JSONObject(getJson());
			String rolestring = jsonObject.getString("role");
			roleapi = rolestring.equalsIgnoreCase("leader") ? RoleType.LEADER
					: rolestring.equalsIgnoreCase("coleader") ? RoleType.COLEADER
							: rolestring.equalsIgnoreCase("admin") ? RoleType.ELDER
									: rolestring.equalsIgnoreCase("member") ? RoleType.MEMBER
											: rolestring.equalsIgnoreCase("not_member") ? RoleType.NOTINCLAN : null;
		}
		return roleapi;
	}

	public HashMap<Type, ArrayList<AchievementData>> getAchievementDatasDB() {
		if (achievementDatasInDB == null) {
			String jsonindb = DBUtil.getValueFromSQL("SELECT data FROM achievements WHERE tag = ?", String.class, tag);
			if (jsonindb != null) {
				ObjectMapper mapper = new ObjectMapper();
				try {
					achievementDatasInDB = mapper.readValue(jsonindb,
							new TypeReference<HashMap<Type, ArrayList<AchievementData>>>() {
							});
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}
			} else {
				achievementDatasInDB = new HashMap<>();
				for (AchievementData.Type type : AchievementData.Type.values()) {
					achievementDatasInDB.put(type, new ArrayList<>());
				}
				// Convert HashMap to JSON string before inserting
				ObjectMapper insertMapper = new ObjectMapper();
				try {
					String jsonData = insertMapper.writeValueAsString(achievementDatasInDB);
					DBUtil.executeUpdate("INSERT INTO achievements (tag, data) VALUES (?, ?)", tag, jsonData);
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}
			}
		}
		return achievementDatasInDB;
	}

	// Helpers

	public AchievementData getDataFromDateIfAvailable(AchievementData.Type type, Timestamp timestamp) {
		HashMap<Type, ArrayList<AchievementData>> alldata = getAchievementDatasDB();
		ArrayList<AchievementData> data = alldata.get(type);
		for (AchievementData x : data) {
			if (x.getTimeExtracted().getTime() == timestamp.getTime()) {
				return x;
			}
		}
		return null;
	}

	public void addAchievementDataToDB(AchievementData.Type type, Timestamp timestamp) {
		AchievementData data = getAchievementDataAPI(type, timestamp);
		HashMap<Type, ArrayList<AchievementData>> datalists = getAchievementDatasDB();
		boolean exists = true;
		if (datalists == null) {
			exists = false;
			datalists = new HashMap<>();
			for (AchievementData.Type t : AchievementData.Type.values()) {
				datalists.put(t, new ArrayList<>());
			}
		}
		ArrayList<AchievementData> datalist = datalists.get(type);
		datalist.add(data);
		datalists.put(type, datalist);
		ObjectMapper mapper = new ObjectMapper();
		String jsonlist = null;
		try {
			jsonlist = mapper.writeValueAsString(datalists);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		if (exists) {
			DBUtil.executeUpdate("UPDATE achievements SET data = ? WHERE tag = ?", jsonlist, tag);
		} else {
			DBUtil.executeUpdate("INSERT INTO achievements (tag, data) VALUES (?, ?)", tag, jsonlist);
		}
		
		// Also insert into the new achievement_data table for ListeningEvent queries
		// This uses the normalized schema that ListeningEvent.handleClanGamesEvent() expects
		if (data != null && data.getData() != null) {
			String typeStr = type.name();
			Integer dataValue = (Integer) data.getData();
			DBUtil.executeUpdate(
				"INSERT INTO achievement_data (player_tag, type, time, data) VALUES (?, ?, ?, ?::jsonb) " +
				"ON CONFLICT (player_tag, type, time) DO UPDATE SET data = ?::jsonb",
				tag, typeStr, timestamp, String.valueOf(dataValue), String.valueOf(dataValue));
		}
	}

	public AchievementData getAchievementDataAPI(AchievementData.Type type, Timestamp timestamp) {
		if (achievementDataAPI == null) {
			Integer value = null;

			switch (type) {
			case WINS:
				value = Integer.valueOf(getAchievementAPI("Conqueror"));
				achievementDataAPI = new AchievementData(timestamp, value, Type.WINS);
				break;
			case CLANGAMES_POINTS:
				value = Integer.valueOf(getAchievementAPI("Games Champion"));
				achievementDataAPI = new AchievementData(timestamp, value, Type.CLANGAMES_POINTS);
				break;
			default:
				return null;
			}
		}
		return achievementDataAPI;
	}

	private Integer getAchievementAPI(String achievementName) {
		Integer value = null;
		JSONObject jsonObject = new JSONObject(getJson());
		JSONArray achievementarray = jsonObject.getJSONArray("achievements");
		for (int i = 0; i < achievementarray.length(); i++) {
			JSONObject achievement = achievementarray.getJSONObject(i);
			String achievementname = achievement.getString("name");
			if (achievementname.equals(achievementName)) {
				value = achievement.getInt("value");
				break;
			}
		}
		return value;
	}

	/**
	 * Helper method to perform HTTP requests with retry logic
	 * 
	 * @param url        The URL to request
	 * @param maxRetries Maximum number of retry attempts
	 * @return HttpResponse or null if all retries failed
	 */
	private HttpResponse<String> performHttpRequestWithRetry(String url, int maxRetries) {
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
				.header("Authorization", "Bearer " + Bot.api_key).header("Accept", "application/json").GET().build();

		int attempt = 0;
		while (attempt <= maxRetries) {
			try {
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

				// If successful (200) or client error (4xx), return immediately (no retry for
				// client errors)
				if (response.statusCode() == 200 || (response.statusCode() >= 400 && response.statusCode() < 500)) {
					return response;
				}

				// For server errors (5xx) or other errors, retry
				if (attempt < maxRetries) {
					long waitTime = (long) Math.pow(2, attempt) * 1000; // Exponential backoff: 1s, 2s, 4s
					System.err.println("Request failed with status " + response.statusCode() + ", retrying in "
							+ waitTime + "ms (attempt " + (attempt + 1) + "/" + maxRetries + ")");
					Thread.sleep(waitTime);
				}
			} catch (IOException | InterruptedException e) {
				if (attempt < maxRetries) {
					long waitTime = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
					System.err.println("Request failed with exception: " + e.getMessage() + ", retrying in " + waitTime
							+ "ms (attempt " + (attempt + 1) + "/" + maxRetries + ")");
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

	public String getJson() {
		// Check if tag is null before encoding
		if (tag == null) {
			System.err.println("Player tag is null, cannot retrieve player data");
			return null;
		}

		// URL-kodieren des Spieler-Tags (# -> %23)
		String encodedTag = java.net.URLEncoder.encode(tag, java.nio.charset.StandardCharsets.UTF_8);
		String url = "https://api.clashofclans.com/v1/players/" + encodedTag;

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
