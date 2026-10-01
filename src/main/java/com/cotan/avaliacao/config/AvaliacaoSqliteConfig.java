package com.cotan.avaliacao.config;

import com.cotan.avaliacao.service.AvaliacaoSqliteDatabasePath;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class AvaliacaoSqliteConfig {

    @Bean(name = "avaliacaoDataSource")
    public DataSource avaliacaoDataSource(
            @Value("${cotan.avaliacao.sqlite.path:${user.home}/.CotanFx/data/avaliacao.db}") String configuredPath) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(AvaliacaoSqliteDatabasePath.toJdbcUrl(configuredPath));
        config.setPoolName("Cotan-Avaliacao-SQLite");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(20_000);
        config.setConnectionTestQuery("SELECT 1");
        config.setConnectionInitSql("PRAGMA foreign_keys = ON");
        return new HikariDataSource(config);
    }
}
