package lostmanager.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import org.json.JSONObject;

/**
 * Utility class to fetch and manage min_townhall.json from GitHub: the town
 * hall range a building's data ID can appear at in a player's upload. Not
 * every ID has an entry — most buildings never leave the missing-list once
 * unlocked, so absence means "no restriction", not "level 0".
 *
 * The map is held in memory for {@link #CACHE_TTL_MILLIS} after it was
 * loaded, the same as {@link ImageMapCache}: `/stats missing` checks every
 * entry of a statType against it, so without the cache that is a few hundred
 * downloads of the same file per call.
 */
public class MinMaxTownHallCache {

  private static final String MIN_TOWNHALL_URL = "https://raw.githubusercontent.com/LOST-Family/lostmanager2/main/min_townhall.json";

  private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;

  private static JSONObject cachedRoot;
  private static long cachedAtMillis;

  private static synchronized JSONObject fetchRoot() {
    long now = System.currentTimeMillis();
    if (cachedRoot != null && now - cachedAtMillis < CACHE_TTL_MILLIS) {
      return cachedRoot;
    }

    JSONObject fresh = downloadRoot();
    if (fresh != null) {
      cachedRoot = fresh;
      cachedAtMillis = now;
      return fresh;
    }

    // GitHub unreachable: an outdated map still filters correctly, null filters nothing.
    return cachedRoot;
  }

  private static JSONObject downloadRoot() {
    try {
      URL url = URI.create(MIN_TOWNHALL_URL).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to fetch min_townhall.json: HTTP " + responseCode);
        return null;
      }

      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
      }

      return new JSONObject(sb.toString());
    } catch (Exception e) {
      System.err.println("Error fetching min_townhall.json: " + e.getMessage());
      return null;
    }
  }

  /**
   * Drop the cached map so the next access downloads it again.
   */
  public static synchronized void invalidate() {
    cachedRoot = null;
    cachedAtMillis = 0L;
  }

  /**
   * Whether a building data ID can appear in a player's upload at the given
   * town hall level. IDs without an entry in either range are unrestricted
   * (this covers Builder Base IDs too, which never carry a town hall entry).
   * When the file cannot be reached, nothing is filtered.
   *
   * @param dataId        the data ID as it appears in the upload / image map
   * @param townHallLevel the player's town hall level, 0 if unknown
   */
  public static boolean isAvailableAt(String dataId, int townHallLevel) {
    JSONObject root = fetchRoot();
    if (root == null || townHallLevel <= 0) {
      return true;
    }

    JSONObject min = root.optJSONObject("min");
    if (min != null && min.has(dataId) && townHallLevel < min.optInt(dataId)) {
      return false;
    }

    JSONObject max = root.optJSONObject("max");
    if (max != null && max.has(dataId) && townHallLevel > max.optInt(dataId)) {
      return false;
    }

    return true;
  }

}
