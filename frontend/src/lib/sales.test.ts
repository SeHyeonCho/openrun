import { describe, expect, it } from 'vitest';
import type { ShowtimeSummary } from '../api/types';
import { effectiveSalesStatus } from './sales';

const showtime = (overrides: Partial<ShowtimeSummary>): ShowtimeSummary => ({
    id: 1,
    startsAt: '2026-12-05T10:00:00.000Z',
    endsAt: '2026-12-05T12:30:00.000Z',
    salesOpensAt: '2026-12-01T11:00:00.000Z',
    salesClosesAt: '2026-12-05T12:30:00.000Z',
    seatKind: 'FREE',
    salesStatus: 'BEFORE_SALE',
    remaining: 52,
    maxSeatsPerBuyer: 4,
    ...overrides,
});

const at = (iso: string) => Date.parse(iso);

describe('회차 판매 상태', () => {
    it('판매 전 회차는 판매 시작 시각 정각부터 판매 중으로 바뀐다', () => {
        const target = showtime({ salesStatus: 'BEFORE_SALE' });

        expect(effectiveSalesStatus(target, at('2026-12-01T10:59:59.999Z'))).toBe('BEFORE_SALE');
        expect(effectiveSalesStatus(target, at('2026-12-01T11:00:00.000Z'))).toBe('ON_SALE');
    });

    it('판매 마감 시각 정각부터 판매 마감으로 본다', () => {
        const target = showtime({ salesStatus: 'ON_SALE' });

        expect(effectiveSalesStatus(target, at('2026-12-05T12:29:59.999Z'))).toBe('ON_SALE');
        expect(effectiveSalesStatus(target, at('2026-12-05T12:30:00.000Z'))).toBe('SALES_CLOSED');
    });

    it('서버가 매진이나 판매 마감이라고 답하면 시각과 상관없이 그대로 따른다', () => {
        const now = at('2026-12-02T00:00:00.000Z');

        expect(effectiveSalesStatus(showtime({ salesStatus: 'SOLD_OUT' }), now)).toBe('SOLD_OUT');
        expect(effectiveSalesStatus(showtime({ salesStatus: 'SALES_CLOSED' }), now)).toBe('SALES_CLOSED');
    });
});
