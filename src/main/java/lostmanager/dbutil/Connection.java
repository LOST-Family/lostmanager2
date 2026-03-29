package lostmanager.dbutil;

import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import lostmanager.Bot;

public class Connection {

	public static String url;
	public static String user;
	public static String password;

	private static java.sql.Connection connection;

	public static boolean checkDB() {

		url = Bot.url;
		user = Bot.user;
		password = Bot.password;

		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			if (conn != null) {
				connection = conn;
				return true;
			} else {
				return false;
			}
		} catch (final SQLException e) {
			System.out.println("Verbindungsfehler: " + e.getMessage());
			return false;
		}
	}

	public static void tablesExists() {
		ArrayList<String> tableNames = new ArrayList<>();
		tableNames.add("clans");
		tableNames.add("users");
		tableNames.add("players");
		tableNames.add("clan_members");
		tableNames.add("clan_settings");
		tableNames.add("kickpoint_reasons");
		tableNames.add("kickpoints");
		tableNames.add("cw_fillers");
		tableNames.add("achievements");
		tableNames.add("achievement_data");
		tableNames.add("upload_sessions");
		tableNames.add("userjsons");
		tableNames.add("datamappings");
		tableNames.add("member_signoffs");
		tableNames.add("rosters");
		tableNames.add("roster_participants");
		try (java.sql.Connection conn = DriverManager.getConnection(url, user, password)) {
			DatabaseMetaData dbm = conn.getMetaData();

			for (final String tableName : tableNames) {
				try (ResultSet tables = dbm.getTables(null, null, tableName, null)) {
					if (tables.next()) {
						System.out.println("Tabelle '" + tableName + "' existiert schon.");
					} else {
						System.out.println("Tabelle '" + tableName + "' existiert nicht. Erstelle sie jetzt...");
						String createTableSQL = null;
						switch (tableName) {
						case "clans" ->
							createTableSQL = "CREATE TABLE " + tableName + " (tag TEXT PRIMARY KEY," + "name TEXT,"
									+ "index BIGINT," + "guild_id CHARACTER VARYING(19),"
									+ "leader_roleid CHARACTER VARYING(19)," + "coleader_roleid CHARACTER VARYING(19),"
									+ "elder_roleid CHARACTER VARYING(19)," + "member_roleid CHARACTER VARYING(19))";
						case "users" ->
							createTableSQL = "CREATE TABLE " + tableName
									+ " (discord_id CHARACTER VARYING(19) PRIMARY KEY," + "is_admin BOOLEAN)";
						case "players" ->
							createTableSQL = "CREATE TABLE " + tableName + " (cr_tag TEXT PRIMARY KEY,"
									+ "discord_id CHARACTER VARYING(19), name TEXT)";
						case "clan_members" ->
							createTableSQL = "CREATE TABLE " + tableName + " (player_tag TEXT PRIMARY KEY,"
									+ "clan_tag TEXT," + "clan_role TEXT)";
						case "clan_settings" ->
							createTableSQL = "CREATE TABLE " + tableName + " (clan_tag TEXT PRIMARY KEY,"
									+ "max_kickpoints BIGINT," + "kickpoints_expire_after_days SMALLINT)";
						case "kickpoint_reasons" ->
							createTableSQL = "CREATE TABLE " + tableName + " (name TEXT," + "clan_tag text,"
									+ "amount SMALLINT," + "PRIMARY KEY (name, clan_tag))";
						case "kickpoints" ->
							createTableSQL = "CREATE TABLE " + tableName + " (id BIGINT PRIMARY KEY,"
									+ "player_tag CHARACTER VARYING(19)," + "date TIMESTAMPTZ," + "amount BIGINT,"
									+ "description CHARACTER VARYING(100),"
									+ "created_by_discord_id CHARACTER VARYING(19)," + "created_at TIMESTAMPTZ,"
									+ "expires_at TIMESTAMPTZ)";
						case "cw_fillers" ->
							createTableSQL = "CREATE TABLE " + tableName + " (id BIGSERIAL PRIMARY KEY,"
									+ "clan_tag TEXT NOT NULL," + "player_tag TEXT NOT NULL,"
									+ "war_end_time TIMESTAMP NOT NULL," + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
									+ "UNIQUE(clan_tag, player_tag, war_end_time))";
						case "achievements" ->
							createTableSQL = "CREATE TABLE " + tableName + " (tag TEXT PRIMARY KEY,"
									+ "data JSONB NOT NULL,"
									+ "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)";
						case "achievement_data" ->
							createTableSQL = "CREATE TABLE " + tableName + " (id BIGSERIAL PRIMARY KEY,"
									+ "player_tag TEXT NOT NULL,"
									+ "type TEXT NOT NULL,"
									+ "time TIMESTAMP NOT NULL,"
									+ "data JSONB NOT NULL,"
									+ "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
									+ "UNIQUE(player_tag, type, time))";
						case "upload_sessions" ->
							createTableSQL = "CREATE TABLE " + tableName + " (session_key TEXT PRIMARY KEY,"
									+ "userid TEXT NOT NULL,"
									+ "expires_at TIMESTAMP NOT NULL)";
						case "userjsons" ->
							createTableSQL = "CREATE TABLE " + tableName + " (userid TEXT NOT NULL,"
									+ "tag TEXT NOT NULL,"
									+ "json JSONB NOT NULL,"
									+ "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
									+ "PRIMARY KEY (userid, tag))";
						case "datamappings" ->
							createTableSQL = "CREATE TABLE " + tableName + " (datavalue TEXT PRIMARY KEY,"
									+ "emojiid TEXT,"
									+ "name TEXT,"
									+ "emojiname TEXT)";
						case "member_signoffs" ->
							createTableSQL = "CREATE TABLE " + tableName + " (id BIGSERIAL PRIMARY KEY,"
									+ "player_tag TEXT NOT NULL,"
									+ "start_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
									+ "end_date TIMESTAMP,"
									+ "reason TEXT,"
									+ "created_by_discord_id TEXT NOT NULL,"
									+ "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,"
									+ "UNIQUE(player_tag))";
						case "rosters" ->
							createTableSQL = "CREATE TABLE " + tableName + " (name VARCHAR(255) PRIMARY KEY,"
									+ "clan VARCHAR(255),"
									+ "min_th INT,"
									+ "delete_at TIMESTAMP,"
									+ "only_signoff BOOLEAN,"
									+ "is_closed BOOLEAN DEFAULT FALSE)";
						case "roster_participants" ->
							createTableSQL = "CREATE TABLE " + tableName + " (roster_name VARCHAR(255) REFERENCES rosters(name) ON DELETE CASCADE,"
									+ "discord_id VARCHAR(19),"
									+ "account_tag VARCHAR(100),"
									+ "status VARCHAR(50),"
									+ "th_level INT,"
									+ "PRIMARY KEY (roster_name, account_tag))";
						}

						try (Statement stmt = conn.createStatement()) {
							stmt.executeUpdate(createTableSQL);
							System.out.println("Tabelle '" + tableName + "' wurde erstellt.");
							
							// Create index for cw_fillers table
							if (tableName.equals("cw_fillers")) {
								stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_cw_fillers_lookup ON cw_fillers(clan_tag, war_end_time)");
								System.out.println("Index 'idx_cw_fillers_lookup' wurde erstellt.");
							}
							
							// Create indexes for achievement_data table
							if (tableName.equals("achievement_data")) {
								stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_achievement_data_lookup ON achievement_data(player_tag, type, time)");
								stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_achievement_data_time ON achievement_data(time)");
								stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_achievement_data_type ON achievement_data(type)");
								System.out.println("Indexes für 'achievement_data' wurden erstellt.");
							}
							
							// Create indexes for member_signoffs table
							if (tableName.equals("member_signoffs")) {
								stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_member_signoffs_player ON member_signoffs(player_tag)");
								stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_member_signoffs_end_date ON member_signoffs(end_date)");
								System.out.println("Indexes für 'member_signoffs' wurden erstellt.");
							}
						}
					}

				}
			}
		} catch (final SQLException e) {
			System.out.println(e.getMessage());
		}

	}

	public static java.sql.Connection getConnection() throws SQLException {
		if (connection == null || connection.isClosed()) {
			connection = DriverManager.getConnection(url, user, password);
		}
		return connection;
	}

}
