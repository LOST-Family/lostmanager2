package lostmanager.commands.api;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import lostmanager.apiutil.ApiRegistry;
import lostmanager.apiutil.ApiResponse;
import lostmanager.apiutil.ApiUtil;
import lostmanager.apiutil.Endpoint;
import lostmanager.apiutil.JsonUtil;
import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.FileUpload;
import org.json.JSONObject;

@SuppressWarnings("null")
public class ApiCommand extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!event.getName().equals("api")) return;

        User user = new User(event.getUser().getId());
        if (!user.isColeaderOrHigher()) {
            event.replyEmbeds(MessageUtil.buildEmbed("API",
                "Du musst mindestens Vize-Anführer eines Clans sein, um diesen Befehl auszuführen.",
                MessageUtil.EmbedType.ERROR)).setEphemeral(true).queue();
            return;
        }

        String groupName = event.getSubcommandGroup();
        String subName = event.getSubcommandName();
        Endpoint endpoint = ApiRegistry.find(groupName, subName);
        if (endpoint == null) {
            event.replyEmbeds(MessageUtil.buildEmbed("API", "Unbekannter Endpunkt.", MessageUtil.EmbedType.ERROR))
                .setEphemeral(true).queue();
            return;
        }

        // Resolve all options before deferring (string ops only — fast)
        String resolvedPath = endpoint.pathTemplate();
        Map<String, String> queryParams = new LinkedHashMap<>();
        JSONObject bodyObj = new JSONObject();

        for (Endpoint.Param param : endpoint.params()) {
            OptionMapping opt = event.getOption(param.name());
            if (opt == null) continue;
            String value = opt.getAsString();
            if (param.pathParam()) {
                resolvedPath = resolvedPath.replace("{" + param.name() + "}",
                    URLEncoder.encode(value, StandardCharsets.UTF_8));
            } else if (endpoint.method().equals("POST")) {
                bodyObj.put(param.name(), value);
            } else {
                queryParams.put(param.name(), value);
            }
        }

        final String finalPath = resolvedPath;
        final Map<String, String> finalQuery = queryParams;
        final String finalBody = endpoint.method().equals("POST") ? bodyObj.toString() : null;

        event.deferReply().queue();

        new Thread(() -> {
            ApiResponse response = ApiUtil.raw(endpoint.method(), finalPath, finalQuery, finalBody);

            int status = response.status();
            String body = response.body() != null ? response.body() : "";

            if (status == -1) {
                event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed("API",
                    "Verbindungsfehler: " + body, MessageUtil.EmbedType.ERROR)).queue();
                return;
            }

            String pretty = JsonUtil.pretty(body);
            MessageUtil.EmbedType embedType = (status >= 200 && status < 300)
                ? MessageUtil.EmbedType.SUCCESS : MessageUtil.EmbedType.ERROR;
            String desc = "**" + groupName + " " + subName + "** — HTTP " + status;
            String filename = groupName + "_" + subName + ".txt";

            ByteArrayInputStream is = new ByteArrayInputStream(pretty.getBytes(StandardCharsets.UTF_8));
            event.getHook().editOriginalAttachments(FileUpload.fromData(is, filename))
                .setEmbeds(MessageUtil.buildEmbed("API", desc, embedType))
                .queue();
        }).start();
    }
}
