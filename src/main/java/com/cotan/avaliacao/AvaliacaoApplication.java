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

        VBox main = new VBox(6);
        main.getChildren().addAll(
                navButton("⌂", "Dashboard", "dashboard"),
                navButton("●", "Alunos", "students"),
                navButton("◆", "Professores", "teachers"),
                navButton("▦", "Turmas", "classes"),
                navButton("◈", "Disciplinas", "subjects"),
                navButton("✓", "Avaliações", "assessments"),
                navButton("✎", "Lançar notas", "grades"),
                navButton("▤", "Relatórios", "reports")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        VBox lower = new VBox(6);
        lower.getChildren().addAll(
                navButton("⚙", "Configurações", "settings"),
                navButton("?", "Sobre", "about")
        );

        side.getChildren().addAll(brand, new Separator(), main, spacer, lower);
        return side;
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

        Map<String, String> titles = Map.of(
                "dashboard", "Dashboard",
                "students", "Gestão de Alunos",
                "teachers", "Gestão de Professores",
                "classes", "Gestão de Turmas",
                "subjects", "Gestão de Disciplinas",
                "assessments", "Avaliações",
                "grades", "Lançamento de Notas",
                "reports", "Relatórios de Desempenho",
                "settings", "Configurações",
                "about", "Sobre o sistema"
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
        boolean hasAction = Set.of("students","teachers","classes","subjects","assessments").contains(currentSection);
        topAction.setVisible(hasAction);
        topAction.setManaged(hasAction);
        topAction.setOnAction(e -> {
            switch (currentSection) {
                case "students" -> studentDialog(null);
                case "teachers" -> teacherDialog(null);
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
                case "students" -> content.getChildren().setAll(buildStudents());
                case "teachers" -> content.getChildren().setAll(buildTeachers());
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
        Map<String,Object> d = database.dashboard();

        VBox page = pageContainer();
        HBox heading = sectionHeading(
                "Visão geral",
                "Acompanhe o estado académico do sistema num único painel."
        );

        Button refresh = new Button("↻ Atualizar");
        refresh.setOnAction(e -> refreshCurrentSection());
        heading.getChildren().add(refresh);

        GridPane cards = new GridPane();
        cards.setHgap(14);
        cards.setVgap(14);
        for (int i = 0; i < 6; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(16.66);
            cards.getColumnConstraints().add(cc);
        }

        cards.add(statCard("Alunos", String.valueOf(d.get("students")), "Registados", "●"), 0, 0);
        cards.add(statCard("Professores", String.valueOf(d.get("teachers")), "Activos", "◆"), 1, 0);
        cards.add(statCard("Turmas", String.valueOf(d.get("classes")), "Em funcionamento", "▦"), 2, 0);
        cards.add(statCard("Disciplinas", String.valueOf(d.get("subjects")), "No currículo", "◈"), 3, 0);
        cards.add(statCard("Avaliações", String.valueOf(d.get("assessments")), "Criadas", "✓"), 4, 0);
        cards.add(statCard("Média geral", String.valueOf(d.get("average")), "Escala de 0–20", "★"), 5, 0);

        VBox performanceCard = card();
        performanceCard.getChildren().addAll(
                label("Desempenho recente", "card-title"),
                label("Os melhores resultados calculados a partir das notas lançadas.", "muted")
        );

        TableView<PerformanceRow> table = new TableView<>();
        table.setPrefHeight(310);
        addColumn(table, "Aluno", 240, PerformanceRow::nameProperty);
        addColumn(table, "Turma", 150, PerformanceRow::classNameProperty);
        addColumn(table, "Avaliações", 120, PerformanceRow::countProperty);
        addColumn(table, "Média", 120, PerformanceRow::averageProperty);
        addColumn(table, "Estado", 160, PerformanceRow::statusProperty);

        ObservableList<PerformanceRow> rows = FXCollections.observableArrayList();
        for (Map<String,Object> r : database.performance()) {
            rows.add(PerformanceRow.from(r));
        }
        table.setItems(rows);

        performanceCard.getChildren().add(table);

        HBox shortcuts = new HBox(12);
        shortcuts.getChildren().addAll(
                actionCard("Novo aluno", "Registar um estudante", () -> studentDialog(null)),
                actionCard("Nova avaliação", "Criar uma avaliação", () -> assessmentDialog(null)),
                actionCard("Lançar notas", "Preencher resultados", () -> showSection("grades")),
                actionCard("Relatórios", "Analisar desempenho", () -> showSection("reports"))
        );

        page.getChildren().addAll(heading, cards, performanceCard, shortcuts);
        return new ScrollPane(page) {{
            setFitToWidth(true);
            setHbarPolicy(ScrollBarPolicy.NEVER);
            getStyleClass().add("edge-to-edge");
        }};
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
        TextFormatter<Double> formatter = new TextFormatter<>(new javafx.util.converter.DoubleStringConverter());
        weight.getValueFactory().valueProperty().bindBidirectional(new SimpleDoubleProperty(1.0).asObject()); // initial style-safe binding

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
        showSection("grades");
        Platform.runLater(() -> {
            if (content.getChildren().isEmpty()) return;
        });
        // O módulo de notas lê a primeira avaliação compatível quando aberto.
        buildGradesWithAssessment(row.id.get());
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

    private <T> TableColumn<T, Void> actionColumn(TableView<T> table, Consumer<T> renderer) {
        TableColumn<T, Void> column = new TableColumn<>("Ações");
        column.setCellFactory(tc -> new TableCell<>() {
            private final HBox box = new HBox(6);
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) setGraphic(null);
                else {
                    box.getChildren().clear();
                    T row = getTableView().getItems().get(getIndex());
                    Node controls = renderer.apply(row);
                    // Consumer renderer is used only to add controls via a temporary holder.
                    if (controls instanceof Parent p) setGraphic(p);
                    else setGraphic(null);
                }
            }
        });
        return column;
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
