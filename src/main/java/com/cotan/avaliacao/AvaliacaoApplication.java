package com.cotan.avaliacao;

import com.cotan.avaliacao.ui.CotanHeader;
import com.cotan.avaliacao.ui.CotanIcons;
import com.cotan.avaliacao.ui.CotanMetricCard;
import com.cotan.avaliacao.ui.CotanModalHost;
import com.cotan.avaliacao.ui.CotanSidebar;
import com.cotan.avaliacao.ui.CotanUi;
import com.cotan.avaliacao.ui.table.AdvancedTableView;
import com.cotan.avaliacao.ui.table.TableUtils;
import com.cotan.avaliacao.report.AvaliacaoProfessorRelatorio;
import com.cotan.avaliacao.report.AvaliacaoProfessorReportService;
import com.cotan.avaliacao.domain.AvaliacaoDesempenhoAnual;
import com.cotan.avaliacao.domain.InstitutionProfile;
import atlantafx.base.controls.Card;
import atlantafx.base.controls.Message;
import atlantafx.base.controls.Tile;
import atlantafx.base.controls.ToggleSwitch;
import org.kordamp.ikonli.feather.Feather;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.awt.Desktop;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

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
@SpringBootApplication
public class AvaliacaoApplication extends Application {

    private static final String APP_NAME = "COTAN";
    private static final String APP_SUBTITLE = "Avaliação e Desempenho";

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> logTerminal(
                "EXCEÇÃO NÃO TRATADA | thread=" + thread.getName(), throwable
        ));
        System.err.println("============================================================");
        System.err.println("COTAN • Avaliação e Desempenho");
        System.err.println("Início da aplicação");
        System.err.println("Java: " + System.getProperty("java.version"));
        System.err.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.err.println("============================================================");
        Application.launch(AvaliacaoApplication.class, args);
    }

    private ConfigurableApplicationContext springContext;
    private Database database;
    private Stage stage;
    private BorderPane root;
    private StackPane content;
    private static final String TAB_SECTION_KEY = "cotan.section";
    private TabPane navigationTabs;
    private final Map<String, Tab> openTabs = new LinkedHashMap<>();
    private final Map<String, Node> openPages = new LinkedHashMap<>();
    private Label dbStatus;
    private CotanModalHost modalHost;
    private CotanSidebar sidebar;
    private CotanHeader header;
    private final Deque<Node> modalHistory = new ArrayDeque<>();
    private String currentSection = "dashboard";
    private boolean darkMode = false;
    private Label footerClock;
    private javafx.animation.Timeline footerClockTimeline;

    private static final Set<String> EXCEL_PERFORMANCE_SCORES =
            Set.of("5", "10", "15", "20");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(?:\\+244\\s?)?9\\d{8}$");
    private static final Pattern ACADEMIC_YEAR_PATTERN =
            Pattern.compile("^20\\d{2}/20\\d{2}$");

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

        stage.setOnCloseRequest(e -> shutdown());
        showLoading();
        stage.show();
        initializeBackendAsync();
    }

    private void initializeBackendAsync() {
        System.err.println("[COTAN][STARTUP] A iniciar backend...");
        CompletableFuture.runAsync(() -> {
            try {
                closeBackendSafely();

                System.err.println("[COTAN][STARTUP] A iniciar contexto Spring...");
                springContext = new SpringApplicationBuilder(AvaliacaoApplication.class)
                        .headless(false)
                        .run();
                System.err.println("[COTAN][STARTUP] Contexto Spring iniciado.");

                System.err.println("[COTAN][STARTUP] A abrir SQLite...");
                database = new Database();
                database.open();
                System.err.println("[COTAN][STARTUP] SQLite inicializado em: " + database.getDatabasePath());

                Platform.runLater(() -> {
                    try {
                        showShell();
                        System.err.println("[COTAN][STARTUP] Interface principal carregada com sucesso.");
                    } catch (Throwable ex) {
                        logTerminal("FALHA AO CARREGAR A INTERFACE PRINCIPAL", ex);
                        showStartupError(ex);
                    }
                });
            } catch (Throwable ex) {
                logTerminal("FALHA NA INICIALIZAÇÃO", ex);
                Platform.runLater(() -> showStartupError(ex));
            }
        });
    }

    private void showLoading() {
        Application.setUserAgentStylesheet(LIGHT_THEME);
        StackPane pane = new StackPane();

        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);

        Label mark = new Label("COTAN");
        mark.getStyleClass().add("title-2");

        Label title = new Label(APP_SUBTITLE);
        title.getStyleClass().add("title-3");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        Label status = new Label("A preparar a base de dados e os módulos...");
        status.getStyleClass().add("text-muted");

        box.getChildren().addAll(mark, title, spinner, status);
        pane.getChildren().add(box);

        Scene scene = new Scene(pane);
        stage.setScene(scene);
    }

    private void showStartupError(Throwable ex) {
        logTerminal("ERRO EXIBIDO NA TELA DE INICIALIZAÇÃO", ex);

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(36));

        Label title = new Label("Não foi possível continuar a inicialização");
        title.getStyleClass().add("title-2");

        String message = ex == null || ex.getMessage() == null || ex.getMessage().isBlank()
                ? String.valueOf(ex)
                : ex.getMessage();

        Label detail = new Label(message);
        detail.setWrapText(true);
        detail.setMaxWidth(900);

        Label terminalHint = new Label(
                "O erro técnico completo foi enviado para o terminal.\n"
                        + "Corrija o problema e use Reintentar para iniciar novamente."
        );
        terminalHint.setWrapText(true);
        terminalHint.setMaxWidth(900);
        terminalHint.getStyleClass().add("text-muted");

        Button retry = CotanIcons.button("Reintentar", Feather.REFRESH_CW, "accent-button", "accent");
        retry.setOnAction(e -> {
            closeBackendSafely();
            showLoading();
            initializeBackendAsync();
        });

        Button close = CotanIcons.button("Fechar", Feather.X, "button-outlined");
        close.setOnAction(e -> stage.close());

        HBox actions = new HBox(10, retry, close);
        actions.setAlignment(Pos.CENTER);

        box.getChildren().addAll(title, detail, terminalHint, actions);
        StackPane pane = new StackPane(box);

        Scene scene = new Scene(pane, 1180, 720);
        stage.setScene(scene);
    }

    private static void logTerminal(String context, Throwable ex) {
        System.err.println();
        System.err.println("========== COTAN / ERRO ==========");
        System.err.println("[COTAN] " + context);
        if (ex == null) {
            System.err.println("[COTAN] Sem exceção técnica disponível.");
            System.err.println("==================================");
            return;
        }

        System.err.println("[COTAN] Exceção: " + ex.getClass().getName());
        System.err.println("[COTAN] Mensagem: "
                + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));

        Throwable cause = ex.getCause();
        int level = 1;
        while (cause != null && level <= 8) {
            System.err.println("[COTAN] Causa " + level + ": "
                    + cause.getClass().getName() + " — "
                    + (cause.getMessage() == null ? cause.toString() : cause.getMessage()));
            cause = cause.getCause();
            level++;
        }

        ex.printStackTrace(System.err);
        System.err.println("==================================");
    }

    private void closeBackendSafely() {
        if (database != null) {
            try {
                database.close();
            } catch (Throwable ex) {
                logTerminal("Não foi possível fechar a base de dados durante uma recuperação", ex);
            } finally {
                database = null;
            }
        }
        if (springContext != null) {
            try {
                springContext.close();
            } catch (Throwable ex) {
                logTerminal("Não foi possível fechar o contexto Spring durante uma recuperação", ex);
            } finally {
                springContext = null;
            }
        }
    }

    private void showShell() {
        root = new BorderPane();

        sidebar = buildSidebar();
        root.setLeft(sidebar);

        VBox applicationTop = new VBox(buildTopBar(), buildNavigationTabs());
        root.setTop(applicationTop);

        content = new StackPane();
        content.setPadding(new Insets(20));
        root.setCenter(content);

        root.setBottom(buildStatusBar());

        modalHost = new CotanModalHost();
        modalHost.setEscapeHandler(event -> closeTopModal());

        StackPane sceneRoot = new StackPane(root, modalHost);
        StackPane.setAlignment(modalHost, Pos.CENTER);

        Scene scene = new Scene(sceneRoot, stage.getWidth(), stage.getHeight());
        scene.widthProperty().addListener((obs, oldWidth, newWidth) ->
                applyResponsiveLayout(newWidth.doubleValue()));
        stage.setScene(scene);
        stage.centerOnScreen();
        applyResponsiveLayout(stage.getWidth());

        showSection("dashboard");
    }

    private CotanSidebar buildSidebar() {
        return new CotanSidebar(this::showSection);
    }

    private Node buildTopBar() {
        header = new CotanHeader(
                this::handleGlobalSearch,
                this::toggleSidebar,
                this::toggleTheme
        );
        header.setPage("Início", "Início");
        return header;
    }

    private TabPane buildNavigationTabs() {
        navigationTabs = new TabPane();
        navigationTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        navigationTabs.setSide(javafx.geometry.Side.TOP);
        navigationTabs.setPrefHeight(46);
        navigationTabs.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> {
            Object route = selected == null ? null : selected.getProperties().get(TAB_SECTION_KEY);
            if (route instanceof String section) {
                activateSection(section);
            }
        });
        return navigationTabs;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(9, 18, 9, 18));

        dbStatus = new Label("Pronto  •  SQLite conectado", CotanIcons.icon(Feather.DATABASE, 13));
        dbStatus.getStyleClass().add("success");
        dbStatus.setGraphicTextGap(7);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label version = new Label("COTAN • Avaliação e Desempenho • 1.0");
        version.getStyleClass().addAll("text-muted", "text-small");

        footerClock = new Label();
        footerClock.getStyleClass().addAll("text-muted", "text-small");
        updateFooterClock();

        bar.getChildren().addAll(dbStatus, spacer, version, new Label("  •  "), footerClock);

        if (footerClockTimeline != null) footerClockTimeline.stop();
        footerClockTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(
                        javafx.util.Duration.seconds(1),
                        e -> updateFooterClock()
                )
        );
        footerClockTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        footerClockTimeline.play();

        return bar;
    }

    private void updateFooterClock() {
        if (footerClock != null) {
            footerClock.setText(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        }
    }

    private void toggleSidebar() {
        if (sidebar != null) sidebar.setCollapsed(!sidebar.isCollapsed());
    }

    private void toggleTheme() {
        darkMode = !darkMode;
        Application.setUserAgentStylesheet(darkMode ? DARK_THEME : LIGHT_THEME);
        if (header != null) {
            header.addNotification(darkMode ? "Modo escuro ativado." : "Modo claro ativado.");
        }
    }

    private void applyResponsiveLayout(double width) {
        if (sidebar == null) return;
        if (width < 1080) {
            sidebar.setCollapsed(true);
        } else if (width >= 1200 && sidebar.isCollapsed()) {
            sidebar.setCollapsed(false);
        }
    }

    private void handleGlobalSearch(String query) {
        if (query == null || query.isBlank() || database == null) return;
        String q = query.trim().toLowerCase(Locale.ROOT);

        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("início", "dashboard");
        aliases.put("dashboard", "dashboard");
        aliases.put("professor", "teachers");
        aliases.put("professores", "teachers");
        aliases.put("administrativo", "administrative");
        aliases.put("administrativos", "administrative");
        aliases.put("avaliação professores", "professor-evaluation");
        aliases.put("avaliação administrativos", "administrative-evaluation");
        aliases.put("aaconect professores", "aaconnect-professors");
        aliases.put("aaconect administrativos", "aaconnect-administrative");
        aliases.put("mapa 1", "map-1");
        aliases.put("mapa 2", "map-2");
        aliases.put("mapa 3", "map-3");
        aliases.put("mapa final professor", "map-final-professor");
        aliases.put("mapa final administrativo", "map-final-administrative");
        aliases.put("alunos", "students");
        aliases.put("turmas", "classes");
        aliases.put("disciplinas", "subjects");
        aliases.put("relatórios", "reports");
        aliases.put("relatorios", "reports");
        aliases.put("indicadores", "indicators");
        aliases.put("configurações", "settings");
        aliases.put("configuracoes", "settings");
        aliases.put("sobre", "about");

        String target = aliases.get(q);
        if (target != null) {
            showSection(target);
            return;
        }

        try {
            String like = "%" + q + "%";

            if (((Number) database.scalar(
                    "SELECT COUNT(*) FROM staff WHERE lower(name) LIKE ? OR lower(code) LIKE ?",
                    like, like)).intValue() > 0) {
                showSection(q.startsWith("adm") ? "administrative" : "teachers");
                return;
            }

            if (((Number) database.scalar(
                    "SELECT COUNT(*) FROM students WHERE lower(name) LIKE ? OR lower(student_number) LIKE ?",
                    like, like)).intValue() > 0) {
                showSection("students");
                return;
            }

            if (((Number) database.scalar(
                    "SELECT COUNT(*) FROM classes WHERE lower(name) LIKE ?",
                    like)).intValue() > 0) {
                showSection("classes");
                return;
            }

            if (((Number) database.scalar(
                    "SELECT COUNT(*) FROM subjects WHERE lower(name) LIKE ? OR lower(code) LIKE ?",
                    like, like)).intValue() > 0) {
                showSection("subjects");
                return;
            }

            if (((Number) database.scalar(
                    "SELECT COUNT(*) FROM performance_indicators WHERE lower(name) LIKE ? OR lower(code) LIKE ?",
                    like, like)).intValue() > 0) {
                showSection("indicators");
                return;
            }

            showToast("Nenhum resultado encontrado para: " + query);
        } catch (SQLException ex) {
            showError("A pesquisa global falhou", ex);
        }
    }

    private void showSection(String section) {
        if (section == null || section.isBlank() || navigationTabs == null) return;

        Tab existingTab = openTabs.get(section);
        if (existingTab != null) {
            navigationTabs.getSelectionModel().select(existingTab);
            return;
        }

        try {
            Node page = createSectionPage(section);
            Tab tab = new Tab(sectionTitle(section));
            tab.getProperties().put(TAB_SECTION_KEY, section);
            tab.setGraphic(CotanIcons.icon(sectionIcon(section), 15));
            tab.setTooltip(new Tooltip(sectionTitle(section)));
            tab.setOnClosed(event -> {
                openTabs.remove(section);
                openPages.remove(section);
                if (section.equals(currentSection)) {
                    Tab selected = navigationTabs.getSelectionModel().getSelectedItem();
                    Object selectedRoute = selected == null ? null : selected.getProperties().get(TAB_SECTION_KEY);
                    if (selectedRoute instanceof String route) {
                        activateSection(route);
                    } else {
                        showSection("dashboard");
                    }
                }
            });

            openTabs.put(section, tab);
            openPages.put(section, pageView(page));
            navigationTabs.getTabs().add(tab);
            navigationTabs.getSelectionModel().select(tab);
        } catch (Exception ex) {
            showError("Erro ao abrir o módulo", ex);
        }
    }

    private void activateSection(String section) {
        currentSection = section;
        String title = sectionTitle(section);
        if (header != null) {
            header.setPage(title, "Início  /  " + title);
            header.clearSearch();
        }

        if (sidebar != null) {
            sidebar.setActive(section);
        }

        updateTopAction();
        Node page = openPages.get(section);
        if (page != null) setContentPage(page);
    }

    private String sectionTitle(String section) {
        return switch (section) {
            case "dashboard" -> "Início";
            case "teachers" -> "Professores";
            case "administrative" -> "Administrativos";
            case "professor-evaluation" -> "Avaliação — Professores";
            case "administrative-evaluation" -> "Avaliação — Administrativos";
            case "aaconnect-professors" -> "AACONECT — Professores";
            case "aaconnect-administrative" -> "AACONECT — Administrativos";
            case "map-1" -> "Mapa 1º Trimestre";
            case "map-2" -> "Mapa 2º Trimestre";
            case "map-3" -> "Mapa 3º Trimestre";
            case "map-final-professor" -> "Mapa Final — Professor";
            case "map-final-administrative" -> "Mapa Final — Administrativo";
            case "students" -> "Gestão de Alunos";
            case "classes" -> "Gestão de Turmas";
            case "subjects" -> "Gestão de Disciplinas";
            case "assessments" -> "Avaliações";
            case "grades" -> "Lançamento de Notas";
            case "reports" -> "Relatórios de Desempenho";
            case "indicators" -> "Indicadores de Avaliação";
            case "settings" -> "Configurações";
            case "about" -> "Sobre o sistema";
            default -> "COTAN";
        };
    }

    private Feather sectionIcon(String section) {
        return switch (section) {
            case "dashboard" -> Feather.HOME;
            case "teachers" -> Feather.USER;
            case "administrative" -> Feather.BRIEFCASE;
            case "professor-evaluation", "administrative-evaluation" -> Feather.CHECK_CIRCLE;
            case "aaconnect-professors", "aaconnect-administrative" -> Feather.LINK;
            case "map-1", "map-2", "map-3" -> Feather.BAR_CHART_2;
            case "map-final-professor", "map-final-administrative" -> Feather.AWARD;
            case "students" -> Feather.USERS;
            case "classes" -> Feather.GRID;
            case "subjects" -> Feather.BOOK_OPEN;
            case "assessments", "grades" -> Feather.CLIPBOARD;
            case "indicators" -> Feather.TARGET;
            case "settings" -> Feather.SETTINGS;
            case "about" -> Feather.INFO;
            default -> Feather.FILE_TEXT;
        };
    }

    private void updateTopAction() {
        if (header == null) return;

        switch (currentSection) {
            case "students" -> header.setPrimaryAction(
                    "Novo aluno", Feather.USER_PLUS, () -> studentDialog(null), true);
            case "teachers" -> header.setPrimaryAction(
                    "Novo professor", Feather.USER_PLUS, () -> staffDialog("PROFESSOR", null), true);
            case "administrative" -> header.setPrimaryAction(
                    "Novo administrativo", Feather.USER_PLUS, () -> staffDialog("ADMINISTRATIVO", null), true);
            case "classes" -> header.setPrimaryAction(
                    "Nova turma", Feather.PLUS, () -> classDialog(null), true);
            case "subjects" -> header.setPrimaryAction(
                    "Nova disciplina", Feather.BOOK_OPEN, () -> subjectDialog(null), true);
            case "indicators" -> header.setPrimaryAction(
                    "Novo indicador", Feather.TARGET, () -> indicatorDialog(null), true);
            case "professor-evaluation", "administrative-evaluation" -> header.setPrimaryAction(
                    "Recarregar", Feather.REFRESH_CW, this::refreshCurrentSection, true);
            default -> header.setPrimaryAction("", Feather.PLUS, null, false);
        }
    }

    private void setContentPage(Node node) {
        if (content == null) return;
        content.getChildren().setAll(node);
    }

    private Node pageView(Node page) {
        if (page instanceof ScrollPane scroll) {
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
            return scroll;
        }
        return CotanUi.scroll(page);
    }

    private void refreshCurrentSection() {
        if (content == null || database == null) return;
        try {
            Node page = pageView(createSectionPage(currentSection));
            openPages.put(currentSection, page);
            if (content.getChildren().isEmpty() || openTabs.get(currentSection) == navigationTabs.getSelectionModel().getSelectedItem()) {
                setContentPage(page);
            }
        } catch (Exception ex) {
            showError("Erro ao carregar o módulo", ex);
        }
    }

    private Node createSectionPage(String section) throws Exception {
        return switch (section) {
            case "dashboard" -> buildDashboard();
            case "administrative" -> buildStaff("ADMINISTRATIVO");
            case "professor-evaluation" -> buildPerformanceEvaluation("PROFESSOR");
            case "administrative-evaluation" -> buildPerformanceEvaluation("ADMINISTRATIVO");
            case "aaconnect-professors" -> buildAaconnect("PROFESSOR");
            case "aaconnect-administrative" -> buildAaconnect("ADMINISTRATIVO");
            case "map-1" -> buildPerformanceMapAll(1);
            case "map-2" -> buildPerformanceMapAll(2);
            case "map-3" -> buildPerformanceMapAll(3);
            case "map-final-professor" -> buildPerformanceFinal("PROFESSOR");
            case "map-final-administrative" -> buildPerformanceFinal("ADMINISTRATIVO");
            case "students" -> buildStudents();
            case "teachers" -> buildStaff("PROFESSOR");
            case "classes" -> buildClasses();
            case "subjects" -> buildSubjects();
            case "assessments" -> buildAssessments();
            case "grades" -> buildGrades();
            case "reports" -> buildReports();
            case "indicators" -> buildIndicators();
            case "settings" -> buildSettings();
            case "about" -> buildAbout();
            default -> buildDashboard();
        };
    }

    // -------------------------------------------------------------------------
    // DASHBOARD
    // -------------------------------------------------------------------------

    private Node buildDashboard() throws SQLException {
        VBox page = pageContainer();

        Card heroCard = new Card();
        HBox hero = new HBox(22);
        hero.setPadding(new Insets(22));
        hero.setAlignment(Pos.CENTER_LEFT);

        VBox intro = new VBox(7);
        Label eyebrow = label("COTAN • SISTEMA INFORMATIZADO", "eyebrow");
        Label title = label("Avaliação de Desempenho", "hero-title");
        title.setWrapText(true);
        Label text = label(
                "Gestão do ciclo de avaliação, dos lançamentos trimestrais à consolidação dos resultados.",
                "muted"
        );
        text.setWrapText(true);
        intro.getChildren().addAll(eyebrow, title, text);

        Region heroSpacer = new Region();
        HBox.setHgrow(heroSpacer, Priority.ALWAYS);

        Message excelBadge = new Message(
            "10 folhas funcionais",
            "Professores • Administrativos • Trimestres • Mapas finais",
            CotanIcons.icon(Feather.FILE_TEXT, 18)
        );
        excelBadge.getStyleClass().add("accent");
        excelBadge.setPrefWidth(300);
        hero.getChildren().addAll(intro, heroSpacer, excelBadge);
        heroCard.setBody(hero);

        FlowPane metrics = new FlowPane();
        metrics.setHgap(14);
        metrics.setVgap(14);
        metrics.setPrefWrapLength(1160);

        long teachersCount = database.scalar(
                "SELECT COUNT(*) FROM staff WHERE staff_type='PROFESSOR'") instanceof Number n1 ? n1.longValue() : 0;
        long adminCount = database.scalar(
                "SELECT COUNT(*) FROM staff WHERE staff_type='ADMINISTRATIVO'") instanceof Number n2 ? n2.longValue() : 0;
        long studentsCount = database.count("students");
        long classesCount = database.count("classes");
        long subjectsCount = database.count("subjects");
        long assessmentsCount = database.count("assessments");
        long indicatorsCount = database.count("performance_indicators");
        long performanceEntries = database.count("performance_scores");

        metrics.getChildren().addAll(
                new CotanMetricCard("Professores", String.valueOf(teachersCount),
                        "Profissionais docentes registados", Feather.USER, "accent"),
                new CotanMetricCard("Administrativos", String.valueOf(adminCount),
                        "Colaboradores administrativos", Feather.BRIEFCASE, "success"),
                new CotanMetricCard("Alunos", String.valueOf(studentsCount),
                        "Alunos vinculados às turmas", Feather.USERS, "accent"),
                new CotanMetricCard("Turmas", String.valueOf(classesCount),
                        "Turmas no ano lectivo", Feather.GRID, "info"),
                new CotanMetricCard("Disciplinas", String.valueOf(subjectsCount),
                        "Disciplinas cadastradas", Feather.BOOK_OPEN, "accent"),
                new CotanMetricCard("Avaliações", String.valueOf(assessmentsCount),
                        "Avaliações académicas criadas", Feather.CLIPBOARD, "success"),
                new CotanMetricCard("Indicadores", String.valueOf(indicatorsCount),
                        "Indicadores de desempenho", Feather.TARGET, "info"),
                new CotanMetricCard("Lançamentos", String.valueOf(performanceEntries),
                        "Pontuações de desempenho", Feather.CHECK_CIRCLE, "success")
        );

        HBox heading = sectionHeading(
                "Centro de controlo",
                "Visão executiva do ciclo de avaliação e acesso rápido aos módulos."
        );

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(c1, c2);

        addHomeCard(grid, 0, 0, "teacher", "Professores", "Cadastro e acompanhamento dos professores.", "teachers");
        addHomeCard(grid, 1, 0, "administrative", "Administrativos", "Cadastro e acompanhamento dos colaboradores administrativos.", "administrative");
        addHomeCard(grid, 0, 1, "evaluation", "Avaliação — Professores", "Lançamento dos indicadores e avaliação de desempenho docente.", "professor-evaluation");
        addHomeCard(grid, 1, 1, "evaluation", "Avaliação — Administrativos", "Lançamento dos indicadores e avaliação de desempenho administrativo.", "administrative-evaluation");
        addHomeCard(grid, 0, 2, "aaconnect", "AACONECT", "Resultados/conexões dos professores e administrativos.", "aaconnect-professors");
        addHomeCard(grid, 1, 2, "map", "Mapas Trimestrais", "Mapas do 1º, 2º e 3º trimestre.", "map-1");
        addHomeCard(grid, 0, 3, "final", "Mapa Final — Professor", "Consolidação anual dos resultados dos professores.", "map-final-professor");
        addHomeCard(grid, 1, 3, "final", "Mapa Final — Administrativo", "Consolidação anual dos resultados administrativos.", "map-final-administrative");

        Card process = new Card();
        process.setHeader(label("Fluxo operacional", "card-title"));
        process.setBody(new VBox(8,
            label("Cadastro   ›   Avaliação   ›   Resultados trimestrais   ›   Consolidação   ›   Relatórios", "text-small"),
            label("As classificações são calculadas centralmente: Mau, Suficiente, Bom e Muito bom.", "muted")
        ));

        HBox shortcuts = new HBox(12);
        shortcuts.getChildren().addAll(
                actionCard("Nova avaliação", "Criar uma avaliação académica e definir turma, disciplina e peso.",
                        () -> assessmentDialog(null)),
                actionCard("Avaliar professor", "Lançar os indicadores de desempenho do professor seleccionado.",
                        () -> showSection("professor-evaluation")),
                actionCard("Mapa trimestral", "Consultar resultados consolidados por trimestre.",
                        () -> showSection("map-1")),
                actionCard("Relatórios", "Abrir a análise consolidada e exportar os resultados.",
                        () -> showSection("reports"))
        );

        page.getChildren().addAll(heroCard, metrics, heading, grid, shortcuts, process);

        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollBarPolicy.NEVER);
        return scroll;
    }

    private void addHomeCard(GridPane grid, int col, int row, String iconKey, String title, String description, String section) {
        Tile tile = new Tile(title, description, CotanIcons.icon(CotanIcons.feather(iconKey), 18));
        tile.setActionHandler(() -> showSection(section));
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setMinHeight(112);
        GridPane.setHgrow(tile, Priority.ALWAYS);
        grid.add(tile, col, row);
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

        Button back = CotanIcons.button("Voltar ao início", Feather.HOME, "button-outlined");
        back.setOnAction(e -> showSection("dashboard"));

        Button reports = CotanIcons.button("Abrir relatórios", Feather.FILE_TEXT, "button-outlined");
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
    // INDICATORS
    // -------------------------------------------------------------------------

    private Node buildIndicators() throws SQLException {
        VBox page = pageContainer();

        HBox heading = sectionHeading(
                "Indicadores de avaliação",
                "Critérios configuráveis usados pelo motor de desempenho. Ajuste nomes e pesos para refletir o mapa oficial."
        );

        AdvancedTableView<IndicatorRow> table = new AdvancedTableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Código", 115, IndicatorRow::codeProperty);
        addColumn(table, "Indicador", 300, IndicatorRow::nameProperty);
        addColumn(table, "Aplicação", 160, IndicatorRow::staffTypeProperty);
        addColumn(table, "Peso", 100, IndicatorRow::weightProperty);
        addColumn(table, "Descrição", 420, IndicatorRow::descriptionProperty);

        TableColumn<IndicatorRow, Void> actions = actionColumn(table, row -> {
            Button edit = miniButton("Editar");
            edit.setOnAction(e -> indicatorDialog(row));
            Button del = miniDangerButton("Eliminar");
            del.setOnAction(e -> confirmDelete("indicador", () -> database.deleteById("performance_indicators", row.id.get())));
            return new HBox(5, edit, del);
        });
        actions.setPrefWidth(92);
        table.getColumns().add(actions);

        ObservableList<IndicatorRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.indicators("AMBOS")) {
            IndicatorRow row = IndicatorRow.from(r);
            if (matches(q, row.code.get(), row.name.get(), row.staffType.get(), row.description.get())) rows.add(row);
        }
        table.setItems(rows);

        VBox note = card();
        note.getChildren().addAll(
                label("Regra observada no Excel", "card-title"),
                label("As classificações seguem as faixas: < 10 Mau • 10–13,9 Suficiente • 14–17,9 Bom • 18–20 Muito bom.", "muted")
        );

        page.getChildren().addAll(heading, note, tableFill(table));
        return page;
    }

    private void indicatorDialog(IndicatorRow existing) {
        CotanModal dialog = dialog(existing == null ? "Novo indicador" : "Editar indicador");
        GridPane grid = formGrid();

        TextField code = field("IND-06");
        TextField name = field("Nome do indicador");
        ComboBox<String> staffType = combo("AMBOS", "PROFESSOR", "ADMINISTRATIVO");
        Spinner<Double> weight = new Spinner<>(0.1, 10.0, 1.0, 0.1);
        TextField description = field("Descrição / orientação do critério");

        staffType.setValue("AMBOS");
        if (existing != null) {
            code.setText(existing.code.get());
            name.setText(existing.name.get());
            staffType.setValue(existing.staffType.get());
            try { weight.getValueFactory().setValue(Double.parseDouble(existing.weight.get())); } catch (Exception ignored) {}
            description.setText(existing.description.get());
        }

        grid.addRow(0, label("Código", "field-label"), code);
        grid.addRow(1, label("Indicador", "field-label"), name);
        grid.addRow(2, label("Aplicação", "field-label"), staffType);
        grid.addRow(3, label("Peso", "field-label"), weight);
        grid.addRow(4, label("Descrição", "field-label"), description);

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateIndicator(code.getText(), name.getText(), staffType.getValue(),
                    weight.getValue(), existing == null ? null : existing.id.get());
            if (validation != null) {
                showWarning(validation);
                return null;
            }
            try {
                if (existing == null) {
                    database.insert("""
                        INSERT INTO performance_indicators(code,name,description,staff_type,weight,sort_order)
                        VALUES(?,?,?,?,?,?)
                        """,
                        code.getText().trim().toUpperCase(Locale.ROOT), name.getText().trim(),
                        blankToNull(description.getText()), staffType.getValue(),
                        weight.getValue(), (int) database.count("performance_indicators") + 1);
                } else {
                    database.update("""
                        UPDATE performance_indicators SET code=?,name=?,description=?,staff_type=?,weight=?
                        WHERE id=?
                        """,
                        code.getText().trim().toUpperCase(Locale.ROOT), name.getText().trim(),
                        blankToNull(description.getText()), staffType.getValue(), weight.getValue(), existing.id.get());
                }
                refreshCurrentSection();
                showToast("Indicador guardado com sucesso.");
            } catch (SQLException e) {
                showError("Não foi possível guardar o indicador", e);
                return null;
            }
            return btn;
        });
        dialog.showAndWait();
    }

    private static final class IndicatorRow {
        final SimpleLongProperty id;
        final SimpleStringProperty code,name,staffType,weight,description;
        IndicatorRow(long id,String code,String name,String staffType,String weight,String description){
            this.id=new SimpleLongProperty(id);this.code=new SimpleStringProperty(code);this.name=new SimpleStringProperty(name);
            this.staffType=new SimpleStringProperty(staffType);this.weight=new SimpleStringProperty(weight);this.description=new SimpleStringProperty(description);
        }
        static IndicatorRow from(Map<String,Object> r){return new IndicatorRow(n(r.get("id")),s(r.get("code")),s(r.get("name")),s(r.get("staff_type")),s(r.get("weight")),s(r.get("description")));}
        SimpleStringProperty codeProperty(){return code;} SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty staffTypeProperty(){return staffType;} SimpleStringProperty weightProperty(){return weight;}
        SimpleStringProperty descriptionProperty(){return description;}
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

        AdvancedTableView<StudentRow> table = new AdvancedTableView<>();
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
        actions.setPrefWidth(92);
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
        CotanModal dialog = dialog(existing == null ? "Novo aluno" : "Editar aluno");
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

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateStudent(number.getText(), name.getText(), gender.getValue(),
                    birth.getValue(), phone.getText(), guardian.getText(),
                    existing == null ? null : existing.id.get());
            if (validation != null) {
                showWarning(validation);
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

        AdvancedTableView<StaffRow> table = new AdvancedTableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Código", 105, StaffRow::codeProperty);
        addColumn(table, "Nome completo", 245, StaffRow::nameProperty);
        addColumn(table, "Categoria", 240, StaffRow::categoryProperty);
        addColumn(table, "Agente nº", 115, StaffRow::agentNumberProperty);
        addColumn(table, "Cargo / função", 190, StaffRow::roleProperty);
        addColumn(table, "Departamento", 170, StaffRow::departmentProperty);
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
        actions.setPrefWidth(130);
        table.getColumns().add(actions);

        ObservableList<StaffRow> rows = FXCollections.observableArrayList();
        String q = search();
        for (Map<String,Object> r : database.staff(type)) {
            StaffRow row = StaffRow.from(r);
            if (matches(q, row.code.get(), row.name.get(), row.category.get(), row.agentNumber.get(), row.role.get(), row.department.get(), row.phone.get(), row.email.get())) {
                rows.add(row);
            }
        }
        table.setItems(rows);

        HBox quick = new HBox(10);
        Button evaluate = CotanIcons.button("Abrir avaliação", Feather.CLIPBOARD, "accent-button", "accent");
        evaluate.setOnAction(e -> showSection("PROFESSOR".equals(type) ? "professor-evaluation" : "administrative-evaluation"));
        Button map = CotanIcons.button("Ver mapa trimestral", Feather.BAR_CHART_2, "button-outlined");
        map.setOnAction(e -> showSection("map-1"));
        quick.getChildren().addAll(evaluate, map);

        page.getChildren().addAll(heading, quick, tableFill(table));
        return page;
    }

    private void staffDialog(String type, StaffRow existing) {
        String personLabel = "PROFESSOR".equals(type) ? "professor" : "administrativo";
        CotanModal dialog = dialog(existing == null ? "Novo " + personLabel : "Editar " + personLabel);
        GridPane grid = formGrid();

        TextField code = field("Ex.: " + ("PROFESSOR".equals(type) ? "PROF-003" : "ADM-003"));
        TextField name = field("Nome completo");
        TextField category = field("Categoria / carreira");
        TextField agentNumber = field("Número de agente / funcionário");
        TextField role = field("Cargo / função");
        TextField department = field("Departamento / área");
        TextField phone = field("923 000 000");
        TextField email = field("nome@cotan.edu");
        DatePicker admission = new DatePicker();

        if (existing != null) {
            code.setText(existing.code.get());
            name.setText(existing.name.get());
            category.setText(existing.category.get());
            agentNumber.setText(existing.agentNumber.get());
            role.setText(existing.role.get());
            department.setText(existing.department.get());
            phone.setText(existing.phone.get());
            email.setText(existing.email.get());
            if (!existing.admission.get().isBlank()) {
                try { admission.setValue(LocalDate.parse(existing.admission.get())); } catch (Exception ignored) {}
            }
        }

        grid.addRow(0, label("Código interno", "field-label"), code);
        grid.addRow(1, label("Nome completo", "field-label"), name);
        grid.addRow(2, label("Categoria / carreira", "field-label"), category);
        grid.addRow(3, label("Agente nº", "field-label"), agentNumber);
        grid.addRow(4, label("Cargo / função", "field-label"), role);
        grid.addRow(5, label("Departamento", "field-label"), department);
        grid.addRow(6, label("Telefone", "field-label"), phone);
        grid.addRow(7, label("E-mail", "field-label"), email);
        grid.addRow(8, label("Admissão", "field-label"), admission);

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateStaff(type, code.getText(), name.getText(),
                    category.getText(), agentNumber.getText(), role.getText(), department.getText(),
                    phone.getText(), email.getText(), admission.getValue(),
                    existing == null ? null : existing.id.get());
            if (validation != null) {
                showWarning(validation);
                return null;
            }
            try {
                String admissionDate = admission.getValue() == null ? null : admission.getValue().toString();
                if (existing == null) {
                    database.insert("""
                        INSERT INTO staff(code,name,staff_type,role,category,agent_number,department,phone,email,admission_date)
                        VALUES(?,?,?,?,?,?,?,?,?,?)
                        """,
                        code.getText().trim(), name.getText().trim(), type,
                        blankToNull(role.getText()), blankToNull(category.getText()), blankToNull(agentNumber.getText()),
                        blankToNull(department.getText()), blankToNull(phone.getText()), blankToNull(email.getText()), admissionDate);
                } else {
                    database.update("""
                        UPDATE staff SET code=?,name=?,role=?,category=?,agent_number=?,department=?,phone=?,email=?,admission_date=?
                        WHERE id=?
                        """,
                        code.getText().trim(), name.getText().trim(),
                        blankToNull(role.getText()), blankToNull(category.getText()), blankToNull(agentNumber.getText()),
                        blankToNull(department.getText()), blankToNull(phone.getText()), blankToNull(email.getText()), admissionDate,
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
        String title = "PROFESSOR".equals(type) ? "Avaliação de desempenho — Professores"
                : "Avaliação de desempenho — Administrativos";

        HBox heading = sectionHeading(title,
                "Lançamento trimestral ligado à ficha anual persistente do profissional.");

        ComboBox<StaffOption> staff = new ComboBox<>();
        staff.setPrefWidth(360);
        ComboBox<String> year = combo("2026/2027", "2027/2028");
        year.setValue("2026/2027");
        ComboBox<Integer> trimester = combo(1, 2, 3);
        trimester.setValue(1);

        DatePicker evaluationDate = new DatePicker(LocalDate.now());
        DatePicker periodStart = new DatePicker();
        DatePicker periodEnd = new DatePicker();
        ComboBox<StaffOption> evaluator = new ComboBox<>();
        ComboBox<StaffOption> homologante = new ComboBox<>();
        ComboBox<String> concordance = new ComboBox<>();
        concordance.getItems().setAll("", "Concordo", "Não concordo");

        TextArea comment1 = new TextArea();
        TextArea comment2 = new TextArea();
        TextArea comment3 = new TextArea();
        TextArea appreciation = new TextArea();
        for (TextArea area : List.of(comment1, comment2, comment3, appreciation)) {
            area.setWrapText(true);
            area.setPrefRowCount(2);
            area.setPromptText("Texto que será impresso na ficha anual…");
        }
        appreciation.setPrefRowCount(3);

        evaluator.setMaxWidth(Double.MAX_VALUE);
        homologante.setMaxWidth(Double.MAX_VALUE);

        try {
            for (Map<String,Object> row : database.staff(type)) {
                staff.getItems().add(StaffOption.from(row));
            }
            for (Map<String,Object> row : database.staffAll()) {
                StaffOption option = StaffOption.from(row);
                evaluator.getItems().add(option);
                homologante.getItems().add(option);
            }
        } catch (SQLException e) {
            showError("Erro ao carregar profissionais", e);
            return page;
        }

        if (selectedPerformanceStaffId != null) {
            staff.getItems().stream()
                    .filter(v -> v.id() == selectedPerformanceStaffId)
                    .findFirst().ifPresent(staff::setValue);
        } else if (!staff.getItems().isEmpty()) {
            staff.setValue(staff.getItems().get(0));
        }

        selectedPerformanceType = type;

        HBox selectors = new HBox(12,
                label("Profissional", "field-label"), staff,
                label("Ano", "field-label"), year,
                label("Trimestre", "field-label"), trimester);
        selectors.setAlignment(Pos.CENTER_LEFT);
        selectors.setPadding(new Insets(12));

        Card annualCard = new Card();
        annualCard.setHeader(label("Dados da ficha anual", "card-title"));

        GridPane annualForm = formGrid();
        annualForm.addRow(0, label("Data de avaliação", "field-label"), evaluationDate);
        annualForm.addRow(1, label("Início do período", "field-label"), periodStart);
        annualForm.addRow(2, label("Fim do período", "field-label"), periodEnd);
        annualForm.addRow(3, label("Avaliador", "field-label"), evaluator);
        annualForm.addRow(4, label("Homologante", "field-label"), homologante);
        annualForm.addRow(5, label("Concordância", "field-label"), concordance);
        annualForm.addRow(6, label("Comentário 1", "field-label"), comment1);
        annualForm.addRow(7, label("Comentário 2", "field-label"), comment2);
        annualForm.addRow(8, label("Comentário 3", "field-label"), comment3);
        annualForm.addRow(9, label("Comentário final", "field-label"), appreciation);

        Label annualHint = label(
                "Estes dados pertencem à ficha anual. O sistema guarda-os no SQLite e o JasperViewer "
                        + "apenas consulta a ficha; nenhum parâmetro é solicitado durante a impressão.",
                "muted"
        );
        annualHint.setWrapText(true);
        annualCard.setBody(new VBox(12, annualForm, annualHint));

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

        AdvancedTableView<PerformanceInputRow> table = new AdvancedTableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table, "Código", 110, PerformanceInputRow::codeProperty);
        addColumn(table, "Indicador", 280, PerformanceInputRow::nameProperty);
        addColumn(table, "Peso", 95, PerformanceInputRow::weightProperty);

        TableColumn<PerformanceInputRow,String> score = new TableColumn<>("Pontuação");
        score.setPrefWidth(170);
        score.setCellValueFactory(cel -> cel.getValue().scoreProperty());
        score.setCellFactory(ComboBoxTableCell.forTableColumn("", "5", "10", "15", "20"));
        score.setOnEditCommit(e -> {
            String v = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (v.isBlank() || EXCEL_PERFORMANCE_SCORES.contains(v)) {
                e.getRowValue().score.set(v);
                recalcPerformance(table, result);
            } else {
                showWarning("Pontuação inválida. A escala do ficheiro Excel é 5, 10, 15 ou 20.");
                table.refresh();
            }
        });

        TableColumn<PerformanceInputRow,String> obs = new TableColumn<>("Observação");
        obs.setPrefWidth(360);
        obs.setCellValueFactory(cel -> cel.getValue().observationProperty());
        obs.setCellFactory(TextFieldTableCell.forTableColumn());
        obs.setOnEditCommit(e -> {
            String value = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (value.length() > 500) {
                showWarning("A observação não pode exceder 500 caracteres.");
                table.refresh();
                return;
            }
            e.getRowValue().observation.set(value);
            table.refresh();
        });
        table.getColumns().addAll(score, obs);

        Runnable load = () -> {
            try {
                StaffOption selectedStaff = staff.getValue();
                if (selectedStaff == null) {
                    table.getItems().clear();
                    return;
                }

                String selectedYear = year.getValue();
                int startYear = Integer.parseInt(selectedYear.substring(0, 4));
                AvaliacaoDesempenhoAnual annual = database.performanceEvaluation(
                        selectedStaff.id(), selectedYear);

                if (annual == null) {
                    evaluationDate.setValue(LocalDate.now());
                    periodStart.setValue(LocalDate.of(startYear, 9, 1));
                    periodEnd.setValue(LocalDate.of(startYear + 1, 6, 30));
                    concordance.setValue("");
                    comment1.clear();
                    comment2.clear();
                    comment3.clear();
                    appreciation.clear();

                    InstitutionProfile profile = database.institutionProfileEntity();
                    evaluator.getSelectionModel().clearSelection();
                    homologante.getSelectionModel().clearSelection();
                    if (profile.defaultEvaluatorStaffId() != null) {
                        evaluator.getItems().stream()
                                .filter(v -> v.id() == profile.defaultEvaluatorStaffId())
                                .findFirst().ifPresent(evaluator::setValue);
                    }
                    if (profile.defaultHomologanteStaffId() != null) {
                        homologante.getItems().stream()
                                .filter(v -> v.id() == profile.defaultHomologanteStaffId())
                                .findFirst().ifPresent(homologante::setValue);
                    }
                } else {
                    evaluationDate.setValue(annual.evaluationDate());
                    periodStart.setValue(annual.periodStart() != null
                            ? annual.periodStart() : LocalDate.of(startYear, 9, 1));
                    periodEnd.setValue(annual.periodEnd() != null
                            ? annual.periodEnd() : LocalDate.of(startYear + 1, 6, 30));
                    concordance.setValue(annual.concordance());
                    comment1.setText(annual.comment1());
                    comment2.setText(annual.comment2());
                    comment3.setText(annual.comment3());
                    appreciation.setText(annual.appreciationGeneral());

                    evaluator.getSelectionModel().clearSelection();
                    homologante.getSelectionModel().clearSelection();
                    if (annual.evaluatorStaffId() != null) {
                        evaluator.getItems().stream()
                                .filter(v -> v.id() == annual.evaluatorStaffId())
                                .findFirst().ifPresent(evaluator::setValue);
                    }
                    if (annual.homologanteStaffId() != null) {
                        homologante.getItems().stream()
                                .filter(v -> v.id() == annual.homologanteStaffId())
                                .findFirst().ifPresent(homologante::setValue);
                    }
                }

                ObservableList<PerformanceInputRow> items = FXCollections.observableArrayList();
                for (Map<String,Object> row : database.performanceScores(
                        selectedStaff.id(), selectedYear, trimester.getValue())) {
                    items.add(PerformanceInputRow.from(row));
                }
                table.setItems(items);
                recalcPerformance(table, result);
            } catch (Exception ex) {
                showError("Não foi possível carregar a ficha anual e os indicadores", ex);
            }
        };

        staff.setOnAction(e -> {
            selectedPerformanceStaffId = staff.getValue() == null ? null : staff.getValue().id();
            load.run();
        });
        year.setOnAction(e -> load.run());
        trimester.setOnAction(e -> load.run());
        load.run();

        Button save = CotanIcons.button("Guardar avaliação", Feather.SAVE, "accent-button", "accent");
        save.setTooltip(new Tooltip("Guardar dados da ficha anual e lançamentos do trimestre selecionado"));
        save.setOnAction(e -> {
            StaffOption selectedStaff = staff.getValue();
            if (selectedStaff == null) {
                showWarning("Selecione o profissional.");
                return;
            }
            if (evaluator.getValue() == null) {
                showWarning("Selecione o avaliador da ficha anual. "
                        + "Pode defini-lo uma vez em Configurações como avaliador padrão.");
                return;
            }

            try {
                int filled = 0;
                for (PerformanceInputRow row : table.getItems()) {
                    if (row.score.get().isBlank()) continue;
                    filled++;
                    if (!EXCEL_PERFORMANCE_SCORES.contains(row.score.get().trim())) {
                        showWarning("A pontuação de " + row.name.get()
                                + " deve ser 5, 10, 15 ou 20, conforme a escala do Excel.");
                        return;
                    }
                    if (row.observation.get() != null && row.observation.get().length() > 500) {
                        showWarning("A observação de " + row.name.get() + " excede 500 caracteres.");
                        return;
                    }
                }
                if (filled > 0 && filled < table.getItems().size()) {
                    showWarning("Preencha todos os " + table.getItems().size()
                            + " indicadores antes de guardar o trimestre.");
                    return;
                }
                for (TextArea area : List.of(comment1, comment2, comment3, appreciation)) {
                    if (area.getText() != null && area.getText().length() > 2000) {
                        showWarning("Os textos da ficha anual não podem exceder 2000 caracteres.");
                        return;
                    }
                }

                AvaliacaoDesempenhoAnual current = database.performanceEvaluation(
                        selectedStaff.id(), year.getValue());

                database.savePerformanceEvaluation(new AvaliacaoDesempenhoAnual(
                        current == null ? 0 : current.id(),
                        selectedStaff.id(),
                        year.getValue(),
                        evaluationDate.getValue(),
                        periodStart.getValue(),
                        periodEnd.getValue(),
                        evaluator.getValue().id(),
                        current == null ? "" : current.quantitative1(),
                        current == null ? "" : current.quantitative2(),
                        current == null ? "" : current.quantitative3(),
                        current == null ? "" : current.qualitative1(),
                        current == null ? "" : current.qualitative2(),
                        current == null ? "" : current.qualitative3(),
                        current == null ? "" : current.finalQuantitative(),
                        current == null ? "" : current.finalQualitative(),
                        comment1.getText(),
                        comment2.getText(),
                        comment3.getText(),
                        appreciation.getText(),
                        concordance.getValue(),
                        homologante.getValue() == null ? null : homologante.getValue().id()
                ));

                String evaluatorName = evaluator.getValue().name();
                for (PerformanceInputRow row : table.getItems()) {
                    if (row.score.get().isBlank()) continue;
                    database.upsertPerformanceScore(
                            selectedStaff.id(), row.id.get(), year.getValue(), trimester.getValue(),
                            Double.parseDouble(row.score.get().replace(",", ".")),
                            row.observation.get(), evaluatorName);
                }

                database.refreshEvaluationClassification(selectedStaff.id(), year.getValue());
                showToast("Ficha anual e avaliação do período guardadas com sucesso.");
                load.run();
            } catch (Exception ex) {
                showError("Não foi possível guardar a ficha anual", ex);
            }
        });

        Button clear = CotanIcons.button("Limpar lançamento", Feather.ROTATE_CCW, "button-outlined");
        clear.setOnAction(e -> {
            for (PerformanceInputRow row : table.getItems()) row.score.set("");
            recalcPerformance(table, result);
        });

        HBox actions = new HBox(10, save, clear);
        page.getChildren().addAll(heading, selectors, annualCard, resultCard, tableFill(table), actions);
        return page;
    }

    private HBox labelledMetric(String caption, Node value) {
        VBox box = new VBox(4, label(caption, "metric-caption"), value);
        HBox wrapper = new HBox(box);
        wrapper.setMinWidth(220);
        return wrapper;
    }

    private void recalcPerformance(AdvancedTableView<PerformanceInputRow> table, HBox result) {
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
            ((Label)b.getChildren().get(1)).setText(weights == 0 ? "—" : String.format(Locale.US,"%.1f",Math.round(avg * 10.0) / 10.0));
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

        Tab professorsTab = new Tab("Professores");
        professorsTab.setGraphic(CotanIcons.icon(Feather.USER, 15));
        Tab administrativeTab = new Tab("Administrativos");
        administrativeTab.setGraphic(CotanIcons.icon(Feather.BRIEFCASE, 15));

        TabPane tabs = new TabPane(professorsTab, administrativeTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.setSide(javafx.geometry.Side.TOP);
        tabs.setMaxWidth(Double.MAX_VALUE);

        Node professorsContent = buildPerformanceMapTable("PROFESSOR", trimester);
        Node administrativeContent = buildPerformanceMapTable("ADMINISTRATIVO", trimester);
        StackPane tabContent = new StackPane(professorsContent);
        VBox.setVgrow(tabContent, Priority.ALWAYS);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, previous, selected) -> {
            if (selected == professorsTab) {
                tabContent.getChildren().setAll(professorsContent);
            } else if (selected == administrativeTab) {
                tabContent.getChildren().setAll(administrativeContent);
            }
        });
        tabs.getSelectionModel().select(professorsTab);

        HBox tabsRow = new HBox(12, tabs, label("ANO LECTIVO 2026/2027", "text-muted"));
        tabsRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(tabs, Priority.ALWAYS);
        page.getChildren().addAll(heading, tabsRow, tabContent);
        return page;
    }

    private Node buildPerformanceMapTable(String type, int trimester) throws SQLException {
        VBox box = new VBox(12);
        box.getChildren().add(label(
                "Resultados do " + ordinalTrimester(trimester) + " • "
                        + ("PROFESSOR".equals(type) ? "Professores" : "Administrativos"),
                "card-title"));

        AdvancedTableView<PerformanceMapRow> table = new AdvancedTableView<>();
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
        table.setEntityName("resultado");
        table.setOnRefresh(this::refreshCurrentSection);
        Node tableNode = table.withSearchBar();
        box.getChildren().add(tableNode);
        VBox.setVgrow(tableNode,Priority.ALWAYS);

        Button evaluate=CotanIcons.button("Abrir avaliação deste período", Feather.CLIPBOARD, "accent-button", "accent");
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

        AdvancedTableView<PerformanceFinalRow> table=new AdvancedTableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        addColumn(table,"Código",115,PerformanceFinalRow::codeProperty);
        addColumn(table,"Nome",270,PerformanceFinalRow::nameProperty);
        addColumn(table,"1º Trim.",115,PerformanceFinalRow::t1Property);
        addColumn(table,"2º Trim.",115,PerformanceFinalRow::t2Property);
        addColumn(table,"3º Trim.",115,PerformanceFinalRow::t3Property);
        addColumn(table,"Média final",130,PerformanceFinalRow::finalProperty);
        addColumn(table,"Classificação",170,PerformanceFinalRow::classificationProperty);
        if ("PROFESSOR".equals(type)) {
            TableColumn<PerformanceFinalRow, Void> reportActions = actionColumn(table, row -> {
                Button print = CotanIcons.button("", Feather.EYE, "button-icon", "flat", "small");
                print.setTooltip(new Tooltip("Abrir ficha anual no JasperViewer"));
                print.setAccessibleText("Abrir ficha anual de " + row.name.get() + " no JasperViewer");
                print.setOnAction(event -> viewAnnualProfessorReport(row));

                Button docx = CotanIcons.button("", Feather.FILE_TEXT, "button-icon", "flat", "small");
                docx.setTooltip(new Tooltip("Exportar ficha anual para Word"));
                docx.setAccessibleText("Exportar ficha anual de " + row.name.get() + " para Word");
                docx.setOnAction(event -> exportAnnualProfessorReportDocx(row));
                return new HBox(4, print, docx);
            });
            reportActions.setText("Ficha anual");
            reportActions.setPrefWidth(104);
            table.getColumns().add(reportActions);
        }

        ObservableList<PerformanceFinalRow> rows=FXCollections.observableArrayList();
        for(Map<String,Object> r: database.performanceFinalMap(type,"2026/2027")) {
            PerformanceFinalRow row=PerformanceFinalRow.from(r);
            if(matches(search(),row.code.get(),row.name.get(),row.classification.get())) rows.add(row);
        }
        table.setItems(rows);

        Button export=CotanIcons.button("Exportar mapa final", Feather.DOWNLOAD, "accent-button", "accent");
        export.setOnAction(e -> exportPerformanceMap(type));

        page.getChildren().addAll(heading,summary,tableFill(table),export);
        return page;
    }

    private AvaliacaoProfessorRelatorio buildAnnualProfessorReport(PerformanceFinalRow row) throws SQLException {
        long staffId = row.id.get();
        String academicYear = "2026/2027";

        Map<String, Object> staff = database.staff("PROFESSOR").stream()
                .filter(candidate -> n(candidate.get("id")) == staffId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("O professor selecionado não foi encontrado."));

        InstitutionProfile institution = database.institutionProfileEntity();
        AvaliacaoDesempenhoAnual evaluation = database.performanceEvaluation(staffId, academicYear);

        if (evaluation == null) {
            throw new IllegalStateException(
                    "A ficha anual deste professor ainda não foi configurada. "
                            + "Abra a Avaliação de desempenho, selecione o professor e guarde a ficha anual."
            );
        }
        if (evaluation.evaluatorStaffId() == null) {
            throw new IllegalStateException(
                    "O avaliador da ficha anual ainda não foi definido. "
                            + "Defina-o na ficha anual ou em Configurações."
            );
        }

        final long evaluatorStaffId = evaluation.evaluatorStaffId();
        Map<String,Object> evaluator = database.staffAll().stream()
                .filter(candidate -> n(candidate.get("id")) == evaluatorStaffId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("O avaliador selecionado não existe mais no cadastro."));

        String finalQuantitative = evaluation.finalQuantitative();
        String finalQualitative = evaluation.finalQualitative();

        if (finalQuantitative.isBlank() || finalQualitative.isBlank()) {
            database.refreshEvaluationClassification(staffId, academicYear);
            evaluation = database.performanceEvaluation(staffId, academicYear);
            finalQuantitative = evaluation == null ? "" : evaluation.finalQuantitative();
            finalQualitative = evaluation == null ? "" : evaluation.finalQualitative();
        }

        List<Map<String,Object>> trimesterOne = database.performanceScores(staffId, academicYear, 1);
        List<Map<String,Object>> trimesterTwo = database.performanceScores(staffId, academicYear, 2);
        List<Map<String,Object>> trimesterThree = database.performanceScores(staffId, academicYear, 3);

        Map<Long, Map<String, Object>> firstScores = indexIndicatorScores(trimesterOne);
        Map<Long, Map<String, Object>> secondScores = indexIndicatorScores(trimesterTwo);
        Map<Long, Map<String, Object>> thirdScores = indexIndicatorScores(trimesterThree);

        List<AvaliacaoProfessorRelatorio.Indicador> indicators = new ArrayList<>();
        for (Map<String, Object> indicator : database.indicators("PROFESSOR")) {
            long indicatorId = n(indicator.get("id"));
            Number first = scoreFor(firstScores, indicatorId);
            Number second = scoreFor(secondScores, indicatorId);
            Number third = scoreFor(thirdScores, indicatorId);
            Number annualAverage = first != null && second != null && third != null
                    ? (first.doubleValue() + second.doubleValue() + third.doubleValue()) / 3.0
                    : null;

            indicators.add(new AvaliacaoProfessorRelatorio.Indicador(
                    indicators.size() + 1,
                    s(indicator.get("name")),
                    first, second, third, annualAverage
            ));
        }

        String homologanteName = "";
        if (evaluation.homologanteStaffId() != null) {
            final long homologanteStaffId = evaluation.homologanteStaffId();
            homologanteName = database.staffAll().stream()
                    .filter(candidate -> n(candidate.get("id")) == homologanteStaffId)
                    .map(candidate -> s(candidate.get("name")))
                    .findFirst()
                    .orElse("");
        }

        if (evaluation.evaluationDate() == null || evaluation.periodStart() == null || evaluation.periodEnd() == null) {
            throw new IllegalStateException(
                    "A ficha anual precisa de data de avaliação e período preenchidos antes da impressão."
            );
        }

        return new AvaliacaoProfessorRelatorio(
                institution.provincialOffice(),
                institution.municipalDirection(),
                institution.school(),
                s(staff.get("name")),
                s(staff.get("category")),
                s(staff.get("agent_number")),
                evaluation.evaluationDate(),
                evaluation.periodStart(),
                evaluation.periodEnd(),
                finalQuantitative,
                finalQualitative,
                evaluation.appreciationGeneral(),
                evaluation.comment1(),
                evaluation.comment2(),
                evaluation.comment3(),
                s(evaluator.get("name")),
                s(evaluator.get("role")),
                evaluation.evaluationDate(),
                s(staff.get("name")),
                evaluation.concordance(),
                homologanteName,
                indicators
        );
    }

    private String firstNonBlank(List<Map<String,Object>> rows, String key) {
        for (Map<String,Object> row : rows) {
            String value = s(row.get(key));
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private LocalDate latestDate(List<Map<String,Object>> rows, String key) {
        for (Map<String,Object> row : rows) {
            String value = s(row.get(key));
            if (value.length() >= 10) {
                try {
                    return LocalDate.parse(value.substring(0, 10));
                } catch (RuntimeException ignored) {
                    // Continua para a próxima data persistida.
                }
            }
        }
        return null;
    }

    private Map<Long, Map<String, Object>> indexIndicatorScores(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> indexed = new HashMap<>();
        for (Map<String, Object> score : rows) {
            indexed.put(n(score.get("indicator_id")), score);
        }
        return indexed;
    }

    private Number scoreFor(Map<Long, Map<String, Object>> scores, long indicatorId) {
        Object score = scores.getOrDefault(indicatorId, Map.of()).get("score");
        return score instanceof Number number ? number : null;
    }

    @SafeVarargs
    private final String findEvaluator(List<Map<String, Object>>... trimesterRows) {
        for (List<Map<String, Object>> rows : trimesterRows) {
            for (Map<String, Object> row : rows) {
                String evaluator = s(row.get("evaluator"));
                if (!evaluator.isBlank()) return evaluator;
            }
        }
        return "";
    }

    private String formatReportScore(String value) {
        try {
            double score = Double.parseDouble(value);
            return Math.rint(score) == score
                    ? String.format(Locale.US, "%.0f", score).replace('.', ',')
                    : String.format(Locale.US, "%.1f", score).replace('.', ',');
        } catch (RuntimeException ex) {
            return "";
        }
    }

    private void viewAnnualProfessorReport(PerformanceFinalRow row) {
        try {
            new AvaliacaoProfessorReportService().showViewer(
                    buildAnnualProfessorReport(row),
                    "COTAN — Ficha de Avaliação de Desempenho Anual — " + row.name.get()
            );
            showToast("Ficha anual aberta no JasperViewer.");
        } catch (Exception ex) {
            showError("Não foi possível abrir a ficha anual no JasperViewer", ex);
        }
    }

    private void exportAnnualProfessorReportDocx(PerformanceFinalRow row) {
        try {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Guardar ficha anual em Word");
            chooser.setInitialFileName("ficha-anual-" + safeFileName(row.code.get()) + ".docx");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Word", "*.docx"));
            File selected = chooser.showSaveDialog(stage);
            if (selected == null) return;

            new AvaliacaoProfessorReportService().generateDocx(
                    selected.toPath(), buildAnnualProfessorReport(row));
            showToast("Ficha anual exportada em Word.");
        } catch (Exception ex) {
            showError("Não foi possível exportar a ficha anual em Word", ex);
        }
    }

    private String safeFileName(String value) {
        return value == null ? "professor" : value.replaceAll("[^A-Za-z0-9_-]", "-");
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

        Button evaluation=CotanIcons.button("Abrir lançamento de avaliação", Feather.CLIPBOARD, "accent-button", "accent");
        evaluation.setOnAction(e -> showSection("PROFESSOR".equals(type) ? "professor-evaluation" : "administrative-evaluation"));

        Button finalMap=CotanIcons.button("Abrir mapa final", Feather.AWARD, "button-outlined");
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
        if (!Double.isFinite(score)) return "Sem avaliação";
        if (score <= 0) return "Sem avaliação";
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
                   .append(csv(r.get("t1_average"))).append(';')
                   .append(csv(r.get("t2_average"))).append(';')
                   .append(csv(r.get("t3_average"))).append(';')
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
        final SimpleStringProperty code,name,category,agentNumber,role,department,phone,email,admission;
        StaffRow(long id,String code,String name,String category,String agentNumber,String role,String department,String phone,String email,String admission){
            this.id=new SimpleLongProperty(id);
            this.code=new SimpleStringProperty(code);
            this.name=new SimpleStringProperty(name);
            this.category=new SimpleStringProperty(category);
            this.agentNumber=new SimpleStringProperty(agentNumber);
            this.role=new SimpleStringProperty(role);
            this.department=new SimpleStringProperty(department);
            this.phone=new SimpleStringProperty(phone);
            this.email=new SimpleStringProperty(email);
            this.admission=new SimpleStringProperty(admission);
        }
        static StaffRow from(Map<String,Object> r){
            return new StaffRow(
                n(r.get("id")),s(r.get("code")),s(r.get("name")),s(r.get("category")),
                s(r.get("agent_number")),s(r.get("role")),s(r.get("department")),
                s(r.get("phone")),s(r.get("email")),s(r.get("admission_date")));
        }
        SimpleStringProperty codeProperty(){return code;}
        SimpleStringProperty nameProperty(){return name;}
        SimpleStringProperty categoryProperty(){return category;}
        SimpleStringProperty agentNumberProperty(){return agentNumber;}
        SimpleStringProperty roleProperty(){return role;}
        SimpleStringProperty departmentProperty(){return department;}
        SimpleStringProperty phoneProperty(){return phone;}
        SimpleStringProperty emailProperty(){return email;}
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
        static PerformanceFinalRow from(Map<String,Object> r){return new PerformanceFinalRow(n(r.get("id")),s(r.get("code")),s(r.get("name")),s(r.get("t1_average")),s(r.get("t2_average")),s(r.get("t3_average")),s(r.get("final_average")));}
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

        AdvancedTableView<TeacherRow> table = new AdvancedTableView<>();
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
        CotanModal dialog = dialog(existing == null ? "Novo professor" : "Editar professor");
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

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateTeacher(name.getText(), specialty.getText(), phone.getText(), email.getText());
            if (validation != null) {
                showWarning(validation);
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

        AdvancedTableView<ClassRow> table = new AdvancedTableView<>();
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
        CotanModal dialog = dialog(existing == null ? "Nova turma" : "Editar turma");
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

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateClass(name.getText(), year.getText(), shift.getValue(),
                    room.getText(), coordinator.getText());
            if (validation != null) {
                showWarning(validation);
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

        AdvancedTableView<SubjectRow> table = new AdvancedTableView<>();
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
        CotanModal dialog = dialog(existing == null ? "Nova disciplina" : "Editar disciplina");
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

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateSubject(name.getText(), code.getText(), workload.getValue(), weight.getValue(),
                    existing == null ? null : existing.id.get());
            if (validation != null) {
                showWarning(validation);
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

        AdvancedTableView<AssessmentRow> table = new AdvancedTableView<>();
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
        CotanModal dialog = dialog(existing == null ? "Nova avaliação" : "Editar avaliação");
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

        dialog.setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return btn;
            String validation = validateAssessment(title.getText(), type.getValue(), term.getValue(), date.getValue(),
                    maxScore.getValue(), weight.getValue(), clazz.getValue(), subject.getValue(), teacher.getValue());
            if (validation != null) {
                showWarning(validation);
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
            setContentPage(buildGradesWithAssessment(row.id.get()));
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

        Button load = CotanIcons.button("Carregar alunos", Feather.REFRESH_CW, "accent-button", "accent");

        Label hint = new Label("Escala 0–20 por padrão; a nota máxima da avaliação é respeitada.");
        hint.getStyleClass().add("text-muted");

        controls.getChildren().addAll(new Label("Avaliação"), assessment, load, hint);

        AdvancedTableView<GradeRow> table = new AdvancedTableView<>();
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
                AssessmentOption selectedAssessment = assessment.getValue();
                double maxAllowed = selectedAssessment == null ? 20 : selectedAssessment.maxScore();
                if (n < 0 || n > maxAllowed) throw new NumberFormatException();
                e.getRowValue().scoreProperty().set(String.format(Locale.US, "%.2f", n));
            } catch (NumberFormatException ex) {
                AssessmentOption selectedAssessment = assessment.getValue();
                double maxAllowed = selectedAssessment == null ? 20 : selectedAssessment.maxScore();
                if (isNumeric(value)) {
                    showWarning("A nota deve estar entre 0 e " + trim(maxAllowed) + ".");
                } else {
                    showWarning("Digite uma nota numérica válida.");
                }
                table.refresh();
            }
        });

        TableColumn<GradeRow, String> observation = new TableColumn<>("Observação");
        observation.setPrefWidth(340);
        observation.setCellValueFactory(c -> c.getValue().observationProperty());
        table.getColumns().addAll(score, observation);

        Label total = label("0 aluno(s) carregados", "muted");
        Button saveAll = CotanIcons.button("Guardar notas", Feather.SAVE, "accent-button", "accent");

        Button clear = CotanIcons.button("Limpar lançamentos", Feather.ROTATE_CCW, "button-outlined");
        clear.setOnAction(e -> {
            for (GradeRow r : table.getItems()) r.scoreProperty().set("");
        });

        HBox footer = new HBox(10, total, new Region(), clear, saveAll);
        HBox.setHgrow(footer.getChildren().get(1), Priority.ALWAYS);
        footer.setAlignment(Pos.CENTER_LEFT);
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
        Button export = CotanIcons.button("Exportar CSV", Feather.DOWNLOAD, "accent-button", "accent");
        export.setOnAction(e -> exportPerformance());
        Button refresh = CotanIcons.button("Atualizar", Feather.REFRESH_CW, "button-outlined");
        refresh.setOnAction(e -> refreshCurrentSection());
        tools.getChildren().addAll(export, refresh);
        heading.getChildren().add(tools);

        AdvancedTableView<PerformanceRow> table = new AdvancedTableView<>();
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

    private Node buildSettings() throws SQLException {
        VBox page = pageContainer();

        page.getChildren().add(sectionHeading(
                "Configurações",
                "Defina uma única vez os dados institucionais e os responsáveis que serão reutilizados nas fichas."
        ));

        Card reportIdentity = new Card();
        reportIdentity.setHeader(label("Dados institucionais da ficha anual", "card-title"));

        GridPane reportForm = formGrid();
        TextField provincial = field("Ex.: GABINETE PROVINCIAL DE EDUCAÇÃO DE LUANDA");
        TextField municipal = field("Ex.: DIRECÇÃO MUNICIPAL DE EDUCAÇÃO DE LUANDA");
        TextField school = field("Ex.: ESCOLA PRIMÁRIA Nº 1118 – MAIANGA");
        ComboBox<StaffOption> defaultEvaluator = new ComboBox<>();
        ComboBox<StaffOption> defaultHomologante = new ComboBox<>();
        defaultEvaluator.setMaxWidth(Double.MAX_VALUE);
        defaultHomologante.setMaxWidth(Double.MAX_VALUE);

        InstitutionProfile profile = database.institutionProfileEntity();
        provincial.setText(profile.provincialOffice());
        municipal.setText(profile.municipalDirection());
        school.setText(profile.school());

        List<Map<String,Object>> staffRows = database.staffAll();
        for (Map<String,Object> row : staffRows) {
            StaffOption option = StaffOption.from(row);
            defaultEvaluator.getItems().add(option);
            defaultHomologante.getItems().add(option);
        }

        if (profile.defaultEvaluatorStaffId() != null) {
            defaultEvaluator.getItems().stream()
                    .filter(v -> v.id() == profile.defaultEvaluatorStaffId())
                    .findFirst().ifPresent(defaultEvaluator::setValue);
        }
        if (profile.defaultHomologanteStaffId() != null) {
            defaultHomologante.getItems().stream()
                    .filter(v -> v.id() == profile.defaultHomologanteStaffId())
                    .findFirst().ifPresent(defaultHomologante::setValue);
        }

        reportForm.addRow(0, label("Gabinete Provincial", "field-label"), provincial);
        reportForm.addRow(1, label("Direcção Municipal", "field-label"), municipal);
        reportForm.addRow(2, label("Escola", "field-label"), school);
        reportForm.addRow(3, label("Avaliador padrão", "field-label"), defaultEvaluator);
        reportForm.addRow(4, label("Homologante padrão", "field-label"), defaultHomologante);

        Label hint = label(
                "O avaliador e o homologante são selecionados a partir do cadastro de profissionais. "
                        + "O nome e a função nunca são digitados dentro do relatório.",
                "muted"
        );
        hint.setWrapText(true);

        Button saveInstitution = CotanIcons.button(
                "Guardar dados institucionais", Feather.SAVE, "accent-button", "accent"
        );
        saveInstitution.setOnAction(e -> {
            try {
                database.saveInstitutionProfile(
                        provincial.getText(),
                        municipal.getText(),
                        school.getText(),
                        defaultEvaluator.getValue() == null ? null : defaultEvaluator.getValue().id(),
                        defaultHomologante.getValue() == null ? null : defaultHomologante.getValue().id()
                );
                refreshCurrentSection();
                showToast("Dados institucionais guardados com sucesso.");
            } catch (Exception ex) {
                showError("Não foi possível guardar os dados institucionais", ex);
            }
        });

        reportIdentity.setBody(new VBox(12, reportForm, hint, saveInstitution));

        Card appearance = new Card();
        appearance.setHeader(label("Aparência", "card-title"));
        ToggleSwitch dark = new ToggleSwitch("Modo escuro");
        dark.setSelected(darkMode);
        dark.selectedProperty().addListener((obs, oldValue, selected) -> {
            darkMode = selected;
            Application.setUserAgentStylesheet(darkMode ? DARK_THEME : LIGHT_THEME);
        });
        appearance.setBody(dark);

        Card databaseCard = new Card();
        databaseCard.setHeader(label("Banco de dados", "card-title"));
        VBox databaseDetails = new VBox(8,
            label(database.getDatabasePath().toString(), "muted"),
            label("SQLite • persistência local • WAL • chaves estrangeiras ativas", "muted")
        );

        HBox dbActions = new HBox(10);
        Button backup = CotanIcons.button("Criar backup", Feather.DATABASE, "accent-button", "accent");
        backup.setOnAction(e -> createBackup());
        Button seedInfo = CotanIcons.button("Dados de demonstração", Feather.INFO, "button-outlined");
        seedInfo.setOnAction(e -> showInfo(
                "O sistema cria automaticamente alguns registos de demonstração apenas quando a base está vazia."
        ));
        dbActions.getChildren().addAll(backup, seedInfo);
        databaseDetails.getChildren().add(dbActions);
        databaseCard.setBody(databaseDetails);

        Card rules = new Card();
        rules.setHeader(label("Regras académicas atuais", "card-title"));
        rules.setBody(label(
                "Escala configurável por avaliação\n"
                        + "Média ponderada pelo peso\n"
                        + "Referência de aprovação: 10 valores\n"
                        + "Notas vinculadas a aluno e avaliação\n"
                        + "Ficha anual vinculada ao profissional e ano lectivo\n"
                        + "Eliminação em cascata",
                "muted"
        ));

        page.getChildren().addAll(reportIdentity, appearance, databaseCard, rules);
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
        Card hero = new Card();
        hero.setHeader(label("COTAN", "eyebrow"));
        hero.setBody(new VBox(8,
            label("Sistema de Avaliação e Desempenho", "hero-title"),
            label("Gestão académica, lançamento de avaliações e análise de desempenho.", "muted")
        ));

        GridPane tech = new GridPane();
        tech.setHgap(18);
        tech.setVgap(12);
        tech.add(infoPill("Java", "21"), 0, 0);
        tech.add(infoPill("JavaFX", "21"), 1, 0);
        tech.add(infoPill("AtlantaFX", "3.0.0"), 2, 0);
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
        VBox box = new VBox(22);
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

    private CotanMetricCard statCard(String title, String value, String note, String icon) {
        Feather metricIcon = switch (icon) {
            case "✓" -> Feather.CHECK_CIRCLE;
            case "★" -> Feather.BAR_CHART_2;
            default -> Feather.USERS;
        };
        return new CotanMetricCard(title, value, note, metricIcon, "✓".equals(icon) ? "success" : "accent");
    }

    private Tile actionCard(String title, String text, Runnable action) {
        Tile tile = new Tile(title, text, CotanIcons.icon(Feather.ARROW_RIGHT, 16));
        tile.setActionHandler(action);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    private Tile infoPill(String title, String value) {
        return new Tile(title, value, CotanIcons.icon(Feather.CHECK, 15));
    }

    private VBox card() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14, 0, 14, 0));
        return box;
    }

    private Node tableFill(TableView<?> table) {
        styleTable(table);

        Node tableNode = table;

        if (table instanceof AdvancedTableView<?> advanced) {
            @SuppressWarnings("unchecked")
            AdvancedTableView<Object> advancedTable = (AdvancedTableView<Object>) advanced;
            advancedTable.setEntityName("registo");
            advancedTable.setOnRefresh(this::refreshCurrentSection);
            tableNode = advancedTable.withSearchBar();
        }

        VBox.setVgrow(tableNode, Priority.ALWAYS);
        Card holder = new Card();
        holder.setBody(tableNode);
        VBox.setVgrow(holder, Priority.ALWAYS);
        return holder;
    }

    private <T> void styleTable(TableView<T> table) {
        if (table instanceof AdvancedTableView<?>) {
            TableUtils.standardize(table);
        } else {
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        }

        table.setPlaceholder(label("Nenhum registo encontrado.", "table-empty"));
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        if (!(table instanceof AdvancedTableView<?>)) {
            table.setFixedCellSize(-1);
        }
        table.setPrefHeight(420);

        if (!(table instanceof AdvancedTableView<?>)) {
            table.setRowFactory((TableView<T> tv) -> {
                TableRow<T> row = new TableRow<>();
                row.itemProperty().addListener((obs, oldItem, newItem) -> {
                    row.pseudoClassStateChanged(
                            javafx.css.PseudoClass.getPseudoClass("row-empty"),
                            newItem == null
                    );
                });
                return row;
            });
        }
    }

    private Label label(String text, String style) {
        Label l = new Label(text);
        String variant = switch (style) {
            case "page-title", "hero-title" -> "title-1";
            case "section-title" -> "title-2";
            case "card-title", "home-card-title", "modal-header-title", "modal-title", "badge-value" -> "title-3";
            case "result-number", "stat-value" -> "title-2";
            case "muted", "hero-subtitle", "badge-detail", "metric-note", "table-empty", "modal-message" -> "text-muted";
            case "hero-eyebrow", "eyebrow", "field-label", "badge-caption", "modal-eyebrow", "metric-title", "stat-label", "process-flow", "modal-detail" -> "text-small";
            case "result-badge" -> "success";
            case "home-action" -> "accent";
            default -> null;
        };
        if (variant != null) {
            l.getStyleClass().add(variant);
        }
        return l;
    }

    private TextField field(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        field.getStyleClass().add("large");
        return field;
    }

    @SafeVarargs
    private final <T> ComboBox<T> combo(T... items) {
        ComboBox<T> c = new ComboBox<>(FXCollections.observableArrayList(items));
        c.setMaxWidth(Double.MAX_VALUE);
        c.getStyleClass().add("large");
        return c;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.setPadding(new Insets(6, 0, 10, 0));

        ColumnConstraints left = new ColumnConstraints();
        left.setMinWidth(145);
        left.setPrefWidth(150);

        ColumnConstraints right = new ColumnConstraints();
        right.setMinWidth(260);
        right.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(left, right);
        return grid;
    }

    private CotanModal dialog(String title) {
        return new CotanModal(title);
    }

    private void openModal(CotanModal modal) {
        if (modalHost == null) {
            return;
        }

        Node current = modalHost.getContent();
        if (current != null && current != modal.getRootBox()) {
            modalHistory.push(current);
        }
        modal.open(modalHost);
    }

    private void closeTopModal() {
        if (modalHost == null) return;
        if (!modalHistory.isEmpty()) {
            modalHost.show(modalHistory.pop());
        } else {
            modalHost.hide(true);
        }
    }

    private void closeModal(CotanModal modal) {
        if (modalHost == null) {
            return;
        }

        if (modalHost.getContent() != modal.getRootBox()) {
            return;
        }

        closeTopModal();
    }

    private <T> void addColumn(AdvancedTableView<T> table, String title, double width,
                               java.util.function.Function<T, javafx.beans.value.ObservableValue<String>> value) {
        TableColumn<T,String> c = new TableColumn<>(title);
        c.setPrefWidth(width);
        c.setCellValueFactory(cell -> value.apply(cell.getValue()));
        table.getColumns().add(c);
    }

    private Button miniButton(String action) {
        Feather icon = switch (action) {
            case "Avaliar" -> Feather.CLIPBOARD;
            case "Notas" -> Feather.FILE_TEXT;
            default -> Feather.EDIT_2;
        };

        Button b = new Button("", CotanIcons.icon(icon, 15));
        b.getStyleClass().addAll("flat", "small");
        b.setTooltip(new Tooltip(action));
        b.setAccessibleText(action);
        b.setFocusTraversable(true);
        return b;
    }

    private Button miniDangerButton(String action) {
        Button b = new Button("", CotanIcons.icon(Feather.TRASH_2, 15));
        b.getStyleClass().addAll("danger", "flat", "small");
        b.setTooltip(new Tooltip(action));
        b.setAccessibleText(action);
        b.setFocusTraversable(true);
        return b;
    }

    private String search() {
        return header == null ? "" : header.getSearchText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean matches(String query, String... values) {
        if (query.isBlank()) return true;
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(v -> v.toLowerCase(Locale.ROOT))
                .anyMatch(v -> v.contains(query));
    }

    private String validateIndicator(String code, String name, String staffType, double weight, Long existingId) {
        List<String> e = new ArrayList<>();
        String normalizedCode = safe(code).trim().toUpperCase(Locale.ROOT);
        String normalizedName = safe(name).trim();
        if (!normalizedCode.matches("IND-\\d{2,3}")) e.add("Código do indicador: use o formato IND-01.");
        if (normalizedName.length() < 3 || normalizedName.length() > 120) e.add("Nome do indicador: entre 3 e 120 caracteres.");
        if (staffType == null || staffType.isBlank()) e.add("Selecione a aplicação do indicador.");
        if (!Double.isFinite(weight) || weight <= 0 || weight > 10) e.add("Peso: valor entre 0,1 e 10.");
        if (e.isEmpty()) {
            try {
                Object duplicate = database.scalar(
                        "SELECT COUNT(*) FROM performance_indicators WHERE code=? AND (? IS NULL OR id<>?)",
                        normalizedCode, existingId, existingId);
                if (duplicate instanceof Number n && n.intValue() > 0) e.add("Já existe um indicador com esse código.");
            } catch (SQLException ex) {
                e.add("Não foi possível validar a unicidade do código.");
            }
        }
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private String validateStudent(String number, String name, String gender, LocalDate birth,
                                   String phone, String guardian, Long existingId) {
        List<String> e = new ArrayList<>();
        String n = safe(number).trim();
        String nm = safe(name).trim();
        if (!n.matches("[A-Za-z0-9][A-Za-z0-9\\-]{2,19}")) e.add("Número do aluno: use 3–20 caracteres alfanuméricos.");
        if (nm.length() < 3 || nm.length() > 120) e.add("Nome do aluno: entre 3 e 120 caracteres.");
        if (gender != null && gender.isBlank()) e.add("Género inválido.");
        if (birth != null && birth.isAfter(LocalDate.now())) e.add("A data de nascimento não pode estar no futuro.");
        if (!validPhone(phone)) e.add("Contacto inválido. Use 9XXXXXXXX ou +244 9XXXXXXXX.");
        if (safe(guardian).trim().length() > 120) e.add("Nome do encarregado: máximo 120 caracteres.");
        if (e.isEmpty()) {
            try {
                Object duplicate = database.scalar(
                        "SELECT COUNT(*) FROM students WHERE student_number=? AND (? IS NULL OR id<>?)",
                        n, existingId, existingId);
                if (duplicate instanceof Number count && count.intValue() > 0) {
                    e.add("Já existe um aluno com este número.");
                }
            } catch (SQLException ex) {
                e.add("Não foi possível validar o número do aluno.");
            }
        }
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private String validateStaff(String type, String code, String name, String category,
                                 String agentNumber, String role, String department,
                                 String phone, String email, LocalDate admission, Long existingId) {
        List<String> e = new ArrayList<>();
        String c = safe(code).trim().toUpperCase(Locale.ROOT);
        String expected = "PROFESSOR".equals(type) ? "PROF" : "ADM";
        if (!c.matches(expected + "-\\d{3}")) e.add("Código inválido. Use " + expected + "-001, " + expected + "-002, etc.");
        if (safe(name).trim().length() < 3 || safe(name).trim().length() > 120) e.add("Nome: entre 3 e 120 caracteres.");
        if (safe(category).trim().length() > 160) e.add("Categoria/carreira: máximo 160 caracteres.");
        if (safe(agentNumber).trim().length() > 40) e.add("Agente nº: máximo 40 caracteres.");
        if (safe(role).trim().length() > 100) e.add("Cargo/função: máximo 100 caracteres.");
        if (safe(department).trim().length() > 100) e.add("Departamento: máximo 100 caracteres.");
        if (!validPhone(phone)) e.add("Telefone inválido. Use 9XXXXXXXX ou +244 9XXXXXXXX.");
        if (!validEmail(email)) e.add("E-mail inválido.");
        if (admission != null && admission.isAfter(LocalDate.now())) e.add("A data de admissão não pode estar no futuro.");
        if (e.isEmpty()) {
            try {
                Object duplicate = database.scalar(
                        "SELECT COUNT(*) FROM staff WHERE code=? AND (? IS NULL OR id<>?)",
                        c, existingId, existingId);
                if (duplicate instanceof Number n && n.intValue() > 0) e.add("Já existe um registo com este código.");

                String agent = safe(agentNumber).trim();
                if (!agent.isBlank()) {
                    Object agentDuplicate = database.scalar(
                            "SELECT COUNT(*) FROM staff WHERE agent_number=? AND (? IS NULL OR id<>?)",
                            agent, existingId, existingId);
                    if (agentDuplicate instanceof Number n && n.intValue() > 0) {
                        e.add("Já existe um profissional com este número de agente.");
                    }
                }
            } catch (SQLException ex) {
                e.add("Não foi possível validar a unicidade do código.");
            }
        }
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private String validateTeacher(String name, String specialty, String phone, String email) {
        List<String> e = new ArrayList<>();
        if (safe(name).trim().length() < 3 || safe(name).trim().length() > 120) e.add("Nome: entre 3 e 120 caracteres.");
        if (safe(specialty).trim().length() > 100) e.add("Especialidade: máximo 100 caracteres.");
        if (!validPhone(phone)) e.add("Telefone inválido. Use 9XXXXXXXX ou +244 9XXXXXXXX.");
        if (!validEmail(email)) e.add("E-mail inválido.");
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private String validateClass(String name, String year, String shift, String room, String coordinator) {
        List<String> e = new ArrayList<>();
        if (safe(name).trim().length() < 2 || safe(name).trim().length() > 60) e.add("Turma: entre 2 e 60 caracteres.");
        if (!validAcademicYear(year)) e.add("Ano lectivo inválido. Use o formato 2026/2027.");
        if (shift == null || shift.isBlank()) e.add("Selecione o turno.");
        if (safe(room).trim().length() > 50) e.add("Sala: máximo 50 caracteres.");
        if (safe(coordinator).trim().length() > 120) e.add("Coordenador: máximo 120 caracteres.");
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private String validateSubject(String name, String code, int workload, double weight, Long existingId) {
        List<String> e = new ArrayList<>();
        String c = safe(code).trim().toUpperCase(Locale.ROOT);
        if (safe(name).trim().length() < 2 || safe(name).trim().length() > 120) e.add("Disciplina: entre 2 e 120 caracteres.");
        if (!c.matches("[A-Z0-9]{2,10}")) e.add("Código da disciplina: 2–10 caracteres, sem espaços.");
        if (workload < 1 || workload > 20) e.add("Carga horária: entre 1 e 20.");
        if (!Double.isFinite(weight) || weight <= 0 || weight > 10) e.add("Peso: valor entre 0,1 e 10.");
        if (e.isEmpty()) {
            try {
                Object duplicate = database.scalar(
                        "SELECT COUNT(*) FROM subjects WHERE code=? AND (? IS NULL OR id<>?)",
                        c, existingId, existingId);
                if (duplicate instanceof Number n && n.intValue() > 0) e.add("Já existe uma disciplina com esse código.");
            } catch (SQLException ex) {
                e.add("Não foi possível validar a unicidade do código.");
            }
        }
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private String validateAssessment(String title, String type, String term, LocalDate date, double maxScore,
                                      double weight, ClassOption clazz, SubjectOption subject, TeacherOption teacher) {
        List<String> e = new ArrayList<>();
        if (safe(title).trim().length() < 3 || safe(title).trim().length() > 120) e.add("Título: entre 3 e 120 caracteres.");
        if (type == null || type.isBlank()) e.add("Selecione o tipo de avaliação.");
        if (term == null || term.isBlank()) e.add("Selecione o período.");
        if (date != null && date.isBefore(LocalDate.of(2000,1,1))) e.add("Data da avaliação inválida.");
        if (!Double.isFinite(maxScore) || maxScore <= 0 || maxScore > 100) e.add("Nota máxima: entre 1 e 100.");
        if (!Double.isFinite(weight) || weight <= 0 || weight > 10) e.add("Peso: valor entre 0,1 e 10.");
        if (subject == null) e.add("Selecione a disciplina.");
        return e.isEmpty() ? null : String.join("\n", e);
    }

    private boolean validPhone(String phone) {
        String p = safe(phone).trim().replace(" ", "");
        if (p.isBlank()) return true;
        return PHONE_PATTERN.matcher(p).matches();
    }

    private boolean validEmail(String email) {
        String v = safe(email).trim();
        if (v.isBlank()) return true;
        return EMAIL_PATTERN.matcher(v).matches();
    }

    private boolean validAcademicYear(String year) {
        String y = safe(year).trim();
        if (!ACADEMIC_YEAR_PATTERN.matcher(y).matches()) return false;
        try {
            int start = Integer.parseInt(y.substring(0,4));
            int end = Integer.parseInt(y.substring(5));
            return end == start + 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean isNumeric(String value) {
        try {
            Double.parseDouble(safe(value).replace(",", "."));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String csv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private void confirmDelete(String what, Runnable action) {
        Message body = new Message(
            "Eliminar " + what + "?",
            "Esta ação pode afetar dados relacionados. Deseja continuar?",
            CotanIcons.icon(Feather.TRASH_2, 18)
        );
        body.getStyleClass().add("danger");

        CotanModal modal = new CotanModal("Confirmar eliminação");
        modal.setContent(body);
        modal.setButtons(ButtonType.CANCEL, ButtonType.OK);
        modal.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    action.run();
                    refreshCurrentSection();
                    showToast("Registo eliminado.");
                } catch (Exception ex) {
                    showError("Não foi possível eliminar o registo", ex);
                    return null;
                }
            }
            return btn;
        });
        modal.showAndWait();
    }

    private void showToast(String text) {
        dbStatus.setText("●  " + text);
        dbStatus.getStyleClass().remove("danger");
        if (!dbStatus.getStyleClass().contains("success")) dbStatus.getStyleClass().add("success");
    }

    private void showWarning(String message) {
        Message body = new Message("Atenção", message, CotanIcons.icon(Feather.ALERT_TRIANGLE, 18));
        body.getStyleClass().add("accent");

        CotanModal modal = new CotanModal("COTAN");
        modal.setContent(body);
        modal.setButtons(ButtonType.OK);
        modal.showAndWait();
    }

    private void showInfo(String message) {
        Message body = new Message("Informação", message, CotanIcons.icon(Feather.INFO, 18));
        body.getStyleClass().add("accent");

        CotanModal modal = new CotanModal("COTAN");
        modal.setContent(body);
        modal.setButtons(ButtonType.OK);
        modal.showAndWait();
    }

    private void showError(String message, Throwable ex) {
        logTerminal(message == null ? "Erro na aplicação" : message, ex);
        if (dbStatus != null) {
            dbStatus.setText("●  " + message);
            dbStatus.getStyleClass().remove("success");
            dbStatus.getStyleClass().add("danger");
        }

        String detail = ex == null ? "" : (ex.getMessage() == null ? ex.toString() : ex.getMessage());
        String visibleError = detail.isBlank() ? message : message + "\n" + detail;
        String technicalError = buildTechnicalError(message, ex);

        Message body = new Message(
            "Não foi possível concluir a operação.",
            visibleError,
            CotanIcons.icon(Feather.ALERT_CIRCLE, 18)
        );
        body.getStyleClass().add("danger");

        CotanModal modal = new CotanModal("COTAN • Erro");
        modal.setContent(body);
        modal.setButtons(COPY_ERROR_BUTTON, ButtonType.OK);
        modal.setResultConverter(type -> {
            if (type == COPY_ERROR_BUTTON) {
                copyErrorToClipboard(technicalError);
                showToast("Erro copiado para a área de transferência.");
                return null;
            }
            return type;
        });
        modal.showAndWait();
    }

    private String buildTechnicalError(String message, Throwable ex) {
        StringBuilder error = new StringBuilder();
        error.append("COTAN — Erro").append(System.lineSeparator());
        error.append("Operação: ").append(message == null ? "" : message).append(System.lineSeparator());

        if (ex == null) {
            error.append("Detalhes: sem exceção técnica disponível.");
            return error.toString();
        }

        error.append("Exceção: ").append(ex.getClass().getName()).append(System.lineSeparator());
        error.append("Mensagem: ")
                .append(ex.getMessage() == null ? ex.toString() : ex.getMessage())
                .append(System.lineSeparator());

        Throwable cause = ex.getCause();
        int level = 1;
        while (cause != null && level <= 5) {
            error.append("Causa ").append(level).append(": ")
                    .append(cause.getClass().getName())
                    .append(" — ")
                    .append(cause.getMessage() == null ? cause.toString() : cause.getMessage())
                    .append(System.lineSeparator());
            cause = cause.getCause();
            level++;
        }

        error.append(System.lineSeparator()).append("Stack trace:").append(System.lineSeparator());
        for (StackTraceElement element : ex.getStackTrace()) {
            error.append("    at ").append(element).append(System.lineSeparator());
        }

        return error.toString().trim();
    }

    private void copyErrorToClipboard(String errorText) {
        ClipboardContent content = new ClipboardContent();
        content.putString(errorText == null ? "" : errorText);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private void shutdown() {
        if (footerClockTimeline != null) footerClockTimeline.stop();
        if (database != null) database.close();
        if (springContext != null) springContext.close();
        Platform.exit();
    }

    private static final ButtonType COPY_ERROR_BUTTON =
            new ButtonType("Copiar erro", ButtonBar.ButtonData.OTHER);

    private final class CotanModal {
        private final String title;
        private final Card rootBox = new Card();
        private final VBox header = new VBox(8);
        private final HBox titleLine = new HBox(12);
        private final VBox titleArea = new VBox(3);
        private final VBox body = new VBox(14);
        private final ScrollPane bodyScroll = new ScrollPane(body);
        private final HBox footer = new HBox(8);
        private final CotanDialogPane dialogPane = new CotanDialogPane();
        private Function<ButtonType, ButtonType> resultConverter;

        CotanModal(String title) {
            this.title = title;

            rootBox.getStyleClass().add("modal-box");
            rootBox.setPrefWidth(680);
            rootBox.setMinWidth(520);
            rootBox.setMaxWidth(780);
            rootBox.setMaxHeight(700);

            titleArea.getChildren().addAll(
                    label("COTAN", "modal-eyebrow"),
                    label(title, "modal-header-title")
            );

            Button close = CotanIcons.button("", Feather.X, "modal-close-button", "button-outlined", "small");
            close.setAccessibleText("Fechar");
            close.setOnAction(e -> closeModal(this));

            HBox.setHgrow(titleArea, Priority.ALWAYS);
            titleLine.setAlignment(Pos.CENTER_LEFT);
            titleLine.getChildren().addAll(titleArea, close);

            header.setPadding(new Insets(18, 20, 10, 20));
            header.getChildren().add(titleLine);

            dialogPane.setContent(body);

            bodyScroll.setFitToWidth(true);
            bodyScroll.setFitToHeight(false);
            bodyScroll.setPrefViewportHeight(460);
            bodyScroll.setHbarPolicy(ScrollBarPolicy.NEVER);
            bodyScroll.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
            bodyScroll.setPadding(new Insets(0, 20, 12, 20));

            footer.setAlignment(Pos.CENTER_RIGHT);
            footer.setPadding(new Insets(12, 20, 16, 20));

            VBox.setVgrow(bodyScroll, Priority.ALWAYS);
            rootBox.setHeader(header);
            rootBox.setBody(bodyScroll);
            rootBox.setFooter(footer);

            dialogPane.getButtonTypes().addListener(
                    (javafx.collections.ListChangeListener<ButtonType>) change -> Platform.runLater(this::rebuildFooter)
            );
        }

        CotanDialogPane getDialogPane() {
            return dialogPane;
        }

        Node getRootBox() {
            return rootBox;
        }

        void setContent(Node node) {
            body.getChildren().setAll(node);
        }

        void setButtons(ButtonType... buttons) {
            dialogPane.getButtonTypes().setAll(buttons);
        }

        void setResultConverter(Function<ButtonType, ButtonType> converter) {
            this.resultConverter = converter;
            rebuildFooter();
        }

        void showAndWait() {
            openModal(this);
        }

        void open(CotanModalHost host) {
            rebuildFooter();

            rootBox.setOpacity(1);
            rootBox.setScaleX(1);
            rootBox.setScaleY(1);

            host.show(rootBox);
        }

        private void rebuildFooter() {
            if (footer == null) return;
            footer.getChildren().clear();

            if (dialogPane.getButtonTypes().isEmpty()) {
                Button close = CotanIcons.button("Fechar", Feather.X, "button-outlined", "small");
                close.setOnAction(e -> closeModal(this));
                footer.getChildren().add(close);
                return;
            }

            for (ButtonType type : dialogPane.getButtonTypes()) {
                Button button = CotanIcons.button(
                        buttonText(type),
                        type == COPY_ERROR_BUTTON ? Feather.COPY
                                : (type == ButtonType.OK ? Feather.CHECK : Feather.X)
                );

                if (type == COPY_ERROR_BUTTON) {
                    button.getStyleClass().addAll("button-outlined", "copy-error-button");
                } else if (type == ButtonType.OK) {
                    button.getStyleClass().add("accent");
                } else {
                    button.getStyleClass().add("button-outlined");
                }

                button.setOnAction(e -> {
                    ButtonType result = resultConverter == null
                            ? type
                            : resultConverter.apply(type);

                    if (result != null && result == type) {
                        closeModal(this);
                    }
                });

                footer.getChildren().add(button);
            }
        }

        private String buttonText(ButtonType type) {
            if (type == ButtonType.OK) return "Guardar";
            if (type == ButtonType.CANCEL) return "Cancelar";
            if (type == ButtonType.YES) return "Sim";
            if (type == ButtonType.NO) return "Não";
            return type.getText();
        }
    }

    private static final class CotanDialogPane extends VBox {
        private final VBox content = new VBox(14);
        private final ObservableList<ButtonType> buttonTypes = FXCollections.observableArrayList();

        CotanDialogPane() {
            setPadding(new Insets(0));
            getChildren().add(content);
        }

        void setContent(Node node) {
            content.getChildren().setAll(node);
        }

        ObservableList<ButtonType> getButtonTypes() {
            return buttonTypes;
        }
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
    private <T> TableColumn<T, Void> actionColumn(AdvancedTableView<T> table, ControlFactory<T> factory) {
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
