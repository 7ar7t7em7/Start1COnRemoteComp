package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // ==== Корневой узел (невидимый) ====
        TreeItem<ServerInfo> root = new TreeItem<>(null);
        root.setExpanded(true);
        treeView.setRoot(root);
        treeView.setShowRoot(false); // сам корень не показываем

     // ==== Отображение узлов ====
        treeView.setCellFactory(tv -> {
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
                        MenuItem unloadItem = new MenuItem("Выгрузить данные");
                        MenuItem loadItem = new MenuItem("Загрузить данные");

                        unloadItem.setOnAction(e -> {
                            if (item.isCentral()) {
                                onUnloadData(item);          // центральный — с окном выбора
                            } else {
                                onUnloadSingle(item);        // периферийный — сразу
                            }
                        });
                        loadItem.setOnAction(e -> {
                            if (item.isCentral()) {
                                onLoadData(item);            // центральный — с окном выбора
                            } else {
                                onLoadSingle(item);          // периферийный — сразу
                            }
                        });

                        ContextMenu menu = new ContextMenu(unloadItem, loadItem);
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
                (obs, oldVal, newVal) -> {
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
        ServerInfo selected = getSelectedServer();
        if (selected == null) {
            showWarning("Выберите сервер для редактирования.");
            return;
        }
        ServerEditDialog dialog = new ServerEditDialog(
                getStage(),
                selected,
                false,
                getPossibleParentNames(selected));
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            String oldName = selected.getName(); // запоминаем старое имя до изменений
            dialog.applyToServer(selected);
            String newName = selected.getName();

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
            selectByName(selected.getName());
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

    /** Обработка "Выгрузить данные" в контекстном меню. */
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
        if (selected.isEmpty()) {
            showWarning("Не выбрано ни одной базы.");
            return;
        }

        // ЗАГЛУШКА: здесь будет реальная логика выгрузки
        StringBuilder msg = new StringBuilder("Выгрузка для баз:\n");
        for (ServerInfo s : selected) {
            msg.append("  • ").append(s.getName())
               .append(" (DBSIGN=").append(s.getDbSign()).append(")\n");
        }
        showInfo(msg.toString());
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
        showInfo("Выгрузка данных для базы:\n"
                + "  • " + displayName(server)
                + "\n  DBSIGN = " + server.getDbSign());
    }

    /** Загрузка для одного периферийного сервера (без окна выбора). */
    private void onLoadSingle(ServerInfo server) {
        // ЗАГЛУШКА: здесь будет реальная логика загрузки
        showInfo("Загрузка данных для базы:\n"
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
    
}