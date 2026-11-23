import { describe, expect, it } from 'vitest';
import {
    findHoldRequest,
    hasOtherBuyerHoldRequest,
    HOLD_REQUEST_RESEND_LIMIT_MS,
    isPastResendLimit,
    removeHoldRequest,
    saveHoldRequest,
    type StoredHoldRequest,
} from './holdRequestStore';

const request = (buyerId: string, showtimeId: number, key: string = crypto.randomUUID()): StoredHoldRequest => ({
    key,
    body: { showtimeId, quantity: 2, buyerName: '밤산책' },
    buyerId,
    createdAt: '2026-11-24T01:00:00.000Z',
});

describe('보관한 선점 요청', () => {
    it('구매자와 회차마다 따로 보관하고, 같은 회차에 다시 보관하면 새 요청으로 바꾼다', () => {
        saveHoldRequest(request('b_me', 12, 'first'));
        saveHoldRequest(request('b_me', 14, 'other-showtime'));
        saveHoldRequest(request('b_other', 12, 'other-buyer'));
        saveHoldRequest(request('b_me', 12, 'second'));

        expect(findHoldRequest('b_me', 12)?.key).toBe('second');
        expect(findHoldRequest('b_me', 14)?.key).toBe('other-showtime');
        expect(findHoldRequest('b_other', 12)?.key).toBe('other-buyer');
        expect(findHoldRequest('b_me', 99)).toBeNull();
    });

    it('지우면 그 구매자의 그 회차 요청만 사라진다', () => {
        const mine = request('b_me', 12);
        saveHoldRequest(mine);
        saveHoldRequest(request('b_me', 14));
        saveHoldRequest(request('b_other', 12));

        removeHoldRequest(mine);

        expect(findHoldRequest('b_me', 12)).toBeNull();
        expect(findHoldRequest('b_me', 14)).not.toBeNull();
        expect(findHoldRequest('b_other', 12)).not.toBeNull();
    });

    it('식별자가 다른 요청은 지우지 않는다', () => {
        const older = request('b_me', 12, 'older');
        saveHoldRequest(older);
        saveHoldRequest(request('b_me', 12, 'newer'));

        removeHoldRequest(older);

        expect(findHoldRequest('b_me', 12)?.key).toBe('newer');
    });

    it('같은 회차에 다른 구매자의 요청이 있는지 알려 준다', () => {
        saveHoldRequest(request('b_other', 12));

        expect(hasOtherBuyerHoldRequest('b_me', 12)).toBe(true);
        expect(hasOtherBuyerHoldRequest('b_me', 14)).toBe(false);
        expect(hasOtherBuyerHoldRequest('b_other', 12)).toBe(false);
    });

    it('보관한 지 23시간이 되는 순간부터 같은 식별자로 다시 보낼 수 없다', () => {
        const stored = request('b_me', 12);
        const createdAt = Date.parse(stored.createdAt);

        expect(isPastResendLimit(stored, createdAt + HOLD_REQUEST_RESEND_LIMIT_MS - 1)).toBe(false);
        expect(isPastResendLimit(stored, createdAt + HOLD_REQUEST_RESEND_LIMIT_MS)).toBe(true);
    });
});
