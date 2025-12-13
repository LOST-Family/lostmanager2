package lostmanager;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import commands.coc.kickpoints.clanconfig;
import commands.coc.kickpoints.kpadd;
import commands.coc.kickpoints.kpaddreason;
import commands.coc.kickpoints.kpclan;
import commands.coc.kickpoints.kpedit;
import commands.coc.kickpoints.kpeditreason;
import commands.coc.kickpoints.kpinfo;
import commands.coc.kickpoints.kpmember;
import commands.coc.kickpoints.kpremove;
import commands.coc.kickpoints.kpremovereason;
import commands.coc.links.link;
import commands.coc.links.playerinfo;
import commands.coc.links.relink;
import commands.coc.links.unlink;
import commands.coc.links.verify;
import commands.coc.memberlist.addmember;
import commands.coc.memberlist.cwlmemberstatus;
import commands.coc.memberlist.editmember;
import commands.coc.memberlist.listmembers;
import commands.coc.memberlist.memberstatus;
import commands.coc.memberlist.removemember;
import commands.coc.memberlist.transfermember;
import commands.coc.util.checkroles;
import commands.coc.util.cwdonator;
import commands.coc.util.listeningevent;
import commands.coc.util.raidping;
import commands.coc.util.setnick;
import commands.coc.util.wins;
import commands.discord.admin.deletemessages;
import commands.discord.admin.reactionsrole;
import commands.discord.admin.restart;
import commands.discord.util.checkreacts;
import commands.discord.util.lmagent;
import commands.discord.util.teamcheck;
import datawrapper.AchievementData.Type;
import dbutil.DBUtil;
import datawrapper.Clan;
import datawrapper.ListeningEvent;
import datawrapper.Player;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Bot extends ListenerAdapter {

	private static ScheduledExecutorService schedulernames = Executors.newSingleThreadScheduledExecutor();
	private static ScheduledExecutorService schedulertasks = Executors.newSingleThreadScheduledExecutor();

	private static JDA jda;
	public static String VERSION;
	public static String guild_id;
	public static String api_key;
	public static String url;
	public static String user;
	public static String password;
	public static String verified_roleid;
	public static String exmember_roleid;

	public static void main(String[] args) throws Exception {
		VERSION = "2.1.0";
		guild_id = System.getenv("DISCORD_GUILD_ID");
		api_key = System.getenv("LOST_MANAGER_API_KEY");
		url = System.getenv("LOST_MANAGER_DB_URL");
		user = System.getenv("LOST_MANAGER_DB_USER");
		password = System.getenv("LOST_MANAGER_DB_PASSWORD");
		verified_roleid = System.getenv("DISCORD_VERIFIED_ROLE_ID");
		exmember_roleid = System.getenv("DISCORD_EX_MEMBER_ROLE_ID");

		String token = System.getenv("LOST_MANAGER_TOKEN");

		if (dbutil.Connection.checkDB()) {
			System.out.println("Verbindung zur Datenbank funktioniert.");
		} else {
			System.out.println("Verbindung zur Datenbank fehlgeschlagen.");
		}

		dbutil.Connection.tablesExists();
		startNameUpdates();
		restartAllEvents();

		JDABuilder.createDefault(token).enableIntents(GatewayIntent.GUILD_MEMBERS)
				.setMemberCachePolicy(MemberCachePolicy.ALL).setChunkingFilter(ChunkingFilter.ALL)
				.setActivity(Activity.playing("mit deinen Kickpunkten")).addEventListeners(getListenerClassObjects())
				.build();

	}

	public static void registerCommands(JDA jda, String guildId) {
		Guild guild = jda.getGuildById(guildId);
		if (guild != null) {
			guild.updateCommands()
					.addCommands(Commands.slash("verify", "Verifiziere deinen Clash of Clans Account.")
							.addOption(OptionType.STRING, "tag", "Der Tag des Clash of Clans Accounts", true)
							.addOption(OptionType.STRING, "apitoken",
									"Der API-Token. Auffindbar in den Einstellungen im Spiel.", true),

							Commands.slash("link",
									"Verlinke einen Clash of Clans Account mit einem Discord User oder einer UserID.")
									.addOption(OptionType.STRING, "tag", "Der Tag des Clash of Clans Accounts", true)
									.addOption(OptionType.MENTIONABLE, "user",
											"Der User, mit dem der Account verlinkt werden soll.")
									.addOption(
											OptionType.STRING, "userid",
											"Die ID des Users, mit dem der Account verlinkt werden soll."),

							Commands.slash("relink", "Verlinke einen bereits verlinkten Clash of Clans Account neu.")
									.addOptions(new OptionData(OptionType.STRING, "tag",
											"Der Tag des Clash of Clans Accounts", true).setAutoComplete(true))
									.addOption(OptionType.MENTIONABLE, "user",
											"Der User, mit dem der Account verlinkt werden soll.")
									.addOption(
											OptionType.STRING, "userid",
											"Die ID des Users, mit dem der Account verlinkt werden soll."),

							Commands.slash("unlink", "Lösche eine Verlinkung eines Clash of Clans Accounts.")
									.addOptions(new OptionData(
											OptionType.STRING, "tag",
											"Der Spieler, wessen Verknüpfung entfernt werden soll", true)
											.setAutoComplete(true)),

							Commands.slash("restart", "Startet den Bot neu."),

							Commands.slash("addmember", "Füge einen Spieler zu einem Clan hinzu.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, zu welchem der Spieler hinzugefügt werden soll", true)
											.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, welcher hinzugefügt werden soll", true).setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "role",
											"Die Rolle, welche der Spieler bekommen soll", true).setAutoComplete(true)),

							Commands.slash("removemember", "Entferne einen Spieler aus seinem Clan.")
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, welcher entfernt werden soll", true).setAutoComplete(true)),

							Commands.slash("listmembers", "Liste aller Spieler in einem Clan.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, welcher ausgegeben werden soll.", true).setAutoComplete(true)),

							Commands.slash("editmember", "Ändere die Rolle eines Mitglieds.")
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, welcher bearbeitet werden soll.", true).setAutoComplete(true))
									.addOptions(
											new OptionData(
													OptionType.STRING, "role",
													"Die Rolle, welcher der Spieler sein soll.", true)
													.setAutoComplete(true)),
							Commands.slash("playerinfo",
									"Info eines Spielers. Bei Eingabe eines Parameters werden Infos über diesen Nutzer aufgelistet.")
									.addOptions(new OptionData(
											OptionType.MENTIONABLE, "user",
											"Der User, über welchem Informationen über verlinkte Accounts gesucht sind."))
									.addOptions(new OptionData(
											OptionType.STRING, "player",
											"Der Spieler, über welchem Informationen gesucht sind.")
											.setAutoComplete(true)),
							Commands.slash("memberstatus",
									"Status über einen Clan, welche Spieler keine Mitglieder sind und welche Mitglieder fehlen.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, welcher ausgegeben werden soll.", true).setAutoComplete(true))
									.addOptions(new OptionData(
											OptionType.STRING, "disable_rolecheck",
											"Überspringe die Rollenüberprüfung (nur 'true' als Wert)", false)
											.setAutoComplete(true)),

							Commands.slash("cwlmemberstatus",
									"Überprüfe, welche Mitglieder einer Rolle in einem bestimmten Clan sind.")
									.addOption(OptionType.ROLE, "team_role",
											"Die Rolle der Mitglieder, die überprüft werden sollen", true)
									.addOptions(new OptionData(OptionType.STRING, "cwl_clan_tag",
											"Der Clantag, des CWL-Clans", true).setAutoComplete(true)),

							Commands.slash("kpaddreason", "Erstelle einen vorgefertigten Kickpunktgrund.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, für welchen dieser erstellt wird.", true).setAutoComplete(true))
									.addOptions(
											new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true))
									.addOptions(new OptionData(OptionType.INTEGER, "amount",
											"Die Anzahl der Kickpunkte.", true)),

							Commands.slash("kpremovereason", "Lösche einen vorgefertigten Kickpunktgrund.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, für welchen dieser erstellt wird.", true).setAutoComplete(true))
									.addOptions(
											new OptionData(
													OptionType.STRING, "reason", "Der angezeigte Grund.", true)
													.setAutoComplete(true)),

							Commands.slash("kpeditreason",
									"Aktualisiere die Anzahl der Kickpunkte für eine Grund-Vorlage.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, für welchen dieser erstellt wird.", true).setAutoComplete(true))
									.addOptions(
											new OptionData(OptionType.STRING, "reason", "Der angezeigte Grund.", true)
													.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.INTEGER, "amount", "Die Anzahl.", true)),

							Commands.slash("kpadd", "Gebe einem Spieler Kickpunkte.")
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, welcher die Kickpunkte erhält.", true).setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "reason", "Die Grund-Vorlage.")
											.setAutoComplete(true)),

							Commands.slash("kpmember", "Zeige alle Kickpunkte eines Spielers an.")
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, welcher angezeigt werden soll.", true).setAutoComplete(true)),

							Commands.slash("kpremove", "Lösche einen Kickpunkt.")
									.addOptions(new OptionData(OptionType.INTEGER, "id",
											"Die ID des Kickpunkts. Ist unter /kpmember zu sehen.", true)),

							Commands.slash("kpedit", "Editiere einen Kickpunkt.")
									.addOptions(new OptionData(OptionType.INTEGER, "id",
											"Die ID des Kickpunkts. Ist unter /kpmember zu sehen.", true)),

							Commands.slash("kpinfo", "Infos über Kickpunkt-Gründe eines Clans.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Die Clan, welcher angezeigt werden soll.", true).setAutoComplete(true)),

							Commands.slash("kpclan", "Zeige die Kickpunktanzahlen aller Spieler in einem Clan.")
									.addOptions(new OptionData(
											OptionType.STRING, "clan", "Der Clan, welcher angezeigt werden soll.", true)
											.setAutoComplete(true)),

							Commands.slash("clanconfig", "Ändere Einstellungen an einem Clan.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, welcher bearbeitet werden soll.", true).setAutoComplete(true)),

							Commands.slash("cwdonator", "Zufällige Spendereinteilung für CWs.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, für welchen eingeteilt werden soll.", true)
											.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "exclude_leaders",
											"Leader und CoLeader ausschließen (nur 'true' als Wert)", false)
											.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "use_lists",
											"Listen-basierte Verteilung nutzen (nur 'true' als Wert)", false)
											.setAutoComplete(true)),

							Commands.slash("checkreacts",
									"Überprüfe die Reaktionen einer Nachricht auf Mitglieder einer Rolle.")
									.addOption(OptionType.ROLE, "role", "Die Rolle, die überprüft werden soll", true)
									.addOption(OptionType.STRING, "message_link",
											"Die Nachrichtenlink der Nachricht, die überprüft werden soll.", true)
									.addOption(
											OptionType.STRING, "emoji", "Der Emoji, nach dem überprüft werden soll.",
											true),

							Commands.slash("setnick",
									"Ändert deinen Nicknamen zu deinem in-Game Namen und optional einem benutzerdefinierten Alias.")
									.addOptions(new OptionData(OptionType.STRING, "my_player",
											"Spieler-Tag des Accounts, dessen Name als Nickname gesetzt werden soll.",
											true).setAutoComplete(true))
									.addOption(
											OptionType.STRING, "alias",
											"Alias, der an deinen Namen angehängt werden soll (z.B. Pixel | [alias])."),

							Commands.slash("deletemessages",
									"Löscht eine bestimmte Anzahl an Nachrichten im aktuellen Channel")
									.addOptions(new OptionData(
											OptionType.INTEGER, "amount",
											"Anzahl an Nachrichten, die gelöscht werden sollen.", true)),

							Commands.slash("reactionsrole",
									"Gebe allen Benutzern, die mit einem bestimmten Emoji reagiert haben, eine Rolle")
									.addOption(OptionType.STRING, "messagelink",
											"Link zur Nachricht, deren Reaktionen überprüft werden sollen", true)
									.addOption(OptionType.STRING, "emoji", "Der Emoji, nach dem überprüft werden soll",
											true)
									.addOption(OptionType.ROLE, "role",
											"Die Rolle, die den Benutzern gegeben werden soll", true),

							Commands.slash("raidping", "Pingt alle Mitglieder, die noch fehlende Raid Angriffe haben.")
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Clan, dessen Mitglieder einen Ping erhalten sollen", true)
											.setAutoComplete(true)),

							Commands.slash("transfermember", "Transferiere einen Spieler in einen anderen Clan.")
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, welcher transferiert werden soll", true)
											.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, zu welchem der Spieler hinzugefügt werden soll", true)
											.setAutoComplete(true)),

							Commands.slash("listeningevent", "Verwalte automatische Event-Listener für Clan-Events.")
									.addSubcommands(
											new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("add",
													"Füge ein neues Listening Event hinzu")
													.addOptions(new OptionData(OptionType.STRING, "clan",
															"Der Clan für das Event", true).setAutoComplete(true))
													.addOptions(new OptionData(OptionType.STRING, "type",
															"Event-Typ (cs, cw, cwlday, raid)", true)
															.addChoices(new Command.Choice("Clan Games", "cs"),
																	new Command.Choice("Clan War", "cw"),
																	new Command.Choice("CWL Day", "cwlday"),
																	new Command.Choice("Raid", "raid")))
													.addOptions(new OptionData(OptionType.STRING, "duration",
															"Dauer/Zeitpunkt (z.B. 1h, 24h, start, 0)", true)
															.setAutoComplete(true))
													.addOptions(new OptionData(OptionType.STRING, "actiontype",
															"Aktionstyp", true).setAutoComplete(true))
													.addOptions(new OptionData(
															OptionType.CHANNEL, "channel",
															"Discord Channel für Nachrichten", true))
													.addOptions(new OptionData(
															OptionType.STRING, "kickpoint_reason",
															"Kickpoint-Grund (erforderlich bei actiontype=kickpoint)",
															false).setAutoComplete(true)),
											new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("list",
													"Liste alle Listening Events auf")
													.addOptions(new OptionData(OptionType.STRING, "clan",
															"Filtere nach Clan (optional)", false)
															.setAutoComplete(true)),
											new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("remove",
													"Lösche ein Listening Event")
													.addOptions(new OptionData(OptionType.INTEGER, "id",
															"Die ID des zu löschenden Events", true))),

							Commands.slash("teamcheck", "Überprüfe die Teamverteilung der Mitglieder.")
									.addOption(OptionType.ROLE, "memberrole",
											"Die Rolle der Mitglieder, die überprüft werden sollen", true)
									.addOption(OptionType.ROLE, "team_role_1", "Die erste Teamrolle", true)
									.addOption(OptionType.ROLE, "memberrole_2",
											"Die zweite Rolle der Mitglieder (optional)", false)
									.addOption(OptionType.ROLE, "team_role_2", "Die zweite Teamrolle (optional)", false)
									.addOption(OptionType.ROLE, "team_role_3", "Die dritte Teamrolle (optional)", false)
									.addOption(OptionType.ROLE, "team_role_4", "Die vierte Teamrolle (optional)", false)
									.addOption(OptionType.ROLE, "team_role_5", "Die fünfte Teamrolle (optional)",
											false),

							Commands.slash("checkroles",
									"Überprüfe, ob Clan-Mitglieder die korrekten Discord-Rollen haben.")
									.addOptions(new OptionData(
											OptionType.STRING, "clan",
											"Der Clan, dessen Mitglieder überprüft werden sollen", true)
											.setAutoComplete(true)),

							Commands.slash("wins",
									"Zeige Wins-Statistiken für Spieler oder einen Clan in einer Season.")
									.addOptions(new OptionData(OptionType.STRING, "season",
											"Der Monat, für den die Wins angezeigt werden sollen", true)
											.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "player",
											"Der Spieler, für den die Wins angezeigt werden sollen", false)
											.setAutoComplete(true))
									.addOptions(new OptionData(OptionType.STRING, "clan",
											"Der Clan, für den die Wins angezeigt werden sollen", false)
											.setAutoComplete(true)),

							Commands.slash("lmagent", "Dummy command mit einem Prompt-Parameter.")
									.addOption(OptionType.STRING, "prompt", "Der Prompt als Text", true)

					).queue();
		}
	}

	private static Object[] getListenerClassObjects() {
		ArrayList<Object> classes = new ArrayList<>();

		classes.add(new Bot());
		classes.add(new verify());
		classes.add(new link());
		classes.add(new unlink());
		classes.add(new relink());
		classes.add(new restart());
		classes.add(new addmember());
		classes.add(new removemember());
		classes.add(new listmembers());
		classes.add(new editmember());
		classes.add(new playerinfo());
		classes.add(new memberstatus());
		classes.add(new cwlmemberstatus());
		classes.add(new kpaddreason());
		classes.add(new kpremovereason());
		classes.add(new kpeditreason());
		classes.add(new kpadd());
		classes.add(new kpmember());
		classes.add(new kpremove());
		classes.add(new kpedit());
		classes.add(new kpinfo());
		classes.add(new kpclan());
		classes.add(new clanconfig());
		classes.add(new cwdonator());
		classes.add(new checkreacts());
		classes.add(new lmagent());
		classes.add(new setnick());
		classes.add(new deletemessages());
		classes.add(new reactionsrole());
		classes.add(new raidping());
		classes.add(new transfermember());
		classes.add(new listeningevent());
		classes.add(new teamcheck());
		classes.add(new checkroles());
		classes.add(new wins());

		return classes.toArray();
	}

	@Override
	public void onReady(ReadyEvent event) {
		setJda(event.getJDA());
		registerCommands(event.getJDA(), guild_id);
	}

	@Override
	public void onShutdown(ShutdownEvent event) {
		stopScheduler();
	}

	public static void setJda(JDA instance) {
		jda = instance;
	}

	public static JDA getJda() {
		return jda;
	}

	public static void restartAllEvents() {
		// Run in a separate thread to avoid crashing the bot if there are errors
		new Thread(() -> {
			try {
				restartAllEventsInternal();
			} catch (Exception e) {
				System.err.println("Error in restartAllEvents: " + e.getMessage());
				e.printStackTrace();
			}
		}, "RestartAllEventsThread").start();
	}

	private static void restartAllEventsInternal() {
		schedulertasks.shutdown();
		schedulertasks = Executors.newSingleThreadScheduledExecutor();
		endClanGamesSavings();
		startClanGamesSavings();
		scheduleSeasonEndWinsSaving();
		scheduleSeasonStartWinsSaving();

		// Start unified event polling system that checks all events periodically
		startEventPolling();
	}

	/**
	 * Execute an event with retry logic and validation
	 * 
	 * @param le         The listening event to execute
	 * @param eventId    The event ID for logging
	 * @param maxRetries Maximum number of retry attempts
	 */
	private static void executeEventWithRetry(ListeningEvent le, Long eventId, int maxRetries) {
		int attempt = 0;
		boolean success = false;

		while (attempt <= maxRetries && !success) {
			try {
				System.out.println(
						"Executing event " + eventId + " (attempt " + (attempt + 1) + "/" + (maxRetries + 1) + ")");

				// Validate that the event should still fire
				if (!shouldEventFire(le)) {
					System.out.println("Event " + eventId + " validation failed - conditions no longer met, skipping");
					return;
				}

				// Execute the event
				le.fireEvent();

				// If we reach here, event fired successfully
				System.out.println("Event " + eventId + " executed successfully");
				success = true;

			} catch (Exception e) {
				System.err.println("Event " + eventId + " execution failed (attempt " + (attempt + 1) + "/"
						+ (maxRetries + 1) + "): " + e.getMessage());
				e.printStackTrace();

				// If not the last attempt, wait before retrying
				if (attempt < maxRetries) {
					long waitTime = (long) Math.pow(2, attempt) * 5000; // 5s, 10s, 20s
					System.err.println("Retrying event " + eventId + " in " + (waitTime / 1000) + " seconds...");
					try {
						Thread.sleep(waitTime);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						System.err.println("Event " + eventId + " retry interrupted");
						return;
					}
				} else {
					System.err.println("Event " + eventId + " failed after " + (maxRetries + 1) + " attempts");
				}
			}
			attempt++;
		}
	}

	/**
	 * Validate if an event should still fire by checking current state
	 * 
	 * @param le The listening event
	 * @return true if the event should fire, false otherwise
	 */
	private static boolean shouldEventFire(ListeningEvent le) {
		try {
			Clan clan = new Clan(le.getClanTag());

			// Check based on event type
			switch (le.getListeningType()) {
			case CS:
				// Clan Games events should fire regardless (they check historical data)
				return true;

			case CW:
				// Check if clan war is actually active
				Boolean cwActive = clan.isCWActive();
				if (cwActive == null || !cwActive) {
					System.out.println("CW event validation: No active clan war");
					return false;
				}
				return true;

			case CWLDAY:
				// Check if CWL is active
				Boolean cwlActive = clan.isCWLActive();
				if (cwlActive == null || !cwlActive) {
					System.out.println("CWL event validation: No active CWL");
					return false;
				}
				return true;

			case RAID:
				// Check if raid is active
				boolean raidActive = clan.RaidActive();
				if (!raidActive) {
					System.out.println("Raid event validation: No active raid");
					return false;
				}
				return true;

			case FIXTIMEINTERVAL:
				// Fixed time events should always fire
				return true;

			default:
				// Unknown types should fire (conservative approach)
				return true;
			}
		} catch (Exception e) {
			// If validation fails, log but allow event to fire (conservative approach)
			System.err.println("Event validation check failed: " + e.getMessage() + " - allowing event to fire");
			return true;
		}
	}

	/**
	 * Unified event polling system that checks all events periodically Checks every
	 * 2 minutes and schedules events that are within 5 minutes of firing This
	 * handles all event types uniformly including CW start triggers
	 */
	private static void startEventPolling() {
		// Track which events have been scheduled to avoid duplicate scheduling
		final java.util.Set<Long> scheduledEvents = java.util.concurrent.ConcurrentHashMap.newKeySet();
		// Track timestamps of scheduled events to allow cleanup of old ones
		final java.util.Map<Long, Long> scheduledEventTimestamps = new java.util.concurrent.ConcurrentHashMap<>();
		// Track which start events have been fired for each clan in this war to avoid
		// duplicates
		final java.util.Map<String, java.util.Set<Long>> firedStartEvents = new java.util.concurrent.ConcurrentHashMap<>();

		Runnable pollingTask = () -> {
			try {
				String sql = "SELECT id FROM listening_events";
				ArrayList<Long> ids = DBUtil.getArrayListFromSQL(sql, Long.class);

				long currentTime = System.currentTimeMillis();
				long schedulingThreshold = 5 * 60 * 1000; // 5 minutes in milliseconds
				long cleanupThreshold = 60 * 60 * 1000; // 1 hour - events scheduled this long ago can be cleaned up

				// Clean up old scheduled events (events whose fire time has passed by more than
				// 1 hour)
				java.util.Iterator<java.util.Map.Entry<Long, Long>> iterator = scheduledEventTimestamps.entrySet()
						.iterator();
				while (iterator.hasNext()) {
					java.util.Map.Entry<Long, Long> entry = iterator.next();
					Long eventId = entry.getKey();
					Long fireTime = entry.getValue();

					// If the event's fire time has passed by more than 1 hour, remove it
					if (fireTime != null && fireTime < (currentTime - cleanupThreshold)) {
						scheduledEvents.remove(eventId);
						iterator.remove();
					}
				}

				// First pass: Group start events by clan for batch processing
				// Map to group start events by clan tag
				java.util.Map<String, java.util.List<Long>> cwStartEventsByClan = new java.util.HashMap<>();
				// Track clan state updates to apply after processing all events
				java.util.Map<String, String> clanStateUpdates = new java.util.HashMap<>();

				for (Long id : ids) {
					try {
						ListeningEvent le = new ListeningEvent(id);
						long duration = le.getDurationUntilEnd();

						// Handle "start" triggers (duration = -1) specially
						if (duration == -1) {
							// For start triggers, group by clan for batch processing
							if (le.getListeningType() == ListeningEvent.LISTENINGTYPE.CW) {
								String clanTag = le.getClanTag();
								cwStartEventsByClan.computeIfAbsent(clanTag, _ -> new java.util.ArrayList<>()).add(id);
							}
							continue; // Don't process start triggers as regular time-based events yet
						}

						// Skip if already scheduled (only for non-start events)
						if (scheduledEvents.contains(id)) {
							continue;
						}

						// For regular time-based events, check timestamp
						Long timestamp = le.getTimestamp();

						// Skip if timestamp is null or invalid
						if (timestamp == null || timestamp == Long.MAX_VALUE) {
							continue;
						}

						long timeUntilFire = timestamp - currentTime;

						// If event is within threshold and not yet scheduled, schedule it
						if (timeUntilFire <= schedulingThreshold && timeUntilFire > 0) {
							System.out.println("Scheduling event " + id + " to fire in " + (timeUntilFire / 1000 / 60)
									+ " minutes");
							scheduledEvents.add(id);
							scheduledEventTimestamps.put(id, timestamp);
							schedulertasks.schedule(() -> {
								try {
									executeEventWithRetry(le, id, 3);
									// Keep in scheduled set to prevent re-scheduling
									// Event will be removed when conditions change (new war, etc.)
								} catch (Exception e) {
									System.err.println("Error executing event " + id + ": " + e.getMessage());
									e.printStackTrace();
									// Keep in set even on error to prevent retry loops
								}
							}, timeUntilFire, TimeUnit.MILLISECONDS);
						} else if (timeUntilFire <= 0) {
							// Event is overdue - skip it instead of firing to prevent duplicate triggers
							// after restart
							// Mark as scheduled so we don't keep trying to process it
							scheduledEvents.add(id);
							scheduledEventTimestamps.put(id, timestamp);
						}
					} catch (Exception e) {
						System.err.println("Error processing event " + id + ": " + e.getMessage());
						e.printStackTrace();
					}
				}

				// Second pass: Check clan war states ONCE per clan and fire all start events
				// for that clan
				for (java.util.Map.Entry<String, java.util.List<Long>> entry : cwStartEventsByClan.entrySet()) {
					String clanTag = entry.getKey();
					java.util.List<Long> eventIds = entry.getValue();

					try {
						datawrapper.Clan clan = new datawrapper.Clan(clanTag);

						// Get last known state (only once per clan)
						String lastState = getCWLastState(clanTag);

						// Get current state (only once per clan)
						org.json.JSONObject cwJson = clan.getCWJson();
						String currentState = cwJson.getString("state");

						// Check if war just started (only once per clan)
						boolean warJustStarted = !lastState.isEmpty()
								&& (lastState.equals("notInWar") || lastState.equals("warEnded"))
								&& (currentState.equals("preparation") || currentState.equals("inWar"));

						// Fire all start events for this clan if war just started
						if (warJustStarted) {
							System.out.println("CW Start detected for clan " + clanTag + ", firing " + eventIds.size()
									+ " events");
							// Mark state for update AFTER all events are processed
							clanStateUpdates.put(clanTag, currentState);

							// Get or create set of fired events for this clan
							java.util.Set<Long> clanFiredEvents = firedStartEvents.computeIfAbsent(clanTag,
									_ -> java.util.concurrent.ConcurrentHashMap.newKeySet());

							// Fire all events that haven't been fired yet for this war
							for (Long eventId : eventIds) {
								if (!clanFiredEvents.contains(eventId)) {
									System.out.println("Firing start event " + eventId + " for clan " + clanTag);
									clanFiredEvents.add(eventId);

									// Create a final reference for use in lambda
									final Long finalEventId = eventId;
									schedulertasks.execute(() -> {
										try {
											ListeningEvent le = new ListeningEvent(finalEventId);
											le.fireEvent();
											System.out.println("Successfully fired start event " + finalEventId
													+ " for clan " + clanTag);
										} catch (Exception e) {
											System.err.println("Error firing start trigger " + finalEventId + ": "
													+ e.getMessage());
											e.printStackTrace();
											// Remove from fired set to allow retry in next poll
											clanFiredEvents.remove(finalEventId);
										}
									});
								} else {
									System.out.println("Start event " + eventId
											+ " already fired for current war in clan " + clanTag + ", skipping");
								}
							}
						} else if (currentState.equals("notInWar") || currentState.equals("warEnded")) {
							// War ended, clear fired events for this clan to allow re-firing on next war
							firedStartEvents.remove(clanTag);
							String previousState = getCWLastState(clanTag);
							if (!previousState.equals("notInWar") && !previousState.equals("warEnded")) {
								setCWLastState(clanTag, currentState);
								System.out.println("CW ended for clan " + clanTag + ", updated state from "
										+ previousState + " to notInWar");
							}
						}
					} catch (Exception e) {
						System.err.println("Error checking war state for clan " + clanTag + ": " + e.getMessage());
					}
				}

				// Apply clan state updates after processing all events
				for (java.util.Map.Entry<String, String> entry : clanStateUpdates.entrySet()) {
					setCWLastState(entry.getKey(), entry.getValue());
					System.out.println("Updated CW state for clan " + entry.getKey() + " to " + entry.getValue());
				}
			} catch (Exception e) {
				System.err.println("Error in event polling: " + e.getMessage());
				e.printStackTrace();
			}
		};

		// Initialize CW states for start trigger detection
		initializeCWLastStates();

		// Run immediately on startup, then every 2 minutes
		schedulertasks.scheduleAtFixedRate(pollingTask, 0, 2, TimeUnit.MINUTES);
		System.out.println("Event polling system started - checking every 2 minutes");
	}

	/**
	 * Initialize CW last states to current state to prevent false start triggers on
	 * restart
	 */
	private static void initializeCWLastStates() {
		try {
			// Get all clans with CW events (not just start triggers)
			String sql = "SELECT DISTINCT clan_tag FROM listening_events WHERE listeningtype = 'cw'";
			ArrayList<String> clanTags = DBUtil.getArrayListFromSQL(sql, String.class);

			for (String clanTag : clanTags) {
				try {
					datawrapper.Clan clan = new datawrapper.Clan(clanTag);
					String currentState = "notInWar"; // default

					if (clan.isCWActive()) {
						org.json.JSONObject cwJson = clan.getCWJson();
						currentState = cwJson.getString("state");
					}

					// Initialize last state to current state
					setCWLastState(clanTag, currentState);
					System.out.println("Initialized CW state for clan " + clanTag + ": " + currentState);
				} catch (Exception e) {
					System.err.println("Error initializing CW state for clan " + clanTag + ": " + e.getMessage());
				}
			}
		} catch (Exception e) {
			System.err.println("Error in initializeCWLastStates: " + e.getMessage());
		}
	}

	// Simple in-memory storage for last war states
	private static java.util.HashMap<String, String> cwLastStates = new java.util.HashMap<>();

	private static String getCWLastState(String clanTag) {
		return cwLastStates.getOrDefault(clanTag, "");
	}

	private static void setCWLastState(String clanTag, String state) {
		cwLastStates.put(clanTag, state);
	}

	public static void endClanGamesSavings() {
		long nowMillis = System.currentTimeMillis();
		ZonedDateTime zdt = getNext28thAt12pm();
		long endMillis = zdt.toInstant().toEpochMilli();
		long enddelay = Math.max(endMillis - nowMillis, 0);

		String sql = "SELECT coc_tag FROM players";

		Timestamp timestampend = Timestamp.from(zdt.toInstant());

		schedulertasks.schedule(() -> {
			System.out.println("Es werden alle Clanspieldaten übertragen...");
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
				Player p = new Player(tag);
				p.addAchievementDataToDB(Type.CLANGAMES_POINTS, timestampend);
			}
			endClanGamesSavings();
		}, enddelay, TimeUnit.MILLISECONDS);
	}

	public static void startClanGamesSavings() {
		long nowMillis = System.currentTimeMillis();
		ZonedDateTime zdt = getNext22thAt7am();
		long startMillis = zdt.toInstant().toEpochMilli();
		long startdelay = Math.max(startMillis - nowMillis, 0);

		String sql = "SELECT coc_tag FROM players";

		Timestamp timestampstart = Timestamp.from(zdt.toInstant());

		schedulertasks.schedule(() -> {
			System.out.println("Es werden alle Clanspieldaten übertragen...");
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
				Player p = new Player(tag);
				p.addAchievementDataToDB(Type.CLANGAMES_POINTS, timestampstart);
			}
			startClanGamesSavings();
		}, startdelay, TimeUnit.MILLISECONDS);
	}

	public static void scheduleSeasonEndWinsSaving() {
		// Fetch the actual season end time from the API
		Timestamp seasonEndTime = util.SeasonUtil.fetchSeasonEndTime();

		if (seasonEndTime == null) {
			System.err.println("Failed to fetch season end time from API. Retrying in 1 hour...");
			// Retry after 1 hour if fetching fails
			schedulertasks.schedule(() -> scheduleSeasonEndWinsSaving(), 1, TimeUnit.HOURS);
			return;
		}

		long nowMillis = System.currentTimeMillis();
		long seasonEndMillis = seasonEndTime.getTime();
		long delay = Math.max(seasonEndMillis - nowMillis, 0);

		System.out.println("Season end wins tracking scheduled for: " + seasonEndTime);

		String sql = "SELECT coc_tag FROM players";

		schedulertasks.schedule(() -> {
			System.out.println("Saving all player wins at season end...");
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
				try {
					Player p = new Player(tag);
					p.addAchievementDataToDB(Type.WINS, seasonEndTime);
				} catch (Exception e) {
					System.err.println("Error saving wins for player " + tag + ": " + e.getMessage());
				}
			}
			// Schedule the next season end
			scheduleSeasonEndWinsSaving();
		}, delay, TimeUnit.MILLISECONDS);
	}

	public static void scheduleSeasonStartWinsSaving() {
		// Fetch the actual season start time from the API
		Timestamp seasonStartTime = util.SeasonUtil.fetchSeasonStartTime();

		if (seasonStartTime == null) {
			System.err.println("Failed to fetch season start time from API. Retrying in 1 hour...");
			// Retry after 1 hour if fetching fails
			schedulertasks.schedule(() -> scheduleSeasonStartWinsSaving(), 1, TimeUnit.HOURS);
			return;
		}

		long nowMillis = System.currentTimeMillis();
		long seasonStartMillis = seasonStartTime.getTime();
		
		String sql = "SELECT coc_tag FROM players";
		
		// If the season start time has already passed, save wins data immediately
		// This handles the case where the bot starts after season has begun
		if (seasonStartMillis <= nowMillis) {
			// Check if we already have data for this season start
			// If not, save it immediately
			System.out.println("Season already started at " + seasonStartTime + ", saving wins data immediately...");
			
			// Execute immediately in a separate thread to not block startup
			schedulertasks.execute(() -> {
				System.out.println("Saving all player wins for current season start...");
				for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
					try {
						Player p = new Player(tag);
						p.addAchievementDataToDB(Type.WINS, seasonStartTime);
					} catch (Exception e) {
						System.err.println("Error saving wins for player " + tag + ": " + e.getMessage());
					}
				}
				System.out.println("Finished saving player wins for season start.");
			});
			
			// Schedule check for next season start in 24 hours
			long delayUntilNextCheck = 24 * 60 * 60 * 1000L;
			System.out.println("Will check again in 24 hours for next season start");
			schedulertasks.schedule(() -> scheduleSeasonStartWinsSaving(), delayUntilNextCheck, TimeUnit.MILLISECONDS);
			return;
		}

		long delay = seasonStartMillis - nowMillis;

		System.out.println("Season start wins tracking scheduled for: " + seasonStartTime);

		schedulertasks.schedule(() -> {
			System.out.println("Saving all player wins at season start...");
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
				try {
					Player p = new Player(tag);
					p.addAchievementDataToDB(Type.WINS, seasonStartTime);
				} catch (Exception e) {
					System.err.println("Error saving wins for player " + tag + ": " + e.getMessage());
				}
			}
			// Schedule the next season start
			scheduleSeasonStartWinsSaving();
		}, delay, TimeUnit.MILLISECONDS);
	}

	public static void startNameUpdates() {
		System.out.println("Alle 2h werden nun die Namen aktualisiert. " + System.currentTimeMillis());
		Runnable task = () -> {
			String sql = "SELECT coc_tag FROM players";
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
				try {
					DBUtil.executeUpdate("UPDATE players SET name = ? WHERE coc_tag = ?", new Player(tag).getNameAPI(),
							tag);
				} catch (Exception e) {
					System.out.println("Fehler beim Namenupdate von Tag " + tag);
				}
			}
		};
		schedulernames.scheduleAtFixedRate(task, 0, 2, TimeUnit.HOURS);
	}

	public void stopScheduler() {
		schedulernames.shutdown();
		schedulertasks.shutdown();
	}

	// Helpers

	public static ZonedDateTime getNext22thAt7am() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();
		int month = now.getMonthValue();

		LocalDateTime target = LocalDateTime.of(year, month, 22, 7, 0, 0, 0);

		// Wenn jetzt >= 22. um 07:00 Uhr dann nächsten Monat nehmen
		if (!now.isBefore(target)) {
			month++;
			if (month > 12) {
				month = 1;
				year++;
			}
			target = LocalDateTime.of(year, month, 22, 7, 0, 0, 0);
		}

		ZonedDateTime zdt = target.atZone(ZoneId.systemDefault());
		return zdt;
	}

	public static ZonedDateTime getNext28thAt12pm() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();
		int month = now.getMonthValue();

		LocalDateTime target = LocalDateTime.of(year, month, 28, 12, 0, 0, 0);

		// Wenn jetzt >= 28. um 12:00 Uhr dann nächsten Monat nehmen
		if (!now.isBefore(target)) {
			month++;
			if (month > 12) {
				month = 1;
				year++;
			}
			target = LocalDateTime.of(year, month, 28, 12, 0, 0, 0);
		}

		ZonedDateTime zdt = target.atZone(ZoneId.systemDefault());
		return zdt;
	}

	public static ZonedDateTime getPrevious22thAt7am() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();
		int month = now.getMonthValue();

		LocalDateTime target = LocalDateTime.of(year, month, 22, 7, 0, 0, 0);

		// Wenn jetzt < 22. um 07:00 Uhr, dann vorherigen Monat nehmen
		if (now.isBefore(target)) {
			month--;
			if (month < 1) {
				month = 12;
				year--;
			}
			target = LocalDateTime.of(year, month, 22, 7, 0, 0, 0);
		}

		ZonedDateTime zdt = target.atZone(ZoneId.systemDefault());
		return zdt;
	}

	public static ZonedDateTime getPrevious28thAt12pm() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();
		int month = now.getMonthValue();

		LocalDateTime target = LocalDateTime.of(year, month, 28, 12, 0, 0, 0);

		// Wenn jetzt < 28. um 12:00 Uhr, dann vorherigen Monat nehmen
		if (now.isBefore(target)) {
			month--;
			if (month < 1) {
				month = 12;
				year--;
			}
			target = LocalDateTime.of(year, month, 28, 12, 0, 0, 0);
		}

		ZonedDateTime zdt = target.atZone(ZoneId.systemDefault());
		return zdt;
	}

	/**
	 * Get next 28th at 13:00 (1pm) - used for listening events to ensure API data
	 * has propagated This is 1 hour after the actual clan games end time
	 */
	public static ZonedDateTime getNext28thAt1pm() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();
		int month = now.getMonthValue();

		LocalDateTime target = LocalDateTime.of(year, month, 28, 13, 0, 0, 0);

		// Wenn jetzt >= 28. um 13:00 Uhr dann nächsten Monat nehmen
		if (!now.isBefore(target)) {
			month++;
			if (month > 12) {
				month = 1;
				year++;
			}
			target = LocalDateTime.of(year, month, 28, 13, 0, 0, 0);
		}

		ZonedDateTime zdt = target.atZone(ZoneId.systemDefault());
		return zdt;
	}

	/**
	 * Get previous 28th at 13:00 (1pm) - used for listening events
	 */
	public static ZonedDateTime getPrevious28thAt1pm() {
		LocalDateTime now = LocalDateTime.now();
		int year = now.getYear();
		int month = now.getMonthValue();

		LocalDateTime target = LocalDateTime.of(year, month, 28, 13, 0, 0, 0);

		// Wenn jetzt < 28. um 13:00 Uhr, dann vorherigen Monat nehmen
		if (now.isBefore(target)) {
			month--;
			if (month < 1) {
				month = 12;
				year--;
			}
			target = LocalDateTime.of(year, month, 28, 13, 0, 0, 0);
		}

		ZonedDateTime zdt = target.atZone(ZoneId.systemDefault());
		return zdt;
	}

}
