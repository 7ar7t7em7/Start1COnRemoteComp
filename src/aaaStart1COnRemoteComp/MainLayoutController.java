package aaaStart1COnRemoteComp;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.MouseButton;
import javafx.stage.Stage;

public class MainLayoutController {

    @FXML
    private ListView<ServerInfo> listView;

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();

    /**
     * Метод инициализации, вызывается автоматически после загрузки FXML.
     * Здесь настраиваем список и добавляем тестовые данные.
     */
    @FXML
    private void initialize() {
        // Настраиваем отображение элементов списка
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ServerInfo item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getComputerName() + " (" + item.getIp() + ") — " + item.getRole());
                }
            }
        });

        // Обработка двойного клика — просмотр
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

        // Для демонстрации — добавим один сервер при запуске
        servers.add(new ServerInfo("SRV-1C-CENTER", "10.1.2.171",
                "C:\\Program Files\\1cv77\\bin\\1cv77.exe",
                "C:\\1C\\Base", "Бухгалтерия", true));
    }

    @FXML
    private void onAdd() {
        ServerInfo newServer = new ServerInfo();
        ServerEditDialog dialog = new ServerEditDialog(getStage(), newServer, false);
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            dialog.applyToServer(newServer);
            servers.add(newServer);
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
            // Обновляем отображение в списке
            int index = servers.indexOf(selected);
            servers.set(index, selected);
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
        confirm.setHeaderText("Удалить сервер \"" + selected.getComputerName() + "\"?");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                servers.remove(selected);
            }
        });
    }

    private void openDialog(ServerInfo server, boolean readOnly) {
        ServerEditDialog dialog = new ServerEditDialog(getStage(), server, readOnly);
        dialog.showAndWait();
        if (!readOnly && dialog.isOkClicked()) {
            dialog.applyToServer(server);
            int index = servers.indexOf(server);
            servers.set(index, server);
        }
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Внимание");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** Возвращает текущее окно (Stage) — нужен для диалогов */
    private Stage getStage() {
        return (Stage) listView.getScene().getWindow();
    }
}