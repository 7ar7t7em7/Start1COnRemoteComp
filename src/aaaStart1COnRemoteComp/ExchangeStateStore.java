package aaaStart1COnRemoteComp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Хранит последнее состояние обмена каждой периферийной базы. */
public final class ExchangeStateStore {
    private static final Path FILE_PATH = Paths.get("D:\\ae\\Config\\exchange_state.json");
    private static final Path TEMP_PATH = Paths.get("D:\\ae\\Config\\exchange_state.json.tmp");
    private static final Path BACKUP_PATH = Paths.get("D:\\ae\\Config\\exchange_state.json.bak");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<ExchangeState>>() {}.getType();
    private static final Map<String, ExchangeState> STATES = new LinkedHashMap<>();
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static Runnable changeListener;

    static { load(); }
    private ExchangeStateStore() {}

    /**
     * Загружает основной файл. При повреждении переносит его в .corrupt-*,
     * затем пытается восстановиться из последней резервной копии.
     */
    private static synchronized void load() {
        boolean primaryAvailable = Files.exists(FILE_PATH);
        if (!primaryAvailable && !Files.exists(BACKUP_PATH)) return;
        boolean loadedFromPrimary = false;

        if (primaryAvailable) {
            try {
                applyLoadedStates(readStates(FILE_PATH));
                loadedFromPrimary = true;
            } catch (IOException | RuntimeException e) {
                AppLogger.error("Не удалось загрузить состояние обмена из " + FILE_PATH
                        + ": " + e.getMessage());
                if (!quarantine(FILE_PATH)) {
                    AppLogger.error("Повреждённый файл состояния не удалось переместить. "
                            + "Исходный файл оставлен без изменений.");
                    return;
                }
            }
        }

        if (!loadedFromPrimary && Files.exists(BACKUP_PATH)) {
            try {
                applyLoadedStates(readStates(BACKUP_PATH));
                AppLogger.warn("Состояние обмена восстановлено из резервной копии " + BACKUP_PATH);
            } catch (IOException | RuntimeException e) {
                AppLogger.error("Не удалось загрузить резервную копию состояния обмена из "
                        + BACKUP_PATH + ": " + e.getMessage());
                quarantine(BACKUP_PATH);
                STATES.clear();
            }
        }

        if (loadedFromPrimary || !STATES.isEmpty() || !Files.exists(FILE_PATH)) {
            try {
                saveInternal();
            } catch (IOException e) {
                AppLogger.error("Не удалось сохранить восстановленное состояние обмена: " + e.getMessage());
            }
        } else if (!Files.exists(BACKUP_PATH)) {
            // Основной файл был повреждён и успешно перемещён; создаём новый пустой файл.
            try {
                saveInternal();
            } catch (IOException e) {
                AppLogger.error("Не удалось создать новый файл состояния обмена: " + e.getMessage());
            }
        }
    }

    private static List<ExchangeState> readStates(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, LIST_TYPE);
        }
    }

    private static void applyLoadedStates(List<ExchangeState> loaded) {
        STATES.clear();
        if (loaded == null) return;
        for (ExchangeState state : loaded) {
            if (state == null || state.getServerId() == null) continue;
            if (state.getStatus() == ExchangeStatus.RUNNING) {
                state.setStatus(ExchangeStatus.INTERRUPTED);
                state.setFinishedAt(now());
                state.setMessage("Приложение было закрыто во время выполнения этапа");
            }
            STATES.put(state.getServerId(), state);
        }
    }

    /** Перемещает повреждённый файл, не перезаписывая предыдущие копии. */
    private static boolean quarantine(Path path) {
        if (!Files.exists(path)) return true;
        String suffix = ".corrupt-" + LocalDateTime.now().format(BACKUP_TIME);
        Path damagedPath = path.resolveSibling(path.getFileName().toString() + suffix);
        try {
            Files.move(path, damagedPath, StandardCopyOption.REPLACE_EXISTING);
            AppLogger.warn("Повреждённый файл состояния сохранён как " + damagedPath);
            return true;
        } catch (IOException e) {
            AppLogger.error("Не удалось сохранить повреждённый файл " + path + ": " + e.getMessage());
            return false;
        }
    }

    public static synchronized void setChangeListener(Runnable listener) { changeListener = listener; }

    public static synchronized void begin(ServerInfo server, ExchangeStage stage, String operationId) {
        if (server == null || server.isCentral()) return;
        server.ensureId();
        ExchangeState state = new ExchangeState();
        state.setServerId(server.getId());
        state.setParentName(server.getParentName());
        state.setServerName(server.getName());
        state.setStage(stage);
        state.setStatus(ExchangeStatus.RUNNING);
        state.setStartedAt(now());
        state.setAttempt(0);
        state.setMessage("Выполняется");
        state.setOperationId(operationId);
        STATES.put(server.getId(), state);
        persistAndNotify();
    }

    public static synchronized void finish(ServerInfo server, ExchangeStatus status, String message, int attempt) {
        finish(server, status, message, attempt, null);
    }

    public static synchronized void finish(ServerInfo server, ExchangeStatus status, String message, int attempt, String operationId) {
        if (server == null || server.isCentral()) return;
        server.ensureId();
        ExchangeState state = STATES.get(server.getId());
        if (state == null || state.getStatus() != ExchangeStatus.RUNNING) return;
        if (operationId != null && !operationId.equals(state.getOperationId())) return;
        state.setStatus(status);
        state.setFinishedAt(now());
        state.setMessage(message == null ? "" : message);
        if (attempt > 0) state.setAttempt(attempt);
        persistAndNotify();
    }

    public static synchronized void failIfRunning(ServerInfo server, String message) {
        failIfRunning(server, message, null);
    }

    public static synchronized void failIfRunning(ServerInfo server, String message, String operationId) {
        if (server == null || server.isCentral()) return;
        server.ensureId();
        ExchangeState state = STATES.get(server.getId());
        if (state != null && state.getStatus() == ExchangeStatus.RUNNING
                && (operationId == null || operationId.equals(state.getOperationId()))) {
            state.setStatus(ExchangeStatus.FAILED);
            state.setFinishedAt(now());
            state.setMessage(message == null ? "Операция завершилась с ошибкой" : message);
            persistAndNotify();
        }
    }

    public static synchronized void updateAttempt(ServerInfo server, int attempt) {
        updateAttempt(server, attempt, null);
    }

    public static synchronized void updateAttempt(ServerInfo server, int attempt, String operationId) {
        if (server == null || server.isCentral()) return;
        server.ensureId();
        ExchangeState state = STATES.get(server.getId());
        if (state != null && state.getStatus() == ExchangeStatus.RUNNING
                && (operationId == null || operationId.equals(state.getOperationId()))) {
            state.setAttempt(attempt);
            persistAndNotify();
        }
    }

    public static synchronized ExchangeState get(ServerInfo server) {
        if (server == null || server.isCentral()) return null;
        server.ensureId();
        return STATES.get(server.getId());
    }

    private static void persistAndNotify() {
        try { saveInternal(); }
        catch (IOException e) { AppLogger.error("Не удалось сохранить состояние обмена: " + e.getMessage()); }
        Runnable listener = changeListener;
        if (listener != null) {
            try { listener.run(); } catch (RuntimeException e) {
                AppLogger.warn("Не удалось обновить отображение состояния обмена: " + e.getMessage());
            }
        }
    }

    /**
     * Сначала полностью записывает временный файл, затем сохраняет предыдущий
     * корректный файл как .bak и атомарно заменяет основной.
     */
    private static void saveInternal() throws IOException {
        Files.createDirectories(FILE_PATH.getParent());
        try (Writer writer = Files.newBufferedWriter(TEMP_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(new ArrayList<>(STATES.values()), writer);
        }

        if (Files.exists(FILE_PATH)) {
            try {
                Files.copy(FILE_PATH, BACKUP_PATH, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                AppLogger.warn("Не удалось обновить резервную копию состояния: " + e.getMessage());
            }
        }

        try {
            Files.move(TEMP_PATH, FILE_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(TEMP_PATH, FILE_PATH, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String now() { return LocalDateTime.now().toString(); }
    public static Path getFilePath() { return FILE_PATH; }
}
