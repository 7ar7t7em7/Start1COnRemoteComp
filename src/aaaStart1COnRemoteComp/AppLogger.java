package aaaStart1COnRemoteComp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;
import java.util.function.Predicate;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;

public class AppLogger {

    private static final String LOG_DIR = "D:\\ae\\Logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_LOG_LINES = 1000;

    private static final ThreadLocal<String> contextDbSign = new ThreadLocal<>();
    private static final ThreadLocal<Stack<String>> opIdStack = ThreadLocal.withInitial(Stack::new);

    private static ListView<LogEntry> uiLogList = null;
    private static final ObservableList<LogEntry> uiLogData = FXCollections.observableArrayList();
    private static final FilteredList<LogEntry> uiLogFiltered = new FilteredList<>(uiLogData, null);

    public enum Level { INFO, WARN, ERROR }

    public static void setContextDbSign(String sign) { contextDbSign.set(sign); }
    public static void clearContextDbSign() { contextDbSign.remove(); }
    public static String getContextDbSign() { return contextDbSign.get(); }

    public static void pushOperationId(String id) { opIdStack.get().push(id); }
    
    public static void popOperationId() {
        Stack<String> stack = opIdStack.get();
        if (!stack.isEmpty()) stack.pop();
    }

    /** Устанавливает ListView и настраивает отображение объектов LogEntry */
    public static void setUiLogArea(ListView<LogEntry> listView) {
        uiLogList = listView;
        if (uiLogList != null) {
            uiLogList.setItems(uiLogFiltered);
            uiLogList.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(LogEntry item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item.getText());
                        if (item.getLevel() == Level.ERROR) {
                            setStyle("-fx-text-fill: red;");
                        } else if (item.getLevel() == Level.WARN) {
                            setStyle("-fx-text-fill: orange;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            });
        }
    }

    public static void setUiLogFilter(Predicate<LogEntry> predicate) {
        Platform.runLater(() -> uiLogFiltered.setPredicate(predicate));
    }

    /** Возвращает полный префикс ID (например, [UP-123-DC-45]) */
    private static String getCurrentOpIdPrefix() {
        Stack<String> stack = opIdStack.get();
        if (stack.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) sb.append("-");
            sb.append(stack.get(i));
        }
        sb.append("] ");
        return sb.toString();
    }

    /** Возвращает корневой ID операции (например, "UP-123") */
    private static String getRootOpId() {
        Stack<String> stack = opIdStack.get();
        return stack.isEmpty() ? "" : stack.firstElement();
    }
    
    public static void milestone(String message) {
        log(Level.INFO, message, true);
    }
    
    public static void log(Level level, String message, boolean isMilestone) {
        String time = TIME_FORMAT.format(new Date());
        String dbSign = contextDbSign.get();
        String prefix = "[" + time + "] " + getCurrentOpIdPrefix();
        if (dbSign != null && !dbSign.isBlank()) {
            prefix += "[" + dbSign + "] ";
        }
        prefix += "[" + level + "] ";

        String[] lines = message.split("\r?\n");
        StringBuilder formattedMessage = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                formattedMessage.append(prefix).append(lines[i]).append("\n");
            } else {
                formattedMessage.append(" ".repeat(prefix.length())).append(lines[i]).append("\n");
            }
        }

        String finalMessage = formattedMessage.toString().trim();
        
        // 1. Консоль
        if (level == Level.ERROR) System.err.print(formattedMessage);
        else System.out.print(formattedMessage);

        // 2. UI
        if (uiLogList != null) {
            String rootOp = getRootOpId();
            String currentDb = (dbSign != null) ? dbSign.trim() : null;
            // Передаем флаг isMilestone в LogEntry
            LogEntry entry = new LogEntry(finalMessage, rootOp, currentDb, level, isMilestone);
            
            Platform.runLater(() -> {
                uiLogData.add(entry);
                if (uiLogData.size() > MAX_LOG_LINES) {
                    uiLogData.remove(0, uiLogData.size() - MAX_LOG_LINES);
                }
                uiLogList.scrollTo(uiLogFiltered.size() - 1);
            });
        }

        // 3. Файл
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) dir.mkdirs();
            File logFile = new File(dir, "app_" + DATE_FORMAT.format(new Date()) + ".log");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(formattedMessage.toString());
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать в лог: " + e.getMessage());
        }
    }
    
    // Перегружаем стандартный log, чтобы принимать флаг
    public static void log(Level level, String message) {
        log(level, message, false);
    }
    
    public static ObservableList<LogEntry> getUiLogData() {
        return uiLogData;
    }

    public static void info(String message) { log(Level.INFO, message); }
    public static void warn(String message) { log(Level.WARN, message); }
    public static void error(String message) { log(Level.ERROR, message); }
    public static void error(String message, Throwable t) {
        log(Level.ERROR, message + ": " + t.getMessage());
        t.printStackTrace();
    }
}