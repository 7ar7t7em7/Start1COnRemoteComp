package aaaStart1COnRemoteComp;

import java.io.File;

/**
 * Мониторит лог-файлы на удалённых серверах и скачивает их.
 */
public class LogMonitor {

    private static final int MAX_WAIT_SECONDS = 300;
    private static final int POLL_INTERVAL_MS = 1000;

    /**
     * Результат мониторинга лога.
     */
    public static class LogResult {
        private final File localFile;
        private final boolean success;
        private final String errorMessage;

        public LogResult(File localFile, boolean success, String errorMessage) {
            this.localFile = localFile;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public File getLocalFile() { return localFile; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Ожидает завершения операции 1С по лог-файлу и скачивает его.
     */
    public static LogResult waitAndDownload(SmbFileTransfer transfer, String remoteShare,
                                             String logFileName, int expectedOperations) {
        long startTime = System.currentTimeMillis();
        AppLogger.info("Ожидаем завершения работы 1С по лог-файлу: " + logFileName
                + " (ожидаем операций: " + expectedOperations + ")");

        while (true) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > MAX_WAIT_SECONDS) {
                AppLogger.error("Таймаут ожидания завершения 1С (" + MAX_WAIT_SECONDS + " сек).");
                return new LogResult(null, false, "Таймаут ожидания");
            }

            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new LogResult(null, false, "Прервано потоком");
            }

            try {
                if (!transfer.exists(remoteShare, logFileName)) {
                    continue;
                }

                String logContent = transfer.readFile(remoteShare, logFileName, "Cp866");

                int successCount = countOccurrences(logContent, "DistUplSuc")
                        + countOccurrences(logContent, "DistDnldSuc");
                int failCount = countOccurrences(logContent, "DistDnldFail")
                        + countOccurrences(logContent, "DistUplFail")
                        + countOccurrences(logContent, "DistDnlErr")
                        + countOccurrences(logContent, "DistUplErr");

                if (failCount > 0) {
                    AppLogger.error("В лог-файле найден маркер ОШИБКИ. Прерываем ожидание.");
                    return new LogResult(null, false, "Найдены маркеры ошибок в логе");
                }

                if (successCount >= expectedOperations) {
                    AppLogger.info("В лог-файле найдено " + successCount
                            + " маркеров успешного завершения. Всё готово.");
                    break;
                }

            } catch (Exception e) {
                // Игнорируем временные ошибки
            }
        }

        try {
            File localDir = new File("D:\\ae\\Logs");
            if (!localDir.exists()) localDir.mkdirs();
            String localPath = localDir.getAbsolutePath() + File.separator + logFileName;
            transfer.download(remoteShare, logFileName, localPath);
            AppLogger.info("Лог-файл 1С скачан с сервера: " + localPath);
            return new LogResult(new File(localPath), true, null);
        } catch (Exception e) {
            AppLogger.error("Не удалось скачать лог: " + e.getMessage());
            return new LogResult(null, false, "Не удалось скачать лог: " + e.getMessage());
        }
    }

    /**
     * Проверяет, появился ли лог-файл (без скачивания).
     */
    public static boolean waitForLogCreation(SmbFileTransfer transfer, String remoteShare,
                                              String logFileName, int maxWaitSeconds) {
        long startTime = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startTime) / 1000 < maxWaitSeconds) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (transfer.exists(remoteShare, logFileName)) {
                return true;
            }
        }
        return false;
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
}
