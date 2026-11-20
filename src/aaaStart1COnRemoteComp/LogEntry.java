package aaaStart1COnRemoteComp;

public class LogEntry {
    private final String text;
    private final String rootOpId;
    private final String dbSign;
    private final AppLogger.Level level;
    private final boolean isMilestone; // Флаг: является ли сообщение ключевым для обмена

    public LogEntry(String text, String rootOpId, String dbSign, AppLogger.Level level) {
        this(text, rootOpId, dbSign, level, false);
    }

    public LogEntry(String text, String rootOpId, String dbSign, AppLogger.Level level, boolean isMilestone) {
        this.text = text;
        this.rootOpId = rootOpId;
        this.dbSign = dbSign;
        this.level = level;
        this.isMilestone = isMilestone;
    }

    public String getText() { return text; }
    public String getRootOpId() { return rootOpId; }
    public String getDbSign() { return dbSign; }
    public AppLogger.Level getLevel() { return level; }
    public boolean isMilestone() { return isMilestone; }
}