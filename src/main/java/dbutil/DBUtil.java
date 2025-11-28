package dbutil;

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
		return executeUpdateWithRetry(sql, 0, params);
	}

	private static Tuple<PreparedStatement, Integer> executeUpdateWithRetry(String sql, int retryCount, Object... params) {
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
			// Check for duplicate key violation (PostgreSQL error code 23505)
			if ("23505".equals(e.getSQLState()) && retryCount < 1) {
				// Extract table name from INSERT statement to reset the correct sequence
				String tableName = extractTableName(sql);
				if (tableName != null && resetSequence(tableName)) {
					// Close the failed statement before retrying
					if (pstmt != null) {
						try {
							pstmt.close();
						} catch (SQLException ex) {
							ex.printStackTrace();
						}
					}
					// Retry the insert after resetting the sequence
					return executeUpdateWithRetry(sql, retryCount + 1, params);
				}
			}
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

	private static final String INSERT_INTO_PREFIX = "INSERT INTO ";

	private static String extractTableName(String sql) {
		// Extract table name from "INSERT INTO table_name (...)" pattern
		String upperSql = sql.trim().toUpperCase();
		if (upperSql.startsWith(INSERT_INTO_PREFIX)) {
			String afterInsertInto = sql.trim().substring(INSERT_INTO_PREFIX.length()).trim();
			int spaceIndex = afterInsertInto.indexOf(' ');
			int parenIndex = afterInsertInto.indexOf('(');
			int endIndex = -1;
			if (spaceIndex > 0 && parenIndex > 0) {
				endIndex = Math.min(spaceIndex, parenIndex);
			} else if (spaceIndex > 0) {
				endIndex = spaceIndex;
			} else if (parenIndex > 0) {
				endIndex = parenIndex;
			}
			if (endIndex > 0) {
				String tableName = afterInsertInto.substring(0, endIndex).trim();
				// Validate table name to prevent SQL injection - only allow alphanumeric and underscore
				if (tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
					return tableName;
				}
			}
		}
		return null;
	}

	private static boolean resetSequence(String tableName) {
		// tableName is already validated by extractTableName() to contain only safe characters
		String sequenceName = tableName + "_id_seq";
		String resetSql = "SELECT setval('" + sequenceName + "', COALESCE((SELECT MAX(id) FROM " + tableName + "), 0) + 1, false)";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(resetSql);
			 ResultSet rs = pstmt.executeQuery()) {
			System.out.println("Reset sequence " + sequenceName + " for table " + tableName);
			return true;
		} catch (SQLException e) {
			System.err.println("Failed to reset sequence " + sequenceName + ": " + e.getMessage());
			return false;
		}
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
