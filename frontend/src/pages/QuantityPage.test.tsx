import { fireEvent, screen, waitFor } from '@testing-library/react';
import { delay, http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EXPECTED_BUYER_HEADER, IDEMPOTENCY_HEADER } from '../api/queries';
import { findHoldRequest, HOLD_REQUEST_RESEND_LIMIT_MS, saveHoldRequest } from '../lib/holdRequestStore';
import { redirectTo } from '../lib/navigation';
import { loadDb, MOCK_USER, type MockDb } from '../mocks/db';
import { disabled, finishKakaoLogin, renderRoute } from '../test/render';
import { server } from '../test/setup';

const PATH = '/performances/1/showtimes/12/quantity';

const sentKeys: (string | null)[] = [];
const sentBuyers: (string | null)[] = [];
server.events.on('request:start', ({ request }) => {
    if (request.method === 'POST' && new URL(request.url).pathname === '/api/orders') {
        sentKeys.push(request.headers.get(IDEMPOTENCY_HEADER));
        sentBuyers.push(request.headers.get(EXPECTED_BUYER_HEADER));
    }
});

const myHeldOrders = () => loadDb().orders.filter((o) => o.ownerId === MOCK_USER.id && o.showtimeId === 12 && o.status === 'HELD');

const storedHold = () => findHoldRequest(MOCK_USER.buyerId, 12);

const withoutOwnHold = (db: MockDb) => {
    db.orders.find((o) => o.token === 'held-demo')!.status = 'CANCELLED';
};

const pick = async (quantity: number) => {
    const increase = await screen.findByRole('button', { name: '한 장 더하기' });
    for (let i = 1; i < quantity; i++) {
        fireEvent.click(increase);
    }
};

const hold = (quantity: number) => fireEvent.click(screen.getByRole('button', { name: `${quantity}매 선택 완료` }));

describe('수량 선택', () => {
    afterEach(() => {
        sentKeys.length = 0;
        sentBuyers.length = 0;
    });

    it('회차당 1인 매수에서 이미 예매한 매수를 뺀 만큼까지만 고를 수 있다', async () => {
        renderRoute(PATH, { loggedIn: true });

        await pick(3);

        expect(screen.getByText('회차당 1인 4매 · 이미 예매한 2매 · 지금 2매까지 고를 수 있어요.')).toBeTruthy();
        expect(disabled(screen.getByRole('button', { name: '한 장 더하기' }))).toBe(true);
        expect(screen.getByRole('button', { name: '2매 선택 완료' })).toBeTruthy();
    });

    it('지정석 회차의 매수 선택 주소로 들어오면 구매 가능 수를 묻거나 선점하지 않고 공연 페이지로 안내한다', async () => {
        const requested: string[] = [];
        const record = ({ request }: { request: Request }) => requested.push(new URL(request.url).pathname);
        server.events.on('request:start', record);

        renderRoute('/performances/1/showtimes/15/quantity', { loggedIn: true });

        expect(await screen.findByText('지정석은 아직 이 화면에서 예매할 수 없어요')).toBeTruthy();
        expect(screen.getByRole('link', { name: '공연 페이지로' }).getAttribute('href')).toBe('/performances/1');
        expect(screen.queryByRole('button', { name: /선택 완료/ })).toBeNull();
        expect(requested).not.toContain('/api/showtimes/15/availability');
        expect(requested).not.toContain('/api/orders');
        server.events.removeListener('request:start', record);
    });

    it('확정까지 주어지는 시간은 고른 회차의 선점 유지 시간으로 알린다', async () => {
        renderRoute(PATH, {
            loggedIn: true,
            prepare: (db) => {
                db.showtimes.find((s) => s.id === 12)!.holdMinutes = 5;
            },
        });

        expect(await screen.findByText('선택한 뒤 5분 안에 예매를 완료해 주세요.')).toBeTruthy();
    });

    it('예매자 이름은 카카오 닉네임으로 채워 두고, 비우면 선점할 수 없다', async () => {
        renderRoute(PATH, { loggedIn: true });

        const name = (await screen.findByLabelText('예매자 이름')) as HTMLInputElement;
        expect(name.value).toBe('밤산책');

        fireEvent.change(name, { target: { value: '  ' } });

        expect(screen.getByText('예매자 이름을 적어 주세요.').getAttribute('aria-live')).toBe('polite');
        expect(disabled(screen.getByRole('button', { name: '1매 선택 완료' }))).toBe(true);
    });

    it('선점에 성공하면 주문 화면으로 간다', async () => {
        const { router } = renderRoute(PATH, { loggedIn: true });

        await pick(2);
        hold(2);

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/));
        expect(storedHold()).toBeNull();
    });

    it('선점 요청에 로그인한 구매자의 buyerId를 Expected-Buyer-Id 헤더로 담는다', async () => {
        renderRoute(PATH, { loggedIn: true });

        await screen.findByRole('button', { name: '1매 선택 완료' });
        hold(1);

        await waitFor(() => expect(sentBuyers).toEqual([MOCK_USER.buyerId]));
    });

    it('그사이 다른 계정으로 로그인돼 BUYER_CHANGED를 받으면 다시 보내지 않고, 예매를 시작한 계정으로 예매확인에서 확인하라고 안내한다', async () => {
        renderRoute(PATH, { scenario: 'buyer-changed', loggedIn: true, prepare: withoutOwnHold });

        await screen.findByRole('button', { name: '1매 선택 완료' });
        hold(1);

        expect(await screen.findByRole('heading', { name: '다른 계정으로 로그인돼 있어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '예매확인으로' }).getAttribute('href')).toBe('/me/orders');
        await new Promise((resolve) => setTimeout(resolve, 1_500));
        expect(sentKeys).toHaveLength(1);
        expect(myHeldOrders()).toHaveLength(0);
        expect(storedHold()?.key).toBe(sentKeys[0]);
    });

    it('고르는 사이 남은 수가 줄면 새 남은 수를 알리고 수량을 줄인다', async () => {
        renderRoute(PATH, { scenario: 'seat-not-available', loggedIn: true, prepare: withoutOwnHold });

        await pick(3);
        hold(3);

        expect(await screen.findByText('고르는 사이 잔여석이 줄었어요. 지금 잔여석은 2석이에요.')).toBeTruthy();
        expect(screen.getByText('잔여석에 맞춰 3매에서 2매로 줄였어요.')).toBeTruthy();
        expect(screen.getByRole('button', { name: '2매 선택 완료' })).toBeTruthy();
        expect(storedHold()).toBeNull();
    });

    it('고르는 사이 매진되면 매진 안내와 다른 회차 보기를 보여 준다', async () => {
        renderRoute(PATH, { scenario: 'sold-out-race', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByText('고르는 사이 매진됐어요.')).toBeTruthy();
        expect(screen.getByRole('heading', { name: '매진됐어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '다른 회차 보기' }).getAttribute('href')).toBe('/performances/1');
    });

    it('회차당 1인 매수를 넘으면 한도와 이미 예매한 매수를 알리고 수량을 줄인다', async () => {
        renderRoute(PATH, { scenario: 'seat-limit', loggedIn: true, prepare: withoutOwnHold });

        await pick(2);
        hold(2);

        expect(await screen.findByText('회차당 1인 4매까지예요. 이미 3매를 예매했어요.')).toBeTruthy();
        expect(await screen.findByRole('button', { name: '1매 선택 완료' })).toBeTruthy();
    });

    it('판매가 끝났으면 판매 종료 안내와 공연 페이지로 가는 버튼을 보여 준다', async () => {
        renderRoute(PATH, { scenario: 'sales-closed', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByRole('heading', { name: '이 회차는 판매가 종료됐어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '공연 페이지로' })).toBeTruthy();
    });

    it('서버가 아직 티켓 오픈 전이라고 답하면 잠시 뒤 자동으로 다시 선점한다', async () => {
        const { router } = renderRoute(PATH, { scenario: 'sales-not-open-once', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByText('아직 티켓 오픈 전이에요. 잠시 뒤 다시 시도할게요.')).toBeTruthy();
        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\//), { timeout: 4_000 });
        expect(sentKeys).toHaveLength(2);
        expect(sentKeys[0]).not.toBe(sentKeys[1]);
    });

    it('선점 응답을 받지 못하면 같은 요청 식별자로 다시 보내, 서버가 이미 만든 주문을 받고 주문은 하나만 생긴다', async () => {
        const { router } = renderRoute(PATH, { scenario: 'hold-lost-created', loggedIn: true, prepare: withoutOwnHold });

        await pick(1);
        hold(1);

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/), { timeout: 3_000 });
        expect(sentKeys).toHaveLength(2);
        expect(sentKeys[1]).toBe(sentKeys[0]);
        expect(myHeldOrders().map((o) => `/orders/${o.token}`)).toEqual([router.state.location.pathname]);
    });

    it('선점 응답을 받지 못했고 서버도 주문을 만들지 않았으면, 같은 식별자로 다시 보낸 요청에서 주문이 생긴다', async () => {
        const { router } = renderRoute(PATH, { scenario: 'hold-lost-none', loggedIn: true, prepare: withoutOwnHold });

        await pick(1);
        hold(1);

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\//), { timeout: 3_000 });
        expect(sentKeys[1]).toBe(sentKeys[0]);
        expect(myHeldOrders()).toHaveLength(1);
    });

    it('서버 오류(5xx)로 결과를 모르면 같은 식별자로 다시 보낸다', async () => {
        server.use(http.post('/api/orders', () => HttpResponse.json({ status: 503 }, { status: 503 }), { once: true }));
        const { router } = renderRoute(PATH, { loggedIn: true, prepare: withoutOwnHold });

        await pick(1);
        hold(1);

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\//), { timeout: 3_000 });
        expect(sentKeys).toHaveLength(2);
        expect(sentKeys[1]).toBe(sentKeys[0]);
    });

    it.each([
        ['판매 전 응답', () => HttpResponse.json({ status: 409, code: 'SALES_NOT_OPEN' }, { status: 409 })],
        ['응답 유실', () => HttpResponse.error()],
    ])('선점 응답(%s)이 오기 전에 화면을 떠나면 다시 보내지 않는다', async (_, reply) => {
        server.use(
            http.post('/api/orders', async () => {
                await delay(300);
                return reply();
            }),
        );
        const { router } = renderRoute(PATH, { loggedIn: true, prepare: withoutOwnHold });

        await pick(1);
        hold(1);
        await router.navigate('/me/orders');
        await new Promise((resolve) => setTimeout(resolve, 2_000));

        expect(sentKeys).toHaveLength(1);
        expect(router.state.location.pathname).toBe('/me/orders');
    }, 5_000);

    it('다시 보내는 동안 선점 버튼을 막고, 정해진 횟수 뒤에도 결과를 모르면 예매확인에서 확인하게 한다', async () => {
        renderRoute(PATH, { scenario: 'hold-lost-always', loggedIn: true, prepare: withoutOwnHold });

        await pick(1);
        hold(1);

        const resending = await screen.findByRole('button', { name: '결과를 확인하는 중' });
        expect(disabled(resending)).toBe(true);
        expect(await screen.findByRole('heading', { name: '예매 결과를 확인하지 못했어요' }, { timeout: 5_000 })).toBeTruthy();
        expect(screen.getByRole('link', { name: '예매확인에서 확인' }).getAttribute('href')).toBe('/me/orders');
        expect(sentKeys).toHaveLength(3);
        expect(new Set(sentKeys).size).toBe(1);
        expect(myHeldOrders()).toHaveLength(1);
        expect(storedHold()?.key).toBe(sentKeys[0]);
        expect(storedHold()?.body).toEqual({ showtimeId: 12, quantity: 1, buyerName: '밤산책' });
    }, 10_000);

    it('선점 응답을 받지 못한 채 새로고침해도, 화면을 다시 열면 같은 식별자로 결과를 확인해 주문은 하나만 생긴다', async () => {
        const first = renderRoute(PATH, { scenario: 'hold-lost-created', loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);
        await screen.findByRole('button', { name: '결과를 확인하는 중' });
        first.unmount();
        expect(sentKeys).toHaveLength(1);

        const { router } = renderRoute(PATH, { scenario: 'hold-lost-created', loggedIn: true });

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/));
        expect(sentKeys).toHaveLength(2);
        expect(new Set(sentKeys).size).toBe(1);
        expect(myHeldOrders()).toHaveLength(1);
        expect(storedHold()).toBeNull();
    });

    it('이 회차에 다른 계정이 보관한 요청만 있으면 보내지 않고 그 계정으로 확인하라고 안내하며, 이 계정으로는 예매할 수 있다', async () => {
        saveHoldRequest({
            key: crypto.randomUUID(),
            body: { showtimeId: 12, quantity: 2, buyerName: '새벽달' },
            buyerId: 'b_someoneElse',
            createdAt: new Date().toISOString(),
        });
        renderRoute(PATH, { loggedIn: true, prepare: withoutOwnHold });

        expect(await screen.findByRole('heading', { name: '다른 계정으로 시작한 예매가 있어요' })).toBeTruthy();
        expect(disabled(screen.getByRole('button', { name: '1매 선택 완료' }))).toBe(false);
        expect(sentKeys).toHaveLength(0);
    });

    it('선점이 401이면 보관한 요청을 두고 로그인으로 가고, 같은 계정으로 돌아오면 같은 식별자로 결과를 확인한다', async () => {
        const view = renderRoute(PATH, { scenario: 'hold-unauthorized-once', loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);
        await waitFor(() => expect(redirectTo).toHaveBeenCalledWith('/oauth2/authorization/kakao'));
        expect(storedHold()?.key).toBe(sentKeys[0]);

        const { router } = finishKakaoLogin(view, { scenario: 'hold-unauthorized-once' });

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/));
        expect(sentKeys).toHaveLength(2);
        expect(new Set(sentKeys).size).toBe(1);
        expect(myHeldOrders()).toHaveLength(1);
        expect(storedHold()).toBeNull();
    });

    it('선점이 401이고 다른 계정으로 로그인해 돌아오면 보관한 요청을 보내지 않고 원래 계정으로 확인하라고 안내한다', async () => {
        const view = renderRoute(PATH, { scenario: 'hold-unauthorized-once', loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);
        await waitFor(() => expect(redirectTo).toHaveBeenCalledWith('/oauth2/authorization/kakao'));
        server.use(
            http.get('/api/me', () => HttpResponse.json({ buyerId: 'b_anotherAccount', nickname: '새벽달', serverTime: new Date().toISOString() })),
        );

        finishKakaoLogin(view, { scenario: 'hold-unauthorized-once' });

        expect(await screen.findByRole('heading', { name: '다른 계정으로 시작한 예매가 있어요' })).toBeTruthy();
        expect(sentKeys).toHaveLength(1);
        expect(storedHold()?.key).toBe(sentKeys[0]);
    });

    it('선점이 403이면 GET /api/me로 CSRF 쿠키를 다시 받은 뒤 같은 식별자로 다시 보낸다', async () => {
        const paths: string[] = [];
        const record = ({ request }: { request: Request }) => paths.push(`${request.method} ${new URL(request.url).pathname}`);
        server.events.on('request:start', record);
        const { router } = renderRoute(PATH, { scenario: 'hold-csrf-once', loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/));
        server.events.removeListener('request:start', record);
        const firstHold = paths.indexOf('POST /api/orders');
        expect(paths.slice(firstHold + 1)).toContain('GET /api/me');
        expect(paths.indexOf('GET /api/me', firstHold)).toBeLessThan(paths.lastIndexOf('POST /api/orders'));
        expect(sentKeys).toHaveLength(2);
        expect(new Set(sentKeys).size).toBe(1);
        expect(myHeldOrders()).toHaveLength(1);
    });

    it('403이 계속되면 다시 보내기 횟수만큼만 같은 식별자로 보내고, 결과를 확인하지 못했다고 안내하며 요청을 남겨 둔다', async () => {
        renderRoute(PATH, { scenario: 'hold-csrf-always', loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);

        expect(await screen.findByRole('heading', { name: '예매 결과를 확인하지 못했어요' })).toBeTruthy();
        expect(sentKeys).toHaveLength(3);
        expect(new Set(sentKeys).size).toBe(1);
        expect(storedHold()?.key).toBe(sentKeys[0]);
    });

    it('화면을 열 때 보관한 지 23시간이 지난 요청은 보내지 않고 버린 뒤 예매확인에서 확인하게 한다', async () => {
        saveHoldRequest({
            key: crypto.randomUUID(),
            body: { showtimeId: 12, quantity: 1, buyerName: '밤산책' },
            buyerId: MOCK_USER.buyerId,
            createdAt: new Date(Date.now() - HOLD_REQUEST_RESEND_LIMIT_MS - 60_000).toISOString(),
        });
        renderRoute(PATH, { loggedIn: true, prepare: withoutOwnHold });

        expect(await screen.findByRole('heading', { name: '예매 결과를 여기서 확인할 수 없어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '예매확인에서 확인' }).getAttribute('href')).toBe('/me/orders');
        expect(sentKeys).toHaveLength(0);
        expect(storedHold()).toBeNull();
    });

    it('보관한 지 23시간이 되기 전이면 화면을 열 때 같은 식별자로 결과를 확인한다', async () => {
        const key = crypto.randomUUID();
        saveHoldRequest({
            key,
            body: { showtimeId: 12, quantity: 1, buyerName: '밤산책' },
            buyerId: MOCK_USER.buyerId,
            createdAt: new Date(Date.now() - HOLD_REQUEST_RESEND_LIMIT_MS + 60_000).toISOString(),
        });
        const { router } = renderRoute(PATH, { loggedIn: true, prepare: withoutOwnHold });

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/));
        expect(sentKeys).toEqual([key]);
    });

    it('결과를 모르는 채 23시간이 지난 뒤 다시 시도를 누르면 보내지 않고 예매확인에서 확인하게 한다', async () => {
        renderRoute(PATH, { scenario: 'hold-lost-always', loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);
        await screen.findByRole('heading', { name: '예매 결과를 확인하지 못했어요' }, { timeout: 5_000 });
        expect(sentKeys).toHaveLength(3);

        vi.setSystemTime(Date.now() + HOLD_REQUEST_RESEND_LIMIT_MS);
        fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

        expect(await screen.findByRole('heading', { name: '예매 결과를 여기서 확인할 수 없어요' })).toBeTruthy();
        expect(sentKeys).toHaveLength(3);
        expect(storedHold()).toBeNull();
    }, 10_000);

    it('같은 화면에서 회차가 바뀌면 바뀐 회차에 보관한 요청을 다시 확인한다', async () => {
        const { router } = renderRoute('/performances/1/showtimes/14/quantity', { loggedIn: true, prepare: withoutOwnHold });
        await screen.findByRole('button', { name: /티켓 오픈까지/ });
        await router.navigate(PATH);
        await screen.findByRole('button', { name: '1매 선택 완료' });
        const key = crypto.randomUUID();
        saveHoldRequest({
            key,
            body: { showtimeId: 14, quantity: 1, buyerName: '밤산책' },
            buyerId: MOCK_USER.buyerId,
            createdAt: new Date().toISOString(),
        });

        await router.navigate('/performances/1/showtimes/14/quantity');

        await waitFor(() => expect(sentKeys).toContain(key));
    });

    it('화면을 떠난 뒤 받은 선점 응답은, 다시 연 화면이 확인 중인 보관 요청을 지우지 않는다', async () => {
        let holdRequests = 0;
        server.use(
            http.post('/api/orders', async () => {
                holdRequests += 1;
                if (holdRequests === 1) {
                    await delay(1_500);
                    return undefined;
                }
                return HttpResponse.error();
            }),
        );
        const first = renderRoute(PATH, { loggedIn: true, prepare: withoutOwnHold });
        await pick(1);
        hold(1);
        await waitFor(() => expect(sentKeys).toHaveLength(1));
        const key = sentKeys[0];
        first.unmount();

        renderRoute(PATH, { loggedIn: true });

        expect(await screen.findByRole('heading', { name: '예매 결과를 확인하지 못했어요' }, { timeout: 6_000 })).toBeTruthy();
        expect(holdRequests).toBe(4);
        expect(storedHold()?.key).toBe(key);
    }, 10_000);

    it('로그인하지 않았으면 카카오 로그인을 먼저 하게 하고, 로그인하면 이 화면으로 돌아온다', async () => {
        const view = renderRoute(PATH);

        expect(await screen.findByRole('heading', { name: '로그인하면 매수를 고를 수 있어요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: '카카오 로그인' }));
        const { router } = finishKakaoLogin(view);

        expect(await screen.findByRole('button', { name: '1매 선택 완료' })).toBeTruthy();
        expect(router.state.location.pathname).toBe(PATH);
    });
});
