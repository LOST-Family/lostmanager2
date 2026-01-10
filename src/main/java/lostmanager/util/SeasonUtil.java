package lostmanager.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import org.json.JSONObject;

import lostmanager.Bot;

public class SeasonUtil {

	/**
	 * Fetches the current season end time from the Clash of Clans API
	 * @return Timestamp of the season end time, or null if the request failed
	 */
	public static Timestamp fetchSeasonEndTime() {
		try {
			String url = "https://api.clashofclans.com/v1/goldpass/seasons/current";
			
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("Authorization", "Bearer " + Bot.api_key)
					.header("Accept", "application/json")
					.GET()
					.build();
			
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			
			if (response.statusCode() == 200) {
				JSONObject jsonObject = new JSONObject(response.body());
				String endTimeStr = jsonObject.getString("endTime");
				
				// Parse the endTime string (format: "20251201T080000.000Z")
				OffsetDateTime endTime = OffsetDateTime.parse(endTimeStr, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX"));
				return Timestamp.from(endTime.toInstant());
			} else {
				System.err.println("Failed to fetch season end time. HTTP " + response.statusCode());
				return null;
			}
		} catch (Exception e) {
			System.err.println("Error fetching season end time: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Fetches the current season start time from the Clash of Clans API
	 * @return Timestamp of the season start time, or null if the request failed
	 */
	public static Timestamp fetchSeasonStartTime() {
		try {
			String url = "https://api.clashofclans.com/v1/goldpass/seasons/current";
			
			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("Authorization", "Bearer " + Bot.api_key)
					.header("Accept", "application/json")
					.GET()
					.build();
			
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			
			if (response.statusCode() == 200) {
				JSONObject jsonObject = new JSONObject(response.body());
				String startTimeStr = jsonObject.getString("startTime");
				
				// Parse the startTime string (format: "20251101T080000.000Z")
				OffsetDateTime startTime = OffsetDateTime.parse(startTimeStr, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX"));
				return Timestamp.from(startTime.toInstant());
			} else {
				System.err.println("Failed to fetch season start time. HTTP " + response.statusCode());
				return null;
			}
		} catch (Exception e) {
			System.err.println("Error fetching season start time: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}
}
