package aaaStart1COnRemoteComp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Сохраняет и загружает список ServerInfo в JSON-файл.
 */
public class ServerStorage {

    // Путь к файлу. Лежит в домашней папке пользователя: C:\Users\<user>\1c_servers.json
	private static final Path FILE_PATH = Paths.get("D:\\ae\\Config\\1c_servers.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting() // Читаемый формат с отступами
            .create();

    /** Сохраняет список серверов в файл. */
    public static void save(List<ServerInfo> servers) throws IOException {
        if (servers != null) {
            for (ServerInfo server : servers) if (server != null) server.ensureId();
        }
        Files.createDirectories(FILE_PATH.getParent()); // создаст D:\ae, если её нет
        try (Writer writer = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(servers, writer);
        }
        AppLogger.info("Сохранено в: " + FILE_PATH);
    }

    /** Загружает список серверов из файла. Если файла нет — возвращает пустой список. */
    public static List<ServerInfo> load() throws IOException {
        if (!Files.exists(FILE_PATH)) {
            return new ArrayList<>();
        }
        try {
            List<ServerInfo> result;
            try (Reader reader = Files.newBufferedReader(FILE_PATH, StandardCharsets.UTF_8)) {
                Type listType = new TypeToken<List<ServerInfo>>() {}.getType();
                result = GSON.fromJson(reader, listType);
            }
            if (result == null) result = new ArrayList<>();
            boolean idsAdded = false;
            for (ServerInfo server : result) {
                if (server != null && (server.getId() == null || server.getId().isBlank())) {
                    server.ensureId();
                    idsAdded = true;
                }
            }
            if (idsAdded) save(result);
            return result;
        } catch (JsonSyntaxException e) {
            throw new IOException("Файл " + FILE_PATH + " повреждён.\n"
                    + "Удалите его или исправьте вручную.\n"
                    + "Ошибка: " + e.getMessage(), e);
        }
    }

    /** Возвращает путь к файлу (для отображения пользователю). */
    public static Path getFilePath() {
        return FILE_PATH;
    }
}