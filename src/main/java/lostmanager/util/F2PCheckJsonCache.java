package lostmanager.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.json.JSONObject;

/**
 * Utility class to fetch and manage the f2p_check.json from GitHub
 * Fetches data fresh from GitHub on every access
 */
public class F2PCheckJsonCache {

    private static final String F2P_CHECK_URL = "https://raw.githubusercontent.com/LOST-Family/lostmanager2/main/f2p_check.json";

    /**
     * Fetch the f2p_check.json from GitHub
     * 
     * @return JSONObject containing the full map or null if fetch fails
     */
    private static JSONObject fetchFullMap() {
        try {
            URL url = URI.create(F2P_CHECK_URL).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                System.err.println("Failed to fetch f2p_check.json: HTTP " + responseCode);
                return null;
            }

            StringBuilder jsonContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }

            return new JSONObject(jsonContent.toString());

        } catch (Exception e) {
            System.err.println("Error fetching f2p_check.json: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extracts a JSONObject with a given path
     * 
     * @param path The key/path to lookup
     * @return JSONObject with data or null if not found
     */
    public static JSONObject getObject(String path) {
        JSONObject fullMap = fetchFullMap();
        if (fullMap == null) {
            return null;
        }

        // Support simple key lookup
        if (fullMap.has(path)) {
            return fullMap.optJSONObject(path);
        }

        return null;
    }

    /**
     * Fetch the full map
     * 
     * @return The full JSONObject
     */
    public static JSONObject getFullMap() {
        return fetchFullMap();
    }
}
