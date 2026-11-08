package aaaStart1COnRemoteComp;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Stack;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class AppLogger {

    private static final String LOG_DIR = "D:\\ae\\Logs";
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    
    // Ссылка на текстовое поле в интерфейсе
    private static TextArea uiLogArea = null;

    /** Устанавливает текстовое поле для вывода логов в UI. */
    public static void setUiLogArea(TextArea textArea) {
        uiLogArea = textArea;
    }

    // Храним стек ID операций для текущего потока (для поддержки вложенности)
    private static final ThreadLocal<Stack<String>> opIdStack = ThreadLocal.withInitial(Stack::new);

    public enum Level { INFO, WARN, ERROR }

    /** Добавляет ID операции в стек (для вложенных операций). */
    public static void pushOperationId(String id) {
        opIdStack.get().push(id);
    }

    /** Убирает последний ID операции из стека. */
    public static void popOperationId() {
        Stack<String> stack = opIdStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /** Формирует строку вида [EX-12345] или [EX-12345-LD1]. */
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

    /** Записывает сообщение в лог. */
    public static void log(Level level, String message) {
        String time = TIME_FORMAT.format(new Date());
        
        // Формируем префикс: [2026-11-02 12:00:00] [EX-1021] [INFO] 
        String prefix = "[" + time + "] " + getCurrentOpIdPrefix() + "[" + level + "] ";

        // Для многострочных сообщений добавляем отступы, чтобы было красиво
        String[] lines = message.split("\r?\n");
        StringBuilder formattedMessage = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i == 0) {
                formattedMessage.append(prefix).append(lines[i]).append("\n");
            } else {
                // Отступ для продолжения строки (выравниваем по тексту сообщения)
                String indent = " ".repeat(prefix.length());
                formattedMessage.append(indent).append(lines[i]).append("\n");
            }
        }

        // 1. Вывод в консоль
        if (level == Level.ERROR) {
            System.err.print(formattedMessage);
        } else {
            System.out.print(formattedMessage);
        }

        // 2. Вывод в UI (только через UI-поток)
        if (uiLogArea != null) {
            String finalMessage = formattedMessage.toString();
            Platform.runLater(() -> {
                uiLogArea.appendText(finalMessage);
                uiLogArea.setScrollTop(Double.MAX_VALUE); // Прокрутка вниз
            });
        }
        
        // 3. Запись в файл
        try {
            File dir = new File(LOG_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = "app_" + DATE_FORMAT.format(new Date()) + ".log";
            File logFile = new File(dir, fileName);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(formattedMessage.toString());
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать в лог: " + e.getMessage());
        }
    }

    public static void info(String message) { log(Level.INFO, message); }
    public static void warn(String message) { log(Level.WARN, message); }
    public static void error(String message) { log(Level.ERROR, message); }

    public static void error(String message, Throwable t) {
        log(Level.ERROR, message + ": " + t.getMessage());
        t.printStackTrace();
    }
}