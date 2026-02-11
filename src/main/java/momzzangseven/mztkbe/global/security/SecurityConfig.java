package momzzangseven.mztkbe.global.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security Configuration.
 *
 * <p>Responsibilities: - Configure authentication and authorization rules - Set up JWT-based
 * stateless authentication - Define public and protected endpoints - Disable CSRF for REST API
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter;
  private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
  private final RestAccessDeniedHandler restAccessDeniedHandler;
  private final SecurityCorsProperties securityCorsProperties;

  /** CORS configuration. CORS는 URL path(/callback 등)가 아니라 Origin(스킴+도메인+포트) 기준으로 허용합니다. */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();

    config.setAllowedOrigins(
        securityCorsProperties.getAllowedOrigins() == null
            ? List.of()
            : securityCorsProperties.getAllowedOrigins());

    config.setAllowCredentials(true);

    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  /** Configure stateless security filter chain, JWT auth, and request authorization rules. */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Disable CSRF (not needed for stateless REST API with JWT)
        .csrf(AbstractHttpConfigurer::disable)

        // Enable CORS (uses CorsConfigurationSource bean)
        .cors(Customizer.withDefaults())

        // Set session management to STATELESS (using JWT, no server-side session)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // Return JSON responses for 401/403 instead of default HTML
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(restAuthenticationEntryPoint)
                    .accessDeniedHandler(restAccessDeniedHandler))

        // Configure authorization rules
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public endpoints (no authentication required)
                    .requestMatchers(HttpMethod.POST, "/auth/signup")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/login")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/reactivate")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/reissue")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/logout")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/auth/stepup")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/users/me/role")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/withdrawal")
                    .hasAuthority("ROLE_STEP_UP")
                    .requestMatchers(HttpMethod.GET, "/levels/policies")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/level-ups")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/web3/challenges")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/web3/wallets")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/web3/wallets/")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/locations/register")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/locations/verify")
                    .authenticated()
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**")
                    .permitAll()

                    // Health check and monitoring endpoints
                    .requestMatchers("/actuator/**")
                    .permitAll()

                    // All other requests require authentication
                    .anyRequest()
                    .authenticated());

    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
