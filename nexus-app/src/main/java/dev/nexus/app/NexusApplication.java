package dev.nexus.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"dev.nexus.app", "dev.nexus.core"})
@EntityScan("dev.nexus.core.db.entity")
@EnableJpaRepositories("dev.nexus.core.db.repository")
@ConfigurationPropertiesScan("dev.nexus.core.config")
@EnableRetry
@EnableScheduling
@EnableJpaAuditing
public class NexusApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusApplication.class, args);
    }
}
