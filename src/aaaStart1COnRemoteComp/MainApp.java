package aaaStart1COnRemoteComp;

import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    private final ObservableList<ServerInfo> servers = FXCollections.observableArrayList();
    private ListView<ServerInfo> listView;

    @Override
    public void start(Stage primaryStage) {
        // ==== Список ====
        listView = new ListView<>(servers);
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

        // Двойной клик — просмотр
        listView.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                ServerInfo selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    openDialog(selected, true);
                }
            }
        });

        // ==== Кнопки ====
        Button addButton = new Button("Добавить");
        Button editButton = new Button("Редактировать");
        Button deleteButton = new Button("Удалить");

        addButton.setOnAction(e -> onAdd());
        editButton.setOnAction(e -> onEdit());
        deleteButton.setOnAction(e -> onDelete());

        HBox buttons = new HBox(10, addButton, editButton, deleteButton);
        buttons.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(listView);
        root.setBottom(buttons);

        primaryStage.setTitle("Список серверов 1С");
        primaryStage.setScene(new Scene(root, 600, 400));
        primaryStage.show();

        // Для демонстрации — добавим один сервер при запуске
        servers.add(new ServerInfo("SRV-1C-CENTER", "10.1.2.171",
                "C:\\Program Files\\1cv77\\bin\\1cv77.exe",
                "C:\\1C\\Base", "Бухгалтерия", true));
    }

    private void onAdd() {
        ServerInfo newServer = new ServerInfo();
        ServerEditDialog dialog = new ServerEditDialog((Stage) listView.getScene().getWindow(), newServer, false);
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            dialog.applyToServer(newServer);
            servers.add(newServer);
        }
    }

    private void onEdit() {
        ServerInfo selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("Выберите сервер для редактирования.");
            return;
        }
        ServerEditDialog dialog = new ServerEditDialog((Stage) listView.getScene().getWindow(), selected, false);
        dialog.showAndWait();
        if (dialog.isOkClicked()) {
            dialog.applyToServer(selected);
            // Обновляем отображение в списке (пересоздаём элемент)
            int index = servers.indexOf(selected);
            servers.set(index, selected);
        }
    }

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
        ServerEditDialog dialog = new ServerEditDialog((Stage) listView.getScene().getWindow(), server, readOnly);
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

    public static void main(String[] args) {
        launch(args);
    }
}