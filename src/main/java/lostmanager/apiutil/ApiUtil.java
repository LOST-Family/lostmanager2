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

	/**
	 * Umschalter fuer Basisadresse und Key, ohne Neustart.
	 *
	 * Steht in API_UMSCHALTER der Pfad einer Datei, wird sie gelesen (hoechstens
	 * alle zehn Sekunden auf eine Aenderung geprueft). Zeilen "url=..." und
	 * "key=..." ueberschreiben die Werte aus der Umgebung; fehlt die Datei oder
	 * eine Zeile, gilt die Umgebung. Anlass: am 25.10.2026 fiel der RoyaleAPI-Proxy
	 * aus, und der Wechsel auf einen direkten Supercell-Key haette sonst einen
	 * Neustart gebraucht. Umgeschaltet wird mit ~/lost/api-umschalten.sh.
	 */
	private static final String UMSCHALTER = System.getenv("API_UMSCHALTER");
	private static volatile String[] umschalterWerte = { null, null };
	private static volatile long umschalterStand = Long.MIN_VALUE;
	private static volatile long umschalterGeprueft = 0;

	private static String[] umschalter() {
		if (UMSCHALTER == null || UMSCHALTER.isBlank()) return umschalterWerte;
		long jetzt = System.currentTimeMillis();
		if (jetzt - umschalterGeprueft < 10_000) return umschalterWerte;
		synchronized (ApiUtil.class) {
			if (jetzt - umschalterGeprueft < 10_000) return umschalterWerte;
			umschalterGeprueft = jetzt;
			java.nio.file.Path datei = java.nio.file.Path.of(UMSCHALTER);
			try {
				long stand = java.nio.file.Files.exists(datei)
						? java.nio.file.Files.getLastModifiedTime(datei).toMillis() : -1;
				if (stand == umschalterStand) return umschalterWerte;
				String url = null, key = null;
				if (stand >= 0) {
					for (String zeile : java.nio.file.Files.readAllLines(datei)) {
						zeile = zeile.strip();
						if (zeile.startsWith("url=") && zeile.length() > 4) url = zeile.substring(4).strip();
						if (zeile.startsWith("key=") && zeile.length() > 4) key = zeile.substring(4).strip();
					}
				}
				umschalterWerte = new String[] { url, key };
				umschalterStand = stand;
				System.out.println("API-Umschalter: " + (url != null ? url : "Adresse aus Umgebung")
						+ ", " + (key != null ? "Key aus Datei" : "Key aus Umgebung"));
			} catch (Exception e) {
				System.err.println("API-Umschalter nicht lesbar, bleibe beim letzten Stand: " + e.getMessage());
			}
		}
		return umschalterWerte;
	}

	/** Aktuelle Basisadresse der API. */
	public static String basisUrl() {
		String url = umschalter()[0];
		return url != null ? url : BASIS_URL;
	}

	/** Aktueller API-Key. */
	public static String apiKey() {
		String key = umschalter()[1];
		return key != null ? key : Bot.api_key;
	}

    public static ApiResponse raw(String method, String path, Map<String, String> query, String jsonBody) {
        StringBuilder urlBuilder = new StringBuilder(basisUrl());
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
            .header("Authorization", "Bearer " + apiKey())
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
