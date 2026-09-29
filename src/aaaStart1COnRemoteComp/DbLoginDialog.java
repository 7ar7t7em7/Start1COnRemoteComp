package aaaStart1COnRemoteComp;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class DbLoginDialog {

    private final Stage stage;
    private String username;
    private String password;
    private boolean okClicked = false;

    private TextField userField;
    private PasswordField passField;

    public DbLoginDialog(Stage owner) {
        this.stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Подключение к SQL Server 2000");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10));
        grid.setHgap(10);
        grid.setVgap(8);

        userField = new TextField();
        passField = new PasswordField();

        grid.add(new Label("Пользователь:"), 0, 0);
        grid.add(userField, 1, 0);
        grid.add(new Label("Пароль:"), 0, 1);
        grid.add(passField, 1, 1);

        Button okButton = new Button("OK");
        Button cancelButton = new Button("Отмена");

        okButton.setOnAction(e -> {
            username = userField.getText();
            password = passField.getText();
            okClicked = true;
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());

        javafx.scene.layout.HBox buttons = new javafx.scene.layout.HBox(10, okButton, cancelButton);
        buttons.setPadding(new Insets(10));

        javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(grid, buttons);
        stage.setScene(new Scene(root, 320, 180));
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public boolean isOkClicked() { return okClicked; }
}