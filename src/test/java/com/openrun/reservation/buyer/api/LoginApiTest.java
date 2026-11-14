package com.openrun.reservation.buyer.api;

import static com.openrun.reservation.TestLogin.loginAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openrun.reservation.TestcontainersConfiguration;
import com.openrun.reservation.buyer.application.LoginBuyer;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class LoginApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("카카오 로그인 경로는 카카오 인가 화면으로 보낸다")
    void redirectsToKakaoAuthorization() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/kakao"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", allOf(
                        startsWith("https://kauth.kakao.com/oauth/authorize?"),
                        containsString("client_id=test-kakao-client-id"),
                        containsString("scope=profile_nickname"))));
    }

    @Test
    @DisplayName("로그인한 구매자를 조회하면 공개 식별값과 닉네임, 서버 시각을 반환한다")
    void returnsLoginBuyer() throws Exception {
        mockMvc.perform(get("/api/me").with(loginAs(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buyerId").value("b_test7"))
                .andExpect(jsonPath("$.nickname").value("구매자7"))
                .andExpect(jsonPath("$.serverTime").isString());
    }

    @Test
    @DisplayName("로그인하지 않고 구매자 조회·선점·확정을 요청하면 401을 반환하고 주문을 만들지 않는다")
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(post("/api/orders").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showtimeId\": 1, \"seatIds\": [1]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders/{orderId}/confirm", 1L).with(csrf()))
                .andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("CSRF 토큰 없이 상태를 바꾸는 요청을 보내면 403을 반환하고 처리하지 않는다")
    void rejectsRequestsWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/orders").with(loginAs(1L)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"showtimeId\": 1, \"seatIds\": [1]}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        MockHttpSession session = loggedInSession();
        mockMvc.perform(post("/api/logout").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("로그아웃하면 204를 반환하고 세션이 끝나며, 로그인하지 않은 상태에서도 204를 반환한다")
    void logsOut() throws Exception {
        MockHttpSession session = loggedInSession();
        mockMvc.perform(get("/api/me").session(session)).andExpect(status().isOk());

        mockMvc.perform(post("/api/logout").session(session).with(csrf())).andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/logout").with(csrf())).andExpect(status().isNoContent());
    }

    private MockHttpSession loggedInSession() {
        LoginBuyer buyer = new LoginBuyer(9L, "b_session", "세션 구매자");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, new SecurityContextImpl(
                new OAuth2AuthenticationToken(buyer, buyer.getAuthorities(), "kakao")));
        return session;
    }
}
