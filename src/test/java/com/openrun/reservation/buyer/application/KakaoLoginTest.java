package com.openrun.reservation.buyer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.openrun.reservation.DatabaseCleaner;
import com.openrun.reservation.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
class KakaoLoginTest {

    private static final int THREADS = 20;

    @Autowired
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void tearDown() {
        databaseCleaner.clean();
    }

    @Test
    @DisplayName("처음 로그인하면 구매자를 만들고, 같은 카카오 계정으로 다시 로그인하면 같은 구매자로 인식하고 닉네임을 갱신한다")
    void sameKakaoAccountIsSameBuyer() {
        LoginBuyer first = kakaoOAuth2UserService.login(new KakaoAccount(1001L, "밤산책"));
        LoginBuyer again = kakaoOAuth2UserService.login(new KakaoAccount(1001L, "새벽산책"));

        assertThat(again.id()).isEqualTo(first.id());
        assertThat(again.publicId()).isEqualTo(first.publicId()).startsWith("b_");
        assertThat(again.nickname()).isEqualTo("새벽산책");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM buyer", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM buyer WHERE kakao_id = 1001", String.class))
                .isEqualTo("새벽산책");
    }

    @Test
    @DisplayName("다른 카카오 계정은 다른 구매자이고 공개 식별값도 다르다")
    void differentKakaoAccountsAreDifferentBuyers() {
        LoginBuyer first = kakaoOAuth2UserService.login(new KakaoAccount(1001L, "밤산책"));
        LoginBuyer second = kakaoOAuth2UserService.login(new KakaoAccount(1002L, "밤산책"));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.publicId()).isNotEqualTo(first.publicId());
    }

    @Test
    @DisplayName("같은 카카오 계정으로 동시에 처음 로그인해도 구매자는 하나만 생기고 모두 같은 구매자로 로그인한다")
    void concurrentFirstLoginsCreateOneBuyer() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<LoginBuyer> buyers = new ArrayList<>();
        try {
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<LoginBuyer>> futures = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return kakaoOAuth2UserService.login(new KakaoAccount(2001L, "동시 로그인"));
                }));
            }
            ready.await();
            start.countDown();
            for (Future<LoginBuyer> future : futures) {
                buyers.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(buyers).extracting(LoginBuyer::id).containsOnly(buyers.getFirst().id());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM buyer WHERE kakao_id = 2001", Integer.class))
                .isEqualTo(1);
    }
}
