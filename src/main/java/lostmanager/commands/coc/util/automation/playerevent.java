package lostmanager.commands.coc.util.automation;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.PlayerListeningEvent;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Manages the player listening events - watchers that fire when a value of a
 * single player changes, as opposed to the clan-wide, time-scheduled events of
 * {@link listeningevent}.
 *
 * A watcher reports to the DMs of whoever created it. There is no channel and no
 * recipient option: either one would let a member aim the bot's messages
 * somewhere they cannot post themselves.
 */
public class playerevent extends ListenerAdapter {

	private static final String TITLE = "Player Event";

	/** Leaves room for the last entry under the 4096 character embed description limit. */
	private static final int EMBED_DESCRIPTION_BUDGET = 3600;

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("playerevent")) {
			return;
		}

		String subcommand = event.getSubcommandName();
		if (subcommand == null) {
			event.replyEmbeds(
					MessageUtil.buildEmbed(TITLE, "Bitte wähle einen Unterbefehl aus.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		// The add path talks to the Clash of Clans API and sends a blocking DM, neither
		// of which may hold up the interaction thread.
		new Thread(() -> {
			switch (subcommand) {
				case "add" -> handleAdd(event);
				case "list" -> handleList(event);
				case "remove" -> handleRemove(event);
				default -> event.replyEmbeds(
						MessageUtil.buildEmbed(TITLE, "Unbekannter Unterbefehl.", MessageUtil.EmbedType.ERROR)).queue();
			}
		}, "PlayereventCommand-" + event.getUser().getId()).start();
	}

	// ============================================================
	// Subcommands
	// ============================================================

	@SuppressWarnings("null")
	private void handleAdd(SlashCommandInteractionEvent event) {
		event.deferReply(true).queue();

		OptionMapping playerOption = event.getOption("player");
		OptionMapping typeOption = event.getOption("type");

		if (playerOption == null || typeOption == null) {
			reply(event, "Spieler und Typ sind erforderlich!", MessageUtil.EmbedType.ERROR);
			return;
		}

		String playerTag = normalizeTag(playerOption.getAsString());
		PlayerListeningEvent.LISTENINGTYPE type = PlayerListeningEvent.LISTENINGTYPE
				.fromString(typeOption.getAsString());
		if (type == null) {
			reply(event, "Unbekannter Typ: " + typeOption.getAsString(), MessageUtil.EmbedType.ERROR);
			return;
		}

		String userId = event.getUser().getId();
		User executor = new User(userId);
		if (!mayManage(executor, playerTag)) {
			reply(event, "Du kannst nur Events für deine eigenen verlinkten Accounts erstellen. "
					+ "Für andere Spieler brauchst du mindestens Vize-Anführer.", MessageUtil.EmbedType.ERROR);
			return;
		}

		// One API call validates the tag and provides the baseline the first change is
		// measured against.
		Player player = new Player(playerTag);
		String json = player.getJson();
		if (json == null) {
			reply(event, "Der Spieler " + playerTag + " konnte nicht über die API gefunden werden.",
					MessageUtil.EmbedType.ERROR);
			return;
		}

		Long baseline;
		try {
			baseline = type.readValue(new JSONObject(json));
		} catch (final org.json.JSONException e) {
			reply(event, "Die API-Antwort für " + playerTag + " konnte nicht gelesen werden.",
					MessageUtil.EmbedType.ERROR);
			return;
		}
		if (baseline == null) {
			reply(event, "Für diesen Spieler liefert die API keinen Wert für " + type.getLabel() + ".",
					MessageUtil.EmbedType.ERROR);
			return;
		}

		// A second watcher on the same value would only double every DM.
		for (final PlayerListeningEvent existing : PlayerListeningEvent.getForUser(userId)) {
			if (existing.getListeningType() == type && playerTag.equals(existing.getPlayerTag())) {
				reply(event, "Du hast für diesen Spieler bereits ein " + type.getLabel() + "-Event (ID "
						+ existing.getId() + ").", MessageUtil.EmbedType.ERROR);
				return;
			}
		}

		Long id = PlayerListeningEvent.create(playerTag, type, PlayerListeningEvent.ACTIONTYPE.DM, userId,
				new ArrayList<>(), baseline);
		if (id == null) {
			reply(event, "Das Event konnte nicht gespeichert werden.", MessageUtil.EmbedType.ERROR);
			return;
		}

		StringBuilder desc = new StringBuilder();
		desc.append("**ID:** ").append(id).append("\n");
		desc.append("**Spieler:** ").append(MessageUtil.unformat(displayName(player, playerTag))).append("\n");
		desc.append("**Typ:** ").append(type.getEmoji()).append(" ").append(type.getLabel()).append("\n");
		desc.append("**Aktueller Wert:** ").append(PlayerListeningEvent.formatNumber(baseline)).append("\n\n");
		desc.append("Ab jetzt bekommst du jede Änderung per DM (Prüfung alle 2 Minuten).");

		// The confirmation doubles as the delivery test. A watcher that cannot reach
		// its owner is worthless, so it is rolled back instead of failing quietly on
		// every future change.
		boolean delivered = PlayerListeningEvent.sendDirectMessage(userId,
				MessageUtil.buildEmbed(TITLE, desc.toString(), MessageUtil.EmbedType.SUCCESS));

		if (!delivered) {
			PlayerListeningEvent.delete(id);
			reply(event, "Der Bot kann dir keine DM schicken, deshalb wurde das Event nicht angelegt.\n"
					+ "Erlaube in den Server-Einstellungen unter *Privatsphäre* Direktnachrichten von "
					+ "Servermitgliedern und versuche es erneut.", MessageUtil.EmbedType.ERROR);
			return;
		}

		reply(event, desc.toString(), MessageUtil.EmbedType.SUCCESS);
	}

	private void handleList(SlashCommandInteractionEvent event) {
		event.deferReply(true).queue();

		OptionMapping playerOption = event.getOption("player");
		User executor = new User(event.getUser().getId());
		boolean seesAll = isColeaderOrHigher(executor);

		ArrayList<PlayerListeningEvent> events;
		if (seesAll) {
			events = playerOption != null
					? PlayerListeningEvent.getForPlayer(normalizeTag(playerOption.getAsString()))
					: PlayerListeningEvent.getAll();
		} else {
			// Everyone else only ever sees their own watchers.
			events = PlayerListeningEvent.getForUser(event.getUser().getId());
			if (playerOption != null) {
				String filter = normalizeTag(playerOption.getAsString());
				events.removeIf(e -> !filter.equals(e.getPlayerTag()));
			}
		}

		StringBuilder desc = new StringBuilder("## Player Events\n\n");
		if (events.isEmpty()) {
			desc.append("Keine Events gefunden.");
		} else {
			int shown = 0;
			for (final PlayerListeningEvent playerEvent : events) {
				// Discord rejects an embed whose description is over 4096 characters, so
				// stop before the whole reply is lost and say how many were left out.
				if (desc.length() > EMBED_DESCRIPTION_BUDGET) {
					desc.append("*... und ").append(events.size() - shown)
							.append(" weitere. Mit `player:` filtern.*");
					break;
				}
				shown++;

				Player player = new Player(playerEvent.getPlayerTag());
				desc.append("**ID:** ").append(playerEvent.getId()).append("\n");
				desc.append("**Spieler:** ")
						.append(MessageUtil.unformat(displayName(player, playerEvent.getPlayerTag()))).append("\n");

				PlayerListeningEvent.LISTENINGTYPE type = playerEvent.getListeningType();
				if (type == null) {
					desc.append("**Typ:** UNKNOWN (Fehler in Datenbank)\n");
				} else {
					desc.append("**Typ:** ").append(type.getEmoji()).append(" ").append(type.getLabel()).append("\n");
				}

				if (seesAll) {
					desc.append("**DM an:** <@").append(playerEvent.getUserID()).append(">\n");
				}
				desc.append("**Letzter Wert:** ")
						.append(playerEvent.getLastValue() == null ? "noch nicht gemessen"
								: PlayerListeningEvent.formatNumber(playerEvent.getLastValue()))
						.append("\n");
				desc.append("**Zuletzt geprüft:** ").append(formatLastChecked(playerEvent)).append("\n\n");
			}
		}

		reply(event, desc.toString(), MessageUtil.EmbedType.INFO);
	}

	@SuppressWarnings("null")
	private void handleRemove(SlashCommandInteractionEvent event) {
		event.deferReply(true).queue();

		OptionMapping idOption = event.getOption("id");
		if (idOption == null) {
			reply(event, "Die ID ist erforderlich!", MessageUtil.EmbedType.ERROR);
			return;
		}

		long id = idOption.getAsLong();
		PlayerListeningEvent playerEvent = PlayerListeningEvent.getById(id);
		if (playerEvent == null) {
			reply(event, "Event mit dieser ID existiert nicht.", MessageUtil.EmbedType.ERROR);
			return;
		}

		User executor = new User(event.getUser().getId());
		if (!event.getUser().getId().equals(playerEvent.getUserID()) && !isColeaderOrHigher(executor)) {
			reply(event, "Du darfst dieses Event nicht löschen.", MessageUtil.EmbedType.ERROR);
			return;
		}

		if (!PlayerListeningEvent.delete(id)) {
			reply(event, "Das Event konnte nicht gelöscht werden.", MessageUtil.EmbedType.ERROR);
			return;
		}

		// No scheduler restart needed: the poller reads the watchers fresh on every
		// tick, so a deleted row simply stops being checked.
		reply(event, "Event mit ID " + id + " wurde erfolgreich gelöscht.", MessageUtil.EmbedType.SUCCESS);
	}

	// ============================================================
	// Autocomplete
	// ============================================================

	@SuppressWarnings("null")
	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("playerevent")) {
			return;
		}

		new Thread(() -> {
			if (!event.getFocusedOption().getName().equals("player")) {
				return;
			}

			String input = event.getFocusedOption().getValue();
			User executor = new User(event.getUser().getId());

			List<Command.Choice> choices;
			if (isColeaderOrHigher(executor)) {
				choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.ALL);
			} else {
				// Everyone else can only watch their own accounts, so only offer those.
				choices = new ArrayList<>();
				for (final Player player : executor.getAllLinkedAccounts()) {
					String display = displayName(player, player.getTag());
					if (display.toLowerCase().contains(input.toLowerCase())
							|| player.getTag().toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, player.getTag()));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}

			event.replyChoices(choices).queue(_ -> {
			}, _ -> {
			});
		}, "PlayereventAutocomplete-" + event.getUser().getId()).start();
	}

	// ============================================================
	// Helpers
	// ============================================================

	/**
	 * Everyone may watch the accounts linked to them; watching somebody else's
	 * account is a leadership action, matching the gate on {@link listeningevent}.
	 */
	private boolean mayManage(User executor, String playerTag) {
		if (isColeaderOrHigher(executor)) {
			return true;
		}
		return ownTags(executor).contains(playerTag);
	}

	private boolean isColeaderOrHigher(User executor) {
		try {
			return executor.isColeaderOrHigher();
		} catch (final Exception e) {
			System.err.println("Player event: could not read roles of " + executor.getUserID() + ": " + e.getMessage());
			return false;
		}
	}

	private List<String> ownTags(User executor) {
		List<String> tags = new ArrayList<>();
		for (final Player player : executor.getAllLinkedAccounts()) {
			tags.add(player.getTag());
		}
		return tags;
	}

	private static String normalizeTag(String raw) {
		String tag = raw.trim().toUpperCase().replace("O", "0");
		if (!tag.startsWith("#")) {
			tag = "#" + tag;
		}
		return tag;
	}

	private static String displayName(Player player, String fallbackTag) {
		try {
			String name = player.getNameDB();
			if (name != null && !name.isBlank()) {
				return name + " (" + player.getTag() + ")";
			}
		} catch (final Exception e) {
			// fall through to the bare tag
		}
		return fallbackTag;
	}

	private static String formatLastChecked(PlayerListeningEvent playerEvent) {
		if (playerEvent.getLastChecked() == null) {
			return "noch nie";
		}
		return "<t:" + (playerEvent.getLastChecked().getTime() / 1000) + ":R>";
	}

	private static void reply(SlashCommandInteractionEvent event, String description, MessageUtil.EmbedType type) {
		event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(TITLE, description, type)).queue();
	}
}
