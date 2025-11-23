package no.kobler.rtb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class AppConfiguration {

    @Bean
    public Random random() {
        // Use a non-seeded Random in production for true randomness
        return new Random();
    }
}
