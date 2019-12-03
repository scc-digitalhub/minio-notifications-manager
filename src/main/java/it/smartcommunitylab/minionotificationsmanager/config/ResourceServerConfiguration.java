package it.smartcommunitylab.minionotificationsmanager.config;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableWebSecurity
public class ResourceServerConfiguration extends WebSecurityConfigurerAdapter {
    private final static Logger _log = LoggerFactory.getLogger(ResourceServerConfiguration.class);

    @Value("${auth.username}")
    private String USERNAME;

    @Value("${auth.password}")
    private String PASSWORD;

    @Value("${auth.enabled}")
    private boolean authenticate;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        if (authenticate) {
            if (StringUtils.isEmpty(PASSWORD)) {
                // generate on the fly
                PASSWORD = RandomStringUtils.randomAlphanumeric(8);
                _log.warn("Configure basic authentication for user " + USERNAME + " with password " + PASSWORD);
            } else {
                _log.info("Configure basic authentication for user " + USERNAME);
            }

            auth.inMemoryAuthentication()
                    .withUser(USERNAME).password(passwordEncoder().encode(PASSWORD))
                    .authorities("ROLE_ADMIN");
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        if (authenticate) {
            http.authorizeRequests()
                    .antMatchers("/api/**").authenticated()
                    .and().httpBasic()
                    .and().csrf().disable();

        } else {
            http.authorizeRequests().anyRequest().permitAll();
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
