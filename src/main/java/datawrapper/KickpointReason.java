package datawrapper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import datautil.Connection;
import datautil.DBUtil;

public class KickpointReason {

	private String kpreason;
	private String clan_tag;
	private Long amount;

	public KickpointReason(String reason, String clan_tag) {
		kpreason = reason;
		this.clan_tag = clan_tag;
	}

	public KickpointReason refreshData() {
		amount = null;
		return this;
	}

	public boolean Exists() {
		String sql = "SELECT 1 FROM kickpoint_reasons WHERE name = ? AND clan_tag = ?";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			pstmt.setString(1, kpreason);
			pstmt.setString(2, clan_tag);
			try (ResultSet rs = pstmt.executeQuery()) {
				return rs.next(); // true, wenn mindestens eine Zeile existiert
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	public String getReason() {
		return kpreason;
	}
	
	public String getName() {
		return kpreason;
	}

	public String getClanTag() {
		return clan_tag;
	}

	public long getAmount() {
		if(amount == null) {
			String sql = "SELECT amount FROM kickpoint_reasons WHERE clan_tag = ? AND name = ?";
			amount = DBUtil.getValueFromSQL(sql, Long.class, clan_tag, kpreason);
		}
		return amount;
	}
}
