package util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;

/**
 * Utility class to cache and manage the image_map.json from GitHub
 * Provides fast lookups using a HashMap
 */
public class ImageMapCache {
  
  private static final String IMAGE_MAP_URL = "https://raw.githubusercontent.com/uniquepixel/lostmanager2/main/assets/image_map.json";
  private static final String GITHUB_ASSETS_BASE_URL = "https://raw.githubusercontent.com/uniquepixel/lostmanager2/main/assets";
  
  // Cache storage
  private static volatile Map<String, JSONObject> imageMapCache = new ConcurrentHashMap<>();
  private static volatile boolean isInitialized = false;
  
  /**
   * Load the image_map.json from GitHub and cache it
   */
  public static synchronized void loadImageMap() {
    try {
      System.out.println("Loading image_map.json from GitHub...");
      
      URL url = new URL(IMAGE_MAP_URL);
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);
      
      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to fetch image_map.json: HTTP " + responseCode);
        return;
      }
      
      StringBuilder jsonContent = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          jsonContent.append(line);
        }
      }
      
      // Parse JSON and populate cache
      JSONObject fullMap = new JSONObject(jsonContent.toString());
      Map<String, JSONObject> newCache = new HashMap<>();
      
      for (String dataId : fullMap.keySet()) {
        JSONObject itemData = fullMap.getJSONObject(dataId);
        newCache.put(dataId, itemData);
      }
      
      // Atomically replace the cache
      imageMapCache = newCache;
      isInitialized = true;
      
      System.out.println("Successfully loaded " + newCache.size() + " entries from image_map.json");
      
    } catch (Exception e) {
      System.err.println("Error loading image_map.json: " + e.getMessage());
      e.printStackTrace();
    }
  }
  
  /**
   * Get item data from cache by data ID
   * @param dataId The data ID to lookup
   * @return JSONObject with item data or null if not found
   */
  public static JSONObject getItemData(String dataId) {
    if (!isInitialized) {
      loadImageMap();
    }
    return imageMapCache.get(dataId);
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
  
  /**
   * Check if cache is initialized
   * @return true if cache has been loaded
   */
  public static boolean isInitialized() {
    return isInitialized;
  }
}
