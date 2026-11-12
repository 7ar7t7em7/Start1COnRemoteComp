package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.SelectionMode;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MainController {

    @FXML
    private TreeView<ServerInfo> treeView;

    @FXML
    private Button readDbButton;
    
    @FXML 
    private ListView<String> logArea;

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Связываем UI с логгером
    	AppLogger.setUiLogArea(logArea);
    	
        // Настройка ячейки для лога
        logArea.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Разрешаем множественное выделение
        
        logArea.setCellFactory(lv -> {
            ListCell<String> cell = new ListCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        if (item.contains("[ERROR]")) {
                            setStyle("-fx-text-fill: red;");
                        } else if (item.contains("[WARN]")) {
                            setStyle("-fx-text-fill: orange;");
                        } else {
                            setStyle("");
                        }
                    }
                }
            };

            // Создаем контекстное меню
            ContextMenu cellMenu = new ContextMenu();
            
            // Пункт 1: Копировать одну строку
            MenuItem copySingleItem = new MenuItem("Копировать строку");
            copySingleItem.setOnAction(e -> {
                if (cell.getItem() != null) {
                    copyToClipboard(cell.getItem());
                }
            });

            // Пункт 2: Копировать всё выделенное
            MenuItem copySelectedItem = new MenuItem("Копировать выделенное");
            copySelectedItem.setOnAction(e -> {
                List<String> selectedItems = logArea.getSelectionModel().getSelectedItems();
                if (!selectedItems.isEmpty()) {
                    copyToClipboard(String.join("\n", selectedItems));
                }
            });
            
            // Пункт 3: Копировать весь лог
            MenuItem copyAllItem = new MenuItem("Копировать весь лог");
            copyAllItem.setOnAction(e -> {
                if (!logArea.getItems().isEmpty()) {
                    copyToClipboard(String.join("\n", logArea.getItems()));
                }
            });

            cellMenu.getItems().addAll(copySingleItem, copySelectedItem, copyAllItem);
            cell.setContextMenu(cellMenu);

            return cell;
        });

        AppLogger.info("Приложение запущено.");
        
        jcifs.Config.setProperty("jcifs.smb.client.attrExpirationPeriod", "0");
        
        // ==== Корневой узел (невидимый) ====
        TreeItem<ServerInfo> root = new TreeItem<>(null);
        root.setExpanded(true);
        treeView.setRoot(root);
        treeView.setShowRoot(false);

        // ==== Отображение узлов ====
        treeView.setCellFactory(_ -> {
            // Явно указываем тип TreeCell<ServerInfo>, чтобы Eclipse не ругался
            TreeCell<ServerInfo> cell = new TreeCell<ServerInfo>() {
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
            protected Void call() throws Exception {
                ExchangeService.ExchangeResult result = ExchangeService.unloadFromCentral(centralServer, selected);
                javafx.application.Platform.runLater(() -> {
                    if (!result.isSuccess()) {
                        showWarning(result.getMessage());
                    }
                });
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
                ExchangeService.ExchangeResult result = ExchangeService.unloadFromPeriphery(peripheralServer, finalCentral);
                javafx.application.Platform.runLater(() -> {
                    if (!result.isSuccess()) {
                        showWarning(result.getMessage());
                    }
                });
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
    
    /** Вспомогательный метод для копирования текста в буфер обмена. */
    private void copyToClipboard(String text) {
        javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
        javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
        content.putString(text);
        clipboard.setContent(content);
    }
}
