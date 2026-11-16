package lostmanager.commands.coc.util.jsonutils;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import lostmanager.datawrapper.Player;
import lostmanager.dbutil.Connection;
import net.dv8tion.jda.api.interactions.commands.Command;

/**
 * Autocomplete choices for "every player with an uploaded JSON", shared by
 * /stats and /f2pcheck.
 *
 * Discord drops an autocomplete that is not answered within three seconds
 * (10062). Both commands used to build this list with three to four queries
 * per uploaded tag on every keystroke (70 tags: more than 200 round trips on
 * the one shared connection), and /f2pcheck additionally asked the CoC API for
 * every tag without a name in {@code players}. That ran into the limit ten
 * times in a week. Here it is one query, and a missing name never blocks: the
 * choice shows the bare tag and the name is fetched in the background for the
 * next keystroke.
 */
final class UploadedPlayerChoices {

	static final int MAX_CHOICES = 25;
	/** Discord's limit for the visible text of a choice. */
	static final int MAX_NAME_LENGTH = 100;

	record Entry(String tag, String name, String clanName) {
	}

	private static final String SQL = "SELECT u.tag, p.name, "
			+ "(SELECT c.name FROM clan_members m JOIN clans c ON c.tag = m.clan_tag "
			+ "WHERE m.player_tag = u.tag LIMIT 1) AS clan_name "
			+ "FROM (SELECT DISTINCT tag FROM userjsons) u LEFT JOIN players p ON p.coc_tag = u.tag ORDER BY u.tag";

	/** Names of players the database does not know, tag -> name from the API. */
	private static final Map<String, String> API_NAMES = new ConcurrentHashMap<>();
	private static final Set<String> LOOKUP_RUNNING = ConcurrentHashMap.newKeySet();
	private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "UploadedPlayerNameLookup");
		t.setDaemon(true);
		return t;
	});

	private UploadedPlayerChoices() {
	}

	/** Every tag with an upload, with name and clan name where the database has them. */
	static List<Entry> load() {
		List<Entry> entries = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(SQL);
				ResultSet rs = pstmt.executeQuery()) {
			while (rs.next()) {
				entries.add(new Entry(rs.getString(1), rs.getString(2), rs.getString(3)));
			}
		} catch (SQLException e) {
			System.err.println("UploadedPlayerChoices.load failed: " + e.getMessage());
		}
		return entries;
	}

	/** The choices for a privileged user: all uploads, filtered by what was typed. */
	static List<Command.Choice> forAll(String input) {
		return choices(load(), input, UploadedPlayerChoices::cachedApiName);
	}

	/**
	 * Pure part: text, filter and limit. A choice reads "Name (#TAG) - Clan"; an
	 * entry matches if that text contains the input or the tag starts with it
	 * (both ignoring case).
	 *
	 * @param fallbackName name for a tag without one in the database; may return
	 *                     null, then the bare tag is shown. Must not block.
	 */
	static List<Command.Choice> choices(List<Entry> entries, String input, Function<String, String> fallbackName) {
		String needle = input == null ? "" : input.toLowerCase();
		List<Command.Choice> choices = new ArrayList<>();
		for (Entry e : entries) {
			if (e.tag() == null || e.tag().isBlank()) {
				continue;
			}
			String display = display(e, fallbackName);
			if (display.toLowerCase().contains(needle) || e.tag().toLowerCase().startsWith(needle)) {
				choices.add(new Command.Choice(display, e.tag()));
				if (choices.size() == MAX_CHOICES) {
					break;
				}
			}
		}
		return choices;
	}

	static String display(Entry e, Function<String, String> fallbackName) {
		String name = e.name();
		if (name == null || name.isBlank()) {
			name = fallbackName == null ? null : fallbackName.apply(e.tag());
		}
		String display = name == null || name.isBlank() ? e.tag() : name + " (" + e.tag() + ")";
		if (e.clanName() != null && !e.clanName().isEmpty()) {
			display += " - " + e.clanName();
		}
		return display.length() > MAX_NAME_LENGTH ? display.substring(0, MAX_NAME_LENGTH) : display;
	}

	/**
	 * Name from the API if a previous keystroke already fetched it; otherwise
	 * null, and the lookup is started in the background (one at a time, once per
	 * tag at a time).
	 */
	private static String cachedApiName(String tag) {
		String known = API_NAMES.get(tag);
		if (known != null) {
			return known;
		}
		if (LOOKUP_RUNNING.add(tag)) {
			LOOKUPS.execute(() -> {
				try {
					String name = new Player(tag).getNameAPI();
					// getNameAPI answers with the tag itself when the profile cannot be
					// fetched - that is not a name, and the next keystroke may try again.
					if (name != null && !name.isBlank() && !name.equals(tag)) {
						API_NAMES.put(tag, name);
					}
				} catch (Throwable t) {
					// no name, the bare tag stays
				} finally {
					LOOKUP_RUNNING.remove(tag);
				}
			});
		}
		return null;
	}
}
