package lostmanager.commands.coc.util.jsonutils;

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
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.FilteredIdsCache;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class stats extends ListenerAdapter {

	// Constants for button and select menu ID prefixes
	private static final String BUTTON_PREFIX = "stats_refresh_";
	private static final String SELECT_PREFIX = "stats_select_";
	private static final String BUTTON_FORWARD_PREFIX = "stats_forward_";
	private static final String BUTTON_BACKWARD_PREFIX = "stats_backward_";
	private static final String BUTTON_PRICES_PREFIX = "stats_prices_refresh_";
	private static final String MODE_OWNED = "owned";
	private static final String MODE_MISSING = "missing";
	private static final String SUBCOMMAND_SHOW = "show";
	private static final String SUBCOMMAND_MISSING = "missing";
	private static final String SUBCOMMAND_PRICES = "prices";
	private static final String SUBCOMMAND_PRICES_OLD = "pricesold";
	private static final String PRICES_TITLE = "Spieler Upgrade-Kosten";
	private static final String PRICES_OLD_TITLE = "Spieler Upgrade-Kosten (alte Preise)";

	/**
	 * The one account that may look at every player, and at the old prices. Kept
	 * as a constant because {@link #canAccessPlayer} and the old-price view both
	 * ask for it.
	 */
	private static final String UNRESTRICTED_USER_ID = "362260317071343630";

	// Maximum characters per page (Discord embed description limit is ~4096, we use
	// 4000 for safety)
	private static final int MAX_PAGE_LENGTH = 4000;

	// Mapping of stat options to JSON field names
	private static final String CATEGORY_BUILDINGS = "Buildings";
	private static final String FIELD_BUILDINGS = "buildings";
	private static final String FIELD_OBSTACLES = "obstacles";

	private static final Map<String, String> STAT_TO_FIELD = new HashMap<>();

	static {
		STAT_TO_FIELD.put(CATEGORY_BUILDINGS, FIELD_BUILDINGS);
		STAT_TO_FIELD.put("Buildings (BB)", "buildings2");
		STAT_TO_FIELD.put("Decos", "decos");
		STAT_TO_FIELD.put("Decos (BB)", "decos2");
		STAT_TO_FIELD.put("Equips", "equipment");
		STAT_TO_FIELD.put("Helpers", "helpers");
		STAT_TO_FIELD.put("Guardians", "guardians");
		STAT_TO_FIELD.put("Heroes (BB)", "heroes2");
		STAT_TO_FIELD.put("Heroes", "heroes");
		STAT_TO_FIELD.put("House Parts", "house_parts");
		STAT_TO_FIELD.put("Obstacles", FIELD_OBSTACLES);
		STAT_TO_FIELD.put("Obstacles (BB)", "obstacles2");
		STAT_TO_FIELD.put("Pets", "pets");
		STAT_TO_FIELD.put("Sceneries", "sceneries");
		STAT_TO_FIELD.put("Sceneries (BB)", "sceneries2");
		STAT_TO_FIELD.put("Sieges", "siege_machines");
		STAT_TO_FIELD.put("Skins", "skins");
		STAT_TO_FIELD.put("Skins (BB)", "skins2");
		STAT_TO_FIELD.put("Spells", "spells");
		STAT_TO_FIELD.put("Traps", "traps");
		STAT_TO_FIELD.put("Traps (BB)", "traps2");
		STAT_TO_FIELD.put("Units", "units");
		STAT_TO_FIELD.put("Units (BB)", "units2");
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

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("stats"))
			return;

		String subcommand = event.getSubcommandName();
		if (subcommand != null && !subcommand.equals(SUBCOMMAND_SHOW) && !subcommand.equals(SUBCOMMAND_MISSING)
				&& !subcommand.equals(SUBCOMMAND_PRICES) && !subcommand.equals(SUBCOMMAND_PRICES_OLD)) {
			event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
			return;
		}

		if (SUBCOMMAND_PRICES.equals(subcommand)) {
			handlePricesCommand(event, SCHEME_CURRENT);
			return;
		}

		if (SUBCOMMAND_PRICES_OLD.equals(subcommand)) {
			handlePricesCommand(event, SCHEME_OLD);
			return;
		}

		String mode = SUBCOMMAND_MISSING.equals(subcommand) ? MODE_MISSING : MODE_OWNED;
		String title = getTitleForMode(mode);
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

			performStatsDisplay(event.getHook(), title, playerTag, statType, 0, mode);

		}, "StatsCommand-" + event.getUser().getId()).start();
	}

	/**
	 * Handle {@code /stats prices} and {@code /stats pricesold}: the upgrade costs
	 * of everything a player owns, added up per currency and priced for the event
	 * and pass they picked. The two differ only in which price keys of the image
	 * map are read - and in who is allowed to ask.
	 */
	private void handlePricesCommand(SlashCommandInteractionEvent event, PriceScheme scheme) {
		event.deferReply().queue();
		String title = scheme.title();

		new Thread(() -> {
			OptionMapping playerOption = event.getOption("player");
			OptionMapping hammerJamOption = event.getOption("hammerjam");
			OptionMapping goldPassOption = event.getOption("goldpass");

			if (playerOption == null || hammerJamOption == null || goldPassOption == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Parameter 'player', 'hammerjam' und 'goldpass' sind erforderlich.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String playerTag = playerOption.getAsString();
			boolean hammerJam = isYes(hammerJamOption.getAsString());
			boolean goldPass = isYes(goldPassOption.getAsString());

			User userExecuted = new User(event.getUser().getId());
			if (!mayUseScheme(userExecuted, scheme)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du hast keine Berechtigung, diesen Befehl zu benutzen.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (!canAccessPlayer(userExecuted, playerTag)) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
								MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			performPricesDisplay(event.getHook(), playerTag, hammerJam, goldPass, scheme);

		}, "StatsPrices-" + event.getUser().getId()).start();
	}

	/**
	 * Who may run a price view at all.
	 *
	 * The current prices are open to everyone who may see the player. The old
	 * ones are not: they are only interesting to compare against, and a wrong
	 * number from them would look like a bug in the live view. So they stay with
	 * the one account named above and with clan admins.
	 */
	private boolean mayUseScheme(User user, PriceScheme scheme) {
		if (!scheme.restricted()) {
			return true;
		}

		if (UNRESTRICTED_USER_ID.equals(user.getUserID())) {
			return true;
		}

		for (String clantag : DBManager.getAllClans()) {
			if (user.getClanRoles().get(clantag) == Player.RoleType.ADMIN) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Rebuild the prices view behind its refresh button, on the same player and
	 * the same Hammer Jam / Gold Pass answers the command was given.
	 */
	private void handlePricesRefresh(ButtonInteractionEvent event) {
		event.deferEdit().queue();

		new Thread(() -> {
			try {
				String[] params = decodePricesButtonId(event.getComponentId());
				if (params.length < 3) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(PRICES_TITLE,
									"Fehler: Button-Daten konnten nicht dekodiert werden.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String playerTag = params[0];
				boolean hammerJam = isYes(params[1]);
				boolean goldPass = isYes(params[2]);
				// Buttons from before the old-price view carry three fields; they
				// mean the current prices.
				PriceScheme scheme = params.length > 3 && SCHEME_OLD.key().equals(params[3]) ? SCHEME_OLD
						: SCHEME_CURRENT;

				User userExecuted = new User(event.getUser().getId());
				if (!mayUseScheme(userExecuted, scheme) || !canAccessPlayer(userExecuted, playerTag)) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(scheme.title(),
									"Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				performPricesDisplay(event.getHook(), playerTag, hammerJam, goldPass, scheme);

			} catch (IllegalArgumentException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(PRICES_TITLE,
								"Fehler: Button-Daten konnten nicht dekodiert werden.",
								MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "StatsPricesRefresh-" + event.getUser().getId()).start();
	}

	private boolean isYes(String value) {
		return value != null && value.equalsIgnoreCase("ja");
	}

	@SuppressWarnings("null")
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

	@SuppressWarnings("null")
	@Override
	public void onButtonInteraction(ButtonInteractionEvent event) {
		String id = event.getComponentId();
		if (id.startsWith(BUTTON_PRICES_PREFIX)) {
			handlePricesRefresh(event);
			return;
		}

		if (!id.startsWith(BUTTON_PREFIX) && !id.startsWith(BUTTON_FORWARD_PREFIX)
				&& !id.startsWith(BUTTON_BACKWARD_PREFIX))
			return;

		event.deferEdit().queue();

		new Thread(() -> {
			// Check permissions
			User userExecuted = new User(event.getUser().getId());
			String title = getTitleForMode(MODE_OWNED);

			// Decode button ID to extract parameters
			try {
				String[] params;
				int pageNumber = 0;

				if (id.startsWith(BUTTON_FORWARD_PREFIX)) {
					params = decodeNavigationButtonId(id, BUTTON_FORWARD_PREFIX);
					if (params != null && params.length >= 3) {
						pageNumber = Integer.parseInt(params[2]) + 1; // Move forward
					}
				} else if (id.startsWith(BUTTON_BACKWARD_PREFIX)) {
					params = decodeNavigationButtonId(id, BUTTON_BACKWARD_PREFIX);
					if (params != null && params.length >= 3) {
						pageNumber = Integer.parseInt(params[2]) - 1; // Move backward
					}
				} else {
					params = decodeButtonId(id);
					if (params != null && params.length >= 3) {
						pageNumber = Integer.parseInt(params[2]);
					}
				}

				if (params == null || params.length < 2) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String playerTag = params[0];
				String statType = params[1];
				String mode = params.length >= 4 ? normalizeMode(params[3]) : MODE_OWNED;
				title = getTitleForMode(mode);

				// Verify access
				if (!canAccessPlayer(userExecuted, playerTag)) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				performStatsDisplay(event.getHook(), title, playerTag, statType, pageNumber, mode);

			} catch (IllegalArgumentException e) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Fehler: Button-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			}

		}, "StatsRefresh-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	@Override
	public void onStringSelectInteraction(StringSelectInteractionEvent event) {
		String id = event.getComponentId();
		if (!id.startsWith(SELECT_PREFIX))
			return;

		event.deferEdit().queue();

		new Thread(() -> {
			// Check permissions
			User userExecuted = new User(event.getUser().getId());
			String title = getTitleForMode(MODE_OWNED);

			// Decode select menu ID to extract player tag
			try {
				String[] params = decodeSelectMenuId(id);
				if (params == null || params.length < 1) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler: Select-Daten konnten nicht dekodiert werden.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				String playerTag = params[0];
				String mode = params.length >= 2 ? normalizeMode(params[1]) : MODE_OWNED;
				title = getTitleForMode(mode);

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

				performStatsDisplay(event.getHook(), title, playerTag, newStatType, 0, mode);

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

		if (user.getUserID().equals("362260317071343630")) {
			return true;
		}
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
	@SuppressWarnings("null")
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

		if (user.getUserID().equals("362260317071343630")) {
			hasPermission = true;
		}

		if (hasPermission) {
			// Get all players with uploaded JSONs
			String sql = "SELECT DISTINCT tag FROM userjsons ORDER BY tag";
			List<String> tags = DBUtil.getArrayListFromSQL(sql, String.class);
			for (String tag : tags) {
				// Try to get player name for better display
				Player player = new Player(tag);
				String clanName = player.getClanDB() != null ? player.getClanDB().getNameDB() : null;
				String display = new Player(tag).getInfoStringDB();
				if (clanName != null && !clanName.isEmpty()) {
					display += " - " + clanName;
				}

				// Filter mit Eingabe (input ist String mit aktuell eingegebenem Text)
				if (display.toLowerCase().contains(input.toLowerCase())
						|| tag.toLowerCase().startsWith(input.toLowerCase())) {
					choices.add(new Command.Choice(display, tag));
					if (choices.size() == 25) {
						break; // Max 25 Vorschläge
					}
				}
			}
		} else {
			// Get only linked accounts with uploaded JSONs
			ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();
			String inputLower = input.toLowerCase();

			for (Player player : linkedAccounts) {
				String tag = player.getTag();
				// Check if this player has any JSON uploaded
				String sql = "SELECT COUNT(*) FROM userjsons WHERE tag = ?";
				Long count = DBUtil.getValueFromSQL(sql, Long.class, tag);
				String playerName = player.getNameDB() != null ? player.getNameDB() : player.getNameAPI();

				if (count != null && count > 0) {
					// Filter by input
					if (tag.toLowerCase().contains(inputLower) || playerName.toLowerCase().contains(inputLower)) {
						String displayName = player.getInfoStringDB() != null ? player.getInfoStringDB()
								: player.getInfoStringAPI();
						choices.add(new Command.Choice(displayName, tag));
						if (choices.size() >= 25)
							break;
					}
				}
			}
		}

		return choices;
	}

	/**
	 * Display stats for a player with pagination support
	 */
	@SuppressWarnings("null")
	private void performStatsDisplay(net.dv8tion.jda.api.interactions.InteractionHook hook, String title,
			String playerTag, String statType, int pageNumber, String mode) {

		String normalizedMode = normalizeMode(mode);

		// Get JSON data from database
		String sql = "SELECT json, timestamp FROM userjsons WHERE tag = ? LIMIT 1";

		try (java.sql.PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection().prepareStatement(sql)) {
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
				Object dataToDisplay = fieldData;

				if (MODE_MISSING.equals(normalizedMode)) {
					dataToDisplay = buildMissingData(statType, fieldData);
					if (dataToDisplay instanceof JSONArray missingArr && missingArr.length() == 0) {
						hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Für **" + statType + "** fehlen keine Einträge.", MessageUtil.EmbedType.INFO)).queue();
						return;
					}
				}

				// Format the data
				String formattedData = formatData(dataToDisplay, timestamp);

				// Format database timestamp
				ZonedDateTime uploadTime = timestamp.toInstant().atZone(ZoneId.of("Europe/Berlin"));
				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String uploadFormatiert = uploadTime.format(formatter);

				// Build description header
				Player p = new Player(playerTag);
				String playerName = p.getNameDB() != null ? p.getNameDB() : p.getNameAPI();
				boolean missingMode = MODE_MISSING.equals(normalizedMode);
				String priceSummary = formatUpgradePriceSummary(dataToDisplay, missingMode);
				String headerText = "**Spieler:** " + (playerName != null ? playerName : playerTag) + "\n"
						+ "**Stat:** " + statType + "\n"
						+ "**Modus:** " + (missingMode ? "Fehlend" : "Vorhanden") + "\n"
						+ "**Hochgeladen:** " + uploadFormatiert + "\n"
						+ (priceSummary.isEmpty() ? "" : priceSummary + "\n")
						+ "\n";

				// Split into pages if needed
				List<String> pages = splitIntoPages(formattedData, headerText);

				// Ensure pageNumber is valid
				if (pageNumber < 0)
					pageNumber = 0;
				if (pageNumber >= pages.size())
					pageNumber = pages.size() - 1;

				// Build description for current page
				StringBuilder description = new StringBuilder();
				description.append(headerText);
				description.append(pages.get(pageNumber));

				// Add page indicator if multiple pages
				if (pages.size() > 1) {
					description.append("\n\n**Seite ").append(pageNumber + 1).append("/").append(pages.size())
							.append("**");
				}

				// Create buttons
				List<Button> buttons = new ArrayList<>();

				// Add backward button if not on first page
				if (pageNumber > 0) {
					String backwardButtonId = encodeNavigationButtonId(playerTag, statType, pageNumber,
							BUTTON_BACKWARD_PREFIX, normalizedMode);
					buttons.add(Button.primary(backwardButtonId, "\u200B").withEmoji(Emoji.fromUnicode("⬅️")));
				}

				// Add refresh button
				String refreshButtonId = encodeButtonId(playerTag, statType, pageNumber, normalizedMode);
				buttons.add(Button.secondary(refreshButtonId, "\u200B").withEmoji(Emoji.fromUnicode("🔁")));

				// Add forward button if not on last page
				if (pageNumber < pages.size() - 1) {
					String forwardButtonId = encodeNavigationButtonId(playerTag, statType, pageNumber,
							BUTTON_FORWARD_PREFIX, normalizedMode);
					buttons.add(Button.primary(forwardButtonId, "\u200B").withEmoji(Emoji.fromUnicode("➡️")));
				}

				String selectMenuId = encodeSelectMenuId(playerTag, normalizedMode);
				StringSelectMenu selectMenu = StringSelectMenu.create(selectMenuId).setPlaceholder("Anderes Feld")
						.addOptions(createStatOptions(statType)).build();

				// Add current time for footer
				ZonedDateTime jetzt = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
				String formatiert = jetzt.format(formatter);

				hook.editOriginal("").setEmbeds(MessageUtil.buildEmbed(title, description.toString(),
						MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
						.setActionRow(buttons).queue(message -> {
							// Add select menu in second row
							message.editMessageComponents(
									net.dv8tion.jda.api.interactions.components.ActionRow.of(buttons),
									net.dv8tion.jda.api.interactions.components.ActionRow.of(selectMenu)).queue();
						});
			}
		} catch (java.sql.SQLException e) {
			System.err.println("Database error loading stats data: " + e.getMessage());
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Laden der Daten aus der Datenbank: " + e.getMessage(), MessageUtil.EmbedType.ERROR))
					.queue();
		} catch (org.json.JSONException e) {
			System.err.println("JSON parsing error: " + e.getMessage());
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Verarbeiten der JSON-Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
		} catch (Exception e) {
			System.err.println("Unexpected error loading stats data: " + e.getMessage());
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Unerwarteter Fehler beim Laden der Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR))
					.queue();
		}
	}

	/**
	 * Show what every upgrade a player owns has cost them, added up per currency.
	 *
	 * Every field the other stats views show one at a time is walked in one go
	 * here, so the Gold line carries the buildings, the traps and everything else
	 * that is paid for in Gold together.
	 */
	private void performPricesDisplay(net.dv8tion.jda.api.interactions.InteractionHook hook, String playerTag,
			boolean hammerJam, boolean goldPass, PriceScheme scheme) {

		String title = scheme.title();
		String sql = "SELECT json, timestamp FROM userjsons WHERE tag = ? LIMIT 1";

		try (java.sql.PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, playerTag);

			try (java.sql.ResultSet rs = pstmt.executeQuery()) {
				if (!rs.next()) {
					hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Keine JSON-Daten für diesen Spieler gefunden.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				JSONObject json = new JSONObject(rs.getString("json"));
				java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");

				Map<String, Map<Integer, Long>> byCategory = collectUpgradePricesByCategory(json, scheme);
				if (byCategory == null) {
					hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Die Preisliste konnte nicht geladen werden. Bitte später erneut versuchen.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				double factor = priceFactor(hammerJam, goldPass);

				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
				String uploadFormatiert = timestamp.toInstant().atZone(ZoneId.of("Europe/Berlin")).format(formatter);

				Player p = new Player(playerTag);
				String playerName = p.getNameDB() != null ? p.getNameDB() : p.getNameAPI();

				StringBuilder description = new StringBuilder();
				description.append("**Spieler:** ").append(playerName != null ? playerName : playerTag)
						.append("\n")
						.append("**Hammer Jam:** ").append(hammerJam ? "Ja" : "Nein").append("\n")
						.append("**Gold Pass:** ").append(goldPass ? "Ja" : "Nein").append("\n")
						.append("**Preis:** ").append(formatPriceFactor(factor)).append("\n")
						.append("**Hochgeladen:** ").append(uploadFormatiert).append("\n");
				description.append(formatPriceTotals(byCategory, factor, scheme));

				String formatiert = ZonedDateTime.now(ZoneId.of("Europe/Berlin")).format(formatter);

				hook.editOriginal("")
						.setEmbeds(MessageUtil.buildEmbed(title, description.toString(),
								MessageUtil.EmbedType.INFO, "Zuletzt aktualisiert am " + formatiert))
						.setActionRow(Button
								.secondary(encodePricesButtonId(playerTag, hammerJam, goldPass, scheme), "\u200B")
								.withEmoji(Emoji.fromUnicode("🔁")))
						.queue();
			}
		} catch (java.sql.SQLException e) {
			System.err.println("Database error loading price data: " + e.getMessage());
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Laden der Daten aus der Datenbank: " + e.getMessage(), MessageUtil.EmbedType.ERROR))
					.queue();
		} catch (org.json.JSONException e) {
			System.err.println("JSON parsing error: " + e.getMessage());
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Fehler beim Verarbeiten der JSON-Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
		} catch (Exception e) {
			System.err.println("Unexpected error loading price data: " + e.getMessage());
			hook.editOriginalEmbeds(MessageUtil.buildEmbed(title,
					"Unerwarteter Fehler beim Laden der Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
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

		if (data instanceof JSONArray arr) {
			if (arr.length() == 0) {
				return "Keine Daten vorhanden";
			}

			// Check if array contains JSONObjects with "data" field
			boolean hasDataField = false;
			for (int i = 0; i < arr.length(); i++) {
				Object item = arr.get(i);
				if (item instanceof JSONObject itemObj && itemObj.has("data")) {
					hasDataField = true;
					break;
				}
			}

			if (hasDataField) {
				// Group by data ID and format
				sb.append(formatGroupedData(arr, jsonTimestamp));
			} else {
				// Simple arrays (skins, sceneries, house_parts) - need to sort by index
				// Fetch image map once for sorting
				org.json.JSONObject imageMapCache = null;
				try {
					imageMapCache = lostmanager.util.ImageMapCache.fetchFullMapOnce();
				} catch (Exception e) {
					System.err.println("Failed to fetch image map for sorting simple arrays: " + e.getMessage());
				}
				final org.json.JSONObject finalImageMapCache = imageMapCache;

				// Collect items into list for sorting
				java.util.List<Object> items = new java.util.ArrayList<>();
				for (int i = 0; i < arr.length(); i++) {
					items.add(arr.get(i));
				}

				// Sort items by index (works for both Integer and String types)
				items.sort((item1, item2) -> {
					// Convert to string for lookup (works for both Integer and String)
					String id1 = item1.toString();
					String id2 = item2.toString();

					try {
						if (finalImageMapCache != null) {
							JSONObject d1 = finalImageMapCache.has(id1) ? finalImageMapCache.getJSONObject(id1) : null;
							JSONObject d2 = finalImageMapCache.has(id2) ? finalImageMapCache.getJSONObject(id2) : null;

							int i1 = d1 != null && d1.has("index") ? d1.getInt("index") : Integer.MAX_VALUE;
							int i2 = d2 != null && d2.has("index") ? d2.getInt("index") : Integer.MAX_VALUE;

							if (i1 != i2) {
								return Integer.compare(i1, i2);
							}
						}
					} catch (org.json.JSONException ex) {
						// Fallback to string comparison
					}
					return id1.compareTo(id2);
				});

				// Display sorted items with original formatting
				for (int i = 0; i < items.size(); i++) {
					Object item = items.get(i);
					if (item instanceof JSONObject jsonItem) {
						sb.append(formatObject(jsonItem, 0, jsonTimestamp));
						if (i < items.size() - 1) {
							sb.append("\n");
						}
					} else {
						// Simple values (e.g., house_parts, skins, sceneries)
						String rawVal = String.valueOf(item);
						if (FilteredIdsCache.isFiltered(rawVal)) {
							// skip filtered ids
							continue;
						}
						String value = getMappedValue(rawVal);
						sb.append("- ").append(value);
						if (i < items.size() - 1) {
							sb.append("\n");
						}
					}
				}
			}
		}

		return sb.toString();
	}

	/**
	 * Format grouped data by data ID
	 */
	private String formatGroupedData(JSONArray arr, java.sql.Timestamp jsonTimestamp) {
		// Fetch image map once for this request to avoid multiple HTTP calls during
		// sorting
		org.json.JSONObject imageMapCache = null;
		try {
			imageMapCache = lostmanager.util.ImageMapCache.fetchFullMapOnce();
		} catch (Exception e) {
			System.err.println("Failed to fetch image map for sorting: " + e.getMessage());
		}
		final org.json.JSONObject finalImageMapCache = imageMapCache;

		// Group objects by their data ID
		Map<String, List<JSONObject>> groupedByData = new LinkedHashMap<>();

		for (int i = 0; i < arr.length(); i++) {
			Object item = arr.get(i);
			if (item instanceof JSONObject obj) {
				if (obj.has("data")) {
					String dataId = obj.get("data").toString();
					if (FilteredIdsCache.isFiltered(dataId)) {
						// skip filtered data ids entirely
						continue;
					}
					groupedByData.computeIfAbsent(dataId, _ -> new ArrayList<>()).add(obj);
				}
			}
		}

		StringBuilder sb = new StringBuilder();
		boolean first = true;

		// Process each data ID group — sort entries by image_map `index` when available
		List<Map.Entry<String, List<JSONObject>>> entryList = new ArrayList<>(groupedByData.entrySet());
		entryList.sort((e1, e2) -> {
			try {
				// Use cached image map to avoid HTTP requests per comparison
				JSONObject d1 = null;
				JSONObject d2 = null;
				if (finalImageMapCache != null) {
					d1 = finalImageMapCache.has(e1.getKey()) ? finalImageMapCache.getJSONObject(e1.getKey()) : null;
					d2 = finalImageMapCache.has(e2.getKey()) ? finalImageMapCache.getJSONObject(e2.getKey()) : null;
				}

				int i1 = d1 != null && d1.has("index") ? d1.getInt("index") : Integer.MAX_VALUE;
				int i2 = d2 != null && d2.has("index") ? d2.getInt("index") : Integer.MAX_VALUE;
				if (i1 != i2)
					return Integer.compare(i1, i2);
			} catch (org.json.JSONException ex) {
				// ignore and fallback
			}
			return e1.getKey().compareTo(e2.getKey());
		});

		// Process each data ID group
		for (Map.Entry<String, List<JSONObject>> entry : entryList) {
			if (!first) {
				sb.append("\n\n");
			}
			first = false;

			String dataId = entry.getKey();
			List<JSONObject> objects = entry.getValue();

			// Get the name for this data ID
			String mappedValue = getMappedValue(dataId);
			sb.append(mappedValue);

			// Group by attributes (excluding "data", "cnt", "gear_up", "timer", and
			// "helper_cooldown")
			Map<String, ConfigGroup> configGroups = new TreeMap<>(new AttributeComparator());

			for (JSONObject obj : objects) {
				// Create a key from all attributes except "data", "cnt", "gear_up", "timer",
				// and "helper_cooldown"
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
			boolean showCounts = configGroups.size() > 1
					|| configGroups.values().stream().anyMatch(g -> g.totalCount > 1);

			// Display grouped configurations
			for (ConfigGroup group : configGroups.values()) {
				// Determine indentation based on whether we're showing counts
				// Use 2 spaces per indent level and "· " for indented items
				String space = EmbedBuilder.ZERO_WIDTH_SPACE;
				String countIndent = space.repeat(2) + "· "; // 1 indent level
				String baseIndent = showCounts ? space.repeat(4) + "· " : space.repeat(2) + "· "; // 2 or 1 indent
																									// levels
				int objIndent = showCounts ? 3 : 2;
				String arrItemIndent = showCounts ? space.repeat(6) + "· " : space.repeat(4) + "· "; // 3 or 2 indent
																										// levels

				// Only show count if there's actual grouping or multiple items
				if (showCounts) {
					sb.append("\n").append(countIndent).append("Anzahl: ").append(group.totalCount);
				}

				// Get and sort keys for consistent display order
				// Note: timer and helper_cooldown are included here for display, even though
				// they don't affect grouping
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

					switch (key) {
						case "timer", "helper_cooldown" -> {
							// Special handling for timer
							int timerSeconds = 0;
							if (value instanceof Number numValue) {
								timerSeconds = numValue.intValue();
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
						}
						case "lvl" -> {
							// Special handling for level - add emoji for items with levels
							sb.append("\n").append(baseIndent);
							String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
							sb.append(translatedKey).append(": ");
							sb.append(value.toString());

							// Add emoji if this item has levels
							if (lostmanager.util.ImageMapCache.hasLevels(dataId)) {
								try {
									int levelNum = Integer.parseInt(value.toString());
									String levelEmoji = getEmojiForLevel(dataId, levelNum);
									if (levelEmoji != null) {
										sb.append(" ").append(levelEmoji);
									}
								} catch (NumberFormatException e) {
									// Level is not a number, skip emoji
								}
							}
						}
						default -> {
							sb.append("\n").append(baseIndent);
							String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);
							sb.append(translatedKey).append(": ");

							switch (value) {
								case JSONObject jsonObj ->
										sb.append("\n").append(formatObject(jsonObj, objIndent, jsonTimestamp));
								case JSONArray valueArr -> {
									if (valueArr.length() > 0) {
										for (int i = 0; i < valueArr.length(); i++) {
											Object arrItem = valueArr.get(i);
											if (arrItem instanceof JSONObject arrObj) {
												sb.append("\n")
														.append(formatObject(arrObj, objIndent, jsonTimestamp));
											} else {
												String mappedArrValue = getMappedValue(String.valueOf(arrItem));
												sb.append("\n").append(arrItemIndent).append(mappedArrValue);
											}
										}
									}
								}
								case Boolean bValue -> sb.append(bValue ? "Ja" : "Nein");
								default -> sb.append(String.valueOf(value));
							}
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
	 * Create a configuration key from a JSON object Excludes attributes defined in
	 * GROUPING_EXCLUDED_ATTRS
	 */
	private String createConfigKey(JSONObject obj) {
		StringBuilder key = new StringBuilder();

		// Get all keys except those in GROUPING_EXCLUDED_ATTRS, sort them for
		// consistent ordering
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
	 * Comparator to sort attribute keys for display Priority: lvl, supercharge,
	 * then alphabetically
	 */
	private static class AttributeDisplayComparator implements Comparator<String> {
		@Override
		public int compare(String k1, String k2) {
			if (k1.equals("lvl"))
				return -1;
			if (k2.equals("lvl"))
				return 1;
			if (k1.equals("supercharge"))
				return -1;
			if (k2.equals("supercharge"))
				return 1;
			return k1.compareTo(k2);
		}
	}

	/**
	 * Format a JSON object with indentation
	 */
	private String formatObject(JSONObject obj, int indent, java.sql.Timestamp jsonTimestamp) {
		StringBuilder sb = new StringBuilder();
		String prefix = indent > 0 ? "· ".repeat(indent) : "";

		String dataId = null;

		// Show identifier if present
		if (obj.has("data") && obj.get("data") != null && obj.get("data") != JSONObject.NULL) {
			dataId = obj.get("data").toString();
			if (FilteredIdsCache.isFiltered(dataId)) {
				return ""; // whole object is filtered
			}
			String mappedValue = getMappedValue(dataId);
			sb.append(prefix).append(mappedValue);
		}

		for (String key : obj.keySet()) {
			if (key.equals("data"))
				continue;

			Object value = obj.get(key);
			if (value == null || value == JSONObject.NULL)
				continue;

			String translatedKey = ATTR_TRANSLATIONS.getOrDefault(key, key);

			// Items with levels carry their emoji on the level line - the rule
			// formatGroupedData follows for top level entries. Without the same rule
			// here, everything rendered as a nested object stays without an icon, the
			// modules underneath a seasonal defense among them.
			if (key.equals("lvl") && dataId != null) {
				sb.append("\n").append(prefix).append(translatedKey).append(": ").append(value.toString());

				if (lostmanager.util.ImageMapCache.hasLevels(dataId)) {
					try {
						String levelEmoji = getEmojiForLevel(dataId, Integer.parseInt(value.toString()));
						if (levelEmoji != null) {
							sb.append(" ").append(levelEmoji);
						}
					} catch (NumberFormatException e) {
						// Level is not a number, skip the emoji
					}
				}

				continue;
			}

			if (key.equals("timer") || key.equals("helper_cooldown")) {
				switch (value) {
					case JSONObject jsonObj -> {
						sb.append("\n").append(prefix).append(translatedKey).append(":");
						sb.append("\n").append(formatObject(jsonObj, indent + 1, jsonTimestamp));
					}
					case JSONArray arr -> {
						String nextIndent = "· ".repeat(indent + 1);
						for (int i = 0; i < arr.length(); i++) {
							Object item = arr.get(i);
							if (item instanceof JSONObject itemObj) {
								sb.append("\n").append(formatObject(itemObj, indent + 1, jsonTimestamp));
							} else {
								String raw = String.valueOf(item);
								if (FilteredIdsCache.isFiltered(raw))
									continue;
								sb.append("\n").append(nextIndent).append(getMappedValue(raw));
							}
						}
					}
					case Number numValue -> {
						int timerSeconds = numValue.intValue();
						long elapsedSeconds = (System.currentTimeMillis() - jsonTimestamp.getTime()) / 1000;
						long remainingSeconds = timerSeconds - elapsedSeconds;
						if (remainingSeconds > 0) {
							sb.append("\n").append(prefix).append(translatedKey).append(": ")
									.append(formatTimerRemaining(remainingSeconds));
						}
					}
					case Boolean bValue -> {
						String valueStr = bValue ? "Ja" : "Nein";
						sb.append("\n").append(prefix).append(translatedKey).append(": ").append(valueStr);
					}
					default -> {
						sb.append("\n").append(prefix).append(translatedKey).append(": ").append(String.valueOf(value));
					}
				}
			} else {
				switch (value) {
					case JSONObject jsonObj -> {
						sb.append("\n").append(prefix).append(translatedKey).append(":");
						sb.append("\n").append(formatObject(jsonObj, indent + 1, jsonTimestamp));
					}
					case JSONArray arr -> {
						String nextIndent = "· ".repeat(indent + 1);
						for (int i = 0; i < arr.length(); i++) {
							Object item = arr.get(i);
							if (item instanceof JSONObject itemObj) {
								sb.append("\n").append(formatObject(itemObj, indent + 1, jsonTimestamp));
							} else {
								String raw = String.valueOf(item);
								if (FilteredIdsCache.isFiltered(raw))
									continue;
								sb.append("\n").append(nextIndent).append(getMappedValue(raw));
							}
						}
					}
					case Boolean bValue -> {
						String valueStr = bValue ? "Ja" : "Nein";
						sb.append("\n").append(prefix).append(translatedKey).append(": ").append(valueStr);
					}
					default -> {
						sb.append("\n").append(prefix).append(translatedKey).append(": ").append(String.valueOf(value));
					}
				}
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
	 * Get mapped value from image_map.json cache For items without levels: returns
	 * "Name Emoji" if icon exists, or just "Name" if no icon For items with levels:
	 * returns just "Name" (emoji will be shown on Level line)
	 * 
	 * @param dataValue The data ID
	 * @return Formatted string with name and emoji (if applicable)
	 */
	private String getMappedValue(String dataValue) {
		try {
			// Get item data from cache
			String name = lostmanager.util.ImageMapCache.getName(dataValue);

			// If no name in cache, return raw data value
			if (name == null) {
				return dataValue;
			}

			// Check if item has levels
			boolean hasLevels = lostmanager.util.ImageMapCache.hasLevels(dataValue);

			if (hasLevels) {
				// For items with levels, don't show emoji here (will be shown on Level line)
				return name;
			} else {
				// For items without levels, always prefer showing price if available, and
				// append emoji if present
				String price = lostmanager.util.ImageMapCache.getPrice(dataValue);
				String priceLine = price != null ? " · Preis: " + price : "";

				// Try to get emoji/icon
				String iconPath = lostmanager.util.ImageMapCache.getIconPath(dataValue);
				String emoji = null;
				if (iconPath != null && !iconPath.isEmpty()) {
					emoji = getOrCreateEmojiForPath(iconPath, name);
				}

				// Build base line with name and optional emoji
				String base = name;
				if (emoji != null) {
					base += " " + emoji;
				}

				// Append price on a new line if available
				if (!priceLine.isEmpty()) {
					return base + "\n" + priceLine;
				}

				// No price
				return base;
			}

		} catch (Exception e) {
			System.err.println("Error getting mapped value for '" + dataValue + "': " + e.getMessage());
			return dataValue;
		}
	}

	/**
	 * Currency slots as they appear in image_map.json ({@code upgrade-priceN}).
	 * Slots 4 and 5 belong to the Builder Base.
	 */
	private static final Map<Integer, String> PRICE_LABELS = new LinkedHashMap<>();

	static {
		PRICE_LABELS.put(1, "Gold");
		PRICE_LABELS.put(2, "Elixier");
		PRICE_LABELS.put(3, "Dunkles Elixier");
		PRICE_LABELS.put(4, "Bauarbeiter-Elixier");
		PRICE_LABELS.put(5, "Bauarbeiter-Gold");
	}

	private static final String PRICE_KEY_PREFIX = "upgrade-price";
	private static final String PRICE_KEY_PREFIX_OLD = "upgrade-price-old";

	/**
	 * Which price keys a view adds up.
	 *
	 * {@code /stats prices} reads {@code upgrade-priceN}, {@code /stats pricesold}
	 * reads {@code upgrade-price-oldN} - same walk, same categories, only another
	 * set of keys. The old view is deliberately narrower: it shows the three home
	 * village currencies and nothing else, and the count-based buildings stay out
	 * of it because their prices are hardcoded here and have no old counterpart.
	 *
	 * @param key        what the refresh button carries to find its way back here
	 * @param keyPrefix  the price keys in the image map
	 * @param slots      the currencies to print, or null for "the usual ones plus
	 *                   whatever the data introduces"
	 * @param countBased whether the bought-not-upgraded buildings are counted
	 * @param restricted whether the view is limited to admins
	 */
	private record PriceScheme(String key, String title, String keyPrefix, List<Integer> slots, boolean countBased,
			boolean restricted) {
	}

	private static final PriceScheme SCHEME_CURRENT = new PriceScheme("neu", PRICES_TITLE, PRICE_KEY_PREFIX, null,
			true, false);
	private static final PriceScheme SCHEME_OLD = new PriceScheme("alt", PRICES_OLD_TITLE, PRICE_KEY_PREFIX_OLD,
			List.of(1, 2, 3), false, true);

	/**
	 * The buildings the extra costs below hang on, by their id in the image map.
	 */
	private static final String DATA_TOWN_HALL = "1000001";
	private static final String DATA_CANNON = "1000008";
	private static final String DATA_ARCHER_TOWER = "1000009";
	private static final String DATA_WIZARD_TOWER = "1000011";
	private static final String DATA_EAGLE_ARTILLERY = "1000031";
	private static final String DATA_MULTI_GEAR_TOWER = "1000079";
	private static final String DATA_MULTI_ARCHER_TOWER = "1000084";
	private static final String DATA_RICOCHET_CANNON = "1000085";
	private static final String DATA_SUPER_WIZARD_TOWER = "1000102";

	/**
	 * What a merged defence was made of.
	 *
	 * Merging two Cannons into a Ricochet Cannon makes both Cannons disappear from
	 * the upload, and with them everything that was ever spent on them - the
	 * merged building only carries its own levels. So for every merged building a
	 * player owns, its source buildings are added back at full price, level 1 to
	 * their maximum.
	 *
	 * Counted per merged building, not once: a player with two Super Wizard Towers
	 * burned four Wizard Towers on them.
	 */
	private static final Map<String, Map<String, Integer>> MERGED_BUILDING_SOURCES = new LinkedHashMap<>();

	static {
		MERGED_BUILDING_SOURCES.put(DATA_RICOCHET_CANNON, Map.of(DATA_CANNON, 2));
		MERGED_BUILDING_SOURCES.put(DATA_MULTI_ARCHER_TOWER, Map.of(DATA_ARCHER_TOWER, 2));
		MERGED_BUILDING_SOURCES.put(DATA_MULTI_GEAR_TOWER, Map.of(DATA_CANNON, 1, DATA_ARCHER_TOWER, 1));
		MERGED_BUILDING_SOURCES.put(DATA_SUPER_WIZARD_TOWER, Map.of(DATA_WIZARD_TOWER, 2));
	}

	/**
	 * From this Town Hall level on, an Eagle Artillery went into the merged
	 * defence as well and is added at full price.
	 */
	private static final int EAGLE_ARTILLERY_FROM_TOWN_HALL = 17;

	/**
	 * Event obstacles a player never cleared - Clashmas Trees, Anniversary Cakes
	 * and the like. Owning at least one of them adds a flat amount that depends on
	 * the Town Hall, once, no matter how many of them are standing around.
	 */
	private static final int KEPT_OBSTACLE_ID_FIRST = 8000031;
	private static final int KEPT_OBSTACLE_ID_LAST = 8000127;

	/**
	 * The currency slot the flat amounts above land in. They were given as bare
	 * numbers; Gold is what every building they belong to is paid in.
	 */
	private static final int KEPT_OBSTACLE_SLOT = 1;

	private static final Map<Integer, Long> KEPT_OBSTACLE_EXTRA = new HashMap<>();

	static {
		KEPT_OBSTACLE_EXTRA.put(13, 10_500_000L);
		KEPT_OBSTACLE_EXTRA.put(14, 24_500_000L);
		KEPT_OBSTACLE_EXTRA.put(15, 43_000_000L);
		KEPT_OBSTACLE_EXTRA.put(16, 66_500_000L);
		KEPT_OBSTACLE_EXTRA.put(17, 66_500_000L);
		KEPT_OBSTACLE_EXTRA.put(18, 120_500_000L);
	}

	/**
	 * Army Camps and Reinforcement Camps are not upgraded, they are bought: what
	 * they cost hangs on how many of them a player owns, not on a level, and the
	 * image map carries no prices for them at all. The tables below are running
	 * totals - the entry for the player's count already is everything the camps
	 * cost them together.
	 */
	private record CountBasedPrices(int slot, NavigableMap<Integer, Long> byCount) {
	}

	/**
	 * Where the prices of a walked entry are added up. The summary of a single
	 * field puts everything into one pot; the prices view holds a pot per
	 * category and asks per entry which one the item belongs in.
	 */
	private interface PriceTarget {
		Map<Integer, Long> totalsFor(String dataId);
	}

	private static final Map<String, CountBasedPrices> COUNT_BASED_PRICES = new HashMap<>();

	static {
		// Army Camp
		COUNT_BASED_PRICES.put("1000042",
				buildCountPrices(4, 0L, 12_000L, 192_000L, 542_000L, 2_542_000L, 5_542_000L));
		// Reinforcement Camp
		COUNT_BASED_PRICES.put("1000049", buildCountPrices(4, 0L, 4_000_000L));
	}

	/**
	 * Turn the prices for owning one, two, three ... of a building into a lookup
	 * keyed by that count.
	 */
	private static CountBasedPrices buildCountPrices(int slot, long... pricesByCount) {
		NavigableMap<Integer, Long> prices = new TreeMap<>();
		for (int i = 0; i < pricesByCount.length; i++) {
			prices.put(i + 1, pricesByCount[i]);
		}
		return new CountBasedPrices(slot, prices);
	}

	/**
	 * Hammer Jam cuts upgrade costs and the Gold Pass discount stacks on top of
	 * the event, so both answers together decide what an upgrade ends up
	 * costing: 40 percent of the original price during the event with a pass, 50
	 * percent during the event without one, 80 percent outside the event with a
	 * pass, and the full price when neither applies.
	 */
	private static final double PRICE_FACTOR_HAMMER_JAM_AND_GOLD_PASS = 0.40;
	private static final double PRICE_FACTOR_HAMMER_JAM = 0.50;
	private static final double PRICE_FACTOR_GOLD_PASS = 0.80;
	private static final double PRICE_FACTOR_FULL = 1.00;

	/**
	 * The share of the original price that is left of it under the event and the
	 * pass the caller asked for.
	 */
	private double priceFactor(boolean hammerJam, boolean goldPass) {
		if (hammerJam) {
			return goldPass ? PRICE_FACTOR_HAMMER_JAM_AND_GOLD_PASS : PRICE_FACTOR_HAMMER_JAM;
		}

		return goldPass ? PRICE_FACTOR_GOLD_PASS : PRICE_FACTOR_FULL;
	}

	private String formatPriceFactor(double factor) {
		if (factor >= PRICE_FACTOR_FULL) {
			return "Originalpreis";
		}

		return Math.round(factor * 100) + " % vom Originalpreis";
	}

	/**
	 * The currency slots of the home village and of the Builder Base, in the
	 * order {@code /stats prices} lists them.
	 */
	private static final List<Integer> HOME_PRICE_SLOTS = List.of(1, 2, 3);
	private static final List<Integer> BUILDER_PRICE_SLOTS = List.of(4, 5);

	/**
	 * Add up the upgrade costs of everything a player owns, kept apart by the
	 * category they sit in, so every currency can be broken down into what each
	 * category of the upload spent on it.
	 *
	 * @param json the player's uploaded data
	 * @return the sums per currency slot per category, the categories in
	 *         alphabetical order, or null when the price list could not be loaded
	 */
	private Map<String, Map<Integer, Long>> collectUpgradePricesByCategory(JSONObject json, PriceScheme scheme) {
		JSONObject imageMap;
		try {
			imageMap = lostmanager.util.ImageMapCache.fetchFullMapOnce();
		} catch (Exception e) {
			System.err.println("Failed to fetch image map for upgrade prices: " + e.getMessage());
			return null;
		}

		if (imageMap == null) {
			return null;
		}

		// TreeMap: the categories come out alphabetically, the order they are
		// listed in under every currency.
		Map<String, Map<Integer, Long>> byCategory = new TreeMap<>();

		for (Map.Entry<String, String> category : STAT_TO_FIELD.entrySet()) {
			String field = category.getValue();
			if (!json.has(field)) {
				continue;
			}

			// Every item asks for its own pot, which is its field's unless the
			// breakdown lists the item apart from it.
			String fieldCategory = category.getKey();
			PriceTarget target = dataId -> byCategory.computeIfAbsent(
					priceCategoryFor(dataId, imageMap, fieldCategory), _ -> new TreeMap<>());

			// A count-based building belongs to exactly one field, so its running
			// total is still read exactly once.
			Map<String, Integer> countBasedOwned = new HashMap<>();
			collectUpgradePrices(json.get(field), imageMap, false, target, countBasedOwned, scheme);
			if (scheme.countBased()) {
				addCountBasedPrices(countBasedOwned, false, target);
			}
		}

		// Merged defences and kept event obstacles are costs of the home village
		// buildings that the walk above cannot see, so they are added to exactly
		// that category.
		addBuildingExtras(json, imageMap, scheme,
				byCategory.computeIfAbsent(CATEGORY_BUILDINGS, _ -> new TreeMap<>()));

		// A category whose entries all cost nothing has nothing to show.
		byCategory.values().removeIf(this::isEverythingFree);

		return byCategory;
	}

	/**
	 * Costs of the home village buildings that the walk over the upload cannot
	 * find on its own: what went into a merged defence before it was merged, and
	 * the flat amount for event obstacles a player kept.
	 *
	 * @param buildingTotals the "Buildings" pot, which is where all of this lands
	 */
	private void addBuildingExtras(JSONObject json, JSONObject imageMap, PriceScheme scheme,
			Map<Integer, Long> buildingTotals) {
		Map<String, Integer> counts = new HashMap<>();
		Map<String, Integer> levels = new HashMap<>();
		collectCountsAndLevels(json.opt(FIELD_BUILDINGS), counts, levels);

		for (Map.Entry<String, Map<String, Integer>> merged : MERGED_BUILDING_SOURCES.entrySet()) {
			int owned = counts.getOrDefault(merged.getKey(), 0);
			if (owned <= 0) {
				continue;
			}

			for (Map.Entry<String, Integer> source : merged.getValue().entrySet()) {
				addFullLevelPrices(imageMap, source.getKey(), owned * source.getValue(), scheme, buildingTotals);
			}
		}

		int townHall = levels.getOrDefault(DATA_TOWN_HALL, 0);

		if (townHall >= EAGLE_ARTILLERY_FROM_TOWN_HALL) {
			addFullLevelPrices(imageMap, DATA_EAGLE_ARTILLERY, 1, scheme, buildingTotals);
		}

		Long obstacleExtra = KEPT_OBSTACLE_EXTRA.get(townHall);
		if (obstacleExtra != null && hasKeptObstacle(json.opt(FIELD_OBSTACLES))) {
			buildingTotals.merge(KEPT_OBSTACLE_SLOT, obstacleExtra, Long::sum);
		}
	}

	/**
	 * Add what {@code times} copies of a building cost over all of its levels.
	 */
	private void addFullLevelPrices(JSONObject imageMap, String dataId, int times, PriceScheme scheme,
			Map<Integer, Long> totals) {
		JSONObject entry = imageMap.optJSONObject(dataId);
		if (entry == null || times <= 0) {
			return;
		}

		addSectionPrices(entry.optJSONObject("levels"), 0, true, times, totals, scheme);
	}

	/**
	 * How many of each building the upload holds, and the level each of them is
	 * on. Walks like {@link #collectOwnedIds} so nested structures are reached the
	 * same way.
	 */
	private void collectCountsAndLevels(Object current, Map<String, Integer> counts, Map<String, Integer> levels) {
		if (current == null || current == JSONObject.NULL) {
			return;
		}

		if (current instanceof JSONArray arr) {
			for (int i = 0; i < arr.length(); i++) {
				collectCountsAndLevels(arr.get(i), counts, levels);
			}
			return;
		}

		if (current instanceof JSONObject obj) {
			if (obj.has("data") && obj.get("data") != JSONObject.NULL) {
				String dataId = String.valueOf(obj.get("data"));
				counts.merge(dataId, Math.max(1, obj.optInt("cnt", 1)), Integer::sum);
				levels.merge(dataId, obj.optInt("lvl", 0), Math::max);
			}

			for (String key : obj.keySet()) {
				Object nested = obj.get(key);
				if (nested instanceof JSONObject || nested instanceof JSONArray) {
					collectCountsAndLevels(nested, counts, levels);
				}
			}
		}
	}

	/**
	 * Whether the upload holds at least one of the event obstacles. One is enough
	 * - the amount is flat, not per obstacle.
	 */
	private boolean hasKeptObstacle(Object obstacles) {
		Set<String> ids = new HashSet<>();
		collectOwnedIds(obstacles, ids);

		for (String id : ids) {
			try {
				int value = Integer.parseInt(id);
				if (value >= KEPT_OBSTACLE_ID_FIRST && value <= KEPT_OBSTACLE_ID_LAST) {
					return true;
				}
			} catch (NumberFormatException e) {
				// Not an id we price on - the next one may well be.
			}
		}

		return false;
	}

	/**
	 * The category an item's costs are listed under: the field it was read from,
	 * unless it is one the breakdown pulls out of that field.
	 *
	 * Walls are stored among the buildings, but they are what most of a Gold
	 * pile actually went into, so they get a line of their own instead of
	 * disappearing into the buildings.
	 */
	private String priceCategoryFor(String dataId, JSONObject imageMap, String fieldCategory) {
		JSONObject entry = imageMap.optJSONObject(dataId);
		if (entry == null) {
			return fieldCategory;
		}

		// "/buildings/wall/" and not just "wall": Wall Breakers and Wall Wreckers
		// are troops and stay where they are.
		String path = getEntryPath(entry).toLowerCase();
		if (path.contains("/buildings/wall/")) {
			return path.contains("/builder-base/") ? "Walls (BB)" : "Walls";
		}

		return fieldCategory;
	}

	private boolean isEverythingFree(Map<Integer, Long> totals) {
		for (Long amount : totals.values()) {
			if (amount != null && amount > 0) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The whole price block: one section per currency, the home village first and
	 * the Builder Base after it.
	 *
	 * Every known currency gets a section even when nothing in the data is paid
	 * for in it - a missing section reads like a bug, a zero reads like an
	 * answer.
	 */
	private String formatPriceTotals(Map<String, Map<Integer, Long>> byCategory, double factor, PriceScheme scheme) {
		StringBuilder sb = new StringBuilder();

		for (Integer slot : orderedPriceSlots(byCategory, scheme)) {
			appendResourceSection(sb, slot, byCategory, factor);
		}

		return sb.toString();
	}

	/**
	 * The currencies to print, in village order, followed by any slot the price
	 * list introduces later - leaving one out would hide costs without a trace.
	 */
	private List<Integer> orderedPriceSlots(Map<String, Map<Integer, Long>> byCategory, PriceScheme scheme) {
		// A scheme that names its currencies shows exactly those - the old prices
		// exist for the three of the home village and nowhere else.
		if (scheme.slots() != null) {
			return scheme.slots();
		}

		List<Integer> slots = new ArrayList<>(HOME_PRICE_SLOTS);
		slots.addAll(BUILDER_PRICE_SLOTS);

		Set<Integer> unknownSlots = new TreeSet<>();
		for (Map<Integer, Long> totals : byCategory.values()) {
			for (Integer slot : totals.keySet()) {
				if (!slots.contains(slot)) {
					unknownSlots.add(slot);
				}
			}
		}
		slots.addAll(unknownSlots);

		return slots;
	}

	/**
	 * One currency: every category that is paid for in it, alphabetically, and
	 * the sum of exactly those lines underneath them. A category that costs
	 * nothing in this currency has nothing to say here and stays out.
	 */
	private void appendResourceSection(StringBuilder sb, int slot, Map<String, Map<Integer, Long>> byCategory,
			double factor) {
		String label = PRICE_LABELS.getOrDefault(slot, "Währung " + slot);
		String indent = EmbedBuilder.ZERO_WIDTH_SPACE.repeat(2);

		sb.append("\n**").append(label).append(":**");

		long total = 0L;
		long originalTotal = 0L;

		for (Map.Entry<String, Map<Integer, Long>> category : byCategory.entrySet()) {
			long original = category.getValue().getOrDefault(slot, 0L);
			if (original <= 0) {
				continue;
			}

			long amount = priceAtFactor(original, factor);
			total += amount;
			originalTotal += original;

			sb.append("\n").append(indent).append("· ").append(category.getKey()).append(": ")
					.append(formatAmount(amount));
		}

		// Added up from the rounded lines above instead of being rounded itself,
		// so the section adds up exactly as it is printed.
		sb.append("\n").append(indent).append("**Gesamt: ").append(formatAmount(total)).append("**");

		if (factor < PRICE_FACTOR_FULL) {
			sb.append(" (Original: ").append(formatAmount(originalTotal)).append(")");
		}
	}

	private long priceAtFactor(long amount, double factor) {
		return Math.round(amount * factor);
	}

	/**
	 * Group digits the German way so the big numbers stay readable.
	 */
	private String formatAmount(long amount) {
		return String.format(Locale.GERMANY, "%,d", amount);
	}

	/**
	 * One "· Label: 1.234" line per currency slot.
	 */
	private void appendPriceLines(StringBuilder sb, Map<Integer, Long> totals) {
		for (Map.Entry<Integer, Long> total : totals.entrySet()) {
			String label = PRICE_LABELS.getOrDefault(total.getKey(), "Währung " + total.getKey());
			sb.append("\n").append(EmbedBuilder.ZERO_WIDTH_SPACE.repeat(2)).append("· ")
					.append(label).append(": ").append(formatAmount(total.getValue()));
		}
	}

	/**
	 * Sum up what the entries currently on screen cost.
	 *
	 * The summary follows the view: in "Vorhanden" mode it adds up every level
	 * the player has actually reached, in "Fehlend" mode the full cost of the
	 * entries they do not own yet - a number under a header that says
	 * "Buildings / Fehlend" has to be about exactly those.
	 *
	 * @param dataToDisplay the same data the page renders
	 * @param missingMode   true while the missing entries are shown
	 * @return the formatted block, or an empty string when nothing has a price
	 */
	@SuppressWarnings("null")
	private String formatUpgradePriceSummary(Object dataToDisplay, boolean missingMode) {
		JSONObject imageMap;
		try {
			imageMap = lostmanager.util.ImageMapCache.fetchFullMapOnce();
		} catch (Exception e) {
			System.err.println("Failed to fetch image map for upgrade prices: " + e.getMessage());
			return "";
		}

		if (imageMap == null) {
			return "";
		}

		// TreeMap: slots come out in numeric order, whichever ones the data holds.
		Map<Integer, Long> totals = new TreeMap<>();
		Map<String, Integer> countBasedOwned = new HashMap<>();

		// One field, one pot - this summary does not break the costs down.
		PriceTarget target = _ -> totals;
		collectUpgradePrices(dataToDisplay, imageMap, missingMode, target, countBasedOwned, SCHEME_CURRENT);
		addCountBasedPrices(countBasedOwned, missingMode, target);

		if (totals.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		sb.append("**").append(missingMode ? "Kosten der fehlenden Einträge (voll ausgebaut)"
				: "Investierte Upgrade-Kosten").append(":**");
		appendPriceLines(sb, totals);

		// What Hammer Jam and a Gold Pass leave of these costs is its own view,
		// /stats prices, where both are answered per call instead of guessed at.
		return sb.toString();
	}

	/**
	 * Walk the displayed data and add up the prices of every entry in it. Shaped
	 * like {@link #collectOwnedIds} so it reaches entries in nested structures
	 * the same way.
	 */
	private void collectUpgradePrices(Object current, JSONObject imageMap, boolean missingMode,
			PriceTarget target, Map<String, Integer> countBasedOwned, PriceScheme scheme) {
		if (current == null || current == JSONObject.NULL) {
			return;
		}

		if (current instanceof JSONArray arr) {
			for (int i = 0; i < arr.length(); i++) {
				collectUpgradePrices(arr.get(i), imageMap, missingMode, target, countBasedOwned, scheme);
			}
			return;
		}

		if (current instanceof JSONObject obj) {
			if (obj.has("data") && obj.get("data") != JSONObject.NULL) {
				String dataId = String.valueOf(obj.get("data"));
				JSONObject itemData = imageMap.optJSONObject(dataId);
				if (itemData != null) {
					// Identical buildings are grouped into one entry via "cnt", and each
					// of them was paid for separately.
					int count = Math.max(1, obj.optInt("cnt", 1));

					if (COUNT_BASED_PRICES.containsKey(dataId)) {
						// Here the price follows the total number owned, and that total can
						// be spread over several entries - so only tally the camps now and
						// price them once the whole walk is done.
						countBasedOwned.merge(dataId, count, Integer::sum);
					} else {
						Map<Integer, Long> totals = target.totalsFor(dataId);
						addSectionPrices(itemData.optJSONObject("levels"), obj.optInt("lvl", 0), missingMode, count,
								totals, scheme);
						addSectionPrices(itemData.optJSONObject("supercharge"), obj.optInt("supercharge", 0),
								missingMode, count, totals, scheme);
					}
				}
			}

			for (String key : obj.keySet()) {
				Object nested = obj.get(key);
				if (nested instanceof JSONObject || nested instanceof JSONArray) {
					collectUpgradePrices(nested, imageMap, missingMode, target, countBasedOwned, scheme);
				}
			}
		}
	}

	/**
	 * Add what the count-based buildings cost to the totals.
	 *
	 * @param countBasedOwned how many of each of them the walk found
	 * @param missingMode     price them as if every one of them were bought - a
	 *                        missing entry carries no count, and the summary above
	 *                        it promises the cost of the finished building
	 */
	private void addCountBasedPrices(Map<String, Integer> countBasedOwned, boolean missingMode,
			PriceTarget target) {
		for (Map.Entry<String, Integer> owned : countBasedOwned.entrySet()) {
			CountBasedPrices prices = COUNT_BASED_PRICES.get(owned.getKey());
			if (prices == null || prices.byCount().isEmpty()) {
				continue;
			}

			int count = missingMode ? prices.byCount().lastKey() : owned.getValue();

			// A count past the end of the table stays at its last entry rather than
			// dropping out of the sum.
			Map.Entry<Integer, Long> price = prices.byCount().floorEntry(count);
			if (price != null && price.getValue() > 0) {
				target.totalsFor(owned.getKey()).merge(prices.slot(), price.getValue(), Long::sum);
			}
		}
	}

	/**
	 * Add the prices of one section ("levels" or "supercharge") to the totals.
	 *
	 * A level entry carries the price of reaching that level (level 1 of the Town
	 * Hall costs 0), so everything up to the reached level is what was paid.
	 *
	 * @param reachedLevel the level the player is on; ignored when allLevels is set
	 * @param allLevels    sum the whole section instead - the item is not owned
	 */
	private void addSectionPrices(JSONObject section, int reachedLevel, boolean allLevels, int count,
			Map<Integer, Long> totals, PriceScheme scheme) {
		if (section == null) {
			return;
		}

		for (String levelKey : section.keySet()) {
			if (!allLevels) {
				try {
					if (Integer.parseInt(levelKey) > reachedLevel) {
						continue;
					}
				} catch (NumberFormatException e) {
					continue;
				}
			}

			JSONObject levelData = section.optJSONObject(levelKey);
			if (levelData == null) {
				continue;
			}

			for (String priceKey : levelData.keySet()) {
				if (!priceKey.startsWith(scheme.keyPrefix())) {
					continue;
				}

				// No slot allowlist on purpose: whatever slot the data introduces gets
				// its own line instead of being dropped without a trace. What the
				// number behind the prefix also does is keep the two schemes apart:
				// "upgrade-price-old1" starts with "upgrade-price" as well, and only
				// fails to parse as a number - which is exactly what keeps the old
				// prices out of the current view.
				int slot;
				try {
					slot = Integer.parseInt(priceKey.substring(scheme.keyPrefix().length()));
				} catch (NumberFormatException e) {
					continue;
				}

				if (levelData.get(priceKey) instanceof Number price) {
					totals.merge(slot, price.longValue() * count, Long::sum);
				}
			}
		}
	}

	/**
	 * Get emoji for a level-based item This should be called when displaying the
	 * "Level: XX" line for items with levels
	 * 
	 * @param dataValue The data ID
	 * @param level     The level number
	 * @return The emoji string or null if not available
	 */
	private String getEmojiForLevel(String dataValue, int level) {
		try {
			String levelPath = lostmanager.util.ImageMapCache.getLevelPath(dataValue, level);
			if (levelPath != null && !levelPath.isEmpty()) {
				String name = lostmanager.util.ImageMapCache.getName(dataValue);
				if (name == null) {
					name = dataValue;
				}
				return getOrCreateEmojiForPath(levelPath, name + "_" + level);
			}
		} catch (Exception e) {
			System.err.println("Error getting emoji for level " + level + " of '" + dataValue + "': " + e.getMessage());
		}
		return null;
	}

	/**
	 * Get or create an app emoji for the given image path
	 * 
	 * @param imagePath The relative image path from image_map.json
	 * @param baseName  The base name for the emoji
	 * @return The emoji in Discord format or null
	 */
	private String getOrCreateEmojiForPath(String imagePath, String baseName) {
		try {
			return lostmanager.util.EmojiManager.getOrCreateEmoji(imagePath, baseName);
		} catch (Exception e) {
			System.err.println("Error creating emoji for path '" + imagePath + "': " + e.getMessage());
			return null;
		}
	}

	private String getTitleForMode(String mode) {
		return MODE_MISSING.equals(mode) ? "Spieler Fehlende Stats" : "Spieler Stats";
	}

	private String normalizeMode(String mode) {
		return MODE_MISSING.equals(mode) ? MODE_MISSING : MODE_OWNED;
	}

	private JSONArray buildMissingData(String statType, Object fieldData) {
		JSONObject imageMapCache = null;
		try {
			imageMapCache = lostmanager.util.ImageMapCache.fetchFullMapOnce();
		} catch (Exception e) {
			System.err.println("Failed to fetch image map for missing-data: " + e.getMessage());
		}

		if (imageMapCache == null) {
			return new JSONArray();
		}

		Set<String> fullMapIds = getAllIdsForStatType(statType, imageMapCache);
		if (fullMapIds.isEmpty()) {
			return new JSONArray();
		}

		Set<String> ownedIds = new HashSet<>();
		collectOwnedIds(fieldData, ownedIds);

		List<String> missingIds = new ArrayList<>();
		for (String id : fullMapIds) {
			if (!ownedIds.contains(id) && !FilteredIdsCache.isFiltered(id)) {
				missingIds.add(id);
			}
		}

		final JSONObject finalImageMapCache = imageMapCache;
		missingIds.sort((id1, id2) -> compareDataIdsByIndex(id1, id2, finalImageMapCache));

		JSONArray missingData = new JSONArray();
		for (String id : missingIds) {
			missingData.put(new JSONObject().put("data", id));
		}

		return missingData;
	}

	private int compareDataIdsByIndex(String id1, String id2, JSONObject imageMapCache) {
		if (imageMapCache != null) {
			JSONObject d1 = imageMapCache.optJSONObject(id1);
			JSONObject d2 = imageMapCache.optJSONObject(id2);

			int i1 = d1 != null ? d1.optInt("index", Integer.MAX_VALUE) : Integer.MAX_VALUE;
			int i2 = d2 != null ? d2.optInt("index", Integer.MAX_VALUE) : Integer.MAX_VALUE;
			if (i1 != i2) {
				return Integer.compare(i1, i2);
			}
		}

		try {
			return Integer.compare(Integer.parseInt(id1), Integer.parseInt(id2));
		} catch (NumberFormatException e) {
			return id1.compareTo(id2);
		}
	}

	private void collectOwnedIds(Object current, Set<String> ownedIds) {
		if (current == null || current == JSONObject.NULL) {
			return;
		}

		if (current instanceof JSONArray arr) {
			for (int i = 0; i < arr.length(); i++) {
				Object item = arr.get(i);
				if (item instanceof JSONObject || item instanceof JSONArray) {
					collectOwnedIds(item, ownedIds);
				} else if (item != null && item != JSONObject.NULL) {
					ownedIds.add(String.valueOf(item));
				}
			}
			return;
		}

		if (current instanceof JSONObject obj) {
			if (obj.has("data") && obj.get("data") != null && obj.get("data") != JSONObject.NULL) {
				ownedIds.add(String.valueOf(obj.get("data")));
			}

			for (String key : obj.keySet()) {
				Object nested = obj.get(key);
				if (nested instanceof JSONObject || nested instanceof JSONArray) {
					collectOwnedIds(nested, ownedIds);
				}
			}
		}
	}

	private Set<String> getAllIdsForStatType(String statType, JSONObject imageMap) {
		Set<String> ids = new HashSet<>();

		for (String id : imageMap.keySet()) {
			JSONObject entry = imageMap.optJSONObject(id);
			if (entry == null) {
				continue;
			}

			if (matchesStatType(statType, entry)) {
				ids.add(id);
			}
		}

		return ids;
	}

	private boolean matchesStatType(String statType, JSONObject entry) {
		String path = getEntryPath(entry).toLowerCase();
		String name = entry.optString("name", "").toLowerCase();

		boolean isHome = path.contains("/home-base/");
		boolean isBuilder = path.contains("/builder-base/");
		boolean isBuilderVariant = isBuilderVariantName(name);

		switch (statType) {
			case "Buildings" -> {
				return path.contains("/buildings/") && isHome;
			}
			case "Decos" -> {
				return path.contains("/decorations/") && !isBuilderVariant;
			}
			case "Equips" -> {
				return path.contains("/equipment/");
			}
			case "Guardians" -> {
				return path.contains("/guardians/");
			}
			case "Helpers" -> {
				return path.contains("/apprentice/") || path.contains("/helpers/") || path.contains("/helper/");
			}
			case "Heroes" -> {
				return path.contains("/heroes/") && isHome;
			}
			case "House Parts" -> {
				return path.contains("/clancapital/") || path.contains("/capital-house/")
						|| path.contains("/house-parts/");
			}
			case "Obstacles" -> {
				return path.contains("/obstacles/") && !isBuilderVariant;
			}
			case "Pets" -> {
				return path.contains("/pets/");
			}
			case "Sceneries" -> {
				return path.contains("/sceneries/") && !isBuilderVariant;
			}
			case "Sieges" -> {
				return path.contains("/troops/") && isHome && isSiegeMachine(name);
			}
			case "Skins" -> {
				return path.contains("/skins/") && isHome;
			}
			case "Spells" -> {
				return path.contains("/spells/");
			}
			case "Traps" -> {
				return path.contains("/traps/") && isHome;
			}
			case "Units" -> {
				return path.contains("/troops/") && isHome && !isSiegeMachine(name);
			}
			case "Buildings (BB)" -> {
				return path.contains("/buildings/") && isBuilder;
			}
			case "Decos (BB)" -> {
				return path.contains("/decorations/") && isBuilderVariant;
			}
			case "Heroes (BB)" -> {
				return path.contains("/heroes/") && isBuilder;
			}
			case "Obstacles (BB)" -> {
				return path.contains("/obstacles/") && isBuilderVariant;
			}
			case "Sceneries (BB)" -> {
				return path.contains("/builder-base/sceneries/") || (path.contains("/sceneries/") && isBuilderVariant);
			}
			case "Skins (BB)" -> {
				return path.contains("/skins/") && isBuilder;
			}
			case "Traps (BB)" -> {
				return path.contains("/traps/") && isBuilder;
			}
			case "Units (BB)" -> {
				return path.contains("/troops/") && isBuilder;
			}
			default -> {
				return false;
			}
		}
	}

	private String getEntryPath(JSONObject entry) {
		String icon = entry.optString("icon", "");
		if (!icon.isEmpty()) {
			return icon;
		}

		String character = entry.optString("character", "");
		if (!character.isEmpty()) {
			return character;
		}

		String characterAlt = entry.optString("character_2", "");
		if (!characterAlt.isEmpty()) {
			return characterAlt;
		}

		String characterAlt2 = entry.optString("character-2", "");
		if (!characterAlt2.isEmpty()) {
			return characterAlt2;
		}

		JSONObject levels = entry.optJSONObject("levels");
		if (levels != null && !levels.isEmpty()) {
			String selectedPath = "";
			int selectedLevel = Integer.MAX_VALUE;

			for (String lvl : levels.keySet()) {
				// Level values used to be the path itself and are objects now, so
				// optString would hand back the whole JSON text of the level.
				Object levelValue = levels.get(lvl);
				String levelPath = levelValue instanceof JSONObject levelObject
						? levelObject.optString("level-icon", "")
						: String.valueOf(levelValue);
				if (levelPath.isEmpty()) {
					continue;
				}

				try {
					int lvlInt = Integer.parseInt(lvl);
					if (lvlInt < selectedLevel) {
						selectedLevel = lvlInt;
						selectedPath = levelPath;
					}
				} catch (NumberFormatException e) {
					if (selectedPath.isEmpty()) {
						selectedPath = levelPath;
					}
				}
			}

			if (!selectedPath.isEmpty()) {
				return selectedPath;
			}
		}

		return "";
	}

	/**
	 * Decos, obstacles and sceneries all live under /home-base/ in the image map, so
	 * the only reliable BB marker is the explicit "(BB)" suffix in the name. Looser
	 * substrings like "builder" would misclassify home items such as
	 * "Builder Statue" or "Builder Bust".
	 */
	private boolean isBuilderVariantName(String nameLower) {
		return nameLower.contains("(bb)");
	}

	private boolean isSiegeMachine(String nameLower) {
		return nameLower.contains("wrecker") || nameLower.contains("blimp") || nameLower.contains("slammer")
				|| nameLower.contains("siege") || nameLower.contains("launcher") || nameLower.contains("flinger")
				|| nameLower.contains("drill");
	}

	/**
	 * Create stat options for select menu
	 */
	@SuppressWarnings("null")
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
	 * Encode parameters into a Base64 string for button ID with page number
	 */
	private String encodeButtonId(String playerTag, String statType, int pageNumber, String mode) {
		// Format: playerTag|statType|pageNumber|mode
		String data = playerTag + "|" + statType + "|" + pageNumber + "|" + normalizeMode(mode);
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
	 * Encode the prices view into its refresh button: the player plus the two
	 * answers the view was built with, so a refresh recomputes the same view.
	 */
	private String encodePricesButtonId(String playerTag, boolean hammerJam, boolean goldPass, PriceScheme scheme) {
		String data = playerTag + "|" + (hammerJam ? "ja" : "nein") + "|" + (goldPass ? "ja" : "nein") + "|"
				+ scheme.key();
		return BUTTON_PRICES_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
	}

	/**
	 * Decode a prices refresh button ID
	 */
	private String[] decodePricesButtonId(String buttonId) {
		String encoded = buttonId.substring(BUTTON_PRICES_PREFIX.length());
		String data = new String(Base64.getUrlDecoder().decode(encoded));

		return data.split("\\|", -1);
	}

	/**
	 * Encode player tag into select menu ID
	 */
	private String encodeSelectMenuId(String playerTag, String mode) {
		String data = playerTag + "|" + normalizeMode(mode);
		return SELECT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
	}

	/**
	 * Decode select menu ID
	 */
	private String[] decodeSelectMenuId(String selectMenuId) {
		// Remove prefix
		String encoded = selectMenuId.substring(SELECT_PREFIX.length());

		// Decode Base64
		String data = new String(Base64.getUrlDecoder().decode(encoded));

		// Split by | (legacy payload may only contain playerTag)
		return data.split("\\|", -1);
	}

	/**
	 * Split formatted data into pages of maximum MAX_PAGE_LENGTH characters
	 */
	private List<String> splitIntoPages(String formattedData, String headerText) {
		List<String> pages = new ArrayList<>();

		// Reserve space for header and page indicator (e.g., "\n\nSeite 99/99")
		int reservedSpace = headerText.length() + 20;
		int availableSpace = MAX_PAGE_LENGTH - reservedSpace;

		if (formattedData.length() <= availableSpace) {
			// No pagination needed
			pages.add(formattedData);
			return pages;
		}

		// Split by lines to avoid breaking in the middle of an item
		// Using -1 as limit preserves trailing empty strings for consistent formatting
		String[] lines = formattedData.split("\n", -1);
		StringBuilder currentPage = new StringBuilder();

		for (String line : lines) {
			// Check if adding this line would exceed the limit
			int lineLength = line.length() + 1; // +1 for newline
			if (currentPage.length() + lineLength > availableSpace && currentPage.length() > 0) {
				// Start a new page
				pages.add(currentPage.toString());
				currentPage = new StringBuilder();
			}

			// Add line to current page
			if (currentPage.length() > 0) {
				currentPage.append("\n");
			}
			currentPage.append(line);
		}

		// Add the last page if it has content
		if (currentPage.length() > 0) {
			pages.add(currentPage.toString());
		}

		// Defensive fallback: If no pages were created (should not happen with valid
		// input), add one page
		if (pages.isEmpty()) {
			pages.add("Keine Daten vorhanden");
		}

		return pages;
	}

	/**
	 * Encode navigation button ID (forward/backward)
	 */
	private String encodeNavigationButtonId(String playerTag, String statType, int pageNumber, String prefix,
			String mode) {
		// Format: playerTag|statType|pageNumber|mode
		String data = playerTag + "|" + statType + "|" + pageNumber + "|" + normalizeMode(mode);
		return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(data.getBytes());
	}

	/**
	 * Decode navigation button ID
	 */
	private String[] decodeNavigationButtonId(String buttonId, String prefix) {
		// Remove prefix
		String encoded = buttonId.substring(prefix.length());

		// Decode Base64
		String data = new String(Base64.getUrlDecoder().decode(encoded));

		// Split by |
		return data.split("\\|", -1);
	}
}
