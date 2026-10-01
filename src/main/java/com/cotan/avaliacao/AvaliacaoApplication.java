package com.cotan.avaliacao;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Cotan • Avaliação e Desempenho
 *
 * Shell completo da aplicação desktop:
 * - Dashboard executivo
 * - Gestão de alunos, professores, turmas e disciplinas
 * - Cadastro de avaliações
 * - Lançamento/edição de notas
 * - Cálculo ponderado de desempenho
 * - Relatórios e exportação CSV
 * - Backup do banco SQLite
 * - Tema claro/escuro com AtlantaFX
 */
public class AvaliacaoApplication extends Application {

    private static final String APP_NAME = "COTAN";
    private static final String APP_SUBTITLE = "Avaliação e Desempenho";

    private ConfigurableApplicationContext springContext;
    private Database database;
    private Stage stage;
    private BorderPane root;
    private StackPane content;
    private VBox sidebar;
    private Label pageTitle;
    private Label breadcrumb;
    private Label dbStatus;
    private TextField searchField;
    private Button topAction;
    private String currentSection = "dashboard";
    private Button activeNav;
    private boolean darkMode = false;

    private static final String LIGHT_THEME = new PrimerLight().getUserAgentStylesheet();
    private static final String DARK_THEME = new PrimerDark().getUserAgentStylesheet();

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle(APP_NAME + " • " + APP_SUBTITLE);
        stage.setMinWidth(1180);
        stage.setMinHeight(720);
        stage.setWidth(1360);
        stage.setHeight(820);

        showLoading();
        stage.show();

        CompletableFuture.runAsync(() -> {
            try {
                springContext = new SpringApplicationBuilder(AvaliacaoApplication.class)
                        .headless(false)
                        .run();
                database = new Database();
                database.open();
                Platform.runLater(this::showShell);
            } catch (Exception ex) {
                Platform.runLater(() -> showStartupError(ex));
            }
        });

        stage.setOnCloseRequest(e -> shutdown());
    }

    private void showLoading() {
        Application.setUserAgentStylesheet(LIGHT_THEME);
        StackPane pane = new StackPane();
        pane.getStyleClass().add("app-background");

        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);

        Label mark = new Label("COTAN");
        mark.getStyleClass().add("loading-mark");

        Label title = new Label(APP_SUBTITLE);
        title.getStyleClass().add("loading-title");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        Label status = new Label("A preparar a base de dados e os módulos...");
        status.getStyleClass().add("muted");

        box.getChildren().addAll(mark, title, spinner, status);
        pane.getChildren().add(box);

        Scene scene = new Scene(pane);
        installCss(scene);
        stage.setScene(scene);
    }

    private void showStartupError(Throwable ex) {
        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));

        Label title = new Label("Não foi possível iniciar o COTAN");
        title.getStyleClass().add("page-title");

        Label detail = new Label(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        detail.setWrapText(true);
        detail.setMaxWidth(850);

        Button close = new Button("Fechar");
        close.setOnAction(e -> stage.close());

        box.getChildren().addAll(title, detail, close);
        StackPane pane = new StackPane(box);
        pane.getStyleClass().add("app-background");

        Scene scene = new Scene(pane, 1180, 720);
        installCss(scene);
        stage.setScene(scene);
    }

    private void showShell() {
        root = new BorderPane();
        root.getStyleClass().add("app-background");

        sidebar = buildSidebar();
        root.setLeft(sidebar);

        root.setTop(buildTopBar());

        content = new StackPane();
        content.setPadding(new Insets(24));
        root.setCenter(content);

        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, stage.getWidth(), stage.getHeight());
        installCss(scene);
        stage.setScene(scene);
        stage.centerOnScreen();

        showSection("dashboard");
    }

    private void installCss(Scene scene) {
        scene.getStylesheets().clear();
        try {
            String css = Objects.requireNonNull(
                    getClass().getResource("/app.css"),
                    "app.css não encontrado"
            ).toExternalForm();
            scene.getStylesheets().add(css);
        } catch (Exception ignored) {
            // O estilo AtlantaFX continua funcional mesmo sem CSS adicional.
        }
    }

    private VBox buildSidebar() {
        VBox side = new VBox(12);
        side.getStyleClass().add("sidebar");
        side.setPadding(new Insets(24, 14, 18, 14));
        side.setPrefWidth(250);

        VBox brand = new VBox(2);
        brand.setPadding(new Insets(0, 12, 20, 12));

        Label brandTitle = new Label("COTAN");
        brandTitle.getStyleClass().add("brand-title");

        Label brandSub = new Label("Avaliação e Desempenho");
        brandSub.getStyleClass().add("brand-subtitle");

        brand.getChildren().addAll(brandTitle, brandSub);

        VBox main = new VBox(8);
        main.getChildren().addAll(
                navGroupLabel("NAVEGAÇÃO PRINCIPAL"),
                navButton("⌂", "Início", "dashboard"),
                navButton("◆", "Professores", "teachers"),
                navButton("◆", "Administrativos", "administrative")
        );

        VBox evaluations = new VBox(6);
        evaluations.getChildren().addAll(
                navGroupLabel("AVALIAÇÕES"),
                navButton("✓", "Avaliação — Professores", "professor-evaluation"),
                navButton("✓", "Avaliação — Administrativos", "administrative-evaluation"),
                navButton("◎", "AACONECT — Professores", "aaconnect-professors"),
                navButton("◎", "AACONECT — Administrativos", "aaconnect-administrative")
        );

        VBox maps = new VBox(6);
        maps.getChildren().addAll(
                navGroupLabel("MAPAS E RESULTADOS"),
                navButton("1", "Mapa 1º Trimestre", "map-1"),
                navButton("2", "Mapa 2º Trimestre", "map-2"),
                navButton("3", "Mapa 3º Trimestre", "map-3"),
                navButton("★", "Mapa Final — Professor", "map-final-professor"),
                navButton("★", "Mapa Final — Administrativo", "map-final-administrative")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox support = new VBox(6);
        support.getChildren().addAll(
                navGroupLabel("DADOS DE APOIO"),
                navButton("●", "Alunos", "students"),
                navButton("▦", "Turmas", "classes"),
                navButton("◈", "Disciplinas", "subjects"),
                navButton("◆", "Relatórios", "reports"),
                navButton("⚙", "Configurações", "settings"),
                navButton("?", "Sobre", "about")
        );
        side.getChildren().addAll(brand, new Separator(), main, evaluations, maps, spacer, support);
        return side;
    }

    private Label navGroupLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("nav-group-label");
        l.setPadding(new Insets(10, 12, 3, 12));
        return l;
    }

    private Button navButton(String icon, String label, String section) {
        Button button = new Button(icon + "   " + label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("sidebar-button");
        button.setOnAction(e -> showSection(section));
        button.setUserData(section);
        return button;
    }

    private HBox buildTopBar() {
        HBox bar = new HBox(16);
        bar.getStyleClass().add("topbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(17, 24, 17, 24));

        VBox titles = new VBox(2);
        pageTitle = new Label("Dashboard");
        pageTitle.getStyleClass().add("top-title");
        breadcrumb = new Label("Início");
        breadcrumb.getStyleClass().add("breadcrumb");
        titles.getChildren().addAll(pageTitle, breadcrumb);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField = new TextField();
        searchField.setPromptText("Pesquisar neste módulo...");
        searchField.setPrefWidth(290);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldValue, newValue) -> refreshCurrentSection());

        topAction = new Button("＋  Novo");
        topAction.getStyleClass().add("accent-button");
        topAction.setVisible(false);
        topAction.setManaged(false);

        Label user = new Label("Administrador");
        user.getStyleClass().add("user-pill");

        bar.getChildren().addAll(titles, spacer, searchField, topAction, user);
        return bar;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(10);
        bar.getStyleClass().add("statusbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(9, 18, 9, 18));

        dbStatus = new Label("●  SQLite • conectado");
        dbStatus.getStyleClass().add("status-ok");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label version = new Label("Cotan Avaliação e Desempenho • 1.0");
        version.getStyleClass().add("muted");

        bar.getChildren().addAll(dbStatus, spacer, version);
        return bar;
    }

    private void showSection(String section) {
        currentSection = section;
        searchField.clear();

        Map<String, String> titles = Map.ofEntries(
                Map.entry("dashboard", "Início"),
                Map.entry("teachers", "Professores"),
                Map.entry("administrative", "Administrativos"),
                Map.entry("professor-evaluation", "Avaliação — Professores"),
                Map.entry("administrative-evaluation", "Avaliação — Administrativos"),
                Map.entry("aaconnect-professors", "AACONECT — Professores"),
                Map.entry("aaconnect-administrative", "AACONECT — Administrativos"),
                Map.entry("map-1", "Mapa 1º Trimestre"),
                Map.entry("map-2", "Mapa 2º Trimestre"),
                Map.entry("map-3", "Mapa 3º Trimestre"),
                Map.entry("map-final-professor", "Mapa Final — Professor"),
                Map.entry("map-final-administrative", "Mapa Final — Administrativo"),
                Map.entry("students", "Gestão de Alunos"),
                Map.entry("classes", "Gestão de Turmas"),
                Map.entry("subjects", "Gestão de Disciplinas"),
                Map.entry("reports", "Relatórios de Desempenho"),
                Map.entry("settings", "Configurações"),
                Map.entry("about", "Sobre o sistema")
        );

        pageTitle.setText(titles.getOrDefault(section, "COTAN"));
        breadcrumb.setText("Início  /  " + titles.getOrDefault(section, "COTAN"));

        updateActiveNav();
        updateTopAction();

        refreshCurrentSection();
    }

    private void updateActiveNav() {
        if (activeNav != null) activeNav.getStyleClass().remove("selected");
        if (sidebar == null) return;

        for (Node node : sidebar.lookupAll(".sidebar-button")) {
            if (node instanceof Button button && currentSection.equals(button.getUserData())) {
                button.getStyleClass().add("selected");
                activeNav = button;
                break;
            }
        }
    }

    private void updateTopAction() {
        boolean hasAction = Set.of(
                "students","teachers","administrative","classes","subjects","assessments",
                "professor-evaluation","administrative-evaluation"
        ).contains(currentSection);
        topAction.setVisible(hasAction);
        topAction.setManaged(hasAction);
        topAction.setOnAction(e -> {
            switch (currentSection) {
                case "students" -> studentDialog(null);
                case "teachers" -> staffDialog("PROFESSOR", null);
                case "administrative" -> staffDialog("ADMINISTRATIVO", null);
                case "professor-evaluation" -> showSection("professor-evaluation");
                case "administrative-evaluation" -> showSection("administrative-evaluation");
                case "classes" -> classDialog(null);
                case "subjects" -> subjectDialog(null);
                case "assessments" -> assessmentDialog(null);
            }
        });
    }

    private void refreshCurrentSection() {
        if (content == null || database == null) return;
        try {
            switch (currentSection) {
                case "dashboard" -> content.getChildren().setAll(buildDashboard());
                case "administrative" -> content.getChildren().setAll(buildStaff("ADMINISTRATIVO"));
                case "professor-evaluation" -> content.getChildren().setAll(buildPerformanceEvaluation("PROFESSOR"));
                case "administrative-evaluation" -> content.getChildren().setAll(buildPerformanceEvaluation("ADMINISTRATIVO"));
                case "aaconnect-professors" -> content.getChildren().setAll(buildAaconnect("PROFESSOR"));
                case "aaconnect-administrative" -> content.getChildren().setAll(buildAaconnect("ADMINISTRATIVO"));
                case "map-1" -> content.getChildren().setAll(buildPerformanceMapAll(1));
                case "map-2" -> content.getChildren().setAll(buildPerformanceMapAll(2));
                case "map-3" -> content.getChildren().setAll(buildPerformanceMapAll(3));
                case "map-final-professor" -> content.getChildren().setAll(buildPerformanceFinal("PROFESSOR"));
                case "map-final-administrative" -> content.getChildren().setAll(buildPerformanceFinal("ADMINISTRATIVO"));
                case "students" -> content.getChildren().setAll(buildStudents());
                case "teachers" -> content.getChildren().setAll(buildStaff("PROFESSOR"));
                case "classes" -> content.getChildren().setAll(buildClasses());
                case "subjects" -> content.getChildren().setAll(buildSubjects());
                case "assessments" -> content.getChildren().setAll(buildAssessments());
                case "grades" -> content.getChildren().setAll(buildGrades());
                case "reports" -> content.getChildren().setAll(buildReports());
                case "settings" -> content.getChildren().setAll(buildSettings());
                case "about" -> content.getChildren().setAll(buildAbout());
                default -> content.getChildren().setAll(buildDashboard());
            }
        } catch (Exception ex) {
            showError("Erro ao carregar o módulo", ex);
        }
    }

    // -------------------------------------------------------------------------
    // DASHBOARD
    // -------------------------------------------------------------------------

    private Node buildDashboard() throws SQLException {
        VBox page = pageContainer();

        HBox hero = new HBox(22);
        hero.getStyleClass().add("home-hero");
        hero.setPadding(new Insets(28));
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox intro = new VBox(7);
        Label eyebrow = label("COTAN • SISTEMA INFORMATIZADO", "hero-eyebrow");
        Label title = label("Avaliação de Desempenho", "hero-title");
        title.setWrapText(true);
        Label text = label(
                "Transformamos a estrutura do mapa Excel num fluxo digital de cadastro, avaliação trimestral e resultados finais.",
                "hero-subtitle"
        );
        text.setWrapText(true);
        intro.getChildren().addAll(eyebrow, title, text);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        VBox excelBadge = new VBox(4);
        excelBadge.getStyleClass().add("excel-badge");
        excelBadge.getChildren().addAll(
                label("MODELO DE REFERÊNCIA", "badge-caption"),
                label("10 folhas funcionais", "badge-value"),
                label("Professores • Administrativos • Trimestres • Mapas finais", "badge-detail")
        );
        excelBadge.setPadding(new Insets(15));
        hero.getChildren().addAll(intro, heroSpacer, excelBadge);

        HBox heading = sectionHeading(
                "Navegação do sistema",
                "Cada cartão representa uma área que será substituída pelo processo digital do Excel."
        );

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);

        addHomeCard(grid, 0, 0, "◆", "Professores", "Cadastro e acompanhamento dos professores.", "teachers");
        addHomeCard(grid, 1, 0, "◆", "Administrativos", "Cadastro e acompanhamento dos colaboradores administrativos.", "administrative");
        addHomeCard(grid, 0, 1, "✓", "Avaliação — Professores", "Lançamento dos indicadores e avaliação de desempenho docente.", "professor-evaluation");
        addHomeCard(grid, 1, 1, "✓", "Avaliação — Administrativos", "Lançamento dos indicadores e avaliação de desempenho administrativo.", "administrative-evaluation");
        addHomeCard(grid, 0, 2, "◎", "AACONECT", "Resultados/conexões dos professores e administrativos.", "aaconnect-professors");
        addHomeCard(grid, 1, 2, "1–3", "Mapas Trimestrais", "Mapas do 1º, 2º e 3º trimestre.", "map-1");
        addHomeCard(grid, 0, 3, "★", "Mapa Final — Professor", "Consolidação anual dos resultados dos professores.", "map-final-professor");
        addHomeCard(grid, 1, 3, "★", "Mapa Final — Administrativo", "Consolidação anual dos resultados administrativos.", "map-final-administrative");

        VBox process = card();
        process.getChildren().addAll(
                label("Fluxo operacional", "card-title"),
                label("1  Cadastro →  2  Avaliação →  3  Resultado trimestral →  4  Consolidação final →  5  Relatório", "process-flow"),
                label("A regra de classificação observada no Excel utiliza as faixas Mau, Suficiente, Bom e Muito bom; ela ficará centralizada no motor de avaliação para evitar fórmulas espalhadas.", "muted")
        );

        page.getChildren().addAll(hero, heading, grid, process);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    private void addHomeCard(GridPane grid, int col, int row, String icon, String title, String description, String section) {
        VBox card = new VBox(8);
        card.getStyleClass().addAll("card", "home-nav-card");
        card.setPadding(new Insets(18));
        card.setOnMouseClicked(e -> showSection(section));

        HBox line = new HBox(10);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getChildren().addAll(label(icon, "home-icon"), label(title, "home-card-title"));

        Label desc = label(description, "muted");
        desc.setWrapText(true);

        Label action = label("Abrir módulo  →", "home-action");
        card.getChildren().addAll(line, desc, action);

        grid.add(card, col, row);
    }

    private Node buildExcelAreaPage(String section) {
        Map<String, String[]> modules = Map.ofEntries(
                Map.entry("administrative", new String[]{"Administrativos", "PREENCHER_ADMINISTRATIVO", "Cadastro e preparação dos dados dos colaboradores administrativos."}),
                Map.entry("professor-evaluation", new String[]{"Avaliação — Professores", "PREENCHER_PROFESSOR", "Área principal para lançamento dos indicadores e classificação do desempenho dos professores."}),
                Map.entry("administrative-evaluation", new String[]{"Avaliação — Administrativos", "PREENCHER_ADMINISTRATIVO", "Área principal para lançamento dos indicadores e classificação do desempenho administrativo."}),
                Map.entry("aaconnect-professors", new String[]{"AACONECT — Professores", "AACONECT_PROFESSORES", "Área consolidada de dados/resultados dos professores."}),
                Map.entry("aaconnect-administrative", new String[]{"AACONECT — Administrativos", "ACONECT_ADMINISTRATIVO", "Área consolidada de dados/resultados dos administrativos."}),
                Map.entry("map-1", new String[]{"Mapa 1º Trimestre", "MAPA 1º TRIMESTRE", "Resultado do primeiro período de avaliação."}),
                Map.entry("map-2", new String[]{"Mapa 2º Trimestre", "MAPA 2º TRIMESTRE", "Resultado do segundo período de avaliação."}),
                Map.entry("map-3", new String[]{"Mapa 3º Trimestre", "MAPA 3º TRIMESTRE", "Resultado do terceiro período de avaliação."}),
                Map.entry("map-final-professor", new String[]{"Mapa Final — Professor", "MAPA FINAL PROFESSOR", "Consolidação final dos resultados dos professores."}),
                Map.entry("map-final-administrative", new String[]{"Mapa Final — Administrativo", "MAPA FINAL ADMINISTRATIVO", "Consolidação final dos resultados administrativos."})
        );

        String[] data = modules.get(section);
        VBox page = pageContainer();

        HBox heading = sectionHeading(data == null ? "Módulo" : data[0],
                data == null ? "Área do sistema" : data[2]);

        VBox source = card();
        source.getChildren().addAll(
                label("Correspondência com o Excel", "card-title"),
                label("Folha de origem: " + (data == null ? "—" : data[1]), "muted"),
                label("Esta página já está integrada ao menu principal e representa a entrada digital da folha correspondente.", "muted")
        );

        HBox actions = new HBox(10);

        Button back = new Button("← Voltar ao início");
        back.setOnAction(e -> showSection("dashboard"));

        Button reports = new Button("Abrir relatórios");
        reports.setOnAction(e -> showSection("reports"));

        actions.getChildren().addAll(back, reports);

        page.getChildren().addAll(heading, source, actionPanelFor(section), actions);
        return page;
    }

    private VBox actionPanelFor(String section) {
        VBox box = card();
        box.getChildren().addAll(
                label("Próximas operações", "card-title"),
                label(operationText(section), "muted")
        );
        return box;
    }

    private String operationText(String section) {
        return switch (section) {
            case "administrative" -> "Registar colaborador, editar dados, definir área/função e acompanhar estado.";
            case "professor-evaluation" -> "Selecionar professor, preencher indicadores, calcular classificação e guardar o período.";
            case "administrative-evaluation" -> "Selecionar administrativo, preencher indicadores, calcular classificação e guardar o período.";
            case "aaconnect-professors" -> "Consolidar os resultados lançados para professores.";
            case "aaconnect-administrative" -> "Consolidar os resultados lançados para administrativos.";
            case "map-1" -> "Consultar, filtrar e fechar o mapa do 1º trimestre.";
            case "map-2" -> "Consultar, filtrar e fechar o mapa do 2º trimestre.";
            case "map-3" -> "Consultar, filtrar e fechar o mapa do 3º trimestre.";
            case "map-final-professor" -> "Calcular e apresentar a consolidação final dos professores.";
            case "map-final-administrative" -> "Calcular e apresentar a consolidação final dos administrativos.";
            default -> "Executar operações do módulo.";
        };
    }

    // -------------------------------------------------------------------------
    // STUDENTS
    // -------------------------------------------------------------------------

    private Node buildStudents() throws SQLException {
        VBox page = pageContainer();

        HBox heading = sectionHeading(
                "Alunos",
                "Cadastro, organização e acompanhamento dos estudantes."
        );

        TableView<StudentRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        addColumn(table, "Nº", 125, StudentRow::numberProperty);
        addColumn(table, "Nome completo", 270, StudentRow::nameProperty);
        addColumn(table, "Género", 110, StudentRow::genderProperty);
        addColumn(table, "Nascimento", 125, StudentRow::birthProperty);
        addColumn(table, "Turma", 140, StudentRow::classNameProperty);
        addColumn(table, "Contacto", 150, StudentRow::phoneProperty);

        TableColumn<StudentRow, Void> actions = actionColumn(table, row -> {
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> studentDialog(row));
            Button delete = miniDangerButton("Eliminar");
            delete.setOnAction(e -> confirmDelete("aluno", () -> database.deleteById("students", row.id.get())));
            return new HBox(6, edit, delete);
        });
        actions.setPrefWidth(155);
        table.getColumns().add(actions);

        ObservableList<StudentRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.students()) {
            StudentRow row = StudentRow.from(r);
            if (matches(q, row.number.get(), row.name.get(), row.className.get(), row.phone.get())) rows.add(row);
        }
        table.setItems(rows);

        page.getChildren().addAll(heading, tableFill(table));
        return page;
    }

    private void studentDialog(StudentRow existing) {
        Dialog<ButtonType> dialog = dialog(existing == null ? "Novo aluno" : "Editar aluno");
        GridPane grid = formGrid();

        TextField number = field("2026-0005");
        TextField name = field("Nome completo");
        ComboBox<String> gender = combo("Masculino", "Feminino", "Outro");
        DatePicker birth = new DatePicker();
        TextField phone = field("923 000 000");
        TextField guardian = field("Nome do encarregado");
        ComboBox<ClassOption> clazz = new ComboBox<>();
        clazz.setMaxWidth(Double.MAX_VALUE);

        try {
            for (Map<String,Object> r : database.classesData()) clazz.getItems().add(ClassOption.from(r));
        } catch (SQLException e) {
            showError("Erro ao carregar turmas", e);
            return;
        }

        if (existing != null) {
            number.setText(existing.number.get());
            name.setText(existing.name.get());
            gender.setValue(existing.gender.get().isBlank() ? null : existing.gender.get());
            if (!existing.birth.get().isBlank()) birth.setValue(LocalDate.parse(existing.birth.get()));
            phone.setText(existing.phone.get());
            guardian.setText(existing.guardian.get());
            clazz.getItems().stream().filter(c -> c.id() == existing.classId).findFirst().ifPresent(clazz::setValue);
        }

        grid.addRow(0, label("Número", "field-label"), number);
        grid.addRow(1, label("Nome", "field-label"), name);
        grid.addRow(2, label("Género", "field-label"), gender);
        grid.addRow(3, label("Nascimento", "field-label"), birth);
        grid.addRow(4, label("Turma", "field-label"), clazz);
        grid.addRow(5, label("Contacto", "field-label"), phone);
        grid.addRow(6, label("Encarregado", "field-label"), guardian);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            if (name.getText().isBlank() || number.getText().isBlank()) {
                showWarning("Preencha o número e o nome do aluno.");
                return null;
            }
            try {
                if (existing == null) {
                    database.insert("""
                        INSERT INTO students(student_number,name,gender,birth_date,class_id,phone,guardian)
                        VALUES(?,?,?,?,?,?,?)
                        """,
                        number.getText().trim(), name.getText().trim(), blankToNull(gender.getValue()),
                        birth.getValue() == null ? null : birth.getValue().toString(),
                        clazz.getValue() == null ? null : clazz.getValue().id(),
                        blankToNull(phone.getText()), blankToNull(guardian.getText()));
                } else {
                    database.update("""
                        UPDATE students SET student_number=?,name=?,gender=?,birth_date=?,class_id=?,phone=?,guardian=?
                        WHERE id=?
                        """,
                        number.getText().trim(), name.getText().trim(), blankToNull(gender.getValue()),
                        birth.getValue() == null ? null : birth.getValue().toString(),
                        clazz.getValue() == null ? null : clazz.getValue().id(),
                        blankToNull(phone.getText()), blankToNull(guardian.getText()), existing.id.get());
                }
                refreshCurrentSection();
                showToast("Aluno guardado com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar o aluno", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // PERFORMANCE / STAFF
    // -------------------------------------------------------------------------

    private Node buildStaff(String type) throws SQLException {
        VBox page = pageContainer();
        String title = "PROFESSOR".equals(type) ? "Professores" : "Administrativos";
        String description = "PROFESSOR".equals(type)
                ? "Cadastro dos profissionais docentes que participam da avaliação de desempenho."
                : "Cadastro dos colaboradores administrativos que participam da avaliação de desempenho.";

        HBox heading = sectionHeading(title, description);

        TableView<StaffRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Código", 115, StaffRow::codeProperty);
        addColumn(table, "Nome completo", 280, StaffRow::nameProperty);
        addColumn(table, "Cargo / função", 220, StaffRow::roleProperty);
        addColumn(table, "Departamento", 190, StaffRow::departmentProperty);
        addColumn(table, "Telefone", 155, StaffRow::phoneProperty);
        addColumn(table, "E-mail", 240, StaffRow::emailProperty);

        TableColumn<StaffRow, Void> actions = actionColumn(table, row -> {
            Button evaluate = miniButton("Avaliar");
            evaluate.setOnAction(e -> {
                selectedPerformanceStaffId = row.id.get();
                selectedPerformanceType = type;
                showSection("PROFESSOR".equals(type) ? "professor-evaluation" : "administrative-evaluation");
            });
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> staffDialog(type, row));
            Button del = miniDangerButton("Eliminar");
            del.setOnAction(e -> confirmDelete("registo", () -> database.deleteById("staff", row.id.get())));
            return new HBox(4, evaluate, edit, del);
        });
        actions.setPrefWidth(220);
        table.getColumns().add(actions);

        ObservableList<StaffRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.staff(type)) {
            StaffRow row = StaffRow.from(r);
            if (matches(q, row.code.get(), row.name.get(), row.role.get(), row.department.get(), row.phone.get(), row.email.get())) {
                rows.add(row);
            }
        }
        table.setItems(rows);

        HBox quick = new HBox(10);
        Button evaluate = new Button("✓  Abrir avaliação");
        evaluate.getStyleClass().add("accent-button");
        evaluate.setOnAction(e -> showSection("PROFESSOR".equals(type) ? "professor-evaluation" : "administrative-evaluation"));
        Button map = new Button("▤  Ver mapa trimestral");
        map.setOnAction(e -> showSection("map-1"));
        quick.getChildren().addAll(evaluate, map);

        page.getChildren().addAll(heading, quick, tableFill(table));
        return page;
    }

    private void staffDialog(String type, StaffRow existing) {
        String personLabel = "PROFESSOR".equals(type) ? "professor" : "administrativo";
        Dialog<ButtonType> dialog = dialog(existing == null ? "Novo " + personLabel : "Editar " + personLabel);
        GridPane grid = formGrid();

        TextField code = field("Ex.: " + ("PROFESSOR".equals(type) ? "PROF-003" : "ADM-003"));
        TextField name = field("Nome completo");
        TextField role = field("Cargo / função");
        TextField department = field("Departamento / área");
        TextField phone = field("923 000 000");
        TextField email = field("nome@cotan.edu");
        DatePicker admission = new DatePicker();

        if (existing != null) {
            code.setText(existing.code.get());
            name.setText(existing.name.get());
            role.setText(existing.role.get());
            department.setText(existing.department.get());
            phone.setText(existing.phone.get());
            email.setText(existing.email.get());
            if (!existing.admission.get().isBlank()) {
                try { admission.setValue(LocalDate.parse(existing.admission.get())); } catch (Exception ignored) {}
            }
        }

        grid.addRow(0, label("Código", "field-label"), code);
        grid.addRow(1, label("Nome", "field-label"), name);
        grid.addRow(2, label("Cargo / função", "field-label"), role);
        grid.addRow(3, label("Departamento", "field-label"), department);
        grid.addRow(4, label("Telefone", "field-label"), phone);
        grid.addRow(5, label("E-mail", "field-label"), email);
        grid.addRow(6, label("Admissão", "field-label"), admission);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            if (code.getText().isBlank() || name.getText().isBlank()) {
                showWarning("Informe o código e o nome.");
                return null;
            }
            try {
                String admissionDate = admission.getValue() == null ? null : admission.getValue().toString();
                if (existing == null) {
                    database.insert("""
                        INSERT INTO staff(code,name,staff_type,role,department,phone,email,admission_date)
                        VALUES(?,?,?,?,?,?,?,?)
                        """,
                        code.getText().trim(), name.getText().trim(), type,
                        blankToNull(role.getText()), blankToNull(department.getText()),
                        blankToNull(phone.getText()), blankToNull(email.getText()), admissionDate);
                } else {
                    database.update("""
                        UPDATE staff SET code=?,name=?,role=?,department=?,phone=?,email=?,admission_date=?
                        WHERE id=?
                        """,
                        code.getText().trim(), name.getText().trim(),
                        blankToNull(role.getText()), blankToNull(department.getText()),
                        blankToNull(phone.getText()), blankToNull(email.getText()), admissionDate,
                        existing.id.get());
                }
                refreshCurrentSection();
                showToast("Registo guardado com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar o registo", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    private Long selectedPerformanceStaffId;
    private String selectedPerformanceType = "PROFESSOR";

    private Node buildPerformanceEvaluation(String type) {
        VBox page = pageContainer();
        String title = "PROFESSOR".equals(type) ? "Avaliação de desempenho — Professores" :
                "Avaliação de desempenho — Administrativos";

        HBox heading = sectionHeading(title,
                "Modelo digital do preenchimento do Excel: indicadores, pontuação 0–20 e classificação automática.");

        ComboBox<StaffOption> staff = new ComboBox<>();
        staff.setPrefWidth(360);
        ComboBox<String> year = combo("2026/2027", "2027/2028");
        year.setValue("2026/2027");
        ComboBox<Integer> trimester = combo(1, 2, 3);
        trimester.setValue(1);

        try {
            for (Map<String,Object> r : database.staff(type)) staff.getItems().add(StaffOption.from(r));
        } catch (SQLException e) {
            showError("Erro ao carregar profissionais", e);
            return page;
        }

        if (selectedPerformanceStaffId != null) {
            staff.getItems().stream().filter(s -> s.id() == selectedPerformanceStaffId).findFirst().ifPresent(staff::setValue);
        } else if (!staff.getItems().isEmpty()) {
            staff.setValue(staff.getItems().get(0));
        }

        selectedPerformanceType = type;

        HBox selectors = new HBox(12,
                label("Profissional", "field-label"), staff,
                label("Ano", "field-label"), year,
                label("Trimestre", "field-label"), trimester);
        selectors.setAlignment(Pos.CENTER_LEFT);
        selectors.getStyleClass().add("toolbar-card");
        selectors.setPadding(new Insets(12));

        VBox resultCard = card();
        HBox result = new HBox(22);
        result.setAlignment(Pos.CENTER_LEFT);
        Label average = label("—", "result-number");
        Label classification = label("Aguardando lançamento", "result-badge");
        Label completeness = label("0/0 indicadores", "muted");
        result.getChildren().addAll(
                labelledMetric("MÉDIA", average),
                labelledMetric("CLASSIFICAÇÃO", classification),
                labelledMetric("PREENCHIMENTO", completeness)
        );
        resultCard.getChildren().addAll(label("Resultado do período", "card-title"), result);

        TableView<PerformanceInputRow> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Código", 110, PerformanceInputRow::codeProperty);
        addColumn(table, "Indicador", 280, PerformanceInputRow::nameProperty);
        addColumn(table, "Peso", 95, PerformanceInputRow::weightProperty);

        TableColumn<PerformanceInputRow,String> score = new TableColumn<>("Pontuação");
        score.setPrefWidth(160);
        score.setCellValueFactory(c -> c.getValue().scoreProperty());
        score.setCellFactory(TextFieldTableCell.forTableColumn());
        score.setOnEditCommit(e -> {
            String v = e.getNewValue() == null ? "" : e.getNewValue().trim().replace(",", ".");
            if (v.isBlank()) { e.getRowValue().score.set(""); recalcPerformance(table,result); return; }
            try {
                double n = Double.parseDouble(v);
                if (n < 0 || n > 20) throw new NumberFormatException();
                e.getRowValue().score.set(String.format(Locale.US, "%.2f", n));
                recalcPerformance(table,result);
            } catch (NumberFormatException ex) {
                showWarning("A pontuação deve estar entre 0 e 20.");
                table.refresh();
            }
        });

        TableColumn<PerformanceInputRow,String> obs = new TableColumn<>("Observação");
        obs.setPrefWidth(360);
        obs.setCellValueFactory(c -> c.getValue().observationProperty());
        table.getColumns().addAll(score, obs);

        Runnable load = () -> {
            try {
                StaffOption selectedStaff = staff.getValue();
                if (selectedStaff == null) {
                    table.getItems().clear();
                    return;
                }
                ObservableList<PerformanceInputRow> items = FXCollections.observableArrayList();
                for (Map<String,Object> r : database.performanceScores(selectedStaff.id(), year.getValue(), trimester.getValue())) {
                    items.add(PerformanceInputRow.from(r));
                }
                table.setItems(items);
                recalcPerformance(table, result);
            } catch (SQLException ex) {
                showError("Não foi possível carregar os indicadores", ex);
            }
        };

        staff.setOnAction(e -> { selectedPerformanceStaffId = staff.getValue() == null ? null : staff.getValue().id(); load.run(); });
        year.setOnAction(e -> load.run());
        trimester.setOnAction(e -> load.run());
        load.run();

        Button save = new Button("✓  Guardar avaliação");
        save.getStyleClass().add("accent-button");
        save.setOnAction(e -> {
            StaffOption selectedStaff = staff.getValue();
            if (selectedStaff == null) { showWarning("Selecione o profissional."); return; }
            try {
                for (PerformanceInputRow row : table.getItems()) {
                    if (row.score.get().isBlank()) continue;
                    database.upsertPerformanceScore(
                            selectedStaff.id(), row.id.get(), year.getValue(), trimester.getValue(),
                            Double.parseDouble(row.score.get().replace(",", ".")),
                            row.observation.get(), "Administrador");
                }
                showToast("Avaliação guardada com sucesso.");
                load.run();
            } catch (Exception ex) {
                showError("Não foi possível guardar a avaliação", ex);
            }
        });

        Button clear = new Button("Limpar");
        clear.setOnAction(e -> {
            for (PerformanceInputRow row : table.getItems()) row.score.set("");
            recalcPerformance(table, result);
        });

        HBox actions = new HBox(10, save, clear);
        page.getChildren().addAll(heading, selectors, resultCard, tableFill(table), actions);
        return page;
    }

    private HBox labelledMetric(String caption, Node value) {
        VBox box = new VBox(4, label(caption, "metric-caption"), value);
        HBox wrapper = new HBox(box);
        wrapper.setMinWidth(220);
        return wrapper;
    }

    private void recalcPerformance(TableView<PerformanceInputRow> table, HBox result) {
        double total=0, weights=0;
        int filled=0;
        for (PerformanceInputRow row : table.getItems()) {
            if (row.score.get().isBlank()) continue;
            try {
                double score=Double.parseDouble(row.score.get().replace(",", "."));
                double weight=Double.parseDouble(row.weight.get());
                total += score*weight;
                weights += weight;
                filled++;
            } catch(Exception ignored) {}
        }
        double avg = weights == 0 ? 0 : total/weights;
        if (!result.getChildren().isEmpty() && result.getChildren().get(0) instanceof VBox b && b.getChildren().size()>1) {
            ((Label)b.getChildren().get(1)).setText(weights == 0 ? "—" : String.format(Locale.US,"%.1f",avg));
        }
        if (result.getChildren().size()>1 && result.getChildren().get(1) instanceof VBox b && b.getChildren().size()>1) {
            ((Label)b.getChildren().get(1)).setText(weights == 0 ? "Aguardando lançamento" : performanceClassification(avg));
        }
        if (result.getChildren().size()>2 && result.getChildren().get(2) instanceof VBox b && b.getChildren().size()>1) {
            ((Label)b.getChildren().get(1)).setText(filled+"/"+table.getItems().size()+" indicadores");
        }
    }

    private Node buildPerformanceMapAll(int trimester) throws SQLException {
        VBox page = pageContainer();
        HBox heading = sectionHeading(
                "Mapa " + ordinalTrimester(trimester),
                "Consolidação trimestral dos professores e administrativos."
        );

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("Professores", buildPerformanceMapTable("PROFESSOR", trimester)));
        tabs.getTabs().add(new Tab("Administrativos", buildPerformanceMapTable("ADMINISTRATIVO", trimester)));

        page.getChildren().addAll(heading, tabs);
        return page;
    }

    private Node buildPerformanceMapTable(String type, int trimester) throws SQLException {
        VBox box = new VBox(12);
        box.getChildren().add(label(
                "Resultados do " + ordinalTrimester(trimester) + " • "
                        + ("PROFESSOR".equals(type) ? "Professores" : "Administrativos"),
                "card-title"));

        TableView<PerformanceMapRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table,"Código",120,PerformanceMapRow::codeProperty);
        addColumn(table,"Nome",270,PerformanceMapRow::nameProperty);
        addColumn(table,"Cargo / função",220,PerformanceMapRow::roleProperty);
        addColumn(table,"Departamento",190,PerformanceMapRow::departmentProperty);
        addColumn(table,"Indicadores",120,PerformanceMapRow::filledProperty);
        addColumn(table,"Média",120,PerformanceMapRow::averageProperty);
        addColumn(table,"Classificação",170,PerformanceMapRow::classificationProperty);

        ObservableList<PerformanceMapRow> rows=FXCollections.observableArrayList();
        for(Map<String,Object> r: database.performanceMap(type,"2026/2027",trimester)) {
            PerformanceMapRow row=PerformanceMapRow.from(r);
            if(matches(search(),row.code.get(),row.name.get(),row.role.get(),row.department.get(),row.classification.get())) rows.add(row);
        }
        table.setItems(rows);
        box.getChildren().add(table);
        VBox.setVgrow(table,Priority.ALWAYS);

        Button evaluate=new Button("✓  Abrir avaliação deste período");
        evaluate.setOnAction(e -> showSection("PROFESSOR".equals(type) ? "professor-evaluation" : "administrative-evaluation"));
        box.getChildren().add(evaluate);
        return box;
    }

    private Node buildPerformanceFinal(String type) throws SQLException {
        VBox page=pageContainer();
        String title="PROFESSOR".equals(type) ? "Mapa Final — Professor" : "Mapa Final — Administrativo";
        String desc="PROFESSOR".equals(type)
                ? "Consolidação final dos três trimestres dos professores."
                : "Consolidação final dos três trimestres dos administrativos.";

        HBox heading=sectionHeading(title,desc);

        HBox summary=new HBox(12);
        Map<String,Object> s=database.performanceSummary(type,"2026/2027");
        summary.getChildren().addAll(
                statCard("Profissionais",String.valueOf(s.get("total")),"Registados","●"),
                statCard("Avaliados",String.valueOf(s.get("evaluated")),"Com lançamentos","✓"),
                statCard("Média global",String.valueOf(s.get("average")),"Notas lançadas","★")
        );

        TableView<PerformanceFinalRow> table=new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table,"Código",115,PerformanceFinalRow::codeProperty);
        addColumn(table,"Nome",270,PerformanceFinalRow::nameProperty);
        addColumn(table,"1º Trim.",115,PerformanceFinalRow::t1Property);
        addColumn(table,"2º Trim.",115,PerformanceFinalRow::t2Property);
        addColumn(table,"3º Trim.",115,PerformanceFinalRow::t3Property);
        addColumn(table,"Média final",130,PerformanceFinalRow::finalProperty);
        addColumn(table,"Classificação",170,PerformanceFinalRow::classificationProperty);

        ObservableList<PerformanceFinalRow> rows=FXCollections.observableArrayList();
        for(Map<String,Object> r: database.performanceFinalMap(type,"2026/2027")) {
            PerformanceFinalRow row=PerformanceFinalRow.from(r);
            if(matches(search(),row.code.get(),row.name.get(),row.classification.get())) rows.add(row);
        }
        table.setItems(rows);

        Button export=new Button("⇩  Exportar mapa final");
        export.getStyleClass().add("accent-button");
        export.setOnAction(e -> exportPerformanceMap(type));

        page.getChildren().addAll(heading,summary,tableFill(table),export);
        return page;
    }

    private Node buildAaconnect(String type) throws SQLException {
        VBox page=pageContainer();
        String title="PROFESSOR".equals(type) ? "AACONECT — Professores" : "AACONECT — Administrativos";

        HBox heading=sectionHeading(title,
                "Painel de consolidação para acompanhar preenchimento, média e situação de cada profissional.");

        Map<String,Object> s=database.performanceSummary(type,"2026/2027");
        GridPane cards=new GridPane();
        cards.setHgap(14);
        cards.add(statCard("Profissionais",String.valueOf(s.get("total")),"Base ativa","●"),0,0);
        cards.add(statCard("Avaliados",String.valueOf(s.get("evaluated")),"Ano 2026/2027","✓"),1,0);
        cards.add(statCard("Média",String.valueOf(s.get("average")),"Notas lançadas","★"),2,0);

        VBox consolidation=card();
        consolidation.getChildren().addAll(
                label("Consolidação", "card-title"),
                label("Os dados são calculados a partir dos lançamentos trimestrais e podem ser auditados pelos mapas.", "muted")
        );

        Button evaluation=new Button("Abrir lançamento de avaliação");
        evaluation.getStyleClass().add("accent-button");
        evaluation.setOnAction(e -> showSection("PROFESSOR".equals(type) ? "professor-evaluation" : "administrative-evaluation"));

        Button finalMap=new Button("Abrir mapa final");
        finalMap.setOnAction(e -> showSection("PROFESSOR".equals(type) ? "map-final-professor" : "map-final-administrative"));

        HBox actions=new HBox(10,evaluation,finalMap);
        page.getChildren().addAll(heading,cards,consolidation,actions);
        return page;
    }

    private String ordinalTrimester(int t) {
        return switch (t) {
            case 1 -> "1º Trimestre";
            case 2 -> "2º Trimestre";
            case 3 -> "3º Trimestre";
            default -> "Trimestre";
        };
    }

    private String performanceClassification(double score) {
        if (score < 10) return "Mau";
        if (score < 14) return "Suficiente";
        if (score < 18) return "Bom";
        return "Muito bom";
    }

    private void exportPerformanceMap(String type) {
        try {
            FileChooser chooser=new FileChooser();
            chooser.setTitle("Guardar mapa final");
            chooser.setInitialFileName(("PROFESSOR".equals(type) ? "mapa-final-professores" : "mapa-final-administrativos")+".csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV","*.csv"));
            File file=chooser.showSaveDialog(stage);
            if(file==null)return;

            StringBuilder csv=new StringBuilder("Código;Nome;1º Trimestre;2º Trimestre;3º Trimestre;Média Final;Classificação\n");
            for(Map<String,Object> r:database.performanceFinalMap(type,"2026/2027")){
                double finalAvg;
                try{finalAvg=Double.parseDouble(safe(r.get("final_average")));}catch(Exception e){finalAvg=0;}
                csv.append(csv(r.get("code"))).append(';')
                   .append(csv(r.get("name"))).append(';')
                   .append(csv(r.get("t1_count"))).append(';')
                   .append(csv(r.get("t2_count"))).append(';')
                   .append(csv(r.get("t3_count"))).append(';')
                   .append(csv(r.get("final_average"))).append(';')
                   .append(csv(performanceClassification(finalAvg))).append('\n');
            }
            Files.writeString(file.toPath(),"\uFEFF"+csv,StandardCharsets.UTF_8);
            showToast("Mapa final exportado com sucesso.");
        }catch(Exception ex){showError("Não foi possível exportar o mapa",ex);}
    }

    private static String safe(Object o){return o==null?"":String.valueOf(o);}

    private static final class StaffRow {
        final SimpleLongProperty id;
        final SimpleStringProperty code,name,role,department,phone,email,admission;
        StaffRow(long id,String code,String name,String role,String department,String phone,String email,String admission){
            this.id=new SimpleLongProperty(id);this.code=new SimpleStringProperty(code);this.name=new SimpleStringProperty(name);
            this.role=new SimpleStringProperty(role);this.department=new SimpleStringProperty(department);
            this.phone=new SimpleStringProperty(phone);this.email=new SimpleStringProperty(email);this.admission=new SimpleStringProperty(admission);
        }
        static StaffRow from(Map<String,Object> r){return new StaffRow(n(r.get("id")),s(r.get("code")),s(r.get("name")),s(r.get("role")),s(r.get("department")),s(r.get("phone")),s(r.get("email")),s(r.get("admission_date")));}
        SimpleStringProperty codeProperty(){return code;} SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty roleProperty(){return role;} SimpleStringProperty departmentProperty(){return department;}
        SimpleStringProperty phoneProperty(){return phone;} SimpleStringProperty emailProperty(){return email;}
    }

    private record StaffOption(long id,String name,String code) {
        static StaffOption from(Map<String,Object> r){return new StaffOption(n(r.get("id")),s(r.get("name")),s(r.get("code")));}
        @Override public String toString(){return code+" • "+name;}
    }

    private static final class PerformanceInputRow {
        final SimpleLongProperty id;
        final SimpleStringProperty code,name,weight,score,observation;
        PerformanceInputRow(long id,String code,String name,String weight,String score,String observation){
            this.id=new SimpleLongProperty(id);this.code=new SimpleStringProperty(code);this.name=new SimpleStringProperty(name);
            this.weight=new SimpleStringProperty(weight);this.score=new SimpleStringProperty(score);this.observation=new SimpleStringProperty(observation);
        }
        static PerformanceInputRow from(Map<String,Object> r){return new PerformanceInputRow(n(r.get("indicator_id")),s(r.get("code")),s(r.get("name")),s(r.get("weight")),s(r.get("score")),s(r.get("observation")));}
        SimpleStringProperty codeProperty(){return code;} SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty weightProperty(){return weight;} SimpleStringProperty scoreProperty(){return score;}
        SimpleStringProperty observationProperty(){return observation;}
    }

    private static final class PerformanceMapRow {
        final SimpleLongProperty id;
        final SimpleStringProperty code,name,role,department,filled,average,classification;
        PerformanceMapRow(long id,String code,String name,String role,String department,String filled,String average){
            this.id=new SimpleLongProperty(id);this.code=new SimpleStringProperty(code);this.name=new SimpleStringProperty(name);
            this.role=new SimpleStringProperty(role);this.department=new SimpleStringProperty(department);this.filled=new SimpleStringProperty(filled);
            this.average=new SimpleStringProperty(average);this.classification=new SimpleStringProperty(classifyValue(average));
        }
        static PerformanceMapRow from(Map<String,Object> r){return new PerformanceMapRow(n(r.get("id")),s(r.get("code")),s(r.get("name")),s(r.get("role")),s(r.get("department")),s(r.get("indicators_filled")),s(r.get("average_score")));}
        SimpleStringProperty codeProperty(){return code;} SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty roleProperty(){return role;} SimpleStringProperty departmentProperty(){return department;}
        SimpleStringProperty filledProperty(){return filled;} SimpleStringProperty averageProperty(){return average;}
        SimpleStringProperty classificationProperty(){return classification;}
        private static String classifyValue(String v){try{double d=Double.parseDouble(v);if(d<10)return "Mau";if(d<14)return "Suficiente";if(d<18)return "Bom";if(d>0)return "Muito bom";return "Sem lançamentos";}catch(Exception e){return "Sem lançamentos";}}
    }

    private static final class PerformanceFinalRow {
        final SimpleLongProperty id;
        final SimpleStringProperty code,name,t1,t2,t3,finalAverage,classification;
        PerformanceFinalRow(long id,String code,String name,String t1,String t2,String t3,String finalAverage){
            this.id=new SimpleLongProperty(id);this.code=new SimpleStringProperty(code);this.name=new SimpleStringProperty(name);
            this.t1=new SimpleStringProperty(t1);this.t2=new SimpleStringProperty(t2);this.t3=new SimpleStringProperty(t3);
            this.finalAverage=new SimpleStringProperty(finalAverage);this.classification=new SimpleStringProperty(classifyValue(finalAverage));
        }
        static PerformanceFinalRow from(Map<String,Object> r){return new PerformanceFinalRow(n(r.get("id")),s(r.get("code")),s(r.get("name")),s(r.get("t1_count")),s(r.get("t2_count")),s(r.get("t3_count")),s(r.get("final_average")));}
        SimpleStringProperty codeProperty(){return code;} SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty t1Property(){return t1;} SimpleStringProperty t2Property(){return t2;}
        SimpleStringProperty t3Property(){return t3;} SimpleStringProperty finalProperty(){return finalAverage;}
        SimpleStringProperty classificationProperty(){return classification;}
        private static String classifyValue(String v){try{double d=Double.parseDouble(v);if(d<10)return "Mau";if(d<14)return "Suficiente";if(d<18)return "Bom";if(d>0)return "Muito bom";return "Sem avaliação";}catch(Exception e){return "Sem avaliação";}}
    }

    // -------------------------------------------------------------------------
    // TEACHERS
    // -------------------------------------------------------------------------

    private Node buildTeachers() throws SQLException {
        VBox page = pageContainer();
        HBox heading = sectionHeading(
                "Professores",
                "Gestão dos docentes e respetivas áreas de especialização."
        );

        TableView<TeacherRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Nome", 270, TeacherRow::nameProperty);
        addColumn(table, "Especialidade", 220, TeacherRow::specialtyProperty);
        addColumn(table, "Telefone", 160, TeacherRow::phoneProperty);
        addColumn(table, "E-mail", 250, TeacherRow::emailProperty);

        TableColumn<TeacherRow, Void> actions = actionColumn(table, row -> {
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> teacherDialog(row));
            Button del = miniDangerButton("Eliminar");
            del.setOnAction(e -> confirmDelete("professor", () -> database.deleteById("teachers", row.id.get())));
            return new HBox(6, edit, del);
        });
        actions.setPrefWidth(155);
        table.getColumns().add(actions);

        ObservableList<TeacherRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.teachers()) {
            TeacherRow row = TeacherRow.from(r);
            if (matches(q, row.name.get(), row.specialty.get(), row.phone.get(), row.email.get())) rows.add(row);
        }
        table.setItems(rows);

        page.getChildren().addAll(heading, tableFill(table));
        return page;
    }

    private void teacherDialog(TeacherRow existing) {
        Dialog<ButtonType> dialog = dialog(existing == null ? "Novo professor" : "Editar professor");
        GridPane grid = formGrid();

        TextField name = field("Nome completo");
        TextField specialty = field("Área / disciplina");
        TextField phone = field("Telefone");
        TextField email = field("E-mail");

        if (existing != null) {
            name.setText(existing.name.get());
            specialty.setText(existing.specialty.get());
            phone.setText(existing.phone.get());
            email.setText(existing.email.get());
        }

        grid.addRow(0, label("Nome", "field-label"), name);
        grid.addRow(1, label("Especialidade", "field-label"), specialty);
        grid.addRow(2, label("Telefone", "field-label"), phone);
        grid.addRow(3, label("E-mail", "field-label"), email);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            if (name.getText().isBlank()) {
                showWarning("Informe o nome do professor.");
                return null;
            }
            try {
                if (existing == null) {
                    database.insert("INSERT INTO teachers(name,specialty,phone,email) VALUES(?,?,?,?)",
                            name.getText().trim(), blankToNull(specialty.getText()),
                            blankToNull(phone.getText()), blankToNull(email.getText()));
                } else {
                    database.update("UPDATE teachers SET name=?,specialty=?,phone=?,email=? WHERE id=?",
                            name.getText().trim(), blankToNull(specialty.getText()),
                            blankToNull(phone.getText()), blankToNull(email.getText()), existing.id.get());
                }
                refreshCurrentSection();
                showToast("Professor guardado com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar o professor", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // CLASSES
    // -------------------------------------------------------------------------

    private Node buildClasses() throws SQLException {
        VBox page = pageContainer();
        HBox heading = sectionHeading(
                "Turmas",
                "Organize anos letivos, turnos, salas e coordenação."
        );

        TableView<ClassRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Turma", 170, ClassRow::nameProperty);
        addColumn(table, "Ano lectivo", 150, ClassRow::yearProperty);
        addColumn(table, "Turno", 130, ClassRow::shiftProperty);
        addColumn(table, "Sala", 130, ClassRow::roomProperty);
        addColumn(table, "Coordenador", 260, ClassRow::coordinatorProperty);

        TableColumn<ClassRow, Void> actions = actionColumn(table, row -> {
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> classDialog(row));
            Button del = miniDangerButton("Eliminar");
            del.setOnAction(e -> confirmDelete("turma", () -> database.deleteById("classes", row.id.get())));
            return new HBox(6, edit, del);
        });
        actions.setPrefWidth(155);
        table.getColumns().add(actions);

        ObservableList<ClassRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.classesData()) {
            ClassRow row = ClassRow.from(r);
            if (matches(q, row.name.get(), row.year.get(), row.shift.get(), row.room.get(), row.coordinator.get())) rows.add(row);
        }
        table.setItems(rows);

        page.getChildren().addAll(heading, tableFill(table));
        return page;
    }

    private void classDialog(ClassRow existing) {
        Dialog<ButtonType> dialog = dialog(existing == null ? "Nova turma" : "Editar turma");
        GridPane grid = formGrid();

        TextField name = field("Ex.: 10ª A");
        TextField year = field("2026/2027");
        ComboBox<String> shift = combo("Manhã", "Tarde", "Noite");
        TextField room = field("Sala 01");
        TextField coordinator = field("Coordenação pedagógica");

        if (existing != null) {
            name.setText(existing.name.get());
            year.setText(existing.year.get());
            shift.setValue(existing.shift.get());
            room.setText(existing.room.get());
            coordinator.setText(existing.coordinator.get());
        } else {
            shift.setValue("Manhã");
        }

        grid.addRow(0, label("Turma", "field-label"), name);
        grid.addRow(1, label("Ano lectivo", "field-label"), year);
        grid.addRow(2, label("Turno", "field-label"), shift);
        grid.addRow(3, label("Sala", "field-label"), room);
        grid.addRow(4, label("Coordenador", "field-label"), coordinator);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            if (name.getText().isBlank() || year.getText().isBlank()) {
                showWarning("Informe a turma e o ano lectivo.");
                return null;
            }
            try {
                if (existing == null) {
                    database.insert("""
                        INSERT INTO classes(name,academic_year,shift,room,coordinator)
                        VALUES(?,?,?,?,?)
                        """,
                        name.getText().trim(), year.getText().trim(),
                        shift.getValue() == null ? "Manhã" : shift.getValue(),
                        blankToNull(room.getText()), blankToNull(coordinator.getText()));
                } else {
                    database.update("""
                        UPDATE classes SET name=?,academic_year=?,shift=?,room=?,coordinator=? WHERE id=?
                        """,
                        name.getText().trim(), year.getText().trim(),
                        shift.getValue() == null ? "Manhã" : shift.getValue(),
                        blankToNull(room.getText()), blankToNull(coordinator.getText()), existing.id.get());
                }
                refreshCurrentSection();
                showToast("Turma guardada com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar a turma", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // SUBJECTS
    // -------------------------------------------------------------------------

    private Node buildSubjects() throws SQLException {
        VBox page = pageContainer();
        HBox heading = sectionHeading(
                "Disciplinas",
                "Defina código, carga horária e peso usado no cálculo ponderado."
        );

        TableView<SubjectRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Disciplina", 300, SubjectRow::nameProperty);
        addColumn(table, "Código", 120, SubjectRow::codeProperty);
        addColumn(table, "Carga horária", 150, SubjectRow::workloadProperty);
        addColumn(table, "Peso", 120, SubjectRow::weightProperty);

        TableColumn<SubjectRow, Void> actions = actionColumn(table, row -> {
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> subjectDialog(row));
            Button del = miniDangerButton("Eliminar");
            del.setOnAction(e -> confirmDelete("disciplina", () -> database.deleteById("subjects", row.id.get())));
            return new HBox(6, edit, del);
        });
        actions.setPrefWidth(155);
        table.getColumns().add(actions);

        ObservableList<SubjectRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.subjects()) {
            SubjectRow row = SubjectRow.from(r);
            if (matches(q, row.name.get(), row.code.get(), row.workload.get(), row.weight.get())) rows.add(row);
        }
        table.setItems(rows);

        page.getChildren().addAll(heading, tableFill(table));
        return page;
    }

    private void subjectDialog(SubjectRow existing) {
        Dialog<ButtonType> dialog = dialog(existing == null ? "Nova disciplina" : "Editar disciplina");
        GridPane grid = formGrid();

        TextField name = field("Ex.: Matemática");
        TextField code = field("MAT");
        Spinner<Integer> workload = new Spinner<>(1, 20, 2);
        Spinner<Double> weight = new Spinner<>(0.1, 10.0, 1.0, 0.1);
        workload.setMaxWidth(Double.MAX_VALUE);
        weight.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) {
            name.setText(existing.name.get());
            code.setText(existing.code.get());
            workload.getValueFactory().setValue(Integer.parseInt(existing.workload.get()));
            try { weight.getValueFactory().setValue(Double.parseDouble(existing.weight.get())); } catch (Exception ignored) {}
        }

        grid.addRow(0, label("Disciplina", "field-label"), name);
        grid.addRow(1, label("Código", "field-label"), code);
        grid.addRow(2, label("Carga horária", "field-label"), workload);
        grid.addRow(3, label("Peso", "field-label"), weight);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            if (name.getText().isBlank() || code.getText().isBlank()) {
                showWarning("Informe o nome e o código da disciplina.");
                return null;
            }
            try {
                if (existing == null) {
                    database.insert("INSERT INTO subjects(name,code,workload,weight) VALUES(?,?,?,?)",
                            name.getText().trim(), code.getText().trim().toUpperCase(Locale.ROOT),
                            workload.getValue(), weight.getValue());
                } else {
                    database.update("UPDATE subjects SET name=?,code=?,workload=?,weight=? WHERE id=?",
                            name.getText().trim(), code.getText().trim().toUpperCase(Locale.ROOT),
                            workload.getValue(), weight.getValue(), existing.id.get());
                }
                refreshCurrentSection();
                showToast("Disciplina guardada com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar a disciplina", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // ASSESSMENTS
    // -------------------------------------------------------------------------

    private Node buildAssessments() throws SQLException {
        VBox page = pageContainer();
        HBox heading = sectionHeading(
                "Avaliações",
                "Crie provas, trabalhos e projetos ligados a uma turma e disciplina."
        );

        TableView<AssessmentRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Avaliação", 240, AssessmentRow::titleProperty);
        addColumn(table, "Tipo", 120, AssessmentRow::typeProperty);
        addColumn(table, "Período", 145, AssessmentRow::termProperty);
        addColumn(table, "Data", 125, AssessmentRow::dateProperty);
        addColumn(table, "Turma", 135, AssessmentRow::classNameProperty);
        addColumn(table, "Disciplina", 190, AssessmentRow::subjectNameProperty);
        addColumn(table, "Máx.", 85, AssessmentRow::maxScoreProperty);

        TableColumn<AssessmentRow, Void> actions = actionColumn(table, row -> {
            Button launch = miniButton("Notas");
            launch.setOnAction(e -> openGradesFor(row));
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> assessmentDialog(row));
            Button del = miniDangerButton("Eliminar");
            del.setOnAction(e -> confirmDelete("avaliação", () -> database.deleteById("assessments", row.id.get())));
            return new HBox(4, launch, edit, del);
        });
        actions.setPrefWidth(220);
        table.getColumns().add(actions);

        ObservableList<AssessmentRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.assessments()) {
            AssessmentRow row = AssessmentRow.from(r);
            if (matches(q, row.title.get(), row.type.get(), row.term.get(), row.date.get(), row.className.get(), row.subjectName.get())) rows.add(row);
        }
        table.setItems(rows);

        page.getChildren().addAll(heading, tableFill(table));
        return page;
    }

    private void assessmentDialog(AssessmentRow existing) {
        Dialog<ButtonType> dialog = dialog(existing == null ? "Nova avaliação" : "Editar avaliação");
        GridPane grid = formGrid();

        TextField title = field("Ex.: 1ª Prova");
        ComboBox<String> type = combo("Prova", "Teste", "Trabalho", "Projeto", "Exame", "Participação");
        ComboBox<String> term = combo("1º Trimestre", "2º Trimestre", "3º Trimestre", "Exame");
        DatePicker date = new DatePicker(LocalDate.now());
        Spinner<Double> maxScore = new Spinner<>(1.0, 100.0, 20.0, 1.0);
        Spinner<Double> weight = new Spinner<>(0.1, 10.0, 1.0, 0.1);
        ComboBox<ClassOption> clazz = new ComboBox<>();
        ComboBox<SubjectOption> subject = new ComboBox<>();
        ComboBox<TeacherOption> teacher = new ComboBox<>();
        TextField notes = field("Observações / critérios");

        try {
            for (Map<String,Object> r : database.classesData()) clazz.getItems().add(ClassOption.from(r));
            for (Map<String,Object> r : database.subjects()) subject.getItems().add(SubjectOption.from(r));
            for (Map<String,Object> r : database.teachers()) teacher.getItems().add(TeacherOption.from(r));
        } catch (SQLException e) {
            showError("Erro ao carregar referências da avaliação", e);
            return;
        }

        type.setValue("Prova");
        term.setValue("1º Trimestre");

        if (existing != null) {
            title.setText(existing.title.get());
            type.setValue(existing.type.get());
            term.setValue(existing.term.get());
            if (!existing.date.get().isBlank()) date.setValue(LocalDate.parse(existing.date.get()));
            try { maxScore.getValueFactory().setValue(Double.parseDouble(existing.maxScore.get())); } catch (Exception ignored) {}
            try { weight.getValueFactory().setValue(Double.parseDouble(existing.weight.get())); } catch (Exception ignored) {}
            clazz.getItems().stream().filter(c -> c.id() == existing.classId).findFirst().ifPresent(clazz::setValue);
            subject.getItems().stream().filter(c -> c.id() == existing.subjectId).findFirst().ifPresent(subject::setValue);
            teacher.getItems().stream().filter(c -> c.id() == existing.teacherId).findFirst().ifPresent(teacher::setValue);
        }

        grid.addRow(0, label("Título", "field-label"), title);
        grid.addRow(1, label("Tipo", "field-label"), type);
        grid.addRow(2, label("Período", "field-label"), term);
        grid.addRow(3, label("Data", "field-label"), date);
        grid.addRow(4, label("Nota máxima", "field-label"), maxScore);
        grid.addRow(5, label("Peso", "field-label"), weight);
        grid.addRow(6, label("Turma", "field-label"), clazz);
        grid.addRow(7, label("Disciplina", "field-label"), subject);
        grid.addRow(8, label("Professor", "field-label"), teacher);
        grid.addRow(9, label("Observações", "field-label"), notes);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            if (title.getText().isBlank() || subject.getValue() == null) {
                showWarning("Informe pelo menos o título e a disciplina.");
                return null;
            }
            try {
                if (existing == null) {
                    database.insert("""
                        INSERT INTO assessments(title,type,term,assessment_date,max_score,weight,class_id,subject_id,teacher_id,notes)
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        """,
                        title.getText().trim(), type.getValue(), term.getValue(),
                        date.getValue() == null ? null : date.getValue().toString(),
                        maxScore.getValue(), weight.getValue(),
                        clazz.getValue() == null ? null : clazz.getValue().id(),
                        subject.getValue().id(),
                        teacher.getValue() == null ? null : teacher.getValue().id(),
                        blankToNull(notes.getText()));
                } else {
                    database.update("""
                        UPDATE assessments SET title=?,type=?,term=?,assessment_date=?,max_score=?,weight=?,
                        class_id=?,subject_id=?,teacher_id=?,notes=? WHERE id=?
                        """,
                        title.getText().trim(), type.getValue(), term.getValue(),
                        date.getValue() == null ? null : date.getValue().toString(),
                        maxScore.getValue(), weight.getValue(),
                        clazz.getValue() == null ? null : clazz.getValue().id(),
                        subject.getValue().id(),
                        teacher.getValue() == null ? null : teacher.getValue().id(),
                        blankToNull(notes.getText()), existing.id.get());
                }
                refreshCurrentSection();
                showToast("Avaliação guardada com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar a avaliação", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    private void openGradesFor(AssessmentRow row) {
        selectedAssessmentId = row.id.get();
        showSection("grades");
        try {
            content.getChildren().setAll(buildGradesWithAssessment(row.id.get()));
        } catch (Exception ex) {
            showError("Não foi possível abrir o lançamento de notas", ex);
        }
    }

    // -------------------------------------------------------------------------
    // GRADES
    // -------------------------------------------------------------------------

    private Long selectedAssessmentId;

    private Node buildGrades() throws SQLException {
        return buildGradesWithAssessment(selectedAssessmentId);
    }

    private Node buildGradesWithAssessment(Long preferred) throws SQLException {
        selectedAssessmentId = preferred;

        VBox page = pageContainer();

        HBox heading = sectionHeading(
                "Lançamento de notas",
                "Selecione uma avaliação, introduza as notas e grave todas de uma vez."
        );

        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("toolbar-card");
        controls.setPadding(new Insets(12));

        ComboBox<AssessmentOption> assessment = new ComboBox<>();
        assessment.setPrefWidth(440);
        assessment.setPromptText("Selecione a avaliação");

        for (Map<String,Object> r : database.assessments()) assessment.getItems().add(AssessmentOption.from(r));

        AssessmentOption selected = null;
        if (preferred != null) {
            selected = assessment.getItems().stream().filter(a -> a.id() == preferred).findFirst().orElse(null);
        }
        if (selected == null && !assessment.getItems().isEmpty()) selected = assessment.getItems().get(0);
        assessment.setValue(selected);

        Button load = new Button("Carregar alunos");
        load.getStyleClass().add("accent-button");

        Label hint = new Label("Escala 0–20 por padrão; a nota máxima da avaliação é respeitada.");
        hint.getStyleClass().add("muted");

        controls.getChildren().addAll(new Label("Avaliação"), assessment, load, hint);

        TableView<GradeRow> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Nº", 120, GradeRow::numberProperty);
        addColumn(table, "Aluno", 310, GradeRow::nameProperty);

        TableColumn<GradeRow, String> score = new TableColumn<>("Nota");
        score.setPrefWidth(150);
        score.setCellValueFactory(c -> c.getValue().scoreProperty());
        score.setCellFactory(TextFieldTableCell.forTableColumn());
        score.setOnEditCommit(e -> {
            String value = e.getNewValue() == null ? "" : e.getNewValue().trim().replace(",", ".");
            if (value.isBlank()) {
                e.getRowValue().scoreProperty().set("");
                return;
            }
            try {
                double n = Double.parseDouble(value);
                if (n < 0) throw new NumberFormatException();
                e.getRowValue().scoreProperty().set(String.format(Locale.US, "%.2f", n));
            } catch (NumberFormatException ex) {
                showWarning("Digite uma nota numérica válida.");
                table.refresh();
            }
        });

        TableColumn<GradeRow, String> observation = new TableColumn<>("Observação");
        observation.setPrefWidth(340);
        observation.setCellValueFactory(c -> c.getValue().observationProperty());
        table.getColumns().addAll(score, observation);

        Label total = label("0 aluno(s) carregados", "muted");
        Button saveAll = new Button("✓  Guardar notas");
        saveAll.getStyleClass().add("accent-button");

        Button clear = new Button("Limpar lançamentos");
        clear.setOnAction(e -> {
            for (GradeRow r : table.getItems()) r.scoreProperty().set("");
        });

        HBox footer = new HBox(10, total, new Region(), clear, saveAll);
        HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("toolbar-card");
        footer.setPadding(new Insets(12));

        Runnable loadRows = () -> {
            try {
                AssessmentOption a = assessment.getValue();
                if (a == null) {
                    table.getItems().clear();
                    total.setText("Selecione uma avaliação");
                    return;
                }
                ObservableList<GradeRow> items = FXCollections.observableArrayList();
                for (Map<String,Object> r : database.studentsForAssessment(a.id())) {
                    items.add(GradeRow.from(r));
                }
                table.setItems(items);
                total.setText(items.size() + " aluno(s) • clique duas vezes na coluna Nota para editar");
            } catch (SQLException ex) {
                showError("Não foi possível carregar as notas", ex);
            }
        };

        load.setOnAction(e -> loadRows.run());
        assessment.setOnAction(e -> {
            if (assessment.getValue() != null) {
                selectedAssessmentId = assessment.getValue().id();
            }
        });

        saveAll.setOnAction(e -> {
            AssessmentOption a = assessment.getValue();
            if (a == null) {
                showWarning("Selecione uma avaliação.");
                return;
            }
            try {
                double max = a.maxScore();
                int saved = 0;
                for (GradeRow r : table.getItems()) {
                    if (r.score.get().isBlank()) continue;
                    double scoreValue = Double.parseDouble(r.score.get().replace(",", "."));
                    if (scoreValue < 0 || scoreValue > max) {
                        showWarning("A nota de " + r.name.get() + " está fora do limite 0–" + max + ".");
                        return;
                    }
                    database.upsertGrade(r.id.get(), a.id(), scoreValue, r.observation.get());
                    saved++;
                }
                showToast(saved + " nota(s) guardada(s) com sucesso.");
                refreshCurrentSection();
            } catch (Exception ex) {
                showError("Não foi possível guardar as notas", ex);
            }
        });

        page.getChildren().addAll(heading, controls, tableFill(table), footer);

        loadRows.run();
        return page;
    }

    // -------------------------------------------------------------------------
    // REPORTS
    // -------------------------------------------------------------------------

    private Node buildReports() throws SQLException {
        VBox page = pageContainer();

        HBox heading = sectionHeading(
                "Relatórios de desempenho",
                "Médias ponderadas, estado académico e exportação para CSV."
        );

        HBox tools = new HBox(10);
        Button export = new Button("⇩  Exportar CSV");
        export.getStyleClass().add("accent-button");
        export.setOnAction(e -> exportPerformance());
        Button refresh = new Button("↻ Atualizar");
        refresh.setOnAction(e -> refreshCurrentSection());
        tools.getChildren().addAll(export, refresh);
        heading.getChildren().add(tools);

        TableView<PerformanceRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Aluno", 290, PerformanceRow::nameProperty);
        addColumn(table, "Nº", 130, PerformanceRow::numberProperty);
        addColumn(table, "Turma", 150, PerformanceRow::classNameProperty);
        addColumn(table, "Avaliações", 130, PerformanceRow::countProperty);
        addColumn(table, "Média ponderada", 160, PerformanceRow::averageProperty);
        addColumn(table, "Estado", 170, PerformanceRow::statusProperty);

        ObservableList<PerformanceRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.performance()) {
            PerformanceRow row = PerformanceRow.from(r);
            if (matches(q, row.name.get(), row.number.get(), row.className.get(), row.status.get())) rows.add(row);
        }
        table.setItems(rows);

        VBox legend = card();
        legend.getChildren().addAll(
                label("Critério", "card-title"),
                label("Aprovado: média ponderada ≥ 10. Abaixo da média: média < 10. Sem avaliações: ainda não existem notas lançadas.", "muted")
        );

        page.getChildren().addAll(heading, tableFill(table), legend);
        return page;
    }

    private void exportPerformance() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar relatório de desempenho");
            chooser.setInitialFileName("relatorio-desempenho.csv");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            File selected = chooser.showSaveDialog(stage);
            if (selected == null) return;

            StringBuilder csv = new StringBuilder();
            csv.append("Nº;Aluno;Turma;Avaliações;Média ponderada;Estado\n");
            for (Map<String,Object> r : database.performance()) {
                csv.append(csv(r.get("student_number")))
                        .append(';').append(csv(r.get("name")))
                        .append(';').append(csv(r.get("class_name")))
                        .append(';').append(csv(r.get("assessments_count")))
                        .append(';').append(csv(r.get("average_score")))
                        .append(';').append(csv(r.get("status")))
                        .append('\n');
            }
            Files.writeString(selected.toPath(), "\uFEFF" + csv, StandardCharsets.UTF_8);
            showToast("Relatório exportado: " + selected.getAbsolutePath());
        } catch (IOException | SQLException ex) {
            showError("Não foi possível exportar o relatório", ex);
        }
    }

    // -------------------------------------------------------------------------
    // SETTINGS
    // -------------------------------------------------------------------------

    private Node buildSettings() {
        VBox page = pageContainer();

        page.getChildren().add(sectionHeading(
                "Configurações",
                "Preferências visuais, segurança dos dados e manutenção da base."
        ));

        VBox appearance = card();
        appearance.getChildren().addAll(
                label("Aparência", "card-title"),
                label("Escolha o tema da aplicação.", "muted")
        );

        ToggleButton dark = new ToggleButton(darkMode ? "☾  Modo escuro" : "☀  Modo claro");
        dark.setSelected(darkMode);
        dark.setOnAction(e -> {
            darkMode = dark.isSelected();
            Application.setUserAgentStylesheet(darkMode ? DARK_THEME : LIGHT_THEME);
            dark.setText(darkMode ? "☾  Modo escuro" : "☀  Modo claro");
        });
        appearance.getChildren().add(dark);

        VBox databaseCard = card();
        databaseCard.getChildren().addAll(
                label("Banco de dados", "card-title"),
                label(database.getDatabasePath().toString(), "muted"),
                label("SQLite • persistência local • WAL • chaves estrangeiras ativas", "muted")
        );

        HBox dbActions = new HBox(10);
        Button backup = new Button("⇩  Criar backup");
        backup.getStyleClass().add("accent-button");
        backup.setOnAction(e -> createBackup());
        Button seedInfo = new Button("Dados de demonstração");
        seedInfo.setOnAction(e -> showInfo("O sistema cria automaticamente alguns registos de demonstração apenas quando a base está vazia."));
        dbActions.getChildren().addAll(backup, seedInfo);
        databaseCard.getChildren().add(dbActions);

        VBox rules = card();
        rules.getChildren().addAll(
                label("Regras académicas atuais", "card-title"),
                label("• Escala suportada por avaliação configurável\n• Cálculo de média ponderada por peso de avaliação\n• Referência de aprovação: 10 valores\n• Notas vinculadas a aluno + avaliação\n• Eliminação em cascata das notas quando um aluno/avaliação é removido", "muted")
        );

        page.getChildren().addAll(appearance, databaseCard, rules);
        return page;
    }

    private void createBackup() {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar backup da base SQLite");
            chooser.setInitialFileName("avaliacao-backup.db");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite DB", "*.db"));
            File target = chooser.showSaveDialog(stage);
            if (target == null) return;
            database.backup(target.toPath());
            dbStatus.setText("●  SQLite • backup criado");
            showToast("Backup criado com sucesso.");
        } catch (Exception ex) {
            showError("Não foi possível criar o backup", ex);
        }
    }

    // -------------------------------------------------------------------------
    // ABOUT
    // -------------------------------------------------------------------------

    private Node buildAbout() {
        VBox page = pageContainer();
        VBox hero = card();
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(35));
        hero.getChildren().addAll(
                label("COTAN", "hero-mark"),
                label("Sistema de Avaliação e Desempenho", "hero-title"),
                label("Uma base desktop moderna para gestão académica, lançamento de notas e análise de desempenho.", "hero-subtitle")
        );

        GridPane tech = new GridPane();
        tech.setHgap(18);
        tech.setVgap(12);
        tech.add(infoPill("Java", "21"), 0, 0);
        tech.add(infoPill("JavaFX", "21"), 1, 0);
        tech.add(infoPill("AtlantaFX", "2.1.0"), 2, 0);
        tech.add(infoPill("Banco", "SQLite"), 3, 0);
        tech.add(infoPill("Arquitetura", "Desktop + Spring Boot"), 0, 1);

        page.getChildren().addAll(
                sectionHeading("Sobre o sistema", "Informações técnicas da versão atual."),
                hero,
                tech
        );
        return page;
    }

    // -------------------------------------------------------------------------
    // UI HELPERS
    // -------------------------------------------------------------------------

    private VBox pageContainer() {
        VBox box = new VBox(18);
        box.setFillWidth(true);
        return box;
    }

    private HBox sectionHeading(String title, String subtitle) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox labels = new VBox(3);
        labels.getChildren().addAll(label(title, "section-title"), label(subtitle, "muted"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(labels, spacer);
        return row;
    }

    private VBox statCard(String title, String value, String note, String icon) {
        VBox box = card();
        box.setPrefHeight(128);

        HBox top = new HBox(10);
        top.setAlignment(Pos.CENTER_LEFT);
        Label i = label(icon, "stat-icon");
        Label t = label(title, "stat-label");
        top.getChildren().addAll(i, t);

        Label v = label(value, "stat-value");
        Label n = label(note, "muted");

        box.getChildren().addAll(top, v, n);
        return box;
    }

    private VBox actionCard(String title, String text, Runnable action) {
        VBox box = card();
        box.setPrefWidth(250);
        box.getStyleClass().add("click-card");
        box.setOnMouseClicked(e -> action.run());
        box.getChildren().addAll(label(title, "card-title"), label(text, "muted"));
        return box;
    }

    private VBox infoPill(String title, String value) {
        VBox box = new VBox(3, label(title, "field-label"), label(value, "card-title"));
        box.getStyleClass().add("info-pill");
        box.setPadding(new Insets(12, 18, 12, 18));
        return box;
    }

    private VBox card() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card");
        box.setPadding(new Insets(18));
        return box;
    }

    private Node tableFill(TableView<?> table) {
        VBox holder = card();
        VBox.setVgrow(table, Priority.ALWAYS);
        holder.getChildren().add(table);
        VBox.setVgrow(holder, Priority.ALWAYS);
        return holder;
    }

    private Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        return l;
    }

    private TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    @SafeVarargs
    private final <T> ComboBox<T> combo(T... items) {
        ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(items));
        c.setMaxWidth(Double.MAX_VALUE);
        return c;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(12);
        grid.setPadding(new Insets(8));
        ColumnConstraints left = new ColumnConstraints();
        left.setMinWidth(130);
        ColumnConstraints right = new ColumnConstraints();
        right.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(left, right);
        return grid;
    }

    private Dialog<ButtonType> dialog(String title) {
        Dialog<ButtonType> d = new Dialog<>();
        d.setTitle("COTAN • " + title);
        d.setHeaderText(title);
        d.getDialogPane().setPrefWidth(560);
        return d;
    }

    private <T> void addColumn(TableView<T> table, String title, double width,
                               java.util.function.Function<T, javafx.beans.value.ObservableValue<String>> value) {
        TableColumn<T,String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> value.apply(cell.getValue()));
        table.getColumns().add(c);
    }

    private Button miniButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("mini-button");
        return b;
    }

    private Button miniDangerButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add("danger-button");
        return b;
    }

    private String search() {
        return searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String query, String... values) {
        if (query.isBlank()) return true;
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .anyMatch(v -> v.contains(query));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        return """ + s.replace(""", """") + """;
    }

    private void confirmDelete(String what, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar eliminação");
        alert.setHeaderText("Eliminar " + what + "?");
        alert.setContentText("Esta ação pode afetar dados relacionados. Deseja continuar?");
        alert.getButtonTypes().setAll(ButtonType.CANCEL, ButtonType.OK);
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    action.run();
                    refreshCurrentSection();
                    showToast("Registo eliminado.");
                } catch (Exception ex) {
                    showError("Não foi possível eliminar o registo", ex);
                }
            }
        });
    }

    private void showToast(String text) {
        dbStatus.setText("●  " + text);
        dbStatus.getStyleClass().removeAll("status-error");
        if (!dbStatus.getStyleClass().contains("status-ok")) dbStatus.getStyleClass().add("status-ok");
    }

    private void showWarning(String message) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle("COTAN");
        a.setHeaderText("Atenção");
        a.setContentText(message);
        a.showAndWait();
    }

    private void showInfo(String message) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("COTAN");
        a.setHeaderText("Informação");
        a.setContentText(message);
        a.showAndWait();
    }

    private void showError(String message, Throwable ex) {
        if (dbStatus != null) {
            dbStatus.setText("●  " + message);
            dbStatus.getStyleClass().add("status-error");
        }
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("COTAN • Erro");
        a.setHeaderText(message);
        a.setContentText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
        a.showAndWait();
    }

    private void shutdown() {
        if (database != null) database.close();
        if (springContext != null) springContext.close();
        Platform.exit();
    }

    // -------------------------------------------------------------------------
    // ROW MODELS
    // -------------------------------------------------------------------------

    private static final class StudentRow {
        final SimpleLongProperty id;
        final SimpleStringProperty number, name, gender, birth, className, phone, guardian;
        final long classId;

        private StudentRow(long id, String number, String name, String gender, String birth,
                           String className, String phone, String guardian, long classId) {
            this.id = new SimpleLongProperty(id);
            this.number = new SimpleStringProperty(number);
            this.name = new SimpleStringProperty(name);
            this.gender = new SimpleStringProperty(gender);
            this.birth = new SimpleStringProperty(birth);
            this.className = new SimpleStringProperty(className);
            this.phone = new SimpleStringProperty(phone);
            this.guardian = new SimpleStringProperty(guardian);
            this.classId = classId;
        }
        static StudentRow from(Map<String,Object> r) {
            return new StudentRow(
                    n(r.get("id")), s(r.get("student_number")), s(r.get("name")), s(r.get("gender")),
                    s(r.get("birth_date")), s(r.get("class_name")), s(r.get("phone")), s(r.get("guardian")),
                    n(r.get("class_id"))
            );
        }
        SimpleStringProperty numberProperty(){return number;}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty genderProperty(){return gender;}
        SimpleStringProperty birthProperty(){return birth;}
        SimpleStringProperty classNameProperty(){return className;}
        SimpleStringProperty phoneProperty(){return phone;}
    }

    private static final class TeacherRow {
        final SimpleLongProperty id;
        final SimpleStringProperty name, specialty, phone, email;
        TeacherRow(long id,String name,String specialty,String phone,String email){
            this.id=new SimpleLongProperty(id); this.name=new SimpleStringProperty(name);
            this.specialty=new SimpleStringProperty(specialty); this.phone=new SimpleStringProperty(phone); this.email=new SimpleStringProperty(email);
        }
        static TeacherRow from(Map<String,Object> r){return new TeacherRow(n(r.get("id")),s(r.get("name")),s(r.get("specialty")),s(r.get("phone")),s(r.get("email")));}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty specialtyProperty(){return specialty;}
        SimpleStringProperty phoneProperty(){return phone;}
        SimpleStringProperty emailProperty(){return email;}
    }

    private static final class ClassRow {
        final SimpleLongProperty id;
        final SimpleStringProperty name, year, shift, room, coordinator;
        ClassRow(long id,String name,String year,String shift,String room,String coordinator){
            this.id=new SimpleLongProperty(id);this.name=new SimpleStringProperty(name);this.year=new SimpleStringProperty(year);
            this.shift=new SimpleStringProperty(shift);this.room=new SimpleStringProperty(room);this.coordinator=new SimpleStringProperty(coordinator);
        }
        static ClassRow from(Map<String,Object> r){return new ClassRow(n(r.get("id")),s(r.get("name")),s(r.get("academic_year")),s(r.get("shift")),s(r.get("room")),s(r.get("coordinator")));}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty yearProperty(){return year;}
        SimpleStringProperty shiftProperty(){return shift;}
        SimpleStringProperty roomProperty(){return room;}
        SimpleStringProperty coordinatorProperty(){return coordinator;}
    }

    private static final class SubjectRow {
        final SimpleLongProperty id;
        final SimpleStringProperty name, code, workload, weight;
        SubjectRow(long id,String name,String code,String workload,String weight){
            this.id=new SimpleLongProperty(id);this.name=new SimpleStringProperty(name);this.code=new SimpleStringProperty(code);
            this.workload=new SimpleStringProperty(workload);this.weight=new SimpleStringProperty(weight);
        }
        static SubjectRow from(Map<String,Object> r){return new SubjectRow(n(r.get("id")),s(r.get("name")),s(r.get("code")),s(r.get("workload")),s(r.get("weight")));}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty codeProperty(){return code;}
        SimpleStringProperty workloadProperty(){return workload;}
        SimpleStringProperty weightProperty(){return weight;}
    }

    private static final class AssessmentRow {
        final SimpleLongProperty id;
        final SimpleStringProperty title,type,term,date,maxScore,weight,className,subjectName,teacherName;
        final long classId, subjectId, teacherId;
        AssessmentRow(long id,String title,String type,String term,String date,String maxScore,String weight,String className,String subjectName,String teacherName,
                       long classId,long subjectId,long teacherId){
            this.id=new SimpleLongProperty(id);this.title=new SimpleStringProperty(title);this.type=new SimpleStringProperty(type);
            this.term=new SimpleStringProperty(term);this.date=new SimpleStringProperty(date);this.maxScore=new SimpleStringProperty(maxScore);
            this.weight=new SimpleStringProperty(weight);this.className=new SimpleStringProperty(className);this.subjectName=new SimpleStringProperty(subjectName);
            this.teacherName=new SimpleStringProperty(teacherName);this.classId=classId;this.subjectId=subjectId;this.teacherId=teacherId;
        }
        static AssessmentRow from(Map<String,Object> r){return new AssessmentRow(n(r.get("id")),s(r.get("title")),s(r.get("type")),s(r.get("term")),s(r.get("assessment_date")),
                s(r.get("max_score")),s(r.get("weight")),s(r.get("class_name")),s(r.get("subject_name")),s(r.get("teacher_name")),
                n(r.get("class_id")),n(r.get("subject_id")),n(r.get("teacher_id")));}
        SimpleStringProperty titleProperty(){return title;}
        SimpleStringProperty typeProperty(){return type;}
        SimpleStringProperty termProperty(){return term;}
        SimpleStringProperty dateProperty(){return date;}
        SimpleStringProperty maxScoreProperty(){return maxScore;}
        SimpleStringProperty classNameProperty(){return className;}
        SimpleStringProperty subjectNameProperty(){return subjectName;}
        SimpleStringProperty weightProperty(){return weight;}
    }

    private static final class GradeRow {
        final SimpleLongProperty id;
        final SimpleStringProperty number,name,score,observation;
        GradeRow(long id,String number,String name,String score,String observation){
            this.id=new SimpleLongProperty(id);this.number=new SimpleStringProperty(number);this.name=new SimpleStringProperty(name);
            this.score=new SimpleStringProperty(score);this.observation=new SimpleStringProperty(observation);
        }
        static GradeRow from(Map<String,Object> r){return new GradeRow(n(r.get("id")),s(r.get("student_number")),s(r.get("name")),s(r.get("score")),s(r.get("observation")));}
        SimpleStringProperty numberProperty(){return number;}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty scoreProperty(){return score;}
        SimpleStringProperty observationProperty(){return observation;}
    }

    private static final class PerformanceRow {
        final SimpleLongProperty id;
        final SimpleStringProperty number,name,className,count,average,status;
        PerformanceRow(long id,String number,String name,String className,String count,String average,String status){
            this.id=new SimpleLongProperty(id);this.number=new SimpleStringProperty(number);this.name=new SimpleStringProperty(name);
            this.className=new SimpleStringProperty(className);this.count=new SimpleStringProperty(count);this.average=new SimpleStringProperty(average);this.status=new SimpleStringProperty(status);
        }
        static PerformanceRow from(Map<String,Object> r){return new PerformanceRow(n(r.get("id")),s(r.get("student_number")),s(r.get("name")),s(r.get("class_name")),
                s(r.get("assessments_count")),s(r.get("average_score")),s(r.get("status")));}
        SimpleStringProperty numberProperty(){return number;}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty classNameProperty(){return className;}
        SimpleStringProperty countProperty(){return count;}
        SimpleStringProperty averageProperty(){return average;}
        SimpleStringProperty statusProperty(){return status;}
    }

    private record ClassOption(long id, String name) {
        static ClassOption from(Map<String,Object> r){ return new ClassOption(n(r.get("id")), s(r.get("name"))); }
        @Override public String toString(){return name;}
    }

    private record SubjectOption(long id, String name) {
        static SubjectOption from(Map<String,Object> r){ return new SubjectOption(n(r.get("id")), s(r.get("name"))); }
        @Override public String toString(){return name;}
    }

    private record TeacherOption(long id, String name) {
        static TeacherOption from(Map<String,Object> r){ return new TeacherOption(n(r.get("id")), s(r.get("name"))); }
        @Override public String toString(){return name;}
    }

    private record AssessmentOption(long id, String title, String subject, String clazz, double maxScore) {
        static AssessmentOption from(Map<String,Object> r){
            double max;
            try { max=Double.parseDouble(s(r.get("max_score"))); } catch(Exception e){max=20;}
            return new AssessmentOption(n(r.get("id")),s(r.get("title")),s(r.get("subject_name")),s(r.get("class_name")),max);
        }
        @Override public String toString(){return title+" • "+subject+" • "+clazz+" • máx. "+trim(maxScore);}
    }

    private static String trim(double d) {
        if (d == Math.rint(d)) return String.valueOf((long)d);
        return String.format(Locale.US, "%.2f", d);
    }

    private static String s(Object o){return o==null?"":String.valueOf(o);}
    private static long n(Object o){
        if(o==null)return 0;
        if(o instanceof Number number)return number.longValue();
        try{return Long.parseLong(String.valueOf(o));}catch(Exception e){return 0;}
    }

    /**
     * Action helper compatível com o renderer usado acima.
     */
    private interface ControlFactory<T> { Node create(T row); }

    // Override helper to support controls returned as Node.
    private <T> TableColumn<T, Void> actionColumn(TableView<T> table, ControlFactory<T> factory) {
        TableColumn<T, Void> column = new TableColumn<>("Ações");
        column.setCellFactory(tc -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    setGraphic(factory.create(getTableView().getItems().get(getIndex())));
                }
            }
        });
        return column;
    }
}
