package lostmanager.commands.roster;

import java.awt.Color;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.MemberSignoff;
import lostmanager.datawrapper.Roster;
import lostmanager.datawrapper.RosterParticipant;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.emoji.Emoji;
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
            case "force_signup" -> handleForceSign(event, "signup");
            case "force_signoff" -> handleForceSign(event, "signoff");
            case "clan_add" -> handleClan(event, true);
            case "clan_remove" -> handleClan(event, false);
            default -> event.reply("Unbekannter Subcommand.").setEphemeral(true).queue();
        }
    }

    /**
     * Fügt einem bestehenden Roster einen Clan hinzu oder nimmt ihn weg.
     *
     * Beim Anlegen lassen sich drei Clans angeben; darüber hinaus - und für
     * Änderungen an einem laufenden Roster - führt der Weg hierher. Bisher ließ
     * sich die Clanzuordnung nach dem Anlegen überhaupt nicht mehr ändern.
     */
    private void handleClan(SlashCommandInteractionEvent event, boolean add) {
        String name = getOptString(event, "name", "");
        String clanTag = getOptString(event, "clan", "");

        if (Roster.getRoster(name) == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }

        List<String> vorher = Roster.getClans(name);
        if (add) {
            Roster.addClan(name, clanTag);
        } else {
            if (vorher.size() <= 1) {
                event.reply("Der letzte Clan lässt sich nicht entfernen - sonst erreicht der Roster niemanden mehr.")
                        .setEphemeral(true).queue();
                return;
            }
            Roster.removeClan(name, clanTag);
        }

        List<String> nachher = Roster.getClans(name);
        event.reply("Roster `" + name + "` läuft jetzt über: " + String.join(", ", nachher)).queue();
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

        // Der erste Clan steht in rosters.clan und wird ebenfalls hier geführt,
        // damit die Auflösung nur eine Quelle kennt.
        Roster.addClan(name, clanStr);
        List<String> weitere = new ArrayList<>();
        for (String opt : new String[] { "clan_2", "clan_3" }) {
            String tag = getOptString(event, opt, "");
            if (!tag.isBlank() && !tag.equals(clanStr)) {
                Roster.addClan(name, tag);
                weitere.add(tag);
            }
        }

        String hinweis = weitere.isEmpty() ? ""
                : " Anmeldung läuft über " + (weitere.size() + 1) + " Clans.";
        event.reply("Roster `" + name + "` erfolgreich erstellt!" + hinweis).queue();
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
        // Ohne das würde aus einem Zwei-Clan-Roster still ein Ein-Clan-Roster,
        // und beim Ping fehlte die Hälfte der Leute.
        for (String tag : Roster.getClans(baseName)) {
            Roster.addClan(newName, tag);
        }
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

        // Ein Roster kann über mehrere Clans laufen - der F2P-Verbund meldet sich
        // etwa über LOST F2P und LOST F2P 2 gemeinsam an. Ohne Eintrag in
        // roster_clans bleibt es beim einen Clan aus rosters.clan.
        List<String> clanTags = Roster.getClans(name);
        if (clanTags.isEmpty()) {
            event.reply("Diesem Roster ist kein Clan zugeordnet.").setEphemeral(true).queue();
            return;
        }
        try {
            // Versteckte Vize stehen nur der Rechte wegen in clan_members und spielen nicht mit.
            List<String> allClanMemberTags = new java.util.ArrayList<>();
            String sql = "SELECT player_tag FROM clan_members WHERE clan_tag = ? AND clan_role <> 'hiddencoleader'";
            for (String clanTag : clanTags) {
                for (String pTag : DBUtil.getArrayListFromSQL(sql, String.class, clanTag)) {
                    if (!allClanMemberTags.contains(pTag)) {
                        allClanMemberTags.add(pTag);
                    }
                }
            }

            List<RosterParticipant> participants = RosterParticipant.getParticipants(name);
            java.util.Set<String> eingetragen = participants.stream()
                .map(RosterParticipant::getAccountTag)
                .collect(Collectors.toSet());

            // Gepingt wird je Discord-Nutzer, nicht je Account: wer mit einem Account
            // eingetragen (an- oder abgemeldet) ist, bekommt keinen Ping für seinen Zweitaccount
            // im selben Clanverbund. Wer per /signoff abgemeldet ist (ohne „Pings erhalten“),
            // bekommt auch keinen. (~A, 22.10.2026)
            java.util.Map<String, Boolean> ohnePing = new java.util.LinkedHashMap<>();
            for (String pTag : allClanMemberTags) {
                User pUser = new Player(pTag).getUser();
                if (pUser == null || pUser.getUserID() == null) continue;
                String v = pUser.getUserID();
                boolean ausnehmen = eingetragen.contains(pTag);
                if (!ausnehmen) {
                    MemberSignoff signoff = new MemberSignoff(pTag);
                    ausnehmen = signoff.isActive() && !signoff.isReceivePings();
                }
                ohnePing.merge(v, ausnehmen, Boolean::logicalOr);
            }
            List<String> pingIds = new ArrayList<>();
            ohnePing.forEach((id, aus) -> { if (!aus) pingIds.add(id); });

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

    private void handleForceSign(SlashCommandInteractionEvent event, String action) {
        String rosterName = getOptString(event, "name", "");
        String pTag = getOptString(event, "player", "");

        Roster r = Roster.getRoster(rosterName);
        if (r == null) {
            event.reply("Dieser Roster existiert nicht.").setEphemeral(true).queue();
            return;
        }

        if (r.isClosed()) {
            event.reply("Dieser Roster ist mittlerweile geschlossen.").setEphemeral(true).queue();
            return;
        }

        Player p = new Player(pTag);
        if (p.getNameAPI() == null && p.getNameDB() == null) {
            event.reply("Der ausgewählte Spieler wurde nicht gefunden.").setEphemeral(true).queue();
            return;
        }
        
        if (action.equals("signup") && p.getThLevelAPI() < r.getMinTh()) {
            event.reply("Der Spieler erfüllt nicht die minimale Rathaus-Anforderung (" + r.getMinTh() + ")!").setEphemeral(true).queue();
            return;
        }

        String linkedUserId = p.getUser() != null ? p.getUser().getUserID() : event.getUser().getId();

        boolean set = RosterParticipant.setParticipantStatus(rosterName, linkedUserId, pTag, action, p.getThLevelAPI());

        String pName = p.getNameAPI() != null ? p.getNameAPI() : p.getNameDB();
        if (set) {
            event.reply("Erfolgreich " + (action.equals("signup") ? "angemeldet" : "abgemeldet") + " für " + pName + "!").queue();
        } else {
            event.reply("Erfolgreich aus dem Roster entfernt für " + pName + "!").queue();
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

        // Ein Embed-Feld fasst höchstens 1024 Zeichen, eine Zeile mit TH-Emoji rund 40. Ab gut
        // 20 Anmeldungen warf addField eine IllegalArgumentException, und jede Aktualisierung der
        // geposteten Nachricht scheiterte still (CWL 2026-11, Oktober 2026: 54 angemeldet, ~18
        // sichtbar). Deshalb auf mehrere Felder verteilen; wird das ganze Embed zu lang (6000),
        // ohne Emojis.
        for (boolean mitEmoji : new boolean[] { true, false }) {
            eb.clearFields();
            addListe(eb, "Dabei (" + signedUp.size() + ")", zeilen(signedUp, mitEmoji), true);
            addListe(eb, "Abgemeldet (" + signedOff.size() + ")", zeilen(signedOff, mitEmoji), false);
            if (eb.length() <= 5800) break;
        }

        List<String> clans = Roster.getClans(r.getName());
        String clanLabel = clans.size() > 1 ? String.join(", ", clans) : r.getClan();
        eb.setFooter("Clan: " + clanLabel + " | Min TH: " + r.getMinTh());
        return eb;
    }

    private static List<String> zeilen(List<RosterParticipant> liste, boolean mitEmoji) {
        List<String> z = new ArrayList<>();
        for (RosterParticipant rp : liste) {
            String th = mitEmoji ? getThEmoji(rp.getThLevel()) : "TH" + rp.getThLevel();
            z.add(th + " " + rp.getAccountName());
        }
        if (z.isEmpty()) z.add("Niemand.");
        return z;
    }

    /** Verteilt Zeilen auf so viele Felder wie nötig (je ≤ 1024 Zeichen); Folgefelder ohne Titel. */
    private static void addListe(EmbedBuilder eb, String titel, List<String> zeilen, boolean inline) {
        StringBuilder teil = new StringBuilder();
        boolean erstes = true;
        for (String zeile : zeilen) {
            if (zeile.length() > 1000) zeile = zeile.substring(0, 1000);
            if (teil.length() + zeile.length() + 1 > 1024) {
                eb.addField(erstes ? titel : "\u200B", teil.toString(), inline);
                erstes = false;
                teil.setLength(0);
            }
            teil.append(zeile).append("\n");
        }
        eb.addField(erstes ? titel : "\u200B", teil.toString(), inline);
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
        buttons.add(Button.secondary("roster_refresh_" + r.getName(), "\u200B").withEmoji(Emoji.fromUnicode("🔁")));
        
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

        if (action.equals("refresh")) {
            event.deferEdit().queue();
            Roster r = Roster.getRoster(rosterName);
            if (r == null) {
                event.getHook().sendMessage("Dieser Roster existiert nicht mehr.").setEphemeral(true).queue();
                return;
            }
            event.getHook().editOriginalEmbeds(buildRosterEmbed(r).build()).setComponents(buildActionRows(r)).queue();
            return;
        }

        event.deferReply(true).queue();

        Roster r = Roster.getRoster(rosterName);
        if (r == null) {
            event.getHook().sendMessage("Dieser Roster existiert nicht mehr.").queue();
            return;
        }

        if (r.isClosed()) {
            event.getHook().sendMessage("Dieser Roster ist mittlerweile geschlossen.").queue();
            return;
        }

        // Fetch accounts for user
        User u = new User(event.getUser().getId()).refreshData();
        List<Player> accounts = u.getAllLinkedAccounts();
        if (accounts == null || accounts.isEmpty()) {
            event.getHook().sendMessage("Du hast keine Accounts verlinkt! Bitte nutze `/link`.").queue();
            return;
        }

        List<SelectOption> options = new ArrayList<>();
        for (Player p : accounts) {
            String name = p.getNameDB(); // Use DB to avoid API call
            String tag = p.getTag();
            int thLevel = p.getThLevelDB(); // Use DB cached level

            if (thLevel < r.getMinTh()) {
                // Skip if below TH requirement for both signup and signoff
                continue;
            }

            options.add(SelectOption.of(name + " (" + tag + ")", "roster_" + action + "cmd_" + rosterName + "_" + tag));
        }

        if (options.isEmpty()) {
            event.getHook().sendMessage("Du hast keine passenden Accounts für diesen Roster (Min TH: " + r.getMinTh() + ").").queue();
            return;
        }

        // Limit options to Discord's max 25
        if (options.size() > 25) {
            options = new ArrayList<>(options.subList(0, 25));
        }

        StringSelectMenu menu = StringSelectMenu.create("roster_select_" + action + "_" + rosterName + "_" + event.getMessageId())
                .setPlaceholder("Wähle einen Account aus")
                .addOptions(options)
                .build();

        event.getHook().sendMessage("Wähle den Account aus, den du " + (action.equals("signup") ? "anmelden" : "abmelden") + " möchtest:")
                .addActionRow(menu).queue();
    }

    @Override
    public void onStringSelectInteraction(@javax.annotation.Nonnull StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("roster_select_")) return;

        event.deferEdit().queue();

        String componentData = id.substring("roster_select_".length());
        int lastUnderscore = componentData.lastIndexOf("_");
        String msgId = lastUnderscore != -1 ? componentData.substring(lastUnderscore + 1) : null;

        String val = event.getValues().get(0);
        if (!val.startsWith("roster_")) return;

        // val format: roster_signupcmd_ROSTERNAME_#TAG
        String valData = val.substring(7); // removes "roster_"
        int firstUnderscoreVal = valData.indexOf("_");
        int lastUnderscoreVal = valData.lastIndexOf("_");

        if (firstUnderscoreVal == -1 || lastUnderscoreVal == -1 || firstUnderscoreVal == lastUnderscoreVal) return;

        String actioncmd = valData.substring(0, firstUnderscoreVal);
        String rName = valData.substring(firstUnderscoreVal + 1, lastUnderscoreVal);
        String pTag = valData.substring(lastUnderscoreVal + 1);

        String logicAction = actioncmd.replace("cmd", ""); // "signup" or "signoff"

        Roster r = Roster.getRoster(rName);
        if (r == null) {
            event.getHook().editOriginal("Dieser Roster existiert nicht mehr.").setComponents().queue();
            return;
        }

        if (r.isClosed()) {
            event.getHook().editOriginal("Dieser Roster ist mittlerweile geschlossen.").setComponents().queue();
            return;
        }

        Player p = new Player(pTag);
        
        boolean set = RosterParticipant.setParticipantStatus(rName, event.getUser().getId(), pTag, logicAction, p.getThLevelAPI());

        if (set) {
            event.getHook().editOriginal("Erfolgreich " + (logicAction.equals("signup") ? "angemeldet" : "abgemeldet") + " für " + p.getNameAPI() + "!")
                .setComponents().queue();
        } else {
            event.getHook().editOriginal("Erfolgreich aus dem Roster entfernt für " + p.getNameAPI() + "!")
                .setComponents().queue();
        }

        if (msgId != null && msgId.matches("\\d+")) {
            event.getChannel().retrieveMessageById(msgId).queue(msg -> {
                msg.editMessageEmbeds(buildRosterEmbed(r).build()).setComponents(buildActionRows(r)).queue();
            }, err -> {});
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@javax.annotation.Nonnull CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("roster")) return;

        String opt = event.getFocusedOption().getName();
        String currentStr = event.getFocusedOption().getValue().toLowerCase();

        switch (opt) {
            case "clan", "clan_2", "clan_3" -> {
                    List<Choice> choices = DBManager.getClansAutocompleteWithSideclans(currentStr);
                    event.replyChoices(choices).queue();
                }
            case "name", "base_roster" -> {
                List<Choice> choices = Roster.getAllRosters().stream()
                        .map(Roster::getName)
                        .filter(n -> n.toLowerCase().contains(currentStr))
                        .map(n -> new Choice(n, n))
                        .limit(25)
                        .collect(Collectors.toList());
                event.replyChoices(choices).queue();
            }
            case "player" ->                 {
                List<Choice> choices = DBManager.getPlayerlistAutocomplete(currentStr, DBManager.InClanType.ALL);
                event.replyChoices(choices).queue();
                }
            default ->                 {
                }
        }
    }
}
