package in.craves.auth.security;

import in.craves.auth.exception.AuthException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CravesJwtAuthenticationFilter extends OncePerRequestFilter {
    private final CravesJwtService jwtService;

    public CravesJwtAuthenticationFilter(CravesJwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring("Bearer ".length()).trim();
        try {
            AccessTokenClaims claims = jwtService.verifyAccessToken(token);
            CurrentUser currentUser = new CurrentUser(
                claims.identityId(),
                claims.firebaseUid(),
                claims.phoneNumber(),
                claims.roles(),
                claims.tokenVersion()
            );
            List<SimpleGrantedAuthority> authorities = claims.roles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(currentUser, token, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (AuthException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(ex.getStatus().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"code\":\"" + ex.getCode() + "\",\"message\":\"" + ex.getMessage() + "\"}");
        }
    }
}
