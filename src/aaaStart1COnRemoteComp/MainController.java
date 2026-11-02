package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
        treeView.setShowRoot(false); // сам корень не показываем

     // ==== Отображение узлов ====
        treeView.setCellFactory(_ -> {
            TreeCell<ServerInfo> cell = new TreeCell<>() {
                @Override
                protected void updateItem(ServerInfo item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                        setContextMenu(null);   // ← нет меню для пустых ячеек
                    } else {
                        String display = item.getName();
                        if (display == null || display.isBlank()) {
                            display = item.getComputerName() != null
                                    ? item.getComputerName()
                                    : "(без имени)";
                        }
                        setText(display);

                        if (isFullyFilled(item)) {
                            setStyle("-fx-text-fill: green;");
                        } else {
                            setStyle("-fx-text-fill: red;");
                        }

                     // ==== Контекстное меню (для всех серверов) ====
                        MenuItem unloadItem = new MenuItem("Выгрузить из центра - Загрузить в периферию");
                        MenuItem loadItem = new MenuItem("Загрузить данные");
                        MenuItem editItem = new MenuItem("Редактировать");
                        
                     // Обработчик "Редактировать" — вызываем наш новый метод editServer
                        editItem.setOnAction(_ -> editServer(item));

                        unloadItem.setOnAction(_ -> {
                            if (item.isCentral()) {
                                onUnloadData(item);          // центральный — с окном выбора
                            } else {
                                onUnloadSingle(item);        // периферийный — сразу
                            }
                        });
                        loadItem.setOnAction(_ -> {
                            if (item.isCentral()) {
                                onLoadData(item);            // центральный — с окном выбора
                            } else {
                                onLoadSingle(item);          // периферийный — сразу
                            }
                        });

                        unloadItem.setOnAction(_ -> {
                            if (item.isCentral()) {
                                onUnloadData(item);          // центральный — с окном выбора
                            } else {
                                onUnloadSingle(item);        // периферийный — сразу
                            }
                        });
                        loadItem.setOnAction(_ -> {
                            if (item.isCentral()) {
                                onLoadData(item);            // центральный — с окном выбора
                            } else {
                                onLoadSingle(item);          // периферийный — сразу
                            }
                        });

                     // Собираем все пункты в меню
                        ContextMenu menu = new ContextMenu(unloadItem, loadItem, new SeparatorMenuItem(), editItem);
                        setContextMenu(menu);
                    }
                }
            };

            // 2. Вешаем фильтр на MOUSE_PRESSED — ДО того как TreeCell обработает клик
            cell.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    // Гасим событие — сворачивание не произойдёт
                    event.consume();

                    ServerInfo selected = getSelectedServer();
                    if (selected != null) {
                        openDialog(selected, true);
                    }
                }
            });

            // 3. Возвращаем ячейку из cellFactory
            return cell;
        });

        // ==== Слушаем изменение выделения — управляем кнопкой ====
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

        // Корневые узлы (без parentName)
        for (ServerInfo s : servers) {
            if (s.getParentName() == null || s.getParentName().isBlank()) {
                TreeItem<ServerInfo> item = new TreeItem<>(s);
                item.setExpanded(true);
                root.getChildren().add(item);
            }
        }

        // Дочерние узлы
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

    /** Ищет TreeItem по имени родителя (рекурсивно). */
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

    /** Возвращает ServerInfo выделенного узла (или null). */
    private ServerInfo getSelectedServer() {
        TreeItem<ServerInfo> item = treeView.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    /** Возвращает TreeItem выделенного узла (или null). */
    private TreeItem<ServerInfo> getSelectedTreeItem() {
        return treeView.getSelectionModel().getSelectedItem();
    }

    /** Включает кнопку "Прочитать БД" только если выделен центральный сервер. */
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
        // Этот метод вызывается кнопкой внизу — берем выделенный элемент
        ServerInfo selected = getSelectedServer();
        if (selected == null) {
            showWarning("Выберите сервер для редактирования.");
            return;
        }
        editServer(selected);
    }
    
    /**
     * Вспомогательный метод для редактирования конкретного сервера.
     * Вызывается и из кнопки, и из контекстного меню.
     */
    private void editServer(ServerInfo server) {
        if (server == null) return;

        ServerEditDialog dialog = new ServerEditDialog(
                getStage(),
                server,
                false,
                getPossibleParentNames(server));
        dialog.showAndWait();
        
        if (dialog.isOkClicked()) {
            String oldName = server.getName(); // запоминаем старое имя
            dialog.applyToServer(server);
            String newName = server.getName();

            // Если имя родителя изменилось — обновляем ссылки у детей
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
                    selected.getIp(),
                    selected.getDatabaseName(),
                    loginDialog.getUsername(),
                    loginDialog.getPassword());

            List<DbSetRow> confirmedRows = new ArrayList<>();

            String serverInfo = selected.getComputerName() + " (" + selected.getIp() + ")";
            DatabaseResultWindow window = new DatabaseResultWindow(
                    getStage(), result, serverInfo, selected.getDatabaseName(), confirmedRows);
            window.showAndWait();

            if (confirmedRows.isEmpty()) return;

            // Создаём ServerInfo из выбранных строк
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
                s.setParentName(selected.getName()); // ← привязка к родителю
                newServers.add(s);
            }

            servers.addAll(newServers);
            rebuildTree();
            saveServers();

            // Раскрываем родителя, чтобы новые узлы были видны
            TreeItem<ServerInfo> parentItem = findTreeItem(treeView.getRoot(), selected.getName());
            if (parentItem != null) {
                parentItem.setExpanded(true);
                treeView.getSelectionModel().select(parentItem);
            }

        } catch (SQLException e) {
            showWarning("Не удалось прочитать базу данных:\n" + e.getMessage());
        }
    }

    // ============================================================
    //  ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ============================================================
    
    private void openDialog(ServerInfo server, boolean readOnly) {
        // Двойной клик по центральному серверу — всегда только просмотр
        boolean effectiveReadOnly = readOnly || server.isCentral();
        
        ServerEditDialog dialog = new ServerEditDialog(
                getStage(),
                server,
                effectiveReadOnly,
                getPossibleParentNames(server));
        dialog.showAndWait();
        
        if (!effectiveReadOnly && dialog.isOkClicked()) {
            dialog.applyToServer(server);
            rebuildTree();
            saveServers();
            selectByName(server.getName());
        }
    }

    /** Выделяет узел с указанным именем (если он есть в дереве). */
    private void selectByName(String name) {
        if (name == null) return;
        TreeItem<ServerInfo> item = findTreeItem(treeView.getRoot(), name);
        if (item != null) {
            treeView.getSelectionModel().select(item);
        }
    }

    /** Проверяет, что все поля ServerInfo заполнены. */
    private boolean isFullyFilled(ServerInfo s) {
        return notBlank(s.getName())
                && notBlank(s.getDbSign())
                && notBlank(s.getComputerName())
                && notBlank(s.getIp())
                && notBlank(s.getProgram1CPath())
                && notBlank(s.getDatabasePath())
                && notBlank(s.getDatabaseName());
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

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
    
    /** Находит всех подчинённых серверов для указанного родителя. */
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
     * Выполняет выгрузку данных из центральной базы.
     */
    private void performUnload(ServerInfo centralServer, List<ServerInfo> targets) {
        // Генерируем ID главной операции
        String mainOpId = "EX-" + (System.currentTimeMillis() % 100000);
        AppLogger.pushOperationId(mainOpId);

        try {
            AppLogger.info("==========================================================");
            AppLogger.info("НАЧАЛО ПРОЦЕССА ВЫГРУЗКИ ИЗ ЦЕНТРА: " + centralServer.getName());
            AppLogger.info("==========================================================");

            // ==== 1. Проверки ====
            if (targets.isEmpty()) {
                showWarning("Не выбрано ни одной базы для выгрузки.");
                return;
            }
            if (isBlank(centralServer.getProgram1CPath())
                    || isBlank(centralServer.getDatabasePath())) {
                showWarning("У центрального сервера не заполнены:\n"
                        + "  • путь к 1С 7.7 (program1CPath)\n"
                        + "  • путь к базе (databasePath)\n"
                        + "Отредактируйте сервер.");
                return;
            }
            if (isBlank(centralServer.getSmbUser()) || isBlank(centralServer.getSmbPassword())) {
                showWarning("У центрального сервера не заполнены логин/пароль SMB/WMI.\n"
                        + "Отредактируйте сервер и заполните поля «Логин SMB/WMI» и «Пароль SMB/WMI».");
                return;
            }

            // ==== 2. Собираем коды баз через запятую ====
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

            // ==== 3. Имя лог-файла ====
            String timestamp = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
            String logFileName = timestamp + "aelog.txt";

            // ==== 4. PRM-файл ====
            String prmContent =
                    "[General]\r\n" +
                    "Output=c:\\ae\\Log\\" + logFileName + "\r\n" +
                    "Quit=1\r\n" +
                    "AutoExchange=1\r\n" +
                    "[AutoExchange]\r\n" +
                    "SharedMode=1\r\n" +
                    "WriteTo=" + writeToString + "\r\n";

            // ==== 5. BAT-файл ====
            String batContent =
                    "@echo off\r\n" +
                    "\"" + centralServer.getProgram1CPath() + "\" " +
                    "config " +
                    "/d\"" + centralServer.getDatabasePath() + "\" " +
                    "/nAe " +
                    "/pAe " +
                    "/@c:\\ae\\aeparams.prm\r\n";

            // ==== 6. Учётные данные ====
            String smbUser = centralServer.getSmbUser();
            String smbPass = centralServer.getSmbPassword();
            String remoteShare = "smb://" + centralServer.getIp() + "/ae$/";

            SmbFileTransfer transfer = new SmbFileTransfer(
                    centralServer.getIp(), smbUser, smbPass);
            transfer.mkdir(remoteShare, "Log");

            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp866")), remoteShare, "unload.bat");

            // 6.3. Запускаем BAT через WMI
            WmiBatRunner wmi = new WmiBatRunner(centralServer.getIp(), smbUser, smbPass, 60);
            String result = wmi.runCommand("C:\\ae\\unload.bat");
            AppLogger.info("WMI результат:\n" + result);

            // 6.4. Проверка появления лога
            String logShare = remoteShare + "Log/";
            AppLogger.info("Ожидаем запуска 1С (появления лог-файла)...");
            
            boolean logStarted = false;
            int waitAttempts = 0;
            int maxWaitAttempts = 15;

            while (waitAttempts < maxWaitAttempts) {
                Thread.sleep(1000);
                waitAttempts++;
                if (transfer.exists(logShare, logFileName)) {
                    logStarted = true;
                    break;
                }
            }

            if (!logStarted) {
                AppLogger.error("Лог-файл не появился за 15 секунд. Вероятно, блокировка.");
                showWarning("Не удалось начать выгрузку.\n\n" +
                            "Файл лога не был создан в течение 15 секунд.\n" +
                            "Вероятнее всего, база данных заблокирована запущенным Конфигуратором 1С.");
                return;
            }

            // ==== 7. Ожидаем завершения и скачиваем лог ====
            AppLogger.info("Лог-файл создан. 1С начала обмен. Ожидаем завершения...");
            File localLog = waitAndDownloadLog(transfer, logShare, logFileName);

            // ==== 8. Копируем файлы и загружаем на периферию ====
            if (localLog != null) {
                AppLogger.info("Начинаем копирование и загрузку на периферийные серверы...");
                for (ServerInfo peripheral : targets) {
                    if (isBlank(peripheral.getIp()) || isBlank(peripheral.getSmbUser())) {
                        AppLogger.warn("Пропуск " + peripheral.getName() + ": не заполнены данные для SMB.");
                        continue;
                    }
                    
                    boolean copied = copyExchangeFile(centralServer, peripheral, peripheral.getDbSign());
                    
                    if (copied) {
                        performLoadToPeriphery(peripheral, centralServer.getDbSign());
                    }
                }
            } else {
                AppLogger.warn("Выгрузка не завершилась успешно, загрузка на периферию пропущена.");
            }

            AppLogger.info("==========================================================");
            AppLogger.info("УСПЕШНОЕ ЗАВЕРШЕНИЕ ПРОЦЕССА ВЫГРУЗКИ");
            AppLogger.info("==========================================================");

        } catch (Exception e) {
            AppLogger.error("КРИТИЧЕСКАЯ ОШИБКА В ПРОЦЕССЕ ВЫГРУЗКИ: " + e.getMessage());
            e.printStackTrace();
            showWarning("Ошибка выгрузки:\n" + e.getMessage());
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

        SelectChildServersWindow window = new SelectChildServersWindow(
                getStage(),
                "Выгрузка данных: " + centralServer.getName(),
                children);
        window.showAndWait();

        if (!window.isOkClicked()) return;

        List<ServerInfo> selected = window.getSelectedServers();
        performUnload(centralServer, selected);
    }

    /** Обработка "Загрузить данные" в контекстном меню. */
    private void onLoadData(ServerInfo centralServer) {
        List<ServerInfo> children = findChildren(centralServer);
        if (children.isEmpty()) {
            showWarning("У сервера \"" + centralServer.getName() + "\" нет подчинённых баз.");
            return;
        }

        SelectChildServersWindow window = new SelectChildServersWindow(
                getStage(),
                "Загрузка данных: " + centralServer.getName(),
                children);
        window.showAndWait();

        if (!window.isOkClicked()) return;

        List<ServerInfo> selected = window.getSelectedServers();
        if (selected.isEmpty()) {
            showWarning("Не выбрано ни одной базы.");
            return;
        }

        // ЗАГЛУШКА: здесь будет реальная логика загрузки
        StringBuilder msg = new StringBuilder("Загрузка для баз:\n");
        for (ServerInfo s : selected) {
            msg.append("  • ").append(s.getName())
               .append(" (DBSIGN=").append(s.getDbSign()).append(")\n");
        }
        showInfo(msg.toString());
    }
    
    /** Выгрузка для одного периферийного сервера (без окна выбора). */
    private void onUnloadSingle(ServerInfo server) {
        // ЗАГЛУШКА: здесь будет реальная логика выгрузки
        showInfo("Выгрузка данных из периферии для центра:\n"
                + "  • " + displayName(server)
                + "\n  DBSIGN = " + server.getDbSign());
    }

    /** Загрузка для одного периферийного сервера (без окна выбора). */
    private void onLoadSingle(ServerInfo server) {
        // ЗАГЛУШКА: здесь будет реальная логика загрузки
        showInfo("Загрузка данных в периферию из центра:\n"
                + "  • " + displayName(server)
                + "\n  DBSIGN = " + server.getDbSign());
    }

    /** Возвращает отображаемое имя сервера (name или dbSign, или заглушку). */
    private String displayName(ServerInfo s) {
        if (notBlank(s.getName())) return s.getName();
        if (notBlank(s.getDbSign())) return s.getDbSign();
        if (notBlank(s.getComputerName())) return s.getComputerName();
        return "(без имени)";
    }

    /** Информационное сообщение. */
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Информация");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    /**
     * Ожидает появления и завершения записи лог-файла на сервере,
     * затем скачивает его на локальный компьютер.
     *
     * @param transfer       SMB-подключение
     * @param remoteShare    шара на сервере ("smb://host/ae$/")
     * @param logFileName    имя лог-файла на сервере
     * @return путь к скачанному файлу или null, если скачать не удалось
     */
    private File waitAndDownloadLog(SmbFileTransfer transfer, String remoteShare,
                                    String logFileName) {
        final int maxWaitSeconds = 30;     // максимум ждём 30 сек
        final int stableSeconds = 5;        // считаем завершённым, если размер не менялся 5 сек
        final int pollIntervalMs = 1000;    // проверяем раз в секунду

        long startTime = System.currentTimeMillis();
        long lastSize = -1;
        long lastChangeTime = System.currentTimeMillis();

        AppLogger.info("Ожидаем появления лог-файла: " + logFileName);

        while (true) {
            // Проверка таймаута
            long elapsed = (System.currentTimeMillis() - startTime) / 1000;
            if (elapsed > maxWaitSeconds) {
            	AppLogger.error("Таймаут ожидания лог-файла (" + maxWaitSeconds + " сек).");
                return null;
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }

            try {
                // Проверяем наличие файла
                if (!transfer.exists(remoteShare, logFileName)) {
                    continue;
                }

                // Получаем размер файла
                long size = transfer.getSize(remoteShare, logFileName);

                if (size != lastSize) {
                    lastSize = size;
                    lastChangeTime = System.currentTimeMillis();
                    AppLogger.info("Размер лог-файла: " + size + " байт");
                } else {
                    // Размер не менялся — возможно, 1С завершилась
                    long stableFor = (System.currentTimeMillis() - lastChangeTime) / 1000;
                    if (stableFor >= stableSeconds && size > 0) {
                    	AppLogger.info("Лог-файл готов, скачиваем...");
                        break;
                    }
                }
            } catch (Exception e) {
            	AppLogger.error("Ошибка при проверке лог-файла: " + e.getMessage());
            }
        }

        // Скачиваем файл
        try {
            File localDir = new File("D:\\ae\\Logs");
            if (!localDir.exists()) {
                localDir.mkdirs();
            }
            String localPath = localDir.getAbsolutePath() + File.separator + logFileName;
            transfer.download(remoteShare, logFileName, localPath);
            AppLogger.info("Лог-файл 1С скачан с сервера: " + localPath);
            return new File(localPath);
        } catch (Exception e) {
        	AppLogger.error("Не удалось скачать лог: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Копирует файл обмена (например, DS0.zip) с центральной базы на периферийную.
     * @return true, если копирование прошло успешно, иначе false.
     */
    private boolean copyExchangeFile(ServerInfo centralServer, ServerInfo peripheralServer, String baseCode) {
        String fileName = baseCode.trim() + "0.zip";
        
        // 1. Формируем SMB-пути к папкам CP
        String centralShare = centralServer.getShareName();
        String peripheralShare = peripheralServer.getShareName();
        
        if (centralShare == null || peripheralShare == null) {
            AppLogger.error("Не удалось определить имя шары для копирования.");
            return false;
        }

        String centralCpPath = "smb://" + centralServer.getIp() + "/" + centralShare + "/CP/";
        String peripheralCpPath = "smb://" + peripheralServer.getIp() + "/" + peripheralShare + "/CP/";

        // Временная папка на локальном компьютере
        File tempDir = new File("D:\\ae\\Temp");
        if (!tempDir.exists()) tempDir.mkdirs();
        String localTempFilePath = tempDir.getAbsolutePath() + File.separator + fileName;

        boolean success = false; // Флаг успеха

        try {
            AppLogger.info("Копирование " + fileName + " с " + centralServer.getComputerName() + " на " + peripheralServer.getComputerName());

            SmbFileTransfer centralTransfer = new SmbFileTransfer(
                    centralServer.getIp(), centralServer.getSmbUser(), centralServer.getSmbPassword());
            
            if (!centralTransfer.exists(centralCpPath, fileName)) {
                showWarning("Файл обмена " + fileName + " не найден на центральном сервере.");
                return false;
            }

            centralTransfer.download(centralCpPath, fileName, localTempFilePath);

            SmbFileTransfer peripheralTransfer = new SmbFileTransfer(
                    peripheralServer.getIp(), peripheralServer.getSmbUser(), peripheralServer.getSmbPassword());
            
            peripheralTransfer.upload(localTempFilePath, peripheralCpPath, fileName);

            AppLogger.info("Файл успешно скопирован на " + peripheralServer.getComputerName());
            success = true; // Все прошло без ошибок

        } catch (Exception e) {
            AppLogger.error("Ошибка копирования файла " + fileName + ": " + e.getMessage());
            e.printStackTrace();
            showWarning("Не удалось скопировать файл " + fileName + ".\n" + e.getMessage());
            success = false;
        } finally {
            // 4. Удаляем временный файл
            File tempFile = new File(localTempFilePath);
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        
        return success;
    }
 
    /**
     * Запускает процесс загрузки данных (обмена) на периферийном сервере.
     */
    private void performLoadToPeriphery(ServerInfo peripheralServer, String centralBaseCode) {
        // Генерируем короткий ID для подоперации
        String subOpId = "LD-" + (System.currentTimeMillis() % 1000);
        AppLogger.pushOperationId(subOpId);

        try {
        	AppLogger.info("==========================================================");
            AppLogger.info(">>> НАЧАЛО ЗАГРУЗКИ В ПЕРИФЕРИЙНУЮ БАЗУ: " + peripheralServer.getName() + " (DBSIGN: " + peripheralServer.getDbSign().trim() + ") <<<");
            AppLogger.info("==========================================================");

            if (isBlank(peripheralServer.getProgram1CPath()) || isBlank(peripheralServer.getDatabasePath())) {
                AppLogger.error("У периферийного сервера не заполнены пути к 1С или базе: " + peripheralServer.getName());
                return;
            }
            if (isBlank(centralBaseCode)) {
                AppLogger.error("Не указан префикс центральной базы (DBSIGN). Загрузка невозможна.");
                showWarning("У центральной базы не заполнен DBSIGN. Невозможно сформировать PRM-файл для загрузки.");
                return;
            }

            String timestamp = new SimpleDateFormat("yyMMdd_HHmmss").format(new Date());
            String logFileName = timestamp + "aelog.txt";
            String aePath = "c:\\ae\\";

            // 1. PRM-файл для ЗАГРУЗКИ
            String prmContent =
                    "[General]\r\n" +
                    "Output=" + aePath + "Log\\" + logFileName + "\r\n" +
                    "Quit=1\r\n" +
                    "AutoExchange=1\r\n" +
                    "[AutoExchange]\r\n" +
                    "SharedMode=1\r\n" +
                    "ReadFrom=" + centralBaseCode.trim() + "\r\n"; 

            // 2. BAT-файл
            String batContent =
                    "@echo off\r\n" +
                    "\"" + peripheralServer.getProgram1CPath() + "\" " +
                    "config " +
                    "/d\"" + peripheralServer.getDatabasePath() + "\" " +
                    "/nAe " +
                    "/pAe " +
                    "/@" + aePath + "aeparams.prm\r\n";

            String smbUser = peripheralServer.getSmbUser();
            String smbPass = peripheralServer.getSmbPassword();
            String remoteShare = "smb://" + peripheralServer.getIp() + "/ae$/";

            SmbFileTransfer transfer = new SmbFileTransfer(peripheralServer.getIp(), smbUser, smbPass);
            transfer.mkdir(remoteShare, "Log");

            transfer.uploadBytes(prmContent.getBytes(Charset.forName("Cp1251")), remoteShare, "aeparams.prm");
            transfer.uploadBytes(batContent.getBytes(Charset.forName("Cp866")), remoteShare, "unload.bat");

            // Запускаем через WMI
            WmiBatRunner wmi = new WmiBatRunner(peripheralServer.getIp(), smbUser, smbPass, 60);
            String result = wmi.runCommand("c:\\ae\\unload.bat");
            AppLogger.info("WMI результат (загрузка " + peripheralServer.getName() + "):\n" + result);

            // Извлекаем ProcessId
            long pid = -1;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("ProcessId\\s*=\\s*(\\d+)").matcher(result);
            if (m.find()) {
                pid = Long.parseLong(m.group(1));
            }

            String logShare = remoteShare + "Log/";
            boolean logStarted = false;
            int maxWaitSeconds = 60;

            AppLogger.info("Ожидаем появления лог-файла на " + peripheralServer.getName() + " (до " + maxWaitSeconds + " сек)...");

            for (int i = 0; i < maxWaitSeconds; i++) {
                Thread.sleep(1000);
                
                if (transfer.exists(logShare, logFileName)) {
                    logStarted = true;
                    break;
                }

                if (pid != -1 && i > 2) { 
                    boolean alive = wmi.isProcessAliveById(pid);
                    if (!alive) {
                        AppLogger.warn("Процесс 1С (" + pid + ") завершился, но лог не создан. Вероятно, база заблокирована.");
                        break; 
                    }
                }
            }

            if (!logStarted) {
                AppLogger.error("Не удалось начать загрузку на " + peripheralServer.getName() + ". Лог-файл не появился.");
                showWarning("Не удалось начать загрузку на " + peripheralServer.getName() + ".\n" +
                            "Вероятно, база заблокирована запущенным Конфигуратором.");
                return;
            }

            AppLogger.info("Лог-файл создан. Ожидаем завершения загрузки...");
            File localLog = waitAndDownloadLog(transfer, logShare, logFileName);
            
            if (localLog != null) {
                AppLogger.info("----------------------------------------------------------");
                AppLogger.info(">>> ЗАГРУЗКА НА " + peripheralServer.getComputerName() + " УСПЕШНО ЗАВЕРШЕНА <<<");
                AppLogger.info("----------------------------------------------------------");
            } else {
                AppLogger.error(">>> ОШИБКА: Не удалось скачать лог загрузки для " + peripheralServer.getComputerName() + " <<<");
            }

        } catch (Exception e) {
            AppLogger.error(">>> ОШИБКА ЗАГРУЗКИ НА " + peripheralServer.getName() + ": " + e.getMessage() + " <<<");
        } finally {
            AppLogger.popOperationId(); 
        }
    }
    
}