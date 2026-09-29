package aaaStart1COnRemoteComp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseReader {

    /** Стандартный порт SQL Server */
    private static final int DEFAULT_PORT = 1433;

    /**
     * Подключается к SQL Server 2000 и читает таблицу _1SDBSET.
     *
     * @param host     IP-адрес или имя сервера SQL
     * @param database Имя базы данных
     * @param username Логин
     * @param password Пароль
     * @return Строка с результатом (или текст ошибки)
     */
    public static String readDbSetTable(String host, String database,
                                        String username, String password) {
        StringBuilder result = new StringBuilder();

        // 1. Регистрация драйвера jTDS
        try {
            Class.forName("net.sourceforge.jtds.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            return "Драйвер jTDS не найден. Убедитесь, что JAR добавлен в Build Path.";
        }

        // 2. Формируем URL подключения
        String dbUrl = "jdbc:jtds:sqlserver://" + host + ":" + DEFAULT_PORT + "/" + database;

        // 3. Подключение и выполнение запроса
        try (Connection conn = DriverManager.getConnection(dbUrl, username, password);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM _1SDBSET")) {

            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();

            // Заголовки столбцов
            for (int i = 1; i <= columnCount; i++) {
                result.append(meta.getColumnName(i)).append("\t");
            }
            result.append("\n----------------------------------------\n");

            // Данные
            while (rs.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    result.append(rs.getString(i)).append("\t");
                }
                result.append("\n");
            }

        } catch (SQLException e) {
            return "Ошибка SQL: " + e.getMessage();
        }

        return result.toString();
    }
}