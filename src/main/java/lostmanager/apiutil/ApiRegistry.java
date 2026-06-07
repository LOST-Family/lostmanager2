package lostmanager.apiutil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

public class ApiRegistry {

    private static Endpoint.Param pp(String name) {
        return new Endpoint.Param(name, name, OptionType.STRING, true, true, List.of());
    }

    private static Endpoint.Param pq(String name, OptionType type) {
        return new Endpoint.Param(name, name, type, false, false, List.of());
    }

    private static Endpoint.Param rq(String name, OptionType type) {
        return new Endpoint.Param(name, name, type, true, false, List.of());
    }

    private static final List<Command.Choice> WAR_FREQ_CHOICES = List.of(
        new Command.Choice("always", "always"),
        new Command.Choice("moreThanOncePerWeek", "moreThanOncePerWeek"),
        new Command.Choice("oncePerWeek", "oncePerWeek"),
        new Command.Choice("lessThanOncePerWeek", "lessThanOncePerWeek"),
        new Command.Choice("never", "never"),
        new Command.Choice("unknown", "unknown"),
        new Command.Choice("any", "any")
    );

    private static final List<Endpoint> ENDPOINTS = List.of(
        // clans
        new Endpoint("clans", "search", "Search clans", "GET", "/clans", List.of(
            pq("name", OptionType.STRING),
            new Endpoint.Param("warfrequency", "warfrequency", OptionType.STRING, false, false, WAR_FREQ_CHOICES),
            pq("location_id", OptionType.STRING),
            pq("min_members", OptionType.INTEGER), pq("max_members", OptionType.INTEGER),
            pq("min_clan_points", OptionType.INTEGER), pq("min_clan_level", OptionType.INTEGER),
            pq("label_ids", OptionType.STRING),
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "get", "Get clan by tag", "GET", "/clans/{tag}", List.of(pp("tag"))),
        new Endpoint("clans", "members", "List clan members", "GET", "/clans/{tag}/members", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "warlog", "Get clan war log", "GET", "/clans/{tag}/warlog", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("clans", "currentwar", "Get current war", "GET", "/clans/{tag}/currentwar", List.of(pp("tag"))),
        new Endpoint("clans", "leaguegroup", "Get CWL league group", "GET", "/clans/{tag}/currentwar/leaguegroup", List.of(pp("tag"))),
        new Endpoint("clans", "capitalraidseasons", "Get capital raid seasons", "GET", "/clans/{tag}/capitalraidseasons", List.of(
            pp("tag"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // clanwarleagues
        new Endpoint("clanwarleagues", "war", "Get CWL war by tag", "GET", "/clanwarleagues/wars/{wartag}", List.of(pp("wartag"))),
        // players
        new Endpoint("players", "get", "Get player by tag", "GET", "/players/{tag}", List.of(pp("tag"))),
        new Endpoint("players", "verifytoken", "Verify player token", "POST", "/players/{tag}/verifytoken", List.of(
            pp("tag"), rq("token", OptionType.STRING)
        )),
        // leagues
        new Endpoint("leagues", "list", "List leagues", "GET", "/leagues", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("leagues", "get", "Get league", "GET", "/leagues/{league_id}", List.of(pp("league_id"))),
        new Endpoint("leagues", "seasons", "Get league seasons", "GET", "/leagues/{league_id}/seasons", List.of(
            pp("league_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("leagues", "seasonrankings", "Get season rankings", "GET", "/leagues/{league_id}/seasons/{season_id}", List.of(
            pp("league_id"), pp("season_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // warleagues
        new Endpoint("warleagues", "list", "List war leagues", "GET", "/warleagues", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("warleagues", "get", "Get war league", "GET", "/warleagues/{league_id}", List.of(pp("league_id"))),
        // capitalleagues
        new Endpoint("capitalleagues", "list", "List capital leagues", "GET", "/capitalleagues", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("capitalleagues", "get", "Get capital league", "GET", "/capitalleagues/{league_id}", List.of(pp("league_id"))),
        // builderbaseleagues
        new Endpoint("builderbaseleagues", "list", "List builder base leagues", "GET", "/builderbaseleagues", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("builderbaseleagues", "get", "Get builder base league", "GET", "/builderbaseleagues/{league_id}", List.of(pp("league_id"))),
        // locations
        new Endpoint("locations", "list", "List locations", "GET", "/locations", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "get", "Get location", "GET", "/locations/{location_id}", List.of(pp("location_id"))),
        new Endpoint("locations", "rankings-clans", "Clan rankings", "GET", "/locations/{location_id}/rankings/clans", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "rankings-players", "Player rankings", "GET", "/locations/{location_id}/rankings/players", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "rankings-clans-builder-base", "Clan BB rankings", "GET", "/locations/{location_id}/rankings/clans/builder-base", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "rankings-players-builder-base", "Player BB rankings", "GET", "/locations/{location_id}/rankings/players/builder-base", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("locations", "rankings-capitals", "Capital rankings", "GET", "/locations/{location_id}/rankings/capitals", List.of(
            pp("location_id"), pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // labels
        new Endpoint("labels", "clans", "List clan labels", "GET", "/labels/clans", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        new Endpoint("labels", "players", "List player labels", "GET", "/labels/players", List.of(
            pq("limit", OptionType.INTEGER), pq("after", OptionType.STRING), pq("before", OptionType.STRING)
        )),
        // goldpass
        new Endpoint("goldpass", "current", "Get current gold pass season", "GET", "/goldpass/seasons/current", List.of())
    );

    public static Endpoint find(String group, String name) {
        for (Endpoint e : ENDPOINTS) {
            if (e.group().equals(group) && e.name().equals(name)) return e;
        }
        return null;
    }

    @SuppressWarnings("null")
    public static SlashCommandData buildSlashCommand() {
        SlashCommandData cmd = Commands.slash("api", "Clash of Clans API");

        Map<String, List<Endpoint>> byGroup = new LinkedHashMap<>();
        for (Endpoint e : ENDPOINTS) {
            byGroup.computeIfAbsent(e.group(), k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<Endpoint>> entry : byGroup.entrySet()) {
            SubcommandGroupData group = new SubcommandGroupData(entry.getKey(), entry.getKey() + " endpoints");
            for (Endpoint endpoint : entry.getValue()) {
                SubcommandData sub = new SubcommandData(endpoint.name(), endpoint.description());
                for (Endpoint.Param param : endpoint.params()) {
                    OptionData opt = new OptionData(param.type(), param.name(), param.description(), param.required());
                    if (!param.choices().isEmpty()) {
                        opt.addChoices(param.choices());
                    }
                    sub.addOptions(opt);
                }
                group.addSubcommands(sub);
            }
            cmd.addSubcommandGroups(group);
        }

        return cmd;
    }
}
