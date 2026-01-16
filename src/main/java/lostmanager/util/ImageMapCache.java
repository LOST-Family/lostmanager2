package lostmanager.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.json.JSONObject;

/**
 * Utility class to fetch and manage the image_map.json from GitHub
 * Fetches data fresh from GitHub on every access
 */
public class ImageMapCache {
  
  private static final String IMAGE_MAP_URL = "https://raw.githubusercontent.com/LOST-Family/lostmanager2/main/image_map.json";
  private static final String GITHUB_ASSETS_BASE_URL = "https://media.githubusercontent.com/media/LOST-Family/lostmanager2/main/assets";
  
  /**
   * Fetch the image_map.json from GitHub
   * @return JSONObject containing the full image map or null if fetch fails
   */
  private static JSONObject fetchFullMap() {
    try {
      URL url = URI.create(IMAGE_MAP_URL).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to fetch image_map.json: HTTP " + responseCode);
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
      System.err.println("Error fetching image_map.json: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * Get item data by data ID
   * @param dataId The data ID to lookup
   * @return JSONObject with item data or null if not found
   */
  public static JSONObject getItemData(String dataId) {
    JSONObject fullMap = fetchFullMap();
    if (fullMap == null || !fullMap.has(dataId)) {
      return null;
    }
    return fullMap.getJSONObject(dataId);
  }
  
  /**
   * Get the name for a data ID
   * @param dataId The data ID to lookup
   * @return The name or null if not found
   */
  public static String getName(String dataId) {
    JSONObject itemData = getItemData(dataId);
    if (itemData != null && itemData.has("name")) {
      return itemData.getString("name");
    }
    return null;
  }

  /**
   * Get the price for a data ID
   * @param dataId The data ID to lookup
   * @return The price or null if not found
   */
  public static String getPrice(String dataId) {
    JSONObject itemData = getItemData(dataId);
    if (itemData != null && itemData.has("price")) {
      return itemData.getString("price");
    }
    return null;
  }
  
  /**
   * Get the icon path for a data ID (for items without levels)
   * @param dataId The data ID to lookup
   * @return The relative icon path or null if not found
   */
  public static String getIconPath(String dataId) {
    JSONObject itemData = getItemData(dataId);
    if (itemData != null && itemData.has("icon")) {
      String icon = itemData.getString("icon");
      if (icon != null && !icon.isEmpty()) {
        return icon;
      }
    }
    return null;
  }
  
  /**
   * Get the level-specific image path for a data ID
   * @param dataId The data ID to lookup
   * @param level The level number
   * @return The relative image path or null if not found
   */
  public static String getLevelPath(String dataId, int level) {
    JSONObject itemData = getItemData(dataId);
    if (itemData != null && itemData.has("levels")) {
      JSONObject levels = itemData.getJSONObject("levels");
      String levelKey = String.valueOf(level);
      if (levels.has(levelKey)) {
        String path = levels.getString(levelKey);
        if (path != null && !path.isEmpty()) {
          return path;
        }
      }
    }
    return null;
  }
  
  /**
   * Check if item has levels
   * @param dataId The data ID to check
   * @return true if item has levels, false otherwise
   */
  public static boolean hasLevels(String dataId) {
    JSONObject itemData = getItemData(dataId);
    return itemData != null && itemData.has("levels");
  }
  
  /**
   * Build full GitHub URL for an image path
   * @param relativePath The relative path from image_map.json
   * @return The full GitHub raw content URL
   */
  public static String buildImageUrl(String relativePath) {
    if (relativePath == null || relativePath.isEmpty()) {
      return null;
    }
    return GITHUB_ASSETS_BASE_URL + relativePath;
  }
  
}
