package com.openrun.reservation.buyer.application;

import com.openrun.reservation.buyer.domain.Buyer;
import com.openrun.reservation.buyer.domain.BuyerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuyerService {

    private final BuyerRepository buyerRepository;

    @Transactional
    public LoginBuyer loginWithKakao(KakaoAccount account) {
        Buyer buyer = buyerRepository.findByKakaoId(account.kakaoId())
                .map(existing -> {
                    existing.refreshNickname(account.nickname());
                    return existing;
                })
                .orElseGet(() -> buyerRepository.saveAndFlush(
                        Buyer.registerWithKakao(account.kakaoId(), account.nickname())));
        return LoginBuyer.of(buyer);
    }
}
