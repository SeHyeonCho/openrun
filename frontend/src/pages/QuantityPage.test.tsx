import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { MockDb } from '../mocks/db';
import { disabled, renderRoute } from '../test/render';

const PATH = '/shows/1/showtimes/12/quantity';

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
    it('1인 최대 좌석 수에서 이미 가진 수를 뺀 만큼까지만 고를 수 있다', async () => {
        renderRoute(PATH, { loggedIn: true });

        await pick(3);

        expect(screen.getByText('1인 최대 4매 · 이미 가진 수 2매 · 지금 2매까지 고를 수 있어요.')).toBeTruthy();
        expect(disabled(screen.getByRole('button', { name: '한 장 더하기' }))).toBe(true);
        expect(screen.getByRole('button', { name: '2매 선택 완료' })).toBeTruthy();
    });

    it('예매자 이름은 카카오 닉네임으로 채워 두고, 비우면 선점할 수 없다', async () => {
        renderRoute(PATH, { loggedIn: true });

        const name = (await screen.findByLabelText('예매자 이름')) as HTMLInputElement;
        expect(name.value).toBe('밤산책');

        fireEvent.change(name, { target: { value: '  ' } });

        expect(screen.getByText('예매자 이름을 적어 주세요.')).toBeTruthy();
        expect(disabled(screen.getByRole('button', { name: '1매 선택 완료' }))).toBe(true);
    });

    it('선점에 성공하면 주문 화면으로 간다', async () => {
        const { router } = renderRoute(PATH, { loggedIn: true });

        await pick(2);
        hold(2);

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\/[0-9a-f]{32}$/));
    });

    it('고르는 사이 남은 수가 줄면 새 남은 수를 알리고 수량을 줄인다', async () => {
        renderRoute(PATH, { scenario: 'seat-not-available', loggedIn: true, prepare: withoutOwnHold });

        await pick(3);
        hold(3);

        expect(await screen.findByText('고르는 사이 잔여석이 줄었어요. 지금 잔여석은 2석이에요.')).toBeTruthy();
        expect(screen.getByText('잔여석에 맞춰 3매에서 2매로 줄였어요.')).toBeTruthy();
        expect(screen.getByRole('button', { name: '2매 선택 완료' })).toBeTruthy();
    });

    it('고르는 사이 매진되면 매진 안내와 다른 회차 보기를 보여 준다', async () => {
        renderRoute(PATH, { scenario: 'sold-out-race', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByText('고르는 사이 매진됐어요.')).toBeTruthy();
        expect(screen.getByRole('heading', { name: '매진됐어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '다른 회차 보기' }).getAttribute('href')).toBe('/shows/1');
    });

    it('1인 최대 좌석 수를 넘으면 한도와 이미 가진 수를 알리고 수량을 줄인다', async () => {
        renderRoute(PATH, { scenario: 'seat-limit', loggedIn: true, prepare: withoutOwnHold });

        await pick(2);
        hold(2);

        expect(await screen.findByText('1인 최대 4매예요. 이미 3매를 가지고 있어요.')).toBeTruthy();
        expect(await screen.findByRole('button', { name: '1매 선택 완료' })).toBeTruthy();
    });

    it('판매가 끝났으면 판매 마감 안내와 공연 페이지로 가는 버튼을 보여 준다', async () => {
        renderRoute(PATH, { scenario: 'sales-closed', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByRole('heading', { name: '이 회차는 판매가 끝났어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '공연 페이지로' })).toBeTruthy();
    });

    it('서버가 아직 판매 전이라고 답하면 잠시 뒤 자동으로 다시 선점한다', async () => {
        const { router } = renderRoute(PATH, { scenario: 'sales-not-open-once', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByText('아직 판매 전이에요. 잠시 뒤 다시 시도할게요.')).toBeTruthy();
        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\//), { timeout: 4_000 });
    });

    it('선점 응답을 받지 못하면 다시 보내지 않고 내 주문에서 방금 주문을 먼저 찾는다', async () => {
        renderRoute(PATH, { scenario: 'hold-lost-created', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByRole('heading', { name: '방금 예매를 찾았어요' })).toBeTruthy();
        expect(screen.getByRole('link', { name: '예매 보기' }).getAttribute('href')).toMatch(/^\/orders\/[0-9a-f]{32}$/);
    });

    it('선점 응답을 받지 못했고 주문도 없으면 그때 다시 선점하게 한다', async () => {
        const { router } = renderRoute(PATH, { scenario: 'hold-lost-none', loggedIn: true });

        await pick(1);
        hold(1);

        expect(await screen.findByRole('heading', { name: '예매가 만들어지지 않았어요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: '1매 다시 선택 완료' }));

        await waitFor(() => expect(router.state.location.pathname).toMatch(/^\/orders\//));
    });

    it('로그인하지 않았으면 카카오 로그인을 먼저 하게 하고, 로그인하면 이 화면으로 돌아온다', async () => {
        renderRoute(PATH);

        expect(await screen.findByRole('heading', { name: '로그인하면 매수를 고를 수 있어요' })).toBeTruthy();
        fireEvent.click(screen.getByRole('button', { name: '카카오 로그인' }));

        expect(await screen.findByRole('button', { name: '1매 선택 완료' })).toBeTruthy();
    });
});
