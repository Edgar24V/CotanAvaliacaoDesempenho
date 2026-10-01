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

    public void deleteById(String table, long id) throws SQLException {
        update("DELETE FROM " + table + " WHERE id=?", id);
    }

    public void backup(Path destination) throws IOException, SQLException {
        closeConnectionOnly();
        Files.createDirectories(destination.getParent());
        Files.copy(databasePath, destination, StandardCopyOption.REPLACE_EXISTING);
        open();
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
