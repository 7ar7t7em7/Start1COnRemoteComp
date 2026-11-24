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
            if (!checkServerAvailability(centralServer, "выгрузка из центральной базы")) {
                return new ExchangeResult(false, "Выгрузка отменена: центральный компьютер недоступен по Ping.");
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

            // Имя лога и параметры создаём один раз: при повторных запусках
            // используем уже загруженные на сервер файлы unload.bat и aeparams.prm.
            File localLog = null;
            String fromSign = centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "";
            String toSigns = writeToString;
            String logFileName = generateLogFileName(fromSign, toSigns);
            String prmContent = createUnloadPrmContent(logFileName, writeToString);

            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");
            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            AppLogger.info("unload.bat и aeparams.prm загружены на сервер. Повторные попытки будут использовать эти же файлы.");

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 1) {
                    try {
                        // Удаляем предыдущий лог, чтобы не принять старую ошибку
                        // за результат новой попытки.
                        transfer.delete(logShare, logFileName);
                    } catch (Exception e) {
                        AppLogger.error("Повтор выгрузки отменён: не удалось удалить предыдущий лог, "
                                + "чтобы исключить повторное чтение старого результата. Причина: " + e.getMessage());
                        return new ExchangeResult(false, "Не удалось подготовить лог для повторной попытки: "
                                + e.getMessage());
                    }
                    AppLogger.info("Повторная попытка выгрузки " + attempt + " из " + MAX_RETRIES
                            + " без повторной загрузки BAT/PRM.");
                } else {
                    AppLogger.info("Попытка выгрузки " + attempt + " из " + MAX_RETRIES);
                }

                RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(centralServer, "c:\\\\ae\\\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + execResult.getShortString());

                boolean logStarted = LogMonitor.waitForLogCreation(transfer, logShare, logFileName, 15);
                if (!logStarted) {
                    AppLogger.error("Лог-файл не появился (попытка " + attempt + ").");
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(10000);
                        continue;
                    }
                    return new ExchangeResult(false, "Лог 1С не появился после " + MAX_RETRIES + " попыток.");
                }

                AppLogger.info("Лог-файл создан. Ожидаем завершения обмена...");
                LogMonitor.LogResult logResult = LogMonitor.waitAndDownload(transfer, logShare, logFileName, targets.size());

                if (logResult.isSuccess()) {
                    AppLogger.info("Выгрузка успешно завершена с попытки " + attempt);
                    localLog = logResult.getLocalFile();
                    break;
                }

                AppLogger.error("Выгрузка: попытка " + attempt + " из " + MAX_RETRIES
                        + " завершилась ошибкой 1С: " + logResult.getErrorMessage());
                if (attempt < MAX_RETRIES) {
                    AppLogger.info("Повторяем запуск с теми же unload.bat и aeparams.prm через 10 секунд.");
                    Thread.sleep(10000);
                } else {
                    return new ExchangeResult(false, "Выгрузка не удалась после " + MAX_RETRIES
                            + " попыток. Последняя причина: " + logResult.getErrorMessage());
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
            if (!checkServerAvailability(peripheralServer, "выгрузка из периферийной базы")) {
                return new ExchangeResult(false, "Выгрузка отменена: периферийный компьютер недоступен по Ping.");
            }
            if (!checkServerAvailability(centralServer, "передача выгруженных данных в центральную базу")) {
                return new ExchangeResult(false, "Операция отменена: центральный компьютер недоступен по Ping.");
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

            // Загружаем BAT и PRM один раз. Все повторные запуски используют их же.
            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp866")), remoteShare, "unload.bat");
            AppLogger.info("unload.bat и aeparams.prm загружены на периферию. Повторные попытки будут использовать эти же файлы.");

            LogMonitor.LogResult successfulLogResult = null;
            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 1) {
                    try {
                        // Старый лог удаляем, иначе его маркер ошибки будет найден снова.
                        transfer.delete(logShare, logFileName);
                    } catch (Exception e) {
                        AppLogger.error("Повтор выгрузки отменён: не удалось удалить предыдущий лог, "
                                + "чтобы исключить повторное чтение старого результата. Причина: " + e.getMessage());
                        return new ExchangeResult(false, "Не удалось подготовить лог для повторной попытки: "
                                + e.getMessage());
                    }
                    AppLogger.info("Повторная попытка выгрузки " + attempt + " из " + MAX_RETRIES
                            + " без повторной загрузки BAT/PRM.");
                } else {
                    AppLogger.info("Попытка выгрузки " + attempt + " из " + MAX_RETRIES);
                }

                RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(peripheralServer, "c:\\\\ae\\\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + execResult.getShortString());

                boolean logStarted = LogMonitor.waitForLogCreation(transfer, logShare, logFileName, 15);
                if (!logStarted) {
                    AppLogger.error("Лог-файл не появился (попытка " + attempt + " из " + MAX_RETRIES + ").");
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(10000);
                        continue;
                    }
                    return new ExchangeResult(false, "Лог 1С не появился после " + MAX_RETRIES + " попыток.");
                }

                AppLogger.info("Лог-файл создан. Ожидаем завершения выгрузки...");
                LogMonitor.LogResult logResult = LogMonitor.waitAndDownload(transfer, logShare, logFileName, 1);
                if (logResult.isSuccess()) {
                    successfulLogResult = logResult;
                    AppLogger.milestone("Выгрузка на периферии завершена успешно с попытки " + attempt + ".");
                    break;
                }

                AppLogger.error("Выгрузка на периферии: попытка " + attempt + " из " + MAX_RETRIES
                        + " завершилась ошибкой 1С: " + logResult.getErrorMessage());
                if (attempt < MAX_RETRIES) {
                    AppLogger.info("Повторяем запуск с теми же unload.bat и aeparams.prm через 10 секунд.");
                    Thread.sleep(10000);
                } else {
                    return new ExchangeResult(false, "Выгрузка на периферии не удалась после " + MAX_RETRIES
                            + " попыток. Последняя причина: " + logResult.getErrorMessage());
                }
            }

            if (successfulLogResult == null) {
                return new ExchangeResult(false, "Выгрузка на периферии не завершилась успешно.");
            }

            // Перед копированием повторно проверяем оба компьютера:
            // за время выгрузки состояние сети могло измениться.
            if (!checkServerAvailability(peripheralServer, "чтение файла обмена с периферийного компьютера")
                    || !checkServerAvailability(centralServer, "копирование файла обмена на центральный компьютер")) {
                return new ExchangeResult(false, "Копирование отменено: один из компьютеров недоступен по Ping.");
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

            if (!checkServerAvailability(centralServer, "загрузка в центральную базу")) {
                return new ExchangeResult(false, "Загрузка отменена: центральный компьютер недоступен по Ping.");
            }

            String remoteShare = "smb://" + centralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            SmbFileTransfer transfer = new SmbFileTransfer(centralServer.getIp(),
                    centralServer.getSmbUser(), centralServer.getSmbPassword());
            transfer.mkdir(remoteShare, "Log");

            String batContent = createUnloadBatContent(centralServer);

            String fromSign = peripheralServer.getDbSign() != null ? peripheralServer.getDbSign().trim() : "";
            String toSign = centralServer.getDbSign() != null ? centralServer.getDbSign().trim() : "";

            // Имя лога и параметры создаём один раз: все попытки загрузки
            // используют уже переданные на сервер unload.bat и aeparams.prm.
            String logFileName = generateLogFileName(fromSign, toSign);
            String prmContent = createLoadPrmContent(logFileName, peripheralServer.getDbSign().trim());

            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");
            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            AppLogger.info("unload.bat и aeparams.prm загружены на центральный сервер. Повторные попытки будут использовать эти же файлы.");

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                if (attempt > 1) {
                    try {
                        // Удаляем предыдущий лог, чтобы не принять старый результат за новый.
                        transfer.delete(logShare, logFileName);
                    } catch (Exception e) {
                        AppLogger.error("Повторная загрузка отменена: не удалось удалить предыдущий лог. Причина: "
                                + e.getMessage());
                        return new ExchangeResult(false, "Не удалось подготовить лог для повторной попытки: "
                                + e.getMessage());
                    }
                    AppLogger.info("Повторная попытка загрузки в центр " + attempt + " из " + MAX_RETRIES
                            + " без повторной загрузки BAT/PRM.");
                } else {
                    AppLogger.info("Попытка загрузки в центр " + attempt + " из " + MAX_RETRIES);
                }

                RemoteExecutor.ExecutionResult execResult = RemoteExecutor.execute(centralServer, "c:\\\\ae\\\\unload.bat");
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
                    AppLogger.warn("Попытка загрузки в центр " + attempt + " завершилась ошибкой.");
                    if (attempt < MAX_RETRIES) {
                        Thread.sleep(10000);
                    } else {
                        return new ExchangeResult(false, "Все " + MAX_RETRIES + " попытки загрузки в центр провалились");
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

        // Центральный сервер мог стать недоступен за время выгрузки.
        if (!checkServerAvailability(centralServer, "чтение файлов обмена с центрального компьютера")) {
            return false;
        }

        for (ServerInfo peripheral : targets) {
            if (isBlank(peripheral.getIp()) || isBlank(peripheral.getSmbUser())) {
                AppLogger.warn("Пропуск " + peripheral.getName() + ": не заполнены данные для SMB.");
                allSuccess = false;
                continue;
            }

            // Если конкретная периферия недоступна, пропускаем только её;
            // остальные выбранные базы продолжаем обрабатывать.
            if (!checkServerAvailability(peripheral, "копирование файла и загрузка в периферийную базу")) {
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

            // Повторная проверка непосредственно перед передачей файла.
            if (!checkServerAvailability(centralServer, "чтение файла " + zipFileName)
                    || !checkServerAvailability(peripheral, "приём файла " + zipFileName)) {
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

            if (!checkServerAvailability(peripheralServer, "загрузка в периферийную базу")) {
                return new ExchangeResult(false, "Загрузка отменена: периферийный компьютер недоступен по Ping.");
            }

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

    /**
     * Формирует SMB-путь к каталогу назначения.
     * При заполненном networkPath ожидается UNC-путь вида
     * \\server\share\path\to\database; имя хоста из UNC не используется,
     * подключение выполняется по IP из настроек сервера.
     */
    private static String buildServerSmbPath(ServerInfo server, String folderName) {
        String networkPath = server.getNetworkPath();
        if (networkPath != null && !networkPath.isBlank()) {
            String normalized = networkPath.trim().replace('\\', '/');
            while (normalized.startsWith("/")) {
                normalized = normalized.substring(1);
            }
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }

            String[] parts = normalized.split("/+");
            // В UNC первые два компонента — имя сервера и имя общей папки.
            // Используем их без подмены на IP: сетевой путь может содержать
            // DNS-имя (например, \\\\bjtrade.lan\\dfsroot\\1C\\Bases\\Dakort).
            if (parts.length < 2) {
                throw new IllegalArgumentException("Некорректный сетевой путь сервера "
                        + server.getName() + ": " + networkPath
                        + ". Ожидается UNC-путь с именем сервера и общей папки.");
            }

            StringBuilder smbPath = new StringBuilder("smb://")
                    .append(parts[0]).append('/').append(parts[1]).append('/');
            for (int i = 2; i < parts.length; i++) {
                smbPath.append(parts[i]).append('/');
            }
            smbPath.append(folderName).append('/');
            return smbPath.toString();
        }

        // Обратная совместимость для серверов, у которых сетевой путь не задан.
        String share = server.getShareName();
        if (share == null || share.isBlank()) {
            throw new IllegalArgumentException("Не задано имя SMB-ресурса или сетевой путь для сервера "
                    + server.getName());
        }
        return "smb://" + server.getIp() + "/" + share + "/" + folderName + "/";
    }

    private static long copyExchangeFile(ServerInfo sourceServer, ServerInfo targetServer,
            String fileName, String folderName) {
        // Для источника и назначения используем полный сетевой путь из настроек сервера.
        // Это важно, когда имя SMB-ресурса отличается от имени каталога базы.
        String sourcePath = null;
        String targetPath = null;

        File tempDir = new File("D:\\ae\\Temp");
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            AppLogger.error("Этап 1/5: не удалось создать временную папку: " + tempDir.getAbsolutePath());
            return 0;
        }
        String localTempFilePath = tempDir.getAbsolutePath() + File.separator + fileName;
        String stage = "подготовка путей";

        try {
            sourcePath = buildServerSmbPath(sourceServer, folderName);
            targetPath = buildServerSmbPath(targetServer, folderName);
            AppLogger.info("Копирование " + fileName + ": источник на сервере "
                    + sourceServer.getName() + " — " + sourcePath + fileName);
            AppLogger.info("Копирование " + fileName + ": локальный временный файл — " + localTempFilePath);
            AppLogger.info("Копирование " + fileName + ": назначение на сервере "
                    + targetServer.getName() + " — " + targetPath + fileName);

            SmbFileTransfer sourceTransfer = new SmbFileTransfer(sourceServer.getIp(),
                    sourceServer.getSmbUser(), sourceServer.getSmbPassword());

            // Этап 1. Проверяем наличие исходного файла.
            stage = "проверка наличия файла на исходном сервере";
            AppLogger.info("Этап 1/5: проверяем наличие " + fileName + " на " + sourceServer.getName() + "...");
            if (!sourceTransfer.exists(sourcePath, fileName)) {
                AppLogger.error("Этап 1/5: файл " + fileName + " не найден: " + sourcePath);
                return 0;
            }
            long sourceSize = sourceTransfer.getSize(sourcePath, fileName);
            AppLogger.info("Этап 1/5: файл найден на исходном сервере. Размер: " + sourceSize + " байт.");

            // Этап 2. Скачиваем файл на компьютер с приложением.
            stage = "скачивание файла с исходного сервера на локальный компьютер";
            AppLogger.info("Этап 2/5: начинаем скачивание " + fileName + " на локальный компьютер...");
            sourceTransfer.download(sourcePath, fileName, localTempFilePath);

            // Этап 3. Проверяем локальный файл и его размер.
            stage = "проверка локального временного файла";
            File localFile = new File(localTempFilePath);
            if (!localFile.isFile()) {
                throw new IllegalStateException("После скачивания локальный файл не найден: " + localTempFilePath);
            }
            long sizeInBytes = localFile.length();
            AppLogger.info("Этап 3/5: файл сохранён локально. Размер: " + sizeInBytes
                    + " байт (на источнике: " + sourceSize + " байт).");
            if (sizeInBytes != sourceSize) {
                throw new IllegalStateException("Размер локального файла не совпадает с размером исходного: "
                        + sizeInBytes + " против " + sourceSize + " байт.");
            }

            // Этап 4. Загружаем локальный файл на целевой сервер.
            stage = "загрузка локального файла на целевой сервер";
            AppLogger.info("Этап 4/5: начинаем загрузку " + fileName + " на " + targetServer.getName()
                    + " по пути " + targetPath);
            SmbFileTransfer targetTransfer = new SmbFileTransfer(targetServer.getIp(),
                    targetServer.getSmbUser(), targetServer.getSmbPassword());
            targetTransfer.upload(localTempFilePath, targetPath, fileName);
            AppLogger.info("Этап 4/5: передача " + fileName + " на целевой сервер завершилась без ошибки.");

            // Этап 5. Проверяем, что файл появился на целевом сервере и имеет ожидаемый размер.
            stage = "проверка файла на целевом сервере после загрузки";
            AppLogger.info("Этап 5/5: проверяем наличие и размер " + fileName + " на целевом сервере...");
            if (!targetTransfer.exists(targetPath, fileName)) {
                throw new IllegalStateException("После загрузки файл не обнаружен на целевом сервере: "
                        + targetPath + fileName);
            }
            long targetSize = targetTransfer.getSize(targetPath, fileName);
            if (targetSize != sizeInBytes) {
                throw new IllegalStateException("Размер файла на целевом сервере не совпадает: "
                        + targetSize + " против " + sizeInBytes + " байт.");
            }
            AppLogger.info("Этап 5/5: файл подтверждён на целевом сервере. Размер: " + targetSize + " байт.");
            return sizeInBytes;

        } catch (Exception e) {
            AppLogger.error("Ошибка копирования " + fileName + " на этапе «" + stage + "»; "
                    + "источник: " + (sourcePath == null ? "<не сформирован>" : sourcePath + fileName)
                    + "; локальный файл: " + localTempFilePath
                    + "; назначение: " + (targetPath == null ? safeServerPath(targetServer, folderName) + fileName : targetPath + fileName)
                    + ". Причина: " + e.getMessage());
            return 0;
        } finally {
            File tempFile = new File(localTempFilePath);
            if (tempFile.exists() && !tempFile.delete()) {
                AppLogger.warn("Не удалось удалить временный файл: " + localTempFilePath);
            }
        }
    }

    private static String safeServerPath(ServerInfo targetServer, String folderName) {
        try {
            return buildServerSmbPath(targetServer, folderName);
        } catch (Exception e) {
            return "<не удалось сформировать путь: " + e.getMessage() + ">";
        }
    }

    /**
     * Проверяет доступность сервера перед сетевой операцией.
     * При неудаче записывает понятное сообщение в лог.
     */
    private static boolean checkServerAvailability(ServerInfo server, String operation) {
        if (server == null || isBlank(server.getIp())) {
            String name = server == null ? "неизвестный сервер" : server.getName();
            AppLogger.error("Невозможно выполнить " + operation
                    + ": у сервера " + name + " не заполнен IP-адрес.");
            return false;
        }

        AppLogger.info("Проверяем доступность компьютера " + server.getName()
                + " (" + server.getIp() + ") перед операцией: " + operation + ".");

        if (!ServerAvailabilityChecker.isAvailable(server)) {
            AppLogger.error("Компьютер " + server.getName() + " (" + server.getIp()
                    + ") недоступен по Ping. Операция отменена: " + operation + ".");
            return false;
        }

        AppLogger.info("Компьютер " + server.getName() + " (" + server.getIp()
                + ") доступен по Ping.");
        return true;
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