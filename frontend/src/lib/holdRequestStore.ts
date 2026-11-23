import type { CreateOrderRequest } from '../api/types';

const STORE_KEY = 'openrun.holdRequests';

export const HOLD_REQUEST_RESEND_LIMIT_MS = 23 * 60 * 60 * 1000;

export type StoredHoldRequest = {
    key: string;
    body: CreateOrderRequest;
    buyerId: string;
    createdAt: string;
};

type Store = Record<string, Record<string, StoredHoldRequest>>;

function storage(): Storage | null {
    try {
        return window.sessionStorage;
    } catch {
        return null;
    }
}

function read(): Store {
    try {
        const raw = storage()?.getItem(STORE_KEY);
        return raw ? (JSON.parse(raw) as Store) : {};
    } catch {
        return {};
    }
}

function write(store: Store) {
    try {
        storage()?.setItem(STORE_KEY, JSON.stringify(store));
    } catch {
        // 저장하지 못하면 이 화면에 있는 동안만 기억한다
    }
}

export function saveHoldRequest(request: StoredHoldRequest) {
    const store = read();
    store[request.buyerId] = { ...store[request.buyerId], [String(request.body.showtimeId)]: request };
    write(store);
}

export function findHoldRequest(buyerId: string, showtimeId: number): StoredHoldRequest | null {
    return read()[buyerId]?.[String(showtimeId)] ?? null;
}

export function hasOtherBuyerHoldRequest(buyerId: string, showtimeId: number) {
    return Object.entries(read()).some(([owner, byShowtime]) => owner !== buyerId && byShowtime[String(showtimeId)] !== undefined);
}

export function removeHoldRequest(request: StoredHoldRequest) {
    const store = read();
    const byShowtime = store[request.buyerId];
    const showtimeId = String(request.body.showtimeId);
    if (!byShowtime || byShowtime[showtimeId]?.key !== request.key) {
        return;
    }
    delete byShowtime[showtimeId];
    if (Object.keys(byShowtime).length === 0) {
        delete store[request.buyerId];
    }
    write(store);
}

export function isPastResendLimit(request: StoredHoldRequest, now: number) {
    return now - Date.parse(request.createdAt) >= HOLD_REQUEST_RESEND_LIMIT_MS;
}
