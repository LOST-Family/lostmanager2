package lostmanager.webserver.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import lostmanager.Bot;
import lostmanager.datawrapper.AchievementData;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Kickpoint;
import lostmanager.datawrapper.KickpointReason;
import lostmanager.datawrapper.ListeningEvent;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.ListeningEventService;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public class ManagementApiHandler implements HttpHandler {

	private String apiToken;
	private final boolean allowNoAuth;
	private final String allowedOrigin;

	public ManagementApiHandler(String apiToken) {
		this.apiToken = apiToken;
		this.allowNoAuth = "true".equalsIgnoreCase(System.getenv("REST_API_ALLOW_NO_AUTH"));
		String origin = System.getenv("REST_API_ALLOWED_ORIGIN");
		this.allowedOrigin = (origin == null || origin.isEmpty()) ? "*" : origin;
	}

	@Override
	public void handle(HttpExchange exchange) throws IOException {
		if ("OPTIONS".equals(exchange.getRequestMethod())) {
			addCorsHeaders(exchange);
			exchange.sendResponseHeaders(204, -1);
			return;
		}

		if (!"POST".equals(exchange.getRequestMethod())) {
			sendResponse(exchange, 405, new JSONObject().put("error", "Method Not Allowed").toString());
			return;
		}

		if (!validateApiToken(exchange)) {
			sendResponse(exchange, 401,
					new JSONObject().put("error", "Unauthorized - Invalid or missing API token").toString());
			return;
		}

		try {
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			JSONObject json = new JSONObject(body);

			String path = exchange.getRequestURI().getPath();
			String subPath = path.replaceFirst("^/api/manage/?", "");

			JSONObject response;

			switch (subPath) {
				case "members/add":
					response = handleAddMember(json);
					break;
				case "members/edit":
					response = handleEditMember(json);
					break;
				case "members/remove":
					response = handleRemoveMember(json);
					break;
				case "kickpoints/add":
					response = handleKickpointAdd(json);
					break;
				case "kickpoints/edit":
					response = handleKickpointEdit(json);
					break;
				case "kickpoints/remove":
					response = handleKickpointRemove(json);
					break;
				case "clanconfig":
					response = handleClanconfig(json);
					break;
				case "kickpoint-reasons/add":
					response = handleKickpointReasonAdd(json);
					break;
				case "kickpoint-reasons/edit":
					response = handleKickpointReasonEdit(json);
					break;
				case "kickpoint-reasons/remove":
					response = handleKickpointReasonRemove(json);
					break;
				case "links/link":
					response = handleLink(json);
					break;
				case "links/relink":
					response = handleRelink(json);
					break;
				case "links/unlink":
					response = handleUnlink(json);
					break;
				case "listeningevents/add":
					response = handleListeningEventAdd(json);
					break;
				case "listeningevents/edit":
					response = handleListeningEventEdit(json);
					break;
				case "listeningevents/remove":
					response = handleListeningEventRemove(json);
					break;
				case "restart":
					response = handleRestart(json);
					break;
				default:
					sendResponse(exchange, 404, new JSONObject().put("error", "Unknown endpoint").toString());
					return;
			}

			int status = response.optBoolean("success", false) ? 200 : response.optInt("statusCode", 400);
			response.remove("statusCode");
			sendResponse(exchange, status, response.toString());

		} catch (org.json.JSONException e) {
			sendResponse(exchange, 400, new JSONObject().put("error", "Invalid JSON body").toString());
		} catch (Exception e) {
			handleException(exchange, e);
		}
	}

	// ==================== Member Management ====================

	@SuppressWarnings("null")
	private JSONObject handleAddMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String clanTag = json.optString("clanTag", null);
		String role = json.optString("role", null);

		if (discordUserId == null || playerTag == null || clanTag == null || role == null) {
			return error("Missing required fields: discordUserId, playerTag, clanTag, role", 400);
		}

		Clan c = new Clan(clanTag);
		if (!c.ExistsDB()) {
			return error("Clan not found", 404);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		if (!(role.equals("leader") || role.equals("coLeader") || role.equals("hiddencoleader")
				|| role.equals("admin") || role.equals("member"))) {
			return error("Invalid role. Valid: leader, coLeader, hiddencoleader, admin, member", 400);
		}
		if (role.equals("leader") && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to assign leader role", 403);
		}
		if (role.equals("coLeader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to assign coleader role", 403);
		}
		if (role.equals("hiddencoleader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to assign hidden coleader role", 403);
		}

		Player p = new Player(playerTag);
		if (!p.IsLinked()) {
			return error("Player is not linked", 404);
		}
		if (p.getClanDB() != null) {
			return error("Player is already in a clan", 400);
		}

		DBUtil.executeUpdate(
				"INSERT INTO clan_members (player_tag, clan_tag, clan_role, joined_at) VALUES (?, ?, ?, NOW())",
				playerTag, clanTag, role);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player added to clan");
		JSONArray roleChanges = new JSONArray();

		String userid = p.getUser().getUserID();
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		if (guild != null) {
			Member member = guild.getMemberById(userid);
			if (member != null) {
				String memberroleid = c.getRoleID(Clan.Role.MEMBER);
				Role memberrole = memberroleid != null ? guild.getRoleById(memberroleid) : null;
				String elderroleid = c.getRoleID(Clan.Role.ELDER);
				Role elderrole = elderroleid != null ? guild.getRoleById(elderroleid) : null;

				if (!role.equals("hiddencoleader") && memberrole != null
						&& !member.getRoles().contains(memberrole)) {
					guild.addRoleToMember(member, memberrole).queue();
					roleChanges.put(new JSONObject().put("type", "added").put("roleId", memberroleid)
							.put("userId", userid));
				}
				if (Player.isElderOrHigherString(role) && elderrole != null
						&& !member.getRoles().contains(elderrole)) {
					guild.addRoleToMember(member, elderrole).queue();
					roleChanges.put(new JSONObject().put("type", "added").put("roleId", elderroleid)
							.put("userId", userid));
				}
			}
		}

		response.put("roleChanges", roleChanges);
		return response;
	}

	@SuppressWarnings("null")
	private JSONObject handleEditMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String role = json.optString("role", null);

		if (discordUserId == null || playerTag == null || role == null) {
			return error("Missing required fields: discordUserId, playerTag, role", 400);
		}

		Player p = new Player(playerTag);
		if (!p.IsLinked()) {
			return error("Player is not linked", 404);
		}

		Clan c = p.getClanDB();
		if (c == null) {
			return error("Player is not in a clan", 400);
		}

		String clanTag = c.getTag();
		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		if (!(role.equals("leader") || role.equals("coLeader") || role.equals("hiddencoleader")
				|| role.equals("admin") || role.equals("member"))) {
			return error("Invalid role. Valid: leader, coLeader, hiddencoleader, admin, member", 400);
		}
		if (role.equals("leader") && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to assign leader role", 403);
		}
		if (role.equals("coLeader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to assign coleader role", 403);
		}
		if (role.equals("hiddencoleader") && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to assign hidden coleader role", 403);
		}

		// Get old role
		String oldRole = null;
		String sql = "SELECT clan_role FROM clan_members WHERE player_tag = ?";
		try (PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, playerTag);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					oldRole = rs.getString("clan_role");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		// Check permission to modify current role
		Player.RoleType oldRoleType = oldRole != null && oldRole.equals("leader") ? Player.RoleType.LEADER
				: oldRole != null && (oldRole.equals("coLeader") || oldRole.equals("hiddencoleader"))
						? Player.RoleType.COLEADER
						: oldRole != null && oldRole.equals("admin") ? Player.RoleType.ELDER
								: oldRole != null && oldRole.equals("member") ? Player.RoleType.MEMBER : null;

		if (oldRoleType == Player.RoleType.LEADER
				&& user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to modify a leader", 403);
		}
		if (oldRoleType == Player.RoleType.COLEADER
				&& !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
						|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to modify a coleader", 403);
		}

		DBUtil.executeUpdate("UPDATE clan_members SET clan_role = ? WHERE player_tag = ?", role, playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Member role updated");
		JSONArray roleChanges = new JSONArray();

		String userid = p.getUser().getUserID();
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		if (guild != null) {
			Member member = guild.getMemberById(userid);
			if (member != null) {
				String elderroleid = c.getRoleID(Clan.Role.ELDER);
				Role elderrole = elderroleid != null ? guild.getRoleById(elderroleid) : null;

				boolean wasElderOrHigher = Player.isElderOrHigherString(oldRole);
				boolean isNowElderOrHigher = Player.isElderOrHigherString(role);

				if (elderrole != null) {
					if (!wasElderOrHigher && isNowElderOrHigher) {
						if (!member.getRoles().contains(elderrole)) {
							guild.addRoleToMember(member, elderrole).queue();
							roleChanges.put(new JSONObject().put("type", "added")
									.put("roleId", elderroleid).put("userId", userid));
						}
					} else if (wasElderOrHigher && !isNowElderOrHigher) {
						ArrayList<Player> allaccs = p.getUser().getAllLinkedAccounts();
						boolean otherElderOrHigherSameClan = false;
						for (Player acc : allaccs) {
							if (!acc.getTag().equals(playerTag) && acc.getClanDB() != null) {
								if (acc.getClanDB().getTag().equals(clanTag)) {
									if (Player.isElderOrHigher(acc.getRoleDB())
											&& !acc.isHiddenColeader()) {
										otherElderOrHigherSameClan = true;
									}
								}
							}
						}
						if (member.getRoles().contains(elderrole) && !otherElderOrHigherSameClan) {
							guild.removeRoleFromMember(member, elderrole).queue();
							roleChanges.put(new JSONObject().put("type", "removed")
									.put("roleId", elderroleid).put("userId", userid));
						}
					}
				}
			}
		}

		response.put("roleChanges", roleChanges);
		return response;
	}

	@SuppressWarnings("null")
	private JSONObject handleRemoveMember(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);

		if (discordUserId == null || playerTag == null) {
			return error("Missing required fields: discordUserId, playerTag", 400);
		}

		Player player = new Player(playerTag);
		if (!player.IsLinked()) {
			return error("Player is not linked", 404);
		}

		Player.RoleType role = player.getRoleDB();
		Clan playerclan = player.getClanDB();
		if (playerclan == null) {
			return error("Player is not in a clan", 400);
		}

		String clanTag = playerclan.getTag();
		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		if (role == Player.RoleType.LEADER && user.getClanRoles().get(clanTag) != Player.RoleType.ADMIN) {
			return error("Must be admin to remove a leader", 403);
		}
		if (role == Player.RoleType.COLEADER && !(user.getClanRoles().get(clanTag) == Player.RoleType.ADMIN
				|| user.getClanRoles().get(clanTag) == Player.RoleType.LEADER)) {
			return error("Must be admin or leader to remove a coleader", 403);
		}

		DBUtil.executeUpdate("DELETE FROM clan_members WHERE player_tag = ?", playerTag);
		MemberSignoff.remove(playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Member removed from clan");
		JSONArray roleChanges = new JSONArray();

		String userid = player.getUser().getUserID();
		Guild guild = Bot.getJda().getGuildById(Bot.guild_id);
		if (guild != null) {
			Member member = guild.getMemberById(userid);
			if (member != null) {
				ArrayList<Player> allaccs = player.getUser().getAllLinkedAccounts();
				boolean hasOtherAccInClan = false;
				boolean otherElderOrHigherSameClan = false;
				// Whether the user still has any linked account in any clan
				boolean inAnyClan = false;
				for (Player acc : allaccs) {
					if (acc.getClanDB() != null) {
						inAnyClan = true;
						if (acc.getClanDB().getTag().equals(clanTag)) {
							hasOtherAccInClan = true;
							if (Player.isElderOrHigher(acc.getRoleDB()) && !acc.isHiddenColeader()) {
								otherElderOrHigherSameClan = true;
							}
						}
					}
				}

				String memberroleid = playerclan.getRoleID(Clan.Role.MEMBER);
				Role memberrole = memberroleid != null ? guild.getRoleById(memberroleid) : null;
				String elderroleid = playerclan.getRoleID(Clan.Role.ELDER);
				Role elderrole = elderroleid != null ? guild.getRoleById(elderroleid) : null;

				if (memberrole != null && member.getRoles().contains(memberrole) && !hasOtherAccInClan) {
					guild.removeRoleFromMember(member, memberrole).queue();
					roleChanges.put(new JSONObject().put("type", "removed")
							.put("roleId", memberroleid).put("userId", userid));
				}
				if (elderrole != null && member.getRoles().contains(elderrole)
						&& !otherElderOrHigherSameClan) {
					guild.removeRoleFromMember(member, elderrole).queue();
					roleChanges.put(new JSONObject().put("type", "removed")
							.put("roleId", elderroleid).put("userId", userid));
				}

				String exmemberroleid = Bot.exmember_roleid;
				Role exmemberrole = exmemberroleid != null ? guild.getRoleById(exmemberroleid) : null;
				if (exmemberrole != null && !inAnyClan && !member.getRoles().contains(exmemberrole)) {
					guild.addRoleToMember(member, exmemberrole).queue();
					roleChanges.put(new JSONObject().put("type", "added")
							.put("roleId", exmemberroleid).put("userId", userid));
				}
			}
		}

		response.put("roleChanges", roleChanges);
		return response;
	}

	// ==================== Kickpoint Management ====================

	private JSONObject handleKickpointAdd(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String reason = json.optString("reason", null);
		String date = json.optString("date", null);

		if (discordUserId == null || playerTag == null || reason == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, playerTag, reason, amount", 400);
		}
		int amount = json.getInt("amount");

		if (date == null) {
			date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
		}

		Player p = new Player(playerTag);
		Clan c = p.getClanDB();
		if (c == null) {
			return error("Player not found or not in a clan", 404);
		}

		String clanTag = c.getTag();
		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		if (c.getDaysKickpointsExpireAfter() == null || c.getMaxKickpoints() == null) {
			return error("Clan config must be set first (use clanconfig)", 400);
		}

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
		LocalDate localdate;
		try {
			localdate = LocalDate.parse(date, formatter);
		} catch (DateTimeParseException e) {
			return error("Invalid date format. Use dd.MM.yyyy", 400);
		}

		LocalDateTime dateTime = localdate.atStartOfDay();
		ZoneId zone = ZoneId.of("Europe/Berlin");
		ZonedDateTime zonedDateTime = dateTime.atZone(zone);
		Timestamp timestampcreated = Timestamp.from(zonedDateTime.toInstant());
		Timestamp timestampexpires = Timestamp.valueOf(dateTime.plusDays(c.getDaysKickpointsExpireAfter()));
		Timestamp timestampnow = Timestamp.from(Instant.now());

		Tuple<Long, Integer> result = DBUtil.executeUpdate(
				"INSERT INTO kickpoints (player_tag, date, amount, description, created_by_discord_id, created_at, expires_at, clan_tag, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
				playerTag, timestampcreated, amount, reason, discordUserId, timestampnow, timestampexpires,
				c.getTag(), timestampnow);

		if (result == null) {
			return error("Failed to add kickpoint", 500);
		}

		Long id = result.getFirst();

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint added");
		response.put("kickpointId", id);

		JSONArray warnings = new JSONArray();

		MemberSignoff signoff = new MemberSignoff(playerTag);
		if (signoff.isActive()) {
			warnings.put("Player is currently signed off");
		}

		if (timestampexpires.before(Timestamp.from(Instant.now()))) {
			warnings.put("Kickpoint is already expired based on the given date");
		}

		long kptotal = 0;
		for (Kickpoint kp : p.getActiveKickpoints()) {
			kptotal += kp.getAmount();
		}
		if (kptotal >= c.getMaxKickpoints()) {
			warnings.put("Player has reached maximum kickpoints");
		}

		if (warnings.length() > 0) {
			response.put("warnings", warnings);
		}

		return response;
	}

	private JSONObject handleKickpointEdit(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String reason = json.optString("reason", null);
		String date = json.optString("date", null);

		if (discordUserId == null || !json.has("id") || reason == null || date == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, id, reason, amount, date", 400);
		}

		int id = json.getInt("id");
		int amount = json.getInt("amount");

		Kickpoint kp = new Kickpoint(id);
		if (kp.getDescription() == null) {
			return error("Kickpoint not found", 404);
		}

		Player kpPlayer = kp.getPlayer();
		Clan kpClan = kpPlayer != null ? kpPlayer.getClanDB() : null;
		User user = new User(discordUserId);
		if (kpClan == null) {
			if (!user.isAdmin()) {
				return error("Player of this kickpoint is not in a clan - only an admin can modify it", 403);
			}
		} else if (!user.isColeaderOrHigherInClan(kpClan.getTag())) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
		LocalDate localdate;
		try {
			localdate = LocalDate.parse(date, formatter);
		} catch (DateTimeParseException e) {
			return error("Invalid date format. Use dd.MM.yyyy", 400);
		}

		LocalDateTime dateTime = localdate.atStartOfDay();
		ZoneId zone = ZoneId.of("Europe/Berlin");
		ZonedDateTime zonedDateTime = dateTime.atZone(zone);
		Timestamp timestampcreated = Timestamp.from(zonedDateTime.toInstant());
		Timestamp timestampnow = Timestamp.from(Instant.now());

		DBUtil.executeUpdate(
				"UPDATE kickpoints SET description = ?, amount = ?, date = ?, updated_at = ? WHERE id = ?",
				reason, amount, timestampcreated, timestampnow, id);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint updated");
		return response;
	}

	private JSONObject handleKickpointRemove(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);

		if (discordUserId == null || !json.has("id")) {
			return error("Missing required fields: discordUserId, id", 400);
		}

		int id = json.getInt("id");

		Kickpoint kp = new Kickpoint(id);
		if (kp.getPlayer() == null || kp.getDescription() == null) {
			return error("Kickpoint not found", 404);
		}

		Clan kpClan = kp.getPlayer().getClanDB();
		User user = new User(discordUserId);
		if (kpClan == null) {
			if (!user.isAdmin()) {
				return error("Player of this kickpoint is not in a clan - only an admin can remove it", 403);
			}
		} else if (!user.isColeaderOrHigherInClan(kpClan.getTag())) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		DBUtil.executeUpdate("DELETE FROM kickpoints WHERE id = ?", id);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint removed");
		return response;
	}

	// ==================== Clan Config ====================

	private JSONObject handleClanconfig(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);

		if (discordUserId == null || clanTag == null || !json.has("maxKickpoints")
				|| !json.has("kickpointsExpireAfterDays") || !json.has("minSeasonWins")) {
			return error(
					"Missing required fields: discordUserId, clanTag, maxKickpoints, kickpointsExpireAfterDays, minSeasonWins",
					400);
		}

		int maxKickpoints = json.getInt("maxKickpoints");
		int kickpointsExpireAfterDays = json.getInt("kickpointsExpireAfterDays");
		int minSeasonWins = json.getInt("minSeasonWins");

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		Clan c = new Clan(clanTag);
		if (c.getInfoString() == null) {
			return error("Clan not found", 404);
		}

		if (c.getDaysKickpointsExpireAfter() == null) {
			DBUtil.executeUpdate(
					"INSERT INTO clan_settings (clan_tag, max_kickpoints, kickpoints_expire_after_days, min_season_wins) VALUES (?, ?, ?, ?)",
					clanTag, maxKickpoints, kickpointsExpireAfterDays, minSeasonWins);
		} else {
			DBUtil.executeUpdate(
					"UPDATE clan_settings SET max_kickpoints = ?, kickpoints_expire_after_days = ?, min_season_wins = ? WHERE clan_tag = ?",
					maxKickpoints, kickpointsExpireAfterDays, minSeasonWins, clanTag);
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Clan config updated");
		return response;
	}

	// ==================== Kickpoint Reasons ====================

	private JSONObject handleKickpointReasonAdd(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);
		String reason = json.optString("reason", null);

		if (discordUserId == null || clanTag == null || reason == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, clanTag, reason, amount", 400);
		}
		int amount = json.getInt("amount");

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		Clan clan = new Clan(clanTag);
		if (!clan.ExistsDB()) {
			return error("Clan not found", 404);
		}

		KickpointReason kpreason = new KickpointReason(reason, clanTag);
		if (kpreason.Exists()) {
			return error("Kickpoint reason already exists", 400);
		}

		DBUtil.executeUpdate("INSERT INTO kickpoint_reasons (name, clan_tag, amount) VALUES (?, ?, ?)",
				reason, clanTag, amount);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reason added");
		return response;
	}

	private JSONObject handleKickpointReasonEdit(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);
		String reason = json.optString("reason", null);

		if (discordUserId == null || clanTag == null || reason == null || !json.has("amount")) {
			return error("Missing required fields: discordUserId, clanTag, reason, amount", 400);
		}
		int amount = json.getInt("amount");

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		Clan clan = new Clan(clanTag);
		if (!clan.ExistsDB()) {
			return error("Clan not found", 404);
		}

		KickpointReason kpreason = new KickpointReason(reason, clanTag);
		if (!kpreason.Exists()) {
			return error("Kickpoint reason not found", 404);
		}

		DBUtil.executeUpdate("UPDATE kickpoint_reasons SET amount = ? WHERE name = ? AND clan_tag = ?",
				amount, reason, clanTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reason updated");
		return response;
	}

	private JSONObject handleKickpointReasonRemove(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String clanTag = json.optString("clanTag", null);
		String reason = json.optString("reason", null);

		if (discordUserId == null || clanTag == null || reason == null) {
			return error("Missing required fields: discordUserId, clanTag, reason", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigherInClan(clanTag)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}

		Clan clan = new Clan(clanTag);
		if (!clan.ExistsDB()) {
			return error("Clan not found", 404);
		}

		KickpointReason kpreason = new KickpointReason(reason, clanTag);
		if (!kpreason.Exists()) {
			return error("Kickpoint reason not found", 404);
		}

		DBUtil.executeUpdate("DELETE FROM kickpoint_reasons WHERE name = ? AND clan_tag = ?", reason, clanTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Kickpoint reason removed");
		return response;
	}

	// ==================== Link Management ====================

	private JSONObject handleLink(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String targetUserId = json.optString("targetUserId", null);

		if (discordUserId == null || playerTag == null || targetUserId == null) {
			return error("Missing required fields: discordUserId, playerTag, targetUserId", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		playerTag = playerTag.toUpperCase().replaceAll("O", "0");
		if (!playerTag.startsWith("#")) {
			playerTag = "#" + playerTag;
		}

		Player p = new Player(playerTag);
		if (!p.AccExists()) {
			return error("Player does not exist or API error", 404);
		}
		if (p.IsLinked()) {
			return error(
					"Player is already linked to user " + p.getUser().getUserID() + ". Use relink instead.",
					400);
		}

		String playername = null;
		try {
			playername = p.getNameAPI();
		} catch (Exception e) {
			e.printStackTrace();
		}

		DBUtil.executeUpdate("INSERT INTO players (coc_tag, discord_id, name) VALUES (?, ?, ?)",
				playerTag, targetUserId, playername);

		try {
			Player player = new Player(playerTag);
			Timestamp now = new Timestamp(System.currentTimeMillis());
			player.addAchievementDataToDB(AchievementData.Type.WINS, now);
		} catch (Exception e) {
			System.err.println("Error saving initial wins for player " + playerTag + ": " + e.getMessage());
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player linked to user");
		return response;
	}

	private JSONObject handleRelink(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);
		String targetUserId = json.optString("targetUserId", null);

		if (discordUserId == null || playerTag == null || targetUserId == null) {
			return error("Missing required fields: discordUserId, playerTag, targetUserId", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		playerTag = playerTag.toUpperCase();
		if (!playerTag.startsWith("#")) {
			playerTag = "#" + playerTag;
		}

		Player p = new Player(playerTag);
		if (!p.AccExists()) {
			return error("Player does not exist or API error", 404);
		}
		if (!p.IsLinked()) {
			return error("Player is not linked. Use link instead.", 400);
		}

		DBUtil.executeUpdate("UPDATE players SET discord_id = ? WHERE coc_tag = ?", targetUserId, playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player relinked to user");
		return response;
	}

	private JSONObject handleUnlink(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		String playerTag = json.optString("playerTag", null);

		if (discordUserId == null || playerTag == null) {
			return error("Missing required fields: discordUserId, playerTag", 400);
		}

		User user = new User(discordUserId);
		if (!user.isColeaderOrHigher()) {
			return error("Insufficient permissions - must be coleader or higher in any clan", 403);
		}

		Player p = new Player(playerTag);
		if (!p.IsLinked()) {
			return error("Player is not linked", 404);
		}
		if (p.getClanDB() != null) {
			return error("Player is still in a clan. Remove from clan first.", 400);
		}

		DBUtil.executeUpdate("DELETE FROM players WHERE coc_tag = ?", playerTag);

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Player unlinked");
		return response;
	}

	// ==================== Admin ====================

	private JSONObject handleRestart(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);

		if (discordUserId == null) {
			return error("Missing required field: discordUserId", 400);
		}

		User user = new User(discordUserId);
		if (!user.isAdmin()) {
			return error("Insufficient permissions - must be admin", 403);
		}

		new Thread(() -> {
			int activeTasks = Bot.activeVerificationTasks.get();
			if (activeTasks > 0) {
				long startTime = System.currentTimeMillis();
				while (Bot.activeVerificationTasks.get() > 0) {
					try {
						Thread.sleep(10000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (System.currentTimeMillis() - startTime > 360000) {
						break;
					}
				}
			}
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.exit(0);
		}, "RestartApi").start();

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Bot restart initiated");
		return response;
	}

	// ==================== Utility Methods ====================

	// ==================== Listening Events ====================

	/**
	 * Builds a {@link ListeningEventService.Spec} from the request body.
	 *
	 * Deliberately lenient about where a number comes from: the browser sends the
	 * duration as the string the user typed ("2h", "start"), while a script is
	 * more likely to send milliseconds. Both end up in the same long.
	 */
	private ListeningEventService.Spec specAusJson(JSONObject json) {
		ListeningEventService.Spec spec = new ListeningEventService.Spec();
		spec.clanTag = lostmanager.util.ClanTag.parse(json.optString("clanTag", null));
		spec.type = leerAlsNull(json.optString("type", null));
		spec.actionType = leerAlsNull(json.optString("actionType", null));
		spec.channelId = leerAlsNull(json.optString("channelId", null));
		spec.kickpointReasonName = leerAlsNull(json.optString("kickpointReason", null));
		spec.customMessage = leerAlsNull(json.optString("customMessage", null));
		spec.thresholdOrAttacks = optInteger(json, "thresholdOrAttacks");
		spec.starCount = optInteger(json, "starCount");
		spec.punishmentMode = optInteger(json, "punishmentMode");
		spec.useLists = optInteger(json, "useLists");
		spec.excludeLeaders = optInteger(json, "excludeLeaders");

		if (json.has("capitalPeakMax") || json.has("otherDistrictsMax")) {
			java.util.Map<String, Integer> schwellen = new java.util.HashMap<>();
			schwellen.put("capital_peak_max", optInteger(json, "capitalPeakMax"));
			schwellen.put("other_districts_max", optInteger(json, "otherDistrictsMax"));
			// Ohne Kickpunktgrund fragt auch das Modal nicht danach; 1 ist dort der
			// Vorgabewert und bleibt es hier.
			Integer beide = optInteger(json, "penalizeBoth");
			schwellen.put("penalize_both", beide != null ? beide : 1);
			spec.raidDistrictThresholds = schwellen;
		}

		java.util.Map<String, Long> einstellungen = new java.util.HashMap<>();
		setzeWennDa(einstellungen, ListeningEvent.SETTING_IGNORE_PERFECT_WAR, json, "ignorePerfectWar");
		setzeWennDa(einstellungen, ListeningEvent.SETTING_RAID_FORCE_KICKPOINTS, json, "raidForceKickpoints");
		setzeWennDa(einstellungen, ListeningEvent.SETTING_WINS_THRESHOLD, json, "winsThreshold");
		setzeWennDa(einstellungen, ListeningEvent.SETTING_STARFAILS_FREE_HITS, json, "starfailsFreeHits");
		setzeWennDa(einstellungen, ListeningEvent.SETTING_CW_MIN_COUNT, json, "cwMinCount");
		if (!einstellungen.isEmpty()) {
			spec.namedSettings = einstellungen;
		}

		return spec;
	}

	private static String leerAlsNull(String wert) {
		return (wert == null || wert.isBlank()) ? null : wert;
	}

	private static Integer optInteger(JSONObject json, String schluessel) {
		if (!json.has(schluessel) || json.isNull(schluessel)) {
			return null;
		}
		try {
			return json.getInt(schluessel);
		} catch (org.json.JSONException e) {
			return null;
		}
	}

	private static void setzeWennDa(java.util.Map<String, Long> ziel, String schluessel, JSONObject json,
			String feld) {
		Integer wert = optInteger(json, feld);
		if (wert != null) {
			ziel.put(schluessel, wert.longValue());
		}
	}

	/**
	 * The duration arrives either as a plain number of milliseconds or as the
	 * shorthand the slash command accepts. "start" is a marker, not a duration.
	 *
	 * @return null if it could not be read
	 */
	private static Long dauerAusJson(JSONObject json) {
		if (!json.has("duration") || json.isNull("duration")) {
			return null;
		}
		Object roh = json.get("duration");
		if (roh instanceof Number zahl) {
			return zahl.longValue();
		}
		String text = roh.toString().trim();
		if (text.equalsIgnoreCase("start") || text.equalsIgnoreCase("cwstart")) {
			return -1L;
		}
		try {
			return ListeningEventService.parseDuration(text);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * Whether this user may manage the events of that clan.
	 *
	 * Unlike {@code /listeningevent}, which only asks whether somebody is a vice
	 * leader <em>anywhere</em>, this asks about the clan the event belongs to -
	 * the same rule kickpoints and member changes already follow. Side clans have
	 * no leadership of their own, so their main clan is what counts.
	 */
	private JSONObject pruefeRecht(String discordUserId, String clanTag) {
		if (discordUserId == null || clanTag == null) {
			return error("Missing required fields: discordUserId, clanTag", 400);
		}
		Clan c = new Clan(clanTag);
		String zustaendig = ListeningEventService.effectiveClanTag(clanTag);
		if (!c.ExistsDB() && zustaendig.equals(clanTag)) {
			return error("Clan not found", 404);
		}
		if (!new User(discordUserId).isColeaderOrHigherInClan(zustaendig)) {
			return error("Insufficient permissions - must be coleader or higher in the clan", 403);
		}
		return null;
	}

	/**
	 * Prüft, ob der Bot in den Zielkanal schreiben kann.
	 *
	 * **Absichtlich weich.** Die meisten Listening Events posten in *Threads*
	 * ("Kickpunkte Failpunkte Notizen", "CW Auffüllen") und nicht in normale
	 * Textkanäle - eine Prüfung über {@code getTextChannelById} hat am 21.09.2026
	 * prompt jeden zweiten echten Kanal abgelehnt. Threads verschwinden zudem aus
	 * dem Cache, sobald sie archiviert werden, obwohl der Bot dorthin weiterhin
	 * senden kann. Ein unbekannter Kanal wird deshalb nicht abgewiesen, sondern
	 * nur gemeldet: eine berechtigte Änderung zu blockieren wäre schlimmer als ein
	 * Tippfehler, den man danach wieder löschen kann.
	 *
	 * @return eine Warnung für die Antwort, oder null wenn alles in Ordnung ist
	 */
	private String kanalWarnung(String channelId) {
		if (Bot.getJda() == null) {
			return null;
		}
		net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel kanal = Bot.getJda()
				.getChannelById(net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel.class, channelId);
		if (kanal == null) {
			return "Der Bot kennt diesen Kanal nicht (gelöscht, aus einem anderen Server "
					+ "oder ein archivierter Thread). Das Event wurde trotzdem gespeichert — bitte prüfen.";
		}
		if (!kanal.getGuild().getId().equals(Bot.guild_id)) {
			return "Dieser Kanal liegt auf einem anderen Server. Das Event wurde trotzdem gespeichert.";
		}
		// canTalk() gibt es nur am Textkanal; für einen Thread hängt das Recht am
		// Elternkanal und ist hier nicht verlässlich zu beantworten.
		if (kanal instanceof net.dv8tion.jda.api.entities.channel.concrete.TextChannel textKanal
				&& !textKanal.canTalk()) {
			return "Der Bot darf in diesem Kanal nicht schreiben. Das Event wurde trotzdem gespeichert.";
		}
		return null;
	}

	private JSONObject handleListeningEventAdd(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		ListeningEventService.Spec spec = specAusJson(json);
		Long dauer = dauerAusJson(json);
		if (dauer == null) {
			return error("Invalid or missing duration - use milliseconds, 2h/30m/1d or 'start'", 400);
		}
		spec.duration = dauer;

		JSONObject verweigert = pruefeRecht(discordUserId, spec.clanTag);
		if (verweigert != null) {
			return verweigert;
		}
		ListeningEventService.Result ergebnis = ListeningEventService.create(spec);
		if (!ergebnis.ok()) {
			return error(ergebnis.error(), ergebnis.statusCode());
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Listening Event angelegt");
		response.put("id", ergebnis.id());
		String warnung = spec.channelId == null ? null : kanalWarnung(spec.channelId);
		if (warnung != null) {
			response.put("warning", warnung);
		}
		return response;
	}

	private JSONObject handleListeningEventEdit(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		if (!json.has("id")) {
			return error("Missing required field: id", 400);
		}
		long id = json.getLong("id");

		if (!ListeningEventService.exists(id)) {
			return error("Listening event not found", 404);
		}

		// Auf beiden Seiten prüfen, wenn das Event den Clan wechselt: sonst könnte
		// ein Vize ein fremdes Event zu sich holen oder eines von sich wegschieben.
		String bisherigerClan = new ListeningEvent(id).getClanTag();
		JSONObject verweigert = pruefeRecht(discordUserId, bisherigerClan);
		if (verweigert != null) {
			return verweigert;
		}

		ListeningEventService.Spec spec = specAusJson(json);
		Long dauer = dauerAusJson(json);
		if (dauer == null) {
			return error("Invalid or missing duration - use milliseconds, 2h/30m/1d or 'start'", 400);
		}
		spec.duration = dauer;

		if (spec.clanTag != null && !spec.clanTag.equals(bisherigerClan)) {
			verweigert = pruefeRecht(discordUserId, spec.clanTag);
			if (verweigert != null) {
				return verweigert;
			}
		}
		ListeningEventService.Result ergebnis = ListeningEventService.update(id, spec);
		if (!ergebnis.ok()) {
			return error(ergebnis.error(), ergebnis.statusCode());
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Listening Event geändert");
		response.put("id", id);
		String warnung = spec.channelId == null ? null : kanalWarnung(spec.channelId);
		if (warnung != null) {
			response.put("warning", warnung);
		}
		return response;
	}

	private JSONObject handleListeningEventRemove(JSONObject json) {
		String discordUserId = json.optString("discordUserId", null);
		if (!json.has("id")) {
			return error("Missing required field: id", 400);
		}
		long id = json.getLong("id");

		if (!ListeningEventService.exists(id)) {
			return error("Listening event not found", 404);
		}

		JSONObject verweigert = pruefeRecht(discordUserId, new ListeningEvent(id).getClanTag());
		if (verweigert != null) {
			return verweigert;
		}

		ListeningEventService.Result ergebnis = ListeningEventService.delete(id);
		if (!ergebnis.ok()) {
			return error(ergebnis.error(), ergebnis.statusCode());
		}

		JSONObject response = new JSONObject();
		response.put("success", true);
		response.put("message", "Listening Event gelöscht");
		response.put("id", id);
		return response;
	}

	private JSONObject error(String message, int statusCode) {
		JSONObject response = new JSONObject();
		response.put("success", false);
		response.put("error", message);
		response.put("statusCode", statusCode);
		return response;
	}

	private boolean validateApiToken(HttpExchange exchange) {
		// The management API is highly privileged (restart, member/link/kickpoint mutations).
		// If no token is configured, fail closed unless explicitly opted out via env.
		if (apiToken == null || apiToken.isEmpty()) {
			return allowNoAuth;
		}
		String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
		if (authHeader != null) {
			if (authHeader.startsWith("Bearer ")) {
				return tokensMatch(apiToken, authHeader.substring(7));
			}
			return tokensMatch(apiToken, authHeader);
		}
		String apiTokenHeader = exchange.getRequestHeaders().getFirst("X-API-Token");
		if (apiTokenHeader != null) {
			return tokensMatch(apiToken, apiTokenHeader);
		}
		return false;
	}

	/**
	 * Constant-time comparison to avoid leaking the token via timing side channels.
	 */
	private static boolean tokensMatch(String expected, String provided) {
		if (expected == null || provided == null) {
			return false;
		}
		return java.security.MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8),
				provided.getBytes(StandardCharsets.UTF_8));
	}

	private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		addCorsHeaders(exchange);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private void addCorsHeaders(HttpExchange exchange) {
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", allowedOrigin);
		exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
		exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
				"Content-Type, Authorization, X-API-Token");
	}

	private void handleException(HttpExchange exchange, Exception e) {
		boolean isClientDisconnect = false;
		if (e instanceof IOException) {
			String msg = e.getMessage();
			if (msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe")
					|| msg.contains("insufficient bytes written"))) {
				isClientDisconnect = true;
			}
		}
		if (isClientDisconnect) {
			// Ignorieren, um Log-Spam zu vermeiden
			// System.out.println("Client disconnected in ManagementApiHandler: " + e.getMessage());
		} else {
			System.err.println("Error in ManagementApiHandler: " + e.getMessage());
			e.printStackTrace();
			try {
				sendResponse(exchange, 500,
						new JSONObject().put("error", "Internal Server Error").toString());
			} catch (IOException ignore) {
			}
		}
	}
}
