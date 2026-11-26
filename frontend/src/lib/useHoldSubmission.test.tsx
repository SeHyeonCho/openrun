import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import type { ReactNode } from 'react';
import { MemoryRouter, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { IDEMPOTENCY_HEADER } from '../api/queries';
import type { AvailabilityResponse } from '../api/types';
import { createQueryClient } from '../app/queryClient';
import { KAKAO_LOGIN_PATH } from '../auth/useLogin';
import { ToastProvider } from '../components/Toast';
import { server } from '../test/setup';
import { findHoldRequest } from './holdRequestStore';
import { redirectTo } from './navigation';
import { useHoldSubmission } from './useHoldSubmission';

const BUYER = 'b_holdHookTest';
const SHOWTIME = 12;
const BODY = { showtimeId: SHOWTIME, quantity: 1, buyerName: '밤산책' };
const TOKEN = 'a'.repeat(32);

const now = () => new Date().toISOString();
const created = () => HttpResponse.json({ token: TOKEN, serverTime: now() }, { status: 201 });
const problem = (status: number, code?: string) =>
    HttpResponse.json({ status, code, detail: 'test', serverTime: now() }, { status });

const sent: { method: string; path: string; key: string | null }[] = [];
const record = ({ request }: { request: Request }) => {
    sent.push({ method: request.method, path: new URL(request.url).pathname, key: request.headers.get(IDEMPOTENCY_HEADER) });
};
server.events.on('request:start', record);
const holdKeys = () => sent.filter((r) => r.method === 'POST' && r.path === '/api/orders').map((r) => r.key);

function respondInOrder(first: () => Response, ...rest: (() => Response)[]) {
    const responses = [first, ...rest];
    let call = 0;
    server.use(http.post('/api/orders', () => (responses[Math.min(call++, responses.length - 1)] ?? first)()));
}

function renderHold() {
    const queryClient = createQueryClient();
    const refreshAvailability = vi.fn(
        async (): Promise<AvailabilityResponse> => ({
            showtimeId: SHOWTIME,
            salesStatus: 'ON_SALE',
            remaining: 3,
            maxSeatsPerBuyer: 4,
            ownedCount: 0,
            serverTime: now(),
        }),
    );
    const wrapper = ({ children }: { children: ReactNode }) => (
        <QueryClientProvider client={queryClient}>
            <ToastProvider>
                <MemoryRouter initialEntries={['/performances/1/showtimes/12/quantity']}>{children}</MemoryRouter>
            </ToastProvider>
        </QueryClientProvider>
    );
    const view = renderHook(
        () => ({
            hold: useHoldSubmission({ performanceId: 1, showtimeId: SHOWTIME, buyerId: BUYER, refreshAvailability }),
            location: useLocation(),
        }),
        { wrapper },
    );
    return { ...view, refreshAvailability };
}

describe('선점 요청 훅', () => {
    afterEach(() => {
        sent.length = 0;
    });

    it('응답을 받지 못하면 같은 식별자로 다시 보내고, 주문을 받으면 보관한 요청을 지우고 주문 화면으로 간다', async () => {
        respondInOrder(() => HttpResponse.error(), created);
        const { result } = renderHold();

        act(() => result.current.hold.submit(BODY));

        await waitFor(() => expect(result.current.location.pathname).toBe(`/orders/${TOKEN}`), { timeout: 3_000 });
        expect(holdKeys()).toHaveLength(2);
        expect(holdKeys()[1]).toBe(holdKeys()[0]);
        expect(findHoldRequest(BUYER, SHOWTIME)).toBeNull();
    });

    it('응답을 받지 못한 채 다시 보내기 횟수를 다 쓰면 결과를 모르는 단계가 되고 보관한 요청을 남긴다', async () => {
        respondInOrder(() => HttpResponse.error());
        const { result } = renderHold();

        act(() => result.current.hold.submit(BODY));

        await waitFor(() => expect(result.current.hold.phase.kind).toBe('unknown'), { timeout: 5_000 });
        expect(holdKeys()).toHaveLength(3);
        expect(new Set(holdKeys()).size).toBe(1);
        expect(findHoldRequest(BUYER, SHOWTIME)?.key).toBe(holdKeys()[0]);
    });

    it('403이면 GET /api/me로 CSRF 쿠키를 다시 받은 뒤 같은 식별자로 다시 보낸다', async () => {
        respondInOrder(() => problem(403), created);
        const { result } = renderHold();

        act(() => result.current.hold.submit(BODY));

        await waitFor(() => expect(result.current.location.pathname).toBe(`/orders/${TOKEN}`));
        const order = sent.map((r) => `${r.method} ${r.path}`);
        const me = order.indexOf('GET /api/me');
        expect(me).toBeGreaterThan(order.indexOf('POST /api/orders'));
        expect(me).toBeLessThan(order.lastIndexOf('POST /api/orders'));
        expect(new Set(holdKeys()).size).toBe(1);
    });

    it('401이면 보관한 요청을 남긴 채 카카오 로그인으로 보낸다', async () => {
        respondInOrder(() => problem(401));
        const { result } = renderHold();

        act(() => result.current.hold.submit(BODY));

        await waitFor(() => expect(redirectTo).toHaveBeenCalledWith(KAKAO_LOGIN_PATH));
        expect(result.current.hold.phase.kind).toBe('idle');
        expect(holdKeys()).toHaveLength(1);
        expect(findHoldRequest(BUYER, SHOWTIME)?.key).toBe(holdKeys()[0]);
    });

    it('판매 시작 전이라고 답하면 기다렸다가 새 식별자로 다시 보낸다', async () => {
        respondInOrder(() => problem(409, 'SALES_NOT_OPEN'), created);
        const { result } = renderHold();

        act(() => result.current.hold.submit(BODY));

        expect(await screen.findByText('아직 티켓 오픈 전이에요. 잠시 뒤 다시 시도할게요.')).toBeTruthy();
        expect(result.current.hold.phase.kind).toBe('waitingOpen');
        await waitFor(() => expect(result.current.location.pathname).toBe(`/orders/${TOKEN}`), { timeout: 4_000 });
        expect(holdKeys()).toHaveLength(2);
        expect(holdKeys()[1]).not.toBe(holdKeys()[0]);
    });

    it('계정이 바뀌었다고 답하면 다시 보내지 않고 보관한 요청을 남긴다', async () => {
        respondInOrder(() => problem(409, 'BUYER_CHANGED'));
        const { result } = renderHold();

        act(() => result.current.hold.submit(BODY));

        await waitFor(() => expect(result.current.hold.phase.kind).toBe('buyerChanged'));
        expect(holdKeys()).toHaveLength(1);
        expect(findHoldRequest(BUYER, SHOWTIME)?.key).toBe(holdKeys()[0]);
    });

    it('남은 좌석이 부족하다고 답하면 화면이 넘긴 함수로 구매 가능 수를 다시 받고 보관한 요청을 지운다', async () => {
        respondInOrder(() => problem(409, 'SEAT_NOT_AVAILABLE'));
        const { result, refreshAvailability } = renderHold();

        act(() => result.current.hold.submit(BODY));

        await waitFor(() => expect(refreshAvailability).toHaveBeenCalledTimes(1));
        expect(result.current.hold.phase.kind).toBe('idle');
        expect(findHoldRequest(BUYER, SHOWTIME)).toBeNull();
    });
});
