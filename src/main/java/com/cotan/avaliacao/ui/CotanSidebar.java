package com.cotan.avaliacao.ui;

import atlantafx.base.controls.Sidebar;
import atlantafx.base.controls.SidebarFooter;
import atlantafx.base.controls.SidebarGroup;
import atlantafx.base.controls.SidebarHeader;
import atlantafx.base.controls.SidebarNav;
import org.kordamp.ikonli.feather.Feather;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class CotanSidebar extends Sidebar<String> {

    private static final double EXPANDED_WIDTH = 260;

    private final Consumer<String> navigation;
    private final Map<String, SidebarNav<String>> itemsByRoute = new HashMap<>();

    public CotanSidebar(Consumer<String> navigation) {
        this.navigation = navigation;
        getStyleClass().add("cotan-ribbon-sidebar");
        setPrefWidth(EXPANDED_WIDTH);
        setMinWidth(72);
        SidebarHeader<String> header = new SidebarHeader<>("COTAN", CotanIcons.icon(Feather.ACTIVITY, 20));
        header.getStyleClass().add("cotan-sidebar-brand");
        setHeader(header);
        SidebarFooter<String> footer = new SidebarFooter<>("Avaliação e Desempenho", CotanIcons.icon(Feather.BAR_CHART_2, 16));
        footer.getStyleClass().add("cotan-sidebar-footer");
        setFooter(footer);
        setOnItemClick(event -> {
            String route = event.getItem().getValue();
            if (route != null) {
                navigation.accept(route);
            }
        });
        buildGroups();
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

    private void addGroup(String title, SidebarNav<String>... items) {
        SidebarGroup<String> group = new SidebarGroup<>(title);
        group.getItems().addAll(items);
        getItems().add(group);
    }

    private SidebarNav<String> item(String title, String route, Feather icon) {
        SidebarNav<String> item = new SidebarNav<>(title, CotanIcons.icon(icon, 16));
        item.setValue(route);
        itemsByRoute.put(route, item);
        return item;
    }

    public void setActive(String route) {
        SidebarNav<String> item = itemsByRoute.get(route);
        if (item != null) {
            getSelectionModel().select(item);
        }
    }
}
