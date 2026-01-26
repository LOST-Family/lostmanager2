package lostmanager.commands.coc.memberlist;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class signoff extends ListenerAdapter {

    @SuppressWarnings("null")
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("signoff"))
            return;
        event.deferReply().queue();

        new Thread(() -> {
            String title = "Abmeldung";

            OptionMapping playerOption = event.getOption("player");
            OptionMapping actionOption = event.getOption("action");

            if (playerOption == null || actionOption == null) {
                event.getHook().editOriginalEmbeds(
                        MessageUtil.buildEmbed(title, "Die Parameter Player und Action sind erforderlich!",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            String playertag = playerOption.getAsString();
            String action = actionOption.getAsString();
            Player p = new Player(playertag);

            if (p.getClanDB() == null) {
                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                        "Dieser Spieler existiert nicht oder ist in keinem Clan.", MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            Clan c = p.getClanDB();
            String clantag = c.getTag();

            User userExecuted = new User(event.getUser().getId());
            if (!(userExecuted.getClanRoles().get(clantag) == Player.RoleType.ADMIN
                    || userExecuted.getClanRoles().get(clantag) == Player.RoleType.LEADER
                    || userExecuted.getClanRoles().get(clantag) == Player.RoleType.COLEADER)) {
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Du musst mindestens Vize-Anführer des Clans sein, um diesen Befehl ausführen zu können.",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            MemberSignoff signoff = new MemberSignoff(playertag);

            if ("create".equals(action)) {
                if (signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist bereits abgemeldet. Nutze die 'extend' oder 'end' Action, um die Abmeldung zu ändern.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                OptionMapping daysOption = event.getOption("days");
                OptionMapping reasonOption = event.getOption("reason");

                Timestamp endDate = null;
                String durationText = "unbegrenzt";
                
                if (daysOption != null) {
                    int days = daysOption.getAsInt();
                    if (days <= 0) {
                        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Die Anzahl der Tage muss größer als 0 sein.", MessageUtil.EmbedType.ERROR))
                                .queue();
                        return;
                    }
                    LocalDateTime endDateTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusDays(days);
                    endDate = Timestamp.valueOf(endDateTime);
                    durationText = days + " Tag" + (days == 1 ? "" : "e");
                }

                String reason = reasonOption != null ? reasonOption.getAsString() : null;

                boolean success = MemberSignoff.create(playertag, endDate, reason, event.getUser().getId());

                if (success) {
                    String desc = "### Abmeldung erfolgreich erstellt.\n";
                    desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                    desc += "Clan: " + c.getInfoString() + "\n";
                    desc += "Dauer: " + durationText + "\n";
                    if (reason != null) {
                        desc += "Grund: " + reason + "\n";
                    }
                    desc += "\n**Während der Abmeldung:**\n";
                    desc += "- Keine automatischen Kickpunkte\n";
                    desc += "- Keine Raid-Pings, CW-Reminder-Pings und Checkreacts-Pings\n";
                    desc += "- Manuelle Kickpunkte weiterhin möglich (mit Warnung)\n";

                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                            .queue();
                } else {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Fehler beim Erstellen der Abmeldung. Bitte versuche es erneut.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                }

            } else if ("end".equals(action)) {
                if (!signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist aktuell nicht abgemeldet.", MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                boolean success = MemberSignoff.remove(playertag);

                if (success) {
                    String desc = "### Abmeldung erfolgreich beendet.\n";
                    desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                    desc += "Clan: " + c.getInfoString() + "\n";

                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                            .queue();
                } else {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Fehler beim Beenden der Abmeldung. Bitte versuche es erneut.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                }

            } else if ("extend".equals(action)) {
                if (!signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist aktuell nicht abgemeldet. Nutze die 'create' Action.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                OptionMapping daysOption = event.getOption("days");
                if (daysOption == null) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Der Parameter 'days' ist erforderlich für die 'extend' Action.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                int days = daysOption.getAsInt();
                if (days <= 0) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Die Anzahl der Tage muss größer als 0 sein.", MessageUtil.EmbedType.ERROR))
                            .queue();
                    return;
                }

                Timestamp newEndDate;
                if (signoff.isUnlimited()) {
                    // If unlimited, extend from now
                    LocalDateTime endDateTime = LocalDateTime.now(ZoneId.of("Europe/Berlin")).plusDays(days);
                    newEndDate = Timestamp.valueOf(endDateTime);
                } else {
                    // If has end date, extend from that date
                    LocalDateTime currentEnd = signoff.getEndDate().toLocalDateTime();
                    LocalDateTime newEnd = currentEnd.plusDays(days);
                    newEndDate = Timestamp.valueOf(newEnd);
                }

                boolean success = signoff.update(newEndDate);

                if (success) {
                    String desc = "### Abmeldung erfolgreich verlängert.\n";
                    desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                    desc += "Clan: " + c.getInfoString() + "\n";
                    desc += "Verlängert um: " + days + " Tag" + (days == 1 ? "" : "e") + "\n";
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                    desc += "Neues Enddatum: " + newEndDate.toLocalDateTime().format(formatter) + "\n";

                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.SUCCESS))
                            .queue();
                } else {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Fehler beim Verlängern der Abmeldung. Bitte versuche es erneut.",
                            MessageUtil.EmbedType.ERROR))
                            .queue();
                }

            } else if ("info".equals(action)) {
                if (!signoff.isActive()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                            "Dieser Spieler ist aktuell nicht abgemeldet.", MessageUtil.EmbedType.INFO))
                            .queue();
                    return;
                }

                String desc = "### Abmeldungs-Information\n";
                desc += "Spieler: " + MessageUtil.unformat(p.getInfoStringDB()) + "\n";
                desc += "Clan: " + c.getInfoString() + "\n";
                
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                desc += "Startdatum: " + signoff.getStartDate().toLocalDateTime().format(formatter) + "\n";
                
                if (signoff.isUnlimited()) {
                    desc += "Dauer: Unbegrenzt\n";
                } else {
                    desc += "Enddatum: " + signoff.getEndDate().toLocalDateTime().format(formatter) + "\n";
                }
                
                if (signoff.getReason() != null) {
                    desc += "Grund: " + signoff.getReason() + "\n";
                }

                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, desc, MessageUtil.EmbedType.INFO))
                        .queue();
            }

        }, "SignoffCommand-" + event.getUser().getId()).start();
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("signoff"))
            return;

        new Thread(() -> {
            String focused = event.getFocusedOption().getName();
            String input = event.getFocusedOption().getValue();

            if (focused.equals("player")) {
                List<Command.Choice> choices = DBManager.getPlayerlistAutocomplete(input, DBManager.InClanType.INCLAN);
                event.replyChoices(choices).queue(_ -> {}, _ -> {});
            } else if (focused.equals("action")) {
                List<Command.Choice> choices = List.of(
                    new Command.Choice("Erstellen", "create"),
                    new Command.Choice("Beenden", "end"),
                    new Command.Choice("Verlängern", "extend"),
                    new Command.Choice("Info", "info")
                );
                event.replyChoices(choices).queue(_ -> {}, _ -> {});
            }
        }, "SignoffAutocomplete-" + event.getUser().getId()).start();
    }
}
