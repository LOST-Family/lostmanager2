package lostmanager.util;

/**
 * One place for turning whatever a user, the database or the API hands us into
 * the canonical clan tag form: a leading {@code #} followed by upper case
 * characters of Supercell's tag alphabet.
 *
 * Every command used to normalise on its own - or not at all. A listening event
 * created with the tag typed as {@code 2R8V8LYV9} went into the database
 * verbatim, and because the tag is only URL encoded before the request the
 * missing {@code #} produced no {@code %23}: every CoC API call for that clan
 * answered 404. A 404 on the league group endpoint legitimately means "no CWL
 * running", so the event sat there as "waiting for an active CWL" forever
 * without a single log line.
 */
public final class ClanTag {

	/** Supercell only mints tags from these characters. */
	private static final String ALPHABET = "0289PYLQGRJCUV";

	private ClanTag() {
	}

	/**
	 * Lenient normalisation for values that are already known to be tags, e.g. what
	 * the API returns or what is stored in the database. Only ever adds the
	 * {@code #}, upper cases and maps the letter O onto the digit zero, so it cannot
	 * turn one valid tag into another.
	 *
	 * @return the normalised tag, or null for null and blank input.
	 */
	public static String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String tag = raw.trim().toUpperCase().replace('O', '0');
		if (tag.isEmpty()) {
			return null;
		}
		return tag.startsWith("#") ? tag : "#" + tag;
	}

	/** @return true if the tag is a {@code #} plus 3 to 12 characters of the tag alphabet. */
	public static boolean isValid(String tag) {
		if (tag == null || tag.length() < 4 || tag.length() > 13 || tag.charAt(0) != '#') {
			return false;
		}
		for (int i = 1; i < tag.length(); i++) {
			if (ALPHABET.indexOf(tag.charAt(i)) < 0) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Strict parsing for user input. Accepts a bare tag, a tag with {@code #} and
	 * the {@code Name (#TAG)} label the autocomplete displays - Discord submits
	 * whatever was typed when the user does not pick a suggestion, and that label
	 * has landed in the database as a clan tag before.
	 *
	 * @return the canonical tag, or null if nothing tag shaped could be found.
	 */
	public static String parse(String raw) {
		if (raw == null) {
			return null;
		}
		String input = raw.trim();
		if (input.isEmpty()) {
			return null;
		}

		String direct = normalize(input);
		if (isValid(direct)) {
			return direct;
		}

		// "LOST 4 (#2LU2V2LPU)" - take the last parenthesised group
		int close = input.lastIndexOf(')');
		int open = close > 0 ? input.lastIndexOf('(', close) : -1;
		if (open >= 0) {
			String inner = normalize(input.substring(open + 1, close));
			if (isValid(inner)) {
				return inner;
			}
		}
		return null;
	}
}
