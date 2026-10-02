package com.cotan.avaliacao.ui;

import atlantafx.base.controls.CustomTextField;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class CotanHeader extends HBox {

    private final Label title = new Label();
    private final Label breadcrumb = new Label();
    private final Label notificationBadge = new Label();
    private final CustomTextField searchField = new CustomTextField();
    private final List<String> notifications = new ArrayList<>();
    private final Consumer<String> searchConsumer;
    private final Runnable toggleSidebar;
    private final Runnable themeToggle;
    private final ContextMenu notificationMenu = new ContextMenu();
    private final MenuButton account = new MenuButton();
    private final Button primaryAction = CotanIcons.button("", Feather.PLUS, "accent-button");

    public CotanHeader(Consumer<String> searchConsumer, Runnable toggleSidebar, Runnable themeToggle) {
        this.searchConsumer = searchConsumer;
        this.toggleSidebar = toggleSidebar;
        this.themeToggle = themeToggle;

        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(12, 20, 12, 20));
        setSpacing(16);

        Button menu = CotanIcons.button("", Feather.MENU, "button-icon", "flat");
        menu.setTooltip(new Tooltip("Alternar menu lateral"));
        menu.setOnAction(e -> toggleSidebar.run());

        VBox titles = new VBox(1, title, breadcrumb);
        title.getStyleClass().add("title-3");
        breadcrumb.getStyleClass().addAll("text-muted", "text-small");

        searchField.setPromptText("Pesquisar no sistema...");
        searchField.setPrefWidth(360);
        searchField.setMinWidth(190);
        searchField.setLeft(CotanIcons.icon(Feather.SEARCH, 15));
        Button clearSearch = CotanIcons.button("", Feather.X, "button-icon", "flat", "small");
        clearSearch.setTooltip(new Tooltip("Limpar pesquisa"));
        clearSearch.setOnAction(e -> clearSearch());
        searchField.setRight(clearSearch);

        Region spacerLeft = new Region();
        HBox.setHgrow(spacerLeft, Priority.ALWAYS);

        primaryAction.setVisible(false);
        primaryAction.setManaged(false);

        Button bell = CotanIcons.button("", Feather.BELL, "button-icon", "flat");
        bell.setTooltip(new Tooltip("Notificações"));
        notificationBadge.getStyleClass().addAll("accent", "text-small");
        notificationBadge.setVisible(false);
        notificationBadge.setManaged(false);

        StackPane bellWrap = new StackPane(bell, notificationBadge);
        StackPane.setAlignment(notificationBadge, Pos.TOP_RIGHT);
        StackPane.setMargin(notificationBadge, new Insets(-4, -4, 0, 0));
        bell.setOnAction(e -> rebuildNotifications(bell));

        Button theme = CotanIcons.button("", Feather.MOON, "button-icon", "flat");
        theme.setTooltip(new Tooltip("Alternar tema"));
        theme.setOnAction(e -> themeToggle.run());

        account.setGraphic(CotanIcons.icon(Feather.USER, 16));
        account.setText("Administrador");
        account.setTooltip(new Tooltip("Conta e sessão"));
        account.getStyleClass().add("button-outlined");
        MenuItem profile = new MenuItem("Perfil", CotanIcons.icon(Feather.USER, 13));
        profile.setDisable(true);
        MenuItem settings = new MenuItem("Configurações", CotanIcons.icon(Feather.SETTINGS, 13));
        settings.setOnAction(e -> searchConsumer.accept("settings"));
        MenuItem about = new MenuItem("Sobre o COTAN", CotanIcons.icon(Feather.INFO, 13));
        about.setOnAction(e -> searchConsumer.accept("about"));
        account.getItems().addAll(profile, new SeparatorMenuItem(), settings, about);

        HBox.setHgrow(searchField, Priority.ALWAYS);
        getChildren().addAll(menu, titles, searchField, primaryAction, spacerLeft, bellWrap, theme, account);

        searchField.setOnAction(e -> {
            String query = searchField.getText() == null
                    ? ""
                    : searchField.getText().trim().toLowerCase(Locale.ROOT);
            if (!query.isBlank()) searchConsumer.accept(query);
        });
    }

    public void setPage(String title, String breadcrumb) {
        this.title.setText(title);
        this.breadcrumb.setText(breadcrumb);
    }

    public void addNotification(String message) {
        if (message == null || message.isBlank()) return;
        notifications.add(0, message);
        while (notifications.size() > 20) notifications.remove(notifications.size() - 1);
        notificationBadge.setText(notifications.size() > 99 ? "99+" : String.valueOf(notifications.size()));
        notificationBadge.setVisible(true);
        notificationBadge.setManaged(true);
    }

    private void rebuildNotifications(Button anchor) {
        notificationMenu.getItems().clear();

        if (notifications.isEmpty()) {
            MenuItem empty = new MenuItem("Nenhuma notificação");
            empty.setDisable(true);
            notificationMenu.getItems().add(empty);
        } else {
            for (String n : notifications) {
                Label text = new Label(n);
                text.setWrapText(true);
                text.setMaxWidth(280);
                HBox row = new HBox(8, CotanIcons.icon(Feather.INFO, 13), text);
                row.setPadding(new Insets(7, 10, 7, 10));
                row.setAlignment(Pos.CENTER_LEFT);
                notificationMenu.getItems().add(new CustomMenuItem(row, false));
            }
            MenuItem clear = new MenuItem("Marcar como lidas", CotanIcons.icon(Feather.CHECK, 13));
            clear.setOnAction(e -> {
                notifications.clear();
                notificationBadge.setVisible(false);
                notificationBadge.setManaged(false);
            });
            notificationMenu.getItems().add(new SeparatorMenuItem());
            notificationMenu.getItems().add(clear);
        }

        notificationMenu.show(anchor, javafx.geometry.Side.BOTTOM, 0, 5);
    }


    public void setPrimaryAction(String text, Feather icon, Runnable action, boolean visible) {
        primaryAction.setText(text == null ? "" : text);
        primaryAction.setGraphic(CotanIcons.icon(icon == null ? Feather.PLUS : icon, 15));
        primaryAction.setOnAction(e -> {
            if (action != null) action.run();
        });
        primaryAction.setVisible(visible);
        primaryAction.setManaged(visible);
    }

    public void clearSearch() {
        searchField.clear();
    }

    public String getSearchText() {
        return searchField.getText() == null ? "" : searchField.getText();
    }
}
