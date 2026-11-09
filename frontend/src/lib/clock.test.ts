import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatCountdown, hasReached, msUntil, resetServerTime, serverNow, serverOffsetMs, syncServerTime } from './clock';

describe('서버 시각 보정', () => {
    afterEach(() => {
        vi.useRealTimers();
        resetServerTime();
    });

    it('요청을 보낸 시각과 받은 시각의 중간을 서버 시각과 비교해 차이를 구한다', () => {
        syncServerTime('2026-12-01T11:00:10.000Z', Date.parse('2026-12-01T11:00:00.000Z'), Date.parse('2026-12-01T11:00:02.000Z'));

        expect(serverOffsetMs()).toBe(9_000);
    });

    it('기기 시계가 늦어도 서버 시각 기준으로 남은 시간을 센다', () => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date('2026-12-01T10:57:00.000Z'));
        syncServerTime('2026-12-01T11:00:00.000Z', Date.now(), Date.now());

        expect(serverNow()).toBe(Date.parse('2026-12-01T11:00:00.000Z'));
        expect(msUntil('2026-12-01T11:00:30.000Z')).toBe(30_000);
    });

    it('시각을 읽을 수 없는 응답은 보정에 쓰지 않는다', () => {
        syncServerTime('2026-12-01T11:00:10.000Z', 0, 0);
        const before = serverOffsetMs();

        syncServerTime('잘못된 시각', 0, 0);

        expect(serverOffsetMs()).toBe(before);
    });
});

describe('시각 경계', () => {
    it('목표 시각 정각부터 도달한 것으로 본다', () => {
        const target = '2026-12-01T11:00:00.000Z';

        expect(hasReached(target, Date.parse(target) - 1)).toBe(false);
        expect(hasReached(target, Date.parse(target))).toBe(true);
        expect(msUntil(target, Date.parse(target) + 5_000)).toBe(0);
    });
});

describe('남은 시간 표시', () => {
    it.each([
        [0, '00:00'],
        [1, '00:01'],
        [999, '00:01'],
        [1_000, '00:01'],
        [1_001, '00:02'],
        [9 * 60_000 + 41_000, '09:41'],
        [60 * 60_000, '1:00:00'],
        [26 * 60 * 60_000 + 5_000, '1일 02:00:05'],
    ])('%d ms 남으면 %s로 보여 준다', (ms, expected) => {
        expect(formatCountdown(ms)).toBe(expected);
    });
});
