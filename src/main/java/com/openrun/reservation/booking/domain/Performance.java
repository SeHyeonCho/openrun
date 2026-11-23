package com.openrun.reservation.booking.domain;

import com.openrun.reservation.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Performance extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(length = 500)
    private String posterUrl;

    private Performance(String title, String venue, String posterUrl) {
        this.title = title;
        this.venue = venue;
        this.posterUrl = posterUrl;
    }

    public static Performance create(String title, String venue) {
        return create(title, venue, null);
    }

    public static Performance create(String title, String venue, String posterUrl) {
        return new Performance(title, venue, posterUrl);
    }
}
