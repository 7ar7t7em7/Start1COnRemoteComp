package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class MainController {

	@FXML
	private TreeTableView<ServerInfo> treeTableView;

	@FXML
	private TreeTableColumn<ServerInfo, String> colName;
	@FXML
	private TreeTableColumn<ServerInfo, String> colLast;
	@FXML
	private TreeTableColumn<ServerInfo, String> colState;

    @FXML
    private Button readDbButton;

    @FXML
    private ListView<LogEntry> logArea;

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();
    private ComboBox<String> logFilterBox;
    private CheckBox milestoneOnlyCheckbox;

    // Копия последнего применённого фильтра (база), чтобы переустановить его после пополнения списка
    private String currentLogFilterName;


    @FXML
    private void initialize() {
        // Связываем UI с логгером (фабрика ячеек задается внутри AppLogger)
        AppLogger.setUiLogArea(logArea);

        // Настройка ячейки для лога (множественное выделение)
        logArea.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Создаем контекстное меню для списка логов
        ContextMenu logContextMenu = new ContextMenu();

        // Пункт 1: Копировать одну строку
        MenuItem copySingleItem = new MenuItem("Копировать строку");
        copySingleItem.setOnAction(e -> {
            LogEntry selected = logArea.getSelectionModel().getSelectedItem();
            if (selected != null) {
                copyToClipboard(selected.getText());
            }
        });

        // Пункт 2: Копировать всё выделенное
        MenuItem copySelectedItem = new MenuItem("Копировать выделенное");
        copySelectedItem.setOnAction(e -> {
            List<LogEntry> selectedItems = logArea.getSelectionModel().getSelectedItems();
            if (!selectedItems.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (LogEntry entry : selectedItems) {
                    sb.append(entry.getText()).append("\n");
                }
                copyToClipboard(sb.toString().trim());
            }
        });

        // Пункт 3: Копировать весь лог
        MenuItem copyAllItem = new MenuItem("Копировать весь лог");
        copyAllItem.setOnAction(e -> {
            if (!logArea.getItems().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (LogEntry entry : logArea.getItems()) {
                    sb.append(entry.getText()).append("\n");
                }
                copyToClipboard(sb.toString().trim());
            }
        });

        logContextMenu.getItems().addAll(copySingleItem, copySelectedItem, copyAllItem);
        logArea.setContextMenu(logContextMenu);

        AppLogger.info("Приложение запущено.");
        ExchangeStateStore.setChangeListener(() -> javafx.application.Platform.runLater(() -> treeTableView.refresh()));

        jcifs.Config.setProperty("jcifs.smb.client.attrExpirationPeriod", "0");

        // ==== Корневой узел (невидимый) ====
        TreeItem<ServerInfo> root = new TreeItem<>(null);
        root.setExpanded(true);
        treeTableView.setRoot(root);
        treeTableView.setShowRoot(false);

        // ==== Колонки: фабрики значений ====
        colName.setCellValueFactory(p -> {
            ServerInfo s = p.getValue().getValue();
            String display = "";
            if (s != null) {
                display = s.getName();
                if (display == null || display.isBlank()) {
                    display = s.getComputerName() != null ? s.getComputerName() : "(без имени)";
                }
            }
            return new javafx.beans.property.SimpleStringProperty(display);
        });

        colLast.setCellValueFactory(p -> {
            ServerInfo s = p.getValue().getValue();
            return new javafx.beans.property.SimpleStringProperty(s == null ? "" : getLastInfo(s));
        });

        colLast.setCellFactory(_ -> new TreeTableCell<ServerInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                }
                setAlignment(javafx.geometry.Pos.CENTER);
            }
        });
        colLast.setStyle("-fx-alignment: CENTER;");

        colState.setCellValueFactory(p -> {
            ServerInfo server = p.getValue().getValue();
            return new javafx.beans.property.SimpleStringProperty(formatExchangeState(server));
        });
        colState.setCellFactory(_ -> new TreeTableCell<ServerInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                ServerInfo server = getTreeTableRow() == null ? null : getTreeTableRow().getItem();
                ExchangeState state = server == null ? null : ExchangeStateStore.get(server);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setTooltip(null);
                    setStyle("");
                } else {
                    setText(item);
                    setTooltip(state == null ? null : new Tooltip(buildStateTooltip(state)));
                    String color = "#555555";
                    if (state != null) {
                        if (state.getStatus() == ExchangeStatus.SUCCESS) color = "#16803c";
                        else if (state.getStatus() == ExchangeStatus.FAILED || state.getStatus() == ExchangeStatus.INTERRUPTED) color = "#c62828";
                        else if (state.getStatus() == ExchangeStatus.RUNNING) color = "#b7791f";
                    }
                    setStyle("-fx-text-fill: " + color + "; -fx-padding: 2 5 2 5;");
                }
            }
        });

        // ==== Кнопка обновления в заголовке "Последний обмен" ====
        Button refreshBtn = new Button("⟳");
        refreshBtn.setStyle("-fx-padding: 0 6 0 6; -fx-font-size: 11px; -fx-cursor: hand;");
        refreshBtn.setOnAction(e -> treeTableView.refresh());

        javafx.scene.layout.HBox headerBox = new javafx.scene.layout.HBox(6);
        headerBox.setAlignment(javafx.geometry.Pos.CENTER);
        headerBox.getChildren().addAll(new javafx.scene.control.Label("Последний обмен"), refreshBtn);

        colLast.setGraphic(headerBox);
        colLast.setText(null); // текст теперь внутри графики

        // ==== Цвет текста в колонке "Имя" ====
        colName.setCellFactory(_ -> new TreeTableCell<ServerInfo, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                // Фиксируем внутренние отступы ячейки, чтобы текст не менял
                // положение при переходе строки в состояние выделения.
                setPadding(new Insets(2, 5, 2, 5));

                ServerInfo server = (getTreeTableRow() != null) ? getTreeTableRow().getItem() : null;

                if (empty || item == null || server == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (isFullyFilled(server)) {
                        setStyle("-fx-text-fill: green;");
                    } else {
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });

        // ==== Строка: контекстное меню и двойной клик ====
        treeTableView.setRowFactory(tv -> {
            TreeTableRow<ServerInfo> row = new TreeTableRow<>();

            // Пункт обмена с сервером
            MenuItem exchangeItem = new MenuItem();
            exchangeItem.setOnAction(e -> {
                ServerInfo s = row.getItem();
                if (s == null) return;
                if (s.isCentral()) {
                    onUnloadData(s);
                } else {
                    onUnloadPeriphery(s);
                }
            });

            MenuItem editItem = new MenuItem("Редактировать");
            editItem.setOnAction(e -> {
                ServerInfo s = row.getItem();
                if (s != null) editServer(s);
            });

            MenuItem pingItem = new MenuItem("Проверка доступности");
            pingItem.setOnAction(e -> {
                ServerInfo s = row.getItem();
                if (s != null) onPingServer(s);
            });

            ContextMenu menu = new ContextMenu(exchangeItem, new SeparatorMenuItem(), pingItem, editItem);
            row.setContextMenu(menu);

            // Строки переиспользуются, поэтому подпись пункта обмена
            // обновляем при смене элемента в строке
            row.itemProperty().addListener((_, _, s) -> {
                if (s == null) {
                    row.setContextMenu(null);
                } else {
                    exchangeItem.setText(s.isCentral()
                            ? "Выгрузить из центра - Загрузить в периферию"
                            : "Выгрузить из периферии - Загрузить в центр");
                    row.setContextMenu(menu);
                }
            });

            // Двойной клик — открываем диалог только для чтения
            row.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    event.consume();
                    ServerInfo selected = row.getItem();
                    if (selected != null) {
                        openDialog(selected, true);
                    }
                }
            });

            return row;
        });

        // ==== Слушаем изменение выделения ====
        treeTableView.getSelectionModel().selectedItemProperty().addListener(
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

        // ==== Фильтр лога ====
        setupLogFilter();
    }


    // ============================================================
    //  ФИЛЬТР ЛОГА
    // ============================================================

    private void setupLogFilter() {
        logFilterBox = new ComboBox<>();
        logFilterBox.setMaxWidth(Double.MAX_VALUE);
        refreshLogFilterItems();

        milestoneOnlyCheckbox = new CheckBox("Только ключевые сообщения обмена");
        milestoneOnlyCheckbox.setSelected(true); // По умолчанию включено

        // Слушатели
        logFilterBox.valueProperty().addListener((obs, oldVal, sel) -> applyLogFilter());
        milestoneOnlyCheckbox.selectedProperty().addListener((obs, oldVal, sel) -> applyLogFilter());

        // Вёрстка
        if (logArea.getParent() instanceof Pane parentPane) {
            VBox logBox = new VBox(4);
            VBox.setVgrow(logArea, Priority.ALWAYS);

            int idx = parentPane.getChildren().indexOf(logArea);
            if (idx >= 0) {
                logBox.getChildren().add(logFilterBox);
                logBox.getChildren().add(milestoneOnlyCheckbox); // Добавляем чекбокс
                logBox.getChildren().add(logArea);
                parentPane.getChildren().add(idx, logBox);
            }
        }
    }


    /** Обновляет список баз в комбобоксе (вызывается после изменения списка серверов). */
    private void refreshLogFilterItems() {
        if (logFilterBox == null) return;
        String current = logFilterBox.getValue();
        if (current == null) current = currentLogFilterName;

        logFilterBox.getItems().setAll("Все базы");
        for (ServerInfo s : servers) {
            if (notBlank(s.getName())) {
                logFilterBox.getItems().add(s.getName());
            }
        }
        logFilterBox.setValue(current != null && logFilterBox.getItems().contains(current) ? current : "Все базы");
    }

    /** Применяет выбранный фильтр к отображению лога. */
    private void applyLogFilter() {
        String sel = logFilterBox.getValue();
        boolean milestoneOnly = milestoneOnlyCheckbox != null && milestoneOnlyCheckbox.isSelected();

        Predicate<LogEntry> predicate = entry -> !milestoneOnly || entry.isMilestone();

        if (sel != null && !sel.equals("Все базы")) {
            ServerInfo selSrv = null;
            for (ServerInfo s : servers) {
                if (s.getName() != null && s.getName().equals(sel)) {
                    selSrv = s;
                    break;
                }
            }
            
            if (selSrv != null) {
                currentLogFilterName = selSrv.getName();
                final String sign = selSrv.getDbSign() != null ? selSrv.getDbSign().trim() : "";

                // Ищем ПОЛНЫЕ ID операций, в которых участвовала эта база
                java.util.Set<String> relevantFullIds = new java.util.HashSet<>();
                for (LogEntry entry : AppLogger.getUiLogData()) {
                    if (sign.equals(entry.getDbSign())) {
                        if (entry.getFullOpId() != null && !entry.getFullOpId().isEmpty()) {
                            relevantFullIds.add(entry.getFullOpId());
                        }
                    }
                }

                // Показываем строки, если:
                // 1. База совпадает
                // 2. ИЛИ полный ID операции начинается с одного из найденных (чтобы захватить родительские вызовы)
                // 3. НО не показываем другие ветки (например, DP-959, если мы ищем DP-532)
                predicate = predicate.and(entry -> {
                    if (sign.equals(entry.getDbSign())) return true;
                    
                    if (entry.getFullOpId() == null || entry.getFullOpId().isEmpty()) return false;
                    
                    // Если текущая строка принадлежит искомой базе или является её родителем (например, корневой UC-7753)
                    for (String relevantId : relevantFullIds) {
                        if (entry.getFullOpId().equals(relevantId) || relevantId.startsWith(entry.getFullOpId())) {
                            return true;
                        }
                    }
                    return false;
                });
            } else {
                currentLogFilterName = null;
            }
        } else {
            currentLogFilterName = null;
        }

        AppLogger.setUiLogFilter(predicate);
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
        TreeItem<ServerInfo> root = treeTableView.getRoot();
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
        TreeItem<ServerInfo> item = treeTableView.getSelectionModel().getSelectedItem();
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
            refreshLogFilterItems();
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

        ServerEditDialog dialog = new ServerEditDialog(getStage(), server, false, getPossibleParentNames(server), false);

        // Немодальное окно: можно открыть редактирование другой базы,
        // не закрывая уже открытое окно.
        dialog.showNonModal(() -> {
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
                refreshLogFilterItems();
                saveServers();
                selectByName(server.getName());
            }
        });
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
                refreshLogFilterItems();
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
            refreshLogFilterItems();
            saveServers();

            TreeItem<ServerInfo> parentItem = findTreeItem(treeTableView.getRoot(), selected.getName());
            if (parentItem != null) {
                parentItem.setExpanded(true);
                treeTableView.getSelectionModel().select(parentItem);
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
            refreshLogFilterItems();
            saveServers();
            selectByName(server.getName());
        }
    }

    private void selectByName(String name) {
        if (name == null) return;
        TreeItem<ServerInfo> item = findTreeItem(treeTableView.getRoot(), name);
        if (item != null) {
        	treeTableView.getSelectionModel().select(item);
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
        return (Stage) treeTableView.getScene().getWindow();
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

    // ============================================================
    //  ОБМЕН ДАННЫМИ (UI-обёртки)
    // ============================================================

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
            protected Void call() {
                ExchangeService.ExchangeResult result = ExchangeService.unloadFromCentral(centralServer, selected);
                if (!result.isSuccess()) {
                    // Убрали showWarning, просто дублируем ошибку в лог (если она не была залогирована)
                    //AppLogger.error("Процесс завершен с ошибкой: " + result.getMessage());
                }
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

    private void onUnloadPeriphery(ServerInfo peripheralServer) {
        // Ищем центральную базу для этой периферии
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
            protected Void call() {
                // Вызываем метод для выгрузки ИЗ ПЕРИФЕРИИ, передавая центральную базу
                ExchangeService.ExchangeResult result = ExchangeService.unloadFromPeriphery(peripheralServer, finalCentral);
                if (!result.isSuccess()) {
                    AppLogger.error("Процесс завершен с ошибкой: " + result.getMessage());
                }
                return null;
            }
        };

        unloadTask.setOnFailed(e -> {
            Throwable ex = unloadTask.getException();
            AppLogger.error("Критическая ошибка в фоновом потоке: " + ex.getMessage());
            // Если хотите убрать окно и здесь, закомментируйте строку ниже
            javafx.application.Platform.runLater(() -> showWarning("Произошла ошибка при выгрузке из периферии. Подробности в логе."));
        });

        Thread thread = new Thread(unloadTask);
        thread.setDaemon(true);
        thread.start();
    }

    /** Вспомогательный метод для копирования текста в буфер обмена. */
    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }

    private void onPingServer(ServerInfo server) {
        if (isBlank(server.getIp())) {
            showWarning("У сервера \"" + server.getName() + "\" не заполнен IP-адрес.");
            return;
        }

        AppLogger.info("--- Проверка доступности: " + server.getName()
                + " (" + server.getIp() + ") ---");

        Task<Void> pingTask = new Task<>() {
            @Override
            protected Void call() {
                if (ServerAvailabilityChecker.isAvailable(server)) {
                    AppLogger.info("Сервер " + server.getIp()
                            + " ДОСТУПЕН (Ping прошел успешно).");
                } else {
                    AppLogger.warn("Сервер " + server.getIp()
                            + " НЕДОСТУПЕН (Ping не прошел).");
                }
                return null;
            }
        };

        Thread thread = new Thread(pingTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Разница в минутах между текущим временем и временем файла
     * "<dbSign>1.zip" в сетевой папке PC центральной базы.
     * Сетевой путь задаётся в реквизите "Сетевой путь" центральной записи.
     */
    private String formatExchangeState(ServerInfo server) {
        if (server == null || server.isCentral()) return "";
        ExchangeState state = ExchangeStateStore.get(server);
        if (state == null || state.getStage() == null || state.getStatus() == null) return "";
        String status;
        switch (state.getStatus()) {
            case RUNNING: status = "выполняется"; break;
            case SUCCESS: status = "успешно"; break;
            case FAILED: status = "ошибка"; break;
            case SKIPPED: status = "пропущено"; break;
            case INTERRUPTED: status = "прервано"; break;
            default: status = state.getStatus().name();
        }
        String time = "";
        String dateTime = state.getFinishedAt() != null ? state.getFinishedAt() : state.getStartedAt();
        if (dateTime != null && dateTime.length() >= 16) time = dateTime.substring(11, 16);
        String message = state.getMessage() == null ? "" : state.getMessage().replaceAll("[\\r\\n]+", " ").trim();
        if (state.getStatus() == ExchangeStatus.RUNNING || "Выполняется".equalsIgnoreCase(message)) message = "";
        if (message.length() > 65) message = message.substring(0, 62) + "...";
        return state.getStage().getTitle() + " • " + status + (time.isBlank() ? "" : " • " + time)
                + (message.isBlank() ? "" : " • " + message);
    }

    private String buildStateTooltip(ExchangeState state) {
        StringBuilder sb = new StringBuilder();
        sb.append(state.getStage() == null ? "Этап неизвестен" : state.getStage().getTitle());
        sb.append("\nСтатус: ").append(state.getStatus());
        if (state.getStartedAt() != null) sb.append("\nНачало: ").append(state.getStartedAt());
        if (state.getFinishedAt() != null) sb.append("\nЗавершение: ").append(state.getFinishedAt());
        if (state.getAttempt() > 0) sb.append("\nПопытка: ").append(state.getAttempt());
        if (state.getOperationId() != null) sb.append("\nОперация: ").append(state.getOperationId());
        if (state.getMessage() != null && !state.getMessage().isBlank()) sb.append("\n").append(state.getMessage());
        return sb.toString();
    }

    private String getLastInfo(ServerInfo s) {
        if (s == null || s.isCentral()) return "";

        String sign = (s.getDbSign() == null) ? null : s.getDbSign().trim();
        if (isBlank(sign) || isBlank(s.getParentName())) return "";

        // Находим центральную базу — родителя этого листа
        ServerInfo central = null;
        for (ServerInfo c : servers) {
            if (c.isCentral() && c.getName() != null && c.getName().equals(s.getParentName())) {
                central = c;
                break;
            }
        }
        if (central == null || isBlank(central.getNetworkPath())) return "";

        // Папка PC: сетевой путь + "\PC" (завершающий слэш в реквизите не мешает)
        File pcDir = new File(central.getNetworkPath().trim(), "PC");
        File zipFile = new File(pcDir, sign + "1.zip");

        if (!zipFile.isFile()) return "";

        long m = (System.currentTimeMillis() - zipFile.lastModified()) / 60000;
        String form;
        long mod10 = m % 10, mod100 = m % 100;
        if (mod10 == 1 && mod100 != 11) form = "минута";
        else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) form = "минуты";
        else form = "минут";
        return m + " " + form;
    }
}
