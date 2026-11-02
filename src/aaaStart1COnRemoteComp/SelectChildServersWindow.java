package aaaStart1COnRemoteComp;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно выбора подчинённых серверов для выгрузки/загрузки.
 */
public class SelectChildServersWindow {

    private final Stage stage;

    /** Модель строки списка: сервер + галочка. */
    public static class ChildRow {
        private final ServerInfo server;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        public ChildRow(ServerInfo server) {
            this.server = server;
        }

        public ServerInfo getServer() { return server; }
        public BooleanProperty selectedProperty() { return selected; }
        public boolean isSelected() { return selected.get(); }
    }

    private final List<ChildRow> rows = new ArrayList<>();
    private boolean okClicked = false;

    /**
     * @param owner   родительское окно
     * @param title   заголовок ("Выгрузка данных" или "Загрузка данных")
     * @param children список подчинённых серверов
     */
    public SelectChildServersWindow(Stage owner, String title, List<ServerInfo> children) {
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);

        // ==== Список с чекбоксами ====
        ListView<ChildRow> listView = new ListView<>();
        for (ServerInfo s : children) {
            rows.add(new ChildRow(s));
        }
        listView.setItems(FXCollections.observableArrayList(rows));

        listView.setCellFactory(CheckBoxListCell.forListView(ChildRow::selectedProperty, new javafx.util.StringConverter<ChildRow>() {
            @Override
            public String toString(ChildRow item) {
                if (item == null) return "";
                String name = item.getServer().getName();
                if (name == null || name.isBlank()) {
                    name = item.getServer().getDbSign();
                }
                return name == null ? "(без имени)" : name;
            }

            @Override
            public ChildRow fromString(String string) {
                return null;
            }
        }));

        // ==== Обработка нажатия пробела ====
        listView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.SPACE) {
                // Получаем текущий выделенный элемент
                ChildRow selectedRow = listView.getSelectionModel().getSelectedItem();
                if (selectedRow != null) {
                    // Инвертируем значение флажка (ставим, если не стоял; снимаем, если стоял)
                    selectedRow.selectedProperty().set(!selectedRow.isSelected());
                    
                    // Гасим событие, чтобы ListView не пытался прокручиваться или делать что-то еще
                    event.consume(); 
                }
            }
        });

        // ==== Кнопки ====
        Button selectAllButton = new Button("Выделить все");
        Button deselectAllButton = new Button("Снять все");
        Button okButton = new Button("OK");
        Button cancelButton = new Button("Отмена");

        selectAllButton.setOnAction(e -> rows.forEach(r -> r.selectedProperty().set(true)));
        deselectAllButton.setOnAction(e -> rows.forEach(r -> r.selectedProperty().set(false)));

        okButton.setOnAction(e -> {
            okClicked = true;
            stage.close();
        });
        cancelButton.setOnAction(e -> stage.close());

        HBox leftButtons = new HBox(10, selectAllButton, deselectAllButton);
        HBox rightButtons = new HBox(10, okButton, cancelButton);
        HBox buttons = new HBox(10, leftButtons, new javafx.scene.layout.Region(), rightButtons);
        HBox.setHgrow(buttons.getChildren().get(1), javafx.scene.layout.Priority.ALWAYS);
        buttons.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(listView);
        root.setBottom(buttons);

        stage.setScene(new Scene(root, 450, 400));
    }

    /** Возвращает список выбранных серверов. */
    public List<ServerInfo> getSelectedServers() {
        List<ServerInfo> result = new ArrayList<>();
        for (ChildRow row : rows) {
            if (row.isSelected()) {
                result.add(row.getServer());
            }
        }
        return result;
    }

    public boolean isOkClicked() {
        return okClicked;
    }

    public void showAndWait() {
        stage.showAndWait();
    }
}