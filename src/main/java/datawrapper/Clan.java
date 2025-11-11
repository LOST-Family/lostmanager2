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
	private ArrayList<Player> cwlmemberlist;
	private Integer cwlday;

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
			JSONObject jsonobject = new JSONObject(APIUtil.getClanJson(clan_tag));
			nameapi = jsonobject.getString("name");
		}
		return nameapi;
	}

	// Playerlists

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
				String endTime = jsonObject.getString("endTime");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
						.withZone(ZoneOffset.UTC);
				Instant instant = Instant.from(formatter.parse(endTime));
				CWLDayEndTimeMillis = instant.toEpochMilli();
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
			String endTime = currentitem.getString("endTime");
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
					.withZone(ZoneOffset.UTC);
			Instant instant = Instant.from(formatter.parse(endTime));
			RaidEndTimeMillis = instant.toEpochMilli();
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

	public Boolean isCWActive() {
		if (cwactive == null) {
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
			if (state.equalsIgnoreCase("notInWar")) {
				cwactive = false;
			} else {
				cwactive = true;
			}

			if (cwactive) {
				// CW Endtime logic here to prevent double api request if in same result
				String endTime = jsonObject.getString("endTime");
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
						.withZone(ZoneOffset.UTC);
				Instant instant = Instant.from(formatter.parse(endTime));
				CWEndTimeMillis = instant.toEpochMilli();
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

	private int getCWLDayActive() {
		if (cwlday == null) {
			if (isCWLActive()) {
				JSONObject jsonObject = getCWLJson();
				JSONArray round = jsonObject.getJSONArray("rounds");
				for (int i = 0; i < round.length(); i++) {
					JSONArray warTags = round.getJSONObject(i).getJSONArray("warTags");
					for (int j = 0; j < warTags.length(); j++) {
						String warTag = warTags.getString(j);
						JSONObject warDayJson = getCWLDayJson(warTag);
						// todo
					}
				}
			}
		}
		return cwlday;
	}

	public JSONObject getCWLJson() {
		String json;

		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar/leaguegroup";

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

	public JSONObject getCWJson() {
		String json;

		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/currentwar";

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

	private JSONObject getRaidJson() {
		String json;

		String encodedTag = java.net.URLEncoder.encode(clan_tag, java.nio.charset.StandardCharsets.UTF_8);

		String url = "https://api.clashofclans.com/v1/clans/" + encodedTag + "/capitalraidseasons?limit=1";

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

	/*
	 * { "state": "ended", "season": "2025-11", "clans": [ { "tag": "#2L8RC2RL8",
	 * "name": "Davidnai", "clanLevel": 13, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/VPDlJzxLI73MhWabpS2LpP3QOfapwf1XDfhzQ405uyw.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/VPDlJzxLI73MhWabpS2LpP3QOfapwf1XDfhzQ405uyw.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/VPDlJzxLI73MhWabpS2LpP3QOfapwf1XDfhzQ405uyw.png"
	 * }, "members": [ { "tag": "#20LG0LU98", "name": "DAVIDnai31", "townHallLevel":
	 * 17 }, { "tag": "#9CGPP9PUU", "name": "naidavid15", "townHallLevel": 15 }, {
	 * "tag": "#GYRJV8R0", "name": "Oscar9XDC;)", "townHallLevel": 12 }, { "tag":
	 * "#V89L2JCL", "name": "GOD'S ⚡️ david", "townHallLevel": 17 }, { "tag":
	 * "#8088QUUC2", "name": "GOD'S ROBIN", "townHallLevel": 17 }, { "tag":
	 * "#2UUUP8JQ9", "name": "DAVID❤️NAIARA", "townHallLevel": 17 }, { "tag":
	 * "#8QQYLQP2G", "name": "♊Nightmare", "townHallLevel": 17 }, { "tag":
	 * "#QV90L9988", "name": "طابيثا❤️", "townHallLevel": 9 }, { "tag":
	 * "#GJVVQ9V2G", "name": "hogorx", "townHallLevel": 12 }, { "tag": "#Y99VRVL",
	 * "name": "Pablo", "townHallLevel": 17 }, { "tag": "#P8J2GU8PG", "name":
	 * "Houdini Furiano", "townHallLevel": 11 }, { "tag": "#GGC2JG8Y", "name":
	 * "ekainer", "townHallLevel": 17 }, { "tag": "#8G8YLQQRP", "name": "MACBORA 4",
	 * "townHallLevel": 13 }, { "tag": "#RLV9V08V", "name": "Houdini",
	 * "townHallLevel": 14 }, { "tag": "#9YJCRVGC", "name": "GOD’S⚡️MACBORA",
	 * "townHallLevel": 17 }, { "tag": "#QCJ88VQGR", "name": "GOD'S ⚡DAVIDNAI",
	 * "townHallLevel": 13 }, { "tag": "#9QG82UYLU", "name": "GOD'S ⚡DAVIDNAI",
	 * "townHallLevel": 17 }, { "tag": "#PLVQ280YQ", "name": "undertaker14",
	 * "townHallLevel": 12 }, { "tag": "#Q82UG22G8", "name": "davidnai",
	 * "townHallLevel": 12 }, { "tag": "#8PPYLCYV8", "name": "Arnau2003",
	 * "townHallLevel": 11 }, { "tag": "#28QCR0CCU", "name": "Kasauski",
	 * "townHallLevel": 17 }, { "tag": "#22RLLULRV", "name": ".:ℹⓂMORT∆L:.⚪Ax",
	 * "townHallLevel": 17 }, { "tag": "#U8Q0G9GJ", "name": "GOD’S⚡️davidnai",
	 * "townHallLevel": 17 }, { "tag": "#2R82RGJ9J", "name": "GOD’S⚡️DAVID",
	 * "townHallLevel": 17 }, { "tag": "#29QPJRYL2", "name": "davidnai88",
	 * "townHallLevel": 14 }, { "tag": "#Q28QP0GC2", "name": "GOD'S⚡️BlkDevil",
	 * "townHallLevel": 17 }, { "tag": "#22R8GUYVL", "name": "GOD’S⚡️HOUDINI",
	 * "townHallLevel": 17 }, { "tag": "#J8PUG0CG", "name": "GOD’S⚡️DaViDnAi",
	 * "townHallLevel": 17 }, { "tag": "#2GC9022", "name": "halcon verde jr",
	 * "townHallLevel": 17 }, { "tag": "#2JQRRPG", "name": "GOD'S⚡Usul",
	 * "townHallLevel": 17 }, { "tag": "#PCP8Y0C", "name": "terminator",
	 * "townHallLevel": 17 } ] }, { "tag": "#2JGLG8R0J", "name": "Sarzamin Pars 2",
	 * "clanLevel": 6, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/HPNPlqWH5eFgoH_Mdt-ljAK0Z20O0etEqf-FG7R9sAE.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/HPNPlqWH5eFgoH_Mdt-ljAK0Z20O0etEqf-FG7R9sAE.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/HPNPlqWH5eFgoH_Mdt-ljAK0Z20O0etEqf-FG7R9sAE.png"
	 * }, "members": [ { "tag": "#PJYL8UPVV", "name": "hamid008", "townHallLevel":
	 * 17 }, { "tag": "#Q2G8VUPG0", "name": "majid", "townHallLevel": 17 }, { "tag":
	 * "#QLVR0LR0Y", "name": "king", "townHallLevel": 12 }, { "tag": "#8Q9CJQQYP",
	 * "name": "..Richard..", "townHallLevel": 17 }, { "tag": "#P8P92R9J0", "name":
	 * "hamid", "townHallLevel": 17 }, { "tag": "#2Y0G2RLGP", "name":
	 * "ram.holy king", "townHallLevel": 17 }, { "tag": "#GPCQ9J8Q", "name":
	 * "marjan", "townHallLevel": 17 }, { "tag": "#L9C82VL0C", "name": "danial rre",
	 * "townHallLevel": 17 }, { "tag": "#Q89CRVGVJ", "name": "⚽Mohammad.M⚽",
	 * "townHallLevel": 17 }, { "tag": "#LU0VRJP8", "name": "fiseki",
	 * "townHallLevel": 17 }, { "tag": "#UJ2QPQ2V", "name": "Pezhman",
	 * "townHallLevel": 17 }, { "tag": "#28J9VPRYU", "name": "farnaz",
	 * "townHallLevel": 17 }, { "tag": "#YUCCYPVRY", "name": "⭐️little angel⭐",
	 * "townHallLevel": 17 }, { "tag": "#9P0RCVPP", "name": "pouriya",
	 * "townHallLevel": 17 }, { "tag": "#2G0U2VUP8", "name": "amir hossein",
	 * "townHallLevel": 17 }, { "tag": "#RP8LYCG0", "name": "Pasha Dynasty",
	 * "townHallLevel": 17 }, { "tag": "#82U8LUY9Q", "name": "LaDy Naz♥️",
	 * "townHallLevel": 17 }, { "tag": "#2GL9L9YV2", "name": "Pasha Balling",
	 * "townHallLevel": 17 }, { "tag": "#2Y8GPPQUR", "name": "Alireza",
	 * "townHallLevel": 17 } ] }, { "tag": "#2GPP0GLYQ", "name": "LOST 3 CWL 2",
	 * "clanLevel": 11, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/TbdsNWqRlgUY2H_Nt75IjjriTt7-1btB7oHMGXF93vI.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/TbdsNWqRlgUY2H_Nt75IjjriTt7-1btB7oHMGXF93vI.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/TbdsNWqRlgUY2H_Nt75IjjriTt7-1btB7oHMGXF93vI.png"
	 * }, "members": [ { "tag": "#9LVYU2UVP", "name": "Luis", "townHallLevel": 17 },
	 * { "tag": "#G89C8C9YG", "name": "Caveira Mini", "townHallLevel": 3 }, { "tag":
	 * "#2C9LUYLJG", "name": "felix.", "townHallLevel": 17 }, { "tag": "#GC9RU0U9J",
	 * "name": "NUK3 I one", "townHallLevel": 3 }, { "tag": "#LRPR890U", "name":
	 * "⚡Samu⚡", "townHallLevel": 17 }, { "tag": "#RCRGV00J", "name":
	 * "Shadow Queen", "townHallLevel": 17 }, { "tag": "#QVVV0LGUR", "name":
	 * "NUK3 I RusH", "townHallLevel": 9 }, { "tag": "#CQQJUUQY", "name":
	 * "SFC25~GReeeN", "townHallLevel": 17 }, { "tag": "#LY9JVUCGU", "name":
	 * "Lenni™️", "townHallLevel": 12 }, { "tag": "#20GL8U008", "name": "Julius",
	 * "townHallLevel": 17 }, { "tag": "#28998CLY", "name": "Johann",
	 * "townHallLevel": 17 }, { "tag": "#98YVRRV0", "name": "Luca", "townHallLevel":
	 * 17 }, { "tag": "#G92VURGJV", "name": "Rüdi 21", "townHallLevel": 6 }, {
	 * "tag": "#2C20VRYPY", "name": "qwertzuiopasdfg", "townHallLevel": 17 }, {
	 * "tag": "#2V20Q0R8", "name": "罪 Edo", "townHallLevel": 17 }, { "tag":
	 * "#QRJ82JLQV", "name": "mobil und so 3", "townHallLevel": 3 }, { "tag":
	 * "#2JYPLQ08G", "name": "Maurice0306", "townHallLevel": 17 }, { "tag":
	 * "#8G0PY09Q", "name": "Oshy44", "townHallLevel": 17 }, { "tag": "#QLU20UCQR",
	 * "name": "SPICE", "townHallLevel": 17 }, { "tag": "#QQV2RJ8LL", "name":
	 * "NUK3 F2P", "townHallLevel": 15 }, { "tag": "#QL9RQCLG", "name": "mrcompi",
	 * "townHallLevel": 17 }, { "tag": "#208GUP9JR", "name": "Keksi",
	 * "townHallLevel": 17 }, { "tag": "#22CGQ9P8R", "name": "Loki",
	 * "townHallLevel": 17 }, { "tag": "#YUVQ2GCQY", "name": "MasterZyn!!",
	 * "townHallLevel": 17 }, { "tag": "#L89YGR9VP", "name": "Florian",
	 * "townHallLevel": 17 } ] }, { "tag": "#2QC0QQPQ2", "name": "LOST 5",
	 * "clanLevel": 25, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/D7F_1t5NblbtgjxOZ2FGSfnIQ0LOymuKopvztuAwiz0.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/D7F_1t5NblbtgjxOZ2FGSfnIQ0LOymuKopvztuAwiz0.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/D7F_1t5NblbtgjxOZ2FGSfnIQ0LOymuKopvztuAwiz0.png"
	 * }, "members": [ { "tag": "#20UUQJR09", "name": "Lost yanwey",
	 * "townHallLevel": 17 }, { "tag": "#9G0PR9Y0C", "name": "HAWAI",
	 * "townHallLevel": 17 }, { "tag": "#QPVUC80PG", "name": "SuperTrip",
	 * "townHallLevel": 17 }, { "tag": "#9GR8QQVCC", "name": "HAWAIII",
	 * "townHallLevel": 17 }, { "tag": "#282CYYQ9L", "name": "Zero Killer",
	 * "townHallLevel": 17 }, { "tag": "#LR22LGUYL", "name": "Lukas",
	 * "townHallLevel": 17 }, { "tag": "#LGCVC909V", "name": "•Moin Meister•",
	 * "townHallLevel": 17 }, { "tag": "#8V9GCJP8", "name": "Phillip-0810",
	 * "townHallLevel": 17 }, { "tag": "#82CPYUPQ", "name": "jonny_jonson",
	 * "townHallLevel": 17 }, { "tag": "#L0Y9RP202", "name": "Da Tim",
	 * "townHallLevel": 17 }, { "tag": "#LGJJ9JC89", "name": "Melon Musk",
	 * "townHallLevel": 17 }, { "tag": "#P08VRV88", "name": "Reiter17",
	 * "townHallLevel": 17 }, { "tag": "#YU9YR0L0U", "name": "999", "townHallLevel":
	 * 17 }, { "tag": "#9GVQRP2CC", "name": "crAnii24", "townHallLevel": 17 }, {
	 * "tag": "#2JCCQYY00", "name": "Sebaspielt", "townHallLevel": 17 }, { "tag":
	 * "#PQ89282Q", "name": "Noa", "townHallLevel": 17 } ] }, { "tag": "#RYVQPVP",
	 * "name": "X-LORDS", "clanLevel": 19, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/tDYKlsqrSPRlpl4NUwYuNpAhMg69X7oCnHlnEXXQqAM.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/tDYKlsqrSPRlpl4NUwYuNpAhMg69X7oCnHlnEXXQqAM.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/tDYKlsqrSPRlpl4NUwYuNpAhMg69X7oCnHlnEXXQqAM.png"
	 * }, "members": [ { "tag": "#PJ2UYQYG2", "name": "pizza baba", "townHallLevel":
	 * 17 }, { "tag": "#8PY0RLV90", "name": "Wokekos", "townHallLevel": 17 }, {
	 * "tag": "#QQVLQ000", "name": "Ome Strik", "townHallLevel": 17 }, { "tag":
	 * "#PUQ92LQ0", "name": "TW Kingkai", "townHallLevel": 17 }, { "tag":
	 * "#Y8GJG9GQ", "name": "Lord Daal", "townHallLevel": 17 }, { "tag":
	 * "#9LGLCQJQL", "name": "RD Flip", "townHallLevel": 17 }, { "tag": "#R0GGPC",
	 * "name": "™️kingVT™️", "townHallLevel": 17 }, { "tag": "#8J8CPRYC", "name":
	 * "Jaylirick", "townHallLevel": 17 }, { "tag": "#9JU2QYJG", "name": "luc",
	 * "townHallLevel": 17 }, { "tag": "#LCGVUGUU2", "name": "zeepkast",
	 * "townHallLevel": 17 }, { "tag": "#PGLYY8JG", "name": "TW Gold",
	 * "townHallLevel": 17 }, { "tag": "#Y9CLLRJYU", "name": "mengos",
	 * "townHallLevel": 17 }, { "tag": "#GPRV2L28", "name": "凌云 ᴢᴏʜᴀɪʙ",
	 * "townHallLevel": 17 }, { "tag": "#9RGG9GC9C", "name": "~•●DuTcHy™●•~",
	 * "townHallLevel": 17 }, { "tag": "#GGUQVVGU0", "name": "Mini 1",
	 * "townHallLevel": 6 }, { "tag": "#8R9PPGRCU", "name": "De Kale Arend",
	 * "townHallLevel": 17 }, { "tag": "#8V880L8QU", "name": "NIZAR 1.0",
	 * "townHallLevel": 17 } ] }, { "tag": "#YG28RVLJ", "name": "天香之国", "clanLevel":
	 * 25, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/cIHsLbIbK7l7qC5wkCTdHafGiaJYuTeOjTvv6DRBq1c.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/cIHsLbIbK7l7qC5wkCTdHafGiaJYuTeOjTvv6DRBq1c.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/cIHsLbIbK7l7qC5wkCTdHafGiaJYuTeOjTvv6DRBq1c.png"
	 * }, "members": [ { "tag": "#PRJCVLYCY", "name": "ســـجاد الجبوري",
	 * "townHallLevel": 17 }, { "tag": "#802JV992P", "name": "Saif H.A",
	 * "townHallLevel": 17 }, { "tag": "#PUCGVPPVG", "name": "☹جميلة يوليو♕❤️",
	 * "townHallLevel": 17 }, { "tag": "#9PVYGQ9UQ", "name": "آدريـنالـيـن",
	 * "townHallLevel": 17 }, { "tag": "#CLP9GPRR", "name": "IQ丨‎RAGNAR",
	 * "townHallLevel": 17 }, { "tag": "#QQ8Y2GG0L", "name": "محمد",
	 * "townHallLevel": 17 }, { "tag": "#90V8RCRVR", "name": "Abdallah t sh",
	 * "townHallLevel": 17 }, { "tag": "#9CJ8LPYYC", "name": "Hasan MDALLAL",
	 * "townHallLevel": 17 }, { "tag": "#2PYGL92C2", "name": "♕『MK』♕شگاگي↑",
	 * "townHallLevel": 17 }, { "tag": "#JUPV89PV", "name": "KURDO",
	 * "townHallLevel": 17 }, { "tag": "#90CJ090J2", "name": "AZAM IL.D",
	 * "townHallLevel": 17 }, { "tag": "#8RGLRJGC", "name": "مـتـﮧوﭑاضـع،♔♪",
	 * "townHallLevel": 17 }, { "tag": "#LVVCVP8C2", "name": "IQ | NAWAR",
	 * "townHallLevel": 17 }, { "tag": "#8U9ULRU0R", "name": "ḞĊ Ḅäŕċệŀöńệ",
	 * "townHallLevel": 17 }, { "tag": "#LVJ90LLR9", "name": "سيادة",
	 * "townHallLevel": 17 }, { "tag": "#9QRRLPCV9", "name": "IQ | FARHAN",
	 * "townHallLevel": 17 }, { "tag": "#8Y0G8PJ80", "name": "IQ | THE HUNTER",
	 * "townHallLevel": 17 }, { "tag": "#CQV8PQ9P", "name": "Salem Aluzbky",
	 * "townHallLevel": 17 }, { "tag": "#JQJLJ2G9", "name": "أبـَـوهـِـزاعِ",
	 * "townHallLevel": 17 }, { "tag": "#82Y089YYU", "name": "『★』escanor『★』",
	 * "townHallLevel": 17 }, { "tag": "#8L0Q208JU", "name": "لـيـثـ T1",
	 * "townHallLevel": 17 } ] }, { "tag": "#P2VY8Y8V", "name": "Bulgaria",
	 * "clanLevel": 27, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/N-GwGMWeDAdb0nBtYS_dTE06ULLvoT-kUSArdQM0gC0.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/N-GwGMWeDAdb0nBtYS_dTE06ULLvoT-kUSArdQM0gC0.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/N-GwGMWeDAdb0nBtYS_dTE06ULLvoT-kUSArdQM0gC0.png"
	 * }, "members": [ { "tag": "#29C9PVJ00", "name": "draw v. 4568",
	 * "townHallLevel": 17 }, { "tag": "#YJCLRPJ8", "name": "E Sa She Spra",
	 * "townHallLevel": 16 }, { "tag": "#YP0ULGYUC", "name": "Garlic'sW⚜️681⚜",
	 * "townHallLevel": 17 }, { "tag": "#2CV8UL9Y8", "name": "ﾒ[ｲ]尺乇ᄊ乇",
	 * "townHallLevel": 17 }, { "tag": "#Y8P99PGY9", "name": "Garlic⚜️681⚜️",
	 * "townHallLevel": 17 }, { "tag": "#9R00U9CYU", "name": "Gen4o1312⚜️681⚜",
	 * "townHallLevel": 17 }, { "tag": "#PGRJJ2280", "name": "Lebow",
	 * "townHallLevel": 17 }, { "tag": "#2QCJCL8P", "name": "dani⚜️681⚜️",
	 * "townHallLevel": 17 }, { "tag": "#28UCQVQGV", "name": "•Aдолфа•⚜️681⚜️",
	 * "townHallLevel": 17 }, { "tag": "#LRJPYGC9V", "name": "Вождь",
	 * "townHallLevel": 17 }, { "tag": "#U08PJQJ8", "name": "neymar",
	 * "townHallLevel": 15 }, { "tag": "#9CLYPQPL", "name": "stasLosh⚜️681⚜️",
	 * "townHallLevel": 17 }, { "tag": "#2Y80VQCVL", "name": "Костов",
	 * "townHallLevel": 17 }, { "tag": "#2YCCV2QQ8", "name": "< УШЕВ >",
	 * "townHallLevel": 17 }, { "tag": "#GG2G0JLYG", "name": "paolofx",
	 * "townHallLevel": 9 }, { "tag": "#9Q8PVP8GR", "name": "FatdogTsvetko",
	 * "townHallLevel": 17 }, { "tag": "#80G9JJ9GY", "name": "Drisko⚜️681⚜️",
	 * "townHallLevel": 17 }, { "tag": "#8YQ2CVYLG", "name": "Simo",
	 * "townHallLevel": 17 }, { "tag": "#2PGRC0C2J", "name": "♣️♥️ÅÑGÈL♦️♠️",
	 * "townHallLevel": 17 }, { "tag": "#GCGC8L9Q8", "name": "Nаrсоs⚜️681⚜️",
	 * "townHallLevel": 16 }, { "tag": "#J0QQVP8U", "name": "BoogeyMan⚜️681⚜",
	 * "townHallLevel": 17 }, { "tag": "#80QV09JY2", "name": "•●●$♡DrOgA♡$●●•",
	 * "townHallLevel": 17 } ] }, { "tag": "#2820UPPQC", "name": "LOST F2P",
	 * "clanLevel": 29, "badgeUrls": { "small":
	 * "https://api-assets.clashofclans.com/badges/70/OS9UujsDabcWl1g8qHqeyxaBhT0Qiv-ObQ9uKR81-go.png",
	 * "large":
	 * "https://api-assets.clashofclans.com/badges/512/OS9UujsDabcWl1g8qHqeyxaBhT0Qiv-ObQ9uKR81-go.png",
	 * "medium":
	 * "https://api-assets.clashofclans.com/badges/200/OS9UujsDabcWl1g8qHqeyxaBhT0Qiv-ObQ9uKR81-go.png"
	 * }, "members": [ { "tag": "#LYUY220G0", "name": "Der Tsunami F2P",
	 * "townHallLevel": 17 }, { "tag": "#QCR2QQQUR", "name": "SiMoon2",
	 * "townHallLevel": 17 }, { "tag": "#20C20VCCJ", "name": "Helmut der 2.",
	 * "townHallLevel": 17 }, { "tag": "#VGCGUUUP", "name": "Spider",
	 * "townHallLevel": 17 }, { "tag": "#Y2PYVJJJ", "name": "Virus",
	 * "townHallLevel": 17 }, { "tag": "#LGQQLVPG9", "name": "JJ F2P",
	 * "townHallLevel": 17 }, { "tag": "#GCGR0U2GR", "name": "dikholi",
	 * "townHallLevel": 11 }, { "tag": "#LGPQUVV02", "name": "LostBen",
	 * "townHallLevel": 17 }, { "tag": "#2GPGQUVYC", "name": "Floid",
	 * "townHallLevel": 17 }, { "tag": "#L8CJQ8R2G", "name": "Nds.li",
	 * "townHallLevel": 17 }, { "tag": "#LJ90YPYYQ", "name": "Dikhol",
	 * "townHallLevel": 17 }, { "tag": "#8J0UPPC0V", "name": "Evolution",
	 * "townHallLevel": 13 }, { "tag": "#9YG2C2RLJ", "name": "Phönix",
	 * "townHallLevel": 17 }, { "tag": "#LRLRYVQ9Y", "name": "Jonas4",
	 * "townHallLevel": 17 }, { "tag": "#9C0RGRUU", "name": "Pixel",
	 * "townHallLevel": 17 }, { "tag": "#YRCP08CCQ", "name": "Ultimat F2P",
	 * "townHallLevel": 17 }, { "tag": "#LGJC0PYP9", "name": "Kampfhamster",
	 * "townHallLevel": 17 }, { "tag": "#P9PQYPJVU", "name": "Fabian",
	 * "townHallLevel": 17 }, { "tag": "#GL9Y8G29", "name": "Trymacs_Youtube",
	 * "townHallLevel": 17 }, { "tag": "#80JY0Q909", "name": "#Bombig",
	 * "townHallLevel": 17 }, { "tag": "#L8UVJGRJQ", "name": "Berkan F2P",
	 * "townHallLevel": 17 }, { "tag": "#LLQ80R2G0", "name": "Florian",
	 * "townHallLevel": 17 }, { "tag": "#RJVVL9PU", "name": "Luca280404",
	 * "townHallLevel": 17 } ] } ], "rounds": [ { "warTags": [ "#8Q8G2G90V",
	 * "#8Q8G2GLVC", "#8Q8G2GPY9", "#8Q8G2GYRQ" ] }, { "warTags": [ "#8Q8J2YQQ9",
	 * "#8Q8J2YJ2C", "#8Q8J2YGCQ", "#8Q8J2YCL2" ] }, { "warTags": [ "#8Q8URLL9Q",
	 * "#8Q8URPVPV", "#8Q8URLJ8Y", "#8Q8URY0R9" ] }, { "warTags": [ "#8Q922VUG2",
	 * "#8Q928028R", "#8Q92809C9", "#8Q9280Y2Q" ] }, { "warTags": [ "#8Q99RJC82",
	 * "#8Q99RR2GV", "#8Q99RRP9Q", "#8Q99RR8V9" ] }, { "warTags": [ "#8Q9L8R8RC",
	 * "#8Q9L8RV82", "#8Q9L8RP02", "#8Q9L8RYPY" ] }, { "warTags": [ "#8Q9GRJCVQ",
	 * "#8Q9GRJV9C", "#8Q9GRU9QC", "#8Q9GRC98R" ] } ] }
	 */
}
