package com.cotan.avaliacao.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Host de modal proprietário do COTAN.
 *
 * Não depende de ModalPane/ModalBox do AtlantaFX.
 * O AtlantaFX continua responsável apenas pelo tema visual da aplicação.
 */
public final class CotanModalHost extends StackPane {

    private final StackPane surface = new StackPane();
    private final Rectangle scrim = new Rectangle();
    private Node content;
    private Consumer<KeyEvent> escapeHandler;

    public CotanModalHost() {
        getStyleClass().add("cotan-modal-host");
        setAlignment(Pos.CENTER);
        setPickOnBounds(true);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        scrim.getStyleClass().add("cotan-modal-scrim");
        scrim.setMouseTransparent(false);
        scrim.widthProperty().bind(widthProperty());
        scrim.heightProperty().bind(heightProperty());

        surface.getStyleClass().add("cotan-modal-surface");
        surface.setAlignment(Pos.CENTER);
        surface.setPickOnBounds(false);
        surface.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        VBox.setVgrow(surface, javafx.scene.layout.Priority.ALWAYS);
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
        Objects.requireNonNull(node, "O conteúdo do modal não pode ser nulo.");

        content = node;
        surface.getChildren().setAll(node);

        setManaged(true);
        setVisible(true);
        toFront();

        scrim.setOpacity(0);
        node.setOpacity(0);
        node.setScaleX(0.96);
        node.setScaleY(0.96);

        FadeTransition fade = new FadeTransition(Duration.millis(160), scrim);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition contentFade = new FadeTransition(Duration.millis(170), node);
        contentFade.setFromValue(0);
        contentFade.setToValue(1);
        contentFade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(190), node);
        scale.setFromX(0.96);
        scale.setFromY(0.96);
        scale.setToX(1);
        scale.setToY(1);
        scale.setInterpolator(Interpolator.EASE_OUT);

        fade.play();
        contentFade.play();
        scale.play();

        Platform.runLater(() -> requestFocus());
    }

    public void hide(boolean clear) {
        if (!isShowing()) {
            if (clear) clearContent();
            return;
        }

        Node node = content;

        FadeTransition fade = new FadeTransition(Duration.millis(120), scrim);
        fade.setFromValue(scrim.getOpacity());
        fade.setToValue(0);

        if (node == null) {
            fade.setOnFinished(e -> {
                if (clear) clearContent();
                setVisible(false);
                setManaged(false);
            });
            fade.play();
            return;
        }

        FadeTransition contentFade = new FadeTransition(Duration.millis(110), node);
        contentFade.setFromValue(node.getOpacity());
        contentFade.setToValue(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(110), node);
        scale.setFromX(node.getScaleX());
        scale.setFromY(node.getScaleY());
        scale.setToX(0.98);
        scale.setToY(0.98);

        fade.play();
        contentFade.play();
        scale.play();

        fade.setOnFinished(e -> {
            if (clear) clearContent();
            setVisible(false);
            setManaged(false);
        });
    }

    public boolean isShowing() {
        return isVisible() && isManaged() && content != null;
    }

    public Node getContent() {
        return content;
    }

    public void clearContent() {
        content = null;
        surface.getChildren().clear();
    }

    public void setEscapeHandler(Consumer<KeyEvent> escapeHandler) {
        this.escapeHandler = escapeHandler;
    }

    public Consumer<KeyEvent> getEscapeHandler() {
        return escapeHandler;
    }
}
