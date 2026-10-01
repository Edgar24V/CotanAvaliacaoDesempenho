package com.cotan.avaliacao.service;

import java.nio.file.Files;
import java.nio.file.Path;

public final class AvaliacaoSqliteDatabasePath {
    private AvaliacaoSqliteDatabasePath() {}

    public static Path resolve(String configuredPath) {
        Path path = Path.of(configuredPath);
        try {
            Path parent = path.toAbsolutePath().normalize().getParent();
            if (parent != null) Files.createDirectories(parent);
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível preparar a pasta do SQLite: " + path, e);
        }
        return path.toAbsolutePath().normalize();
    }

    public static String toJdbcUrl(String configuredPath) {
        return "jdbc:sqlite:" + resolve(configuredPath);
    }
}
