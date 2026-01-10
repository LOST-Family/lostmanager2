package lostmanager.dbutil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;

import lostmanager.util.Tuple;

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
				if (tableName != null) {
					// Close the failed statement before retrying
					if (pstmt != null) {
						try {
							pstmt.close();
						} catch (SQLException ex) {
							ex.printStackTrace();
						}
					}
					
					// First try to reset the sequence (preferred approach)
					SequenceResetResult resetResult = resetSequence(tableName);
					if (resetResult == SequenceResetResult.SUCCESS) {
						// Retry the insert after resetting the sequence
						return executeUpdateWithRetry(sql, retryCount + 1, params);
					}
					
					// If table doesn't have an 'id' column, don't try the explicit ID fallback.
					// This code is within the duplicate key error handler (SQLState 23505), and for
					// tables without an 'id' column (like 'achievements' which uses 'tag'), the 
					// duplicate key error is a true duplicate value that should be handled by the caller
					
					// If sequence reset fails for other reasons (e.g., permission denied), 
					// try inserting with explicit ID (only if table has an id column)
					if (resetResult == SequenceResetResult.FAILED) {
						String modifiedSql = modifyInsertWithExplicitId(sql, tableName);
						if (modifiedSql != null) {
							System.out.println("Sequence reset failed, retrying with explicit ID for table " + tableName);
							return executeUpdateWithRetry(modifiedSql, retryCount + 1, params);
						}
					}
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

	/**
	 * Result of a sequence reset attempt.
	 * SUCCESS: sequence was reset successfully
	 * NO_ID_COLUMN: table doesn't have an 'id' column (sequence reset not applicable)
	 * FAILED: sequence reset failed for other reasons
	 */
	private enum SequenceResetResult {
		SUCCESS, NO_ID_COLUMN, FAILED
	}

	private static SequenceResetResult resetSequence(String tableName) {
		// Defensive check: tableName must be validated by extractTableName() to contain only safe characters
		// (alphanumeric and underscore, starting with letter or underscore)
		if (tableName == null || !tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
			return SequenceResetResult.FAILED;
		}
		String sequenceName = tableName + "_id_seq";
		String resetSql = "SELECT setval('" + sequenceName + "', COALESCE((SELECT MAX(id) FROM " + tableName + "), 0) + 1, false)";
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(resetSql);
			 ResultSet rs = pstmt.executeQuery()) {
			rs.next(); // Consume the result set - setval always returns a value
			System.out.println("Reset sequence " + sequenceName + " for table " + tableName);
			return SequenceResetResult.SUCCESS;
		} catch (SQLException e) {
			// PostgreSQL error code 42703 = "undefined_column" (column does not exist)
			// This means the table doesn't have an 'id' column, so sequence reset is not applicable
			if ("42703".equals(e.getSQLState())) {
				System.out.println("Table " + tableName + " does not have an 'id' column, skipping sequence reset");
				return SequenceResetResult.NO_ID_COLUMN;
			}
			System.err.println("Failed to reset sequence " + sequenceName + ": " + e.getMessage());
			return SequenceResetResult.FAILED;
		}
	}

	/**
	 * Modifies an INSERT statement to use an explicit ID value calculated from MAX(id) + 1.
	 * This is used as a fallback when sequence reset fails (e.g., due to permission issues).
	 * 
	 * Note: This approach has a potential race condition with concurrent inserts. However,
	 * since this is a last-resort fallback after sequence reset fails, concurrent issues
	 * are acceptable. The proper fix is to grant sequence permissions at the database level.
	 * 
	 * @param sql The original INSERT statement
	 * @param tableName The validated table name (already sanitized by extractTableName)
	 * @return Modified SQL with explicit ID, or null if modification not possible
	 */
	private static String modifyInsertWithExplicitId(String sql, String tableName) {
		// Only modify INSERT statements that don't already have an explicit id column
		String trimmedSql = sql.trim();
		String upperSql = trimmedSql.toUpperCase();
		
		if (!upperSql.startsWith("INSERT INTO ")) {
			return null;
		}
		
		// Check if the INSERT already specifies the id column
		int openParen = trimmedSql.indexOf('(');
		int closeParen = trimmedSql.indexOf(')');
		if (openParen < 0 || closeParen < 0 || closeParen < openParen) {
			return null;
		}
		
		String columnsPart = trimmedSql.substring(openParen + 1, closeParen);
		// Check if id column is already specified as a standalone column name
		// Split by comma and check each column name individually
		// Handle both unquoted (id) and quoted ("id") identifiers
		String[] columns = columnsPart.split(",");
		for (String col : columns) {
			String trimmedCol = col.trim();
			// Remove quotes if present and compare case-insensitively
			String normalizedCol = trimmedCol.replaceAll("^\"|\"$", "").toUpperCase();
			if (normalizedCol.equals("ID")) {
				// id column already exists, can't easily modify
				return null;
			}
		}
		
		// Find VALUES clause
		int valuesIndex = upperSql.indexOf("VALUES");
		if (valuesIndex < 0) {
			return null;
		}
		
		// Build modified SQL: INSERT INTO table (id, col1, col2, ...) VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM table), ?, ?, ...)
		// Note: The ID value is computed via subquery, not a parameter, so the parameter count stays the same
		String beforeColumns = trimmedSql.substring(0, openParen + 1);
		String afterColumns = trimmedSql.substring(closeParen);
		
		// Find values placeholder
		int valuesOpenParen = afterColumns.indexOf('(');
		if (valuesOpenParen < 0) {
			return null;
		}
		
		String valuesClause = afterColumns.substring(0, valuesOpenParen + 1);
		String valuesContent = afterColumns.substring(valuesOpenParen + 1);
		
		// Construct the modified SQL with explicit id subquery
		// tableName is already validated by extractTableName() to be safe
		String modifiedSql = beforeColumns + "id, " + columnsPart + valuesClause + 
				"(SELECT COALESCE(MAX(id), 0) + 1 FROM " + tableName + "), " + valuesContent;
		
		return modifiedSql;
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
