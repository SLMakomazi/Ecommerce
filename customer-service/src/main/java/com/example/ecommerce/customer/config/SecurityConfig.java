package com.example.ecommerce.customer.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
public class SecurityConfig {
  @Bean
  org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwks) {
    var decoder =
        org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri(jwks).build();
    var audience =
        new org.springframework.security.oauth2.core.OAuth2TokenValidator<
            org.springframework.security.oauth2.jwt.Jwt>() {
          public org.springframework.security.oauth2.core.OAuth2TokenValidatorResult validate(
              org.springframework.security.oauth2.jwt.Jwt jwt) {
            return jwt.getAudience().contains("ecommerce-api")
                ? org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
                : org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                    new org.springframework.security.oauth2.core.OAuth2Error(
                        "invalid_token", "Invalid audience", null));
          }
        };
    var subject =
        new org.springframework.security.oauth2.jwt.JwtClaimValidator<String>(
            "sub",
            value ->
                value != null
                    && value.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"));
    decoder.setJwtValidator(
        new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
            org.springframework.security.oauth2.jwt.JwtValidators.createDefaultWithIssuer(issuer),
            audience,
            subject));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, @Value("${app.internal-key}") String key)
      throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> {
          var access = jwt.getClaimAsMap("realm_access");
          var roles =
              access == null ? List.of() : (List<?>) access.getOrDefault("roles", List.of());
          return roles.stream()
              .<org.springframework.security.core.GrantedAuthority>map(
                  r -> new SimpleGrantedAuthority("ROLE_" + r))
              .toList();
        });
    http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/**")
                    .permitAll()
                    .requestMatchers("/internal/**")
                    .hasRole("INTERNAL")
                    .requestMatchers("/api/products", "/api/products/*")
                    .access(
                        (auth, ctx) ->
                            new org.springframework.security.authorization.AuthorizationDecision(
                                "GET".equals(ctx.getRequest().getMethod())
                                    || auth.get().getAuthorities().stream()
                                        .anyMatch(x -> x.getAuthority().equals("ROLE_admin"))))
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
        .addFilterBefore(
            new OncePerRequestFilter() {
              protected void doFilterInternal(
                  HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                  throws ServletException, IOException {
                if (req.getRequestURI().startsWith("/internal/")) {
                  String supplied = req.getHeader("X-Service-Key");
                  if (supplied == null
                      || key.isBlank()
                      || !MessageDigest.isEqual(
                          key.getBytes(StandardCharsets.UTF_8),
                          supplied.getBytes(StandardCharsets.UTF_8))) {
                    res.sendError(401);
                    return;
                  }
                  SecurityContextHolder.getContext()
                      .setAuthentication(
                          new UsernamePasswordAuthenticationToken(
                              "service",
                              null,
                              List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))));
                }
                chain.doFilter(req, res);
              }
            },
            BearerTokenAuthenticationFilter.class);
    return http.build();
  }
}
