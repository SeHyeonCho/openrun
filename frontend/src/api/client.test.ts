import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { server } from '../test/setup';
import { serverNow } from '../lib/clock';
import { CSRF_HEADER, NoResponseError, request } from './client';

describe('CSRF 토큰', () => {
    it('상태를 바꾸는 요청에는 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 담고, 조회에는 담지 않는다', async () => {
        const sent: Record<string, string | null> = {};
        server.use(
            http.get('/api/probe', ({ request: received }) => {
                sent.get = received.headers.get(CSRF_HEADER);
                return HttpResponse.json({});
            }),
            http.post('/api/probe', ({ request: received }) => {
                sent.post = received.headers.get(CSRF_HEADER);
                return HttpResponse.json({});
            }),
        );
        document.cookie = 'XSRF-TOKEN=token%2Fvalue; path=/';

        await request('/probe');
        await request('/probe', { method: 'POST' });

        expect(sent.get).toBeNull();
        expect(sent.post).toBe('token/value');
    });
});

describe('서버 시각', () => {
    it('실패 응답의 serverTime으로도 기기 시계를 맞춘다', async () => {
        const ahead = new Date(Date.now() + 60 * 60_000).toISOString();
        server.use(
            http.post('/api/probe', () =>
                HttpResponse.json({ status: 409, code: 'SALES_CLOSED', serverTime: ahead }, { status: 409, headers: { 'Content-Type': 'application/problem+json' } }),
            ),
        );

        await request('/probe', { method: 'POST' }).catch(() => undefined);

        expect(serverNow() - Date.now()).toBeGreaterThan(59 * 60_000);
    });
});

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
