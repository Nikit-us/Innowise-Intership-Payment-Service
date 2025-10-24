package com.innowise.config;

import com.innowise.exception.LiquibaseMigrationException;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MongoLiquibaseConfig {

    @Value("${spring.data.mongodb.host}")
    private String host;

    @Value("${spring.data.mongodb.port}")
    private int port;

    @Value("${spring.data.mongodb.database}")
    private String database;

    @Value("${spring.data.mongodb.username}")
    private String username;

    @Value("${spring.data.mongodb.password}")
    private String password;

    @Value("${spring.liquibase.change-log}")
    private String changeLogMaster;

    @Bean
    public Liquibase mongoLiquibase() {
        String url = String.format("mongodb://%s:%s@%s:%d/%s?authSource=admin",
                username, password, host, port, database);
        try {
            MongoLiquibaseDatabase mongoDatabase = (MongoLiquibaseDatabase)
                    DatabaseFactory.getInstance().openDatabase(url, null, null, null, null);

            Liquibase liquibase = new Liquibase(
                    changeLogMaster,
                    new ClassLoaderResourceAccessor(),
                    mongoDatabase
            );

            liquibase.update();
            return liquibase;
        } catch (Exception e) {
            throw new LiquibaseMigrationException("Migration error", e);
        }
    }
}