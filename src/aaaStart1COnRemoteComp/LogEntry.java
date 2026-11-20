package aaaStart1COnRemoteComp;

public class LogEntry {
    private final String text;
    private final String rootOpId;
    private final String fullOpId; // <-- ДОБАВИТЬ: полный ID (например, "UC-7753-DP-532")
    private final String dbSign;
    private final AppLogger.Level level;
    private final boolean isMilestone;

    public LogEntry(String text, String rootOpId, String fullOpId, String dbSign, AppLogger.Level level, boolean isMilestone) {
        this.text = text;
        this.rootOpId = rootOpId;
        this.fullOpId = fullOpId;
        this.dbSign = dbSign;
        this.level = level;
        this.isMilestone = isMilestone;
    }

    public String getText() { return text; }
    public String getRootOpId() { return rootOpId; }
    public String getDbSign() { return dbSign; }
    public AppLogger.Level getLevel() { return level; }
    public boolean isMilestone() { return isMilestone; }
    public String getFullOpId() { return fullOpId; }
}