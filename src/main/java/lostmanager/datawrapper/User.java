package lostmanager.datawrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import lostmanager.Bot;
import lostmanager.dbutil.DBManager;
import lostmanager.dbutil.DBUtil;

public class User {

	private HashMap<String, Player.RoleType> clanroles;
	private String userid;
	private ArrayList<Player> linkedaccounts;
	private Boolean isadmin;
	private String nickname;

	public User(String userid) {
		this.userid = userid;
	}

	public User refreshData() {
		clanroles = null;
		linkedaccounts = null;
		isadmin = null;
		nickname = null;
		return this;
	}

	// all public getter Methods
	public String getUserID() {
		return userid;
	}

	public boolean isAdmin() {
		if (isadmin == null) {
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class,
					userid) == null) {
				try {
					DBUtil.executeUpdate("INSERT INTO users (discord_id, name, is_admin) VALUES (?, ?, ?)", userid,
							Bot.getJda().getGuildById(Bot.guild_id).retrieveMemberById(userid).submit().get()
									.getEffectiveName(),
							false);
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class, userid)) {
				isadmin = true;
			}
		}
		if (isadmin == null) {
			isadmin = false;
		}
		return isadmin;
	}

	public ArrayList<Player> getAllLinkedAccounts() {
		if (linkedaccounts == null) {
			linkedaccounts = new ArrayList<>();
			String sql = "SELECT coc_tag FROM players WHERE discord_id = ?";
			for (String tag : DBUtil.getArrayListFromSQL(sql, String.class, userid)) {
				linkedaccounts.add(new Player(tag));
			}
		}
		return linkedaccounts;
	}

	public HashMap<String, Player.RoleType> getClanRoles() {
		if (clanroles == null) {
			clanroles = new HashMap<>();
			boolean admin = false;
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class,
					userid) == null) {
				DBUtil.executeUpdate("INSERT INTO users (discord_id, name, is_admin) VALUES (?, ?, ?)", userid,
						getNickname(), false);
			}
			if (DBUtil.getValueFromSQL("SELECT is_admin FROM users WHERE discord_id = ?", Boolean.class, userid)) {
				admin = true;
			}

			ArrayList<Player> linkedaccs = getAllLinkedAccounts();
			ArrayList<String> allclans = DBManager.getAllClans();

			if (admin) {
				for (String clantag : allclans) {
					clanroles.put(clantag, Player.RoleType.ADMIN);
				}
			} else {
				for (Player p : linkedaccs) {
					if (p.getClanDB() != null) {
						clanroles.put(p.getClanDB().getTag(), p.getRoleDB());
					}
				}
			}
			for (String clantag : allclans) {
				if (!clanroles.containsKey(clantag)) {
					clanroles.put(clantag, Player.RoleType.NOTINCLAN);
				}
			}
		}
		return clanroles;
	}

	public String getNickname() {
		if (nickname == null) {
			try {
				nickname = Bot.getJda().getGuildById(Bot.guild_id).retrieveMemberById(userid).submit().get()
						.getEffectiveName();
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
		return nickname;
	}

}
