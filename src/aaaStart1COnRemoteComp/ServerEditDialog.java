package aaaStart1COnRemoteComp;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Диалог создания/редактирования/просмотра ServerInfo.
 */
public class ServerEditDialog {

    private final Stage stage;
    private final ServerInfo server;
    private boolean okClicked = false;

    // Поля формы
    private TextField computerNameField;
    private TextField ipField;
    private TextField program1CPathField;
    private TextField databasePathField;
    private TextField databaseNameField;
    private CheckBox centralCheckBox;

    /**
     * @param owner       родительское окно
     * @param server      объект для редактирования; null — создаём новый
     * @param readOnly    true — только просмотр (кнопки сохранения нет)
     */
    public ServerEditDialog(Stage owner, ServerInfo server, boolean readOnly) {
        this.server = server;
        this.stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(readOnly ? "Просмотр сервера" : (server == null ? "Новый сервер" : "Редактирование сервера"));

        // ==== Форма ====
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(8);

        computerNameField = new TextField();
        ipField = new TextField();
        program1CPathField = new TextField();
        databasePathField = new TextField();
        databaseNameField = new TextField();
        centralCheckBox = new CheckBox("Центральный сервер");

        grid.add(new Label("Имя компьютера:"), 0, 0);
        grid.add(computerNameField, 1, 0);
        grid.add(new Label("IP-адрес:"), 0, 1);
        grid.add(ipField, 1, 1);
        grid.add(new Label("Путь к 1С 7.7:"), 0, 2);
        grid.add(program1CPathField, 1, 2);
        grid.add(new Label("Путь к базе:"), 0, 3);
        grid.add(databasePathField, 1, 3);
        grid.add(new Label("Имя БД:"), 0, 4);
        grid.add(databaseNameField, 1, 4);
        grid.add(centralCheckBox, 1, 5);

        // ==== Кнопки ====
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Отмена");

        okButton.setOnAction(e -> {
            if (isInputValid()) {
                okClicked = true;
                stage.close();
            }
        });
        cancelButton.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(10, okButton, cancelButton);
        buttons.setPadding(new Insets(10, 10, 10, 10));

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(grid, buttons);
        stage.setScene(new Scene(root, 480, 300));

        if (readOnly) {
            // Запрещаем редактирование
            computerNameField.setEditable(false);
            ipField.setEditable(false);
            program1CPathField.setEditable(false);
            databasePathField.setEditable(false);
            databaseNameField.setEditable(false);
            centralCheckBox.setDisable(true);
            okButton.setDisable(true);
            cancelButton.setText("Закрыть");
        }

        // Заполняем поля, если объект передан
        if (server != null) {
            computerNameField.setText(server.getComputerName());
            ipField.setText(server.getIp());
            program1CPathField.setText(server.getProgram1CPath());
            databasePathField.setText(server.getDatabasePath());
            databaseNameField.setText(server.getDatabaseName());
            centralCheckBox.setSelected(server.isCentral());
        }
    }

    /** Валидация: обязательные поля не должны быть пустыми */
    private boolean isInputValid() {
        String error = "";
        if (computerNameField.getText() == null || computerNameField.getText().isBlank()) {
            error += "Не заполнено имя компьютера.\n";
        }
        if (ipField.getText() == null || ipField.getText().isBlank()) {
            error += "Не заполнен IP-адрес.\n";
        }
        if (error.isEmpty()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка ввода");
        alert.setHeaderText("Заполните обязательные поля");
        alert.setContentText(error);
        alert.showAndWait();
        return false;
    }

    /** Переносит данные из полей в объект ServerInfo */
    public void applyToServer(ServerInfo s) {
        s.setComputerName(computerNameField.getText());
        s.setIp(ipField.getText());
        s.setProgram1CPath(program1CPathField.getText());
        s.setDatabasePath(databasePathField.getText());
        s.setDatabaseName(databaseNameField.getText());
        s.setCentral(centralCheckBox.isSelected());
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    public void showAndWait() {
        stage.showAndWait();
    }
}