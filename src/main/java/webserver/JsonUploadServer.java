package webserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import dbutil.DBUtil;

public class JsonUploadServer {
	
	private HttpServer server;
	private ScheduledExecutorService scheduler;
	private int port;
	
	public JsonUploadServer(int port) {
		this.port = port;
		this.scheduler = Executors.newScheduledThreadPool(2);
	}
	
	public void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/upload", new UploadPageHandler());
		server.createContext("/submit", new SubmitHandler());
		server.setExecutor(Executors.newFixedThreadPool(10));
		server.start();
		
		System.out.println("JSON Upload Server started on port " + port);
		
		// Start cleanup tasks
		startCleanupTasks();
	}
	
	public void stop() {
		if (server != null) {
			server.stop(0);
			System.out.println("JSON Upload Server stopped");
		}
		if (scheduler != null) {
			scheduler.shutdown();
		}
	}
	
	/**
	 * Generate a new upload session for a user
	 * @param userId Discord user ID
	 * @return Session key (UUID)
	 */
	public static String createUploadSession(String userId) {
		String sessionKey = UUID.randomUUID().toString();
		Timestamp expiresAt = Timestamp.from(Instant.now().plusSeconds(600)); // 10 minutes
		
		String sql = "INSERT INTO upload_sessions (session_key, userid, expires_at) VALUES (?, ?, ?)";
		DBUtil.executeUpdate(sql, sessionKey, userId, expiresAt);
		
		return sessionKey;
	}
	
	/**
	 * Validate a session key and return the associated user ID
	 * @param sessionKey Session key to validate
	 * @return User ID if valid, null otherwise
	 */
	private static String validateSession(String sessionKey) {
		String sql = "SELECT userid FROM upload_sessions WHERE session_key = ? AND expires_at > CURRENT_TIMESTAMP";
		return DBUtil.getValueFromSQL(sql, String.class, sessionKey);
	}
	
	/**
	 * Invalidate a session after successful upload
	 * @param sessionKey Session key to invalidate
	 */
	private static void invalidateSession(String sessionKey) {
		String sql = "DELETE FROM upload_sessions WHERE session_key = ?";
		DBUtil.executeUpdate(sql, sessionKey);
	}
	
	/**
	 * Start cleanup tasks for expired sessions and old JSON data
	 */
	private void startCleanupTasks() {
		// Cleanup expired sessions every minute
		scheduler.scheduleAtFixedRate(() -> {
			try {
				String sql = "DELETE FROM upload_sessions WHERE expires_at < CURRENT_TIMESTAMP";
				util.Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(sql);
				if (result != null && result.getSecond() != null && result.getSecond() > 0) {
					System.out.println("Cleaned up " + result.getSecond() + " expired upload sessions");
				}
			} catch (Exception e) {
				System.err.println("Error cleaning up expired sessions: " + e.getMessage());
				e.printStackTrace();
			}
		}, 1, 1, TimeUnit.MINUTES);
		
		// Cleanup old JSON data every hour
		scheduler.scheduleAtFixedRate(() -> {
			try {
				String sql = "DELETE FROM userjsons WHERE timestamp < CURRENT_TIMESTAMP - INTERVAL '7 days'";
				util.Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(sql);
				if (result != null && result.getSecond() != null && result.getSecond() > 0) {
					System.out.println("Cleaned up " + result.getSecond() + " old JSON entries");
				}
			} catch (Exception e) {
				System.err.println("Error cleaning up old JSON data: " + e.getMessage());
				e.printStackTrace();
			}
		}, 1, 1, TimeUnit.HOURS);
	}
	
	/**
	 * Handler for GET /upload?key={sessionKey}
	 * Serves the HTML upload page
	 */
	private static class UploadPageHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed");
				return;
			}
			
			String query = exchange.getRequestURI().getQuery();
			String sessionKey = extractQueryParam(query, "key");
			
			if (sessionKey == null) {
				sendResponse(exchange, 400, "Missing session key");
				return;
			}
			
			String userId = validateSession(sessionKey);
			if (userId == null) {
				sendHtmlResponse(exchange, 403, getErrorPage("Invalid or Expired Session", 
					"This upload link is invalid or has expired. Please generate a new link from Discord."));
				return;
			}
			
			// Send upload page
			String html = getUploadPage(sessionKey);
			sendHtmlResponse(exchange, 200, html);
		}
	}
	
	/**
	 * Handler for POST /submit?key={sessionKey}
	 * Receives and processes JSON submission
	 */
	private static class SubmitHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"POST".equals(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed");
				return;
			}
			
			String query = exchange.getRequestURI().getQuery();
			String sessionKey = extractQueryParam(query, "key");
			
			if (sessionKey == null) {
				sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Missing session key\"}");
				return;
			}
			
			String userId = validateSession(sessionKey);
			if (userId == null) {
				sendJsonResponse(exchange, 403, "{\"success\":false,\"message\":\"Invalid or expired session\"}");
				return;
			}
			
			// Read JSON from request body
			String jsonData;
			try (InputStream is = exchange.getRequestBody()) {
				jsonData = new String(is.readAllBytes(), StandardCharsets.UTF_8);
			}
			
			if (jsonData == null || jsonData.trim().isEmpty()) {
				sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"Empty JSON data\"}");
				return;
			}
			
			// Parse and validate JSON
			try {
				JSONObject json = new JSONObject(jsonData);
				
				// Extract tag field
				if (!json.has("tag")) {
					sendJsonResponse(exchange, 400, "{\"success\":false,\"message\":\"JSON must contain a 'tag' field\"}");
					return;
				}
				
				String tag = json.getString("tag");
				
				// Store in database (UPSERT)
				String sql = "INSERT INTO userjsons (userid, tag, json, timestamp) " +
							 "VALUES (?, ?, ?::jsonb, CURRENT_TIMESTAMP) " +
							 "ON CONFLICT (userid, tag) DO UPDATE SET json = EXCLUDED.json, timestamp = EXCLUDED.timestamp";
				DBUtil.executeUpdate(sql, userId, tag, jsonData);
				
				// Invalidate session after successful upload
				invalidateSession(sessionKey);
				
				sendJsonResponse(exchange, 200, "{\"success\":true,\"message\":\"JSON data saved successfully\"}");
				
			} catch (Exception e) {
				System.err.println("Error processing JSON upload: " + e.getMessage());
				e.printStackTrace();
				// Use JSONObject to properly escape the error message
				String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
				JSONObject errorResponse = new JSONObject();
				errorResponse.put("success", false);
				errorResponse.put("message", "Invalid JSON format: " + errorMsg);
				sendJsonResponse(exchange, 400, errorResponse.toString());
			}
		}
	}
	
	/**
	 * Extract query parameter from query string (with URL decoding)
	 */
	private static String extractQueryParam(String query, String param) {
		if (query == null) return null;
		
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			String[] keyValue = pair.split("=");
			if (keyValue.length == 2 && keyValue[0].equals(param)) {
				try {
					return java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
				} catch (Exception e) {
					return keyValue[1]; // Return raw value if decoding fails
				}
			}
		}
		return null;
	}
	
	/**
	 * Send plain text response
	 */
	private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
		byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
	
	/**
	 * Send HTML response
	 */
	private static void sendHtmlResponse(HttpExchange exchange, int statusCode, String html) throws IOException {
		byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
	
	/**
	 * Send JSON response
	 */
	private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(statusCode, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
	
	/**
	 * Generate HTML upload page
	 * Note: sessionKey is a UUID generated internally, so no XSS risk from user input
	 */
	private static String getUploadPage(String sessionKey) {
		return "<!DOCTYPE html>\n" +
			"<html lang=\"de\">\n" +
			"<head>\n" +
			"    <meta charset=\"UTF-8\">\n" +
			"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
			"    <title>JSON Upload</title>\n" +
			"    <style>\n" +
			"        * {\n" +
			"            margin: 0;\n" +
			"            padding: 0;\n" +
			"            box-sizing: border-box;\n" +
			"        }\n" +
			"        body {\n" +
			"            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n" +
			"            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
			"            min-height: 100vh;\n" +
			"            display: flex;\n" +
			"            justify-content: center;\n" +
			"            align-items: center;\n" +
			"            padding: 20px;\n" +
			"        }\n" +
			"        .container {\n" +
			"            background: white;\n" +
			"            border-radius: 12px;\n" +
			"            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);\n" +
			"            padding: 40px;\n" +
			"            max-width: 600px;\n" +
			"            width: 100%;\n" +
			"        }\n" +
			"        h1 {\n" +
			"            color: #333;\n" +
			"            margin-bottom: 10px;\n" +
			"            font-size: 28px;\n" +
			"        }\n" +
			"        .info {\n" +
			"            color: #666;\n" +
			"            margin-bottom: 30px;\n" +
			"            padding: 12px;\n" +
			"            background: #f0f0f0;\n" +
			"            border-radius: 6px;\n" +
			"            font-size: 14px;\n" +
			"        }\n" +
			"        .info strong {\n" +
			"            color: #d9534f;\n" +
			"        }\n" +
			"        label {\n" +
			"            display: block;\n" +
			"            margin-bottom: 8px;\n" +
			"            color: #555;\n" +
			"            font-weight: 600;\n" +
			"        }\n" +
			"        textarea {\n" +
			"            width: 100%;\n" +
			"            min-height: 200px;\n" +
			"            padding: 12px;\n" +
			"            border: 2px solid #ddd;\n" +
			"            border-radius: 6px;\n" +
			"            font-family: 'Courier New', monospace;\n" +
			"            font-size: 13px;\n" +
			"            resize: vertical;\n" +
			"            transition: border-color 0.3s;\n" +
			"        }\n" +
			"        textarea:focus {\n" +
			"            outline: none;\n" +
			"            border-color: #667eea;\n" +
			"        }\n" +
			"        button {\n" +
			"            margin-top: 20px;\n" +
			"            width: 100%;\n" +
			"            padding: 14px;\n" +
			"            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
			"            color: white;\n" +
			"            border: none;\n" +
			"            border-radius: 6px;\n" +
			"            font-size: 16px;\n" +
			"            font-weight: 600;\n" +
			"            cursor: pointer;\n" +
			"            transition: transform 0.2s, box-shadow 0.2s;\n" +
			"        }\n" +
			"        button:hover {\n" +
			"            transform: translateY(-2px);\n" +
			"            box-shadow: 0 10px 20px rgba(0, 0, 0, 0.2);\n" +
			"        }\n" +
			"        button:active {\n" +
			"            transform: translateY(0);\n" +
			"        }\n" +
			"        button:disabled {\n" +
			"            background: #ccc;\n" +
			"            cursor: not-allowed;\n" +
			"            transform: none;\n" +
			"        }\n" +
			"        .message {\n" +
			"            margin-top: 20px;\n" +
			"            padding: 12px;\n" +
			"            border-radius: 6px;\n" +
			"            display: none;\n" +
			"            font-size: 14px;\n" +
			"        }\n" +
			"        .message.success {\n" +
			"            background: #d4edda;\n" +
			"            color: #155724;\n" +
			"            border: 1px solid #c3e6cb;\n" +
			"        }\n" +
			"        .message.error {\n" +
			"            background: #f8d7da;\n" +
			"            color: #721c24;\n" +
			"            border: 1px solid #f5c6cb;\n" +
			"        }\n" +
			"    </style>\n" +
			"</head>\n" +
			"<body>\n" +
			"    <div class=\"container\">\n" +
			"        <h1>JSON Daten hochladen</h1>\n" +
			"        <div class=\"info\">\n" +
			"            <strong>⚠️ Hinweis:</strong> Dieser Link ist nur <strong>10 Minuten</strong> gültig und kann nur einmal verwendet werden.\n" +
			"        </div>\n" +
			"        <form id=\"uploadForm\">\n" +
			"            <label for=\"jsonData\">JSON-Daten aus dem Spiel einfügen:</label>\n" +
			"            <textarea id=\"jsonData\" name=\"jsonData\" placeholder='{\"tag\": \"#ABC123\", \"name\": \"Player\", ...}' required></textarea>\n" +
			"            <button type=\"submit\" id=\"submitBtn\">Speichern</button>\n" +
			"        </form>\n" +
			"        <div id=\"message\" class=\"message\"></div>\n" +
			"    </div>\n" +
			"    <script>\n" +
			"        const form = document.getElementById('uploadForm');\n" +
			"        const textarea = document.getElementById('jsonData');\n" +
			"        const submitBtn = document.getElementById('submitBtn');\n" +
			"        const messageDiv = document.getElementById('message');\n" +
			"        \n" +
			"        form.addEventListener('submit', async (e) => {\n" +
			"            e.preventDefault();\n" +
			"            \n" +
			"            const jsonData = textarea.value.trim();\n" +
			"            if (!jsonData) {\n" +
			"                showMessage('Bitte JSON-Daten eingeben', 'error');\n" +
			"                return;\n" +
			"            }\n" +
			"            \n" +
			"            // Validate JSON\n" +
			"            try {\n" +
			"                JSON.parse(jsonData);\n" +
			"            } catch (e) {\n" +
			"                showMessage('Ungültiges JSON-Format: ' + e.message, 'error');\n" +
			"                return;\n" +
			"            }\n" +
			"            \n" +
			"            submitBtn.disabled = true;\n" +
			"            submitBtn.textContent = 'Wird hochgeladen...';\n" +
			"            \n" +
			"            try {\n" +
			"                const response = await fetch('/submit?key=" + sessionKey + "', {\n" +
			"                    method: 'POST',\n" +
			"                    headers: {\n" +
			"                        'Content-Type': 'application/json'\n" +
			"                    },\n" +
			"                    body: jsonData\n" +
			"                });\n" +
			"                \n" +
			"                const result = await response.json();\n" +
			"                \n" +
			"                if (result.success) {\n" +
			"                    showMessage('✅ ' + result.message + ' Du kannst diese Seite jetzt schließen.', 'success');\n" +
			"                    textarea.disabled = true;\n" +
			"                    submitBtn.style.display = 'none';\n" +
			"                } else {\n" +
			"                    showMessage('❌ ' + result.message, 'error');\n" +
			"                    submitBtn.disabled = false;\n" +
			"                    submitBtn.textContent = 'Speichern';\n" +
			"                }\n" +
			"            } catch (error) {\n" +
			"                showMessage('❌ Fehler beim Hochladen: ' + error.message, 'error');\n" +
			"                submitBtn.disabled = false;\n" +
			"                submitBtn.textContent = 'Speichern';\n" +
			"            }\n" +
			"        });\n" +
			"        \n" +
			"        function showMessage(text, type) {\n" +
			"            messageDiv.textContent = text;\n" +
			"            messageDiv.className = 'message ' + type;\n" +
			"            messageDiv.style.display = 'block';\n" +
			"        }\n" +
			"    </script>\n" +
			"</body>\n" +
			"</html>";
	}
	
	/**
	 * Generate HTML error page
	 * Note: title and message are hardcoded strings, not user input
	 */
	private static String getErrorPage(String title, String message) {
		return "<!DOCTYPE html>\n" +
			"<html lang=\"de\">\n" +
			"<head>\n" +
			"    <meta charset=\"UTF-8\">\n" +
			"    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
			"    <title>" + title + "</title>\n" +
			"    <style>\n" +
			"        * {\n" +
			"            margin: 0;\n" +
			"            padding: 0;\n" +
			"            box-sizing: border-box;\n" +
			"        }\n" +
			"        body {\n" +
			"            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;\n" +
			"            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);\n" +
			"            min-height: 100vh;\n" +
			"            display: flex;\n" +
			"            justify-content: center;\n" +
			"            align-items: center;\n" +
			"            padding: 20px;\n" +
			"        }\n" +
			"        .container {\n" +
			"            background: white;\n" +
			"            border-radius: 12px;\n" +
			"            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);\n" +
			"            padding: 40px;\n" +
			"            max-width: 500px;\n" +
			"            width: 100%;\n" +
			"            text-align: center;\n" +
			"        }\n" +
			"        h1 {\n" +
			"            color: #d9534f;\n" +
			"            margin-bottom: 20px;\n" +
			"            font-size: 28px;\n" +
			"        }\n" +
			"        p {\n" +
			"            color: #666;\n" +
			"            font-size: 16px;\n" +
			"            line-height: 1.6;\n" +
			"        }\n" +
			"    </style>\n" +
			"</head>\n" +
			"<body>\n" +
			"    <div class=\"container\">\n" +
			"        <h1>❌ " + title + "</h1>\n" +
			"        <p>" + message + "</p>\n" +
			"    </div>\n" +
			"</body>\n" +
			"</html>";
	}
}
