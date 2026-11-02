package aaaStart1COnRemoteComp;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

public class DatabaseResultWindow {

    private final Stage stage;
    private final TableView<DbSetRow> table = new TableView<>();

    /** Список, куда попадут выбранные строки после нажатия "Записать". */
    private final List<DbSetRow> confirmedRows;

    public DatabaseResultWindow(Stage owner, DatabaseReader.QueryResult result,
                                String serverInfo, String dbName,
                                List<DbSetRow> confirmedRows) {
        this.confirmedRows = confirmedRows;
        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Результат запроса: " + serverInfo + " / " + dbName);

        // ==== Колонка с чекбоксами ====
        TableColumn<DbSetRow, Boolean> checkColumn = new TableColumn<>("");
        checkColumn.setCellValueFactory(data -> data.getValue().selectedProperty());
        checkColumn.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setOnAction(e -> {
                    DbSetRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null && row.isSelectable()) {
                        row.setSelected(checkBox.isSelected());
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                DbSetRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(row.isSelected());
                    checkBox.setDisable(!row.isSelectable());
                    setGraphic(checkBox);
                }
            }
        });
        checkColumn.setPrefWidth(40);
        checkColumn.setSortable(false);
        table.getColumns().add(checkColumn);

        // ==== Колонки данных ====
        for (String columnName : result.columns) {
            TableColumn<DbSetRow, String> column = new TableColumn<>(columnName);
            column.setCellValueFactory(data ->
                    new SimpleStringProperty(data.getValue().get(columnName)));
            column.setPrefWidth(120);
            table.getColumns().add(column);
        }

        // ==== Данные ====
        List<DbSetRow> rows = result.rows.stream()
                .map(DbSetRow::new)
                .collect(Collectors.toList());
        table.setItems(FXCollections.observableArrayList(rows));

        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // ==== Кнопка "Записать" ====
        Button applyButton = new Button("Записать");
        applyButton.setOnAction(e -> onApply());

        Button cancelButton = new Button("Отмена");
        cancelButton.setOnAction(e -> stage.close());

        HBox buttons = new HBox(10, applyButton, cancelButton);
        buttons.setPadding(new Insets(10));
        buttons.setStyle("-fx-alignment: center-right;");

        BorderPane root = new BorderPane();
        root.setCenter(table);
        root.setBottom(buttons);

        stage.setScene(new Scene(root, 900, 550));
    }

    /** Обработка нажатия "Записать": собираем выбранные строки и закрываем окно. */
    private void onApply() {
        confirmedRows.clear();
        confirmedRows.addAll(
                table.getItems().stream()
                        .filter(DbSetRow::isSelected)
                        .collect(Collectors.toList())
        );

        if (confirmedRows.isEmpty()) return;

        stage.close();
    }

    public List<DbSetRow> getConfirmedRows() {
        return confirmedRows;
    }

    public void showAndWait() {
        stage.showAndWait();
    }
}