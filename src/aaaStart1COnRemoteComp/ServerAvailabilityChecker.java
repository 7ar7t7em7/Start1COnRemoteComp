package aaaStart1COnRemoteComp;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Единая проверка доступности компьютера по ICMP Ping.
 *
 * Используется как ручной проверкой из интерфейса, так и перед операциями обмена.
 */
public final class ServerAvailabilityChecker {

    private static final int PING_TIMEOUT_MS = 2000;
    private static final int PROCESS_TIMEOUT_SECONDS = 4;

    private ServerAvailabilityChecker() {
        // Utility class
    }

    /**
     * Возвращает true только если системная команда Windows ping завершилась
     * с кодом 0. Вывод команды не анализируется, поэтому метод не зависит
     * от языка Windows.
     */
    public static boolean isAvailable(ServerInfo server) {
        if (server == null || server.getIp() == null || server.getIp().isBlank()) {
            return false;
        }

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    "ping", "-n", "1", "-w",
                    String.valueOf(PING_TIMEOUT_MS), server.getIp().trim());

            // Не читаем локализованный текст ping и не допускаем переполнения pipe.
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);

            process = builder.start();

            boolean finished = process.waitFor(
                    PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return false;
            }

            return process.exitValue() == 0;

        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            if (process != null) {
                process.destroyForcibly();
            }
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
