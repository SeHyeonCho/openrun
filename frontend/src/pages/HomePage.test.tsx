import { screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { peekLoginReturn, saveLoginReturn } from '../auth/loginReturn';
import { renderRoute } from '../test/render';

describe('첫 화면', () => {
    it('로그인하러 가기 전에 저장한 화면으로 돌아가고, 저장한 경로는 한 번만 쓴다', async () => {
        saveLoginReturn('/me/orders');

        const { router } = renderRoute('/', { loggedIn: true });

        await waitFor(() => expect(router.state.location.pathname).toBe('/me/orders'));
        expect(peekLoginReturn()).toBeNull();
    });

    it('저장한 경로가 이 사이트 밖을 가리키면 따라가지 않는다', async () => {
        saveLoginReturn('//example.com/login');

        const { router } = renderRoute('/');

        expect(await screen.findByRole('heading', { name: '페이지를 찾을 수 없어요' })).toBeTruthy();
        expect(router.state.location.pathname).toBe('/');
    });
});
