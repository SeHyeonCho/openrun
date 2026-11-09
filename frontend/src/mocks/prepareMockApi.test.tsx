import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { prepareMockApi } from './prepareMockApi';

describe('목 API 준비', () => {
    it('목 모듈을 불러오지 못해도 화면을 그릴 수 있게 실패 안내를 돌려준다', async () => {
        const panel = await prepareMockApi(() => Promise.reject(new Error('청크를 불러오지 못했다')));

        render(<>{panel}</>);

        expect(screen.getByRole('alert').textContent).toContain('목 API를 시작하지 못했어요');
    });
});
