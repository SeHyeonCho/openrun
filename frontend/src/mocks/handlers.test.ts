import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { api } from '../api/queries';
import { isLive, loadDb, MOCK_USER, mockNow, remainingOf, saveDb, shrinkRemainingTo } from './db';
import { setMockLoggedIn, storeScenario, type ScenarioId } from './scenarios';

const request = { showtimeId: 12, quantity: 1, buyerName: '밤산책' };

const prepare = (scenario: ScenarioId = 'default') => {
    storeScenario(scenario);
    setMockLoggedIn(true);
    const db = loadDb();
    db.orders = db.orders.filter((o) => o.token !== 'held-demo');
    saveDb(db);
};

const rejection = (promise: Promise<unknown>) => promise.then(() => null, (error: unknown) => error as ApiError);

const myOrderCount = () => loadDb().orders.filter((o) => o.ownerId === MOCK_USER.id && o.showtimeId === 12).length;

describe('목 API', () => {
    beforeEach(() => prepare());

    it('선점 요청이 겹쳐도 서로의 주문을 덮어쓰지 않고 다른 입장번호를 배정한다', async () => {
        const [first, second] = await Promise.all([api.createOrder(request, crypto.randomUUID()), api.createOrder(request, crypto.randomUUID())]);

        expect(first.entryNumbers).not.toEqual(second.entryNumbers);
        const tokens = loadDb().orders.map((o) => o.token);
        expect(tokens).toContain(first.token);
        expect(tokens).toContain(second.token);
    });

    it('잔여석보다 많은 선점 요청이 동시에 와도 잔여석만큼만 성공하고 입장번호가 겹치지 않는다', async () => {
        const db = loadDb();
        const showtime = db.showtimes.find((s) => s.id === 12)!;
        shrinkRemainingTo(db, showtime, 3, mockNow(db));
        saveDb(db);

        const results = await Promise.allSettled(Array.from({ length: 8 }, () => api.createOrder(request, crypto.randomUUID())));

        const succeeded = results.filter((r) => r.status === 'fulfilled');
        const rejected = results.filter((r): r is PromiseRejectedResult => r.status === 'rejected');
        expect(succeeded).toHaveLength(3);
        expect(rejected.map((r) => (r.reason as ApiError).code)).toEqual(Array(5).fill('SEAT_NOT_AVAILABLE'));
        const after = loadDb();
        const held = after.orders.filter((o) => o.showtimeId === 12 && isLive(o, mockNow(after))).flatMap((o) => o.entryNumbers);
        expect(new Set(held).size).toBe(held.length);
        expect(remainingOf(after, after.showtimes.find((s) => s.id === 12)!, mockNow(after))).toBe(0);
    });

    it('같은 구매자가 같은 식별자와 같은 내용으로 다시 보내면 처음 만든 주문을 그대로 돌려준다', async () => {
        const key = crypto.randomUUID();
        const before = myOrderCount();

        const first = await api.createOrder(request, key);
        const again = await api.createOrder(request, key);

        expect(again.token).toBe(first.token);
        expect(myOrderCount()).toBe(before + 1);
    });

    it('같은 식별자로 두 요청이 동시에 와도 주문은 하나만 생긴다', async () => {
        const key = crypto.randomUUID();
        const before = myOrderCount();

        const [first, second] = await Promise.all([api.createOrder(request, key), api.createOrder(request, key)]);

        expect(second.token).toBe(first.token);
        expect(myOrderCount()).toBe(before + 1);
    });

    it('규칙 위반(409)도 기억해서, 같은 식별자로 다시 보내면 같은 거부를 돌려준다', async () => {
        prepare('sales-not-open-once');
        const key = crypto.randomUUID();

        const first = await rejection(api.createOrder(request, key));
        const again = await rejection(api.createOrder(request, key));

        expect(first?.code).toBe('SALES_NOT_OPEN');
        expect(again?.code).toBe('SALES_NOT_OPEN');
    });

    it('같은 식별자를 다른 내용으로 보내면 식별자를 잘못 다시 쓴 것으로 보고 거부한다', async () => {
        const key = crypto.randomUUID();
        await api.createOrder(request, key);

        const reused = await rejection(api.createOrder({ ...request, quantity: 2 }, key));

        expect(reused?.status).toBe(422);
        expect(reused?.code).toBe('IDEMPOTENCY_KEY_REUSED');
    });
});
