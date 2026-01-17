package lostmanager.webserver.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import lostmanager.Bot;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.datawrapper.KickpointReason;
import lostmanager.dbutil.DBManager;
import lostmanager.webserver.api.dto.ClanDTO;
import lostmanager.webserver.api.dto.PlayerDTO;
import lostmanager.webserver.api.dto.UserDTO;
import lostmanager.webserver.api.dto.KickpointReasonDTO;
import lostmanager.webserver.api.dto.SideclanDTO;

import java.util.concurrent.Executors;

import org.json.JSONObject;

/**
 * REST API Server for Clans, Players, and Users
 * Runs on port 8070 by default
 */
public class RestApiServer {

    private HttpServer server;
    private int port;
    private ObjectMapper objectMapper;
    private String apiToken;

    public RestApiServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.apiToken = System.getenv("REST_API_TOKEN");

        if (this.apiToken == null || this.apiToken.isEmpty()) {
            System.err.println(
                    "WARNING: REST_API_TOKEN is not set. API endpoints will be accessible without authentication.");
        }
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        // Register API endpoints
        // Note: More specific paths should be registered before more general paths
        server.createContext("/api/clans/", new ClanSpecificHandler());
        server.createContext("/api/clans", new ClansHandler());
        server.createContext("/api/sideclans", new SideclansHandler());
        server.createContext("/api/players/", new PlayerHandler());
        server.createContext("/api/users/", new UserHandler());
        server.createContext("/api/guild", new GuildHandler());

        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        System.out.println("REST API Server started on port " + port);
    }

    /**
     * Handler for GET /api/sideclans
     * Returns all sideclans from DB table `sideclans` with fields
     * `clan_tag`, `name`, `belongs_to`.
     */
    private class SideclansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            if (!validateApiToken(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
                return;
            }

            try {
                String sql = "SELECT clan_tag, name, belongs_to FROM sideclans ORDER BY name ASC";
                List<SideclanDTO> list = new ArrayList<>();

                try (PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection().prepareStatement(sql)) {
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            String tag = rs.getString("clan_tag");
                            String name = rs.getString("name");
                            String belongsTo = rs.getString("belongs_to");
                            list.add(new SideclanDTO(tag, name, belongsTo));
                        }
                    }
                }

                String json = objectMapper.writeValueAsString(list);
                sendJsonResponse(exchange, 200, json);
            } catch (Exception e) {
                handleException(exchange, "SideclansHandler", e);
            }
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("REST API Server stopped");
        }
    }

    /**
     * Handler for GET /api/clans
     * Returns all available clans
     */
    private class ClansHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Validate API token
            if (!validateApiToken(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
                return;
            }

            try {
                // Get all clan tags from database
                ArrayList<String> clanTags = DBManager.getAllClans();

                // Convert to DTOs
                List<ClanDTO> clans = new ArrayList<>();
                for (String tag : clanTags) {
                    try {
                        Clan clan = new Clan(tag);
                        clans.add(new ClanDTO(clan));
                    } catch (Exception e) {
                        System.err.println("Error processing clan " + tag + ": " + e.getMessage());
                        // Continue with other clans
                    }
                }

                // Serialize to JSON
                String json = objectMapper.writeValueAsString(clans);
                sendJsonResponse(exchange, 200, json);

            } catch (Exception e) {
                handleException(exchange, "ClansHandler", e);
            }
        }
    }

    /**
     * Handler for clan-specific endpoints:
     * - GET /api/clans/{tag} - clan info
     * - GET /api/clans/{tag}/members - clan members (DB only)
     * - GET /api/clans/{tag}/kickpoint-reasons - kickpoint reasons for clan
     * - GET /api/clans/{tag}/war-members - clan war participants (DB only)
     * - GET /api/clans/{tag}/raid-members - raid participants (DB only)
     * - GET /api/clans/{tag}/cwl-members - CWL participants (DB only)
     */
    private class ClanSpecificHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Validate API token
            if (!validateApiToken(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
                return;
            }

            try {
                // Extract path
                String path = exchange.getRequestURI().getPath();
                String[] parts = path.split("/");

                // Need at least /api/clans/{tag}
                if (parts.length < 4) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid path format\"}");
                    return;
                }

                String clanTag = parts[3];

                // Validate clan exists (DB call only)
                Clan clan = new Clan(clanTag);
                if (!clan.ExistsDB()) {
                    sendResponse(exchange, 404, "{\"error\":\"Clan not found\"}");
                    return;
                }

                // Route based on sub-path
                if (parts.length >= 5) {
                    String subPath = parts[4];

                    switch (subPath) {
                        case "members":
                            handleClanMembers(exchange, clan);
                            break;
                        case "kickpoint-reasons":
                            handleKickpointReasons(exchange, clan);
                            break;
                        case "war-members":
                            handleWarMembers(exchange, clan);
                            break;
                        case "raid-members":
                            handleRaidMembers(exchange, clan);
                            break;
                        case "cwl-members":
                            handleCWLMembers(exchange, clan);
                            break;
                        default:
                            sendResponse(exchange, 404, "{\"error\":\"Unknown endpoint\"}");
                    }
                } else {
                    // Return clan info (DB call only)
                    ClanDTO clanDTO = new ClanDTO(clan);
                    String json = objectMapper.writeValueAsString(clanDTO);
                    sendJsonResponse(exchange, 200, json);
                }

            } catch (Exception e) {
                handleException(exchange, "ClanSpecificHandler", e);
            }
        }

        private void handleClanMembers(HttpExchange exchange, Clan clan) throws Exception {
            // Return members list (DB call only - no API call)
            ArrayList<Player> members = clan.getPlayersDB();

            if (members == null) {
                sendJsonResponse(exchange, 200, "[]");
                return;
            }

            // Convert to DTOs - include player data here so not every one needs to be
            // checked
            List<PlayerDTO> playerDTOs = new ArrayList<>();
            for (Player player : members) {
                try {
                    playerDTOs.add(new PlayerDTO(player));
                } catch (Exception e) {
                    System.err.println("Error processing player " + player.getTag() + ": " + e.getMessage());
                    // Continue with other players
                }
            }

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(playerDTOs);
            sendJsonResponse(exchange, 200, json);
        }

        private void handleKickpointReasons(HttpExchange exchange, Clan clan) throws Exception {
            // Get kickpoint reasons for the clan (DB call only)
            ArrayList<KickpointReason> reasons = clan.getKickpointReasons();

            if (reasons == null) {
                sendJsonResponse(exchange, 200, "[]");
                return;
            }

            // Convert to DTOs
            List<KickpointReasonDTO> reasonDTOs = new ArrayList<>();
            for (KickpointReason reason : reasons) {
                try {
                    reasonDTOs.add(new KickpointReasonDTO(reason));
                } catch (Exception e) {
                    System.err.println("Error processing kickpoint reason: " + e.getMessage());
                    // Continue with other reasons
                }
            }

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(reasonDTOs);
            sendJsonResponse(exchange, 200, json);
        }

        private void handleWarMembers(HttpExchange exchange, Clan clan) throws Exception {
            // Get clan war members (DB call only)
            ArrayList<Player> warMembers = clan.getWarMemberList();

            if (warMembers == null) {
                sendJsonResponse(exchange, 200, "[]");
                return;
            }

            // Convert to simple tag list for performance
            List<String> tags = new ArrayList<>();
            for (Player player : warMembers) {
                tags.add(player.getTag());
            }

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(tags);
            sendJsonResponse(exchange, 200, json);
        }

        private void handleRaidMembers(HttpExchange exchange, Clan clan) throws Exception {
            // Get raid members (DB call only)
            ArrayList<Player> raidMembers = clan.getRaidMemberList();

            if (raidMembers == null) {
                sendJsonResponse(exchange, 200, "[]");
                return;
            }

            // Convert to simple tag list for performance
            List<String> tags = new ArrayList<>();
            for (Player player : raidMembers) {
                tags.add(player.getTag());
            }

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(tags);
            sendJsonResponse(exchange, 200, json);
        }

        private void handleCWLMembers(HttpExchange exchange, Clan clan) throws Exception {
            // Get CWL members (DB call only)
            ArrayList<Player> cwlMembers = clan.getCWLMemberList();

            if (cwlMembers == null) {
                sendJsonResponse(exchange, 200, "[]");
                return;
            }

            // Convert to simple tag list for performance
            List<String> tags = new ArrayList<>();
            for (Player player : cwlMembers) {
                tags.add(player.getTag());
            }

            // Serialize to JSON
            String json = objectMapper.writeValueAsString(tags);
            sendJsonResponse(exchange, 200, json);
        }
    }

    /**
     * Handler for GET /api/players/{tag}
     * Returns a player object with all requested data (DB call only)
     */
    private class PlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Validate API token
            if (!validateApiToken(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
                return;
            }

            try {
                // Extract player tag from path
                String path = exchange.getRequestURI().getPath();
                // Path format: /api/players/{tag}
                String[] parts = path.split("/");

                if (parts.length < 4) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid path format. Expected /api/players/{tag}\"}");
                    return;
                }

                String playerTag = parts[3];

                // Create player object
                Player player = new Player(playerTag);

                // Check if player is linked (exists in DB)
                if (!player.IsLinked()) {
                    sendResponse(exchange, 404, "{\"error\":\"Player not found\"}");
                    return;
                }

                // Convert to DTO (DB calls only)
                PlayerDTO playerDTO = new PlayerDTO(player);

                // Serialize to JSON
                String json = objectMapper.writeValueAsString(playerDTO);
                sendJsonResponse(exchange, 200, json);

            } catch (Exception e) {
                handleException(exchange, "PlayerHandler", e);
            }
        }
    }

    /**
     * Handler for GET /api/users/{userId}
     * Returns a user object with isAdmin and list of linked players (DB call only)
     */
    private class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Validate API token
            if (!validateApiToken(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
                return;
            }

            try {
                // Extract user ID from path
                String path = exchange.getRequestURI().getPath();
                // Path format: /api/users/{userId}
                String[] parts = path.split("/");

                if (parts.length < 4) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid path format. Expected /api/users/{userId}\"}");
                    return;
                }

                String userId = parts[3];

                // Create user object
                User user = new User(userId);

                // Check if user has any linked accounts
                ArrayList<Player> linkedPlayers = user.getAllLinkedAccounts();
                if ((linkedPlayers == null || linkedPlayers.isEmpty()) && !user.isAdmin()) {
                    sendResponse(exchange, 404, "{\"error\":\"User not found or has no linked accounts\"}");
                    return;
                }

                // Convert to DTO
                UserDTO userDTO = new UserDTO(user);

                // Serialize to JSON
                String json = objectMapper.writeValueAsString(userDTO);
                sendJsonResponse(exchange, 200, json);

            } catch (Exception e) {
                handleException(exchange, "UserHandler", e);
            }
        }
    }

    private class GuildHandler implements HttpHandler {
        @SuppressWarnings("null")
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                addCorsHeaders(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }

            // Validate API token
            if (!validateApiToken(exchange)) {
                sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Invalid or missing API token\"}");
                return;
            }

            try {
                JSONObject object = new JSONObject();
                object.put("membercount", Bot.getJda().getGuildById(Bot.guild_id).getMemberCount());
                String json = object.toString();
                sendJsonResponse(exchange, 200, json);

            } catch (Exception e) {
                handleException(exchange, "GuildHandler", e);
            }
        }
    }

    /**
     * Handle exceptions from handlers, suppressing connection reset errors
     */
    private void handleException(HttpExchange exchange, String handlerName, Exception e) {
        boolean isClientDisconnect = false;
        if (e instanceof IOException) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Connection reset") || msg.contains("Broken pipe")
                    || msg.contains("insufficient bytes written"))) {
                isClientDisconnect = true;
            }
        }

        if (isClientDisconnect) {
            System.out.println("Client disconnected in " + handlerName + ": " + e.getMessage());
        } else {
            System.err.println("Error in " + handlerName + ": " + e.getMessage());
            e.printStackTrace();
            try {
                sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
            } catch (IOException ignore) {
                // Failed to send error response
            }
        }
    }

    /**
     * Validate API token from request headers
     * 
     * @return true if token is valid or no token is required, false otherwise
     */
    private boolean validateApiToken(HttpExchange exchange) {
        // If no token is configured, allow all requests
        if (apiToken == null || apiToken.isEmpty()) {
            return true;
        }

        // Check Authorization header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null) {
            // Support both "Bearer <token>" and direct token
            if (authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                return apiToken.equals(token);
            } else {
                return apiToken.equals(authHeader);
            }
        }

        // Check X-API-Token header
        String apiTokenHeader = exchange.getRequestHeaders().getFirst("X-API-Token");
        if (apiTokenHeader != null) {
            return apiToken.equals(apiTokenHeader);
        }

        return false;
    }

    /**
     * Send plain text response
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Send JSON response
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Add CORS headers to allow cross-origin requests
     */
    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-API-Token");
    }
}
