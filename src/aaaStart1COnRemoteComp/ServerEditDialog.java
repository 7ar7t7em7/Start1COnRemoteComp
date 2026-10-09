package aaaStart1COnRemoteComp;

import java.util.List;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
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
    private ComboBox<String> parentComboBox;
    private TextField dbSignField;
    private TextField nameField;
    private TextField computerNameField;
    private TextField ipField;
    private TextField program1CPathField;
    private TextField databasePathField;
    private TextField databaseNameField;
    private CheckBox centralCheckBox;
    private TextField smbUserField;
    private PasswordField smbPasswordField;

    /**
     * @param owner       родительское окно
     * @param server      объект для редактирования; null — создаём новый
     * @param readOnly    true — только просмотр (кнопки сохранения нет)
     */
    public ServerEditDialog(Stage owner, ServerInfo server, boolean readOnly, List<String> possibleParents) {
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

     // ==== Растягивание колонок ====
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(150);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);
        
        dbSignField = new TextField();
        dbSignField.setMaxWidth(Double.MAX_VALUE);
        nameField = new TextField();
        nameField.setMaxWidth(Double.MAX_VALUE);
        computerNameField = new TextField();
        computerNameField.setMaxWidth(Double.MAX_VALUE);
        ipField = new TextField();
        ipField.setMaxWidth(Double.MAX_VALUE);
        program1CPathField = new TextField();
        program1CPathField.setMaxWidth(Double.MAX_VALUE);
        databasePathField = new TextField();
        databasePathField.setMaxWidth(Double.MAX_VALUE);
        databaseNameField = new TextField();
        parentComboBox = new ComboBox<>();  
        centralCheckBox = new CheckBox("Центральный сервер");
        smbUserField = new TextField();
        smbUserField.setMaxWidth(Double.MAX_VALUE);
        smbPasswordField = new PasswordField();
        smbPasswordField.setMaxWidth(Double.MAX_VALUE);
        
        grid.add(new Label("Наименование:"), 0, 0);   grid.add(nameField, 1, 0);
        grid.add(new Label("Имя компьютера:"), 0, 1); grid.add(computerNameField, 1, 1);
        grid.add(new Label("IP-адрес:"), 0, 2);       grid.add(ipField, 1, 2);
        grid.add(new Label("Путь к 1С 7.7:"), 0, 3);  grid.add(program1CPathField, 1, 3);
        grid.add(new Label("Путь к базе:"), 0, 4);    grid.add(databasePathField, 1, 4);
        grid.add(new Label("Имя БД:"), 0, 5);         grid.add(databaseNameField, 1, 5);
        grid.add(new Label("DBSIGN:"), 0, 6);         grid.add(dbSignField, 1, 6);
        grid.add(new Label("Родитель:"), 0, 7);       grid.add(parentComboBox, 1, 7);
        grid.add(new Label("Логин SMB/WMI:"), 0, 8);
        grid.add(smbUserField, 1, 8);
        grid.add(new Label("Пароль SMB/WMI:"), 0, 9);
        grid.add(smbPasswordField, 1, 9);
        grid.add(centralCheckBox, 1, 10);

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
        stage.setScene(new Scene(root, 480, 460));

        if (readOnly) {
            // Запрещаем редактирование
            computerNameField.setEditable(false);
            ipField.setEditable(false);
            program1CPathField.setEditable(false);
            databasePathField.setEditable(false);
            databaseNameField.setEditable(false);
            dbSignField.setEditable(false);
            centralCheckBox.setDisable(true);
            okButton.setDisable(true);
            cancelButton.setText("Закрыть");
            parentComboBox.setDisable(true);
            smbUserField.setEditable(false);
            smbPasswordField.setEditable(false);
        }

        // Заполняем поля, если объект передан
        if (server != null) {
        	nameField.setText(server.getName());
            computerNameField.setText(server.getComputerName());
            ipField.setText(server.getIp());
            program1CPathField.setText(server.getProgram1CPath());
            databasePathField.setText(server.getDatabasePath());
            databaseNameField.setText(server.getDatabaseName());
            centralCheckBox.setSelected(server.isCentral());
            dbSignField.setText(server.getDbSign());
            parentComboBox.setValue(server.getParentName());
            smbUserField.setText(server.getSmbUser());
            smbPasswordField.setText(server.getSmbPassword());
        }
    }

    /** Валидация: обязательные поля не должны быть пустыми */
    private boolean isInputValid() {
        String error = "";
        
        if (dbSignField.getText() == null || dbSignField.getText().isBlank()) {
            error += "Не заполнен DBSIGN.\n";
        }
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            error += "Не заполнено наименование.\n";
        }
        
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
    	s.setName(nameField.getText());
        s.setComputerName(computerNameField.getText());
        s.setIp(ipField.getText());
        s.setProgram1CPath(program1CPathField.getText());
        s.setDatabasePath(databasePathField.getText());
        s.setDatabaseName(databaseNameField.getText());
        s.setDbSign(dbSignField.getText());
        s.setCentral(centralCheckBox.isSelected());
        s.setParentName(parentComboBox.getValue());
        s.setSmbUser(smbUserField.getText());
        s.setSmbPassword(smbPasswordField.getText());
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    public void showAndWait() {
        stage.showAndWait();
    }
}