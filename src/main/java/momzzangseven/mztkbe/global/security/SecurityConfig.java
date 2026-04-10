package momzzangseven.mztkbe.global.security;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
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

  /**
   * Role hierarchy: ADMIN_SEED and ADMIN_GENERATED inherit ROLE_ADMIN, which inherits ROLE_TRAINER,
   * which inherits ROLE_USER. This means hasAuthority("ROLE_ADMIN") automatically matches
   * ADMIN_SEED and ADMIN_GENERATED.
   */
  @Bean
  public RoleHierarchy roleHierarchy() {
    return RoleHierarchyImpl.fromHierarchy(
        """
        ROLE_ADMIN_SEED > ROLE_ADMIN
        ROLE_ADMIN_GENERATED > ROLE_ADMIN
        ROLE_ADMIN > ROLE_TRAINER
        ROLE_TRAINER > ROLE_USER
        """);
  }

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
                    // --- Public Endpoints ---
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
                    .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**")
                    .permitAll()
                    .requestMatchers("/actuator/health")
                    .permitAll()

                    // --- Auth Endpoints ---
                    .requestMatchers(HttpMethod.POST, "/auth/stepup")
                    .authenticated()

                    // --- User & Me Endpoints ---
                    .requestMatchers(HttpMethod.GET, "/users/me")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/auth/withdrawal")
                    .hasAuthority("ROLE_STEP_UP")
                    .requestMatchers(HttpMethod.PATCH, "/users/me/role")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/attendance")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/users/me/attendance/status")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/users/me/attendance/weekly")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/users/me/level")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/level-ups")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/users/me/level-up-histories")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/users/me/xp-ledger")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/locations/register")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/users/me/transfers")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/users/me/transfers/{resourceId}")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.GET, "/users/me/web3/execution-intents/{executionIntentId}")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/users/me/web3/execution-intents/{executionIntentId}/execute")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/verification/photo")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/verification/record")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/verification/{verificationId}")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/verification/today-completion")
                    .authenticated()

                    // --- Level Policies ---
                    .requestMatchers(HttpMethod.GET, "/levels/policies")
                    .authenticated()

                    // --- Location Endpoints ---
                    .requestMatchers(HttpMethod.POST, "/locations/verify")
                    .authenticated()

                    // --- Web3 Endpoints ---
                    .requestMatchers(HttpMethod.POST, "/web3/challenges")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/web3/wallets")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/web3/wallets/{walletAddress}")
                    .authenticated()

                    // --- Post (Community) Endpoints ---
                    .requestMatchers(HttpMethod.POST, "/posts/question")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/posts/free")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/posts")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/posts/{postId}")
                    .authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/posts/{postId}")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/posts/{postId}")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/posts/{postId}/likes")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/posts/{postId}/likes")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/questions/{postId}/answers")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/questions/{postId}/answers")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/questions/{postId}/answers/{answerId}")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/questions/{postId}/answers/{answerId}")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.POST, "/questions/{postId}/answers/{answerId}/likes")
                    .authenticated()
                    .requestMatchers(
                        HttpMethod.DELETE, "/questions/{postId}/answers/{answerId}/likes")
                    .authenticated()
                    .requestMatchers(HttpMethod.DELETE, "/users/me/locations/**")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "users/me/locations")
                    .authenticated()

                    // --- Admin Endpoints ---
                    .requestMatchers(HttpMethod.POST, "/admin/recovery/reseed")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/admin/accounts")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(HttpMethod.GET, "/admin/accounts")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/accounts/*/password/reset")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/auth/password")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(HttpMethod.POST, "/admin/web3/treasury-keys/provision")
                    .hasAuthority("ROLE_ADMIN")
                    .requestMatchers(
                        HttpMethod.POST, "/admin/web3/transactions/{txId}/mark-succeeded")
                    .hasAuthority("ROLE_ADMIN")

                    // --- Image Endpoints ---
                    .requestMatchers(HttpMethod.POST, "/images/presigned-urls")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/images")
                    .authenticated()

                    // --- Actuator Endpoints ---
                    .requestMatchers("/actuator/info")
                    .authenticated()
                    .requestMatchers("/actuator/**")
                    .hasAuthority("ROLE_ADMIN")

                    // --- Marketplace Endpoints ---
                    .requestMatchers(HttpMethod.PUT, "/marketplace/trainer/store")
                    .hasAuthority("ROLE_TRAINER")
                    .requestMatchers(HttpMethod.GET, "/marketplace/trainer/store")
                    .hasAuthority("ROLE_TRAINER")

                    // --- Internal Endpoints ---

                    .requestMatchers(
                        "/internal/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()

                    // Fallback
                    .anyRequest()
                    .authenticated());

    http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }
}
