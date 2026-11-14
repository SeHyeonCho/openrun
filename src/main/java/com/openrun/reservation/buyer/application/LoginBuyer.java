package com.openrun.reservation.buyer.application;

import com.openrun.reservation.buyer.domain.Buyer;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public record LoginBuyer(Long id, String publicId, String nickname) implements OAuth2User, Serializable {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_BUYER"));

    public static LoginBuyer of(Buyer buyer) {
        return new LoginBuyer(buyer.getId(), buyer.getPublicId(), buyer.getNickname());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("buyerId", publicId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getName() {
        return publicId;
    }
}
