package com.elipair.spacestudyship.auth;

import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@AutoConfigurationPackage(basePackages = "com.elipair.spacestudyship")
@ImportAutoConfiguration({
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        DataJpaRepositoriesAutoConfiguration.class
})
@EnableJpaRepositories(basePackages = "com.elipair.spacestudyship.auth.repository")
public class TestAuthApplication {
}
