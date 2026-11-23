import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { api } from '../api/queries';
import { loadDb, MOCK_USER, mockNow, remainingOf, saveDb, shrinkRemainingTo } from './db';
import { setMockLoggedIn, storeScenario, type ScenarioId } from './scenarios';

const request = { showtimeId: 12, quantity: 1, buyerName: '밤산책' };

const prepare = async (scenario: ScenarioId = 'default') => {
    storeScenario(scenario);
    setMockLoggedIn(true);
    const db = loadDb();
    db.orders = db.orders.filter((o) => o.token !== 'held-demo');
    saveDb(db);
    await api.getMe();
};

const rejection = (promise: Promise<unknown>) => promise.then(() => null, (error: unknown) => error as ApiError);

const myOrderCount = () => loadDb().orders.filter((o) => o.ownerId === MOCK_USER.id && o.showtimeId === 12).length;

describe('목 API', () => {
    beforeEach(() => prepare());

    it('CSRF 토큰 헤더가 없는 POST는 서버처럼 403으로 거부하고 주문을 만들지 않는다', async () => {
        const before = myOrderCount();

        const response = await fetch(new URL('/api/orders', window.location.href), {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
            body: JSON.stringify(request),
        });

        expect(response.status).toBe(403);
        expect(myOrderCount()).toBe(before);
    });

    it('선점 요청이 겹쳐도 서로의 주문을 덮어쓰지 않고, 선점한 주문에는 입장번호를 주지 않는다', async () => {
        const [first, second] = await Promise.all([api.createOrder(request, crypto.randomUUID()), api.createOrder(request, crypto.randomUUID())]);

        expect(first.token).not.toBe(second.token);
        expect(first.entryNumbers).toEqual([]);
        expect(second.entryNumbers).toEqual([]);
        const tokens = loadDb().orders.map((o) => o.token);
        expect(tokens).toContain(first.token);
        expect(tokens).toContain(second.token);
    });

    it('입장번호는 확정한 순서대로 회차의 다음 번호를 주고, 한 주문의 여러 장은 이어진 번호를 받으며, 다시 확정해도 바뀌지 않는다', async () => {
        const early = await api.createOrder({ ...request, quantity: 2 }, crypto.randomUUID());
        const late = await api.createOrder(request, crypto.randomUUID());

        const lateConfirmed = await api.confirmOrder(late.token);
        const earlyConfirmed = await api.confirmOrder(early.token);
        const again = await api.confirmOrder(early.token);

        expect(lateConfirmed.entryNumbers).toEqual([37]);
        expect(earlyConfirmed.entryNumbers).toEqual([38, 39]);
        expect(again.entryNumbers).toEqual([38, 39]);
    });

    it('잔여석보다 많은 선점 요청이 동시에 와도 잔여석만큼만 성공한다', async () => {
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
        expect(remainingOf(after, after.showtimes.find((s) => s.id === 12)!, mockNow(after))).toBe(0);
    });

    it('지정석 회차를 수량으로 선점하면 서버처럼 400으로 거부한다', async () => {
        const rejected = await rejection(api.createOrder({ ...request, showtimeId: 15 }, crypto.randomUUID()));

        expect(rejected?.status).toBe(400);
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
        await prepare('sales-not-open-once');
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
