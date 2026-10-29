package de.chriswohlbrecht.maintenance.configuration;

import de.chriswohlbrecht.maintenance.component.IInitialAdminComponent;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableConfigurationProperties({InitialAdminProperties.class, AccountMailProperties.class, AltchaProperties.class})
public class UserAccountConfiguration {

    /** Delegating encoder (BCrypt by default); hashes carry an {id} prefix so the algorithm can change later. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Household mode has no passwords, so the initial admin is just a profile there. */
    @Bean
    @Conditional(OperatingModeCondition.Portal.class)
    public ApplicationRunner initialAdminPasswordRunner(IInitialAdminComponent initialAdminComponent) {
        return args -> initialAdminComponent.initializePassword();
    }
}
