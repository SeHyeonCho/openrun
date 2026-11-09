import type { OrderStatus, ProblemCode, SalesStatus } from '../api/types';
import { currentScenario, type ScenarioId } from './scenarios';

export const MOCK_USER = { id: 'kakao-mock-me', nickname: '밤산책' };
const OTHER_USER = 'kakao-mock-other';

export interface MockShowtime {
    id: number;
    showId: number;
    startsAt: string;
    endsAt: string;
    salesOpensAt: string;
    salesClosesAt: string;
    capacity: number;
    maxSeatsPerBuyer: number;
    takenByOthers: number[];
}

export interface MockShow {
    id: number;
    title: string;
    venue: string;
    posterUrl: string | null;
    showtimeIds: number[];
}

export interface MockOrder {
    token: string;
    showtimeId: number;
    ownerId: string;
    buyerName: string;
    entryNumbers: number[];
    status: OrderStatus;
    heldUntil: string;
    createdAt: string;
}

export interface MockDb {
    scenario: ScenarioId;
    skewMs: number;
    holdMs: number;
    shows: MockShow[];
    showtimes: MockShowtime[];
    orders: MockOrder[];
    once: Record<string, boolean>;
    idempotency: Record<string, IdempotencyRecord>;
}

export interface IdempotencyRecord {
    fingerprint: string;
    token?: string;
    code?: ProblemCode;
}

const DB_KEY = 'openrun.mock.db.v2';
const MINUTE = 60_000;

const kst = (local: string) => new Date(`${local}:00+09:00`).toISOString();
const range = (from: number, to: number) => Array.from({ length: to - from + 1 }, (_, i) => from + i);
const iso = (ms: number) => new Date(ms).toISOString();

export function mockNow(db: MockDb) {
    return Date.now() + db.skewMs;
}

export function createDb(scenario: ScenarioId): MockDb {
    const skewMs = scenario === 'clock-skew' ? 3 * MINUTE : 0;
    const now = Date.now() + skewMs;
    return {
        scenario,
        skewMs,
        holdMs: scenario === 'short-hold' ? 30_000 : 10 * MINUTE,
        shows: [
            {
                id: 1,
                title: 'NIGHT SIGNAL',
                venue: '홍대 라이브홀 B2',
                posterUrl: '/mock/night-signal-poster.png',
                showtimeIds: [11, 12, 13, 14],
            },
            {
                id: 2,
                title: '언더그라운드 나이트 vol.12',
                venue: '문래 소극장 지하',
                posterUrl: null,
                showtimeIds: [21],
            },
        ],
        showtimes: [
            {
                id: 11,
                showId: 1,
                startsAt: kst('2026-12-04T19:30'),
                endsAt: kst('2026-12-04T21:30'),
                salesOpensAt: kst('2026-11-01T20:00'),
                salesClosesAt: iso(now - 60 * MINUTE),
                capacity: 90,
                maxSeatsPerBuyer: 4,
                takenByOthers: range(1, 70),
            },
            {
                id: 12,
                showId: 1,
                startsAt: kst('2026-12-05T19:00'),
                endsAt: kst('2026-12-05T21:30'),
                salesOpensAt: kst('2026-11-01T20:00'),
                salesClosesAt: kst('2026-12-05T21:30'),
                capacity: 90,
                maxSeatsPerBuyer: 4,
                takenByOthers: range(1, 36),
            },
            {
                id: 13,
                showId: 1,
                startsAt: kst('2026-12-06T15:00'),
                endsAt: kst('2026-12-06T17:30'),
                salesOpensAt: kst('2026-11-01T20:00'),
                salesClosesAt: kst('2026-12-06T17:30'),
                capacity: 60,
                maxSeatsPerBuyer: 4,
                takenByOthers: range(1, 60).filter((n) => n !== 12),
            },
            {
                id: 14,
                showId: 1,
                startsAt: kst('2026-12-06T19:00'),
                endsAt: kst('2026-12-06T21:30'),
                salesOpensAt: kst('2026-12-03T20:00'),
                salesClosesAt: kst('2026-12-06T21:30'),
                capacity: 90,
                maxSeatsPerBuyer: 4,
                takenByOthers: [],
            },
            {
                id: 21,
                showId: 2,
                startsAt: kst('2026-12-12T18:00'),
                endsAt: kst('2026-12-12T20:00'),
                salesOpensAt: iso(Math.ceil((now + 30_000) / 1000) * 1000),
                salesClosesAt: kst('2026-12-12T20:00'),
                capacity: 40,
                maxSeatsPerBuyer: 2,
                takenByOthers: [],
            },
        ],
        orders: [
            {
                token: 'held-demo',
                showtimeId: 12,
                ownerId: MOCK_USER.id,
                buyerName: MOCK_USER.nickname,
                entryNumbers: [37, 38],
                status: 'HELD',
                heldUntil: iso(now + 9 * MINUTE + 41_000),
                createdAt: iso(now - 19_000),
            },
            {
                token: 'confirmed-demo',
                showtimeId: 13,
                ownerId: MOCK_USER.id,
                buyerName: MOCK_USER.nickname,
                entryNumbers: [12],
                status: 'CONFIRMED',
                heldUntil: iso(now - 2 * 24 * 60 * MINUTE),
                createdAt: iso(now - 2 * 24 * 60 * MINUTE - 4 * MINUTE),
            },
            {
                token: 'expired-demo',
                showtimeId: 12,
                ownerId: MOCK_USER.id,
                buyerName: MOCK_USER.nickname,
                entryNumbers: [21, 22],
                status: 'EXPIRED',
                heldUntil: iso(now - 3 * 24 * 60 * MINUTE),
                createdAt: iso(now - 3 * 24 * 60 * MINUTE - 10 * MINUTE),
            },
            {
                token: 'cancelled-demo',
                showtimeId: 11,
                ownerId: MOCK_USER.id,
                buyerName: MOCK_USER.nickname,
                entryNumbers: [5],
                status: 'CANCELLED',
                heldUntil: iso(now - 4 * 24 * 60 * MINUTE),
                createdAt: iso(now - 4 * 24 * 60 * MINUTE - 10 * MINUTE),
            },
            {
                token: 'viewer-demo',
                showtimeId: 12,
                ownerId: OTHER_USER,
                buyerName: '새벽달',
                entryNumbers: [39, 40],
                status: 'HELD',
                heldUntil: iso(now + 8 * MINUTE + 27_000),
                createdAt: iso(now - MINUTE),
            },
        ],
        once: {},
        idempotency: {},
    };
}

export function loadDb(): MockDb {
    const scenario = currentScenario();
    try {
        const raw = window.sessionStorage.getItem(DB_KEY);
        if (raw) {
            const db = JSON.parse(raw) as MockDb;
            if (db.scenario === scenario) {
                return db;
            }
        }
    } catch {
        // 저장소를 쓸 수 없으면 새로 만든다
    }
    const db = createDb(scenario);
    saveDb(db);
    return db;
}

export function saveDb(db: MockDb) {
    try {
        window.sessionStorage.setItem(DB_KEY, JSON.stringify(db));
    } catch {
        // 저장소를 쓸 수 없으면 이번 요청 동안만 유지된다
    }
}

export function resetDb() {
    try {
        window.sessionStorage.removeItem(DB_KEY);
    } catch {
        // 저장소를 쓸 수 없으면 지울 것도 없다
    }
}

export function isLive(order: MockOrder, now: number) {
    return order.status === 'CONFIRMED' || (order.status === 'HELD' && now < Date.parse(order.heldUntil));
}

export function takenNumbers(db: MockDb, showtime: MockShowtime, now: number) {
    const taken = new Set(showtime.takenByOthers);
    for (const order of db.orders) {
        if (order.showtimeId === showtime.id && isLive(order, now)) {
            order.entryNumbers.forEach((n) => taken.add(n));
        }
    }
    return taken;
}

export function remainingOf(db: MockDb, showtime: MockShowtime, now: number) {
    return showtime.capacity - takenNumbers(db, showtime, now).size;
}

export function salesStatusOf(db: MockDb, showtime: MockShowtime, now: number): SalesStatus {
    if (now < Date.parse(showtime.salesOpensAt)) {
        return 'BEFORE_SALE';
    }
    if (now >= Date.parse(showtime.salesClosesAt)) {
        return 'SALES_CLOSED';
    }
    return remainingOf(db, showtime, now) === 0 ? 'SOLD_OUT' : 'ON_SALE';
}

export function ownedCountOf(db: MockDb, showtimeId: number, userId: string, now: number) {
    return db.orders
        .filter((order) => order.showtimeId === showtimeId && order.ownerId === userId && isLive(order, now))
        .reduce((sum, order) => sum + order.entryNumbers.length, 0);
}

export function shrinkRemainingTo(db: MockDb, showtime: MockShowtime, left: number, now: number) {
    const taken = takenNumbers(db, showtime, now);
    const free = range(1, showtime.capacity).filter((n) => !taken.has(n));
    showtime.takenByOthers.push(...free.slice(0, Math.max(0, free.length - left)));
}

export function assignNumbers(db: MockDb, showtime: MockShowtime, quantity: number, now: number) {
    const taken = takenNumbers(db, showtime, now);
    const numbers = range(1, showtime.capacity).filter((n) => !taken.has(n)).slice(0, quantity);
    for (const order of db.orders) {
        const lapsed = order.showtimeId === showtime.id && order.status === 'HELD' && !isLive(order, now);
        if (lapsed && order.entryNumbers.some((n) => numbers.includes(n))) {
            order.status = 'EXPIRED';
        }
    }
    return numbers;
}

export function newToken() {
    return crypto.randomUUID().replaceAll('-', '');
}
