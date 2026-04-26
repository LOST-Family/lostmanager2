package lostmanager.commands.discord.admin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lostmanager.datawrapper.User;
import lostmanager.util.MessageUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class deletemessages extends ListenerAdapter {

	private static final int MESSAGE_ID_SCAN_STEP = 200;
	private static final int MESSAGE_ID_SCAN_MAX = 5000;

	@SuppressWarnings("null")
	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (!event.getName().equals("deletemessages"))
			return;
		event.deferReply().queue();

		new Thread(() -> {
			String title = "Delete-Messages";

			User userexecuted = new User(event.getUser().getId());
			if (!userexecuted.isAdmin()) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Du musst Admin sein, um diesen Befehl ausführen zu können.", MessageUtil.EmbedType.ERROR))
						.queue();
				return;
			}

			OptionMapping amountOption = event.getOption("amount");
			OptionMapping messageIdOption = event.getOption("messageid");
			MessageChannelUnion channel = event.getChannel();
			String interactionResponseMessageId = resolveInteractionResponseMessageId(event);

			boolean hasAmount = amountOption != null;
			boolean hasMessageId = messageIdOption != null;

			if (hasAmount == hasMessageId) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Gib genau einen Parameter an: amount oder messageid.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			if (hasAmount) {
				int amount = amountOption.getAsInt();
				int fetchAmount = amount + 1;

				channel.getIterableHistory().takeAsync(fetchAmount).thenAccept(messages -> {
					List<Message> messagesToDelete = new ArrayList<>(messages);
					messagesToDelete = skipFirstInteractionMessage(messagesToDelete,
							interactionResponseMessageId, null);

					if (messagesToDelete.size() > amount) {
						messagesToDelete = new ArrayList<>(messagesToDelete.subList(0, amount));
					}

					deleteMessages(channel, messagesToDelete);
					sendSuccess(event, title, messagesToDelete.size());
				}).exceptionally(throwable -> {
					event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
							"Beim Laden der Nachrichten ist ein Fehler aufgetreten.",
							MessageUtil.EmbedType.ERROR)).queue();
					return null;
				});
				return;
			}

			String messageId = parseMessageId(messageIdOption.getAsString());
			if (messageId == null) {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Ungültige Message-ID.", MessageUtil.EmbedType.ERROR)).queue();
				return;
			}

			channel.retrieveMessageById(messageId).queue(_ -> {
				collectMessagesUntilTarget(channel, messageId, MESSAGE_ID_SCAN_STEP,
						messagesToDelete -> {
							List<Message> filteredMessages = skipFirstInteractionMessage(messagesToDelete,
									interactionResponseMessageId, messageId);
							deleteMessages(channel, filteredMessages);
							sendSuccess(event, title, filteredMessages.size());
						},
						errorMessage -> event.getHook().editOriginalEmbeds(
								MessageUtil.buildEmbed(title, errorMessage, MessageUtil.EmbedType.ERROR)).queue());
			}, _ -> {
				event.getHook().editOriginalEmbeds(MessageUtil.buildEmbed(title,
						"Nachricht mit dieser ID konnte nicht gefunden werden.", MessageUtil.EmbedType.ERROR)).queue();
			});

		}, "DeletemessagesCommand-" + event.getUser().getId()).start();

	}

	private String resolveInteractionResponseMessageId(SlashCommandInteractionEvent event) {
		try {
			Message interactionMessage = event.getHook().retrieveOriginal().complete();
			if (interactionMessage != null) {
				return interactionMessage.getId();
			}
		} catch (Exception ignored) {
			// If this cannot be resolved, deletion continues without skipping.
		}

		return null;
	}

	private String parseMessageId(String input) {
		if (input == null) {
			return null;
		}

		String value = input.trim();
		if (value.isEmpty()) {
			return null;
		}

		if (value.startsWith("<") && value.endsWith(">")) {
			value = value.substring(1, value.length() - 1).trim();
		}

		if (value.contains("/")) {
			String[] parts = value.split("/");
			for (int i = parts.length - 1; i >= 0; i--) {
				if (parts[i] != null && !parts[i].isBlank()) {
					value = parts[i].trim();
					break;
				}
			}
		}

		int queryIndex = value.indexOf('?');
		if (queryIndex >= 0) {
			value = value.substring(0, queryIndex);
		}

		int fragmentIndex = value.indexOf('#');
		if (fragmentIndex >= 0) {
			value = value.substring(0, fragmentIndex);
		}

		if (value.matches("\\d+")) {
			return value;
		}

		Matcher matcher = Pattern.compile("(\\d{10,})").matcher(value);
		if (matcher.find()) {
			return matcher.group(1);
		}

		return null;
	}

	private void collectMessagesUntilTarget(MessageChannelUnion channel, String targetMessageId, int limit,
			Consumer<List<Message>> onSuccess, Consumer<String> onFailure) {
		int requestedLimit = Math.min(limit, MESSAGE_ID_SCAN_MAX);

		channel.getIterableHistory().takeAsync(requestedLimit).thenAccept(messages -> {
			List<Message> fetchedMessages = new ArrayList<>(messages);
			int targetIndex = -1;

			for (int i = 0; i < fetchedMessages.size(); i++) {
				if (fetchedMessages.get(i).getId().equals(targetMessageId)) {
					targetIndex = i;
					break;
				}
			}

			if (targetIndex >= 0) {
				onSuccess.accept(new ArrayList<>(fetchedMessages.subList(0, targetIndex + 1)));
				return;
			}

			if (fetchedMessages.size() < requestedLimit) {
				onFailure.accept("Die Nachricht wurde im Channel-Verlauf nicht gefunden.");
				return;
			}

			if (requestedLimit >= MESSAGE_ID_SCAN_MAX) {
				onFailure.accept("Die Nachricht liegt zu weit in der Vergangenheit (Scan-Limit erreicht).");
				return;
			}

			collectMessagesUntilTarget(channel, targetMessageId, requestedLimit + MESSAGE_ID_SCAN_STEP, onSuccess,
					onFailure);
		}).exceptionally(throwable -> {
			String detail = throwable.getMessage();
			if ((detail == null || detail.isBlank()) && throwable.getCause() != null) {
				detail = throwable.getCause().getMessage();
			}
			if (detail == null || detail.isBlank()) {
				detail = throwable.getClass().getSimpleName();
			}
			onFailure.accept("Beim Laden des Channel-Verlaufs ist ein Fehler aufgetreten: " + detail);
			return null;
		});
	}

	private List<Message> skipFirstInteractionMessage(List<Message> messages,
			String interactionResponseMessageId, String targetMessageId) {
		if (messages.isEmpty()) {
			return messages;
		}

		if (interactionResponseMessageId == null) {
			return messages;
		}

		Message firstMessage = messages.get(0);
		boolean isInteractionMessage = firstMessage.getId().equals(interactionResponseMessageId);
		boolean isTargetMessage = targetMessageId != null && firstMessage.getId().equals(targetMessageId);

		if (isInteractionMessage && !isTargetMessage) {
			return new ArrayList<>(messages.subList(1, messages.size()));
		}

		return messages;
	}

	private void deleteMessages(MessageChannelUnion channel, List<Message> messages) {
		List<Message> recent = new ArrayList<>();
		List<Message> old = new ArrayList<>();

		for (Message msg : messages) {
			if (msg.getTimeCreated().isAfter(OffsetDateTime.now().minusDays(14))) {
				recent.add(msg);
			} else {
				old.add(msg);
			}
		}

		// Bulk-Delete für bis zu 100 Messages unter 14 Tage
		for (int i = 0; i < recent.size(); i += 100) {
			int end = Math.min(i + 100, recent.size());
			List<? extends Message> batch = new ArrayList<>(recent.subList(i, end));
			channel.purgeMessages(batch);
		}

		// Einzeln löschen für >14 Tage
		for (Message msg : old) {
			msg.delete().queue();
		}
	}

	private void sendSuccess(SlashCommandInteractionEvent event, String title, int deletedCount) {
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		event.getHook()
				.editOriginalEmbeds(MessageUtil.buildEmbed(title,
						deletedCount + " Nachrichten wurden gelöscht. Diese Nachricht wird auch wieder gelöscht.",
						MessageUtil.EmbedType.SUCCESS))
				.queue(message -> {
					message.delete().queueAfter(10, TimeUnit.SECONDS);
				});
	}
}
