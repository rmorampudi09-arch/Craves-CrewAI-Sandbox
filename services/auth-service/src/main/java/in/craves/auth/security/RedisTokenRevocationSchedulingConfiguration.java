package in.craves.auth.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "craves.token-revocation",
    name = "publisher-enabled",
    havingValue = "true"
)
public class RedisTokenRevocationSchedulingConfiguration {
}
