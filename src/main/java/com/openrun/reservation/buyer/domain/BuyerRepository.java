package com.openrun.reservation.buyer.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyerRepository extends JpaRepository<Buyer, Long> {

    Optional<Buyer> findByKakaoId(Long kakaoId);
}
