package in.craves.subscription.config;

import in.craves.subscription.security.CravesJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private static final RegexRequestMatcher ORDER_CREATED_INTERNAL_CALLBACK = new RegexRequestMatcher(
        "^/internal/v1/subscription-occurrences/[^/]+/order-created$",
        HttpMethod.POST.name()
    );

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CravesJwtAuthenticationFilter filter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/subscriptions/plans", "/api/v1/subscriptions/plans/**").permitAll()
                .requestMatchers(ORDER_CREATED_INTERNAL_CALLBACK).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
