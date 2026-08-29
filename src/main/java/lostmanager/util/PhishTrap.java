package lostmanager.util;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.TimeUtil;

/**
 * Honeypot against phished accounts.
 *
 * A hijacked account posts its advertisement into every channel it can reach,
 * so one channel nobody has a reason to write in is enough to recognise it: any
 * message in the trap channel is treated as proof that the account is no longer
 * under the owner's control.
 *
 * The reaction is deliberately mild for the player and hard for the spam - the
 * account is muted for an hour (long enough for the owner to notice and for
 * staff to react, short enough to not punish the victim) and everything it
 * wrote in the last 30 minutes is deleted server-wide.
 */
public class PhishTrap extends ListenerAdapter {

	private static final String TRAP_CHANNEL_ID = resolveTrapChannelId();

	/** How long the account is muted after it walked into the trap. */
	private static final Duration TIMEOUT_DURATION = Duration.ofHours(1);

	/** How far back the message sweep reaches. */
	private static final Duration DELETE_WINDOW = Duration.ofMinutes(30);

	/**
	 * Upper bound of messages read per channel. A channel that saw more than this
	 * within the delete window is busier than any human spammer can outrun, and the
	 * cap keeps a single trigger from paging through a whole channel history.
	 */
	private static final int HISTORY_SCAN_LIMIT = 500;

	/**
	 * A spammer usually fires several messages before the mute lands, and every one
	 * of them would start its own server-wide sweep. Within this window only the
	 * first message sweeps; the later ones are simply deleted.
	 */
	private static final long RESWEEP_COOLDOWN_MILLIS = 60_000;

	private static final Map<Long, Long> lastTriggered = new ConcurrentHashMap<>();

	private static String resolveTrapChannelId() {
		String fromEnv = System.getenv("PHISH_TRAP_CHANNEL_ID");
		return (fromEnv != null && !fromEnv.isEmpty()) ? fromEnv : "1543271724051595396";
	}

	@SuppressWarnings("null")
	@Override
	public void onMessageReceived(MessageReceivedEvent event) {
		if (!event.isFromGuild() || !isTrapChannel(event.getChannel())) {
			return;
		}

		if (event.getAuthor().isBot() || event.getAuthor().isSystem() || event.isWebhookMessage()) {
			return;
		}

		long userId = event.getAuthor().getIdLong();
		if (!claimTrigger(userId)) {
			event.getMessage().delete().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
			return;
		}

		Guild guild = event.getGuild();
		System.out.println("Phishing trap triggered by " + event.getAuthor().getName() + " (" + userId + ")");

		Member member = event.getMember();
		if (member != null) {
			timeout(member);
		} else {
			guild.retrieveMemberById(userId).queue(this::timeout,
					_ -> System.err.println("Phishing trap: member " + userId + " could not be retrieved for timeout"));
		}

		purgeRecentMessages(guild, userId);
	}

	/**
	 * Threads count as part of the trap - otherwise a thread underneath the trap
	 * channel would be a free spot to post in.
	 */
	private boolean isTrapChannel(MessageChannelUnion channel) {
		if (channel.getId().equals(TRAP_CHANNEL_ID)) {
			return true;
		}

		if (channel instanceof ThreadChannel thread) {
			return thread.getParentChannel().getId().equals(TRAP_CHANNEL_ID);
		}

		return false;
	}

	/**
	 * @return true if this message is the one that owns the sweep, false if the
	 *         same account already triggered one moments ago.
	 */
	private boolean claimTrigger(long userId) {
		long now = System.currentTimeMillis();
		lastTriggered.entrySet().removeIf(entry -> now - entry.getValue() > RESWEEP_COOLDOWN_MILLIS);
		return lastTriggered.putIfAbsent(userId, now) == null;
	}

	@SuppressWarnings("null")
	private void timeout(Member member) {
		Member self = member.getGuild().getSelfMember();
		String name = member.getUser().getName();

		if (!self.hasPermission(Permission.MODERATE_MEMBERS)) {
			System.err.println("Phishing trap: missing MODERATE_MEMBERS permission, " + name + " was not muted");
			return;
		}

		// Members above the bot and administrators cannot be muted at all; their
		// messages are still deleted below.
		if (!self.canInteract(member)) {
			System.err.println("Phishing trap: " + name + " outranks the bot and was not muted");
			return;
		}

		member.timeoutFor(TIMEOUT_DURATION).reason("Phishing-Schutz: Nachricht im Trap-Channel")
				.queue(_ -> System.out.println("Phishing trap: muted " + name + " for " + TIMEOUT_DURATION.toHours()
						+ "h"), error -> System.err
								.println("Phishing trap: muting " + name + " failed - " + error.getMessage()));
	}

	/**
	 * Deletes everything the account wrote server-wide within the delete window.
	 * Each channel is read in parallel so the spam disappears in one go instead of
	 * channel by channel.
	 */
	@SuppressWarnings("null")
	private void purgeRecentMessages(Guild guild, long userId) {
		OffsetDateTime cutoff = OffsetDateTime.now().minus(DELETE_WINDOW);
		long cutoffSnowflake = TimeUtil.getDiscordTimestamp(System.currentTimeMillis() - DELETE_WINDOW.toMillis());
		Member self = guild.getSelfMember();
		AtomicInteger deleted = new AtomicInteger();
		List<CompletableFuture<Void>> sweeps = new ArrayList<>();
		int unreachable = 0;

		for (GuildMessageChannel channel : collectMessageChannels(guild)) {
			if (!self.hasPermission(channel, Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY,
					Permission.MESSAGE_MANAGE)) {
				unreachable++;
				continue;
			}

			// The newest message id is known from the gateway, so channels that went
			// quiet before the window opened are skipped without a single request.
			long latestMessageId = channel.getLatestMessageIdLong();
			if (latestMessageId != 0 && latestMessageId < cutoffSnowflake) {
				continue;
			}

			sweeps.add(channel.getIterableHistory()
					.takeUntilAsync(HISTORY_SCAN_LIMIT, message -> message.getTimeCreated().isBefore(cutoff))
					.thenAccept(history -> {
						List<Message> spam = history.stream()
								.filter(message -> message.getAuthor().getIdLong() == userId).toList();
						if (spam.isEmpty()) {
							return;
						}

						channel.purgeMessages(spam);
						deleted.addAndGet(spam.size());
					}).exceptionally(error -> {
						System.err.println("Phishing trap: reading #" + channel.getName() + " failed - "
								+ error.getMessage());
						return null;
					}));
		}

		// Channels the bot may not clean are worth naming: without them the sweep
		// silently leaves spam behind.
		String skipped = unreachable == 0 ? ""
				: " (" + unreachable + " channel(s) skipped, missing view/history/manage permission)";

		CompletableFuture.allOf(sweeps.toArray(CompletableFuture[]::new)).whenComplete((_, _) -> System.out
				.println("Phishing trap: deleted " + deleted.get() + " message(s) of " + userId + " from the last "
						+ DELETE_WINDOW.toMinutes() + " minutes" + skipped));
	}

	/**
	 * Every channel of the guild that can hold messages, threads included. Archived
	 * threads are not cached and therefore out of reach, but nothing is archived
	 * within half an hour of the last message in it.
	 */
	private List<GuildMessageChannel> collectMessageChannels(Guild guild) {
		Map<Long, GuildMessageChannel> channels = new LinkedHashMap<>();

		for (GuildChannel channel : guild.getChannels()) {
			if (channel instanceof GuildMessageChannel messageChannel) {
				channels.putIfAbsent(messageChannel.getIdLong(), messageChannel);
			}
		}

		for (ThreadChannel thread : guild.getThreadChannelCache()) {
			channels.putIfAbsent(thread.getIdLong(), thread);
		}

		return new ArrayList<>(channels.values());
	}
}
