package lostmanager.commands.coc.util;

import java.util.ArrayList;
import java.util.List;

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

            event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
                    "F2P Check für " + playerTag + " ist noch nicht implementiert.", MessageUtil.EmbedType.INFO))
                    .queue();

        }, "F2PCheckCommand-" + event.getUser().getId()).start();
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
}
