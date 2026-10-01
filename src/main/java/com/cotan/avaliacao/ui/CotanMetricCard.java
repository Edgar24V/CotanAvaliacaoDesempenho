package com.cotan.avaliacao.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

public final class CotanMetricCard extends VBox {

    public CotanMetricCard(String title, String value, String note, Feather icon, String tone) {
        getStyleClass().addAll("cotan-metric-card", "tone-" + (tone == null ? "accent" : tone));
        setPadding(new Insets(16));
        setSpacing(9);
        setPrefWidth(235);
        setMinHeight(118);

        HBox top = new HBox(9);
        top.setAlignment(Pos.CENTER_LEFT);

        Label iconBox = new Label("", CotanIcons.icon(icon, 18));
        iconBox.getStyleClass().add("metric-icon");

        Label caption = new Label(title);
        caption.getStyleClass().add("metric-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        top.getChildren().addAll(iconBox, caption, spacer);

        Label amount = new Label(value);
        amount.getStyleClass().add("metric-value");

        Label detail = new Label(note == null ? "" : note);
        detail.getStyleClass().add("metric-note");
        detail.setWrapText(true);

        getChildren().addAll(top, amount, detail);
    }
}
