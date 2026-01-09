package commands.coc.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

import datawrapper.Player;
import datawrapper.User;
import dbutil.DBManager;
import dbutil.DBUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import util.MessageUtil;

public class stats extends ListenerAdapter {

	// Constants for button and select menu ID prefixes
	private static final String BUTTON_PREFIX = "stats_refresh_";
	private static final String SELECT_PREFIX = "stats_select_";

	// Mapping of stat options to JSON field names
	private static final Map<String, String> STAT_TO_FIELD = new HashMap<>();

	static {
		STAT_TO_FIELD.put("Helpers", "helpers");
		STAT_TO_FIELD.put("Guardians", "guardians");
		STAT_TO_FIELD.put("Buildings", "buildings");
		STAT_TO_FIELD.put("Buildings (BB)", "buildings2");
		STAT_TO_FIELD.put("Traps", "traps");
		STAT_TO_FIELD.put("Traps (BB)", "traps2");
		STAT_TO_FIELD.put("Decos", "decos");
		STAT_TO_FIELD.put("Decos (BB)", "decos2");
		STAT_TO_FIELD.put("Obstacles", "obstacles");
		STAT_TO_FIELD.put("Obstacles (BB)", "obstacles2");
		STAT_TO_FIELD.put("Units", "units");
		STAT_TO_FIELD.put("Units (BB)", "units2");
		STAT_TO_FIELD.put("Sieges", "siege_machines");
		STAT_TO_FIELD.put("Heroes", "heroes");
		STAT_TO_FIELD.put("Heroes (BB)", "heroes2");
		STAT_TO_FIELD.put("Spells", "spells");
		STAT_TO_FIELD.put("Pets", "pets");
		STAT_TO_FIELD.put("Equips", "equipment");
		STAT_TO_FIELD.put("House Parts", "house_parts");
		STAT_TO_FIELD.put("Skins", "skins");
		STAT_TO_FIELD.put("Skins (BB)", "skins2");
		STAT_TO_FIELD.put("Sceneries", "sceneries");
		STAT_TO_FIELD.put("Sceneries (BB)", "sceneries2");
	}

	// German translations for attributes
	private static final Map<String, String> ATTR_TRANSLATIONS = new HashMap<>();

	static {
		ATTR_TRANSLATIONS.put("cnt", "Anzahl");
		ATTR_TRANSLATIONS.put("lvl", "Level");
		ATTR_TRANSLATIONS.put("supercharge", "Supercharge-Level");
		ATTR_TRANSLATIONS.put("timer", "Dauer");
		ATTR_TRANSLATIONS.put("helper_recurrent", "Wiederholender Helfer");
		ATTR_TRANSLATIONS.put("gear_up", "Entwicklung");
		ATTR_TRANSLATIONS.put("helper_cooldown", "Helfer-Abklingzeit");
		ATTR_TRANSLATIONS.put("types", "Typen");
		ATTR_TRANSLATIONS.put("modules", "Module");
	}

	// Attributes to exclude from configuration key for grouping
	// These attributes don't affect item identity for grouping purposes
	private static final Set<String> GROUPING_EXCLUDED_ATTRS = new HashSet<>();

	static {
		GROUPING_EXCLUDED_ATTRS.add("data");
		GROUPING_EXCLUDED_ATTRS.add("cnt");
		GROUPING_EXCLUDED_ATTRS.add("gear_up");
		GROUPING_EXCLUDED_ATTRS.add("timer");
		GROUPING_EXCLUDED_ATTRS.add("helper_cooldown");
	}

	// Indentation level for flattened hierarchy to avoid Discord's deep indent handling
	// All nested levels use this same indent value to keep indentation at maximum 1 level
	private static final int FLATTENED_INDENT_LEVEL = 1;

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("stats"))
			return;

		String title = "Spieler Stats";
		event.deferReply().queue();

		new Thread(() -> {
			// Get parameters
			OptionMapping playerOption = event.getOption("player");
			OptionMapping statOption = event.getOption("stat");

			if (playerOption == null || statOption == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Parameter 'player' und 'stat' sind erforderlich.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String playerTag = playerOption.getAsString();
			String statType = statOption.getAsString();

			// Check permissions and access
			User userExecuted = new User(event.getUser().getId());
			if (!canAccessPlayer(userExecuted, playerTag)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			// Create button ID with encoded data
			String buttonId = encodeButtonId(playerTag, statType);

			performStatsDisplay(event.getHook(), title, playerTag, statType, buttonId);

		}, "StatsCommand-" + event.getUser().getId()).start();
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("stats"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();

			if (focused.equals("player")) {
				String input = event.getFocusedOption().getValue();
				User userExecuted = new User(event.getUser().getId());

				// Get available players based on permissions
				List<Command.Choice> choices = getAvailablePlayers(userExecuted, input);

				event.replyChoices(choices).queue(_ -> {
				}, error -> System.err.println("Error replying to autocomplete: " + error.getMessage()));
			}
		}, "StatsAutocomplete-" + event.getUser().getId()).start();
	}

	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith(BUTTON_PREFIX))
			return;

		event.deferEdit().queue();

		String title = "Spieler Stats";

		new Thread(() -> {
			// Check permissions
			User userExecuted = new User(event.getUser().getId());

			// Decode button ID to extract parameters
			try {
				String[] params = decodeButtonId(id);
				if (params == null || params.length < 2) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String playerTag = params[0];
				String statType = params[1];

				// Verify access
				if (!canAccessPlayer(userExecuted, playerTag)) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				performStatsDisplay(event.getHook(), title, playerTag, statType, id);

			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "StatsRefresh-" + event.getUser().getId()).start();
	}

	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith(SELECT_PREFIX))
			return;

		event.deferEdit().queue();

		String title = "Spieler Stats";

		new Thread(() -> {
			// Check permissions
			User userExecuted = new User(event.getUser().getId());

			// Decode select menu ID to extract player tag
			try {
				String playerTag = decodeSelectMenuId(id);
				if (playerTag == null) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Select-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				// Get selected stat
				String newStatType = event.getValues().get(0);

				// Verify access
				if (!canAccessPlayer(userExecuted, playerTag)) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				// Create new button ID
				String buttonId = encodeButtonId(playerTag, newStatType);

				performStatsDisplay(event.getHook(), title, playerTag, newStatType, buttonId);

			} catch (Exception e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Select-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "StatsSelectMenu-" + event.getUser().getId()).start();
	}

	/**
	 * Check if a user can access a specific player's data
	 */
	private boolean canAccessPlayer(User user, String playerTag) {
		// Check if user is at least coleader in any clan
		boolean hasPermission = false;
		for (String clantag : DBManager.getAllClans()) {
			Player.RoleType role = user.getClanRoles().get(clantag);
			if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER || role == Player.RoleType.COLEADER) {
				hasPermission = true;
				break;
			}
		}

		if (hasPermission) {
			// Can see all players
			return true;
		}

		// Check if player is linked to this user
		ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();
		for (Player player : linkedAccounts) {
			if (player.getTag().equals(playerTag)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Get list of available players for autocomplete based on permissions
	 */
	private List<Command.Choice> getAvailablePlayers(User user, String input) {
		List<Command.Choice> choices = new ArrayList<>();

		// Check if user is at least coleader in any clan
		boolean hasPermission = false;
		for (String clantag : DBManager.getAllClans()) {
			Player.RoleType role = user.getClanRoles().get(clantag);
			if (role == Player.RoleType.ADMIN || role == Player.RoleType.LEADER || role == Player.RoleType.COLEADER) {
				hasPermission = true;
				break;
			}
		}

		if (hasPermission) {
			// Get all players with uploaded JSONs
			String sql = "SELECT DISTINCT tag FROM userjsons WHERE tag ILIKE ? ORDER BY tag LIMIT 25";
			List<String> tags = DBUtil.getArrayListFromSQL(sql, String.class, "%" + input + "%");

			for (String tag : tags) {
				// Try to get player name for better display
				String playerName = getPlayerName(tag);
				String displayName = playerName != null ? playerName + " (" + tag + ")" : tag;
				choices.add(new Command.Choice(displayName, tag));
			}
		} else {
			// Get only linked accounts with uploaded JSONs
			ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();
			String inputLower = input.toLowerCase();

			for (Player player : linkedAccounts) {
				String tag = player.getTag();
				// Check if this player has any JSON uploaded
				String sql = "SELECT COUNT(*) FROM userjsons WHERE tag = ?";
				Integer count = DBUtil.getValueFromSQL(sql, Integer.class, tag);

				if (count != null && count > 0) {
					// Filter by input
					if (tag.toLowerCase().contains(inputLower)
							|| player.getNameAPI().toLowerCase().contains(inputLower)) {
						String displayName = player.getNameAPI() + " (" + tag + ")";
						choices.add(new Command.Choice(displayName, tag));
					}
				}
			}
		}

		return choices;
	}

	/**
	 * Get player name from database or JSON
	 */
	private String getPlayerName(String tag) {
		// Try players table first
		String sql = "SELECT name FROM players WHERE coc_tag = ?";
		String name = DBUtil.getValueFromSQL(sql, String.class, tag);

		if (name == null) {
			// Try to get from JSON
			sql = "SELECT json->>'name' FROM userjsons WHERE tag = ? LIMIT 1";
			name = DBUtil.getValueFromSQL(sql, String.class, tag);
		}

		return name;
	}

	/**
	 * Display stats for a player
	 */
	private void performStatsDisplay(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String playerTag, String statType, String buttonId) {

		// Get JSON data from database
		String sql = "SELECT json, timestamp FROM userjsons WHERE tag = ? LIMIT 1";

		try (java.sql.PreparedStatement pstmt = dbutil.Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, playerTag);

			try (java.sql.ResultSet rs = pstmt.executeQuery()) {
				if (!rs.next()) {
					hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Keine JSON-Daten für diesen Spieler gefunden.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				String jsonStr = rs.getString("json");
				java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");

				JSONObject json = new JSONObject(jsonStr);

				// Get the field name for this stat
				String fieldName = STAT_TO_FIELD.get(statType);
				if (fieldName == null) {
					hook.editOriginalEmbeds(
							MessageUtil.buildEmbed(title, "Ungültiger Stat-Typ.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				// Check if field exists in JSON
				if (!json.has(fieldName)) {
					hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Keine Daten für **" + statType + "** gefunden.", MessageUtil.EmbedType.INFO)).queue();
					return;
				}

				Object fieldData = json.get(fieldName);

				// Format the data
				String formattedData = formatData(fieldData, timestamp);

				// Build description
				StringBuilder description = new StringBuilder();
				String playerName = getPlayerName(playerTag);
				description.append("**Spieler:** ").append(playerName != null ? playerName : playerTag).append("\n");
				description.append("**Stat:** ").append(statType).append("\n\n");
				description.append(formattedData);
				description.append("\n");

				// Create buttons
				Button refreshButton = Button.secondary(buttonId, "\u200B").withEmoji(Emoji.fromUnicode("🔁"));

				String selectMenuId = encodeSelectMenuId(playerTag);
				StringSelectMenu selectMenu = StringSelectMenu.create(selectMenuId).setPlaceholder("Anderes Feld")
						.addOptions(createStatOptions(statType)).build();

				// Add timestamp
				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String formatiert = jetzt.format(formatter);

				hook.editOriginal("").setEmbeds(MessageUtil.buildEmbed(title, description.toString(),
						MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
						.setActionRow(refreshButton).queue(message -> {
							// Add select menu in second row
							message.editMessageComponents(
									net.dv8tion.jda.api.interactions.components.ActionRow.of(refreshButton),
									net.dv8tion.jda.api.interactions.components.ActionRow.of(selectMenu)).queue();
						});
			}
		} catch (java.sql.SQLException e) {
			System.err.println("Database error loading stats data: " + e.getMessage());
			e.printStackTrace();
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Laden der Daten aus der Datenbank: " + e.getMessage(), MessageUtil.EmbedType.ERROR))
					.queue();
		} catch (org.json.JSONException e) {
			System.err.println("JSON parsing error: " + e.getMessage());
			e.printStackTrace();
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Verarbeiten der JSON-Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
		} catch (Exception e) {
			System.err.println("Unexpected error loading stats data: " + e.getMessage());
			e.printStackTrace();
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Unerwarteter Fehler beim Laden der Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR))
					.queue();
		}
	}

	/**
	 * Format data for display
	 */
	private String formatData(Object data, java.sql.Timestamp jsonTimestamp) {
		if (data == null || data == JSONObject.NULL) {
			return "Keine Daten vorhanden";
		}

		StringBuilder sb = new StringBuilder();

		if (data instanceof JSONArray) {
			JSONArray arr = (JSONArray) data;
			if (arr.length() == 0) {
				return "Keine Daten vorhanden";
			}

			// Check if array contains JSONObjects with "data" field
			boolean hasDataField = false;
			for (int i = 0; i < arr.length(); i++) {
				Object item = arr.get(i);
				if (item instanceof JSONObject && ((JSONObject) item).has("data")) {
					hasDataField = true;
					break;
				}
			}

			if (hasDataField) {
				// Group by data ID and format
				sb.append(formatGroupedData(arr, jsonTimestamp));
			} else {
				// Original formatting for simple arrays
				for (int i = 0; i < arr.length(); i++) {
					Object item = arr.get(i);
					if (item instanceof JSONObject) {
						sb.append(formatObject((JSONObject) item, 0, jsonTimestamp));
						sb.append("\n");
					} else {
						// Simple values (e.g., house_parts, skins, sceneries)
						String value = getMappedValue(item.toString());
						sb.append("- ").append(value).append("\n");
					}

					if (i < arr.length() - 1) {
						sb.append("\n");
					}
				}
			}
		} else if (data instanceof JSONObject) {
			sb.append(formatObject((JSONObject) data, 0, jsonTimestamp));
		} else {
			sb.append(data.toString());
		}

		return sb.toString();
	}

	/**
	 * Format grouped data by data ID
	 */
	private String formatGroupedData(JSONArray arr, java.sql.Timestamp jsonTimestamp) {
		// Group objects by their data ID
		Map<String, List<JSONObject>> groupedByData = new LinkedHashMap<>();

		for (int i = 0; i < arr.length(); i++) {
			Object item = arr.get(i);
			if (item instanceof JSONObject) {
				JSONObject obj = (JSONObject) item;
				if (obj.has("data")) {
					String dataId = obj.get("data").toString();
					groupedByData.computeIfAbsent(dataId, _ -> new ArrayList<>()).add(obj);
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		boolean first = true;

		// Process each data ID group
		for (Map.Entry<String, List<JSONObject>> entry : groupedByData.entrySet()) {
			if (!first) {
				sb.append("\n\n");
			}
			first = false;

			String dataId = entry.getKey();
			List<JSONObject> objects = entry.getValue();

			// Get the name for this data ID
			String mappedValue = getMappedValue(dataId);
			sb.append(mappedValue);

			// Group by attributes (excluding "data", "cnt", "gear_up", "timer", and "helper_cooldown")
			Map<String, ConfigGroup> configGroups = new TreeMap<>(new AttributeComparator());

			for (JSONObject obj : objects) {
				// Create a key from all attributes except "data", "cnt", "gear_up", "timer", and "helper_cooldown"
				String configKey = createConfigKey(obj);
				
				ConfigGroup group = configGroups.computeIfAbsent(configKey, _ -> new ConfigGroup(obj));
				
				// Add count
				int cnt = obj.has("cnt") ? obj.optInt("cnt", 1) : 1;
				group.totalCount += cnt;
				
				// Track gear_up
				if (obj.has("gear_up")) {
					int gearUp = obj.optInt("gear_up", 0);
					if (gearUp > 0) {
						group.gearedUpCount += cnt;
					}
				}
			}

			// Determine if we need to show counts (only if multiple groups OR count > 1)
			boolean showCounts = configGroups.size() > 1 || 
				configGroups.values().stream().anyMatch(g -> g.totalCount > 1);
			
			// Display grouped configurations
			for (ConfigGroup group : configGroups.values()) {
				// Determine indentation based on whether we're showing counts
				// For consistency, always use fixed indent strings that don't accumulate
				String countIndent = "  - ";  // Fixed indent for Anzahl
				String baseIndent = showCounts ? "    - " : "  - ";
				int objIndent = showCounts ? 3 : 2;
				String arrItemIndent = showCounts ? "      - " : "    - ";
				
				// Only show count if there's actual grouping or multiple items
				if (showCounts) {
					sb.append("\n").append(countIndent).append("Anzahl: ").append(group.totalCount);
				}
				
				// Get and sort keys for consistent display order
				// Note: timer and helper_cooldown are included here for display, even though they don't affect grouping
				List<String> sortedKeys = new ArrayList<>();
				for (String key : group.representative.keySet()) {
					if (!key.equals("data") && !key.equals("cnt") && !key.equals("gear_up")) {
						sortedKeys.add(key);
					}
				}
				// Sort keys: lvl first, then supercharge, then alphabetically
				sortedKeys.sort(new AttributeDisplayComparator());
				
				// Display attributes
				for (String key : sortedKeys) {
					Object value = group.representative.get(key);
					if (value == null || value == JSONObject.NULL) {
						continue;
					}

					if (key.equals("timer") || key.equals("helper_cooldown")) {
						// Special handling for timer
						int timerSeconds = 0;
						if (value instanceof Number) {
							timerSeconds = ((Number) value).intValue();
						}

						// Calculate remaining time
						long elapsedSeconds = (System.currentTimeMillis() - jsonTimestamp.getTime()) / 1000;
						long remainingSeconds = timerSeconds - elapsedSeconds;

						if (remainingSeconds > 0) {
							sb.append("\n").append(baseIndent);
							String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
							sb.append(translatedKey).append(": ");
							String timerStr = formatTimerRemaining(remainingSeconds);
							sb.append(timerStr);
						}
						// Skip timer if expired - don't add any output
					} else {
						sb.append("\n").append(baseIndent);
						String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
						sb.append(translatedKey).append(": ");

						if (value instanceof JSONObject) {
							sb.append("\n").append(formatObject((JSONObject) value, objIndent, jsonTimestamp));
						} else if (value instanceof JSONArray) {
							JSONArray valueArr = (JSONArray) value;
							if (valueArr.length() > 0) {
								for (int i = 0; i < valueArr.length(); i++) {
									Object arrItem = valueArr.get(i);
									if (arrItem instanceof JSONObject) {
										sb.append("\n").append(formatObject((JSONObject) arrItem, objIndent, jsonTimestamp));
									} else {
										String mappedArrValue = getMappedValue(arrItem.toString());
										sb.append("\n").append(arrItemIndent).append(mappedArrValue);
									}
								}
							}
						} else {
							String valueStr = value.toString();
							if (value instanceof Boolean) {
								valueStr = (Boolean) value ? "Ja" : "Nein";
							}
							sb.append(valueStr);
						}
					}
				}
				
				// Show geared up count if applicable
				if (group.gearedUpCount > 0) {
					sb.append("\n").append(baseIndent).append("Entwickelt: ").append(group.gearedUpCount);
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Create a configuration key from a JSON object
	 * Excludes attributes defined in GROUPING_EXCLUDED_ATTRS
	 */
	private String createConfigKey(JSONObject obj) {
		StringBuilder key = new StringBuilder();
		
		// Get all keys except those in GROUPING_EXCLUDED_ATTRS, sort them for consistent ordering
		List<String> keys = new ArrayList<>();
		for (String k : obj.keySet()) {
			if (!GROUPING_EXCLUDED_ATTRS.contains(k)) {
				keys.add(k);
			}
		}
		keys.sort(String::compareTo);
		
		// Build key from sorted attributes
		for (String k : keys) {
			Object value = obj.get(k);
			if (value != null && value != JSONObject.NULL) {
				if (key.length() > 0) {
					key.append("|");
				}
				key.append(k).append("=").append(value.toString());
			}
		}
		
		return key.toString();
	}

	/**
	 * Helper class to group configurations
	 */
	private static class ConfigGroup {
		JSONObject representative;
		int totalCount = 0;
		int gearedUpCount = 0;
		
		ConfigGroup(JSONObject obj) {
			this.representative = obj;
		}
	}

	/**
	 * Comparator to sort configuration groups by their attributes
	 */
	private static class AttributeComparator implements Comparator<String> {
		@Override
		public int compare(String key1, String key2) {
			// Parse and compare configuration keys
			// Priority: lvl (descending), then other attributes
			
			Map<String, String> attrs1 = parseConfigKey(key1);
			Map<String, String> attrs2 = parseConfigKey(key2);
			
			// Compare by level first (descending)
			String lvl1 = attrs1.get("lvl");
			String lvl2 = attrs2.get("lvl");
			if (lvl1 != null && lvl2 != null) {
				try {
					int lvlCmp = Integer.compare(Integer.parseInt(lvl2), Integer.parseInt(lvl1));
					if (lvlCmp != 0) {
						return lvlCmp;
					}
				} catch (NumberFormatException e) {
					// If not numeric, compare as strings
				}
			}
			
			// Fall back to string comparison
			return key1.compareTo(key2);
		}
		
		private Map<String, String> parseConfigKey(String key) {
			Map<String, String> attrs = new HashMap<>();
			String[] parts = key.split("\\|");
			for (String part : parts) {
				String[] kv = part.split("=", 2);
				if (kv.length == 2) {
					attrs.put(kv[0], kv[1]);
				}
			}
			return attrs;
		}
	}

	/**
	 * Comparator to sort attribute keys for display
	 * Priority: lvl, supercharge, then alphabetically
	 */
	private static class AttributeDisplayComparator implements Comparator<String> {
		@Override
		public int compare(String k1, String k2) {
			if (k1.equals("lvl")) return -1;
			if (k2.equals("lvl")) return 1;
			if (k1.equals("supercharge")) return -1;
			if (k2.equals("supercharge")) return 1;
			return k1.compareTo(k2);
		}
	}

	/**
	 * Format a JSON object with indentation
	 * Uses fixed indentation levels to avoid Discord's special formatting for deep indents
	 * Discord treats indentation deeper than 1 level differently, so we flatten all attributes
	 * to use the same 2-space indentation level
	 */
	private String formatObject(JSONObject obj, int indent, java.sql.Timestamp jsonTimestamp) {
		StringBuilder sb = new StringBuilder();
		// To avoid Discord's special handling of deep indentation, we use a flat system:
		// - Data fields: 4 spaces (visual sub-item, no dash)
		// - Attributes: 2 spaces + dash (all at same level regardless of nesting)
		// This keeps indentation at maximum 1 level (2 spaces) for attributes
		
		String dataIndentStr;
		String attrIndentStr;
		
		if (indent == 0) {
			// Top-level objects have no indentation
			dataIndentStr = "";
			attrIndentStr = "";
		} else {
			// All nested objects use flat indentation:
			// - Data fields always use 4 spaces (no dash)
			// - Attributes always use 2 spaces + dash (added separately)
			dataIndentStr = "    ";
			attrIndentStr = "  ";
		}

		// First, display the "data" field if it exists (as the identifier)
		if (obj.has("data") && obj.get("data") != null && obj.get("data") != JSONObject.NULL) {
			String mappedValue = getMappedValue(obj.get("data").toString());
			sb.append(dataIndentStr).append(mappedValue);
		}

		// Then display all other fields
		for (String key : obj.keySet()) {
			if (key.equals("data")) {
				continue; // Already displayed above
			}

			Object value = obj.get(key);

			if (value == null || value == JSONObject.NULL) {
				continue; // Skip null values
			}

			if (key.equals("timer") || key.equals("helper_cooldown")) {
				// Special handling for timer
				int timerSeconds = 0;
				if (value instanceof Number) {
					timerSeconds = ((Number) value).intValue();
				}

				// Calculate remaining time
				long elapsedSeconds = (System.currentTimeMillis() - jsonTimestamp.getTime()) / 1000;
				long remainingSeconds = timerSeconds - elapsedSeconds;

				if (remainingSeconds > 0) {
					String timerStr = formatTimerRemaining(remainingSeconds);
					String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
					sb.append("\n").append(attrIndentStr).append("- ").append(translatedKey).append(": ").append(timerStr);
				}
				// Don't show timer if it has already expired
			} else if (value instanceof JSONObject) {
				String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
				sb.append("\n").append(attrIndentStr).append("- ").append(translatedKey).append(":");
				// Nested objects use flattened indent to avoid Discord's deep indent handling
				sb.append("\n").append(formatObject((JSONObject) value, FLATTENED_INDENT_LEVEL, jsonTimestamp));
			} else if (value instanceof JSONArray) {
				String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
				JSONArray arr = (JSONArray) value;
				if (arr.length() > 0) {
					sb.append("\n").append(attrIndentStr).append("- ").append(translatedKey).append(":");
					for (int i = 0; i < arr.length(); i++) {
						Object item = arr.get(i);
						if (item instanceof JSONObject) {
							// Array item objects use flattened indent
							sb.append("\n").append(formatObject((JSONObject) item, FLATTENED_INDENT_LEVEL, jsonTimestamp));
						} else {
							String mappedValue = getMappedValue(item.toString());
							// Simple values use data indentation (4 spaces, no dash) for visual nesting
							sb.append("\n").append(dataIndentStr).append(mappedValue);
						}
					}
				}
			} else {
				String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
				String valueStr = value.toString();

				// Handle boolean values
				if (value instanceof Boolean) {
					valueStr = (Boolean) value ? "Ja" : "Nein";
				}

				sb.append("\n").append(attrIndentStr).append("- ").append(translatedKey).append(": ").append(valueStr);
			}
		}

		return sb.toString();
	}

	/**
	 * Format remaining timer duration
	 */
	private String formatTimerRemaining(long seconds) {
		long days = seconds / 86400;
		seconds %= 86400;
		long hours = seconds / 3600;
		seconds %= 3600;
		long minutes = seconds / 60;
		seconds %= 60;

		StringBuilder sb = new StringBuilder();
		if (days > 0) {
			sb.append(days).append("d ");
		}
		if (hours > 0) {
			sb.append(hours).append("h ");
		}
		if (minutes > 0) {
			sb.append(minutes).append("m ");
		}
		if (seconds > 0 || sb.length() == 0) {
			sb.append(seconds).append("s");
		}
		sb.append(" verbleibend");

		return sb.toString().trim();
	}

	/**
	 * Get mapped value from datamappings table or return raw value
	 * Format: Name (space) Emoji (if both available), or Name (if no emoji), or dataValue (if no name)
	 * Example: "Walls <:walls:123456789>"
	 */
	private String getMappedValue(String dataValue) {
		// Query datamappings table
		String sql = "SELECT emojiid, name, emojiname FROM datamappings WHERE datavalue = ?";

		try (java.sql.PreparedStatement pstmt = dbutil.Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, dataValue);

			try (java.sql.ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					String emojiId = rs.getString("emojiid");
					String name = rs.getString("name");
					String emojiName = rs.getString("emojiname");

					boolean hasName = name != null && !name.isEmpty();
					boolean hasEmoji = isValidEmoji(emojiId, emojiName);

					// If both name and emoji exist, return "Name <:emojiName:emojiId>" format
					if (hasName && hasEmoji) {
						return name + " <:" + emojiName + ":" + emojiId + ">";
					}
					
					// If only emoji exists, return emoji
					if (hasEmoji) {
						return "<:" + emojiName + ":" + emojiId + ">";
					}

					// If only name exists, return name
					if (hasName) {
						return name;
					}
				}
			}
		} catch (java.sql.SQLException e) {
			// Database query failed, log and return raw value
			System.err.println("Error querying datamappings for value '" + dataValue + "': " + e.getMessage());
		}

		// Return raw data value if no mapping found
		return dataValue;
	}

	/**
	 * Check if emoji data is valid for Discord custom emoji format
	 */
	private boolean isValidEmoji(String emojiId, String emojiName) {
		boolean hasEmojiId = emojiId != null && !emojiId.isEmpty();
		boolean hasEmojiName = emojiName != null && !emojiName.isEmpty();
		boolean isNumericId = hasEmojiId && emojiId.matches("\\d+");
		
		return hasEmojiId && hasEmojiName && isNumericId;
	}

	/**
	 * Create stat options for select menu
	 */
	private List<net.dv8tion.jda.api.interactions.components.selections.SelectOption> createStatOptions(
			String currentStat) {
		List<net.dv8tion.jda.api.interactions.components.selections.SelectOption> options = new ArrayList<>();

		for (String stat : STAT_TO_FIELD.keySet()) {
			net.dv8tion.jda.api.interactions.components.selections.SelectOption option = net.dv8tion.jda.api.interactions.components.selections.SelectOption
					.of(stat, stat);

			// Mark current stat as default
			if (stat.equals(currentStat)) {
				option = option.withDefault(true);
			}

			options.add(option);
		}

		return options;
	}

	/**
	 * Encode parameters into a Base64 string for button ID
	 */
	private String encodeButtonId(String playerTag, String statType) {
		// Format: playerTag|statType
		String data = playerTag + "|" + statType;
		return BUTTON_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
	}

	/**
	 * Decode a Base64-encoded button ID
	 */
	private String[] decodeButtonId(String buttonId) {
		// Remove prefix
		String encoded = buttonId.substring(BUTTON_PREFIX.length());

		// Decode Base64
		String data = new String(Base64.getUrlDecoder().decode(encoded));

		// Split by |
		return data.split("\\|", -1);
	}

	/**
	 * Encode player tag into select menu ID
	 */
	private String encodeSelectMenuId(String playerTag) {
		return SELECT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(playerTag.getBytes());
	}

	/**
	 * Decode select menu ID
	 */
	private String decodeSelectMenuId(String selectMenuId) {
		// Remove prefix
		String encoded = selectMenuId.substring(SELECT_PREFIX.length());

		// Decode Base64
		return new String(Base64.getUrlDecoder().decode(encoded));
	}
}
