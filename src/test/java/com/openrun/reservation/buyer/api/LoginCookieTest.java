package com.openrun.reservation.buyer.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.openrun.reservation.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class LoginCookieTest {

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    @DisplayName("로그인을 시작하면 세션 쿠키를 HttpOnly·Secure·SameSite=Lax로 내려준다")
    void sessionCookieIsHttpOnlySecureAndLax() throws Exception {
        List<String> cookies = setCookies("/oauth2/authorization/kakao");

        assertThat(cookies).filteredOn(cookie -> cookie.startsWith("JSESSIONID="))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).contains("HttpOnly", "Secure", "SameSite=Lax"));
    }

    @Test
    @DisplayName("화면이 읽을 수 있도록 CSRF 토큰 쿠키를 HttpOnly 없이 Secure·SameSite=Lax로 내려준다")
    void csrfCookieIsReadableSecureAndLax() throws Exception {
        List<String> cookies = setCookies("/api/me");

        assertThat(cookies).filteredOn(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .singleElement()
                .satisfies(cookie -> assertThat(cookie).contains("Secure", "SameSite=Lax").doesNotContain("HttpOnly"));
    }

    private List<String> setCookies(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).headers().allValues("Set-Cookie");
    }
}
