package com.cotan.avaliacao.ui.table;

import javafx.collections.ListChangeListener;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Utilitários de padronização das tabelas do COTAN, inspirados no Kubata.
 */
public final class TableUtils {

    private TableUtils() {
    }

    public static void standardize(TableView<?> table) {
        if (table == null) return;
        addStyleIfMissing(table, "bordered");
        addStyleIfMissing(table, "striped");
        table.setTableMenuButtonVisible(true);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        applySmartResize(table);
    }

    public static void readOnly(TableView<?> table) {
        standardize(table);
        table.setEditable(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    public static void editable(TableView<?> table) {
        standardize(table);
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    public static void report(TableView<?> table) {
        standardize(table);
        table.setEditable(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    }

    private static void addStyleIfMissing(TableView<?> table, String style) {
        if (!table.getStyleClass().contains(style)) {
            table.getStyleClass().add(style);
        }
    }

    private static void applySmartResize(TableView<?> table) {
        Runnable resize = () -> {
            if (table.getWidth() <= 0 || table.getColumns().isEmpty()) return;

            double totalWidth = table.getColumns().stream()
                    .filter(TableColumn::isVisible)
                    .mapToDouble(TableColumn::getPrefWidth)
                    .sum();

            double availableWidth = Math.max(0, table.getWidth() - 24);
            table.setColumnResizePolicy(
                    totalWidth < availableWidth
                            ? TableView.CONSTRAINED_RESIZE_POLICY
                            : TableView.UNCONSTRAINED_RESIZE_POLICY
            );
        };

        table.widthProperty().addListener((obs, oldValue, newValue) -> resize.run());
        table.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) change -> resize.run());
    }
}
