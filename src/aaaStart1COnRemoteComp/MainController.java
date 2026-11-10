package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainController {

    @FXML
    private TreeView<ServerInfo> treeView;

    @FXML
    private Button readDbButton;
    
    @FXML
    private javafx.scene.control.TextArea logArea;

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Связываем UI с логгером
        AppLogger.setUiLogArea(logArea);
        AppLogger.info("Приложение запущено.");
        
        jcifs.Config.setProperty("jcifs.smb.client.attrExpirationPeriod", "0");
        
        // ==== Корневой узел (невидимый) ====
        TreeItem<ServerInfo> root = new TreeItem<>(null);
        root.setExpanded(true);
        treeView.setRoot(root);
        treeView.setShowRoot(false);

        // ==== Отображение узлов ====
        treeView.setCellFactory(_ -> {
            TreeCell<ServerInfo> cell = new TreeCell<>() {
                @Override
                protected void updateItem(ServerInfo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                        setContextMenu(null);
                    } else {
                        String display = item.getName();
                        if (display == null || display.isBlank()) {
                            display = item.getComputerName() != null ? item.getComputerName() : "(без имени)";
                        }
                        setText(display);

                        if (isFullyFilled(item)) {
                            setStyle("-fx-text-fill: green;");
                        } else {
                            setStyle("-fx-text-fill: red;");
                        }

                        MenuItem exchangeItem;
                        if (item.isCentral()) {
                            exchangeItem = new MenuItem("Выгрузить из центра - Загрузить в периферию");
                            exchangeItem.setOnAction(e -> onUnloadData(item));
                        } else {
                            exchangeItem = new MenuItem("Выгрузить из периферии - Загрузить в центр");
                            exchangeItem.setOnAction(e -> onUnloadPeriphery(item));
                        }
                        
                        MenuItem editItem = new MenuItem("Редактировать");
                        editItem.setOnAction(e -> editServer(item));

                        ContextMenu menu = new ContextMenu(exchangeItem, new SeparatorMenuItem(), editItem);
                        setContextMenu(menu);
                    }
                }
            };

            // Двойной клик
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    event.consume();
                    ServerInfo selected = getSelectedServer();
                    if (selected != null) {
                        openDialog(selected, true);
                    }
                }
            });

            return cell;
        });

        // ==== Слушаем изменение выделения ====
        treeView.getSelectionModel().selectedItemProperty().addListener(
                (_, _, newVal) -> {
                    ServerInfo s = (newVal == null) ? null : newVal.getValue();
                    updateReadDbButtonState(s);
                });
        readDbButton.setDisable(true);

        // ==== Загружаем данные ====
        try {
            servers.addAll(ServerStorage.load());
            rebuildTree();
        } catch (IOException e) {
            showWarning("Не удалось загрузить список серверов:\n" + e.getMessage());
        }
    }

    // ============================================================
    //  РАБОТА С ДЕРЕВОМ
    // ============================================================

    private List<String> getPossibleParentNames(ServerInfo exclude) {
        List<String> result = new ArrayList<>();
        for (ServerInfo s : servers) {
            if (s.isCentral() && s != exclude && notBlank(s.getName())) {
                result.add(s.getName());
            }
        }
        return result;
    }
    
    private void rebuildTree() {
        TreeItem<ServerInfo> root = treeView.getRoot();
        root.getChildren().clear();

        for (ServerInfo s : servers) {
            if (s.getParentName() == null || s.getParentName().isBlank()) {
                TreeItem<ServerInfo> item = new TreeItem<>(s);
                item.setExpanded(true);
                root.getChildren().add(item);
            }
        }

        for (ServerInfo s : servers) {
            String parent = s.getParentName();
            if (parent == null || parent.isBlank()) continue;

            TreeItem<ServerInfo> parentItem = findTreeItem(root, parent);
            if (parentItem != null) {
                TreeItem<ServerInfo> child = new TreeItem<>(s);
                parentItem.getChildren().add(child);
            } else {
                TreeItem<ServerInfo> orphan = new TreeItem<>(s);
                root.getChildren().add(orphan);
            }
        }
    }

    private TreeItem<ServerInfo> findTreeItem(TreeItem<ServerInfo> node, String parentName) {
        for (TreeItem<ServerInfo> child : node.getChildren()) {
            ServerInfo s = child.getValue();
            if (s != null && parentName.equals(s.getName())) {
                return child;
            }
            TreeItem<ServerInfo> found = findTreeItem(child, parentName);
            if (found != null) return found;
        }
        return null;
    }

    private ServerInfo getSelectedServer() {
        TreeItem<ServerInfo> item = treeView.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private void updateReadDbButtonState(ServerInfo selected) {
        boolean enabled = (selected != null) && selected.isCentral();
        readDbButton.setDisable(!enabled);
    }

    // ============================================================
    //  КНОПКИ
    // ============================================================

    @FXML
    private void onAdd() {
        ServerInfo newServer = new ServerInfo();
        ServerEditDialog dialog = new ServerEditDialog(getStage(), newServer, false, getPossibleParentNames(newServer));
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            dialog.applyToServer(newServer);
            servers.add(newServer);
            rebuildTree();
            saveServers();
        }
    }

    @FXML
    private void onEdit() {
        ServerInfo selected = getSelectedServer();
        if (selected == null) {
            showWarning("Выберите сервер для редактирования.");
            return;
        }
        editServer(selected);
    }
    
    private void editServer(ServerInfo server) {
        if (server == null) return;

        ServerEditDialog dialog = new ServerEditDialog(getStage(), server, false, getPossibleParentNames(server));
        dialog.showAndWait();
        
        if (dialog.isOkClicked()) {
            String oldName = server.getName();
            dialog.applyToServer(server);
            String newName = server.getName();

            if (oldName != null && !oldName.equals(newName)) {
                for (ServerInfo s : servers) {
                    if (oldName.equals(s.getParentName())) {
                        s.setParentName(newName);
                    }
                }
            }
            rebuildTree();
            saveServers();
            selectByName(server.getName());
        }
    }

    @FXML
    private void onDelete() {
        ServerInfo selected = getSelectedServer();
        if (selected == null) {
            showWarning("Выберите сервер для удаления.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Удаление");
        confirm.setHeaderText("Удалить сервер \"" + selected.getName() + "\"?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                servers.remove(selected);
                rebuildTree();
                saveServers();
            }
        });
    }

    @FXML
    private void onReadDatabase() {
        ServerInfo selected = getSelectedServer();
        if (selected == null || !selected.isCentral()) {
            showWarning("Выберите центральный сервер для чтения базы данных.");
            return;
        }
        if (isBlank(selected.getIp()) || isBlank(selected.getDatabaseName())) {
            showWarning("У выбранного сервера не заполнены IP-адрес или имя базы данных.");
            return;
        }

        DbLoginDialog loginDialog = new DbLoginDialog(getStage());
        loginDialog.showAndWait();
        if (!loginDialog.isOkClicked()) return;

        try {
            DatabaseReader.QueryResult result = DatabaseReader.readDbSetTable(
                    selected.getIp(), selected.getDatabaseName(),
                    loginDialog.getUsername(), loginDialog.getPassword());

            List<DbSetRow> confirmedRows = new ArrayList<>();
            String serverInfo = selected.getComputerName() + " (" + selected.getIp() + ")";
            DatabaseResultWindow window = new DatabaseResultWindow(getStage(), result, serverInfo, selected.getDatabaseName(), confirmedRows);
            window.showAndWait();

            if (confirmedRows.isEmpty()) return;

            List<ServerInfo> newServers = new ArrayList<>();
            for (DbSetRow row : confirmedRows) {
                ServerInfo s = new ServerInfo();
                s.setName(row.get("dbdescr"));
                s.setDbSign(row.get("DBSIGN"));
                s.setComputerName("");
                s.setIp("");
                s.setProgram1CPath("");
                s.setDatabasePath("");
                s.setDatabaseName("");
                s.setCentral(false);
                s.setParentName(selected.getName());
                newServers.add(s);
            }

            servers.addAll(newServers);
            rebuildTree();
            saveServers();

            TreeItem<ServerInfo> parentItem = findTreeItem(treeView.getRoot(), selected.getName());
            if (parentItem != null) {
                parentItem.setExpanded(true);
                treeView.getSelectionModel().select(parentItem);
            }

        } catch (SQLException e) {
            showWarning("Не удалось прочитать базу данных:\n" + e.getMessage());
        }
    }

    private void openDialog(ServerInfo server, boolean readOnly) {
        boolean effectiveReadOnly = readOnly || server.isCentral();
        ServerEditDialog dialog = new ServerEditDialog(getStage(), server, effectiveReadOnly, getPossibleParentNames(server));
        dialog.showAndWait();
        
        if (!effectiveReadOnly && dialog.isOkClicked()) {
            dialog.applyToServer(server);
            rebuildTree();
            saveServers();
            selectByName(server.getName());
        }
    }

    private void selectByName(String name) {
        if (name == null) return;
        TreeItem<ServerInfo> item = findTreeItem(treeView.getRoot(), name);
        if (item != null) {
            treeView.getSelectionModel().select(item);
        }
    }

    private boolean isFullyFilled(ServerInfo s) {
        return notBlank(s.getName()) && notBlank(s.getDbSign()) && notBlank(s.getComputerName())
                && notBlank(s.getIp()) && notBlank(s.getProgram1CPath())
                && notBlank(s.getDatabasePath()) && notBlank(s.getDatabaseName());
    }

    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private boolean isBlank(String s) { return s == null || s.isBlank(); }

    private void saveServers() {
        try {
            ServerStorage.save(servers);
        } catch (IOException e) {
            showWarning("Не удалось сохранить список серверов:\n" + e.getMessage());
        }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Внимание");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private Stage getStage() {
        return (Stage) treeView.getScene().getWindow();
    }
    
    private List<ServerInfo> findChildren(ServerInfo parent) {
        List<ServerInfo> result = new ArrayList<>();
        for (ServerInfo s : servers) {
            if (parent.getName() != null && parent.getName().equals(s.getParentName())) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Выполняет выгрузку данных из центральной базы (с ретраями).
     */
    private void performUnload(ServerInfo centralServer, List<ServerInfo> targets) {
        String mainOpId = "EX-" + (System.currentTimeMillis() % 100000);
        AppLogger.pushOperationId(mainOpId);

        try {
            List<String> exportedFiles = new ArrayList<>();
            AppLogger.info("==========================================================");
            AppLogger.info("НАЧАЛО ПРОЦЕССА ВЫГРУЗКИ ИЗ ЦЕНТРА: " + centralServer.getName());
            AppLogger.info("==========================================================");

            if (targets.isEmpty()) {
                showWarning("Не выбрано ни одной базы для выгрузки.");
                return;
            }
            if (isBlank(centralServer.getProgram1CPath()) || isBlank(centralServer.getDatabasePath())) {
                showWarning("У центрального сервера не заполнены пути к 1С или базе.");
                return;
            }
            if (isBlank(centralServer.getSmbUser()) || isBlank(centralServer.getSmbPassword())) {
                showWarning("У центрального сервера не заполнены логин/пароль SMB/WMI.");
                return;
            }

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
                showWarning("Ни у одной из выбранных баз не заполнен DBSIGN.");
                return;
            }
            String writeToString = writeTo.toString();

            String batContent = "@echo off\r\n" +
                    "\"" + centralServer.getProgram1CPath() + "\" config " +
                    "/d\"" + centralServer.getDatabasePath() + "\" /nAe /pAe " +
                    "/@c:\\ae\\aeparams.prm\r\n";

            String smbUser = centralServer.getSmbUser();
            String smbPass = centralServer.getSmbPassword();
            String remoteShare = "smb://" + centralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            SmbFileTransfer transfer = new SmbFileTransfer(centralServer.getIp(), smbUser, smbPass);
            transfer.mkdir(remoteShare, "Log");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");

            File localLog = null;
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                AppLogger.info("Попытка выгрузки " + attempt + " из " + maxRetries);
                
                String timestamp = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
                String logFileName = timestamp + "aelog.txt";
                
                String prmContent = "[General]\r\n" +
                        "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                        "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                        "SharedMode=1\r\nWriteTo=" + writeToString + "\r\n";
                transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");

                // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД ЗАПУСКА
                String result = runRemoteBat(centralServer, "c:\\ae\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + result);

                boolean logStarted = false;
                for (int i = 0; i < 15; i++) {
                    Thread.sleep(1000);
                    if (transfer.exists(logShare, logFileName)) {
                        logStarted = true;
                        break;
                    }
                }

                if (!logStarted) {
                    AppLogger.error("Лог-файл не появился (попытка " + attempt + "). Вероятно, блокировка.");
                    if (attempt == maxRetries) {
                        showWarning("Не удалось начать выгрузку. База заблокирована?");
                        return;
                    }
                    Thread.sleep(10000);
                    continue;
                }

                AppLogger.info("Лог-файл создан. 1С начала обмен. Ожидаем завершения...");
                localLog = waitAndDownloadLog(transfer, logShare, logFileName, targets.size());

                if (localLog != null) {
                    AppLogger.info("Выгрузка успешно завершена с попытки " + attempt);
                    break;
                } else {
                    AppLogger.warn("Попытка " + attempt + " завершилась ошибкой (смотрите лог 1С).");
                    if (attempt < maxRetries) {
                        AppLogger.info("Ждем 10 секунд перед повтором...");
                        Thread.sleep(10000);
                    } else {
                        AppLogger.error("Все " + maxRetries + " попытки провалились.");
                        showWarning("Выгрузка не удалась после " + maxRetries + " попыток.");
                        return;
                    }
                }
            }

            boolean allSuccess = true;

            if (localLog != null) {
                AppLogger.info("Начинаем копирование и загрузку на периферийные серверы...");
                Thread.sleep(3000); 
                
                for (ServerInfo peripheral : targets) {
                    if (isBlank(peripheral.getIp()) || isBlank(peripheral.getSmbUser())) {
                        AppLogger.warn("Пропуск " + peripheral.getName() + ": не заполнены данные для SMB.");
                        allSuccess = false;
                        continue;
                    }
                    
                    String zipFileName = peripheral.getDbSign().trim() + "0.zip";
                    exportedFiles.add(zipFileName);
                    
                    String centralShare = centralServer.getShareName();
                    String centralCpPath = "smb://" + centralServer.getIp() + "/" + centralShare + "/CP/";
                    SmbFileTransfer centralTransfer = new SmbFileTransfer(centralServer.getIp(), centralServer.getSmbUser(), centralServer.getSmbPassword());
                    
                    boolean fileFound = false;
                    for (int i = 0; i < 15; i++) {
                        if (centralTransfer.exists(centralCpPath, zipFileName)) {
                            fileFound = true;
                            break;
                        }
                        AppLogger.info("Ожидаем появления файла " + zipFileName + " на центральном сервере...");
                        Thread.sleep(1000);
                    }

                    if (!fileFound) {
                        AppLogger.error("Файл " + zipFileName + " так и не появился на центральном сервере.");
                        allSuccess = false;
                        continue;
                    }
                    
                    boolean copied = copyExchangeFileCustom(centralServer, peripheral, zipFileName, "CP");
                    if (copied) {
                        boolean loadSuccess = performLoadToPeriphery(peripheral, centralServer.getDbSign());
                        if (!loadSuccess) allSuccess = false;
                    } else {
                        allSuccess = false;
                    }
                }
            } else {
                allSuccess = false;
                AppLogger.warn("Выгрузка не завершилась успешно, загрузка на периферию пропущена.");
            }

            AppLogger.info("==========================================================");
            if (allSuccess) {
                AppLogger.info("УСПЕШНОЕ ЗАВЕРШЕНИЕ ПРОЦЕССА ВЫГРУЗКИ ИЗ ЦЕНТРА И ЗАГРУЗКИ В ПЕРИФЕРИЮ");
            } else {
                AppLogger.error("ПРОЦЕСС ЗАВЕРШЕН С ОШИБКАМИ (не все базы удалось загрузить)");
            }
            AppLogger.info("Файлы выгрузки: " + String.join(", ", exportedFiles));
            AppLogger.info("==========================================================");

        } catch (Exception e) {
            AppLogger.error("КРИТИЧЕСКАЯ ОШИБКА В ПРОЦЕССЕ ВЫГРУЗКИ: " + e.getMessage());
            e.printStackTrace();
        } finally {
            AppLogger.popOperationId(); 
        }
    }

    private void onUnloadData(ServerInfo centralServer) {
        List<ServerInfo> children = findChildren(centralServer);
        if (children.isEmpty()) {
            showWarning("У сервера \"" + centralServer.getName() + "\" нет подчинённых баз.");
            return;
        }

        SelectChildServersWindow window = new SelectChildServersWindow(getStage(), "Выгрузка данных: " + centralServer.getName(), children);
        window.showAndWait();

        if (!window.isOkClicked()) return;

        List<ServerInfo> selected = window.getSelectedServers();
        if (selected.isEmpty()) return;

        Task<Void> unloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                performUnload(centralServer, selected);
                return null;
            }
        };

        unloadTask.setOnFailed(e -> {
            Throwable ex = unloadTask.getException();
            AppLogger.error("Критическая ошибка в фоновом потоке: " + ex.getMessage());
            javafx.application.Platform.runLater(() -> showWarning("Произошла ошибка при выгрузке. Подробности в логе."));
        });

        Thread thread = new Thread(unloadTask);
        thread.setDaemon(true);
        thread.start();
        
        AppLogger.info("Процесс выгрузки запущен в фоновом режиме.");
    }
    
    private File waitAndDownloadLog(SmbFileTransfer transfer, String remoteShare, String logFileName, int expectedOperations) {
        final int maxWaitSeconds = 300; 
        final int pollIntervalMs = 1000;
        long startTime = System.currentTimeMillis();
        
        AppLogger.info("Ожидаем завершения работы 1С по лог-файлу: " + logFileName + " (ожидаем операций: " + expectedOperations + ")");

        while (true) {
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > maxWaitSeconds) {
                AppLogger.error("Таймаут ожидания завершения 1С (" + maxWaitSeconds + " сек).");
                return null;
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            try {
                if (!transfer.exists(remoteShare, logFileName)) {
                    continue;
                }

                String logContent = transfer.readFile(remoteShare, logFileName, "Cp866");
                
                int successCount = countOccurrences(logContent, "DistUplSuc") + countOccurrences(logContent, "DistDnldSuc");
                int failCount = countOccurrences(logContent, "DistDnldFail") + countOccurrences(logContent, "DistUplFail") 
                              + countOccurrences(logContent, "DistDnlErr") + countOccurrences(logContent, "DistUplErr");

                if (failCount > 0) {
                    AppLogger.error("В лог-файле найден маркер ОШИБКИ. Прерываем ожидание.");
                    return null;
                }

                if (successCount >= expectedOperations) {
                    AppLogger.info("В лог-файле найдено " + successCount + " маркеров успешного завершения. Всё готово.");
                    break;
                }
                
            } catch (Exception e) {
                // Игнорируем
            }
        }

        try {
            File localDir = new File("D:\\ae\\Logs");
            if (!localDir.exists()) localDir.mkdirs();
            String localPath = localDir.getAbsolutePath() + File.separator + logFileName;
            transfer.download(remoteShare, logFileName, localPath);
            AppLogger.info("Лог-файл 1С скачан с сервера: " + localPath);
            return new File(localPath);
        } catch (Exception e) {
            AppLogger.error("Не удалось скачать лог: " + e.getMessage());
            return null;
        }
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
    
    private boolean copyExchangeFileCustom(ServerInfo sourceServer, ServerInfo targetServer, String fileName, String folderName) {
        String sourceShare = sourceServer.getShareName();
        String targetShare = targetServer.getShareName();
        
        String sourcePath = "smb://" + sourceServer.getIp() + "/" + sourceShare + "/" + folderName + "/";
        String targetPath = "smb://" + targetServer.getIp() + "/" + targetShare + "/" + folderName + "/";

        File tempDir = new File("D:\\ae\\Temp");
        if (!tempDir.exists()) tempDir.mkdirs();
        String localTempFilePath = tempDir.getAbsolutePath() + File.separator + fileName;

        try {
            SmbFileTransfer sourceTransfer = new SmbFileTransfer(sourceServer.getIp(), sourceServer.getSmbUser(), sourceServer.getSmbPassword());
            if (!sourceTransfer.exists(sourcePath, fileName)) {
                AppLogger.error("Файл " + fileName + " не найден в папке " + folderName + " на " + sourceServer.getName());
                return false;
            }
            sourceTransfer.download(sourcePath, fileName, localTempFilePath);

            SmbFileTransfer targetTransfer = new SmbFileTransfer(targetServer.getIp(), targetServer.getSmbUser(), targetServer.getSmbPassword());
            targetTransfer.upload(localTempFilePath, targetPath, fileName);

            return true;
        } catch (Exception e) {
            AppLogger.error("Ошибка копирования " + fileName + ": " + e.getMessage());
            return false;
        } finally {
            File tempFile = new File(localTempFilePath);
            if (tempFile.exists()) tempFile.delete();
        }
    }
 
    private boolean performLoadToPeriphery(ServerInfo peripheralServer, String centralBaseCode) {
        String subOpId = "LD-" + (System.currentTimeMillis() % 1000);
        AppLogger.pushOperationId(subOpId);
        boolean success = false;

        try {
            AppLogger.info("==========================================================");
            AppLogger.info(">>> НАЧАЛО ЗАГРУЗКИ В ПЕРИФЕРИЙНУЮ БАЗУ: " + peripheralServer.getName() + " <<<");
            AppLogger.info("==========================================================");

            if (isBlank(peripheralServer.getProgram1CPath()) || isBlank(peripheralServer.getDatabasePath())) {
                AppLogger.error("У периферийного сервера не заполнены пути к 1С или базе.");
                return false;
            }
            if (isBlank(centralBaseCode)) {
                AppLogger.error("Не указан префикс центральной базы (DBSIGN).");
                javafx.application.Platform.runLater(() -> showWarning("У центральной базы не заполнен DBSIGN."));
                return false;
            }

            String aePath = "c:\\ae\\";
            String smbUser = peripheralServer.getSmbUser();
            String smbPass = peripheralServer.getSmbPassword();
            String remoteShare = "smb://" + peripheralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            String batContent = "@echo off\r\n" +
                    "\"" + peripheralServer.getProgram1CPath() + "\" config " +
                    "/d\"" + peripheralServer.getDatabasePath() + "\" /nAe /pAe " +
                    "/@" + aePath + "aeparams.prm\r\n";

            SmbFileTransfer transfer = new SmbFileTransfer(peripheralServer.getIp(), smbUser, smbPass);
            transfer.mkdir(remoteShare, "Log");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");

            File localLog = null;
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                AppLogger.info("Попытка загрузки " + attempt + " из " + maxRetries);
                
                String timestamp = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
                String logFileName = timestamp + "aelog.txt";

                String prmContent = "[General]\r\n" +
                        "Output=" + aePath + "Log\\" + logFileName + "\r\n" +
                        "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                        "SharedMode=1\r\nReadFrom=" + centralBaseCode.trim() + "\r\n"; 
                transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");

                // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД ЗАПУСКА
                String result = runRemoteBat(peripheralServer, "c:\\ae\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + result);

                boolean logStarted = false;
                for (int i = 0; i < 10; i++) {
                    Thread.sleep(1000);
                    if (transfer.exists(logShare, logFileName)) {
                        logStarted = true;
                        break;
                    }
                }

                if (!logStarted) {
                    AppLogger.error("Не удалось начать загрузку (попытка " + attempt + ").");
                    if (attempt == maxRetries) {
                        javafx.application.Platform.runLater(() -> showWarning("Не удалось начать загрузку на " + peripheralServer.getName()));
                        return false;
                    }
                    Thread.sleep(10000);
                    continue;
                }

                AppLogger.info("Лог-файл создан. Ожидаем завершения загрузки...");
                localLog = waitAndDownloadLog(transfer, logShare, logFileName, 1);
                
                if (localLog != null) {
                    AppLogger.info(">>> ЗАГРУЗКА НА " + peripheralServer.getComputerName() + " УСПЕШНО ЗАВЕРШЕНА <<<");
                    success = true;
                    break;
                } else {
                    AppLogger.warn("Попытка загрузки " + attempt + " завершилась ошибкой.");
                    if (attempt < maxRetries) {
                        AppLogger.info("Ждем 10 секунд перед повтором...");
                        Thread.sleep(10000);
                    } else {
                        AppLogger.error("Все " + maxRetries + " попытки провалились.");
                    }
                }
            }

        } catch (Exception e) {
            AppLogger.error(">>> ОШИБКА ЗАГРУЗКИ НА " + peripheralServer.getName() + ": " + e.getMessage() + " <<<");
        } finally {
            AppLogger.popOperationId(); 
        }
        return success;
    }
    
    private void onUnloadPeriphery(ServerInfo peripheralServer) {
        ServerInfo centralServer = null;
        for (ServerInfo s : servers) {
            if (s.getName() != null && s.getName().equals(peripheralServer.getParentName())) {
                centralServer = s;
                break;
            }
        }

        if (centralServer == null) {
            showWarning("Не найдена центральная база для " + peripheralServer.getName());
            return;
        }

        final ServerInfo finalCentral = centralServer;

        Task<Void> unloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                performUnloadFromPeriphery(peripheralServer, finalCentral);
                return null;
            }
        };

        unloadTask.setOnFailed(e -> {
            Throwable ex = unloadTask.getException();
            AppLogger.error("Критическая ошибка в фоновом потоке: " + ex.getMessage());
            javafx.application.Platform.runLater(() -> showWarning("Произошла ошибка при выгрузке из периферии. Подробности в логе."));
        });

        Thread thread = new Thread(unloadTask);
        thread.setDaemon(true);
        thread.start();
    }
    
    private void performUnloadFromPeriphery(ServerInfo peripheralServer, ServerInfo centralServer) {
        String mainOpId = "UP-" + (System.currentTimeMillis() % 100000);
        AppLogger.pushOperationId(mainOpId);

        try {
            AppLogger.info("==========================================================");
            AppLogger.info("НАЧАЛО ПРОЦЕССА ВЫГРУЗКИ ИЗ ПЕРИФЕРИИ: " + peripheralServer.getName());
            AppLogger.info("Центральная база: " + centralServer.getName());
            AppLogger.info("==========================================================");

            if (isBlank(peripheralServer.getProgram1CPath()) || isBlank(peripheralServer.getDatabasePath())) {
                AppLogger.error("У периферийного сервера не заполнены пути.");
                return;
            }
            if (isBlank(peripheralServer.getSmbUser()) || isBlank(peripheralServer.getSmbPassword())) {
                AppLogger.error("У периферийного сервера не заполнены логин/пароль SMB.");
                return;
            }

            String timestamp = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
            String logFileName = timestamp + "aelog.txt";

            String prmContent = "[General]\r\n" +
                    "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                    "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                    "SharedMode=1\r\nWriteTo=" + centralServer.getDbSign().trim() + "\r\n";

            String batContent = "@echo off\r\n" +
                    "\"" + peripheralServer.getProgram1CPath() + "\" config " +
                    "/d\"" + peripheralServer.getDatabasePath() + "\" /nAe /pAe " +
                    "/@c:\\ae\\aeparams.prm\r\n";

            String smbUser = peripheralServer.getSmbUser();
            String smbPass = peripheralServer.getSmbPassword();
            String remoteShare = "smb://" + peripheralServer.getIp() + "/ae$/";

            SmbFileTransfer transfer = new SmbFileTransfer(peripheralServer.getIp(), smbUser, smbPass);
            transfer.mkdir(remoteShare, "Log");
            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp866")), remoteShare, "unload.bat");

            // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД ЗАПУСКА
            String result = runRemoteBat(peripheralServer, "c:\\ae\\unload.bat");
            AppLogger.info("Результат запуска BAT: " + result);

            String logShare = remoteShare + "Log/";
            boolean logStarted = false;
            for (int i = 0; i < 15; i++) {
                Thread.sleep(1000);
                if (transfer.exists(logShare, logFileName)) {
                    logStarted = true;
                    break;
                }
            }

            if (!logStarted) {
                AppLogger.error("Не удалось начать выгрузку на " + peripheralServer.getName() + ". База заблокирована?");
                return;
            }

            AppLogger.info("Лог-файл создан. Ожидаем завершения выгрузки...");
            waitAndDownloadLog(transfer, logShare, logFileName, 1);

            String fileName = peripheralServer.getDbSign().trim() + "1.zip";
            
            AppLogger.info("Начинаем копирование " + fileName + " на центральный сервер...");
            boolean copied = copyExchangeFileCustom(peripheralServer, centralServer, fileName, "PC");
            
            boolean allSuccess = true;
            if (copied) {
                boolean loadSuccess = performLoadToCenter(centralServer, peripheralServer);
                if (!loadSuccess) allSuccess = false;
            } else {
                allSuccess = false;
            }

            AppLogger.info("==========================================================");
            if (allSuccess) {
                AppLogger.info("УСПЕШНОЕ ЗАВЕРШЕНИЕ ПРОЦЕССА ВЫГРУЗКИ ИЗ ПЕРИФЕРИИ И ЗАГРУЗКИ В ЦЕНТР");
            } else {
                AppLogger.error("ПРОЦЕСС ЗАВЕРШЕН С ОШИБКАМИ (не удалось загрузить в центр)");
            }
            AppLogger.info("Файл выгрузки: " + fileName);
            AppLogger.info("==========================================================");

        } catch (Exception e) {
            AppLogger.error("КРИТИЧЕСКАЯ ОШИБКА: " + e.getMessage());
        } finally {
            AppLogger.popOperationId();
        }
    }
    
    private boolean performLoadToCenter(ServerInfo centralServer, ServerInfo peripheralServer) {
        String subOpId = "LC-" + (System.currentTimeMillis() % 1000);
        AppLogger.pushOperationId(subOpId);
        boolean success = false;

        try {
            AppLogger.info(">>> НАЧАЛО ЗАГРУЗКИ В ЦЕНТРАЛЬНУЮ БАЗУ: " + centralServer.getName() + " <<<");

            String smbUser = centralServer.getSmbUser();
            String smbPass = centralServer.getSmbPassword();
            String remoteShare = "smb://" + centralServer.getIp() + "/ae$/";
            String logShare = remoteShare + "Log/";

            String batContent = "@echo off\r\n" +
                    "\"" + centralServer.getProgram1CPath() + "\" config " +
                    "/d\"" + centralServer.getDatabasePath() + "\" /nAe /pAe " +
                    "/@c:\\ae\\aeparams.prm\r\n";

            SmbFileTransfer transfer = new SmbFileTransfer(centralServer.getIp(), smbUser, smbPass);
            transfer.mkdir(remoteShare, "Log");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp1251")), remoteShare, "unload.bat");

            File localLog = null;
            int maxRetries = 3;

            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                AppLogger.info("Попытка загрузки в центр " + attempt + " из " + maxRetries);
                
                String timestamp = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
                String logFileName = timestamp + "aelog.txt";

                String prmContent = "[General]\r\n" +
                        "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                        "Quit=1\r\nAutoExchange=1\r\n[AutoExchange]\r\n" +
                        "SharedMode=1\r\nReadFrom=" + peripheralServer.getDbSign().trim() + "\r\n";
                transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");

                // ИСПОЛЬЗУЕМ НОВЫЙ МЕТОД ЗАПУСКА
                String result = runRemoteBat(centralServer, "c:\\ae\\unload.bat");
                AppLogger.info("Результат запуска BAT: " + result);

                boolean logStarted = false;
                for (int i = 0; i < 60; i++) {
                    Thread.sleep(1000);
                    if (transfer.exists(logShare, logFileName)) {
                        logStarted = true;
                        break;
                    }
                }

                if (!logStarted) {
                    AppLogger.error("Не удалось начать загрузку в центр (попытка " + attempt + ").");
                    if (attempt == maxRetries) return false;
                    Thread.sleep(10000);
                    continue;
                }

                localLog = waitAndDownloadLog(transfer, logShare, logFileName, 1);
                
                if (localLog != null) {
                    AppLogger.info(">>> ЗАГРУЗКА В ЦЕНТР УСПЕШНО ЗАВЕРШЕНА <<<");
                    success = true;
                    break;
                } else {
                    AppLogger.warn("Попытка загрузки в центр " + attempt + " завершилась ошибкой.");
                    if (attempt < maxRetries) {
                        AppLogger.info("Ждем 10 секунд перед повтором...");
                        Thread.sleep(10000);
                    } else {
                        AppLogger.error("Все " + maxRetries + " попытки провалились.");
                    }
                }
            }

        } catch (Exception e) {
            AppLogger.error("Ошибка при загрузке в центр: " + e.getMessage());
        } finally {
            AppLogger.popOperationId();
        }
        return success;
    }
    
    /**
     * Запускает BAT-файл на удаленном сервере (через PsExec или WMI).
     */
    private String runRemoteBat(ServerInfo server, String remoteBatPath) throws Exception {
        if (server.isUsePsExec()) {
            // === ЗАПУСК ЧЕРЕЗ PSEXEC (в интерактивной сессии) ===
            String psexecPath = "D:\\ae\\Utils\\PSTools\\PsExec.exe"; // ПУТЬ НА ВАШЕМ ПК
            
            List<String> cmdList = new ArrayList<>();
            cmdList.add(psexecPath);
            cmdList.add("-accepteula");
            cmdList.add("-i"); // Интерактивная сессия
            cmdList.add("\\\\" + server.getIp());
            cmdList.add("-u");
            cmdList.add(server.getSmbUser());
            cmdList.add("-p");
            cmdList.add(server.getSmbPassword());
            cmdList.add(remoteBatPath);

            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), "Cp866"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }
            
            boolean finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "PsExec: Таймаут выполнения";
            }
            
            int exitCode = process.exitValue();
            return "PsExec (ExitCode=" + exitCode + "): " + output.toString().trim().replace("\r\n", " ");
            
        } else {
            // === ЗАПУСК ЧЕРЕЗ WMI ===
            WmiBatRunner wmi = new WmiBatRunner(server.getIp(), server.getSmbUser(), server.getSmbPassword(), 60);
            String rawResult = wmi.runCommand(remoteBatPath);
            
            // Извлекаем ProcessId и ReturnValue для короткого вывода
            long pid = -1;
            String returnValue = "N/A";
            
            java.util.regex.Matcher mPid = java.util.regex.Pattern.compile("ProcessId\\s*=\\s*(\\d+)").matcher(rawResult);
            if (mPid.find()) pid = Long.parseLong(mPid.group(1));
            
            java.util.regex.Matcher mRet = java.util.regex.Pattern.compile("ReturnValue\\s*=\\s*(\\d+)").matcher(rawResult);
            if (mRet.find()) returnValue = mRet.group(1);
            
            // Возвращаем чистую строку
            return "WMI (PID=" + pid + ", ReturnValue=" + returnValue + ")";
        }
    }
}