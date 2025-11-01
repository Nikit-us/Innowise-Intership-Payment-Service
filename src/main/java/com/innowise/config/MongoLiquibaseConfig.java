package com.innowise.config;

import com.innowise.exception.LiquibaseMigrationException;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.ext.mongodb.database.MongoLiquibaseDatabase;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.liquibase.enabled", havingValue = "true", matchIfMissing = true)
public class MongoLiquibaseConfig {
    @Value("${spring.data.mongodb.uri}")
    String url;

    @Value("${spring.liquibase.change-log}")
    private String changeLogMaster;

    @Bean
    public Liquibase mongoLiquibase() {
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