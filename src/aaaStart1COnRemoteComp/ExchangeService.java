package aaaStart1COnRemoteComp;

import java.io.File;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Выполняет обмен данными между центральными и периферийными серверами 1С.
 */
public class ExchangeService {

    private static final int MAX_RETRIES = 3;
    private static final int COPY_POLL_SECONDS = 15;
    private static final int COPY_POLL_INTERVAL_MS = 1000;

    /**
     * Результат операции обмена.
     */
    public static class ExchangeResult {
        private final boolean success;
        private final String message;

        public ExchangeResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    /**
     * Выполняет выгрузку из центральной базы и загрузку в периферийные.
     */
    public static ExchangeResult unloadFromCentral(ServerInfo centralServer, List<ServerInfo> targets) {
        String mainOpId = "UC-" + (System.currentTimeMillis() % 100000);
        AppLogger.pushOperationId(mainOpId);
        AppLogger.setContextDbSign(centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "");

        try {
            AppLogger.info("==========================================================");
            AppLogger.milestone("НАЧАЛО ПРОЦЕССА ВЫГРУЗКИ ИЗ ЦЕНТРА: " + centralServer.getName());
            AppLogger.info("==========================================================");

            // Проверки
            if (targets.isEmpty()) {
                return new ExchangeResult(false, "Не выбрано ни одной базы для выгрузки.");
            }
            if (isBlank(centralServer.getProgram1CPath()) || isBlank(centralServer.getDatabasePath())) {
                return new ExchangeResult(false, "У центрального сервера не заполнены пути к 1С или базе.");
            }
            if (isBlank(centralServer.getSmbUser()) || isBlank(centralServer.getSmbPassword())) {
                return new ExchangeResult(false, "У центрального сервера не заполнены логин/пароль SMB/WMI.");
            }

            // Собираем DBSIGN
            StringBuilder writeTo = new StringBuilder();
            for (ServerInfo t : targets) {
                String sign = t.getDbSign();
                if (sign == null) continue;
                sign = sign.trim();
                if (sign.isEmpty()) continue;
                if (writeTo.length() > 0) writeTo.append(",");
                writeTo.append(sign);
            }
            if (writeTo.length() == 0) {
                return new ExchangeResult(false, "Ни у одной из выбранных баз не заполнен DBSIGN.");
            }
            String writeToString = writeTo.toString();

            String remoteShare = "smb://" + centralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            SmbFileTransfer transfer = new SmbFileTransfer(centralServer.getIp(),
                    centralServer.getSmbUser(), centralServer.getSmbPassword());
            
            // ПРОВЕРКА НАЛИЧИЯ ШАРЫ
            if (!transfer.shareExists("ae$")) {
                AppLogger.error("На сервере " + centralServer.getIp() + " не найдена шара 'ae$'.");
                return new ExchangeResult(false, "Не найдена шара 'ae$' на сервере " + centralServer.getName());
            }
            
            transfer.mkdir(remoteShare, "Log");

            String batContent = createUnloadBatContent(centralServer);

            // Цикл с ретраями
            File localLog = null;
            String fromSign = centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "";
            String toSigns = writeToString;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            	String logFileName = generateLogFileName(fromSign, toSigns);
                String prmContent = createUnloadPrmContent(logFileName, writeToString);
                
                // Загружаем файлы разом
                transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");
                transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
                
                AppLogger.info("Загружены unload.bat и aeparams.prm; Попытка выгрузки " + attempt + " из " + MAX_RETRIES);

                // Запускаем
                RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(centralServer, "c:\\ae\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + execResult.getShortString());

                // Ждём лог
                boolean logStarted = LogMonitor.waitForLogCreation(transfer, logShare, logFileName, 15);
                if (!logStarted) {
                    AppLogger.error("Лог-файл не появился (попытка " + attempt + "). Вероятно, блокировка.");
                    if (attempt == MAX_RETRIES) {
                        return new ExchangeResult(false, "Не удалось начать выгрузку. База заблокирована?");
                    }
                    Thread.sleep(10000);
                    continue;
                }

                AppLogger.info("Лог-файл создан. 1С начала обмен. Ожидаем завершения...");
                LogMonitor.LogResult logResult = LogMonitor.waitAndDownload(transfer, logShare, logFileName, targets.size());

                if (logResult.isSuccess()) {
                    AppLogger.info("Выгрузка успешно завершена с попытки " + attempt);
                    localLog = logResult.getLocalFile();
                    break;
                } else {
                    AppLogger.warn("Попытка " + attempt + " завершилась ошибкой (смотрите лог 1С): " + logResult.getErrorMessage());
                    if (attempt < MAX_RETRIES) {
                        AppLogger.info("Ждем 10 секунд перед повтором...");
                        Thread.sleep(10000);
                    } else {
                        return new ExchangeResult(false, "Выгрузка не удалась после " + MAX_RETRIES + " попыток.");
                    }
                }
            }

            if (localLog == null) {
                return new ExchangeResult(false, "Выгрузка не завершилась успешно.");
            }

            // Копируем файлы на периферию
            boolean allSuccess = copyAndLoadToPeripherals(centralServer, targets);
            if (allSuccess) {
                return new ExchangeResult(true, "УСПЕШНОЕ ЗАВЕРШЕНИЕ ПРОЦЕССА ВЫГРУЗКИ ИЗ ЦЕНТРА И ЗАГРУЗКИ В ПЕРИФЕРИЮ");
            } else {
                return new ExchangeResult(false, "ПРОЦЕСС ЗАВЕРШЕН С ОШИБКАМИ (не все базы удалось загрузить)");
            }

        } catch (Exception e) {
            AppLogger.error("КРИТИЧЕСКАЯ ОШИБКА В ПРОЦЕССЕ ВЫГРУЗКИ: " + e.getMessage());
            return new ExchangeResult(false, "Критическая ошибка: " + e.getMessage());
        } finally {
            AppLogger.popOperationId();
            AppLogger.clearContextDbSign();
        }
    }

    /**
     * Выполняет выгрузку из периферийной базы и загрузку в центральную.
     */
    public static ExchangeResult unloadFromPeriphery(ServerInfo peripheralServer, ServerInfo centralServer) {
        String mainOpId = "UP-" + (System.currentTimeMillis() % 100000);
        AppLogger.pushOperationId(mainOpId);
        AppLogger.setContextDbSign(peripheralServer.getDbSign() != null ? peripheralServer.getDbSign().trim() : "");

        try {
            AppLogger.info("==========================================================");
            AppLogger.milestone("НАЧАЛО ПРОЦЕССА ВЫГРУЗКИ ИЗ ПЕРИФЕРИИ: " + peripheralServer.getName());
            AppLogger.info("Центральная база: " + centralServer.getName());
            AppLogger.info("==========================================================");

            if (isBlank(peripheralServer.getProgram1CPath()) || isBlank(peripheralServer.getDatabasePath())) {
                return new ExchangeResult(false, "У периферийного сервера не заполнены пути.");
            }
            if (isBlank(peripheralServer.getSmbUser()) || isBlank(peripheralServer.getSmbPassword())) {
                return new ExchangeResult(false, "У периферийного сервера не заполнены логин/пароль SMB.");
            }

            String remoteShare = "smb://" + peripheralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            SmbFileTransfer transfer = new SmbFileTransfer(peripheralServer.getIp(),
                    peripheralServer.getSmbUser(), peripheralServer.getSmbPassword());
            
            // ПРОВЕРКА НАЛИЧИЯ ШАРЫ
            if (!transfer.shareExists("ae$")) {
                AppLogger.error("На сервере " + peripheralServer.getIp() + " не найдена шара 'ae$'.");
                return new ExchangeResult(false, "Не найдена шара 'ae$' на сервере " + peripheralServer.getName());
            }
            
            transfer.mkdir(remoteShare, "Log");

            String logFileName = generateLogFileName(
                    peripheralServer.getDbSign() != null ? peripheralServer.getDbSign().trim() : "",
                    centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : ""
            );
            String prmContent = createUnloadPrmContent(logFileName, centralServer.getDbSign().trim());
            String batContent = createUnloadBatContent(peripheralServer);

            // Загружаем файлы разом
            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp866")), remoteShare, "unload.bat");
            
            AppLogger.info("Загружены unload.bat и aeparams.prm; Попытка выгрузки 1 из 1");

            RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(peripheralServer, "c:\\ae\\unload.bat");
            AppLogger.info("Результат запуска BAT: " + execResult.getShortString());

            boolean logStarted = LogMonitor.waitForLogCreation(transfer, logShare, logFileName, 15);
            if (!logStarted) {
                return new ExchangeResult(false, "Не удалось начать выгрузку на " + peripheralServer.getName() + ". База заблокирована?");
            }

            AppLogger.info("Лог-файл создан. Ожидаем завершения выгрузки...");
            LogMonitor.LogResult logResult = LogMonitor.waitAndDownload(transfer, logShare, logFileName, 1);

            if (logResult.isSuccess()) {
                AppLogger.info("Выгрузка на периферии завершена успешно."); // техническая
            }

            // Только если выгрузка прошла успешно, копируем файл
            String fileName = peripheralServer.getDbSign().trim() + "1.zip";
            long copiedBytes = copyExchangeFile(peripheralServer, centralServer, fileName, "PC");

            boolean allSuccess = (copiedBytes > 0);
            if (copiedBytes > 0) {
                long sizeInKb = copiedBytes / 1024;
                AppLogger.info("Скачан и отправлен файл обмена: " + fileName + " (Размер: " + sizeInKb + " КБ)");

                ExchangeResult loadResult = loadToCenter(centralServer, peripheralServer);
                allSuccess = loadResult.isSuccess();
            }

            if (allSuccess) {
                return new ExchangeResult(true, "УСПЕШНОЕ ЗАВЕРШЕНИЕ ПРОЦЕССА ВЫГРУЗКИ ИЗ ПЕРИФЕРИИ И ЗАГРУЗКИ В ЦЕНТР");
            } else {
                return new ExchangeResult(false, "ПРОЦЕСС ЗАВЕРШЕН С ОШИБКАМИ (не удалось загрузить в центр)");
            }

        } catch (Exception e) {
            AppLogger.error("КРИТИЧЕСКАЯ ОШИБКА: " + e.getMessage());
            return new ExchangeResult(false, "Критическая ошибка: " + e.getMessage());
        } finally {
            AppLogger.popOperationId();
            AppLogger.clearContextDbSign();
        }
    }

    // ============================================================
    //  ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================

    private static ExchangeResult loadToCenter(ServerInfo centralServer, ServerInfo peripheralServer) {
        String subOpId = "DC-" + (System.currentTimeMillis() % 1000);
        AppLogger.pushOperationId(subOpId);
        
        // Меняем контекст на центральную базу (для лога)
        String previousContext = AppLogger.getContextDbSign();
        AppLogger.setContextDbSign(centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "");

        try {
        	AppLogger.milestone(">>> НАЧАЛО ЗАГРУЗКИ В ЦЕНТРАЛЬНУЮ БАЗУ: " + centralServer.getName() + " <<<");

            String remoteShare = "smb://" + centralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            SmbFileTransfer transfer = new SmbFileTransfer(centralServer.getIp(),
                    centralServer.getSmbUser(), centralServer.getSmbPassword());
            transfer.mkdir(remoteShare, "Log");

            String batContent = createUnloadBatContent(centralServer);

            String fromSign = peripheralServer.getDbSign() != null ? peripheralServer.getDbSign().trim() : "";
            String toSign = centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "";
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                String logFileName = generateLogFileName(fromSign, toSign);

                String prmContent = createLoadPrmContent(logFileName, peripheralServer.getDbSign().trim());
                
                // Загружаем файлы разом
                transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");
                transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
                
                AppLogger.info("Загружены unload.bat и aeparams.prm; Попытка загрузки " + attempt + " из " + MAX_RETRIES);

                RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(centralServer, "c:\\ae\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + execResult.getShortString());

                boolean logStarted = LogMonitor.waitForLogCreation(transfer, logShare, logFileName, 60);
                if (!logStarted) {
                    AppLogger.error("Не удалось начать загрузку в центр (попытка " + attempt + ").");
                    if (attempt == MAX_RETRIES) return new ExchangeResult(false, "Не удалось начать загрузку в центр");
                    Thread.sleep(10000);
                    continue;
                }

                LogMonitor.LogResult logResult = LogMonitor.waitAndDownload(transfer, logShare, logFileName, 1);
                if (logResult.isSuccess()) {
                    AppLogger.milestone(">>> ЗАГРУЗКА В ЦЕНТР УСПЕШНО ЗАВЕРШЕНА <<<");
                    return new ExchangeResult(true, "Загрузка в центр завершена успешно");
                } else {

                    // Проверяем, нужно ли прекращать повторные попытки
                    if (logResult.shouldStopRetrying()) {
                        AppLogger.error("Повторные попытки загрузки отменены: "
                                + logResult.getErrorMessage());

                        return new ExchangeResult(
                                false,
                                logResult.getErrorMessage()
                        );
                    }

                    AppLogger.warn("Попытка загрузки в центр " + attempt
                            + " завершилась ошибкой.");

                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(10000);
                    } else {
                        return new ExchangeResult(false,
                                "Все " + MAX_RETRIES
                                + " попытки загрузки в центр провалились");
                    }
                }
            }
            return new ExchangeResult(false, "Не удалось загрузить в центр");
        } catch (Exception e) {
            AppLogger.error("Ошибка при загрузке в центр: " + e.getMessage());
            return new ExchangeResult(false, "Ошибка: " + e.getMessage());
        } finally {
            AppLogger.popOperationId();
            AppLogger.setContextDbSign(previousContext);
        }
    }

    private static boolean copyAndLoadToPeripherals(ServerInfo centralServer, List<ServerInfo> targets) {
        AppLogger.info("Начинаем копирование и загрузку на периферийные серверы...");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        boolean allSuccess = true;
        int filesCopiedCount = 0;
        long totalCopiedBytes = 0;

        for (ServerInfo peripheral : targets) {
            if (isBlank(peripheral.getIp()) || isBlank(peripheral.getSmbUser())) {
                AppLogger.warn("Пропуск " + peripheral.getName() + ": не заполнены данные для SMB.");
                allSuccess = false;
                continue;
            }

            String zipFileName = peripheral.getDbSign().trim() + "0.zip";
            String centralShare = centralServer.getShareName();
            String centralCpPath = "smb://" + centralServer.getIp() + "/" + centralShare + "/CP/";

            SmbFileTransfer centralTransfer = new SmbFileTransfer(centralServer.getIp(),
                    centralServer.getSmbUser(), centralServer.getSmbPassword());

            boolean fileFound = false;
            for (int i = 0; i < COPY_POLL_SECONDS; i++) {
                try {
                    if (centralTransfer.exists(centralCpPath, zipFileName)) {
                        fileFound = true;
                        break;
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки SMB при проверке, пробуем в следующий раз
                    AppLogger.warn("Ошибка проверки файла " + zipFileName + ": " + e.getMessage());
                }

                AppLogger.info("Ожидаем появления файла " + zipFileName + " на центральном сервере...");
                try {
                    Thread.sleep(COPY_POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (!fileFound) {
                AppLogger.error("Файл " + zipFileName + " так и не появился на центральном сервере.");
                allSuccess = false;
                continue;
            }

            long copiedBytes = copyExchangeFile(centralServer, peripheral, zipFileName, "CP");
            if (copiedBytes > 0) {
                filesCopiedCount++;
                totalCopiedBytes += copiedBytes;
                ExchangeResult loadResult = loadToPeriphery(peripheral, centralServer);
                if (!loadResult.isSuccess()) allSuccess = false;
            } else {
                allSuccess = false;
            }
        }

        if (filesCopiedCount > 0) {
            long sizeInKb = totalCopiedBytes / 1024;
            AppLogger.info("Скопировано файлов: " + filesCopiedCount
                    + ", суммарный объём: " + sizeInKb + " КБ");
        }

        return allSuccess;
    }
    
    private static ExchangeResult loadToPeriphery(ServerInfo peripheralServer, ServerInfo centralServer) {
        String subOpId = "DP-" + (System.currentTimeMillis() % 1000);
        AppLogger.pushOperationId(subOpId);
        
        // Меняем контекст на периферийную базу
        String previousContext = AppLogger.getContextDbSign();
        AppLogger.setContextDbSign(peripheralServer.getDbSign() != null ? peripheralServer.getDbSign().trim() : "");

        try {
            AppLogger.info("==========================================================");
            AppLogger.milestone(">>> НАЧАЛО ЗАГРУЗКИ В ПЕРИФЕРИЙНУЮ БАЗУ: " + peripheralServer.getName() + " <<<");
            AppLogger.info("==========================================================");

            if (isBlank(peripheralServer.getProgram1CPath()) || isBlank(peripheralServer.getDatabasePath())) {
                return new ExchangeResult(false, "У периферийного сервера не заполнены пути к 1С или базе.");
            }
            if (isBlank(centralServer.getDbSign())) {
                return new ExchangeResult(false, "У центральной базы не заполнен DBSIGN.");
            }

            String remoteShare = "smb://" + peripheralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            SmbFileTransfer transfer = new SmbFileTransfer(peripheralServer.getIp(),
                    peripheralServer.getSmbUser(), peripheralServer.getSmbPassword());
            transfer.mkdir(remoteShare, "Log");

            String batContent = createPeripheryLoadBatContent(peripheralServer);

            String fromSign = centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "";
            String toSign = peripheralServer.getDbSign() != null ? peripheralServer.getDbSign().trim() : "";
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                String logFileName = generateLogFileName(fromSign, toSign);

                String prmContent = createPeripheryLoadPrmContent(logFileName, centralServer.getDbSign().trim());
                
                // Загружаем файлы разом
                transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");
                transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
                
                AppLogger.info("Загружены unload.bat и aeparams.prm; Попытка загрузки " + attempt + " из " + MAX_RETRIES);

                RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(peripheralServer, "c:\\ae\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + execResult.getShortString());

                boolean logStarted = LogMonitor.waitForLogCreation(transfer, logShare, logFileName, 10);
                if (!logStarted) {
                    AppLogger.error("Не удалось начать загрузку (попытка " + attempt + ").");
                    if (attempt == MAX_RETRIES) {
                        return new ExchangeResult(false, "Не удалось начать загрузку на " + peripheralServer.getName());
                    }
                    Thread.sleep(10000);
                    continue;
                }

                AppLogger.info("Лог-файл создан. Ожидаем завершения загрузки...");
                LogMonitor.LogResult logResult = LogMonitor.waitAndDownload(transfer, logShare, logFileName, 1);

                if (logResult.isSuccess()) {
                    AppLogger.milestone(">>> ЗАГРУЗКА НА " + peripheralServer.getName() + " УСПЕШНО ЗАВЕРШЕНА <<<");
                    return new ExchangeResult(true, "Загрузка на периферию завершена успешно");
                } else {
                    AppLogger.warn("Попытка загрузки " + attempt + " завершилась ошибкой.");
                    if (attempt < MAX_RETRIES) {
                        AppLogger.info("Ждем 10 секунд перед повтором...");
                        Thread.sleep(10000);
                    } else {
                        return new ExchangeResult(false, "Все " + MAX_RETRIES + " попытки провалились");
                    }
                }
            }
            return new ExchangeResult(false, "Не удалось загрузить на периферию");
        } catch (Exception e) {
            AppLogger.error(">>> ОШИБКА ЗАГРУЗКИ НА " + peripheralServer.getName() + ": " + e.getMessage() + " <<<");
            return new ExchangeResult(false, "Ошибка: " + e.getMessage());
        } finally {
            AppLogger.popOperationId();
            AppLogger.setContextDbSign(previousContext);
        }
    }

    private static long copyExchangeFile(ServerInfo sourceServer, ServerInfo targetServer,
            String fileName, String folderName) {
        // Используем getShareName(), который уже умеет возвращать ручное значение, если оно задано
        String sourceShare = sourceServer.getShareName();
        String targetShare = targetServer.getShareName();

        String sourcePath = "smb://" + sourceServer.getIp() + "/" + sourceShare + "/" + folderName + "/";
        String targetPath = "smb://" + targetServer.getIp() + "/" + targetShare + "/" + folderName + "/";

        File tempDir = new File("D:\\ae\\Temp");
        if (!tempDir.exists()) tempDir.mkdirs();
        String localTempFilePath = tempDir.getAbsolutePath() + File.separator + fileName;

        try {
            SmbFileTransfer sourceTransfer = new SmbFileTransfer(sourceServer.getIp(),
                    sourceServer.getSmbUser(), sourceServer.getSmbPassword());

            // Проверяем существование файла
            if (!sourceTransfer.exists(sourcePath, fileName)) {
                AppLogger.error("Файл " + fileName + " не найден в папке " + folderName + " на " + sourceServer.getName());
                return 0;
            }

            // Скачиваем
            sourceTransfer.download(sourcePath, fileName, localTempFilePath);

            // Размер скачанного файла
            File localFile = new File(localTempFilePath);
            long sizeInBytes = localFile.length();

            // Загружаем
            SmbFileTransfer targetTransfer = new SmbFileTransfer(targetServer.getIp(),
                    targetServer.getSmbUser(), targetServer.getSmbPassword());
            targetTransfer.upload(localTempFilePath, targetPath, fileName);

            return sizeInBytes;

        } catch (Exception e) {
            AppLogger.error("Ошибка копирования " + fileName + ": " + e.getMessage());
            return 0;
        } finally {
            File tempFile = new File(localTempFilePath);
            if (tempFile.exists()) tempFile.delete();
        }
    }

    // ============================================================
    //  УТИЛИТЫ
    // ============================================================

    private static String createUnloadBatContent(ServerInfo server) {
        return "@echo off\r\n" +
                "chcp 1251 > nul\r\n" + // <-- Меняем кодовую страницу консоли на ANSI (Cp1251)
                "\"" + server.getProgram1CPath() + "\" config " +
                "/d\"" + server.getDatabasePath() + "\" /nAe /pAe " +
                "/@c:\\ae\\aeparams.prm\r\n";
    }

    private static String createUnloadPrmContent(String logFileName, String writeToString) {
        return "[General]\r\n" +
                "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                "SharedMode=1\r\nWriteTo=" + writeToString + "\r\n";
    }

    private static String createPeripheryLoadBatContent(ServerInfo server) {
        return "@echo off\r\n" +
                "\"" + server.getProgram1CPath() + "\" config " +
                "/d\"" + server.getDatabasePath() + "\" /nAe /pAe " +
                "/@c:\\ae\\aeparams.prm\r\n";
    }

    private static String createPeripheryLoadPrmContent(String logFileName, String readFrom) {
        return "[General]\r\n" +
                "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                "SharedMode=1\r\nReadFrom=" + readFrom + "\r\n";
    }

    private static String createLoadPrmContent(String logFileName, String readFrom) {
        return "[General]\r\n" +
                "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                "SharedMode=1\r\nReadFrom=" + readFrom + "\r\n";
    }

    private static String generateLogFileName(String fromDbSign, String toDbSign) {
        String ts = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
        String from = (fromDbSign == null) ? "" : fromDbSign.trim();
        String to = (toDbSign == null) ? "" : toDbSign.trim().replace(",", "_");
        return ts + "-" + from + "-" + to + ".txt";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}