package aaaStart1COnRemoteComp;

/**
 * Описывает удаленный сервер, на котором планируется запуск 1С 7.7.
 */
public class ServerInfo {
	
	/** Признак/код базы данных (DBSIGN из _1SDBSET) */
	private String dbSign;
	
	/** Краткое наименование сервера (отображается в списке) */
	private String name;

    /** Имя компьютера в сети (например, "SRV-1C-01") */
    private String computerName;

    /** IP-адрес сервера (например, "10.1.2.171") */
    private String ip;

    /** Полный путь к исполняемому файлу 1С 7.7 на сервере (например, "C:\\Program Files\\1cv77\\bin\\1cv77.exe") */
    private String program1CPath;

    /** Полный путь к каталогу базы данных 1С (например, "C:\\1C\\Base") */
    private String databasePath;

    /** Имя базы данных 1С (например, "Бухгалтерия") */
    private String databaseName;

    /** Признак: true — центральный сервер, false — периферийный */
    private boolean central;

    // ===== Конструкторы =====

    /** Пустой конструктор (нужен для фреймворков и удобства) */
    public ServerInfo() {
    }

    public ServerInfo(String name, String computerName, String ip, String program1CPath,
            String databasePath, String databaseName, String dbSign, boolean central) {
this.name = name;
this.computerName = computerName;
this.ip = ip;
this.program1CPath = program1CPath;
this.databasePath = databasePath;
this.databaseName = databaseName;
this.dbSign = dbSign;
this.central = central;
}

    // ===== Геттеры и сеттеры =====
    
    public String getDbSign() {
        return dbSign;
    }

    public void setDbSign(String dbSign) {
        this.dbSign = dbSign;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComputerName() {
        return computerName;
    }

    public void setComputerName(String computerName) {
        this.computerName = computerName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getProgram1CPath() {
        return program1CPath;
    }

    public void setProgram1CPath(String program1CPath) {
        this.program1CPath = program1CPath;
    }

    public String getDatabasePath() {
        return databasePath;
    }

    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public boolean isCentral() {
        return central;
    }

    public void setCentral(boolean central) {
        this.central = central;
    }

    // ===== Удобные методы =====

    /** Возвращает "центр" или "периферия" — удобно для логов */
    public String getRole() {
        return central ? "центр" : "периферия";
    }

    /** Полный путь к базе: path + name */
    public String getFullDatabasePath() {
        if (databasePath == null || databaseName == null) {
            return null;
        }
        return databasePath + "\\" + databaseName;
    }

    @Override
    public String toString() {
        return "ServerInfo{" +
                "name='" + name + '\'' +
                ", computerName='" + computerName + '\'' +
                ", ip='" + ip + '\'' +
                ", program1CPath='" + program1CPath + '\'' +
                ", databasePath='" + databasePath + '\'' +
                ", databaseName='" + databaseName + '\'' +
                ", dbSign='" + dbSign + '\'' +
                ", central=" + central +
                '}';
    }
}