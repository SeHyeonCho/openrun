import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { isMockLoggedIn } from '../mocks/scenarios';
import { disabled, renderRoute, salesOpenIn } from '../test/render';
import { server } from '../test/setup';

const opensIn = (ms: number) => salesOpenIn(21, ms);

describe('공연 페이지', () => {
    it('회차가 하나면 처음부터 골라져 있고, 판매 시작 시각이 되면 새로고침 없이 예매 버튼이 열린다', async () => {
        renderRoute('/shows/2', { loggedIn: true, prepare: opensIn(1_500) });

        const waiting = await screen.findByRole('button', { name: /판매 시작까지 00:0[12]/ });
        expect(disabled(waiting)).toBe(true);
        expect(screen.getByRole('button', { name: /12\.12 SAT 18:00–20:00/ }).getAttribute('aria-pressed')).toBe('true');

        const book = await screen.findByRole('button', { name: '예매하기' }, { timeout: 4_000 });
        expect(disabled(book)).toBe(false);
        expect(screen.getByText('판매 중')).toBeTruthy();
    });

    it('서버 시계가 기기보다 빨라도 서버 시각 기준으로 판매 시작까지 남은 시간을 센다', async () => {
        renderRoute('/shows/2', { scenario: 'clock-skew', loggedIn: true, prepare: opensIn(2_000) });

        const waiting = await screen.findByRole('button', { name: /판매 시작까지/ });
        expect(waiting.textContent).toMatch(/00:0[12]$/);
        await screen.findByRole('button', { name: '예매하기' }, { timeout: 4_000 });
    });

    it('회차가 여럿이면 고르기 전까지 예매 버튼을 열지 않고, 고른 회차의 판매 상태에 맞춰 버튼을 바꾼다', async () => {
        renderRoute('/shows/1', { loggedIn: true });

        const cta = await screen.findByRole('button', { name: '회차를 골라 주세요' });
        expect(disabled(cta)).toBe(true);

        fireEvent.click(screen.getByRole('button', { name: /12\.06 SUN 15:00–17:30/ }));
        expect(disabled(screen.getByRole('button', { name: '매진' }))).toBe(true);

        fireEvent.click(screen.getByRole('button', { name: /12\.04 FRI 19:30–21:30/ }));
        expect(disabled(screen.getByRole('button', { name: '판매 마감' }))).toBe(true);

        const onSale = screen.getByRole('button', { name: /12\.05 SAT 19:00–21:30/ });
        fireEvent.click(onSale);
        expect(within(onSale).getByText(/잔여 50석 · 1인 최대 4매/)).toBeTruthy();
        expect(disabled(screen.getByRole('button', { name: '예매하기' }))).toBe(false);
    });

    it('로그인하지 않고 예매하기를 누르면 카카오 로그인을 거쳐 수량 선택으로 간다', async () => {
        const { router } = renderRoute('/shows/1');

        expect(await screen.findByRole('heading', { name: '판매 전에 로그인해 두세요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: /12\.05 SAT 19:00–21:30/ }));
        fireEvent.click(screen.getByRole('button', { name: '예매하기' }));

        await waitFor(() => expect(router.state.location.pathname).toBe('/shows/1/showtimes/12/quantity'));
        expect(isMockLoggedIn()).toBe(true);
    });

    it('로그인 상태를 불러오지 못하면 로그인 카드 대신 다시 시도를 보여 준다', async () => {
        server.use(http.get('/api/me', () => HttpResponse.json({ status: 500 }, { status: 500 })));
        renderRoute('/shows/1', { loggedIn: true });

        expect(await screen.findByRole('heading', { name: '로그인 상태를 확인하지 못했어요' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: '카카오 로그인' })).toBeNull();
        expect(screen.getByRole('button', { name: '다시 시도' })).toBeTruthy();
    });

    it('로그인 상태를 불러오지 못해도 예매 버튼은 열어 두고, 매수 선택 화면에서 로그인 상태를 다시 확인하게 한다', async () => {
        server.use(http.get('/api/me', () => HttpResponse.json({ status: 500 }, { status: 500 })));
        const { router } = renderRoute('/shows/1', { loggedIn: true });
        await screen.findByRole('heading', { name: '로그인 상태를 확인하지 못했어요' });

        fireEvent.click(screen.getByRole('button', { name: /12\.05 SAT 19:00–21:30/ }));
        fireEvent.click(screen.getByRole('button', { name: '예매하기' }));

        await waitFor(() => expect(router.state.location.pathname).toBe('/shows/1/showtimes/12/quantity'));
        expect(await screen.findByRole('heading', { name: '정보를 불러오지 못했어요' })).toBeTruthy();
        expect(screen.queryByRole('button', { name: '카카오 로그인' })).toBeNull();
        expect(screen.queryByRole('button', { name: /매 선택 완료/ })).toBeNull();
    });

    it('없는 공연이면 공연을 찾을 수 없다고 알린다', async () => {
        renderRoute('/shows/999');

        expect(await screen.findByRole('heading', { name: '공연을 찾을 수 없어요' })).toBeTruthy();
    });
});
