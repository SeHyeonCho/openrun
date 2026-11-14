package com.openrun.reservation.common.config;

import com.openrun.reservation.buyer.application.KakaoOAuth2UserService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;

@Configuration
public class SecurityConfig {

    private static final String AFTER_LOGIN_PATH = "/";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, KakaoOAuth2UserService kakaoOAuth2UserService)
            throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/showtimes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/orders/*").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .oauth2Login(login -> login
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService))
                        .defaultSuccessUrl(AFTER_LOGIN_PATH, true)
                        .failureUrl(AFTER_LOGIN_PATH))
                .logout(logout -> logout
                        .logoutUrl("/api/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, e) ->
                                writeProblem(response, HttpStatus.FORBIDDEN)));
        return http.build();
    }

    private CookieCsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.secure(true).sameSite("Lax"));
        return repository;
    }

    private void writeProblem(HttpServletResponse response, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d}""".formatted(status.getReasonPhrase(), status.value()));
    }
}
