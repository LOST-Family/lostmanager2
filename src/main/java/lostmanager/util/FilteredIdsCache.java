package lostmanager.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Utility to fetch a list of filtered IDs from GitHub (filtered_ids.json).
 * Structure expected: {"filtered": [int, int, ...]}
 */
public class FilteredIdsCache {

  private static final String FILTERED_URL = "https://raw.githubusercontent.com/LOST-Family/lostmanager2/main/filtered_ids.json";

  private static JSONArray fetchFilteredArray() {
    try {
      URL url = URI.create(FILTERED_URL).toURL();
      HttpURLConnection conn = (HttpURLConnection) url.openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(10000);
      conn.setReadTimeout(10000);

      int responseCode = conn.getResponseCode();
      if (responseCode != 200) {
        System.err.println("Failed to fetch filtered_ids.json: HTTP " + responseCode);
        return null;
      }

      StringBuilder sb = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          sb.append(line);
        }
      }

      JSONObject root = new JSONObject(sb.toString());
      if (root.has("filtered")) {
        return root.getJSONArray("filtered");
      }
      return null;
    } catch (Exception e) {
      System.err.println("Error fetching filtered_ids.json: " + e.getMessage());
      return null;
    }
  }

  public static Set<Integer> getFilteredSet() {
    Set<Integer> set = new HashSet<>();
    JSONArray arr = fetchFilteredArray();
    if (arr == null) return set;
    for (int i = 0; i < arr.length(); i++) {
      try {
        set.add(arr.getInt(i));
      } catch (Exception e) {
        // ignore non-int entries
      }
    }
    return set;
  }

  /**
   * Check if a given dataId is filtered. Tries to parse an integer from the
   * dataId string and checks membership.
   */
  public static boolean isFiltered(String dataId) {
    if (dataId == null) return false;
    try {
      int id = Integer.parseInt(dataId);
      Set<Integer> s = getFilteredSet();
      return s.contains(id);
    } catch (NumberFormatException e) {
      // try to extract digits
      String digits = dataId.replaceAll("\\D+", "");
      if (digits.isEmpty()) return false;
      try {
        int id = Integer.parseInt(digits);
        Set<Integer> s = getFilteredSet();
        return s.contains(id);
      } catch (Exception ex) {
        return false;
      }
    }
  }

}
