package datawrapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import org.json.JSONObject;

import datautil.APIUtil;
import datautil.Connection;
import datautil.DBUtil;
import lostmanager.Bot;

public class Player {

	public enum RoleType {
		ADMIN, LEADER, COLEADER, ELDER, MEMBER, NOTINCLAN
	};

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
	private RoleType role;

	public Player(String tag) {
		this.tag = tag;
	}

	// sets all Data except tag to null for reloading with getters

	public Player refreshData() {
		warpreference = null;
		namedb = null;
		nameapi = null;
		user = null;
		clandb = null;
		clanapi = null;
		kickpoints = null;
		kickpointstotal = null;
		role = null;
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

	public Player setRole(RoleType role) {
		this.role = role;
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
			URL url = new URL("https://api.clashofclans.com/v1/players/" + encodedTag);

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
			URL url = new URL("https://api.clashofclans.com/v1/players/" + encodedTag + "/verifytoken");

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
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
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
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
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
			JSONObject jsonObject = new JSONObject(APIUtil.getPlayerJson(tag));
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

	public RoleType getRole() {
		if (role == null) {
			if (new Player(tag).getClanDB() == null) {
				return RoleType.NOTINCLAN;
			}
			String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
			try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
				pstmt.setString(1, tag);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						String rolestring = rs.getString("clan_role");
						role = rolestring.equals("leader") ? RoleType.LEADER
								: rolestring.equals("coLeader") ? RoleType.COLEADER
										: rolestring.equals("admin") ? RoleType.ELDER
												: rolestring.equals("member") ? RoleType.MEMBER : null;
					}
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return role;
	}
}
