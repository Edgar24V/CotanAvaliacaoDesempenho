package com.cotan.avaliacao;

import com.cotan.avaliacao.domain.AvaliacaoDesempenhoAnual;
import com.cotan.avaliacao.domain.InstitutionProfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;
import java.util.*;

/**
 * Persistência SQLite do sistema de Avaliação e Desempenho.
 * A classe mantém a camada de dados independente da interface JavaFX.
 */
public final class Database implements AutoCloseable {

    private final Path databasePath;
    private Connection connection;

    public Database() {
        String base = System.getProperty("user.home");
        this.databasePath = Path.of(base, ".CotanAvaliacaoDesempenho", "data", "avaliacao.db");
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public void open() throws SQLException, IOException {
        Files.createDirectories(databasePath.getParent());
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        createSchema();
        seed();
        seedPerformance();
    }

    private void createSchema() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS classes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    academic_year TEXT NOT NULL,
                    shift TEXT NOT NULL DEFAULT 'Manhã',
                    room TEXT,
                    coordinator TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS teachers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    specialty TEXT,
                    phone TEXT,
                    email TEXT,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS subjects (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL UNIQUE,
                    code TEXT NOT NULL UNIQUE,
                    workload INTEGER NOT NULL DEFAULT 2,
                    weight REAL NOT NULL DEFAULT 1
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS students (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_number TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    gender TEXT,
                    birth_date TEXT,
                    class_id INTEGER,
                    phone TEXT,
                    guardian TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    FOREIGN KEY(class_id) REFERENCES classes(id) ON DELETE SET NULL
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS assessments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    type TEXT NOT NULL,
                    term TEXT NOT NULL,
                    assessment_date TEXT,
                    max_score REAL NOT NULL DEFAULT 20,
                    weight REAL NOT NULL DEFAULT 1,
                    class_id INTEGER,
                    subject_id INTEGER,
                    teacher_id INTEGER,
                    notes TEXT,
                    FOREIGN KEY(class_id) REFERENCES classes(id) ON DELETE SET NULL,
                    FOREIGN KEY(subject_id) REFERENCES subjects(id) ON DELETE SET NULL,
                    FOREIGN KEY(teacher_id) REFERENCES teachers(id) ON DELETE SET NULL
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS grades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id INTEGER NOT NULL,
                    assessment_id INTEGER NOT NULL,
                    score REAL NOT NULL,
                    observation TEXT,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(student_id, assessment_id),
                    FOREIGN KEY(student_id) REFERENCES students(id) ON DELETE CASCADE,
                    FOREIGN KEY(assessment_id) REFERENCES assessments(id) ON DELETE CASCADE
                )
                """);

            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_students_class ON students(class_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assessments_class ON assessments(class_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_assessments_subject ON assessments(subject_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_grades_student ON grades(student_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_grades_assessment ON grades(assessment_id)");

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS staff (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    staff_type TEXT NOT NULL CHECK(staff_type IN ('PROFESSOR','ADMINISTRATIVO')),
                    role TEXT,
                    category TEXT,
                    agent_number TEXT,
                    department TEXT,
                    phone TEXT,
                    email TEXT,
                    admission_date TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS performance_indicators (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    description TEXT,
                    staff_type TEXT NOT NULL DEFAULT 'AMBOS'
                        CHECK(staff_type IN ('PROFESSOR','ADMINISTRATIVO','AMBOS')),
                    weight REAL NOT NULL DEFAULT 1,
                    sort_order INTEGER NOT NULL DEFAULT 0,
                    active INTEGER NOT NULL DEFAULT 1
                )
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS performance_scores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    staff_id INTEGER NOT NULL,
                    indicator_id INTEGER NOT NULL,
                    academic_year TEXT NOT NULL,
                    trimester INTEGER NOT NULL CHECK(trimester IN (1,2,3)),
                    score REAL NOT NULL CHECK(score >= 0 AND score <= 20),
                    observation TEXT,
                    evaluator TEXT,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(staff_id, indicator_id, academic_year, trimester),
                    FOREIGN KEY(staff_id) REFERENCES staff(id) ON DELETE CASCADE,
                    FOREIGN KEY(indicator_id) REFERENCES performance_indicators(id) ON DELETE CASCADE
                )
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_staff_type ON staff(staff_type)
                """);
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS institution_profile (
                    id INTEGER PRIMARY KEY CHECK(id = 1),
                    provincial_office TEXT NOT NULL DEFAULT '',
                    municipal_direction TEXT NOT NULL DEFAULT '',
                    school TEXT NOT NULL DEFAULT '',
                    default_evaluator_staff_id INTEGER,
                    default_homologante_staff_id INTEGER,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(default_evaluator_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
                    FOREIGN KEY(default_homologante_staff_id) REFERENCES staff(id) ON DELETE SET NULL
                )
                """);


            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_performance_scores_cycle
                ON performance_scores(academic_year, trimester, staff_id)
                """);

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS performance_evaluations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    staff_id INTEGER NOT NULL,
                    academic_year TEXT NOT NULL,
                    evaluation_date TEXT,
                    period_start TEXT,
                    period_end TEXT,
                    evaluator_staff_id INTEGER,
                    quantitative_1 TEXT,
                    quantitative_2 TEXT,
                    quantitative_3 TEXT,
                    qualitative_1 TEXT,
                    qualitative_2 TEXT,
                    qualitative_3 TEXT,
                    final_quantitative TEXT,
                    final_qualitative TEXT,
                    comment1 TEXT,
                    comment2 TEXT,
                    comment3 TEXT,
                    appreciation_general TEXT,
                    concordance TEXT CHECK(concordance IS NULL OR concordance IN ('Concordo','Não concordo')),
                    homologante_staff_id INTEGER,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(staff_id, academic_year),
                    FOREIGN KEY(staff_id) REFERENCES staff(id) ON DELETE CASCADE,
                    FOREIGN KEY(evaluator_staff_id) REFERENCES staff(id) ON DELETE SET NULL,
                    FOREIGN KEY(homologante_staff_id) REFERENCES staff(id) ON DELETE SET NULL
                )
                """);

            st.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_performance_evaluations_year
                ON performance_evaluations(academic_year, staff_id)
                """);
        }

        ensureColumn("staff", "category", "TEXT");
        ensureColumn("staff", "agent_number", "TEXT");
        ensureColumn("institution_profile", "default_evaluator_staff_id", "INTEGER");
        ensureColumn("institution_profile", "default_homologante_staff_id", "INTEGER");

        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_staff_agent_number
                ON staff(agent_number)
                WHERE agent_number IS NOT NULL AND trim(agent_number) <> ''
                """);
        }
    }

    private void ensureColumn(String table, String column, String definition) throws SQLException {
        if (columnExists(table, column)) return;
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean columnExists(String table, String column) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) return true;
            }
        }
        return false;
    }

    private void seed() throws SQLException {
        if (count("classes") > 0) return;

        connection.setAutoCommit(false);
        try {
            long c1 = insert("""
                INSERT INTO classes(name, academic_year, shift, room, coordinator)
                VALUES(?,?,?,?,?)
                """, "10ª A", "2026/2027", "Manhã", "Sala 01", "Coordenação Pedagógica");
            long c2 = insert("""
                INSERT INTO classes(name, academic_year, shift, room, coordinator)
                VALUES(?,?,?,?,?)
                """, "11ª B", "2026/2027", "Tarde", "Sala 04", "Coordenação Pedagógica");

            long t1 = insert("""
                INSERT INTO teachers(name, specialty, phone, email) VALUES(?,?,?,?)
                """, "Ana Manuel", "Matemática", "923 000 001", "ana@cotan.edu");
            long t2 = insert("""
                INSERT INTO teachers(name, specialty, phone, email) VALUES(?,?,?,?)
                """, "Carlos José", "Português", "923 000 002", "carlos@cotan.edu");
            long t3 = insert("""
                INSERT INTO teachers(name, specialty, phone, email) VALUES(?,?,?,?)
                """, "Marta Silva", "Informática", "923 000 003", "marta@cotan.edu");

            long s1 = insert("""
                INSERT INTO subjects(name, code, workload, weight) VALUES(?,?,?,?)
                """, "Matemática", "MAT", 4, 1.2);
            long s2 = insert("""
                INSERT INTO subjects(name, code, workload, weight) VALUES(?,?,?,?)
                """, "Língua Portuguesa", "POR", 4, 1.0);
            long s3 = insert("""
                INSERT INTO subjects(name, code, workload, weight) VALUES(?,?,?,?)
                """, "Informática", "INF", 3, 1.0);

            long st1 = insert("""
                INSERT INTO students(student_number, name, gender, birth_date, class_id, phone, guardian)
                VALUES(?,?,?,?,?,?,?)
                """, "2026-0001", "Edgar Manuel", "Masculino", "2008-04-12", c1, "923 111 111", "Maria Manuel");
            long st2 = insert("""
                INSERT INTO students(student_number, name, gender, birth_date, class_id, phone, guardian)
                VALUES(?,?,?,?,?,?,?)
                """, "2026-0002", "Júlia Francisco", "Feminino", "2008-08-19", c1, "923 111 112", "Paulo Francisco");
            long st3 = insert("""
                INSERT INTO students(student_number, name, gender, birth_date, class_id, phone, guardian)
                VALUES(?,?,?,?,?,?,?)
                """, "2026-0003", "Mateus António", "Masculino", "2007-11-03", c2, "923 111 113", "Teresa António");
            long st4 = insert("""
                INSERT INTO students(student_number, name, gender, birth_date, class_id, phone, guardian)
                VALUES(?,?,?,?,?,?,?)
                """, "2026-0004", "Sofia Domingos", "Feminino", "2008-02-21", c2, "923 111 114", "João Domingos");

            long a1 = insert("""
                INSERT INTO assessments(title, type, term, assessment_date, max_score, weight, class_id, subject_id, teacher_id, notes)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, "1ª Prova", "Prova", "1º Trimestre", "2026-10-10", 20, 1, c1, s1, t1, "Avaliação diagnóstica");
            long a2 = insert("""
                INSERT INTO assessments(title, type, term, assessment_date, max_score, weight, class_id, subject_id, teacher_id, notes)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, "Trabalho de Leitura", "Trabalho", "1º Trimestre", "2026-10-14", 20, 1, c1, s2, t2, "Leitura orientada");
            long a3 = insert("""
                INSERT INTO assessments(title, type, term, assessment_date, max_score, weight, class_id, subject_id, teacher_id, notes)
                VALUES(?,?,?,?,?,?,?,?,?,?)
                """, "Projeto Digital", "Projeto", "1º Trimestre", "2026-10-20", 20, 1, c2, s3, t3, "Projeto de turma");

            upsertGrade(st1, a1, 17, "Muito bom");
            upsertGrade(st2, a1, 15, "Bom");
            upsertGrade(st1, a2, 16, "");
            upsertGrade(st2, a2, 18, "Excelente");
            upsertGrade(st3, a3, 14, "");
            upsertGrade(st4, a3, 19, "Excelente");

            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public long insert(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, params);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        return -1;
    }

    public int update(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                ResultSetMetaData meta = rs.getMetaData();
                int columns = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columns; i++) {
                        row.put(meta.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    public Object scalar(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = query(sql, params);
        if (rows.isEmpty()) return null;
        return rows.get(0).values().iterator().next();
    }

    public long count(String table) throws SQLException {
        Object value = scalar("SELECT COUNT(*) FROM " + table);
        return value == null ? 0 : ((Number) value).longValue();
    }

    private void seedPerformance() throws SQLException {
        update("""
            INSERT INTO institution_profile(
                id, provincial_office, municipal_direction, school,
                default_evaluator_staff_id, default_homologante_staff_id
            )
            SELECT 1, ?, ?, ?, NULL, NULL
            WHERE NOT EXISTS (SELECT 1 FROM institution_profile WHERE id = 1)
            """,
            "GABINETE PROVINCIAL DE EDUCAÇÃO DE LUANDA",
            "DIRECÇÃO MUNICIPAL DE EDUCAÇÃO DE LUANDA",
            "ESCOLA PRIMÁRIA Nº 1118 – MAIANGA");

        if (count("staff") == 0) {
            insert("""
                INSERT INTO staff(code,name,staff_type,role,category,agent_number,department,phone,email)
                VALUES(?,?,?,?,?,?,?,?,?)
                """,
                "PROF-001", "Ana Manuel", "PROFESSOR", "Professora",
                "Prof. Do Ens. Prim. E Sec. Do 6º Grau", "88010001",
                "Área Pedagógica", "923 100 001", "ana.manuel@cotan.edu");
            insert("""
                INSERT INTO staff(code,name,staff_type,role,category,agent_number,department,phone,email)
                VALUES(?,?,?,?,?,?,?,?,?)
                """,
                "PROF-002", "Carlos José", "PROFESSOR", "Professor",
                "Prof. Do Ens. Prim. E Sec. Do 6º Grau", "88010002",
                "Área Pedagógica", "923 100 002", "carlos.jose@cotan.edu");
            insert("INSERT INTO staff(code,name,staff_type,role,department,phone,email) VALUES(?,?,?,?,?,?,?)",
                    "ADM-001", "Maria José", "ADMINISTRATIVO", "Assistente Administrativa", "Secretaria", "923 200 001", "maria.jose@cotan.edu");
            insert("INSERT INTO staff(code,name,staff_type,role,department,phone,email) VALUES(?,?,?,?,?,?,?)",
                    "ADM-002", "Paulo António", "ADMINISTRATIVO", "Técnico Administrativo", "Administração", "923 200 002", "paulo.antonio@cotan.edu");
        }

        if (count("performance_indicators") == 0) {
            Object[][] indicators = {
                    {"IND-01", "Qualidade do trabalho", "Qualidade, rigor e consistência das entregas.", "AMBOS", 1.0, 1},
                    {"IND-02", "Produtividade e resultados", "Capacidade de cumprir metas, tarefas e resultados.", "AMBOS", 1.0, 2},
                    {"IND-03", "Responsabilidade e compromisso", "Cumprimento de responsabilidades, regras e prazos.", "AMBOS", 1.0, 3},
                    {"IND-04", "Pontualidade e assiduidade", "Presença, pontualidade e cumprimento dos horários.", "AMBOS", 1.0, 4},
                    {"IND-05", "Relacionamento e colaboração", "Cooperação, comunicação e relacionamento profissional.", "AMBOS", 1.0, 5}
            };
            for (Object[] item : indicators) {
                insert("""
                    INSERT INTO performance_indicators(code,name,description,staff_type,weight,sort_order)
                    VALUES(?,?,?,?,?,?)
                    """, item);
            }
        }

        if (count("performance_scores") == 0) {
            List<Map<String,Object>> staffRows = staffAll();
            List<Map<String,Object>> indicators = indicators("AMBOS");
            if (!staffRows.isEmpty() && !indicators.isEmpty()) {
                double[][] sample = {
                        {20, 15, 20, 15, 20},
                        {15, 10, 15, 10, 15},
                        {20, 20, 15, 20, 15},
                        {10, 15, 10, 15, 10}
                };
                for (int i = 0; i < Math.min(staffRows.size(), sample.length); i++) {
                    long staffId = n(staffRows.get(i).get("id"));
                    for (int j = 0; j < Math.min(indicators.size(), sample[i].length); j++) {
                        long indicatorId = n(indicators.get(j).get("id"));
                        upsertPerformanceScore(staffId, indicatorId, "2026/2027", 1, sample[i][j], "Dados demonstrativos", "Administrador");
                        if (i < 2) {
                            upsertPerformanceScore(staffId, indicatorId, "2026/2027", 2, Math.max(0, sample[i][j] - 1), "", "Administrador");
                            upsertPerformanceScore(staffId, indicatorId, "2026/2027", 3, sample[i][j], "", "Administrador");
                        }
                    }
                }
            }
        }

        migratePerformanceEvaluationFacts();
    }

    private void migratePerformanceEvaluationFacts() throws SQLException {
        List<Map<String,Object>> rows = query("""
            SELECT DISTINCT staff_id, academic_year
            FROM performance_scores
            ORDER BY academic_year, staff_id
            """);

        for (Map<String,Object> row : rows) {
            long staffId = n(row.get("staff_id"));
            String year = s(row.get("academic_year"));
            if (performanceEvaluation(staffId, year) != null) continue;

            int startYear = Integer.parseInt(year.substring(0,4));
            String evaluatorName = s(scalar("""
                SELECT evaluator
                FROM performance_scores
                WHERE staff_id=? AND academic_year=?
                  AND evaluator IS NOT NULL AND trim(evaluator)<>''
                ORDER BY updated_at DESC
                LIMIT 1
                """, staffId, year));

            Object evaluator = evaluatorName.isBlank() ? null
                    : scalar("SELECT id FROM staff WHERE active=1 AND lower(name)=lower(?) LIMIT 1", evaluatorName);

            String evaluationDate = s(scalar("""
                SELECT substr(MAX(updated_at),1,10)
                FROM performance_scores
                WHERE staff_id=? AND academic_year=?
                """, staffId, year));

            List<Map<String,Object>> comments = query("""
                SELECT observation
                FROM performance_scores
                WHERE staff_id=? AND academic_year=?
                  AND observation IS NOT NULL AND trim(observation)<>''
                GROUP BY observation
                ORDER BY MIN(updated_at), MIN(id)
                LIMIT 3
                """, staffId, year);

            insert("""
                INSERT INTO performance_evaluations(
                    staff_id,academic_year,evaluation_date,period_start,period_end,evaluator_staff_id,
                    comment1,comment2,comment3
                ) VALUES(?,?,?,?,?,?,?,?,?)
                """,
                staffId, year,
                blankToNull(evaluationDate),
                java.time.LocalDate.of(startYear,9,1).toString(),
                java.time.LocalDate.of(startYear + 1,6,30).toString(),
                evaluator,
                comments.size() > 0 ? blankToNull(s(comments.get(0).get("observation"))) : null,
                comments.size() > 1 ? blankToNull(s(comments.get(1).get("observation"))) : null,
                comments.size() > 2 ? blankToNull(s(comments.get(2).get("observation"))) : null
            );
            refreshEvaluationClassification(staffId, year);
        }
    }

    public List<Map<String,Object>> staffAll() throws SQLException {
        return query("""
            SELECT id,code,name,staff_type,COALESCE(role,'') role,
                   COALESCE(category,'') category,COALESCE(agent_number,'') agent_number,
                   COALESCE(department,'') department,COALESCE(phone,'') phone,
                   COALESCE(email,'') email,COALESCE(admission_date,'') admission_date
            FROM staff WHERE active=1 ORDER BY staff_type,name
            """);
    }

    public List<Map<String,Object>> staff(String type) throws SQLException {
        return query("""
            SELECT id,code,name,staff_type,COALESCE(role,'') role,
                   COALESCE(category,'') category,COALESCE(agent_number,'') agent_number,
                   COALESCE(department,'') department,COALESCE(phone,'') phone,
                   COALESCE(email,'') email,COALESCE(admission_date,'') admission_date
            FROM staff WHERE active=1 AND staff_type=? ORDER BY name
            """, type);
    }

    public List<Map<String,Object>> indicators(String staffType) throws SQLException {
        return query("""
            SELECT id,code,name,COALESCE(description,'') description,staff_type,weight,sort_order
            FROM performance_indicators
            WHERE active=1 AND (staff_type='AMBOS' OR staff_type=?)
            ORDER BY sort_order,id
            """, staffType);
    }

    public List<Map<String,Object>> performanceScores(long staffId, String academicYear, int trimester) throws SQLException {
        return query("""
            SELECT i.id indicator_id,i.code,i.name,i.description,i.weight,
                   ps.score,COALESCE(ps.observation,'') observation,COALESCE(ps.evaluator,'') evaluator
            FROM performance_indicators i
            LEFT JOIN performance_scores ps
              ON ps.indicator_id=i.id AND ps.staff_id=?
             AND ps.academic_year=? AND ps.trimester=?
            WHERE i.active=1
            ORDER BY i.sort_order,i.id
            """, staffId, academicYear, trimester);
    }

    public Map<String,Object> institutionProfile() throws SQLException {
        Map<String,Object> row = query("""
            SELECT COALESCE(provincial_office,'') provincial_office,
                   COALESCE(municipal_direction,'') municipal_direction,
                   COALESCE(school,'') school,
                   default_evaluator_staff_id,
                   default_homologante_staff_id
            FROM institution_profile
            WHERE id=1
            """).stream().findFirst().orElseGet(LinkedHashMap::new);

        row.putIfAbsent("provincial_office", "");
        row.putIfAbsent("municipal_direction", "");
        row.putIfAbsent("school", "");
        row.putIfAbsent("default_evaluator_staff_id", null);
        row.putIfAbsent("default_homologante_staff_id", null);
        return row;
    }

    public InstitutionProfile institutionProfileEntity() throws SQLException {
        Map<String,Object> row = institutionProfile();
        return new InstitutionProfile(
                1L,
                s(row.get("provincial_office")),
                s(row.get("municipal_direction")),
                s(row.get("school")),
                row.get("default_evaluator_staff_id") == null ? null : n(row.get("default_evaluator_staff_id")),
                row.get("default_homologante_staff_id") == null ? null : n(row.get("default_homologante_staff_id"))
        );
    }

    public void saveInstitutionProfile(String provincialOffice, String municipalDirection, String school,
                                       Long defaultEvaluatorStaffId, Long defaultHomologanteStaffId) throws SQLException {
        if (blankToNull(provincialOffice) == null) throw new IllegalArgumentException("O Gabinete Provincial é obrigatório.");
        if (blankToNull(municipalDirection) == null) throw new IllegalArgumentException("A Direcção Municipal é obrigatória.");
        if (blankToNull(school) == null) throw new IllegalArgumentException("A Escola é obrigatória.");

        update("""
            INSERT INTO institution_profile(
                id,provincial_office,municipal_direction,school,
                default_evaluator_staff_id,default_homologante_staff_id,updated_at
            ) VALUES(1,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(id) DO UPDATE SET
                provincial_office=excluded.provincial_office,
                municipal_direction=excluded.municipal_direction,
                school=excluded.school,
                default_evaluator_staff_id=excluded.default_evaluator_staff_id,
                default_homologante_staff_id=excluded.default_homologante_staff_id,
                updated_at=CURRENT_TIMESTAMP
            """, blankToNull(provincialOffice), blankToNull(municipalDirection),
                blankToNull(school), defaultEvaluatorStaffId, defaultHomologanteStaffId);
    }

    public List<Map<String,Object>> performanceReportFacts(long staffId, String academicYear) throws SQLException {
        return query("""
            SELECT trimester,
                   COALESCE(observation,'') observation,
                   COALESCE(evaluator,'') evaluator,
                   updated_at
            FROM performance_scores
            WHERE staff_id=? AND academic_year=?
            ORDER BY updated_at DESC, trimester, id
            """, staffId, academicYear);
    }

    public AvaliacaoDesempenhoAnual performanceEvaluation(long staffId, String academicYear) throws SQLException {
        Map<String,Object> row = query("""
            SELECT pe.id,pe.staff_id,pe.academic_year,pe.evaluation_date,pe.period_start,pe.period_end,
                   COALESCE(pe.evaluator_staff_id,ip.default_evaluator_staff_id) evaluator_staff_id,
                   COALESCE(quantitative_1,'') quantitative_1,
                   COALESCE(quantitative_2,'') quantitative_2,
                   COALESCE(quantitative_3,'') quantitative_3,
                   COALESCE(qualitative_1,'') qualitative_1,
                   COALESCE(qualitative_2,'') qualitative_2,
                   COALESCE(qualitative_3,'') qualitative_3,
                   COALESCE(final_quantitative,'') final_quantitative,
                   COALESCE(final_qualitative,'') final_qualitative,
                   COALESCE(comment1,'') comment1,
                   COALESCE(comment2,'') comment2,
                   COALESCE(comment3,'') comment3,
                   COALESCE(appreciation_general,'') appreciation_general,
                   COALESCE(concordance,'') concordance,
                   COALESCE(pe.homologante_staff_id,ip.default_homologante_staff_id) homologante_staff_id
            FROM performance_evaluations pe
            LEFT JOIN institution_profile ip ON ip.id=1
            WHERE pe.staff_id=? AND pe.academic_year=?
            """, staffId, academicYear).stream().findFirst().orElse(null);
        if (row == null) return null;

        return new AvaliacaoDesempenhoAnual(
                n(row.get("id")), n(row.get("staff_id")), s(row.get("academic_year")),
                parseDate(row.get("evaluation_date")), parseDate(row.get("period_start")), parseDate(row.get("period_end")),
                row.get("evaluator_staff_id") == null ? null : n(row.get("evaluator_staff_id")),
                s(row.get("quantitative_1")), s(row.get("quantitative_2")), s(row.get("quantitative_3")),
                s(row.get("qualitative_1")), s(row.get("qualitative_2")), s(row.get("qualitative_3")),
                s(row.get("final_quantitative")), s(row.get("final_qualitative")),
                s(row.get("comment1")), s(row.get("comment2")), s(row.get("comment3")),
                s(row.get("appreciation_general")), s(row.get("concordance")),
                row.get("homologante_staff_id") == null ? null : n(row.get("homologante_staff_id"))
        );
    }

    public void savePerformanceEvaluation(AvaliacaoDesempenhoAnual evaluation) throws SQLException {
        Objects.requireNonNull(evaluation, "evaluation");
        validateAcademicYear(evaluation.academicYear());
        if (evaluation.staffId() <= 0) throw new IllegalArgumentException("O profissional é obrigatório.");
        if (evaluation.evaluatorStaffId() == null || evaluation.evaluatorStaffId() <= 0) {
            throw new IllegalArgumentException("O avaliador da ficha anual é obrigatório.");
        }
        if (evaluation.evaluationDate() != null && evaluation.evaluationDate().isAfter(java.time.LocalDate.now())) {
            throw new IllegalArgumentException("A data de avaliação não pode estar no futuro.");
        }
        if (evaluation.periodStart() != null && evaluation.periodEnd() != null
                && evaluation.periodEnd().isBefore(evaluation.periodStart())) {
            throw new IllegalArgumentException("O período final não pode ser anterior ao período inicial.");
        }
        String concordance = blankToNull(evaluation.concordance());
        if (concordance != null && !Set.of("Concordo", "Não concordo").contains(concordance)) {
            throw new IllegalArgumentException("A concordância deve ser Concordo ou Não concordo.");
        }

        update("""
            INSERT INTO performance_evaluations(
                staff_id,academic_year,evaluation_date,period_start,period_end,evaluator_staff_id,
                quantitative_1,quantitative_2,quantitative_3,
                qualitative_1,qualitative_2,qualitative_3,
                final_quantitative,final_qualitative,
                comment1,comment2,comment3,appreciation_general,concordance,homologante_staff_id,
                updated_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(staff_id,academic_year) DO UPDATE SET
                evaluation_date=excluded.evaluation_date,
                period_start=excluded.period_start,
                period_end=excluded.period_end,
                evaluator_staff_id=excluded.evaluator_staff_id,
                quantitative_1=excluded.quantitative_1,
                quantitative_2=excluded.quantitative_2,
                quantitative_3=excluded.quantitative_3,
                qualitative_1=excluded.qualitative_1,
                qualitative_2=excluded.qualitative_2,
                qualitative_3=excluded.qualitative_3,
                final_quantitative=excluded.final_quantitative,
                final_qualitative=excluded.final_qualitative,
                comment1=excluded.comment1,
                comment2=excluded.comment2,
                comment3=excluded.comment3,
                appreciation_general=excluded.appreciation_general,
                concordance=excluded.concordance,
                homologante_staff_id=excluded.homologante_staff_id,
                updated_at=CURRENT_TIMESTAMP
            """,
            evaluation.staffId(), evaluation.academicYear(),
            toIso(evaluation.evaluationDate()), toIso(evaluation.periodStart()), toIso(evaluation.periodEnd()),
            evaluation.evaluatorStaffId(),
            blankToNull(evaluation.quantitative1()), blankToNull(evaluation.quantitative2()), blankToNull(evaluation.quantitative3()),
            blankToNull(evaluation.qualitative1()), blankToNull(evaluation.qualitative2()), blankToNull(evaluation.qualitative3()),
            blankToNull(evaluation.finalQuantitative()), blankToNull(evaluation.finalQualitative()),
            blankToNull(evaluation.comment1()), blankToNull(evaluation.comment2()), blankToNull(evaluation.comment3()),
            blankToNull(evaluation.appreciationGeneral()), concordance, evaluation.homologanteStaffId()
        );
    }

    public void refreshEvaluationClassification(long staffId, String academicYear) throws SQLException {
        validateAcademicYear(academicYear);
        List<Object> values = new ArrayList<>();
        for (int trimester = 1; trimester <= 3; trimester++) {
            values.add(scalar("""
                SELECT CASE
                    WHEN COUNT(ps.id) = (
                        SELECT COUNT(*) FROM performance_indicators pi
                        JOIN staff sx ON sx.id=?
                        WHERE pi.active=1 AND (pi.staff_type='AMBOS' OR pi.staff_type=sx.staff_type)
                    )
                    THEN ROUND(SUM(ps.score),0)
                    ELSE NULL
                END
                FROM performance_scores ps
                JOIN performance_indicators i ON i.id=ps.indicator_id
                WHERE ps.staff_id=? AND ps.academic_year=? AND ps.trimester=?
                """, staffId, staffId, academicYear, trimester));
        }

        String q1 = formatOptionalInteger(values.get(0));
        String q2 = formatOptionalInteger(values.get(1));
        String q3 = formatOptionalInteger(values.get(2));
        String l1 = classificationFromQuantity(values.get(0));
        String l2 = classificationFromQuantity(values.get(1));
        String l3 = classificationFromQuantity(values.get(2));

        String finalQuantitative = "";
        String finalQualitative = "";
        Double a = number(values.get(0)), b = number(values.get(1)), d = number(values.get(2));
        if (a != null && b != null && d != null) {
            finalQuantitative = String.format(Locale.US, "%.0f", (a + b + d) / 3.0);
            finalQualitative = classificationFromQuantity((a + b + d) / 3.0);
        }

        update("""
            UPDATE performance_evaluations SET
                quantitative_1=?,quantitative_2=?,quantitative_3=?,
                qualitative_1=?,qualitative_2=?,qualitative_3=?,
                final_quantitative=?,final_qualitative=?,updated_at=CURRENT_TIMESTAMP
            WHERE staff_id=? AND academic_year=?
            """,
            blankToNull(q1), blankToNull(q2), blankToNull(q3),
            blankToNull(l1), blankToNull(l2), blankToNull(l3),
            blankToNull(finalQuantitative), blankToNull(finalQualitative),
            staffId, academicYear);
    }

    private void validateAcademicYear(String academicYear) {
        if (academicYear == null || !academicYear.matches("20\\d{2}/20\\d{2}$")) {
            throw new IllegalArgumentException("Ano lectivo inválido.");
        }
        int start = Integer.parseInt(academicYear.substring(0,4));
        int end = Integer.parseInt(academicYear.substring(5));
        if (end != start + 1) throw new IllegalArgumentException("Ano lectivo inválido.");
    }

    private java.time.LocalDate parseDate(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return java.time.LocalDate.parse(String.valueOf(value).substring(0,10)); }
        catch (RuntimeException ex) { return null; }
    }

    private String toIso(java.time.LocalDate value) {
        return value == null ? null : value.toString();
    }

    private String formatOptionalInteger(Object value) {
        Double number = number(value);
        return number == null ? "" : String.format(Locale.US,"%.0f",number);
    }

    private Double number(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? null : Double.parseDouble(String.valueOf(value)); }
        catch (RuntimeException ex) { return null; }
    }

    private String classificationFromQuantity(Object value) {
        Double number = number(value);
        if (number == null) return "";
        if (number < 10) return "Mau";
        if (number < 14) return "Suficiente";
        if (number < 18) return "Bom";
        return "Muito bom";
    }

    public void upsertPerformanceScore(long staffId, long indicatorId, String year, int trimester,
                                       double score, String observation, String evaluator) throws SQLException {
        if (staffId <= 0 || indicatorId <= 0) {
            throw new IllegalArgumentException("Profissional e indicador são obrigatórios.");
        }
        if (year == null || !year.matches("20\\d{2}/20\\d{2}$")) {
            throw new IllegalArgumentException("Ano lectivo inválido.");
        }
        int startYear = Integer.parseInt(year.substring(0, 4));
        int endYear = Integer.parseInt(year.substring(5));
        if (endYear != startYear + 1) {
            throw new IllegalArgumentException("O ano lectivo deve seguir o formato 2026/2027.");
        }
        if (trimester < 1 || trimester > 3) {
            throw new IllegalArgumentException("O trimestre deve ser 1, 2 ou 3.");
        }
        if (!Double.isFinite(score) || !(score == 5 || score == 10 || score == 15 || score == 20)) {
            throw new IllegalArgumentException("A pontuação deve ser 5, 10, 15 ou 20.");
        }
        if (observation != null && observation.length() > 500) {
            throw new IllegalArgumentException("A observação não pode exceder 500 caracteres.");
        }

        update("""
            INSERT INTO performance_scores(
                staff_id,indicator_id,academic_year,trimester,score,observation,evaluator,updated_at
            ) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(staff_id,indicator_id,academic_year,trimester) DO UPDATE SET
                score=excluded.score,
                observation=excluded.observation,
                evaluator=excluded.evaluator,
                updated_at=CURRENT_TIMESTAMP
            """, staffId, indicatorId, year, trimester, score,
                blankToNull(observation), blankToNull(evaluator));
    }

    public List<Map<String,Object>> performanceMap(String staffType, String year, int trimester) throws SQLException {
        return query("""
            SELECT s.id,s.code,s.name,COALESCE(s.role,'') role,COALESCE(s.department,'') department,
                   COUNT(ps.id) indicators_filled,
                   CASE
                       WHEN COUNT(ps.id) = (
                           SELECT COUNT(*)
                           FROM performance_indicators pi
                           WHERE pi.active=1
                             AND (pi.staff_type='AMBOS' OR pi.staff_type=s.staff_type)
                       )
                       THEN ROUND(SUM(ps.score * i.weight) / NULLIF(SUM(i.weight),0),2)
                       ELSE NULL
                   END average_score
            FROM staff s
            LEFT JOIN performance_scores ps
              ON ps.staff_id=s.id AND ps.academic_year=? AND ps.trimester=?
            LEFT JOIN performance_indicators i ON i.id=ps.indicator_id
            WHERE s.active=1 AND s.staff_type=?
            GROUP BY s.id,s.code,s.name,s.role,s.department
            ORDER BY average_score DESC,s.name
            """, year, trimester, staffType);
    }

    public List<Map<String,Object>> performanceFinalMap(String staffType, String year) throws SQLException {
        return query("""
            SELECT s.id,s.code,s.name,COALESCE(s.role,'') role,COALESCE(s.department,'') department,
                   t1.avg_score t1_average,
                   t2.avg_score t2_average,
                   t3.avg_score t3_average,
                   CASE
                       WHEN t1.avg_score IS NOT NULL
                        AND t2.avg_score IS NOT NULL
                        AND t3.avg_score IS NOT NULL
                       THEN ROUND((t1.avg_score + t2.avg_score + t3.avg_score) / 3.0, 1)
                       ELSE NULL
                   END final_average
            FROM staff s
            LEFT JOIN (
                SELECT ps.staff_id,
                       SUM(ps.score * i.weight) / NULLIF(SUM(i.weight),0) avg_score
                FROM performance_scores ps
                JOIN performance_indicators i ON i.id=ps.indicator_id
                JOIN staff sx ON sx.id=ps.staff_id
                WHERE ps.academic_year=? AND ps.trimester=1
                GROUP BY ps.staff_id
                HAVING COUNT(ps.id) = (
                    SELECT COUNT(*) FROM performance_indicators pi
                    WHERE pi.active=1 AND (pi.staff_type='AMBOS' OR pi.staff_type=sx.staff_type)
                )
            ) t1 ON t1.staff_id=s.id
            LEFT JOIN (
                SELECT ps.staff_id,
                       SUM(ps.score * i.weight) / NULLIF(SUM(i.weight),0) avg_score
                FROM performance_scores ps
                JOIN performance_indicators i ON i.id=ps.indicator_id
                JOIN staff sx ON sx.id=ps.staff_id
                WHERE ps.academic_year=? AND ps.trimester=2
                GROUP BY ps.staff_id
                HAVING COUNT(ps.id) = (
                    SELECT COUNT(*) FROM performance_indicators pi
                    WHERE pi.active=1 AND (pi.staff_type='AMBOS' OR pi.staff_type=sx.staff_type)
                )
            ) t2 ON t2.staff_id=s.id
            LEFT JOIN (
                SELECT ps.staff_id,
                       SUM(ps.score * i.weight) / NULLIF(SUM(i.weight),0) avg_score
                FROM performance_scores ps
                JOIN performance_indicators i ON i.id=ps.indicator_id
                JOIN staff sx ON sx.id=ps.staff_id
                WHERE ps.academic_year=? AND ps.trimester=3
                GROUP BY ps.staff_id
                HAVING COUNT(ps.id) = (
                    SELECT COUNT(*) FROM performance_indicators pi
                    WHERE pi.active=1 AND (pi.staff_type='AMBOS' OR pi.staff_type=sx.staff_type)
                )
            ) t3 ON t3.staff_id=s.id
            WHERE s.active=1 AND s.staff_type=?
            ORDER BY final_average DESC,s.name
            """, year, year, year, staffType);
    }

    public Map<String,Object> performanceSummary(String staffType, String year) throws SQLException {
        Map<String,Object> result = new LinkedHashMap<>();
        Object total = scalar("SELECT COUNT(*) FROM staff WHERE active=1 AND staff_type=?", staffType);
        Object evaluated = scalar("""
            SELECT COUNT(DISTINCT staff_id) FROM performance_scores
            WHERE academic_year=? AND staff_id IN (SELECT id FROM staff WHERE staff_type=? AND active=1)
            """, year, staffType);
        Object avg = scalar("""
            SELECT ROUND(COALESCE(AVG(score),0),2) FROM performance_scores
            WHERE academic_year=? AND staff_id IN (SELECT id FROM staff WHERE staff_type=? AND active=1)
            """, year, staffType);
        result.put("total", total == null ? 0 : total);
        result.put("evaluated", evaluated == null ? 0 : evaluated);
        result.put("average", avg == null ? 0 : avg);
        return result;
    }

    private static String classification(double score) {
        if (score < 10) return "Mau";
        if (score < 14) return "Suficiente";
        if (score < 18) return "Bom";
        return "Muito bom";
    }

    public List<Map<String,Object>> students() throws SQLException {
        return query("""
            SELECT s.id, s.student_number, s.name, COALESCE(s.gender,'') gender,
                   COALESCE(s.birth_date,'') birth_date, COALESCE(c.name,'Sem turma') class_name,
                   COALESCE(s.phone,'') phone, COALESCE(s.guardian,'') guardian, s.class_id
            FROM students s
            LEFT JOIN classes c ON c.id=s.class_id
            WHERE s.active=1 ORDER BY s.name
            """);
    }

    public List<Map<String,Object>> teachers() throws SQLException {
        return query("SELECT id,name,COALESCE(specialty,'') specialty,COALESCE(phone,'') phone,COALESCE(email,'') email FROM teachers WHERE active=1 ORDER BY name");
    }

    public List<Map<String,Object>> classesData() throws SQLException {
        return query("SELECT id,name,academic_year,shift,COALESCE(room,'') room,COALESCE(coordinator,'') coordinator FROM classes WHERE active=1 ORDER BY name");
    }

    public List<Map<String,Object>> subjects() throws SQLException {
        return query("SELECT id,name,code,workload,weight FROM subjects ORDER BY name");
    }

    public List<Map<String,Object>> assessments() throws SQLException {
        return query("""
            SELECT a.id,a.title,a.type,a.term,COALESCE(a.assessment_date,'') assessment_date,
                   a.max_score,a.weight,COALESCE(c.name,'Todas') class_name,
                   COALESCE(s.name,'Não definida') subject_name,
                   COALESCE(t.name,'Não definido') teacher_name,
                   a.class_id,a.subject_id,a.teacher_id
            FROM assessments a
            LEFT JOIN classes c ON c.id=a.class_id
            LEFT JOIN subjects s ON s.id=a.subject_id
            LEFT JOIN teachers t ON t.id=a.teacher_id
            ORDER BY COALESCE(a.assessment_date,'9999-12-31') DESC, a.id DESC
            """);
    }

    public List<Map<String,Object>> studentsForAssessment(long assessmentId) throws SQLException {
        return query("""
            SELECT s.id,s.student_number,s.name,
                   COALESCE(g.score, NULL) score, COALESCE(g.observation,'') observation
            FROM students s
            JOIN assessments a ON a.id=?
            LEFT JOIN grades g ON g.student_id=s.id AND g.assessment_id=a.id
            WHERE s.active=1 AND (a.class_id IS NULL OR s.class_id=a.class_id)
            ORDER BY s.name
            """, assessmentId);
    }

    public List<Map<String,Object>> performance() throws SQLException {
        return query("""
            SELECT s.id,s.student_number,s.name,COALESCE(c.name,'Sem turma') class_name,
                   COUNT(g.id) assessments_count,
                   ROUND(COALESCE(SUM(g.score * a.weight) / NULLIF(SUM(a.weight),0),0),2) average_score,
                   CASE
                     WHEN COUNT(g.id)=0 THEN 'Sem avaliações'
                     WHEN (SUM(g.score * a.weight) / NULLIF(SUM(a.weight),0)) >= 10 THEN 'Aprovado'
                     ELSE 'Abaixo da média'
                   END status
            FROM students s
            LEFT JOIN classes c ON c.id=s.class_id
            LEFT JOIN grades g ON g.student_id=s.id
            LEFT JOIN assessments a ON a.id=g.assessment_id
            WHERE s.active=1
            GROUP BY s.id,s.student_number,s.name,c.name
            ORDER BY average_score DESC, s.name
            """);
    }

    public Map<String,Object> dashboard() throws SQLException {
        Map<String,Object> result = new HashMap<>();
        result.put("students", count("students"));
        result.put("teachers", count("teachers"));
        result.put("classes", count("classes"));
        result.put("subjects", count("subjects"));
        result.put("assessments", count("assessments"));
        Object avg = scalar("SELECT ROUND(COALESCE(AVG(score),0),2) FROM grades");
        result.put("average", avg == null ? 0 : avg);
        return result;
    }

    public void upsertGrade(long studentId, long assessmentId, double score, String observation) throws SQLException {
        if (studentId <= 0 || assessmentId <= 0) {
            throw new IllegalArgumentException("Aluno e avaliação são obrigatórios.");
        }
        Object maxObject = scalar("SELECT max_score FROM assessments WHERE id=?", assessmentId);
        if (!(maxObject instanceof Number maxNumber)) {
            throw new IllegalArgumentException("A avaliação selecionada não existe.");
        }
        double maxScore = maxNumber.doubleValue();
        if (!Double.isFinite(score) || score < 0 || score > maxScore) {
            throw new IllegalArgumentException("A nota deve estar entre 0 e " + maxScore + ".");
        }
        if (observation != null && observation.length() > 500) {
            throw new IllegalArgumentException("A observação não pode exceder 500 caracteres.");
        }

        update("""
            INSERT INTO grades(student_id,assessment_id,score,observation,updated_at)
            VALUES(?,?,?,?,CURRENT_TIMESTAMP)
            ON CONFLICT(student_id,assessment_id) DO UPDATE SET
                score=excluded.score,
                observation=excluded.observation,
                updated_at=CURRENT_TIMESTAMP
            """, studentId, assessmentId, score, observation == null ? "" : observation);
    }

    public void deleteById(String table, long id) {
        try {
            update("DELETE FROM " + table + " WHERE id=?", id);
        } catch (SQLException e) {
            throw new IllegalStateException("Não foi possível eliminar o registo.", e);
        }
    }

    public void backup(Path destination) throws IOException, SQLException {
        if (connection != null && !connection.isClosed()) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
        closeConnectionOnly();
        if (destination.getParent() != null) Files.createDirectories(destination.getParent());
        Files.copy(databasePath, destination, StandardCopyOption.REPLACE_EXISTING);
        open();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String s(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static long n(Object o){
        if(o==null)return 0;
        if(o instanceof Number number)return number.longValue();
        try{return Long.parseLong(String.valueOf(o));}catch(Exception e){return 0;}
    }

    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            if (p instanceof LocalDateHolder date) ps.setString(i + 1, date.value());
            else if (p == null) ps.setNull(i + 1, Types.NULL);
            else ps.setObject(i + 1, p);
        }
    }

    private void closeConnectionOnly() throws SQLException {
        if (connection != null && !connection.isClosed()) connection.close();
    }

    @Override
    public void close() {
        try {
            closeConnectionOnly();
        } catch (SQLException ignored) {
        }
    }

    /** Pequeno wrapper para datas quando se pretende persistir como ISO-8601. */
    public record LocalDateHolder(String value) {}
}
