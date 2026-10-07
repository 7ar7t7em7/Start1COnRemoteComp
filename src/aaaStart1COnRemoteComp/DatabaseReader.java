package aaaStart1COnRemoteComp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DatabaseReader {

    private static final int DEFAULT_PORT = 1433;

    /**
     * Результат запроса: список колонок и список строк.
     */
    public static class QueryResult {
        public final List<String> columns = new ArrayList<>();
        public final List<Map<String, String>> rows = new ArrayList<>();
    }

    public static QueryResult readDbSetTable(String host, String database,
                                             String username, String password) throws SQLException {
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("Драйвер jTDS не найден. Убедитесь, что JAR добавлен в Build Path.", e);
        }

        String dbUrl = "jdbc:jtds:sqlserver://" + host + ":" + DEFAULT_PORT + "/" + database;
        QueryResult result = new QueryResult();

        try (Connection conn = DriverManager.getConnection(dbUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM _1SDBSET")) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // Запоминаем имена колонок
            for (int i = 1; i <= columnCount; i++) {
                result.columns.add(meta.getColumnName(i));
            }

            // Читаем строки
            while (rs.next()) {
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String value = rs.getString(i);
                    row.put(meta.getColumnName(i), value == null ? "" : value);
                }
                result.rows.add(row);
            }
        }
        return result;
    }
}