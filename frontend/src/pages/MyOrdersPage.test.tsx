import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { renderRoute } from '../test/render';

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
        expect(within(held!).getByText('확정 전')).toBeTruthy();
        expect(within(held!).getByText(/2매 · 입장번호 37 · 38 · 09:\d\d 남음/)).toBeTruthy();
        expect(within(confirmed!).getByText('확정')).toBeTruthy();
        expect(within(expired!).getByText('기한 지남')).toBeTruthy();
        expect(within(cancelled!).getByText('취소')).toBeTruthy();
        expect(screen.queryByText(/39 · 40/)).toBeNull();
    });

    it('로그인하지 않았으면 로그인을 먼저 하게 하고, 로그인하면 목록을 보여 준다', async () => {
        renderRoute('/me/orders');

        expect(await screen.findByRole('heading', { name: '로그인하면 주문을 볼 수 있어요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: '카카오 로그인' }));

        expect(await screen.findByText('밤산책 님이 카카오 계정으로 예매한 주문이에요.')).toBeTruthy();
    });
});
