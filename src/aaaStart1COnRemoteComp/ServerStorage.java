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
import java.util.ArrayList;
import java.util.List;

/**
 * Сохраняет и загружает список ServerInfo в JSON-файл.
 */
public class ServerStorage {

    // Путь к файлу. Лежит в домашней папке пользователя: C:\Users\<user>\1c_servers.json
	private static final Path FILE_PATH = Paths.get("D:\\ae\\1c_servers.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting() // Читаемый формат с отступами
            .create();

    /** Сохраняет список серверов в файл. */
    public static void save(List<ServerInfo> servers) throws IOException {
        Files.createDirectories(FILE_PATH.getParent()); // создаст D:\ae, если её нет
        try (Writer writer = Files.newBufferedWriter(FILE_PATH, StandardCharsets.UTF_8)) {
            GSON.toJson(servers, writer);
        }
        System.out.println("Сохранено в: " + FILE_PATH);
    }

    /** Загружает список серверов из файла. Если файла нет — возвращает пустой список. */
    public static List<ServerInfo> load() throws IOException {
        if (!Files.exists(FILE_PATH)) {
            System.out.println("Файл не найден, начинаем с пустого списка: " + FILE_PATH);
            return new ArrayList<>();
        }
        try (Reader reader = Files.newBufferedReader(FILE_PATH, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<ServerInfo>>() {}.getType();
            List<ServerInfo> result = GSON.fromJson(reader, listType);
            return result != null ? result : new ArrayList<>();
        }
    }

    /** Возвращает путь к файлу (для отображения пользователю). */
    public static Path getFilePath() {
        return FILE_PATH;
    }
}