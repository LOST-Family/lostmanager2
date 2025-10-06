package lostmanager;

import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import commands.admin.deletemessages;
import commands.admin.restart;
import commands.kickpoints.clanconfig;
import commands.kickpoints.kpadd;
import commands.kickpoints.kpaddreason;
import commands.kickpoints.kpclan;
import commands.kickpoints.kpedit;
import commands.kickpoints.kpeditreason;
import commands.kickpoints.kpinfo;
import commands.kickpoints.kpmember;
import commands.kickpoints.kpremove;
import commands.kickpoints.kpremovereason;
import commands.links.link;
import commands.links.playerinfo;
import commands.links.unlink;
import commands.links.verify;
import commands.memberlist.addmember;
import commands.memberlist.editmember;
import commands.memberlist.listmembers;
import commands.memberlist.memberstatus;
import commands.memberlist.removemember;
import commands.memberlist.transfermember;
import commands.util.checkreacts;
import commands.util.cwdonator;
import commands.util.raidping;
import commands.util.setnick;
import datautil.DBUtil;
import datawrapper.Player;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class Bot extends ListenerAdapter {

	private final static ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

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
		VERSION = "2.0";
		guild_id = System.getenv("DISCORD_GUILD_ID");
		api_key = System.getenv("LOST_MANAGER_API_KEY");
		url = System.getenv("LOST_MANAGER_DB_URL");
		user = System.getenv("LOST_MANAGER_DB_USER");
		password = System.getenv("LOST_MANAGER_DB_PASSWORD");
		verified_roleid = System.getenv("DISCORD_VERIFIED_ROLEID");
		exmember_roleid = System.getenv("DISCORD_EXMEMBER_ROLEID");

		String token = System.getenv("LOST_MANAGER_TOKEN");

		if (datautil.Connection.checkDB()) {
			System.out.println("Verbindung zur Datenbank funktioniert.");
		} else {
			System.out.println("Verbindung zur Datenbank fehlgeschlagen.");
		}

		// sql.Connection.tablesExists();
		startNameUpdates();

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

							Commands.slash("link", "Verlinke einen Clash of Clans Account mit einem Discord User.")
									.addOption(OptionType.STRING, "tag", "Der Tag des Clash of Clans Accounts", true)
									.addOption(
											OptionType.MENTIONABLE, "user",
											"Der User, mit dem der Account verlinkt werden soll.", true),

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
									.addOptions(
											new OptionData(
													OptionType.STRING, "clan",
													"Der Clan, welcher ausgegeben werden soll.", true)
													.setAutoComplete(true)),

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
											.setAutoComplete(true))

					).queue();
		}
	}

	private static Object[] getListenerClassObjects() {
		ArrayList<Object> classes = new ArrayList<>();

		classes.add(new Bot());
		classes.add(new verify());
		classes.add(new link());
		classes.add(new unlink());
		classes.add(new restart());
		classes.add(new addmember());
		classes.add(new removemember());
		classes.add(new listmembers());
		classes.add(new editmember());
		classes.add(new playerinfo());
		classes.add(new memberstatus());
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
		classes.add(new setnick());
		classes.add(new deletemessages());
		classes.add(new raidping());
		classes.add(new transfermember());

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

	public static void startNameUpdates() {
		Thread thread = new Thread(() -> {
			Runnable task = () -> {
				System.out.println("Alle 2h werden nun die Namen aktualisiert. " + System.currentTimeMillis());

				String sql = "SELECT coc_tag FROM players";
				for (String tag : DBUtil.getArrayListFromSQL(sql, String.class)) {
					try {
						DBUtil.executeUpdate("UPDATE players SET name = ? WHERE coc_tag = ?",
								new Player(tag).getNameAPI(), tag);
					} catch (Exception e) {
						System.out.println(
								"Beim Updaten des Namens von Spieler mit Tag " + tag + " ist ein Fehler aufgetreten.");
					}
				}

			};
			scheduler.scheduleAtFixedRate(task, 0, 2, TimeUnit.HOURS);
		});
		thread.start();
	}

	public void stopScheduler() {
		scheduler.shutdown();
	}

}
