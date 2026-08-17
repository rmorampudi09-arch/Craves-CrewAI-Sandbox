package in.craves.auth;

import in.craves.auth.config.FirebaseProperties;
import in.craves.auth.config.InternalServiceProperties;
import in.craves.auth.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({FirebaseProperties.class, JwtProperties.class, InternalServiceProperties.class})
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
