package lostmanager.commands.coc.util.playerutils;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class wins extends ListenerAdapter {

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("wins"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Wins Statistik";

			OptionMapping clanOption = event.getOption("clan");
			OptionMapping playerOption = event.getOption("player");
			OptionMapping seasonOption = event.getOption("season");

			if (clanOption == null && playerOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Einer der beiden Parameter (clan oder player) ist erforderlich!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (clanOption != null && playerOption != null) {
				event.getHook().editOriginalEmbeds(
						MessageUtil.buildEmbed(title, "Bitte gib nur einen Parameter an!", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			if (seasonOption == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Der Season Parameter ist erforderlich!", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			String seasonStr = seasonOption.getAsString();
			YearMonth selectedMonth = YearMonth.parse(seasonStr, DateTimeFormatter.ofPattern("yyyy-MM"));
			YearMonth currentMonth = YearMonth.now();

			String desc = "";

			if (playerOption != null) {
				// Single player mode
				String playerTag = playerOption.getAsString();
				Player player = new Player(playerTag);

				if (!player.IsLinked()) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler ist nicht verifiziert.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}

				desc += "## " + MessageUtil.unformat(player.getInfoStringDB()) + "\n";
				desc += getPlayerWinsForSeason(player, selectedMonth, currentMonth);

			} else if (clanOption != null) {
				// Clan mode
				String clanTag = clanOption.getAsString();
				Clan clan = new Clan(clanTag);

				desc += "## " + MessageUtil.unformat(clan.getInfoString()) + "\n";
				desc += "Season: " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
						+ "\n\n";

				ArrayList<Player> members = clan.getPlayersDB();
				if (members == null || members.isEmpty()) {
					desc += "Keine Mitglieder gefunden.\n";
				} else {
					// Sort members by wins (descending)
					ArrayList<PlayerWinsData> playerWinsDataList = new ArrayList<>();

					for (Player member : members) {
						if (!member.IsLinked()) {
							continue;
						}

						java.time.ZoneId zone = java.time.ZoneId.of("Europe/Berlin");
						java.time.ZonedDateTime startOfMonth = java.time.ZonedDateTime.of(selectedMonth.getYear(),
								selectedMonth.getMonthValue(), 1, 0, 0, 0, 0, zone);
						Player.WinsData wd = member.getMonthlyWins(selectedMonth.getYear(),
								selectedMonth.getMonthValue(),
								selectedMonth.equals(currentMonth), startOfMonth, startOfMonth.plusMonths(1), zone);
						if (wd != null) {
							playerWinsDataList.add(new PlayerWinsData(member, wd.wins));
						}
					}

					// Sort by wins descending
					playerWinsDataList.sort((a, b) -> b.wins.compareTo(a.wins));

					for (int i = 0; i < playerWinsDataList.size(); i++) {
						PlayerWinsData data = playerWinsDataList.get(i);
						desc += (i + 1) + ". " + MessageUtil.unformat(data.player.getInfoStringDB()) + ": **"
								+ data.wins + "** Wins\n";
					}

					if (playerWinsDataList.isEmpty()) {
						desc += "Keine Daten für diese Season verfügbar.\n";
					}
				}
			}

			event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO)).queue();
		}, "WinsCommand-" + event.getUser().getId()).start();
	}

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("wins"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("player")) {
				List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);
				event.replyChoices(choices).queue();

			} else if (focused.equals("clan")) {
				List<Command.Choice> choices = DBManager.getClansAutocomplete(input);
				event.replyChoices(choices).queue();

			} else if (focused.equals("season")) {
				List<Command.Choice> choices = getAvailableSeasons(input);
				event.replyChoices(choices).queue();
			}
		}, "WinsAutocomplete-" + event.getUser().getId()).start();
	}

	/**
	 * Get list of available seasons based on data in the database
	 */
	@SuppressWarnings("null")
	private List<Command.Choice> getAvailableSeasons(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		// Letzte 6 Monate inkl. aktuellem Monat
		YearMonth currentMonth = YearMonth.now();
		List<YearMonth> months = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			months.add(currentMonth.minusMonths(i));
		}

		// Sortiert (currentMonth ist sowieso der neueste, aber zur Sicherheit)
		months.sort((a, b) -> b.compareTo(a));

		DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN);
		DateTimeFormatter valueFormatter = DateTimeFormatter.ofPattern("yyyy-MM");

		for (YearMonth month : months) {
			String display = month.format(displayFormatter);
			String value = month.format(valueFormatter);

			if (input == null || input.isEmpty() || display.toLowerCase().contains(input.toLowerCase())
					|| value.contains(input)) {

				choices.add(new Command.Choice(display, value));
				if (choices.size() >= 25) {
					break;
				}
			}
		}

		// Falls nach Filter nix übrig bleibt, trotzdem aktuellen Monat anbieten
		if (choices.isEmpty()) {
			String display = currentMonth.format(displayFormatter);
			String value = currentMonth.format(valueFormatter);
			choices.add(new Command.Choice(display, value));
		}

		return choices;
	}

	/**
	 * Get player wins description for a specific season
	 */
	private String getPlayerWinsForSeason(Player player, YearMonth selectedMonth, YearMonth currentMonth) {

		java.time.ZoneId zone = java.time.ZoneId.of("Europe/Berlin");
		java.time.ZonedDateTime startOfMonth = java.time.ZonedDateTime.of(selectedMonth.getYear(),
				selectedMonth.getMonthValue(), 1, 0, 0, 0, 0, zone);
		Player.WinsData winsData = player.getMonthlyWins(selectedMonth.getYear(), selectedMonth.getMonthValue(),
				selectedMonth.equals(currentMonth), startOfMonth, startOfMonth.plusMonths(1), zone);

		if (winsData == null) {
			return "Season: " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
					+ "\n\n⚠️ noch keine Daten, zu kurz verlinkt\n";
		}

		String warning = winsData.hasWarning ? " ⚠️" : "";
		String warningNote = winsData.hasWarning
				? "\n\n⚠️ *Spieler wurde mitten in der Season verlinkt - Daten unvollständig*"
				: "";

		return "Season: " + selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)) + "\n\n**"
				+ winsData.wins + "** Wins in dieser Season" + warning + warningNote + "\n";
	}

	/**
	 * Helper class to store player and wins data for sorting
	 */
	private static class PlayerWinsData {
		Player player;
		Integer wins;

		PlayerWinsData(Player player, Integer wins) {
			this.player = player;
			this.wins = wins;
		}
	}
}
