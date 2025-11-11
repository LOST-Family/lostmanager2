package commands.util;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import util.MessageUtil;

public class checkreacts extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("checkreacts"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
		String title = "Check-Reacts";

		OptionMapping roleOption = event.getOption("role");
		OptionMapping messagelinkOption = event.getOption("message_link");
		OptionMapping emojiOption = event.getOption("emoji");

		if (roleOption == null || messagelinkOption == null || emojiOption == null) {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Die Parameter Rolle, Message-Link und Emoji sind pflicht.", MessageUtil.EmbedType.ERROR))
					.queue();
			return;
		}

		Role role = roleOption.getAsRole();
		String messagelink = messagelinkOption.getAsString();
		String messageId = messagelink.split("/")[messagelink.split("/").length - 1];
		String emoji = emojiOption.getAsString();
		String channelId = messagelink.split("/")[messagelink.split("/").length - 2];

		MessageChannelUnion channel = null;
		if (channelId != null) {
			channel = event.getJDA().getChannelById(MessageChannelUnion.class ,channelId);
			if (channel == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Channel mit dieser ID nicht gefunden.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}
		}

		channel.retrieveMessageById(messageId).queue(message -> {
			MessageReaction reaction = message.getReactions().stream()
					.filter(r -> r.getEmoji().getFormatted().equals(emoji)).findFirst().orElse(null);

			if (reaction == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Reaktion mit dem Emoji " + emoji
								+ " auf der Nachricht " + messagelink + " gefunden.", MessageUtil.EmbedType.INFO))
						.queue();
				return;
			}

			reaction.retrieveUsers().queue(users -> {
				Set<String> reactedUserIds = users.stream().map(User::getId).collect(Collectors.toSet());

				Guild guild = role.getGuild();

				guild.loadMembers().onSuccess(members -> {
					List<Member> missingMembers = members.stream().filter(member -> member.getRoles().contains(role))
							.filter(member -> !reactedUserIds.contains(member.getId())).collect(Collectors.toList());
					if (missingMembers.isEmpty()) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Alle Mitglieder der Rolle " + role.getAsMention() + " haben schon mit dem Emoji "
										+ emoji + " auf die Nachricht " + messagelink + " reagiert.",
								MessageUtil.EmbedType.INFO)).queue();
					} else {
						event.getHook()
								.editOriginalEmbeds(MessageUtil.buildEmbed(title,
										"Mitglieder der Rolle " + role.getAsMention() + ", die noch nicht mit " + emoji
												+ " auf die Nachricht " + messagelink + " reagiert haben:\n",
										MessageUtil.EmbedType.INFO))
								.queue();
						String pingstring = "";
						for (Member member : missingMembers) {
							pingstring += " " + member.getAsMention();
						}
						event.getChannel().sendMessage(pingstring).queue();
					}
				});
			});
		}, _ -> {
			event.getHook()
					.editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Nachricht mit dieser ID konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR))
					.queue();
		});

		}, "CheckreactsCommand-" + event.getUser().getId()).start();

	}
}
