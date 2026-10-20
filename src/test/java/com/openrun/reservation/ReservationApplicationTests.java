package com.openrun.reservation;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReservationApplicationTests {

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("CI가 실패한 테스트를 잡아내는지 확인하는 임시 테스트")
    void failsOnPurposeForCiCheck() {
        fail("CI 실패 확인용 임시 테스트");
    }

}
