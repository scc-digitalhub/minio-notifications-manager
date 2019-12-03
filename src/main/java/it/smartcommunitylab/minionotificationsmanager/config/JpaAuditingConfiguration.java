package it.smartcommunitylab.minionotificationsmanager.config;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {

    @Bean
    public AuditorAware<String> auditorProvider() {

        // Can use Spring Security to return currently logged in user
        // SecurityContextHolder.getContext().getAuthentication().getName()
        // SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername()

        return () -> Optional.ofNullable("system");
    }
}
