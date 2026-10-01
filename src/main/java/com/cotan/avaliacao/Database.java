package com.cotan.avaliacao;

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
                CREATE INDEX IF NOT EXISTS idx_performance_scores_cycle
                ON performance_scores(academic_year, trimester, staff_id)
                """);
        }
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
        if (count("staff") == 0) {
            insert("INSERT INTO staff(code,name,staff_type,role,department,phone,email) VALUES(?,?,?,?,?,?,?)",
                    "PROF-001", "Ana Manuel", "PROFESSOR", "Professora", "Área Pedagógica", "923 100 001", "ana.manuel@cotan.edu");
            insert("INSERT INTO staff(code,name,staff_type,role,department,phone,email) VALUES(?,?,?,?,?,?,?)",
                    "PROF-002", "Carlos José", "PROFESSOR", "Professor", "Área Pedagógica", "923 100 002", "carlos.jose@cotan.edu");
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
                        {17, 16, 18, 15, 17},
                        {15, 14, 16, 14, 15},
                        {18, 17, 16, 18, 17},
                        {14, 15, 13, 16, 15}
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
    }

    public List<Map<String,Object>> staffAll() throws SQLException {
        return query("""
            SELECT id,code,name,staff_type,COALESCE(role,'') role,COALESCE(department,'') department,
                   COALESCE(phone,'') phone,COALESCE(email,'') email,COALESCE(admission_date,'') admission_date
            FROM staff WHERE active=1 ORDER BY staff_type,name
            """);
    }

    public List<Map<String,Object>> staff(String type) throws SQLException {
        return query("""
            SELECT id,code,name,staff_type,COALESCE(role,'') role,COALESCE(department,'') department,
                   COALESCE(phone,'') phone,COALESCE(email,'') email,COALESCE(admission_date,'') admission_date
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

    public void upsertPerformanceScore(long staffId, long indicatorId, String year, int trimester,
                                       double score, String observation, String evaluator) throws SQLException {
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
                   ROUND(COALESCE(
                       SUM(ps.score * i.weight) / NULLIF(SUM(i.weight),0)
                   ,0),2) average_score
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
                   ROUND(COALESCE((
                       SELECT SUM(ps1.score*i1.weight)/NULLIF(SUM(i1.weight),0)
                       FROM performance_scores ps1
                       JOIN performance_indicators i1 ON i1.id=ps1.indicator_id
                       WHERE ps1.staff_id=s.id AND ps1.academic_year=? AND ps1.trimester=1
                   ),0),2) t1_average,
                   ROUND(COALESCE((
                       SELECT SUM(ps2.score*i2.weight)/NULLIF(SUM(i2.weight),0)
                       FROM performance_scores ps2
                       JOIN performance_indicators i2 ON i2.id=ps2.indicator_id
                       WHERE ps2.staff_id=s.id AND ps2.academic_year=? AND ps2.trimester=2
                   ),0),2) t2_average,
                   ROUND(COALESCE((
                       SELECT SUM(ps3.score*i3.weight)/NULLIF(SUM(i3.weight),0)
                       FROM performance_scores ps3
                       JOIN performance_indicators i3 ON i3.id=ps3.indicator_id
                       WHERE ps3.staff_id=s.id AND ps3.academic_year=? AND ps3.trimester=3
                   ),0),2) t3_average,
                   ROUND(COALESCE((
                       SELECT AVG(t.avg_score) FROM (
                           SELECT SUM(ps4.score*i4.weight)/NULLIF(SUM(i4.weight),0) avg_score
                           FROM performance_scores ps4
                           JOIN performance_indicators i4 ON i4.id=ps4.indicator_id
                           WHERE ps4.staff_id=s.id AND ps4.academic_year=? AND ps4.trimester IN (1,2,3)
                           GROUP BY ps4.trimester
                       ) t
                   ),0),2) final_average
            FROM staff s
            WHERE s.active=1 AND s.staff_type=?
            ORDER BY final_average DESC,s.name
            """, year, year, year, year, staffType);
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
