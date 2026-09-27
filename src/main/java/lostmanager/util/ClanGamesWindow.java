package lostmanager.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * The clan games run on fixed dates every month: from the 22nd 07:00 until the
 * 28th 12:00. The times are in Europe/Berlin because that is what the scheduled
 * snapshot jobs in {@code Bot} have always used - all existing snapshots in
 * achievement_data sit on those instants.
 *
 * A window is identified by its key (e.g. "2026-06") instead of by an exact
 * timestamp. Baseline snapshots are matched against the whole window rather than
 * against one instant, because a snapshot is often not taken exactly when the
 * window opened: the bot may have been restarted, or a member may have joined
 * the clan while the games were already running.
 */
public class ClanGamesWindow {

	private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

	private final long startMillis;
	private final long endMillis;
	private final String key;

	private ClanGamesWindow(long startMillis, long endMillis, String key) {
		this.startMillis = startMillis;
		this.endMillis = endMillis;
		this.key = key;
	}

	/** The window of the given month. */
	public static ClanGamesWindow forMonth(YearMonth yearMonth) {
		long start = LocalDateTime.of(yearMonth.getYear(), yearMonth.getMonth(), 22, 7, 0)
				.atZone(ZONE).toInstant().toEpochMilli();
		long end = LocalDateTime.of(yearMonth.getYear(), yearMonth.getMonth(), 28, 12, 0)
				.atZone(ZONE).toInstant().toEpochMilli();
		return new ClanGamesWindow(start, end,
				String.format("%04d-%02d", yearMonth.getYear(), yearMonth.getMonthValue()));
	}

	/** The window that is currently running, or null when between two windows. */
	public static ClanGamesWindow current(long nowMillis) {
		ClanGamesWindow window = forMonth(YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(ZONE)));
		return window.contains(nowMillis) ? window : null;
	}

	/** The window that is currently running, otherwise the next upcoming one. */
	public static ClanGamesWindow currentOrNext(long nowMillis) {
		YearMonth month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(ZONE));
		ClanGamesWindow window = forMonth(month);
		return nowMillis < window.endMillis ? window : forMonth(month.plusMonths(1));
	}

	/**
	 * The window whose results are the most recent ones - the running window while
	 * the games are on, otherwise the one that ended last. This is the window a
	 * clan games event fires for.
	 */
	public static ClanGamesWindow currentOrPrevious(long nowMillis) {
		YearMonth month = YearMonth.from(Instant.ofEpochMilli(nowMillis).atZone(ZONE));
		ClanGamesWindow window = forMonth(month);
		return nowMillis >= window.startMillis ? window : forMonth(month.minusMonths(1));
	}

	public boolean contains(long millis) {
		return millis >= startMillis && millis < endMillis;
	}

	public long getStartMillis() {
		return startMillis;
	}

	public long getEndMillis() {
		return endMillis;
	}

	/** Window identifier such as "2026-06". */
	public String getKey() {
		return key;
	}

	public Timestamp getStartTimestamp() {
		return new Timestamp(startMillis);
	}

	public Timestamp getEndTimestamp() {
		return new Timestamp(endMillis);
	}

	/**
	 * Earliest timestamp that still counts as a baseline snapshot for this window:
	 * the end of the previous window.
	 *
	 * "Games Champion" only grows while clan games are running, so every snapshot
	 * taken between the end of the previous games and the start of these ones
	 * holds exactly the starting value. The end snapshot of the previous window is
	 * therefore a valid baseline on its own - and it is what saves the window when
	 * the start job fails.
	 *
	 * This used to be a 12 hour lookback. On 2026-09-22 the start job ran 19 times
	 * in parallel (leaked scheduler pools, see Bot.restartAllEvents), the CoC API
	 * answered with HTTP 429, and only 209 of about 1800 players got a baseline on
	 * time. The rest was backfilled two hours later and counted as "taken too
	 * late" - unrated, so about 1600 members dropped out of the clan games check
	 * while the few with a punctual baseline could still be punished. For all 209
	 * punctual players the previous end snapshot was identical to the start value,
	 * which is the evidence this rule rests on.
	 */
	public Timestamp getBaselineLookupStart() {
		return new Timestamp(previous().endMillis);
	}

	/** The window of the month before this one. */
	private ClanGamesWindow previous() {
		return forMonth(YearMonth.parse(key).minusMonths(1));
	}

	/**
	 * A baseline taken after this point misses part of the window, so the measured
	 * points would be too low. Such members are reported but never punished.
	 */
	public Timestamp getBaselineLateAfter() {
		return new Timestamp(startMillis + 60 * 60 * 1000L);
	}

	@Override
	public String toString() {
		return "ClanGamesWindow[" + key + "]";
	}
}
