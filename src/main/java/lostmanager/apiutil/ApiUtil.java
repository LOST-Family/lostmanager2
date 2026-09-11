package lostmanager.apiutil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lostmanager.Bot;

public class ApiUtil {

	/**
	 * Basisadresse der Clash of Clans-API.
	 *
	 * Konfigurierbar, weil Supercell-Keys an feste IP-Adressen gebunden sind:
	 * an einem Anschluss mit wechselnder IP funktioniert ein solcher Key nicht.
	 * Mit COC_API_BASE_URL=https://cocproxy.royaleapi.dev/v1 laeuft der
	 * Verkehr ueber den RoyaleAPI-Proxy, dessen feste Adresse (45.79.218.79)
	 * beim Erstellen des Keys hinterlegt wird. Ohne gesetzte Variable bleibt
	 * alles wie bisher.
	 */
	public static final String BASIS_URL = System.getenv()
			.getOrDefault("COC_API_BASE_URL", "https://api.clashofclans.com/v1");

    public static ApiResponse raw(String method, String path, Map<String, String> query, String jsonBody) {
        StringBuilder urlBuilder = new StringBuilder(BASIS_URL);
        urlBuilder.append(path);
        if (!query.isEmpty()) {
            urlBuilder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) urlBuilder.append("&");
                urlBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                urlBuilder.append("=");
                urlBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                first = false;
            }
        }

        // Reuse the shared client instead of allocating a new one (with its own
        // connection pool / selector thread) on every single API call.
        HttpClient client = Bot.httpClient;
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(urlBuilder.toString()))
            .header("Authorization", "Bearer " + Bot.api_key)
            .header("Accept", "application/json");

        if ("POST".equals(method) && jsonBody != null) {
            reqBuilder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            reqBuilder.GET();
        }

        try {
            HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
            return new ApiResponse(response.statusCode(), response.body());
        } catch (IOException | InterruptedException e) {
            System.err.println(e.getMessage());
            return new ApiResponse(-1, e.getMessage());
        }
    }
}
