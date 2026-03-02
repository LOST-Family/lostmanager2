package lostmanager.util;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
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
}
