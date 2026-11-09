import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../test/setup';
import { NoResponseError, request } from './client';

describe('요청 시간 제한', () => {
    afterEach(() => {
        vi.useRealTimers();
    });

    it('헤더만 오고 본문이 멈추면 시간 제한 뒤 응답을 받지 못한 것으로 본다', async () => {
        server.use(
            http.get('/api/stalled', () => {
                const body = new ReadableStream({
                    start(controller) {
                        controller.enqueue(new TextEncoder().encode('{"serverTime":'));
                    },
                });
                return new HttpResponse(body, { headers: { 'Content-Type': 'application/json' } });
            }),
        );
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });

        const result = request('/stalled').then(
            () => 'resolved',
            (error: unknown) => error,
        );
        await vi.advanceTimersByTimeAsync(10_000);

        expect(await result).toBeInstanceOf(NoResponseError);
    });
});
