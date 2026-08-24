package lostmanager.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

public class SeasonUtil {

	/**
	 * Returns the end of the current season (1st of next month, midnight UTC).
	 * 
	 * @return Timestamp of the season end time
	 */
	public static Timestamp fetchSeasonEndTime() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime endOfSeason = now.withDayOfMonth(1).plusMonths(1)
				.withHour(0).withMinute(0).withSecond(0).withNano(0);
		return Timestamp.from(endOfSeason.toInstant());
	}

	/**
	 * Returns the start of the current season (1st of current month, midnight UTC).
	 * 
	 * @return Timestamp of the season start time
	 */
	public static Timestamp fetchSeasonStartTime() {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		OffsetDateTime startOfMonth = now.withDayOfMonth(1)
				.withHour(0).withMinute(0).withSecond(0).withNano(0);
		return Timestamp.from(startOfMonth.toInstant());
	}

	/**
	 * Grace period after a season boundary in which an event still belongs to the
	 * season that just ended.
	 *
	 * A season end event with duration 0 fires exactly at the boundary, and at that
	 * moment the calendar month has already rolled over. Without this the check
	 * would measure the brand new season, in which nobody has played yet.
	 */
	private static final long SEASON_ROLLOVER_GRACE_MS = 12 * 60 * 60 * 1000L;

	/** The season a season end event fires for at the given moment. */
	public static YearMonth evaluatedSeason(long nowMillis) {
		OffsetDateTime now = Instant.ofEpochMilli(nowMillis).atOffset(ZoneOffset.UTC);
		OffsetDateTime monthStart = now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
		if (nowMillis - monthStart.toInstant().toEpochMilli() < SEASON_ROLLOVER_GRACE_MS) {
			return YearMonth.from(now).minusMonths(1);
		}
		return YearMonth.from(now);
	}

	/** First moment of the given season. */
	public static Timestamp seasonStart(YearMonth season) {
		return Timestamp.from(season.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
	}

	/** First moment after the given season. */
	public static Timestamp seasonEnd(YearMonth season) {
		return Timestamp.from(season.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC));
	}
}
