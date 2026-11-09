import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { holdDemoEndsIn, renderRoute } from '../test/render';
import { server } from '../test/setup';

describe('내 주문', () => {
    it('로그인한 구매자의 주문만 최근 순으로 공연, 회차, 수량, 상태와 함께 보여 준다', async () => {
        renderRoute('/me/orders', { loggedIn: true });

        const items = await screen.findAllByRole('link', { name: /NIGHT SIGNAL/ });
        expect(items.map((item) => item.getAttribute('href'))).toEqual([
            '/orders/held-demo',
            '/orders/confirmed-demo',
            '/orders/expired-demo',
            '/orders/cancelled-demo',
        ]);
        const [held, confirmed, expired, cancelled] = items;
        expect(within(held!).getByText('확정 대기')).toBeTruthy();
        expect(within(held!).getByText(/2매 · 입장번호 37 · 38 · 09:\d\d 남음/)).toBeTruthy();
        expect(within(confirmed!).getByText('예매 완료')).toBeTruthy();
        expect(within(expired!).getByText('시간 만료')).toBeTruthy();
        expect(within(cancelled!).getByText('취소')).toBeTruthy();
        expect(screen.queryByText(/39 · 40/)).toBeNull();
    });

    it('보고 있는 사이 확정 대기 예매의 기한이 지나면 시간 만료로 바꾼다', async () => {
        renderRoute('/me/orders', { loggedIn: true, prepare: holdDemoEndsIn(1_200) });

        const held = await screen.findByRole('link', { name: /37 · 38/ });
        expect(within(held).getByText('확정 대기')).toBeTruthy();

        await waitFor(() => expect(within(held).getByText('시간 만료')).toBeTruthy(), { timeout: 3_000 });
    });

    it('서버 시계가 기기보다 빨라도 서버 시각 기준으로 남은 시간을 세고 기한에 만료로 바꾼다', async () => {
        renderRoute('/me/orders', { scenario: 'clock-skew', loggedIn: true, prepare: holdDemoEndsIn(1_200) });

        const held = await screen.findByRole('link', { name: /37 · 38/ });
        expect(within(held).getByText(/00:0[012] 남음/)).toBeTruthy();

        await waitFor(() => expect(within(held).getByText('시간 만료')).toBeTruthy(), { timeout: 3_000 });
    });

    it('로그인하지 않았으면 로그인을 먼저 하게 하고, 로그인하면 목록을 보여 준다', async () => {
        renderRoute('/me/orders');

        expect(await screen.findByRole('heading', { name: '로그인하면 예매 내역을 볼 수 있어요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: '카카오 로그인' }));

        expect(await screen.findByText('밤산책 님이 카카오 계정으로 예매한 내역이에요.')).toBeTruthy();
    });

    it('로그인 상태를 불러오지 못하면 다시 로그인하라고 하지 않고 다시 시도를 보여 준다', async () => {
        server.use(http.get('/api/me', () => HttpResponse.json({ status: 500 }, { status: 500 })));
        renderRoute('/me/orders', { loggedIn: true });

        expect(await screen.findByRole('heading', { name: '정보를 불러오지 못했어요' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: '카카오 로그인' })).toBeNull();
        expect(screen.getByRole('button', { name: '다시 시도' })).toBeTruthy();
    });
});
