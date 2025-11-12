package datautil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import util.Tuple;

public class DBUtil {

	public static Tuple<PreparedStatement, Integer> executeUpdate(String sql, Object... params) {
		PreparedStatement pstmt = null;
		try {
			// Only request generated keys for INSERT statements that need them
			// UPDATE and DELETE don't need generated keys, and some tables don't have an "id" column
			String trimmedSql = sql.trim().toUpperCase();
			if (trimmedSql.startsWith("INSERT")) {
				// For INSERT statements, use RETURN_GENERATED_KEYS to let PostgreSQL decide which keys to return
				// This avoids errors when tables don't have an "id" column
				pstmt = Connection.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			} else {
				// For UPDATE, DELETE, etc., don't request generated keys
				pstmt = Connection.getConnection().prepareStatement(sql);
			}
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}
			int rA = pstmt.executeUpdate();

			PreparedStatement finalPstmt = pstmt;
			new Thread(() -> {
				try {
					Thread.sleep(10000);
					finalPstmt.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}).start();

			return new Tuple<>(pstmt, rA);
		} catch (SQLException e) {
			e.printStackTrace();
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ex) {
					ex.printStackTrace();
				}
			}
		}
		return null;
	}

	public static <T> T getValueFromSQL(String sql, Class<T> clazz, Object... params) {
		T result = null;
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					// Get the first column name from ResultSetMetaData
					ResultSetMetaData metaData = rs.getMetaData();
					String columnName = metaData.getColumnName(1);
					
					// Use getString() for String class to handle JSONB columns properly
					if (clazz == String.class) {
						result = clazz.cast(rs.getString(columnName));
					} else {
						result = rs.getObject(columnName, clazz);
					}
				}
				Statement stmt = rs.getStatement();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static OffsetDateTime getDateFromSQL(String sql, Object... params) {
		OffsetDateTime a = null;
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					// Get the first column name from ResultSetMetaData
					ResultSetMetaData metaData = rs.getMetaData();
					String columnName = metaData.getColumnName(1);
					a = rs.getObject(columnName, OffsetDateTime.class);
				}
				Statement stmt = rs.getStatement();
				rs.close();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return a;
	}

	public static <E> ArrayList<E> getArrayListFromSQL(String sql, Class<E> clazz, Object... params) {
		ArrayList<E> result = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				// Get the first column name from ResultSetMetaData
				ResultSetMetaData metaData = rs.getMetaData();
				String columnName = metaData.getColumnName(1);
				
				while (rs.next()) {
					// Use getString() for String class to handle JSONB columns properly
					if (clazz == String.class) {
						result.add(clazz.cast(rs.getString(columnName)));
					} else {
						result.add(rs.getObject(columnName, clazz));
					}
				}
				Statement stmt = rs.getStatement();
				rs.close();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

}
