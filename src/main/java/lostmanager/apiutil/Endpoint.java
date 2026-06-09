package lostmanager.apiutil;

import java.util.List;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;

public record Endpoint(
    String group,
    String name,
    String description,
    String method,
    String pathTemplate,
    List<Param> params
) {
    public record Param(
        String name,
        String description,
        OptionType type,
        boolean required,
        boolean pathParam,
        List<Command.Choice> choices
    ) {}
}
