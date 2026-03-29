package lostmanager.commands.roster;

import java.awt.Color;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.Roster;
import lostmanager.datawrapper.RosterParticipant;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

@SuppressWarnings("null")
public class RosterCommand extends ListenerAdapter {

    private String getOptString(SlashCommandInteractionEvent event, String name, String def) {
        net.dv8tion.jda.api.interactions.commands.OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : def;
    }

    private int getOptInt(SlashCommandInteractionEvent event, String name, int def) {
        net.dv8tion.jda.api.interactions.commands.OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsInt() : def;
    }

    private boolean getOptBool(SlashCommandInteractionEvent event, String name, boolean def) {
        net.dv8tion.jda.api.interactions.commands.OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsBoolean() : def;
    }

    @Override
    public void onSlashCommandInteraction(@javax.annotation.Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("roster")) return;

        User user = new User(event.getUser().getId()).refreshData();
        String subcmd = event.getSubcommandName();
        if (!user.isColeaderOrHigher()) {
            if (subcmd != null && !subcmd.equals("post")) { 
                event.reply("Dafür musst du Vize-Anführer sein!").setEphemeral(true).queue();
                return;
            }
        }

        if (subcmd == null) {
            event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
            return;
        }

        switch (subcmd) {
            case "create" -> handleCreate(event);
            case "clone" -> handleClone(event);
            case "delete" -> handleDelete(event);
            case "modify" -> handleModify(event);
            case "close" -> handleClose(event);
            case "post" -> handlePost(event);
            case "ping" -> handlePing(event);
            default -> event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        String clanStr = getOptString(event, "clan", "");
        String name = getOptString(event, "name", "");
        int minTh = getOptInt(event, "min_town_hall", 1);
        int deleteAfter = getOptInt(event, "delete_after", 60);
        boolean onlySignoff = getOptBool(event, "only_signoff", false);

        if (Roster.getRoster(name) != null) {
            event.reply("Ein Roster mit diesem Namen existiert bereits!").setEphemeral(true).queue();
            return;
        }

        Timestamp deleteAt = null;
        if (deleteAfter != -1) {
            deleteAt = new Timestamp(System.currentTimeMillis() + (deleteAfter * 86400000L)); // days to millis
        }

        Roster.createRoster(name, clanStr, minTh, deleteAt, onlySignoff);
        event.reply("Roster `" + name + "` erfolgreich erstellt!").queue();
    }

    private void handleClone(SlashCommandInteractionEvent event) {
        String baseName = getOptString(event, "base_roster", "");
        String newName = getOptString(event, "new_name", "");

        Roster base = Roster.getRoster(baseName);
        if (base == null) {
            event.reply("Der angegebene Basis-Roster wurde nicht gefunden.").setEphemeral(true).queue();
            return;
        }

        if (Roster.getRoster(newName) != null) {
            event.reply("Ein Roster mit dem Namen `" + newName + "` existiert bereits!").setEphemeral(true).queue();
            return;
        }

        Timestamp deleteAt = new Timestamp(System.currentTimeMillis() + (60 * 86400000L)); // default 60 days for clone
        Roster.createRoster(newName, base.getClan(), base.getMinTh(), deleteAt, base.isOnlySignoff());
        event.reply("Roster erfolgreich zu `" + newName + "` geklont!").queue();
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        String name = getOptString(event, "name", "");
        Roster r = Roster.getRoster(name);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }
        Roster.deleteRoster(name);
        event.reply("Roster `" + name + "` erfolgreich gelöscht!").queue();
    }

    private void handleModify(SlashCommandInteractionEvent event) {
        String name = getOptString(event, "name", "");
        int minTh = getOptInt(event, "min_town_hall", 1);

        Roster r = Roster.getRoster(name);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }
        Roster.setMinTh(name, minTh);
        event.reply("Roster `" + name + "` wurde aktualisiert (Min TH: " + minTh + ").").queue();
    }

    private void handleClose(SlashCommandInteractionEvent event) {
        String name = getOptString(event, "name", "");
        Roster r = Roster.getRoster(name);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }
        Roster.setClosed(name, true);
        event.reply("Roster `" + name + "` wurde geschlossen. Buttons auf bestehenden Posts funktionieren nun nicht mehr.").queue();
    }

    private void handlePost(SlashCommandInteractionEvent event) {
        String name = getOptString(event, "name", "");
        Roster r = Roster.getRoster(name);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(buildRosterEmbed(r).build()).setComponents(buildActionRows(r)).queue();
    }

    private void handlePing(SlashCommandInteractionEvent event) {
        String name = getOptString(event, "name", "");
        Roster roster = Roster.getRoster(name);
        if (roster == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }

        // Get clan users
        String clanTag = roster.getClan(); // Might need mapping from clan name to tag if clanStr is not a tag
        // Wait! Let's check how autocomplete is structured. The autocomplete returns Clan names and tags probably.
        try {
            // Validates if the clan exists, throws exception if not
			@SuppressWarnings("unused")
			Clan _clan = new Clan(clanTag);
            List<String> allClanMemberTags = new java.util.ArrayList<>();
            // Assuming we get members from DB or API:
            String sql = "SELECT player_tag FROM clan_members WHERE clan_tag = ?";
            for (String pTag : DBUtil.getArrayListFromSQL(sql, String.class, clanTag)) {
                allClanMemberTags.add(pTag);
            }

            List<RosterParticipant> participants = RosterParticipant.getParticipants(name);
            List<String> signedUpAccounts = participants.stream()
                .map(RosterParticipant::getAccountTag)
                .collect(Collectors.toList());

            // Get discord ids for those NOT signed up
            List<String> pingIds = new ArrayList<>();
            for (String pTag : allClanMemberTags) {
                if (!signedUpAccounts.contains(pTag)) {
                    Player p = new Player(pTag);
                    User pUser = p.getUser();
                    if (pUser != null && pUser.getUserID() != null) {
                        String v = pUser.getUserID();
                        if (!pingIds.contains(v)) {
                            pingIds.add(v);
                        }
                    }
                }
            }

            if (pingIds.isEmpty()) {
                event.reply("Alle Mitglieder in diesem Clan haben sich bereits eingetragen/abgemeldet!").setEphemeral(true).queue();
                return;
            }

            String pings = pingIds.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" "));
            
            // Sending it to the current channel directly to avoid pinging in command reply directly (or using a normal message message)
            event.reply("Erfolgreich ausgeführt.").setEphemeral(true).queue(s -> {
                event.getChannel().sendMessage("**Roster Erinnerung (" + name + ")**\nBitte tragt euch ein oder meldet euch ab!\n" + pings).queue();
            });

        } catch(Exception e) {
            event.reply("Fehler beim Abrufen des Clans: " + e.getMessage()).setEphemeral(true).queue();
        }
    }

    public static EmbedBuilder buildRosterEmbed(Roster r) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Roster: " + r.getName());
        eb.setColor(Color.BLUE);

        if (r.isClosed()) {
            eb.setDescription("**Dieser Roster ist geschlossen.**\n\n");
            eb.setColor(Color.RED);
        }

        List<RosterParticipant> participants = RosterParticipant.getParticipants(r.getName());
        List<RosterParticipant> signedUp = new ArrayList<>();
        List<RosterParticipant> signedOff = new ArrayList<>();

        for (RosterParticipant rp : participants) {
            if (rp.getStatus().equals("signup")) {
                signedUp.add(rp);
            } else {
                signedOff.add(rp);
            }
        }

        // Sort alphabetically by account name
        Comparator<RosterParticipant> comp = Comparator.comparing(p -> p.getAccountName().toLowerCase());
        signedUp.sort(comp);
        signedOff.sort(comp);

        StringBuilder upStr = new StringBuilder();
        for (RosterParticipant rp : signedUp) {
            String emojiStr = getThEmoji(rp.getThLevel());
            upStr.append(emojiStr).append(" ").append(rp.getAccountName()).append("\n");
        }
        if (upStr.length() == 0) upStr.append("Niemand.\n");

        StringBuilder offStr = new StringBuilder();
        for (RosterParticipant rp : signedOff) {
            String emojiStr = getThEmoji(rp.getThLevel());
            offStr.append(emojiStr).append(" ").append(rp.getAccountName()).append("\n");
        }
        if (offStr.length() == 0) offStr.append("Niemand.\n");

        eb.addField("Dabei (" + signedUp.size() + ")", upStr.toString(), true);
        eb.addField("Abgemeldet (" + signedOff.size() + ")", offStr.toString(), true);

        eb.setFooter("Clan: " + r.getClan() + " | Min TH: " + r.getMinTh());
        return eb;
    }

    private static String getThEmoji(int level) {
        try {
            String dataValue = "1000001";
            String levelPath = lostmanager.util.ImageMapCache.getLevelPath(dataValue, level);
            if (levelPath != null && !levelPath.isEmpty()) {
                String name = lostmanager.util.ImageMapCache.getName(dataValue);
                if (name == null) {
                    name = dataValue;
                }
                String emoji = lostmanager.util.EmojiManager.getOrCreateEmoji(levelPath, name + "_" + level);
                if (emoji != null) {
                    return emoji;
                }
            }
        } catch (Exception e) {}

        return "TH" + level;
    }

    public static List<ActionRow> buildActionRows(Roster r) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.secondary("roster_refresh_" + r.getName(), "Refresh"));
        
        if (!r.isClosed()) {
            if (!r.isOnlySignoff()) {
                buttons.add(Button.success("roster_signup_" + r.getName(), "Anmelden"));
            }
            buttons.add(Button.danger("roster_signoff_" + r.getName(), "Abmelden"));
        } else {
            buttons.add(Button.danger("roster_closed", "Geschlossen").asDisabled());
        }

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(buttons));
        return rows;
    }

    @Override
    public void onButtonInteraction(@javax.annotation.Nonnull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("roster_")) return;

        if (id.equals("roster_closed")) {
            event.deferEdit().queue();
            return;
        }

        String action = id.substring(7, id.indexOf("_", 7));
        String rosterName = id.substring(7 + action.length() + 1);

        Roster r = Roster.getRoster(rosterName);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht mehr.").setEphemeral(true).queue();
            return;
        }

        if (action.equals("refresh")) {
            event.editMessageEmbeds(buildRosterEmbed(r).build()).setComponents(buildActionRows(r)).queue();
            return;
        }

        if (r.isClosed()) {
            event.reply("Dieser Roster ist mittlerweile geschlossen.").setEphemeral(true).queue();
            return;
        }

        // Fetch accounts for user
        User u = new User(event.getUser().getId()).refreshData();
        List<Player> accounts = u.getAllLinkedAccounts();
        if (accounts == null || accounts.isEmpty()) {
            event.reply("Du hast keine Accounts verlinkt! Bitte nutze `/link`.").setEphemeral(true).queue();
            return;
        }

        List<SelectOption> options = new ArrayList<>();
        for (Player p : accounts) {
            String name = p.getNameAPI();
            String tag = p.getTag();
            int thLevel = p.getThLevelAPI();

            if (action.equals("signup") && thLevel < r.getMinTh()) {
                // Skip if below TH requirement for signing UP
                continue;
            }

            options.add(SelectOption.of(name + " (" + tag + ")", "roster_" + action + "cmd_" + rosterName + "_" + tag));
        }

        if (options.isEmpty()) {
            event.reply("Du hast keine passenden Accounts für diesen Roster (Min TH: " + r.getMinTh() + ").").setEphemeral(true).queue();
            return;
        }

        // Limit options to Discord's max 25
        if (options.size() > 25) {
            options = new ArrayList<>(options.subList(0, 25));
        }

        StringSelectMenu menu = StringSelectMenu.create("roster_select_" + action + "_" + rosterName)
                .setPlaceholder("Wähle einen Account aus")
                .addOptions(options)
                .build();

        event.reply("Wähle den Account aus, den du " + (action.equals("signup") ? "anmelden" : "abmelden") + " möchtest:")
                .setComponents(ActionRow.of(menu)).setEphemeral(true).queue();
    }

    @Override
    public void onStringSelectInteraction(@javax.annotation.Nonnull StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("roster_select_")) return;

        String val = event.getValues().get(0);
        // format: roster_signupcmd_NAME_#TAG
        if (!val.startsWith("roster_")) return;

        String parts[] = val.substring(7).split("_", 3);
        if (parts.length < 3) return;

        String actioncmd = parts[0];
        String rName = parts[1];
        String pTag = parts[2];

        String logicAction = actioncmd.replace("cmd", ""); // "signup" or "signoff"

        Roster r = Roster.getRoster(rName);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht mehr.").setEphemeral(true).queue();
            return;
        }

        if (r.isClosed()) {
            event.reply("Dieser Roster ist mittlerweile geschlossen.").setEphemeral(true).queue();
            return;
        }

        Player p = new Player(pTag);
        
        RosterParticipant.setParticipantStatus(rName, event.getUser().getId(), pTag, logicAction, p.getThLevelAPI());

        event.reply("Erfolgreich " + (logicAction.equals("signup") ? "angemeldet" : "abgemeldet") + " für " + p.getNameAPI() + "!")
            .setEphemeral(true).queue();

    }

    @Override
    public void onCommandAutoCompleteInteraction(@javax.annotation.Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("roster")) return;

        String opt = event.getFocusedOption().getName();
        String currentStr = event.getFocusedOption().getValue().toLowerCase();

        if (opt.equals("clan")) {
            List<Choice> choices = DBManager.getClansAutocompleteWithSideclans(currentStr);
            event.replyChoices(choices).queue();

        } else if (opt.equals("name") || opt.equals("base_roster")) {
            List<Choice> choices = Roster.getAllRosters().stream()
                .map(Roster::getName)
                .filter(n -> n.toLowerCase().contains(currentStr))
                .map(n -> new Choice(n, n))
                .limit(25)
                .collect(Collectors.toList());

            event.replyChoices(choices).queue();
        }
    }
}
