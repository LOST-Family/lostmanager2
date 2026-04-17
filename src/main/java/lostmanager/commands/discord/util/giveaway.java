package lostmanager.commands.discord.util;

import java.awt.Color;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lostmanager.Bot;
import lostmanager.datawrapper.Giveaway;
import lostmanager.datawrapper.GiveawayEntry;
import lostmanager.datawrapper.User;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

@SuppressWarnings("null")
public class giveaway extends ListenerAdapter {

    private static final String GIVEAWAY_ROLE_ID = "1489926297000870009";
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([mhd])$");

    // ─── Permission Check ────────────────────────────────────────────

    private boolean hasGiveawayPermission(SlashCommandInteractionEvent event) {
        User user = new User(event.getUser().getId()).refreshData();
        if (user.isAdmin()) return true;
        return event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(GIVEAWAY_ROLE_ID));
    }

    // ─── Auto Complete Handler ───────────────────────────────────────

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("giveaway") || !event.getSubcommandName().equals("reroll")) return;

        OptionMapping idMapping = event.getOption("giveaway_id");
        if (idMapping == null) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        long giveawayId;
        try {
            giveawayId = idMapping.getAsLong();
        } catch (NumberFormatException e) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        Giveaway giveaway = Giveaway.getById(giveawayId);
        if (giveaway == null || giveaway.getWinners() == null || giveaway.getWinners().isEmpty()) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        String input = event.getFocusedOption().getValue().toLowerCase();
        List<String> winnerIds = java.util.Arrays.asList(giveaway.getWinners().split(","));
        List<Command.Choice> choices = new ArrayList<>();

        for (String wId : winnerIds) {
            String idStr = wId.trim();
            if (idStr.isEmpty()) continue;
            
            // wir versuchen namen vom nutzer aufzulösen
            net.dv8tion.jda.api.entities.Member member = event.getGuild() != null ? event.getGuild().getMemberById(idStr) : null;
            net.dv8tion.jda.api.entities.User dUser = event.getJDA().getUserById(idStr);
            String name = idStr;
            if (member != null) {
                name = member.getEffectiveName();
            } else if (dUser != null) {
                name = dUser.getName();
            }

            if (name.toLowerCase().contains(input) || idStr.contains(input)) {
                choices.add(new Command.Choice(name, idStr));
            }
            if (choices.size() >= 25) break; 
        }

        event.replyChoices(choices).queue();
    }

    // ─── Duration Parsing ────────────────────────────────────────────

    /**
     * Parse a duration string like "5d", "2h", "30m" into milliseconds.
     * @return milliseconds, or -1 if invalid
     */
    private static long parseDuration(String input) {
        Matcher m = DURATION_PATTERN.matcher(input.trim().toLowerCase());
        if (!m.matches()) return -1;
        long value = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "m" -> value * 60 * 1000L;
            case "h" -> value * 60 * 60 * 1000L;
            case "d" -> value * 24 * 60 * 60 * 1000L;
            default -> -1;
        };
    }

    // ─── Slash Command Router ────────────────────────────────────────

    @Override
    public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("giveaway")) return;

        String subcmd = event.getSubcommandName();
        if (subcmd == null) {
            event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
            return;
        }

        switch (subcmd) {
            case "create" -> handleCreate(event);
            case "end" -> handleEnd(event);
            case "participants" -> handleParticipants(event);
            case "list" -> handleList(event);
            case "reroll" -> handleReroll(event);
            default -> event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
        }
    }

    // ─── Subcommand Handlers ─────────────────────────────────────────

    private void handleCreate(SlashCommandInteractionEvent event) {
        if (!hasGiveawayPermission(event)) {
            event.reply("❌ Keine Berechtigung! Nur Admins und berechtigte Rollen können Giveaways erstellen.")
                    .setEphemeral(true).queue();
            return;
        }

        String durationStr = event.getOption("duration").getAsString();
        int winners = event.getOption("winners").getAsInt();
        String prize = event.getOption("prize").getAsString();

        if (winners < 1) {
            event.reply("❌ Es muss mindestens 1 Gewinner geben.").setEphemeral(true).queue();
            return;
        }

        long durationMs = parseDuration(durationStr);
        if (durationMs <= 0) {
            event.reply("❌ Ungültiges Zeitformat! Nutze z.B. `30m`, `2h` oder `5d`.").setEphemeral(true).queue();
            return;
        }

        Timestamp endTime = Timestamp.from(Instant.now().plusMillis(durationMs));
        long endEpochSeconds = endTime.toInstant().getEpochSecond();

        Giveaway giveaway = Giveaway.create(prize, winners, event.getUser().getId(),
                event.getChannel().getId(), endTime);

        if (giveaway == null) {
            System.err.println("Giveaway creation failed: Giveaway.create returned null");
            event.reply("❌ Fehler beim Erstellen des Giveaways.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = buildGiveawayEmbed(giveaway, endEpochSeconds, false, null);

        event.replyEmbeds(eb.build())
                .addActionRow(
                        Button.success("giveaway_join_" + giveaway.getId(), "Teilnehmen")
                                .withEmoji(Emoji.fromUnicode("🎉"))
                )
                .queue(hook -> {
                    hook.retrieveOriginal().queue(msg -> {
                        giveaway.setMessageId(msg.getId());
                        scheduleGiveaway(giveaway, event.getJDA());
                    });
                });
    }

    private void handleEnd(SlashCommandInteractionEvent event) {
        if (!hasGiveawayPermission(event)) {
            event.reply("❌ Keine Berechtigung!").setEphemeral(true).queue();
            return;
        }

        long giveawayId = event.getOption("giveaway_id").getAsLong();
        Giveaway giveaway = Giveaway.getById(giveawayId);

        if (giveaway == null) {
            event.reply("❌ Giveaway mit ID `" + giveawayId + "` nicht gefunden.").setEphemeral(true).queue();
            return;
        }

        if (giveaway.isEnded()) {
            event.reply("❌ Dieses Giveaway ist bereits beendet.").setEphemeral(true).queue();
            return;
        }

        event.reply("Möchtest du die Gewinner jetzt auslosen?")
                .setEphemeral(true)
                .addActionRow(
                        Button.success("giveaway_endroll_" + giveawayId, "Jetzt auslosen"),
                        Button.danger("giveaway_endclose_" + giveawayId, "Ohne Gewinner schließen")
                )
                .queue();
    }

    private void handleReroll(SlashCommandInteractionEvent event) {
        if (!hasGiveawayPermission(event)) {
            event.reply("❌ Keine Berechtigung!").setEphemeral(true).queue();
            return;
        }

        long giveawayId = event.getOption("giveaway_id").getAsLong();
        Giveaway giveaway = Giveaway.getById(giveawayId);

        if (giveaway == null) {
            event.reply("❌ Giveaway mit ID `" + giveawayId + "` nicht gefunden.").setEphemeral(true).queue();
            return;
        }

        List<String> entries = new ArrayList<>(giveaway.getEntryDiscordIds());

        if (entries.isEmpty()) {
            event.reply("❌ Es gibt keine Teilnehmer zum Auslosen.").setEphemeral(true).queue();
            return;
        }

        OptionMapping whoOption = event.getOption("who");
        String whoId = whoOption != null ? whoOption.getAsString() : null;

        if (whoId != null) {
            if (giveaway.getWinners() == null || !java.util.Arrays.asList(giveaway.getWinners().split(",")).contains(whoId)) {
                event.reply("❌ Dieser User ist derzeit kein Gewinner.").setEphemeral(true).queue();
                return;
            }

            List<String> currentWinners = new ArrayList<>(java.util.Arrays.asList(giveaway.getWinners().split(",")));
            currentWinners.remove(whoId);

            List<String> candidates = new ArrayList<>(entries);
            candidates.removeAll(currentWinners);
            candidates.remove(whoId); 

            if (candidates.isEmpty()) {
                event.reply("❌ Es gibt keine weiteren Teilnehmer, die nicht schon gewonnen haben.").setEphemeral(true).queue();
                return;
            }

            Collections.shuffle(candidates);
            String newWinnerId = candidates.get(0);
            currentWinners.add(newWinnerId);

            String winnersCSV = String.join(",", currentWinners);
            giveaway.end(winnersCSV);

            editGiveawayMessage(giveaway, event.getJDA(), currentWinners);
            pingWinners(giveaway, event.getJDA(), Collections.singletonList(newWinnerId));

            event.reply("✅ <@" + whoId + "> wurde durch <@" + newWinnerId + "> ersetzt.").setEphemeral(true).queue();
            return;
        }

        Collections.shuffle(entries);
        int winnersToPick = Math.min(giveaway.getWinnerCount(), entries.size());
        List<String> winnerIds = entries.subList(0, winnersToPick);
        String winnersCSV = String.join(",", winnerIds);

        giveaway.end(winnersCSV);
        editGiveawayMessage(giveaway, event.getJDA(), winnerIds);
        pingWinners(giveaway, event.getJDA(), winnerIds);

        event.reply("✅ Giveaway erfolgreich neu ausgelost.").setEphemeral(true).queue();
    }

    private void handleParticipants(SlashCommandInteractionEvent event) {
        long giveawayId = event.getOption("giveaway_id").getAsLong();
        Giveaway giveaway = Giveaway.getById(giveawayId);

        if (giveaway == null) {
            event.reply("❌ Giveaway mit ID `" + giveawayId + "` nicht gefunden.").setEphemeral(true).queue();
            return;
        }

        List<String> entries = giveaway.getEntryDiscordIds();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Teilnehmer — " + giveaway.getPrize() + " (ID: " + giveaway.getId() + ")");
        eb.setColor(giveaway.isEnded() ? Color.RED : new Color(0x2ecc71));

        if (entries.isEmpty()) {
            eb.setDescription("Keine Teilnehmer.");
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < entries.size(); i++) {
                String mention = "<@" + entries.get(i) + ">";
                // Discord embed field limit is 1024 chars
                if (sb.length() + mention.length() + 2 > 1000) {
                    eb.addField("Teilnehmer", sb.toString(), false);
                    sb = new StringBuilder();
                }
                sb.append(mention);
                if (i < entries.size() - 1) sb.append(", ");
            }
            if (sb.length() > 0) {
                eb.addField("Teilnehmer (" + entries.size() + ")", sb.toString(), false);
            }
        }

        event.replyEmbeds(eb.build()).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        var typeOption = event.getOption("type");
        Boolean activeOnly = null;
        if (typeOption != null) {
            String typeStr = typeOption.getAsString();
            if (typeStr.equals("Active")) activeOnly = true;
            else if (typeStr.equals("Inactive")) activeOnly = false;
        }

        List<Giveaway> giveaways = Giveaway.getAll(activeOnly);

        if (giveaways.isEmpty()) {
            event.reply("Keine Giveaways gefunden.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder();
        String title = "Giveaways";
        if (activeOnly != null) {
            title += activeOnly ? " (Aktiv)" : " (Beendet)";
        }
        eb.setTitle(title);
        eb.setColor(new Color(0x3498db));

        for (Giveaway g : giveaways) {
            long endEpoch = g.getEndTime().toInstant().getEpochSecond();
            String status = g.isEnded() ? "🔴 Beendet" : "🟢 Aktiv";
            String value = status
                    + "\n**Preis:** " + g.getPrize()
                    + "\n**Host:** <@" + g.getHostDiscordId() + ">"
                    + "\n**Endet:** <t:" + endEpoch + ":R>"
                    + "\n**Teilnehmer:** " + g.getEntryCount()
                    + "\n**Gewinner:** " + g.getWinnerCount();
            if (g.getWinners() != null && !g.getWinners().isEmpty()) {
                String winnerMentions = java.util.Arrays.stream(g.getWinners().split(","))
                        .map(id -> "<@" + id.trim() + ">")
                        .collect(Collectors.joining(", "));
                value += "\n**Gewonnen:** " + winnerMentions;
            }
            eb.addField("ID: " + g.getId(), value, false);
        }

        event.replyEmbeds(eb.build()).queue();
    }

    // ─── Button Interaction Handler ──────────────────────────────────

    @Override
    public void onButtonInteraction(@javax.annotation.Nonnull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("giveaway_")) return;

        if (id.startsWith("giveaway_join_")) {
            handleJoinButton(event);
        } else if (id.startsWith("giveaway_endroll_")) {
            handleEndRollButton(event);
        } else if (id.startsWith("giveaway_endclose_")) {
            handleEndCloseButton(event);
        }
    }

    private void handleJoinButton(ButtonInteractionEvent event) {
        String idStr = event.getComponentId().substring("giveaway_join_".length());
        long giveawayId;
        try {
            giveawayId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            event.reply("❌ Ungültige Giveaway-ID.").setEphemeral(true).queue();
            return;
        }

        Giveaway giveaway = Giveaway.getById(giveawayId);
        if (giveaway == null) {
            event.reply("❌ Dieses Giveaway existiert nicht mehr.").setEphemeral(true).queue();
            return;
        }

        if (giveaway.isEnded()) {
            event.reply("❌ Dieses Giveaway ist bereits beendet.").setEphemeral(true).queue();
            return;
        }

        String discordId = event.getUser().getId();

        if (GiveawayEntry.hasEntry(giveawayId, discordId)) {
            event.reply("Du nimmst bereits an diesem Giveaway teil!").setEphemeral(true).queue();
            return;
        }

        boolean added = GiveawayEntry.addEntry(giveawayId, discordId);
        if (added) {
            event.reply("Du nimmst nun am Giveaway **" + giveaway.getPrize() + "** teil! 🎉")
                    .setEphemeral(true).queue();
            editGiveawayMessage(giveaway, event.getJDA(), null);
        } else {
            event.reply("❌ Fehler beim Beitreten. Bitte versuche es erneut.").setEphemeral(true).queue();
        }
    }

    private void handleEndRollButton(ButtonInteractionEvent event) {
        String idStr = event.getComponentId().substring("giveaway_endroll_".length());
        long giveawayId;
        try {
            giveawayId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            event.reply("❌ Ungültige Giveaway-ID.").setEphemeral(true).queue();
            return;
        }

        // Permission check: only admins or giveaway role
        User user = new User(event.getUser().getId()).refreshData();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(GIVEAWAY_ROLE_ID));
        if (!user.isAdmin() && !hasRole) {
            event.reply("❌ Keine Berechtigung!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        rollAndEndGiveaway(giveawayId, event.getJDA());

        event.getHook().editOriginal("✅ Giveaway wurde ausgelost und beendet.").setComponents().setEmbeds().queue();
    }

    private void handleEndCloseButton(ButtonInteractionEvent event) {
        String idStr = event.getComponentId().substring("giveaway_endclose_".length());
        long giveawayId;
        try {
            giveawayId = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            event.reply("❌ Ungültige Giveaway-ID.").setEphemeral(true).queue();
            return;
        }

        // Permission check
        User user = new User(event.getUser().getId()).refreshData();
        boolean hasRole = event.getMember() != null && event.getMember().getRoles().stream()
                .anyMatch(r -> r.getId().equals(GIVEAWAY_ROLE_ID));
        if (!user.isAdmin() && !hasRole) {
            event.reply("❌ Keine Berechtigung!").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();

        Giveaway giveaway = Giveaway.getById(giveawayId);
        if (giveaway == null || giveaway.isEnded()) {
            event.getHook().editOriginal("❌ Giveaway nicht gefunden oder bereits beendet.").setComponents().setEmbeds().queue();
            return;
        }

        giveaway.end(null);

        // Edit the original giveaway embed
        editGiveawayMessage(giveaway, event.getJDA(), null);

        event.getHook().editOriginal("✅ Giveaway wurde ohne Gewinner geschlossen.").setComponents().setEmbeds().queue();
    }

    // ─── Shared Roll & End Logic ─────────────────────────────────────

    /**
     * Roll winners for a giveaway and end it. Edits the embed and pings winners.
     * This is called both from the manual "end" button and from the periodic check.
     */
    public static void rollAndEndGiveaway(long giveawayId, JDA jda) {
        Giveaway giveaway = Giveaway.getById(giveawayId);
        if (giveaway == null || giveaway.isEnded()) return;

        List<String> entries = new ArrayList<>(giveaway.getEntryDiscordIds());

        if (entries.isEmpty()) {
            // No participants — close without winners
            giveaway.end(null);
            editGiveawayMessage(giveaway, jda, null);
            return;
        }

        // Shuffle and pick winners
        Collections.shuffle(entries);
        int winnersToPick = Math.min(giveaway.getWinnerCount(), entries.size());
        List<String> winnerIds = entries.subList(0, winnersToPick);
        String winnersCSV = String.join(",", winnerIds);

        giveaway.end(winnersCSV);

        // Edit the giveaway embed
        editGiveawayMessage(giveaway, jda, winnerIds);

        // Ping winners in the channel
        pingWinners(giveaway, jda, winnerIds);
    }

    // ─── Embed Builders ──────────────────────────────────────────────

    private static EmbedBuilder buildGiveawayEmbed(Giveaway giveaway, long endEpochSeconds,
                                                     boolean ended, List<String> winnerIds) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(giveaway.getPrize());
        eb.setDescription("Klick den Knopf, um dem Giveaway beizutreten!");

        eb.addField("Endet", "<t:" + endEpochSeconds + ":R> (<t:" + endEpochSeconds + ":f>)", false);
        eb.addField("Host", "<@" + giveaway.getHostDiscordId() + ">", true);
        eb.addField("Gewinner", String.valueOf(giveaway.getWinnerCount()), true);
        eb.addField("Teilnehmer", String.valueOf(giveaway.getEntryCount()), true);
        eb.setFooter("Giveaway-ID: " + giveaway.getId());

        if (ended) {
            eb.setColor(Color.RED);
            if (winnerIds != null && !winnerIds.isEmpty()) {
                String winnerMentions = winnerIds.stream()
                        .map(wid -> "<@" + wid + ">")
                        .collect(Collectors.joining(", "));
                eb.addField("Geschlossen, Gewinner:", winnerMentions, false);
            } else {
                eb.addField("Status", "Geschlossen, keine Gewinner.", false);
            }
            eb.setDescription("Dieses Giveaway ist beendet.");
        } else {
            eb.setColor(new Color(0x2ecc71));
        }

        return eb;
    }

    private static void editGiveawayMessage(Giveaway giveaway, JDA jda, List<String> winnerIds) {
        if (giveaway.getChannelId() == null || giveaway.getMessageId() == null) return;

        JDA finalJda = jda != null ? jda : lostmanager.Bot.getJda();
        if (finalJda == null) {
            System.err.println("Error editing giveaway message: JDA is null.");
            return;
        }

        try {
            MessageChannel channel = finalJda.getChannelById(MessageChannel.class, giveaway.getChannelId());
            if (channel == null) return;

            long endEpoch = giveaway.getEndTime().toInstant().getEpochSecond();
            EmbedBuilder eb = buildGiveawayEmbed(giveaway, endEpoch, giveaway.isEnded(), winnerIds);

            channel.retrieveMessageById(giveaway.getMessageId()).queue(msg -> {
                Button joinButton = Button.success("giveaway_join_" + giveaway.getId(), "Teilnehmen")
                        .withEmoji(Emoji.fromUnicode("🎉"));
                if (giveaway.isEnded()) {
                    joinButton = joinButton.asDisabled();
                }

                msg.editMessageEmbeds(eb.build())
                        .setActionRow(joinButton)
                        .queue();
            }, err -> System.err.println("Could not edit giveaway message " + giveaway.getMessageId() + ": " + err.getMessage()));
        } catch (Exception e) {
            System.err.println("Error editing giveaway message: " + e.getMessage());
        }
    }

    private static void pingWinners(Giveaway giveaway, JDA jda, List<String> winnerIds) {
        if (giveaway.getChannelId() == null || winnerIds == null || winnerIds.isEmpty()) return;

        JDA finalJda = jda != null ? jda : lostmanager.Bot.getJda();
        if (finalJda == null) {
            System.err.println("Error pinging giveaway winners: JDA is null.");
            return;
        }

        try {
            MessageChannel channel = finalJda.getChannelById(MessageChannel.class, giveaway.getChannelId());
            if (channel == null) return;

            String mentions = winnerIds.stream()
                    .map(wid -> "<@" + wid + ">")
                    .collect(Collectors.joining(", "));
            if(winnerIds.size() == 1) {
                channel.sendMessage("🎉 Herzlichen Glückwunsch " + mentions
                        + "! Du bist der Gewinner von **" + giveaway.getPrize() + "**").queue();
            } else {
                channel.sendMessage("🎉 Herzlichen Glückwunsch " + mentions
                        + "! Ihr seid die Gewinner von **" + giveaway.getPrize() + "**").queue();
            }
        } catch (Exception e) {
            System.err.println("Error pinging giveaway winners: " + e.getMessage());
        }
    }

    // ─── Precise Scheduling ──────────────────────────────────────────

    /**
     * Schedules a single giveaway to end exactly at its end time.
     */
    public static void scheduleGiveaway(Giveaway giveaway, JDA jda) {
        long delayMs = giveaway.getEndTime().getTime() - System.currentTimeMillis();
        
        if (delayMs <= 0) {
            Bot.schedulertasks.schedule(() -> {
                try {
                    rollAndEndGiveaway(giveaway.getId(), jda != null ? jda : Bot.getJda());
                } catch (Exception e) {
                    System.err.println("Error auto-ending giveaway " + giveaway.getId() + ": " + e.getMessage());
                }
            }, 10, TimeUnit.SECONDS);
        } else {
            System.out.println("Scheduling giveaway " + giveaway.getId() + " to end in " + (delayMs / 1000) + " seconds.");
            Bot.schedulertasks.schedule(() -> {
                try {
                    rollAndEndGiveaway(giveaway.getId(), jda != null ? jda : Bot.getJda());
                } catch (Exception e) {
                    System.err.println("Error auto-ending giveaway " + giveaway.getId() + ": " + e.getMessage());
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Schedules all active giveaways. Called from Bot.java on startup.
     */
    public static void scheduleAllActiveGiveaways(JDA jda) {
        try {
            List<Giveaway> active = Giveaway.getAll(true);
            for (Giveaway g : active) {
                scheduleGiveaway(g, jda);
            }
        } catch (Exception e) {
            System.err.println("Error scheduling active giveaways: " + e.getMessage());
        }
    }
}
