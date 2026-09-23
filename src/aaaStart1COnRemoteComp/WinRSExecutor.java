package aaaStart1COnRemoteComp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Класс для выполнения команд на удаленном Windows-компьютере через winrs.exe.
 * Подходит для старых систем, где WinRM 1.0 (например, Windows Server 2003).
 */
public class WinRSExecutor {

    private final String host;
    private final String username;
    private final String password;
    private final long timeoutSeconds;

    /**
     * @param host           IP-адрес или имя удаленного ПК
     * @param username       Имя пользователя (для домена: "DOMAIN\\user")
     * @param password       Пароль
     * @param timeoutSeconds Таймаут ожидания выполнения команды
     */
    public WinRSExecutor(String host, String username, String password, long timeoutSeconds) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Выполняет команду на удаленном компьютере через winrs.
     *
     * @param command Команда для выполнения (например, "dir C:\\" или "ipconfig /all")
     * @return Результат выполнения (stdout + stderr)
     */
    public String execute(String command) throws IOException, InterruptedException {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("winrs");
        cmdList.add("-r:" + host);
        cmdList.add("-u:" + username);
        cmdList.add("-p:" + password);
        cmdList.add(command);

        // Важно: передаем аргументы раздельно, а НЕ одной строкой.
        // Это распространенная ошибка при использовании ProcessBuilder[citation:3].
        ProcessBuilder pb = new ProcessBuilder(cmdList);
        pb.redirectErrorStream(true); // Объединяем stdout и stderr

        Process process = pb.start();

        // Читаем вывод процесса
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        }

        // Ждем завершения процесса с таймаутом
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Превышен таймаут выполнения команды (" + timeoutSeconds + " сек)");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            output.append(System.lineSeparator())
                  .append("Процесс завершился с кодом: ").append(exitCode);
        }

        return output.toString();
    }

    // Пример использования

}