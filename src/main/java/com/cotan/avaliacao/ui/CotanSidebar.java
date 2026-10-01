package com.cotan.avaliacao.ui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CotanSidebar extends VBox {

    private static final double EXPANDED_WIDTH = 260;
    private static final double COMPACT_WIDTH = 82;

    private final Consumer<String> navigation;
    private final VBox content = new VBox(7);
    private final ScrollPane scroller = new ScrollPane(content);
    private final List<ToggleButton> items = new ArrayList<>();
    private final List<TitledPane> groups = new ArrayList<>();
    private final java.util.Map<ToggleButton, String> labels = new java.util.HashMap<>();
    private boolean collapsed;

    public CotanSidebar(Consumer<String> navigation) {
        this.navigation = navigation;
        getStyleClass().add("cotan-sidebar");
        setPadding(new Insets(10));
        setSpacing(8);
        setMinWidth(EXPANDED_WIDTH);
        setPrefWidth(EXPANDED_WIDTH);
        setMaxWidth(EXPANDED_WIDTH);

        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setPannable(true);
        scroller.setFocusTraversable(false);
        scroller.getStyleClass().add("cotan-sidebar-scroll");

        VBox brand = new VBox(2);
        brand.setPadding(new Insets(13, 12, 12, 12));
        brand.getChildren().addAll(
                new javafx.scene.control.Label("COTAN", CotanIcons.icon(Feather.ACTIVITY, 20)),
                new javafx.scene.control.Label("Avaliação e Desempenho")
        );
        brand.getStyleClass().add("cotan-brand");

        content.getChildren().add(brand);
        buildGroups();
        getChildren().add(scroller);
        VBox.setVgrow(scroller, Priority.ALWAYS);
    }

    private void buildGroups() {
        addGroup("INÍCIO",
                item("Início", "dashboard", Feather.HOME),
                item("Professores", "teachers", Feather.USER),
                item("Administrativos", "administrative", Feather.BRIEFCASE)
        );

        addGroup("AVALIAÇÕES",
                item("Avaliação — Professores", "professor-evaluation", Feather.CHECK_CIRCLE),
                item("Avaliação — Administrativos", "administrative-evaluation", Feather.CHECK_CIRCLE),
                item("AACONECT — Professores", "aaconnect-professors", Feather.LINK),
                item("AACONECT — Administrativos", "aaconnect-administrative", Feather.LINK)
        );

        addGroup("MAPAS E RESULTADOS",
                item("Mapa 1º Trimestre", "map-1", Feather.BAR_CHART_2),
                item("Mapa 2º Trimestre", "map-2", Feather.BAR_CHART_2),
                item("Mapa 3º Trimestre", "map-3", Feather.BAR_CHART_2),
                item("Mapa Final — Professor", "map-final-professor", Feather.AWARD),
                item("Mapa Final — Administrativo", "map-final-administrative", Feather.AWARD)
        );

        addGroup("DADOS DE APOIO",
                item("Alunos", "students", Feather.USERS),
                item("Turmas", "classes", Feather.GRID),
                item("Disciplinas", "subjects", Feather.BOOK_OPEN),
                item("Relatórios", "reports", Feather.FILE_TEXT),
                item("Indicadores", "indicators", Feather.TARGET)
        );

        addGroup("SISTEMA",
                item("Configurações", "settings", Feather.SETTINGS),
                item("Sobre", "about", Feather.INFO)
        );
    }

    private void addGroup(String title, Node... nodes) {
        VBox box = new VBox(4);
        box.getChildren().addAll(nodes);
        TitledPane pane = new TitledPane(title, box);
        pane.setExpanded("INÍCIO".equals(title) || "AVALIAÇÕES".equals(title));
        pane.getStyleClass().add("cotan-nav-group");
        pane.expandedProperty().addListener((obs, oldValue, expanded) -> {
            FadeTransition fade = new FadeTransition(Duration.millis(140), box);
            fade.setFromValue(expanded ? 0 : 1);
            fade.setToValue(expanded ? 1 : 0);
            fade.setInterpolator(Interpolator.EASE_OUT);
            fade.play();
        });
        groups.add(pane);
        content.getChildren().add(pane);
    }

    private ToggleButton item(String title, String section, Feather icon) {
        ToggleButton button = new ToggleButton(title, CotanIcons.icon(icon, 16));
        button.setUserData(section);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
        button.setGraphicTextGap(10);
        button.setPadding(new Insets(8, 10, 8, 12));
        button.getStyleClass().add("cotan-nav-item");
        button.setTooltip(new Tooltip(title));
        button.setOnAction(e -> {
            setActive(section);
            navigation.accept(section);
        });
        items.add(button);
        labels.put(button, title);
        return button;
    }

    public void setActive(String section) {
        for (ToggleButton item : items) {
            boolean selected = section.equals(item.getUserData());
            item.setSelected(selected);
            if (selected) {
                expandContainingGroup(item);
            }
        }
    }

    private void expandContainingGroup(ToggleButton target) {
        for (TitledPane group : groups) {
            if (group.getContent() instanceof VBox box && box.getChildren().contains(target)) {
                group.setExpanded(true);
                break;
            }
        }
    }

    public void setCollapsed(boolean collapsed) {
        if (this.collapsed == collapsed) return;
        this.collapsed = collapsed;

        for (ToggleButton item : items) {
            item.setText(collapsed ? "" : labels.getOrDefault(item, ""));
            item.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
            item.setAlignment(Pos.CENTER);
            item.setPadding(collapsed ? new Insets(8) : new Insets(8, 10, 8, 12));
            item.setTooltip(new Tooltip(labels.getOrDefault(item, "")));
        }

        for (TitledPane group : groups) {
            group.setExpanded(collapsed ? false : group.isExpanded());
        }

        if (!collapsed) {
            animateWidth(EXPANDED_WIDTH, 1.0);
            setVisible(true);
            setManaged(true);
        } else {
            animateWidth(COMPACT_WIDTH, 0.96);
        }
    }

    private void animateWidth(double width, double opacity) {
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.millis(220),
                        new KeyValue(minWidthProperty(), width, Interpolator.EASE_BOTH),
                        new KeyValue(prefWidthProperty(), width, Interpolator.EASE_BOTH),
                        new KeyValue(maxWidthProperty(), width, Interpolator.EASE_BOTH),
                        new KeyValue(this.opacityProperty(), opacity, Interpolator.EASE_BOTH)
                )
        );
        timeline.play();
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void updateCompactPresentation() { setCollapsed(collapsed); }
}
