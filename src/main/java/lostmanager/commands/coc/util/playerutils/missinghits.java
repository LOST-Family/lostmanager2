package lostmanager.commands.coc.util.playerutils;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.Bot;
import lostmanager.datawrapper.Clan;
import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
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
                boolean overallFoundMissingHits = false;

                List<String> registeredClans = DBManager.getAllClans();
                List<String> sideClans = DBUtil.getArrayListFromSQL("SELECT clan_tag FROM sideclans", String.class);
                Map<String, JSONObject> cwlWarCache = new ConcurrentHashMap<>();

                java.util.concurrent.atomic.AtomicInteger completedCount = new java.util.concurrent.atomic.AtomicInteger(
                        0);
                Set<String> accountsProcessing = ConcurrentHashMap.newKeySet();
                List<CompletableFuture<String>> accountFutures = new ArrayList<>();
                Map<String, JSONObject> raidCache = new ConcurrentHashMap<>();
                Map<String, JSONObject> cwCache = new ConcurrentHashMap<>();

                for (Player player : linkedAccounts) {
                    CompletableFuture<String> accountFuture = CompletableFuture.supplyAsync(() -> {
                        String playerName = player.getNameDB();
                        if (playerName == null)
                            playerName = player.getTag();
                        accountsProcessing.add(playerName);
                        updateProgressBar(event, completedCount, accountsProcessing, linkedAccounts.size());

                        try {
                            StringBuilder playerReport = new StringBuilder();
                            boolean playerHasMissing = false;

                            Set<String> clansToCheck = new java.util.LinkedHashSet<>();
                            Clan apiClan = player.getClanAPI();
                            if (apiClan != null)
                                clansToCheck.add(apiClan.getTag());
                            Clan dbClan = player.getClanDB();
                            if (dbClan != null)
                                clansToCheck.add(dbClan.getTag());
                            clansToCheck.addAll(registeredClans);
                            clansToCheck.addAll(sideClans);

                            // Parallel check for CW, CWL, and Raid across all clans
                            CompletableFuture<String> cwTask = findMissingTask(player, clansToCheck, "CW", cwlWarCache,
                                    raidCache, cwCache);
                            CompletableFuture<String> cwlTask = findMissingTask(player, clansToCheck, "CWL",
                                    cwlWarCache, raidCache, cwCache);
                            CompletableFuture<String> raidTask = findMissingTask(player, clansToCheck, "RAID",
                                    cwlWarCache, raidCache, cwCache);

                            String cwResult = cwTask.join();
                            String cwlResult = cwlTask.join();
                            String raidResult = raidTask.join();

                            if (!cwResult.isEmpty() && !cwResult.equals("FOUND_BUT_DONE")) {
                                playerReport.append(cwResult);
                                playerHasMissing = true;
                            }
                            if (!cwlResult.isEmpty() && !cwlResult.equals("FOUND_BUT_DONE")) {
                                playerReport.append(cwlResult);
                                playerHasMissing = true;
                            }
                            if (!raidResult.isEmpty() && !raidResult.equals("FOUND_BUT_DONE")) {
                                playerReport.append(raidResult);
                                playerHasMissing = true;
                            }

                            String clanGamesResult = checkClanGames(player);
                            if (!clanGamesResult.isEmpty()) {
                                playerReport.append(clanGamesResult);
                                playerHasMissing = true;
                            }

                            if (playerHasMissing) {
                                String clanName = apiClan != null && apiClan.getNameDB() != null ? apiClan.getNameDB()
                                        : (dbClan != null && dbClan.getNameDB() != null ? dbClan.getNameDB()
                                                : "");
                                StringBuilder detail = new StringBuilder();
                                detail.append("### ").append(playerName);
                                if (!clanName.isEmpty())
                                    detail.append(" (").append(clanName).append(")");
                                detail.append("\n");
                                detail.append(playerReport).append("\n");
                                return detail.toString();
                            }
                        } finally {
                            accountsProcessing.remove(playerName);
                            completedCount.incrementAndGet();
                            updateProgressBar(event, completedCount, accountsProcessing, linkedAccounts.size());
                        }
                        return "";
                    });
                    accountFutures.add(accountFuture);
                }

                CompletableFuture.allOf(accountFutures.toArray(new CompletableFuture[0])).join();

                for (CompletableFuture<String> fut : accountFutures) {
                    String result = fut.join();
                    if (!result.isEmpty()) {
                        report.append(result);
                        overallFoundMissingHits = true;
                    }
                }

                if (overallFoundMissingHits) {
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

    private static boolean tagsMatch(String t1, String t2) {
        if (t1 == null || t2 == null)
            return false;
        return t1.replace("#", "").equalsIgnoreCase(t2.replace("#", ""));
    }

    private synchronized void updateProgressBar(SlashCommandInteractionEvent event,
            java.util.concurrent.atomic.AtomicInteger completed, Set<String> processing, int total) {
        int comp = completed.get();
        int proc = processing.size();
        int queue = total - comp - proc;

        StringBuilder sb = new StringBuilder();
        sb.append(getProgressBar(comp, total)).append("\n\n");
        if (comp != 0)
            sb.append("✅ **Fertig:** ").append(comp).append("\n");
        sb.append("🔄 **In Bearbeitung:** ").append(proc);
        if (proc > 0) {
            sb.append(" (").append(String.join(", ", processing)).append(")");
        }
        sb.append("\n");
        if (queue > 0)
            sb.append("⏳ **In Warteschlange:** ").append(queue);

        event.getHook().editOriginalEmbeds(
                MessageUtil.buildEmbed("Missing Hits - Suche läuft...", sb.toString(), MessageUtil.EmbedType.LOADING))
                .queue();
    }

    private CompletableFuture<String> findMissingTask(Player player, Set<String> clans, String type,
            Map<String, JSONObject> cwlWarCache, Map<String, JSONObject> raidCache, Map<String, JSONObject> cwCache) {
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (String tag : clans) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                Clan clan = new Clan(tag);
                switch (type) {
                    case "CW":
                        return checkCW(player, clan, cwCache);
                    case "CWL":
                        return checkCWL(player, clan, cwlWarCache);
                    case "RAID":
                        return checkRaid(player, clan, raidCache);
                    default:
                        return "";
                }
            }));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> {
            String bestBackup = "";
            for (CompletableFuture<String> fut : futures) {
                String result = fut.join();
                if (result.isEmpty())
                    continue;
                if (!result.equals("FOUND_BUT_DONE") && !result.startsWith("NOT_PARTICIPATING"))
                    return result;

                if (result.equals("FOUND_BUT_DONE")) {
                    bestBackup = "FOUND_BUT_DONE";
                } else if (result.startsWith("NOT_PARTICIPATING") && !bestBackup.equals("FOUND_BUT_DONE")) {
                    bestBackup = result;
                }
            }

            if (bestBackup.startsWith("NOT_PARTICIPATING")) {
                String timeLeft = bestBackup.substring("NOT_PARTICIPATING".length());
                return "• **Raid:** Noch nicht angegriffen (" + timeLeft + " verbleibend)\n";
            }
            return bestBackup;
        });
    }

    private String checkCW(Player player, Clan clan, Map<String, JSONObject> cwCache) {
        JSONObject cwJson = cwCache.computeIfAbsent(clan.getTag(), t -> clan.getCWJson());
        if (cwJson != null && cwJson.has("state")) {
            String state = cwJson.getString("state");
            if (state.equals("inWar")) {
                int attacksPerMember = cwJson.optInt("attacksPerMember", 2);
                if (!cwJson.has("clan"))
                    return "";
                JSONObject clanData = cwJson.getJSONObject("clan");
                JSONArray members = clanData.getJSONArray("members");

                for (int i = 0; i < members.length(); i++) {
                    JSONObject member = members.getJSONObject(i);
                    if (tagsMatch(member.getString("tag"), player.getTag())) {
                        int attacks = member.has("attacks") ? member.getJSONArray("attacks").length() : 0;
                        if (attacks < attacksPerMember) {
                            long endTime = 0;
                            if (cwJson.has("endTime")) {
                                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                                        .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
                                        .withZone(java.time.ZoneOffset.UTC);
                                endTime = java.time.Instant.from(formatter.parse(cwJson.getString("endTime")))
                                        .toEpochMilli();
                            }
                            String timeLeft = formatTimeLeft(endTime - System.currentTimeMillis());
                            return "• **Clan War:** " + (attacksPerMember - attacks) + " Angriff(e) fehlen (" + timeLeft
                                    + " verbleibend)\n";
                        }
                        return "FOUND_BUT_DONE";
                    }
                }
            }
        }
        return "";
    }

    private String checkCWL(Player player, Clan clan, Map<String, JSONObject> cwlWarCache) {
        if (clan.isCWLActive()) {
            JSONObject cwlJson = clan.getCWLJson();
            if (cwlJson != null && cwlJson.has("rounds")) {
                JSONArray rounds = cwlJson.getJSONArray("rounds");
                for (int r = 0; r < rounds.length(); r++) {
                    JSONArray warTags = rounds.getJSONObject(r).getJSONArray("warTags");
                    for (int w = 0; w < warTags.length(); w++) {
                        String warTag = warTags.getString(w);
                        if (warTag.equals("#0"))
                            continue;

                        JSONObject warData = cwlWarCache.computeIfAbsent(warTag, Clan::getCWLDayJson);
                        if (warData == null || !warData.has("state") || !warData.has("clan")
                                || !warData.has("opponent"))
                            continue;

                        JSONObject warClan = warData.getJSONObject("clan");
                        JSONObject warOpponent = warData.getJSONObject("opponent");

                        if (tagsMatch(warClan.getString("tag"), clan.getTag())
                                || tagsMatch(warOpponent.getString("tag"), clan.getTag())) {
                            String state = warData.getString("state");
                            if (state.equals("inWar") || state.equals("warEnded")) {
                                JSONObject ourWarPart = tagsMatch(warClan.getString("tag"), clan.getTag()) ? warClan
                                        : warOpponent;
                                JSONArray members = ourWarPart.getJSONArray("members");
                                for (int i = 0; i < members.length(); i++) {
                                    JSONObject m = members.getJSONObject(i);
                                    if (tagsMatch(m.getString("tag"), player.getTag())) {
                                        int attacks = m.has("attacks") ? m.getJSONArray("attacks").length() : 0;
                                        if (attacks < 1 && state.equals("inWar")) {
                                            String endTimeStr = warData.optString("endTime", "");
                                            long cwlDayEndTime = 0;
                                            if (!endTimeStr.isEmpty()) {
                                                java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                                                        .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
                                                        .withZone(java.time.ZoneOffset.UTC);
                                                cwlDayEndTime = java.time.Instant.from(formatter.parse(endTimeStr))
                                                        .toEpochMilli();
                                            }
                                            String timeLeft = formatTimeLeft(
                                                    cwlDayEndTime - System.currentTimeMillis());
                                            return "• **CWL Tag " + (r + 1) + ":** 1 Angriff fehlt (" + timeLeft
                                                    + " verbleibend)\n";
                                        }
                                        return "FOUND_BUT_DONE";
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return "";
    }

    private String checkRaid(Player player, Clan clan, Map<String, JSONObject> raidCache) {
        JSONObject raidJson = raidCache.computeIfAbsent(clan.getTag(), t -> clan.getRaidJsonFull());
        if (raidJson != null && raidJson.has("items")) {
            JSONArray items = raidJson.getJSONArray("items");
            if (items.length() > 0) {
                JSONObject currentItem = items.getJSONObject(0);
                String state = currentItem.optString("state", "ended");
                if (state.equals("ongoing")) {
                    long endTime = 0;
                    if (currentItem.has("endTime")) {
                        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
                                .ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'")
                                .withZone(java.time.ZoneOffset.UTC);
                        endTime = java.time.Instant.from(formatter.parse(currentItem.getString("endTime")))
                                .toEpochMilli();
                    }
                    String timeLeft = formatTimeLeft(endTime - System.currentTimeMillis());

                    if (currentItem.has("members")) {
                        JSONArray members = currentItem.getJSONArray("members");
                        for (int i = 0; i < members.length(); i++) {
                            JSONObject member = members.getJSONObject(i);
                            if (tagsMatch(member.getString("tag"), player.getTag())) {
                                int attacks = member.optInt("attackCount", member.optInt("attacks", 0));
                                int attackLimit = member.optInt("attackLimit", 6);
                                int bonusAttackLimit = member.optInt("bonusAttackLimit", 0);
                                int totalLimit = attackLimit + bonusAttackLimit;

                                if (attacks < totalLimit) {
                                    return "• **Raid:** " + (totalLimit - attacks) + " Angriff(e) fehlen (" + timeLeft
                                            + " verbleibend)\n";
                                }
                                return "FOUND_BUT_DONE";
                            }
                        }
                    }
                    return "NOT_PARTICIPATING" + timeLeft;
                }
            }
        }
        return "";
    }

    private String checkClanGames(Player player) {
        ZonedDateTime gamesStart = Bot.getPrevious22thAt7am();
        ZonedDateTime gamesEnd = Bot.getPrevious28thAt12pm();
        ZonedDateTime now = ZonedDateTime.now();

        if (now.isAfter(gamesStart) && now.isBefore(gamesEnd)) {
            try {
                JSONObject playerJson = new JSONObject(player.getJson());
                JSONArray achievements = playerJson.getJSONArray("achievements");
                int currentPoints = 0;
                for (int i = 0; i < achievements.length(); i++) {
                    JSONObject a = achievements.getJSONObject(i);
                    if (a.getString("name").equals("Games Champion")) {
                        currentPoints = a.getInt("value");
                        break;
                    }
                }

                String sql = "SELECT data::text::integer FROM achievement_data WHERE player_tag = ? AND type = 'CLANGAMES_POINTS' AND time = ? ORDER BY time LIMIT 1";
                Integer pointsStart = DBUtil.getValueFromSQL(sql, Integer.class, player.getTag(),
                        java.sql.Timestamp.from(gamesStart.toInstant()));

                if (pointsStart != null) {
                    int earned = currentPoints - pointsStart;
                    if (earned < 4000) {
                        String timeLeft = formatTimeLeft(
                                gamesEnd.toInstant().toEpochMilli() - System.currentTimeMillis());
                        return "• **Clan Games:** " + (4000 - earned) + " Punkte fehlen (" + timeLeft
                                + " verbleibend)\n";
                    }
                }
            } catch (Exception e) {
                /* ignore */ }
        }
        return "";
    }

    private String getProgressBar(int current, int total) {
        int size = 10;
        double progress = (double) current / total;
        int activeBlocks = (int) (progress * size);
        StringBuilder sb = new StringBuilder("`[");
        for (int i = 0; i < size; i++) {
            if (i < activeBlocks)
                sb.append("■");
            else
                sb.append("□");
        }
        sb.append("]` (").append((int) (progress * 100)).append("%)");
        return sb.toString();
    }

    private String formatTimeLeft(long millis) {
        if (millis <= 0)
            return "beendet";
        long days = millis / (1000 * 60 * 60 * 24);
        long hours = (millis / (1000 * 60 * 60)) % 24;
        long minutes = (millis / (1000 * 60)) % 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }
}
