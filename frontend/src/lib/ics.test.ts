import { describe, expect, it } from 'vitest';
import { buildIcs } from './ics';

describe('캘린더 파일', () => {
    const ics = buildIcs({
        uid: 'abc@openrun',
        title: 'NIGHT SIGNAL',
        location: '홍대 라이브홀 B2',
        startsAt: '2026-12-05T10:00:00.000Z',
        endsAt: '2026-12-05T12:30:00.000Z',
        url: 'https://openrun.example/orders/abc',
        description: '입장번호 37 · 38, 예매자 밤산책\n주문 보기',
        stampedAt: '2026-11-05T06:00:00.000Z',
    });

    it('회차 시작·종료 시각을 UTC로 넣고 공연 3시간 전 알림을 단다', () => {
        expect(ics).toContain('DTSTART:20261205T100000Z\r\n');
        expect(ics).toContain('DTEND:20261205T123000Z\r\n');
        expect(ics).toContain('TRIGGER:-PT3H\r\n');
        expect(ics).toContain('URL:https://openrun.example/orders/abc\r\n');
    });

    it('쉼표와 줄바꿈을 이스케이프하고 75바이트가 넘는 줄은 접는다', () => {
        expect(ics).toContain('\\, 예매자');
        expect(ics).toContain('\\n');
        for (const line of ics.split('\r\n')) {
            expect(new TextEncoder().encode(line).length).toBeLessThanOrEqual(75);
        }
    });
});
