package lostmanager.commands.coc.util.playerutils;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class missinghits extends ListenerAdapter {

    @SuppressWarnings("null")
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("missinghits"))
            return;

        // Defer reply as ephemeral since this involves personal account info
        event.deferReply(true).queue();

        new Thread(() -> {
            try {
                User user = new User(event.getUser().getId());
                ArrayList<Player> linkedAccounts = user.getAllLinkedAccounts();

                if (linkedAccounts.isEmpty()) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed("Missing Hits",
                            "Du hast keine verlinkten Clash of Clans Accounts.", MessageUtil.EmbedType.ERROR)).queue();
                    return;
                }

                StringBuilder report = new StringBuilder();
                boolean foundMissingHits = false;

                for (Player player : linkedAccounts) {
                    Clan clan = player.getClanDB();
                    // Skip if player is not in a clan
                    if (clan == null) {
                        continue;
                    }

                    StringBuilder playerReport = new StringBuilder();
                    boolean playerHasMissing = false;

                    // --- Clan War Check ---
                    if (clan.isCWActive()) {
                        JSONObject cwJson = clan.getCWJson();
                        String state = cwJson.getString("state");

                        // Only check if war is in progress or ended (not preparation)
                        if (state.equals("inWar") || state.equals("warEnded")) {
                            // Determine required attacks (default 2, or from API if available)
                            int attacksPerMember = 2;
                            if (cwJson.has("attacksPerMember")) {
                                attacksPerMember = cwJson.getInt("attacksPerMember");
                            }

                            // Find player in war member list
                            JSONObject clanData = cwJson.getJSONObject("clan");
                            JSONArray members = clanData.getJSONArray("members");

                            for (int i = 0; i < members.length(); i++) {
                                JSONObject member = members.getJSONObject(i);
                                if (member.getString("tag").equals(player.getTag())) {
                                    int attacks = 0;
                                    if (member.has("attacks")) {
                                        attacks = member.getJSONArray("attacks").length();
                                    }

                                    if (attacks < attacksPerMember) {
                                        playerReport.append("- **Clan War:** ").append(attacksPerMember - attacks)
                                                .append(" Angriff(e) fehlen\n");
                                        playerHasMissing = true;
                                    }
                                    break;
                                }
                            }
                        }
                    }

                    // --- Raid Weekend Check ---
                    // Check if raid is active (ongoing)
                    // We use a broader check here to look at the latest raid data similar to
                    // ListeningEvent
                    JSONObject raidJson = clan.getRaidJsonFull();
                    JSONArray items = raidJson.getJSONArray("items");

                    if (items.length() > 0) {
                        JSONObject currentRaid = items.getJSONObject(0);
                        String state = currentRaid.getString("state");

                        if (state.equals("ongoing")) {
                            // Find player in raid member list
                            ArrayList<Player> raidMembers = clan.getRaidMemberList();
                            boolean playerFoundInRaid = false;

                            for (Player raidPlayer : raidMembers) {
                                if (raidPlayer.getTag().equals(player.getTag())) {
                                    playerFoundInRaid = true;

                                    int attacks = raidPlayer.getCurrentRaidAttacks();
                                    int limit = raidPlayer.getCurrentRaidAttackLimit();
                                    int bonus = raidPlayer.getCurrentRaidbonusAttackLimit(); // Note: Player class uses
                                                                                             // 'bonus' lowercase in
                                                                                             // getter name but logic
                                                                                             // implies it exists

                                    int totalLimit = limit + bonus;

                                    if (attacks < totalLimit) {
                                        playerReport.append("- **Raid:** ").append(totalLimit - attacks)
                                                .append(" Angriff(e) fehlen\n");
                                        playerHasMissing = true;
                                    }
                                    break;
                                }
                            }

                            // If player is not in the raid list but is in the clan, they might have 0
                            // attacks (and effectively 6 missing if they haven't started)
                            // However, we don't know if they are eligible or just haven't attacked.
                            // Usually if they haven't attacked they are not in the API list until they do,
                            // OR they are in the list with 0 attacks?
                            // The API usually returns "members" list in raid data.
                            // Let's rely on getRaidMemberList from Clan which parses the API.

                            if (!playerFoundInRaid) {
                                // If expected to attack but hasn't started yet.
                                // Hard to tell if they are eligible without more info, but usually checking if
                                // they are in the clan is a good proxy.
                                // But they might be new.
                                // For now, let's only report if we have data or if we clearly see they missed
                                // it (like in ListeningEvent where we assume they should attack).
                                // ListeningEvent logic:
                                // "Check members who didn't raid at all or didn't finish"
                                // It iterates dbMembers and checks if they are in raidMembers.
                                // If not in raidMembers, it considers them "notDone".

                                // So we should report "Not started" or "6 attacks missing".
                                playerReport.append("- **Raid:** Noch nicht angegriffen\n");
                                playerHasMissing = true;
                            }
                        }
                    }

                    if (playerHasMissing) {
                        report.append("### ").append(player.getNameDB()).append(" (").append(clan.getNameDB())
                                .append(")\n");
                        report.append(playerReport);
                        report.append("\n");
                        foundMissingHits = true;
                    }
                }

                if (foundMissingHits) {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed("Missing Hits",
                            report.toString(), MessageUtil.EmbedType.INFO)).queue();
                } else {
                    event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed("Missing Hits",
                            "Du hast aktuell keine fehlenden Angriffe!", MessageUtil.EmbedType.SUCCESS)).queue();
                }

            } catch (Exception e) {
                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed("Fehler",
                        "Ein Fehler ist aufgetreten: " + e.getMessage(), MessageUtil.EmbedType.ERROR)).queue();
                e.printStackTrace();
            }
        }, "MissingHits-" + event.getUser().getId()).start();
    }
}
