package lostmanager.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.json.JSONObject;

/**
 * Utility class to fetch and manage the image_map.json from GitHub
 *
 * The map is held in memory for {@link #CACHE_TTL_MILLIS} after it was loaded.
 * Every lookup method below resolves through the full map, and a single stats
 * page asks for several of them per displayed item - without the cache that is
 * a few hundred downloads of the same file per rendering.
 */
public class ImageMapCache {
  
  private static final String IMAGE_MAP_URL = "https://raw.githubusercontent.com/LOST-Family/lostmanager2/main/image_map.json";
  private static final String GITHUB_ASSETS_BASE_URL = "https://media.githubusercontent.com/media/LOST-Family/lostmanager2/main/assets";

  /**
   * How long a loaded map stays valid. The file only changes when someone
   * pushes to the repository, so minutes of staleness cost nothing; after a
   * data change the bot picks it up within this window (a restart is instant).
   */
  private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

  private static JSONObject cachedMap;
  private static long cachedAtMillis;

  /**
   * Return the cached map, downloading it when it is missing or stale.
   *
   * Synchronized on purpose: when a stats page renders, many lookups arrive at
   * once, and without the lock every one of them would start its own download
   * of the same file. The others wait for the first and then read the cache.
   *
   * @return JSONObject containing the full image map or null if fetch fails
   */
  private static synchronized JSONObject fetchFullMap() {
    long now = System.currentTimeMillis();
    if (cachedMap != null && now - cachedAtMillis < CACHE_TTL_MILLIS) {
      return cachedMap;
    }

    JSONObject freshMap = downloadFullMap();
    if (freshMap != null) {
      cachedMap = freshMap;
      cachedAtMillis = now;
      return freshMap;
    }

    // GitHub unreachable: an outdated map still renders icons, null renders none.
    return cachedMap;
  }

  /**
   * Drop the cached map so the next access downloads it again.
   */
  public static synchronized void invalidate() {
    cachedMap = null;
    cachedAtMillis = 0L;
  }

  /**
   * Download the image_map.json from GitHub
   * @return JSONObject containing the full image map or null if fetch fails
   */
  private static JSONObject downloadFullMap() {
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
   * Fetch the full image map for callers that walk it themselves
   * @return JSONObject containing the full image map or null if fetch fails
   */
  public static JSONObject fetchFullMapOnce() {
    return fetchFullMap();
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
        Object levelData = levels.get(levelKey);
        String path;
        if (levelData instanceof JSONObject levelObject) {
          path = levelObject.optString("level-icon", itemData.optString("character", ""));
        } else {
          path = String.valueOf(levelData);
        }
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
