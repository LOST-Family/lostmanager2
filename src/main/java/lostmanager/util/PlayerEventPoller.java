package lostmanager.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

import lostmanager.Bot;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.PlayerListeningEvent;

/**
 * Drives the player listening events.
 *
 * Clan events know their fire time in advance and are scheduled individually;
 * a player value has no such schedule - it moves whenever the player plays. So
 * the watchers are polled: every tick reads the current value of each watched
 * player once and compares it against what was stored the previous time. Any
 * difference is reported to the owner's DMs and becomes the new stored value.
 *
 * All watchers on the same player share a single API request, so the cost of a
 * tick is one request per distinct watched tag, no matter how many values are
 * watched on it.
 */
public class PlayerEventPoller {

	/**
	 * How often the watched players are read. The Clash of Clans API serves cached
	 * player data anyway, so polling faster than this mostly buys duplicate
	 * responses; it matches the clan event polling interval.
	 */
	private static final long POLL_INTERVAL_MINUTES = 2;

	private PlayerEventPoller() {
	}

	/**
	 * Starts the poll loop on the shared task scheduler. Called from
	 * {@code Bot.restartAllEvents()}, which replaces that scheduler, so this has to
	 * be re-invoked whenever the events are restarted.
	 */
	public static void start() {
		Bot.schedulertasks.scheduleAtFixedRate(PlayerEventPoller::pollSafely, 0, POLL_INTERVAL_MINUTES,
				TimeUnit.MINUTES);
		System.out.println("Player event polling started - checking every " + POLL_INTERVAL_MINUTES + " minutes");
	}

	/**
	 * scheduleAtFixedRate silently cancels the task if it ever throws, which would
	 * stop every player event until the next restart. Nothing may escape here.
	 */
	private static void pollSafely() {
		try {
			poll();
		} catch (final Exception e) {
			System.err.println("Error in player event polling: " + e.getMessage());
		}
	}

	private static void poll() {
		// Bot.restartAllEvents() runs before JDA is built, so the first tick can land
		// without a bot to report through. Skipping the whole tick also keeps
		// last_value from moving past a change that could not have been reported.
		if (Bot.getJda() == null) {
			return;
		}

		ArrayList<PlayerListeningEvent> events = PlayerListeningEvent.getAll();
		if (events.isEmpty()) {
			return;
		}

		// One request per player, not per watcher.
		Map<String, List<PlayerListeningEvent>> byPlayer = new LinkedHashMap<>();
		for (final PlayerListeningEvent event : events) {
			if (event.getPlayerTag() == null) {
				continue;
			}
			byPlayer.computeIfAbsent(event.getPlayerTag(), _ -> new ArrayList<>()).add(event);
		}

		for (final Map.Entry<String, List<PlayerListeningEvent>> entry : byPlayer.entrySet()) {
			try {
				checkPlayer(entry.getKey(), entry.getValue());
			} catch (final Exception e) {
				System.err.println("Player event check failed for " + entry.getKey() + ": " + e.getMessage());
			}
		}
	}

	private static void checkPlayer(String playerTag, List<PlayerListeningEvent> events) {
		Player player = new Player(playerTag);
		String json = player.getJson();
		if (json == null) {
			// The API did not answer. Leaving last_value untouched means the change is
			// still reported on the next successful tick instead of being swallowed.
			System.err.println("Player event: no API response for " + playerTag + ", skipping this tick");
			return;
		}

		JSONObject playerJson;
		try {
			playerJson = new JSONObject(json);
		} catch (final org.json.JSONException e) {
			System.err.println("Player event: unreadable API response for " + playerTag + ": " + e.getMessage());
			return;
		}

		for (final PlayerListeningEvent event : events) {
			try {
				checkEvent(event, player, playerJson);
			} catch (final Exception e) {
				System.err.println("Player event " + event.getId() + " failed: " + e.getMessage());
			}
		}
	}

	private static void checkEvent(PlayerListeningEvent event, Player player, JSONObject playerJson) {
		PlayerListeningEvent.LISTENINGTYPE type = event.getListeningType();
		if (type == null) {
			return;
		}

		Long current = type.readValue(playerJson);
		if (current == null) {
			System.err.println("Player event " + event.getId() + ": " + type.getLabel() + " missing from API response");
			return;
		}

		Long last = event.getLastValue();
		if (last == null) {
			// First sighting: record the baseline, there is nothing to compare against.
			event.storeObservation(current);
			return;
		}

		if (last.longValue() == current.longValue()) {
			// Still record the observation so last_checked shows the watcher is alive.
			event.storeObservation(current);
			return;
		}

		// Store before sending: a DM that bounces (owner closed their DMs) must not
		// make the same change fire again on every following tick.
		event.storeObservation(current);
		event.fireEvent(last, current, player);
	}
}
