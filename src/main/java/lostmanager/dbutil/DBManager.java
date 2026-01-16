package lostmanager.dbutil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lostmanager.datawrapper.Player;
import net.dv8tion.jda.api.interactions.commands.Command;

public class DBManager {

	public enum InClanType {
		INCLAN, NOTINCLAN, ALL
	}

	public static ArrayList<String> getAllClans() {
		return DBUtil.getArrayListFromSQL("SELECT tag FROM clans", String.class);
	}

	public static List<Command.Choice> getKPReasonsAutocomplete(String input, String clantag) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT name, clan_tag FROM kickpoint_reasons WHERE clan_tag = ?";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, clantag);
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String name = rs.getString("name");

					if (name.toLowerCase().contains(input.toLowerCase())) {
						choices.add(new Command.Choice(name, name));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

	public static int getAvailableKPID() {
		String sql = "SELECT id FROM kickpoints";
		int available = 0;

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				ArrayList<Integer> used = new ArrayList<>();
				while (rs.next()) {
					used.add(rs.getInt("id"));
				}
				while (used.contains(available)) {
					available++;
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return available;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getClansAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT name, tag FROM clans ORDER BY index ASC";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String name = rs.getString("name");

					String display = name + " (" + tag + ")";

					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getSideClansAutocomplete(String input) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT name, clan_tag FROM sideclans ORDER BY name ASC";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("clan_tag");
					String name = rs.getString("name");

					String display = name + " (" + tag + ")";

					if (display.toLowerCase().contains(input.toLowerCase())
							|| tag.toLowerCase().startsWith(input.toLowerCase())) {
						choices.add(new Command.Choice(display, tag));
						if (choices.size() == 25) {
							break;
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return choices;
	}

	@SuppressWarnings("null")
	public static List<Command.Choice> getPlayerlistAutocomplete(String input, InClanType inclantype) {
		List<Command.Choice> choices = new ArrayList<>();

		String sql = "SELECT players.coc_tag AS tag, players.name AS player_name, clans.name AS clan_name "
				+ "FROM players " + "LEFT JOIN clan_members ON clan_members.player_tag = players.coc_tag "
				+ "LEFT JOIN clans ON clans.tag = clan_members.clan_tag";

		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String tag = rs.getString("tag");
					String name = rs.getString("player_name");
					String clanName = rs.getString("clan_name");

					String display = new Player(tag).setNameDB(name).getInfoStringDB();
					if (inclantype == InClanType.NOTINCLAN) {
						if (clanName == null || clanName.isEmpty()) {
							if (display.toLowerCase().contains(input.toLowerCase())
									|| tag.toLowerCase().startsWith(input.toLowerCase())) {
								choices.add(new Command.Choice(display, tag));
								if (choices.size() == 25) {
									break;
								}
							}
						}
					} else if (inclantype == InClanType.INCLAN) {
						if (clanName != null && !clanName.isEmpty()) {
							display += " - " + clanName;
							if (display.toLowerCase().contains(input.toLowerCase())
									|| tag.toLowerCase().startsWith(input.toLowerCase())) {
								choices.add(new Command.Choice(display, tag));
								if (choices.size() == 25) {
									break; // Max 25 Vorschläge
								}
							}
						}
					} else if (inclantype == InClanType.ALL) {
						if (clanName != null && !clanName.isEmpty()) {
							display += " - " + clanName;
						}

						// Filter mit Eingabe (input ist String mit aktuell eingegebenem Text)
						if (display.toLowerCase().contains(input.toLowerCase())
								|| tag.toLowerCase().startsWith(input.toLowerCase())) {
							choices.add(new Command.Choice(display, tag));
							if (choices.size() == 25) {
								break; // Max 25 Vorschläge
							}
						}
					}
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return choices;
	}

}
