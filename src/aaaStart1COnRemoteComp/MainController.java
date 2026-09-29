package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

import java.io.IOException;

public class MainController {

    @FXML
    private ListView<ServerInfo> listView;

    @FXML
    private Button readDbButton;

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();

    @FXML
    private void initialize() {
        // Настраиваем отображение: в списке — только наименование
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ServerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String display = item.getName();
                    if (display == null || display.isBlank()) {
                        display = item.getComputerName() != null
                                ? item.getComputerName()
                                : "(без имени)";
                    }
                    setText(display);
                }
            }
        });

        // Двойной клик — просмотр
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                ServerInfo selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openDialog(selected, true);
                }
            }
        });

        // Привязываем данные к списку
        listView.setItems(servers);

        // Слушаем изменение выделения в списке — управляем кнопкой "Прочитать БД"
        listView.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateReadDbButtonState(newVal));

        // По умолчанию — ничего не выделено, кнопка выключена
        readDbButton.setDisable(true);

        // Загружаем список из файла
        try {
            servers.addAll(ServerStorage.load());
        } catch (IOException e) {
            showWarning("Не удалось загрузить список серверов:\n" + e.getMessage());
        }
    }

    /**
     * Включает кнопку "Прочитать БД" только если:
     * 1) выделен элемент списка;
     * 2) у этого элемента признак central == true.
     */
    private void updateReadDbButtonState(ServerInfo selected) {
        boolean enabled = (selected != null) && selected.isCentral();
        readDbButton.setDisable(!enabled);
    }

    @FXML
    private void onAdd() {
        ServerInfo newServer = new ServerInfo();
        ServerEditDialog dialog = new ServerEditDialog(getStage(), newServer, false);
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            dialog.applyToServer(newServer);
            servers.add(newServer);
            saveServers();
        }
    }

    @FXML
    private void onEdit() {
        ServerInfo selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Выберите сервер для редактирования.");
            return;
        }
        ServerEditDialog dialog = new ServerEditDialog(getStage(), selected, false);
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            dialog.applyToServer(selected);
            int index = servers.indexOf(selected);
            servers.set(index, selected);
            // После редактирования признак central мог измениться — обновим состояние кнопки
            updateReadDbButtonState(selected);
            saveServers();
        }
    }

    @FXML
    private void onDelete() {
        ServerInfo selected = listView.getSelectionModel().getSelectedItem();
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
                saveServers();
                // После удаления выделения нет — кнопка снова выключена
                updateReadDbButtonState(null);
            }
        });
    }

    @FXML
    private void onReadDatabase() {
        ServerInfo selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null || !selected.isCentral()) {
            showWarning("Выберите центральный сервер для чтения базы данных.");
            return;
        }

        // Проверяем, что все нужные поля заполнены
        if (isBlank(selected.getIp()) || isBlank(selected.getDatabaseName())) {
            showWarning("У выбранного сервера не заполнены IP-адрес или имя базы данных.\n" +
                    "Отредактируйте сервер и заполните поля.");
            return;
        }

        // Запрашиваем логин/пароль
        DbLoginDialog loginDialog = new DbLoginDialog(getStage());
        loginDialog.showAndWait();

        if (loginDialog.isOkClicked()) {
            // Передаём данные из выбранного ServerInfo
            String result = DatabaseReader.readDbSetTable(
                    selected.getIp(),                  // host
                    selected.getDatabaseName(),        // database
                    loginDialog.getUsername(),         // user
                    loginDialog.getPassword());        // password

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Содержимое _1SDBSET");
            alert.setHeaderText("Сервер: " + selected.getComputerName()
                    + " (" + selected.getIp() + "), БД: " + selected.getDatabaseName());
            alert.setContentText(result.length() > 1000 ? result.substring(0, 1000) + "..." : result);
            alert.showAndWait();
        }
    }

    /** Утилита: проверяет, что строка пустая или null */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private void openDialog(ServerInfo server, boolean readOnly) {
        ServerEditDialog dialog = new ServerEditDialog(getStage(), server, readOnly);
        dialog.showAndWait();
        if (!readOnly && dialog.isOkClicked()) {
            dialog.applyToServer(server);
            int index = servers.indexOf(server);
            servers.set(index, server);
            updateReadDbButtonState(server);
            saveServers();
        }
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
        return (Stage) listView.getScene().getWindow();
    }
}