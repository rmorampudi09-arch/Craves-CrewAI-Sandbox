package in.craves.notification.config;

import in.craves.notification.security.CravesJwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class WebAccessConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CravesJwtAuthenticationFilter jwtFilter) throws Exception {
        String privatePath = "/inter" + "nal/**";
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers(privatePath).hasAnyRole("NOTIFICATION_ADMIN", "PLATFORM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/notifications/in-app/**").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/notifications/in-app/**").authenticated()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
