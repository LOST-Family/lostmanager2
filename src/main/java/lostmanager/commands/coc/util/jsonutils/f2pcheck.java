package lostmanager.commands.coc.util.jsonutils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import lostmanager.datawrapper.Player;
import lostmanager.datawrapper.User;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class f2pcheck extends ListenerAdapter {

    @SuppressWarnings("null")
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("f2pcheck"))
            return;

        String title = "F2P Check";
        event.deferReply().queue();

        new Thread(() -> {
            OptionMapping playerOption = event.getOption("player");

            if (playerOption == null) {
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Der Parameter 'player' ist erforderlich.", MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            String playerTag = playerOption.getAsString();
            User userExecuted = new User(event.getUser().getId());

            if (!canAccessPlayer(userExecuted, playerTag)) {
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Du hast keine Berechtigung, die Daten dieses Spielers anzusehen.",
                                MessageUtil.EmbedType.ERROR))
                        .queue();
                return;
            }

            // Load JSON from DB
            String sql = "SELECT json, timestamp FROM userjsons WHERE tag = ? LIMIT 1";

            try (java.sql.PreparedStatement pstmt = lostmanager.dbutil.Connection.getConnection()
                    .prepareStatement(sql)) {
                pstmt.setString(1, playerTag);

                try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                    if (!rs.next()) {
                        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                                "Keine JSON-Daten für diesen Spieler gefunden.", MessageUtil.EmbedType.ERROR)).queue();
                        return;
                    }

                    String jsonStr = rs.getString("json");
                    java.sql.Timestamp timestamp = rs.getTimestamp("timestamp");
                    JSONObject json = new JSONObject(jsonStr);

                    // Collect data into Map for O(1) access
                    Map<String, Integer> playerData = new java.util.HashMap<>();
                    collectDataIds(json, playerData);

                    System.out.println("DEBUG: PlayerData: " + playerData);

                    // Format database timestamp
                    ZonedDateTime uploadTime = timestamp.toInstant().atZone(ZoneId.of("Europe/Berlin"));
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'");
                    String uploadFormatiert = uploadTime.format(formatter);

                    // Run F2P Check
                    F2PCheckAlgorithm.CheckResult result = F2PCheckAlgorithm.check(playerData);
                    Player player = new Player(playerTag);
                    if (result.isF2P()) {
                        String description = "Dieser Spieler " + player.getInfoStringAPI()
                                + " ist **Free to Play** (F2P) konform!\n\n"
                                + "**Hochgeladen:** " + uploadFormatiert;

                        event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title, description,
                                MessageUtil.EmbedType.SUCCESS))
                                .queue();
                    } else {
                        List<String> reasons = result.getReasons();
                        sendFailureMessages(event, title, player, reasons, uploadFormatiert);
                    }
                }
            } catch (Exception e) {
                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                        "Fehler beim Laden der Daten: " + e.getMessage(), MessageUtil.EmbedType.ERROR))
                        .queue();
                e.printStackTrace();
            }

        }, "F2PCheckCommand-" + event.getUser().getId()).start();
    }

    private void collectDataIds(Object obj, Map<String, Integer> dataMap) {
        if (obj instanceof JSONObject) {
            JSONObject jsonObject = (JSONObject) obj;
            if (jsonObject.has("data")) {
                String dataId = jsonObject.get("data").toString();
                int count = jsonObject.optInt("cnt", 1);
                dataMap.put(dataId, dataMap.getOrDefault(dataId, 0) + count);
            }

            // Recursively check all keys
            for (String key : jsonObject.keySet()) {
                Object value = jsonObject.get(key);
                collectDataIds(value, dataMap);
            }
        } else if (obj instanceof JSONArray) {
            JSONArray jsonArray = (JSONArray) obj;
            for (int i = 0; i < jsonArray.length(); i++) {
                Object item = jsonArray.get(i);
                if (item instanceof Integer) {
                    dataMap.put(item.toString(), dataMap.getOrDefault(item.toString(), 0) + 1);
                } else {
                    collectDataIds(item, dataMap);
                }
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("f2pcheck"))
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
        }, "F2PCheckAutocomplete-" + event.getUser().getId()).start();
    }

    /**
     * Check if a user can access a specific player's data
     */
    private boolean canAccessPlayer(User user, String playerTag) {
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

    @SuppressWarnings("null")
    private void sendFailureMessages(SlashCommandInteractionEvent event, String title, Player player,
            List<String> reasons, String uploadTimestamp) {
        String baseMessage = "Dieser Spieler " + player.getInfoStringAPI() + " ist **NICHT F2P**.\n"
                + "**Hochgeladen:** " + uploadTimestamp + "\n\n";

        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();
        currentChunk.append(baseMessage);

        if (reasons == null || reasons.isEmpty()) {
            currentChunk.append("Grund: Unbekannt");
            chunks.add(currentChunk.toString());
        } else if (reasons.size() == 1) {
            currentChunk.append("Grund: ").append(reasons.get(0));
            chunks.add(currentChunk.toString());
        } else {
            currentChunk.append("Gründe:\n");
            for (String reason : reasons) {
                String line = "- " + reason + "\n";
                // Check if adding this line exceeds limit (using 4000 to be safe)
                if (currentChunk.length() + line.length() > 4000) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }
                currentChunk.append(line);
            }
            if (currentChunk.length() > 0) {
                chunks.add(currentChunk.toString());
            }
        }

        for (int i = 0; i < chunks.size(); i++) {
            if (i == 0) {
                event.getHook()
                        .editOriginalEmbeds(MessageUtil.buildEmbed(title, chunks.get(i), MessageUtil.EmbedType.ERROR))
                        .complete();
            } else {
                event.getChannel()
                        .sendMessageEmbeds(MessageUtil.buildEmbed(title, chunks.get(i), MessageUtil.EmbedType.ERROR))
                        .complete();
            }
        }
    }
}
