import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { MockDb } from '../mocks/db';
import { disabled, renderRoute } from '../test/render';

const heldUntil = (ms: number) => (db: MockDb) => {
    db.orders.find((o) => o.token === 'held-demo')!.heldUntil = new Date(Date.now() + ms).toISOString();
};

const queryButton = (name: string) => screen.queryByRole('button', { name });

describe('주문 화면', () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    it('선점 중이면 입장번호, 확정까지 남은 시간, 확정·선점 취소·공유하기를 보여 준다', async () => {
        renderRoute('/orders/held-demo', { loggedIn: true });

        const ticket = await screen.findByRole('article', { name: '모바일 티켓' });
        expect(within(ticket).getByText('37 · 38')).toBeTruthy();
        expect(within(ticket).getByRole('img', { name: '확정 전이라 흐린 입장용 QR' })).toBeTruthy();
        expect(screen.getByRole('timer').textContent).toMatch(/^09:(4[01]|39)$/);
        expect(queryButton('예매 확정')).toBeTruthy();
        expect(queryButton('예매 취소')).toBeTruthy();
        expect(queryButton('공유하기')).toBeTruthy();
    });

    it('확정하면 선명한 QR과 이미지로 저장·캘린더에 추가·공유하기를 보여 준다', async () => {
        renderRoute('/orders/held-demo', { loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '예매 확정' }));

        expect(await screen.findByRole('heading', { name: '예매가 완료됐어요' })).toBeTruthy();
        expect(screen.getByRole('img', { name: '입장용 QR' })).toBeTruthy();
        expect(queryButton('이미지로 저장')).toBeTruthy();
        expect(queryButton('캘린더에 추가')).toBeTruthy();
        expect(queryButton('공유하기')).toBeTruthy();
        expect(queryButton('예매 취소')).toBeNull();
    });

    it('선점 취소는 확인 시트를 거쳐 취소하고, 다시 고르기로 수량 선택에 돌아간다', async () => {
        renderRoute('/orders/held-demo', { loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '예매 취소' }));
        const sheet = screen.getByRole('dialog', { name: '예매를 취소할까요?' });
        expect(within(sheet).getByText(/입장번호 37 · 38이 바로 다른 사람에게 풀려요/)).toBeTruthy();
        fireEvent.click(within(sheet).getByRole('button', { name: '예매 취소' }));

        expect(await screen.findByRole('heading', { name: '예매를 취소했어요' })).toBeTruthy();
        expect(screen.queryByText(/풀렸어요/)).toBeNull();
        expect(screen.getByRole('link', { name: '다시 고르기' }).getAttribute('href')).toBe('/shows/1/showtimes/12/quantity');
    });

    it('예매를 취소하고 공연 페이지로 돌아가면 풀린 잔여석을 바로 보여 준다', async () => {
        const { router } = renderRoute('/shows/1', { loggedIn: true });
        fireEvent.click(await screen.findByRole('button', { name: /12\.05 SAT 19:00–21:30/ }));
        expect(screen.getByText(/잔여 50석/)).toBeTruthy();

        await router.navigate('/orders/held-demo');
        fireEvent.click(await screen.findByRole('button', { name: '예매 취소' }));
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '예매 취소' }));
        await screen.findByRole('heading', { name: '예매를 취소했어요' });

        await router.navigate('/shows/1');
        fireEvent.click(await screen.findByRole('button', { name: /12\.05 SAT 19:00–21:30/ }));
        expect(await screen.findByText(/잔여 52석/)).toBeTruthy();
    });

    it('남은 시간이 0이 되면 기한 지남 화면으로 바뀐다', async () => {
        renderRoute('/orders/held-demo', { loggedIn: true, prepare: heldUntil(1_200) });

        expect(await screen.findByRole('timer')).toBeTruthy();
        expect(await screen.findByRole('heading', { name: '예매 가능 시간이 지났어요' }, { timeout: 3_000 })).toBeTruthy();
        expect(screen.getByText(/입장번호 37 · 38이 다시 풀렸어요/)).toBeTruthy();
    });

    it('기한이 지난 선점 주문은 상태가 HELD여도 기한 지남으로 보여 준다', async () => {
        renderRoute('/orders/held-demo', { loggedIn: true, prepare: heldUntil(-1_000) });

        expect(await screen.findByRole('heading', { name: '예매 가능 시간이 지났어요' })).toBeTruthy();
        expect(queryButton('예매 확정')).toBeNull();
    });

    it('화면의 남은 시간이 있어도 서버가 기한 지남으로 거부하면 그 이유를 보여 준다', async () => {
        renderRoute('/orders/held-demo', { scenario: 'confirm-expired', loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '예매 확정' }));

        expect(await screen.findByText('예매 가능 시간이 지났어요.')).toBeTruthy();
        expect(await screen.findByRole('heading', { name: '예매 가능 시간이 지났어요' })).toBeTruthy();
    });

    it('이미 취소한 주문을 확정하면 이미 취소했다고 알리고 취소 화면을 보여 준다', async () => {
        renderRoute('/orders/held-demo', { scenario: 'confirm-cancelled', loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '예매 확정' }));

        expect(await screen.findByText('이미 취소한 예매예요.')).toBeTruthy();
        expect(await screen.findByRole('heading', { name: '예매를 취소했어요' })).toBeTruthy();
    });

    it('이미 확정한 주문을 취소하면 취소할 수 없다고 알리고 확정 화면을 보여 준다', async () => {
        renderRoute('/orders/held-demo', { scenario: 'cancel-confirmed', loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '예매 취소' }));
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '예매 취소' }));

        expect(await screen.findByText('이미 완료한 예매라 여기서 취소할 수 없어요.')).toBeTruthy();
        expect(await screen.findByRole('heading', { name: '예매가 완료됐어요' })).toBeTruthy();
    });

    it('확정 응답을 받지 못하면 다시 눌러도 된다고 알리고, 다시 누르면 같은 결과를 받는다', async () => {
        renderRoute('/orders/held-demo', { scenario: 'confirm-lost-once', loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '예매 확정' }));
        expect(await screen.findByText('확정이 됐는지 알 수 없어요. 다시 눌러도 같은 결과예요.')).toBeTruthy();

        const again = await screen.findByRole('button', { name: '예매 확정' });
        await waitFor(() => expect(disabled(again)).toBe(false));
        fireEvent.click(again);

        expect(await screen.findByRole('heading', { name: '예매가 완료됐어요' })).toBeTruthy();
    });

    it('주문 링크로 들어온 다른 사람에게는 확정·취소 버튼을 보여 주지 않는다', async () => {
        renderRoute('/orders/viewer-demo', { loggedIn: true });

        expect(await screen.findByRole('heading', { name: '공유받은 예매예요' })).toBeTruthy();
        expect(screen.getByText('39 · 40')).toBeTruthy();
        expect(queryButton('예매 확정')).toBeNull();
        expect(queryButton('예매 취소')).toBeNull();
        expect(screen.getByRole('link', { name: '이 공연 보러 가기' })).toBeTruthy();
    });

    it('로그인하지 않고 내 주문 링크를 열면 내용만 보여 주고, 로그인하면 확정할 수 있다', async () => {
        renderRoute('/orders/held-demo');

        expect(await screen.findByRole('heading', { name: '공유받은 예매예요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: '카카오 로그인' }));

        expect(await screen.findByRole('button', { name: '예매 확정' })).toBeTruthy();
    });

    it('공유 창이 없는 브라우저에서는 주문 링크를 복사한다', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        vi.stubGlobal('navigator', { ...navigator, share: undefined, clipboard: { writeText } });
        renderRoute('/orders/held-demo', { loggedIn: true });

        fireEvent.click(await screen.findByRole('button', { name: '공유하기' }));

        expect(await screen.findByText('예매 링크를 복사했어요.')).toBeTruthy();
        expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/orders/held-demo`);
    });

    it('없는 주문 링크면 주문을 찾을 수 없다고 알린다', async () => {
        renderRoute('/orders/unknown-token');

        expect(await screen.findByRole('heading', { name: '예매를 찾을 수 없어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '예매 내역 보기' })).toBeTruthy();
    });
});
