package lostmanager.webserver.api;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.webserver.api.dto.ClanDTO;
import lostmanager.webserver.api.dto.PlayerDTO;
import lostmanager.webserver.api.dto.UserDTO;

import java.util.concurrent.Executors;

/**
 * REST API Server for Clans, Players, and Users
 * Runs on port 8070 by default
 */
public class RestApiServer {
    
    private HttpServer server;
    private int port;
    private ObjectMapper objectMapper;
    
    public RestApiServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        
        // Register API endpoints
        server.createContext("/api/clans", new ClansHandler());
        server.createContext("/api/clans/", new ClanMembersHandler());
        server.createContext("/api/players/", new PlayerHandler());
        server.createContext("/api/users/", new UserHandler());
        
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();
        
        System.out.println("REST API Server started on port " + port);
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
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
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
                System.err.println("Error in ClansHandler: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
            }
        }
    }
    
    /**
     * Handler for GET /api/clans/{tag}/members
     * Returns memberlist of a clan with player objects including player data
     */
    private class ClanMembersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            
            try {
                // Extract clan tag from path
                String path = exchange.getRequestURI().getPath();
                // Path format: /api/clans/{tag}/members
                String[] parts = path.split("/");
                
                if (parts.length < 5 || !"members".equals(parts[4])) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid path format. Expected /api/clans/{tag}/members\"}");
                    return;
                }
                
                String clanTag = parts[3];
                
                // Validate clan exists
                Clan clan = new Clan(clanTag);
                if (!clan.ExistsDB()) {
                    sendResponse(exchange, 404, "{\"error\":\"Clan not found\"}");
                    return;
                }
                
                // Get members from database
                ArrayList<Player> members = clan.getPlayersDB();
                
                if (members == null) {
                    sendJsonResponse(exchange, 200, "[]");
                    return;
                }
                
                // Convert to DTOs - include player data here so not every one needs to be checked
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
                
            } catch (Exception e) {
                System.err.println("Error in ClanMembersHandler: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
            }
        }
    }
    
    /**
     * Handler for GET /api/players/{tag}
     * Returns a player object with all requested data
     */
    private class PlayerHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
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
                
                // Convert to DTO
                PlayerDTO playerDTO = new PlayerDTO(player);
                
                // Serialize to JSON
                String json = objectMapper.writeValueAsString(playerDTO);
                sendJsonResponse(exchange, 200, json);
                
            } catch (Exception e) {
                System.err.println("Error in PlayerHandler: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
            }
        }
    }
    
    /**
     * Handler for GET /api/users/{userId}
     * Returns a user object with isAdmin and list of linked players
     */
    private class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
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
                if (linkedPlayers == null || linkedPlayers.isEmpty()) {
                    sendResponse(exchange, 404, "{\"error\":\"User not found or has no linked accounts\"}");
                    return;
                }
                
                // Convert to DTO
                UserDTO userDTO = new UserDTO(user);
                
                // Serialize to JSON
                String json = objectMapper.writeValueAsString(userDTO);
                sendJsonResponse(exchange, 200, json);
                
            } catch (Exception e) {
                System.err.println("Error in UserHandler: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Internal Server Error\"}");
            }
        }
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
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }
}
