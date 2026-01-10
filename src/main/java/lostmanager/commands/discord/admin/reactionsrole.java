package lostmanager.commands.discord.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class reactionsrole extends ListenerAdapter {

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("reactionsrole"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Reactions-Role";

			User userexecuted = new User(event.getUser().getId());
			if (!userexecuted.isAdmin()) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst Admin sein, um diesen Befehl ausführen zu können.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			OptionMapping messagelinkOption = event.getOption("messagelink");
			OptionMapping emojiOption = event.getOption("emoji");
			OptionMapping roleOption = event.getOption("role");

			if (messagelinkOption == null || emojiOption == null || roleOption == null) {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Die Parameter Message-Link, Emoji und Rolle sind pflicht.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			String messagelink = messagelinkOption.getAsString();
			String messageId = messagelink.split("/")[messagelink.split("/").length - 1];
			String channelId = messagelink.split("/")[messagelink.split("/").length - 2];
			String emoji = emojiOption.getAsString();
			Role role = roleOption.getAsRole();
			Guild guild = role.getGuild();

			MessageChannelUnion channel = null;
			if (channelId != null) {
				channel = event.getJDA().getChannelById(MessageChannelUnion.class, channelId);
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
					// Handle case where no users reacted
					if (users.isEmpty()) {
						event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Keine Benutzer haben mit dem Emoji " + emoji + " auf die Nachricht " + messagelink
										+ " reagiert.",
								MessageUtil.EmbedType.INFO)).queue();
						return;
					}

					// Use thread-safe collections
					List<String> alreadyHadRole = Collections.synchronizedList(new ArrayList<>());
					List<String> roleGiven = Collections.synchronizedList(new ArrayList<>());
					List<String> notInGuild = Collections.synchronizedList(new ArrayList<>());
					AtomicInteger processedCount = new AtomicInteger(0);
					int totalUsers = users.size();

					for (net.dv8tion.jda.api.entities.User user : users) {
						guild.retrieveMember(user).queue(member -> {
							if (member.getRoles().contains(role)) {
								alreadyHadRole.add(member.getAsMention());
								checkAndSendResponse(event, title, emoji, messagelink, role, alreadyHadRole,
										roleGiven, notInGuild, processedCount, totalUsers);
							} else {
								guild.addRoleToMember(member, role).queue(
										_ -> {
											roleGiven.add(member.getAsMention());
											checkAndSendResponse(event, title, emoji, messagelink, role, alreadyHadRole,
													roleGiven, notInGuild, processedCount, totalUsers);
										},
										_ -> {
											notInGuild.add(member.getAsMention());
											checkAndSendResponse(event, title, emoji, messagelink, role, alreadyHadRole,
													roleGiven, notInGuild, processedCount, totalUsers);
										});
							}
						}, _ -> {
							notInGuild.add(user.getAsMention());
							checkAndSendResponse(event, title, emoji, messagelink, role, alreadyHadRole, roleGiven,
									notInGuild, processedCount, totalUsers);
						});
					}
				});
			}, _ -> {
				event.getHook()
						.editOriginalEmbeds(MessageUtil.buildEmbed(title,
								"Nachricht mit dieser ID konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR))
						.queue();
			});

		}, "ReactionsroleCommand-" + event.getUser().getId()).start();

	}

	private void checkAndSendResponse(SlashCommandInteractionEvent event, String title, String emoji,
			String messagelink, Role role, List<String> alreadyHadRole, List<String> roleGiven,
			List<String> notInGuild, AtomicInteger processedCount, int totalUsers) {
		// Increment processed count and check if we're done
		int processed = processedCount.incrementAndGet();
		if (processed >= totalUsers) {
			StringBuilder response = new StringBuilder();
			response.append("Überprüfung der Reaktionen mit dem Emoji ").append(emoji).append(" auf die Nachricht ")
					.append(messagelink).append(" abgeschlossen.\n\n");
			response.append("**Rolle:** ").append(role.getAsMention()).append("\n\n");

			if (!roleGiven.isEmpty()) {
				response.append("**Rolle vergeben an (").append(roleGiven.size()).append("):**\n");
				for (String user : roleGiven) {
					response.append("- ").append(user).append("\n");
				}
				response.append("\n");
			}

			if (!alreadyHadRole.isEmpty()) {
				response.append("**Hatte Rolle bereits (").append(alreadyHadRole.size()).append("):**\n");
				for (String user : alreadyHadRole) {
					response.append("- ").append(user).append("\n");
				}
				response.append("\n");
			}

			if (!notInGuild.isEmpty()) {
				response.append("**Nicht im Server oder Fehler (").append(notInGuild.size()).append("):**\n");
				for (String user : notInGuild) {
					response.append("- ").append(user).append("\n");
				}
			}

			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, response.toString(), MessageUtil.EmbedType.SUCCESS)).queue();
		}
	}
}
