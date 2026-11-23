import { delay, http, HttpResponse } from 'msw';
import { IDEMPOTENCY_HEADER } from '../api/queries';
import type {
    AvailabilityResponse,
    CreateOrderRequest,
    MeResponse,
    MyOrderSummary,
    MyOrdersResponse,
    OrderResponse,
    ProblemCode,
    PerformanceResponse,
} from '../api/types';
import {
    holdMsOf,
    issueEntryNumbers,
    isLive,
    loadDb,
    MOCK_USER,
    mockNow,
    newToken,
    ownedCountOf,
    remainingOf,
    salesStatusOf,
    saveDb,
    shrinkRemainingTo,
    type MockDb,
    type MockOrder,
} from './db';
import { currentScenario, isMockLoggedIn, setMockLoggedIn } from './scenarios';

const titles: Record<number, string> = {
    400: 'Bad Request',
    401: 'Unauthorized',
    404: 'Not Found',
    409: 'Conflict',
    422: 'Unprocessable Content',
};

function problem(status: number, code?: ProblemCode, detail?: string) {
    return HttpResponse.json(
        { type: 'about:blank', title: titles[status] ?? 'Error', status, detail, code },
        { status, headers: { 'Content-Type': 'application/problem+json' } },
    );
}

async function slowDown() {
    if (currentScenario() === 'slow') {
        await delay(2000);
    }
}

function firstTime(db: MockDb, key: string) {
    if (db.once[key]) {
        return false;
    }
    db.once[key] = true;
    return true;
}

const serverTime = (db: MockDb) => new Date(mockNow(db)).toISOString();

const meView = (db: MockDb): MeResponse => ({ buyerId: MOCK_USER.buyerId, nickname: MOCK_USER.nickname, serverTime: serverTime(db) });

export function orderView(db: MockDb, order: MockOrder): OrderResponse {
    const showtime = db.showtimes.find((s) => s.id === order.showtimeId)!;
    const performance = db.performances.find((p) => p.id === showtime.performanceId)!;
    return {
        token: order.token,
        status: order.status,
        performance: { id: performance.id, title: performance.title, venue: performance.venue },
        showtime: { id: showtime.id, startsAt: showtime.startsAt, endsAt: showtime.endsAt },
        quantity: order.quantity,
        entryNumbers: order.entryNumbers,
        buyerName: order.buyerName,
        heldUntil: order.heldUntil,
        createdAt: order.createdAt,
        isOwner: isMockLoggedIn() && order.ownerId === MOCK_USER.id,
        serverTime: serverTime(db),
    };
}

const showtimeOf = (db: MockDb, order: MockOrder) => db.showtimes.find((s) => s.id === order.showtimeId)!;

function findOwnOrder(db: MockDb, token: string) {
    return db.orders.find((o) => o.token === token && o.ownerId === MOCK_USER.id);
}

export const handlers = [
    http.post('/api/auth/mock-login', async () => {
        await slowDown();
        setMockLoggedIn(true);
        return HttpResponse.json<MeResponse>(meView(loadDb()));
    }),

    http.post('/api/auth/logout', async () => {
        await slowDown();
        setMockLoggedIn(false);
        return new HttpResponse(null, { status: 204 });
    }),

    http.get('/api/me', async () => {
        await slowDown();
        if (!isMockLoggedIn()) {
            return problem(401);
        }
        return HttpResponse.json<MeResponse>(meView(loadDb()));
    }),

    http.get('/api/performances/:performanceId', async ({ params }) => {
        await slowDown();
        const db = loadDb();
        const performance = db.performances.find((p) => String(p.id) === params.performanceId);
        if (!performance) {
            return problem(404);
        }
        const now = mockNow(db);
        return HttpResponse.json<PerformanceResponse>({
            id: performance.id,
            title: performance.title,
            venue: performance.venue,
            posterUrl: performance.posterUrl,
            showtimes: performance.showtimeIds.map((id) => {
                const st = db.showtimes.find((s) => s.id === id)!;
                return {
                    id: st.id,
                    startsAt: st.startsAt,
                    endsAt: st.endsAt,
                    salesOpensAt: st.salesOpensAt,
                    salesClosesAt: st.salesClosesAt,
                    holdMinutes: Math.round(holdMsOf(db, st) / 60_000) || 1,
                    seatKind: 'GENERAL',
                    salesStatus: salesStatusOf(db, st, now),
                    remaining: remainingOf(db, st, now),
                    maxSeatsPerBuyer: st.maxSeatsPerBuyer,
                };
            }),
            serverTime: serverTime(db),
        });
    }),

    http.get('/api/showtimes/:showtimeId/availability', async ({ params }) => {
        await slowDown();
        const db = loadDb();
        if (!isMockLoggedIn()) {
            return problem(401);
        }
        const st = db.showtimes.find((s) => String(s.id) === params.showtimeId);
        if (!st) {
            return problem(404);
        }
        const now = mockNow(db);
        return HttpResponse.json<AvailabilityResponse>({
            showtimeId: st.id,
            salesStatus: salesStatusOf(db, st, now),
            remaining: remainingOf(db, st, now),
            maxSeatsPerBuyer: st.maxSeatsPerBuyer,
            ownedCount: ownedCountOf(db, st.id, MOCK_USER.id, now),
            serverTime: serverTime(db),
        });
    }),

    http.post('/api/orders', async ({ request }) => {
        const body = ((await request.json().catch(() => null)) ?? {}) as Partial<CreateOrderRequest>;
        const key = request.headers.get(IDEMPOTENCY_HEADER);
        await slowDown();
        const db = loadDb();
        if (!isMockLoggedIn()) {
            return problem(401);
        }
        if (!key) {
            return problem(400, undefined, '선점 요청에는 Idempotency-Key가 필요하다');
        }
        const quantity = Number(body.quantity);
        const buyerName = String(body.buyerName ?? '').trim();
        const fingerprint = JSON.stringify([body.showtimeId, quantity, buyerName]);
        const recordKey = `${MOCK_USER.id}:${key}`;
        const recorded = db.idempotency[recordKey];
        if (recorded) {
            if (recorded.fingerprint !== fingerprint) {
                return problem(422, 'IDEMPOTENCY_KEY_REUSED');
            }
            if (db.scenario === 'hold-lost-always') {
                return HttpResponse.error();
            }
            if (recorded.code) {
                return problem(409, recorded.code);
            }
            const order = db.orders.find((o) => o.token === recorded.token)!;
            return HttpResponse.json<OrderResponse>(orderView(db, order), { status: 201 });
        }
        const reject = (code: ProblemCode) => {
            db.idempotency[recordKey] = { fingerprint, code };
            saveDb(db);
            return problem(409, code);
        };

        if (!Number.isInteger(quantity) || quantity < 1 || buyerName === '') {
            return problem(400, undefined, '수량과 예매자 이름이 필요하다');
        }
        const st = db.showtimes.find((s) => s.id === body.showtimeId);
        if (!st) {
            return problem(404);
        }
        let now = mockNow(db);

        if (db.scenario === 'sales-not-open-once' && firstTime(db, 'hold')) {
            return reject('SALES_NOT_OPEN');
        }
        if (db.scenario === 'sales-closed') {
            st.salesClosesAt = new Date(now).toISOString();
        }
        if (db.scenario === 'seat-not-available' && firstTime(db, 'hold')) {
            shrinkRemainingTo(db, st, 2, now);
        }
        if (db.scenario === 'sold-out-race' && firstTime(db, 'hold')) {
            shrinkRemainingTo(db, st, 0, now);
        }
        if (db.scenario === 'seat-limit' && firstTime(db, 'hold')) {
            const missing = 3 - ownedCountOf(db, st.id, MOCK_USER.id, now);
            if (missing > 0) {
                db.orders.push({
                    token: newToken(),
                    showtimeId: st.id,
                    ownerId: MOCK_USER.id,
                    buyerName,
                    quantity: missing,
                    entryNumbers: issueEntryNumbers(st, missing),
                    status: 'CONFIRMED',
                    heldUntil: new Date(now).toISOString(),
                    createdAt: new Date(now - 30_000).toISOString(),
                });
            }
        }
        if (db.scenario === 'hold-lost-none' && firstTime(db, 'hold')) {
            saveDb(db);
            return HttpResponse.error();
        }

        now = mockNow(db);
        const status = salesStatusOf(db, st, now);
        if (status === 'BEFORE_SALE') {
            return reject('SALES_NOT_OPEN');
        }
        if (status === 'SALES_CLOSED') {
            return reject('SALES_CLOSED');
        }
        if (ownedCountOf(db, st.id, MOCK_USER.id, now) + quantity > st.maxSeatsPerBuyer) {
            return reject('SEAT_LIMIT_EXCEEDED');
        }
        if (remainingOf(db, st, now) < quantity) {
            return reject('SEAT_NOT_AVAILABLE');
        }

        const order: MockOrder = {
            token: newToken(),
            showtimeId: st.id,
            ownerId: MOCK_USER.id,
            buyerName,
            quantity,
            entryNumbers: [],
            status: 'HELD',
            heldUntil: new Date(now + holdMsOf(db, st)).toISOString(),
            createdAt: new Date(now).toISOString(),
        };
        db.orders.push(order);
        db.idempotency[recordKey] = { fingerprint, token: order.token };
        const lost = (db.scenario === 'hold-lost-created' && firstTime(db, 'hold')) || db.scenario === 'hold-lost-always';
        saveDb(db);
        if (lost) {
            return HttpResponse.error();
        }
        return HttpResponse.json<OrderResponse>(orderView(db, order), { status: 201 });
    }),

    http.get('/api/orders/:token', async ({ params }) => {
        await slowDown();
        const db = loadDb();
        const order = db.orders.find((o) => o.token === params.token);
        if (!order) {
            return problem(404);
        }
        return HttpResponse.json<OrderResponse>(orderView(db, order));
    }),

    http.post('/api/orders/:token/confirm', async ({ params }) => {
        await slowDown();
        const db = loadDb();
        if (!isMockLoggedIn()) {
            return problem(401);
        }
        const order = findOwnOrder(db, String(params.token));
        if (!order) {
            return problem(404);
        }
        const now = mockNow(db);
        if (db.scenario === 'confirm-expired' && order.status === 'HELD') {
            order.heldUntil = new Date(now - 1000).toISOString();
        }
        if (db.scenario === 'confirm-cancelled' && order.status === 'HELD') {
            order.status = 'CANCELLED';
        }
        if (order.status === 'CANCELLED') {
            saveDb(db);
            return problem(409, 'ORDER_CANCELLED');
        }
        if (order.status === 'EXPIRED' || (order.status === 'HELD' && !isLive(order, now))) {
            saveDb(db);
            return problem(409, 'ORDER_EXPIRED');
        }
        if (order.status === 'HELD') {
            order.status = 'CONFIRMED';
            order.entryNumbers = issueEntryNumbers(showtimeOf(db, order), order.quantity);
        }
        saveDb(db);
        if (db.scenario === 'confirm-lost-once' && firstTime(db, 'confirm')) {
            saveDb(db);
            return HttpResponse.error();
        }
        return HttpResponse.json<OrderResponse>(orderView(db, order));
    }),

    http.post('/api/orders/:token/cancel', async ({ params }) => {
        await slowDown();
        const db = loadDb();
        if (!isMockLoggedIn()) {
            return problem(401);
        }
        const order = findOwnOrder(db, String(params.token));
        if (!order) {
            return problem(404);
        }
        const now = mockNow(db);
        if (db.scenario === 'cancel-confirmed' && order.status === 'HELD') {
            order.status = 'CONFIRMED';
        }
        if (order.status === 'CONFIRMED') {
            saveDb(db);
            return problem(409, 'ORDER_CONFIRMED');
        }
        if (order.status === 'EXPIRED' || (order.status === 'HELD' && !isLive(order, now))) {
            saveDb(db);
            return problem(409, 'ORDER_EXPIRED');
        }
        order.status = 'CANCELLED';
        saveDb(db);
        return HttpResponse.json<OrderResponse>(orderView(db, order));
    }),

    http.get('/api/me/orders', async () => {
        await slowDown();
        const db = loadDb();
        if (!isMockLoggedIn()) {
            return problem(401);
        }
        const orders: MyOrderSummary[] = db.orders
            .filter((o) => o.ownerId === MOCK_USER.id)
            .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
            .map((o) => {
                const view = orderView(db, o);
                return {
                    token: view.token,
                    status: view.status,
                    performance: view.performance,
                    showtime: view.showtime,
                    quantity: view.quantity,
                    entryNumbers: view.entryNumbers,
                    heldUntil: view.heldUntil,
                    createdAt: view.createdAt,
                };
            });
        return HttpResponse.json<MyOrdersResponse>({ orders, serverTime: serverTime(db) });
    }),
];
