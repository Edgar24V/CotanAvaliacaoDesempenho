package com.cotan.avaliacao.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.function.Consumer;

public final class CotanModalHost extends StackPane {

    private final Rectangle scrim = new Rectangle();
    private final StackPane surface = new StackPane();
    private Node content;
    private Consumer<KeyEvent> escapeHandler;

    public CotanModalHost() {
        getStyleClass().add("modal-pane");
        setAlignment(Pos.CENTER);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPickOnBounds(true);

        scrim.setFill(Color.rgb(0, 0, 0, 0.32));
        scrim.setMouseTransparent(false);
        scrim.widthProperty().bind(widthProperty());
        scrim.heightProperty().bind(heightProperty());

        surface.setAlignment(Pos.CENTER);
        surface.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        surface.setPickOnBounds(false);
        getChildren().addAll(scrim, surface);

        addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE && isShowing() && escapeHandler != null) {
                event.consume();
                escapeHandler.accept(event);
            }
        });

        setVisible(false);
        setManaged(false);
    }

    public void show(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("O conteúdo do modal não pode ser nulo.");
        }

        content = node;
        surface.getChildren().setAll(node);
        setManaged(true);
        setVisible(true);
        toFront();

        scrim.setOpacity(0);
        node.setOpacity(0);
        node.setTranslateY(10);
        ParallelTransition entrance = new ParallelTransition(
                new FadeTransition(Duration.millis(160), scrim),
                new FadeTransition(Duration.millis(180), node),
                new TranslateTransition(Duration.millis(180), node)
        );
        ((FadeTransition) entrance.getChildren().get(0)).setToValue(1);
        ((FadeTransition) entrance.getChildren().get(1)).setToValue(1);
        ((TranslateTransition) entrance.getChildren().get(2)).setToY(0);
        entrance.play();
    }

    public void hide(boolean clear) {
        if (!isShowing()) {
            if (clear) clearContent();
            return;
        }

        Node node = content;
        ParallelTransition exit = new ParallelTransition(
                new FadeTransition(Duration.millis(120), scrim),
                new FadeTransition(Duration.millis(120), node),
                new TranslateTransition(Duration.millis(120), node)
        );
        ((FadeTransition) exit.getChildren().get(0)).setToValue(0);
        ((FadeTransition) exit.getChildren().get(1)).setToValue(0);
        ((TranslateTransition) exit.getChildren().get(2)).setToY(6);
        exit.setOnFinished(event -> {
            if (clear) clearContent();
            setVisible(false);
            setManaged(false);
        });
        exit.play();
    }

    public Node getContent() {
        return content;
    }

    public void clearContent() {
        content = null;
        surface.getChildren().clear();
        scrim.setOpacity(0);
    }

    public boolean isShowing() {
        return isVisible() && isManaged() && content != null;
    }

    public void setEscapeHandler(Consumer<KeyEvent> escapeHandler) {
        this.escapeHandler = escapeHandler;
    }

    public Consumer<KeyEvent> getEscapeHandler() {
        return escapeHandler;
    }
}
