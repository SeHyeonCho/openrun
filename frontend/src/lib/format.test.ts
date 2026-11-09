import { describe, expect, it } from 'vitest';
import { formatDateSpan } from './format';

describe('공연 날짜 범위', () => {
    it('첫 회차와 마지막 회차 날짜를 잇는다', () => {
        expect(formatDateSpan(['2026-12-06T10:00:00Z', '2026-12-04T10:30:00Z'])).toBe('2026.12.04 – 12.06');
    });

    it('회차가 없으면 날짜를 비운다', () => {
        expect(formatDateSpan([])).toBe('');
    });
});
