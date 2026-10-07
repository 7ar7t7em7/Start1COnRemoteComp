package aaaStart1COnRemoteComp;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import java.util.Map;

public class DbSetRow {

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    /** Разрешение менять галочку. Если false — чекбокс будет серым (disabled). */
    private final ReadOnlyBooleanWrapper selectable = new ReadOnlyBooleanWrapper(true);
    private final Map<String, String> values;

    public DbSetRow(Map<String, String> values) {
        this.values = values;

        String dbStatus = getTrimmed("dbstatus");

        if ("M".equalsIgnoreCase(dbStatus)) {
            // Строку с dbstatus = M трогать нельзя
            selected.set(false);
            selectable.set(false);
        } else if ("C".equalsIgnoreCase(dbStatus)) {
            // Строка с dbstatus = C — сразу отмечена
            selected.set(true);
            selectable.set(true);
        } else {
            // Остальные строки — можно отмечать вручную
            selectable.set(true);
        }
    }

    /** Вспомогательный метод: получить значение и обрезать пробелы. */
    private String getTrimmed(String columnName) {
        return get(columnName).trim();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean value) {
        if (selectable.get()) {
            selected.set(value);
        }
    }

    /** Свойство "можно ли менять галочку". Только для чтения. */
    public ReadOnlyBooleanProperty selectableProperty() {
        return selectable.getReadOnlyProperty();
    }

    public boolean isSelectable() {
        return selectable.get();
    }

    public String get(String columnName) {
        if (columnName == null) return "";
        // Сначала точное совпадение
        if (values.containsKey(columnName)) {
            String v = values.get(columnName);
            return v == null ? "" : v;
        }
        // Затем поиск без учёта регистра
        for (Map.Entry<String, String> e : values.entrySet()) {
            if (e.getKey().equalsIgnoreCase(columnName)) {
                return e.getValue() == null ? "" : e.getValue();
            }
        }
        return "";
    }

    public Map<String, String> getValues() {
        return values;
    }
}