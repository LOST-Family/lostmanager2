package lostmanager.commands.discord.admin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lostmanager.datawrapper.User;
import lostmanager.util.LangeListe;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class reactionsrole extends ListenerAdapter {

	/** Obergrenze fürs Blättern, wie in /checkreacts. */
	private static final int REAKTIONEN_MAX = 5000;

	@SuppressWarnings("null")
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
				channel = MessageUtil.getChannelById(channelId);
				if (channel == null) {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Channel mit dieser ID nicht gefunden.", MessageUtil.EmbedType.ERROR)).queue();
					return;
				}
			}

			channel.retrieveMessageById(messageId).queue(message -> {
				MessageReaction reaction = message.getReactions().stream()
						.filter(r -> {
							net.dv8tion.jda.api.entities.emoji.Emoji e = r.getEmoji();
							String searchEmoji = emoji.trim();
							
							// Direct matches
							if (e.getFormatted().equalsIgnoreCase(searchEmoji) || e.getName().equalsIgnoreCase(searchEmoji)) {
								return true;
							}
							
							// Custom Emoji loose matching
							if (e.getType() == net.dv8tion.jda.api.entities.emoji.Emoji.Type.CUSTOM) {
								String id = ((net.dv8tion.jda.api.entities.emoji.CustomEmoji) e).getId();
								if (searchEmoji.contains(id)) return true;
								
								// Name-based fallback if IDs don't match (e.g., cross-server emoji with same name)
								String parsedName = searchEmoji;
								if (searchEmoji.startsWith("<") && searchEmoji.endsWith(">")) {
									String[] parts = searchEmoji.split(":");
									if (parts.length >= 2) {
										parsedName = parts[1]; 
									}
								}
								if (e.getName().equalsIgnoreCase(parsedName)) return true;
							}
							
							// Unicode Emoji loose matching
							String eNameClean = e.getName().replace("\uFE0F", "");
							String emojiClean = searchEmoji.replace("\uFE0F", "");
							return eNameClean.equalsIgnoreCase(emojiClean);
						}).findFirst().orElse(null);

				if (reaction == null) {
					event.getHook()
							.editOriginalEmbeds(MessageUtil.buildEmbed(title, "Keine Reaktion mit dem Emoji " + emoji
									+ " auf der Nachricht " + messagelink + " gefunden.", MessageUtil.EmbedType.INFO))
							.queue();
					return;
				}

				// queue() holt nur die erste Seite (100 Nutzer) – wer danach reagiert hat, bekam
				// die Rolle still nicht. takeAsync blättert weiter (wie /checkreacts).
				reaction.retrieveUsers().takeAsync(REAKTIONEN_MAX).thenAccept(users -> {
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
				}).exceptionally(fehler -> {
					System.err.println("reactionsrole: Reaktionen laden gescheitert: " + fehler);
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Fehler beim Laden der Reaktionen.", MessageUtil.EmbedType.ERROR)).queue();
					return null;
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
			String response = baueAntwort(emoji, messagelink, role.getAsMention(), roleGiven, alreadyHadRole,
					notInGuild);

			event.getHook().editOriginalEmbeds(
					MessageUtil.buildEmbed(title, response, MessageUtil.EmbedType.SUCCESS)).queue();
		}
	}

	/**
	 * Die Abschlussmeldung. Jede Liste hört mit „… und N weitere“ auf, bevor die
	 * Beschreibung die 4096 Zeichen eines Embeds erreicht – ab etwa 160 Reagierenden
	 * warf JDA sonst beim Bau, und die Antwort blieb auf „denkt nach“ stehen, obwohl die
	 * Rollen vergeben waren. Die Zahl in der Überschrift bleibt die echte.
	 */
	static String baueAntwort(String emoji, String messagelink, String rolle, List<String> roleGiven,
			List<String> alreadyHadRole, List<String> notInGuild) {
		StringBuilder response = new StringBuilder();
		response.append("Überprüfung der Reaktionen mit dem Emoji ").append(emoji).append(" auf die Nachricht ")
				.append(messagelink).append(" abgeschlossen.\n\n");
		response.append("**Rolle:** ").append(rolle).append("\n\n");
		abschnitt(response, "Rolle vergeben an", roleGiven);
		abschnitt(response, "Hatte Rolle bereits", alreadyHadRole);
		abschnitt(response, "Nicht im Server oder Fehler", notInGuild);
		return response.toString();
	}

	private static void abschnitt(StringBuilder response, String titel, List<String> nutzer) {
		if (nutzer.isEmpty()) {
			return;
		}
		response.append("**").append(titel).append(" (").append(nutzer.size()).append("):**\n");
		List<String> zeilen = new ArrayList<>();
		synchronized (nutzer) {
			for (String user : nutzer) {
				zeilen.add("- " + user);
			}
		}
		LangeListe.anhaengen(response, zeilen, LangeListe.BESCHREIBUNG_WEICH);
		response.append("\n");
	}
}
