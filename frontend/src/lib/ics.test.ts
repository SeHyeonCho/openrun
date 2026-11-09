import { describe, expect, it } from 'vitest';
import { buildIcs } from './ics';

describe('캘린더 파일', () => {
    const event = {
        uid: 'abc@openrun',
        title: 'NIGHT SIGNAL',
        location: '홍대 라이브홀 B2',
        startsAt: '2026-12-05T10:00:00.000Z',
        endsAt: '2026-12-05T12:30:00.000Z',
        url: 'https://openrun.example/orders/abc',
        description: '입장번호 37 · 38, 예매자 밤산책\n주문 보기',
        stampedAt: '2026-11-05T06:00:00.000Z',
    };
    const ics = buildIcs(event);

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

    it('긴 설명은 75바이트 안으로 접고, 접은 줄을 이으면 원래 값과 같다', () => {
        const description = '입장번호 37 · 38, 예매자 밤산책. 공연 시작 30분 전부터 입장합니다'.repeat(3);
        const folded = buildIcs({ ...event, description });

        expect(folded).toContain('\r\n ');
        expect(folded.replace(/\r\n /g, '')).toContain(`DESCRIPTION:${description.replace(/,/g, '\\,')}\r\n`);
        for (const line of folded.split('\r\n')) {
            expect(new TextEncoder().encode(line).length).toBeLessThanOrEqual(75);
        }
    });

    it('세미콜론과 CR을 이스케이프해 값 안에서 새 속성 줄이 생기지 않는다', () => {
        const injected = buildIcs({ ...event, title: 'A;B', location: '홍대\rX-INJECT:1', description: '줄1\r\n줄2' });

        expect(injected).toContain('SUMMARY:A\\;B\r\n');
        expect(injected).toContain('LOCATION:홍대\\nX-INJECT:1\r\n');
        expect(injected).toContain('DESCRIPTION:줄1\\n줄2\r\n');
        expect(injected.replace(/\r\n/g, '')).not.toContain('\r');
    });
});
