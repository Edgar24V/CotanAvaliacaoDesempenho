package com.cotan.avaliacao.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;

public final class CotanIcons {

    private CotanIcons() {}

    public static FontIcon icon(Feather icon) {
        return icon(icon, 17);
    }

    public static FontIcon icon(Feather icon, int size) {
        FontIcon fontIcon = new FontIcon(icon);
        fontIcon.setIconSize(size);
        return fontIcon;
    }

    public static Button button(String text, Feather icon, String... styleClasses) {
        Button button = new Button(text, icon(icon));
        for (String styleClass : styleClasses) {
            if (styleClass != null && !styleClass.isBlank()) {
                switch (styleClass) {
                    case "accent-button" -> addStyleClass(button, "accent");
                    case "danger-button" -> addStyleClass(button, "danger");
                    case "mini-button", "table-action-button" -> addStyleClass(button, "small");
                    case "header-icon-button", "modal-close-button" -> addStyleClass(button, "button-icon");
                    case "button-flat" -> addStyleClass(button, "flat");
                    case "accent", "danger", "success", "button-icon", "button-outlined", "flat", "small", "large" -> addStyleClass(button, styleClass);
                    default -> { }
                }
            }
        }
        return button;
    }

    private static void addStyleClass(Button button, String styleClass) {
        if (!button.getStyleClass().contains(styleClass)) {
            button.getStyleClass().add(styleClass);
        }
    }

    public static void applyIcon(Button button, Feather icon) {
        button.setGraphic(icon(icon));
    }

    public static Feather feather(String key) {
        if (key == null || key.isBlank()) return Feather.CIRCLE;
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "home" -> Feather.HOME;
            case "teachers", "teacher" -> Feather.USER;
            case "administrative", "admin" -> Feather.BRIEFCASE;
            case "evaluation", "check" -> Feather.CHECK_CIRCLE;
            case "aaconnect", "link" -> Feather.LINK;
            case "map", "maps" -> Feather.BAR_CHART_2;
            case "final" -> Feather.AWARD;
            case "students", "student" -> Feather.USERS;
            case "classes", "class" -> Feather.GRID;
            case "subjects", "subject" -> Feather.BOOK_OPEN;
            case "reports", "report" -> Feather.FILE_TEXT;
            case "indicators", "indicator" -> Feather.TARGET;
            case "settings" -> Feather.SETTINGS;
            case "about", "info" -> Feather.INFO;
            case "add", "new", "plus" -> Feather.PLUS;
            case "save" -> Feather.SAVE;
            case "edit" -> Feather.EDIT_2;
            case "delete", "trash" -> Feather.TRASH_2;
            case "refresh" -> Feather.REFRESH_CW;
            case "download", "export" -> Feather.DOWNLOAD;
            case "upload" -> Feather.UPLOAD;
            case "search" -> Feather.SEARCH;
            case "calendar" -> Feather.CALENDAR;
            case "close" -> Feather.X;
            case "clear" -> Feather.ROTATE_CCW;
            case "theme" -> Feather.MOON;
            case "database" -> Feather.DATABASE;
            case "help" -> Feather.HELP_CIRCLE;
            default -> Feather.CIRCLE;
        };
    }
}
