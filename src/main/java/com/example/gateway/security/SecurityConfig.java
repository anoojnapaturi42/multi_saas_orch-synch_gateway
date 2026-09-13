package com.example.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/api/v1/webhooks/ingest/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            java.util.Set<GrantedAuthority> authorities = new java.util.HashSet<>(scopes.convert(jwt));
            addRoles(authorities, jwt.getClaimAsStringList("roles"));
            addRoles(authorities, jwt.getClaimAsStringList("groups"));
            Object realmAccess = jwt.getClaims().get("realm_access");
            if (realmAccess instanceof java.util.Map<?, ?> map && map.get("roles") instanceof java.util.Collection<?> roles) {
                roles.forEach(role -> addRole(authorities, String.valueOf(role)));
            }
            return authorities;
        });
        return converter;
    }

    private void addRoles(java.util.Set<GrantedAuthority> authorities, java.util.Collection<String> roles) {
        if (roles != null) roles.forEach(role -> addRole(authorities, role));
    }

    private void addRole(java.util.Set<GrantedAuthority> authorities, String role) {
        if (role != null && !role.isBlank()) {
            authorities.add(new SimpleGrantedAuthority(role.startsWith("ROLE_") ? role : "ROLE_" + role.toUpperCase(java.util.Locale.ROOT)));
        }
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer) {
        return JwtDecoders.fromIssuerLocation(issuer);
    }
}
