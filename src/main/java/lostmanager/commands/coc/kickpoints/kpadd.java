package lostmanager.commands.coc.kickpoints;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Kickpoint;
import lostmanager.datawrapper.KickpointReason;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import lostmanager.util.Tuple;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

public class kpadd extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("kpadd"))
			return;
		String title = "Kickpunkte";

		OptionMapping playeroption = event.getOption("player");
		OptionMapping reasonoption = event.getOption("reason");

		if (playeroption == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Der Paramenter Player sind verpflichtend!",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		String reason = null;
		if (reasonoption != null) {
			reason = reasonoption.getAsString();
		}
		String playertag = playeroption.getAsString();
		Player p = new Player(playertag);

		String clantag = p.getClanDB().getTag();

		User userexecuted = new User(event.getUser().getId());
		if (!(userexecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
				|| userexecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		if (p.getClanDB() == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title, "Dieser Spieler existiert nicht oder ist in keinem Clan.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}
		if (p.getClanDB().getDaysKickpointsExpireAfter() == null || p.getClanDB().getMaxKickpoints() == null) {
			event.replyEmbeds(MessageUtil.buildEmbed(title,
					"Es müssen zuerst die Clanconfigs eingestellt werden. Nutze /clanconfig.",
					MessageUtil.EmbedType.ERROR)).queue();
			return;
		}

		KickpointReason kpreason = new KickpointReason(reason, clantag);

		TextInput reasonti;
		TextInput kpamountti;
		if (reason != null) {
			reasonti = TextInput.create("reason", "Grund", TextInputStyle.SHORT).setPlaceholder("z.B. CW vergessen")
					.setValue(reason).setMinLength(1).build();
			if (kpreason.Exists()) {
				kpamountti = TextInput.create("amount", "Anzahl Kickpunkte", TextInputStyle.SHORT)
						.setPlaceholder("z.B. 1").setValue(kpreason.getAmount() + "").setMinLength(1).build();
			} else {
				kpamountti = TextInput.create("amount", "Anzahl Kickpunkte", TextInputStyle.SHORT)
						.setPlaceholder("z.B. 1").setMinLength(1).build();
			}
		} else {
			reasonti = TextInput.create("reason", "Grund", TextInputStyle.SHORT).setPlaceholder("z.B. CW vergessen")
					.setMinLength(1).build();
			kpamountti = TextInput.create("amount", "Anzahl Kickpunkte", TextInputStyle.SHORT).setPlaceholder("z.B. 1")
					.setMinLength(1).build();
		}
		TextInput dateti = TextInput.create("date", "Datum", TextInputStyle.SHORT).setPlaceholder("z.B. 31.01.2025")
				.setValue(LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).setMinLength(1).build();

		TextInput playertagti = TextInput.create("tag", "Spieler-Tag", TextInputStyle.SHORT)
				.setPlaceholder("z.B. #Y0RYLP0Q").setValue(playertag).setMinLength(1).build();

		Modal modal = Modal.create("kpadd", "Kickpunkt hinzufügen").addComponents(ActionRow.of(reasonti),
				ActionRow.of(kpamountti), ActionRow.of(dateti), ActionRow.of(playertagti)).build();

		event.replyModal(modal).queue();

	}

	@Override
	public void onModalInteraction(ModalInteractionEvent event) {
		if (event.getModalId().equals("kpadd")) {
			event.deferReply().queue();

			new Thread(() -> {
				String title = "Kickpunkte";
				String reason = event.getValue("reason").getAsString();
				String amountstr = event.getValue("amount").getAsString();
				int amount = -1;
				try {
					amount = Integer.valueOf(amountstr);
				} catch (Exception ex) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, "Die Anzahl muss eine Zahl sein.",
							MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
				String date = event.getValue("date").getAsString();
				String playertag = event.getValue("tag").getAsString();

				Player p = new Player(playertag);
				Clan c = p.getClanDB();
				if (c == null) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Dieser Spieler existiert nicht oder ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
				boolean validdate;
				try {
					LocalDate.parse(date, formatter);
					validdate = true;
				} catch (DateTimeParseException e) {
					validdate = false;
				}
				if (!validdate) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Ungültiges Format für die Datums-Eingabe. Nutze dd.MM.yyyy", MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				LocalDate localdate = LocalDate.parse(date, formatter);
				LocalDateTime dateTime = localdate.atStartOfDay();
				ZoneId zone = ZoneId.of("Europe/Berlin");
				ZonedDateTime zonedDateTime = dateTime.atZone(zone);
				Timestamp timestampcreated = Timestamp.from(zonedDateTime.toInstant());
				Timestamp timestampexpires = Timestamp.valueOf(dateTime.plusDays(c.getDaysKickpointsExpireAfter()));
				Timestamp timestampnow = Timestamp.from(Instant.now());
				String userid = event.getUser().getId();

				Tuple<PreparedStatement, Integer> result = DBUtil.executeUpdate(
						"INSERT INTO kickpoints (player_tag, date, amount, description, created_by_discord_id, created_at, expires_at, clan_tag, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
						playertag, timestampcreated, amount, reason, userid, timestampnow, timestampexpires, c.getTag(),
						timestampnow);

				if (result == null) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title,
									"Fehler beim Hinzufügen des Kickpunkts. Bitte versuche es erneut.",
									MessageUtil.EmbedType.ERROR))
							.queue();
					return;
				}

				PreparedStatement stmt = result.getFirst();
				int rowsAffected = result.getSecond();

				Long id = null;

				if (rowsAffected > 0) {
					try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
						if (generatedKeys.next()) {
							id = generatedKeys.getLong(1);
						}
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}

				String desc = "### Der Kickpunkt wurde hinzugefügt.\n";
				desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
				desc += "Clan: " + c.getInfoString() + "\n";
				desc += "Anzahl: " + amount + "\n";
				desc += "Grund: " + reason + "\n";
				desc += "ID: " + id + "\n";

				if (timestampexpires.before(Timestamp.from(Instant.now()))) {
					desc += "### Achtung: Der Kickpunkt ist bereits abgelaufen.\n";
				}

				long kptotal = 0;
				for (Kickpoint kp : p.getActiveKickpoints()) {
					kptotal += kp.getAmount();
				}
				if (kptotal >= c.getMaxKickpoints()) {
					desc += "### Achtung: Der Spieler hat die maximale Anzahl der Kickpunkte erreicht.\n";
				}

				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
						.queue();
			}, "KpaddModal-" + event.getUser().getId()).start();
		}
	}

	@Override
	public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
		if (!event.getName().equals("kpadd"))
			return;

		new Thread(() -> {
			String focused = event.getFocusedOption().getName();
			String input = event.getFocusedOption().getValue();

			if (focused.equals("player")) {
				List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);

				event.replyChoices(choices).queue(_ -> {
				}, _ -> {
				});
			}
			if (focused.equals("reason")) {
				if (event.getOption("player") != null) {
					String playertag = event.getOption("player").getAsString();
					Player p = new Player(playertag);
					Clan c = p.getClanDB();
					if (c == null) {
						return;
					}
					String clantag = c.getTag();
					List<Command.Choice> choices = DBManager.getKPReasonsAutocomplete(input, clantag);

					event.replyChoices(choices).queue(_ -> {
					}, _ -> {
					});
				}
			}
		}, "KpAddAutocomplete-" + event.getUser().getId()).start();
	}

}
