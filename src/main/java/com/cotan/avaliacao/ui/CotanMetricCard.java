package com.cotan.avaliacao.ui;

import atlantafx.base.controls.Card;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

public final class CotanMetricCard extends Card {

    public CotanMetricCard(String title, String value, String note, Feather icon, String tone) {
        Label iconBox = new Label("", CotanIcons.icon(icon, 16));
        iconBox.getStyleClass().add("success".equals(tone) ? "success" : "accent");

        getStyleClass().add("cotan-metric-card");

        Label caption = new Label(title);
        caption.getStyleClass().addAll("text-muted", "text-small");

        HBox header = new HBox(9, iconBox, caption);
        header.setAlignment(Pos.CENTER_LEFT);
        setHeader(header);

        Label amount = new Label(value);
        amount.getStyleClass().add("title-2");

        Label detail = new Label(note == null ? "" : note);
        detail.getStyleClass().addAll("text-muted", "text-small");
        detail.setWrapText(true);

        VBox body = new VBox(6, amount, detail);
        body.setPadding(new Insets(4, 0, 0, 0));
        setBody(body);

        setPrefWidth(235);
        setMinHeight(132);
    }
}
