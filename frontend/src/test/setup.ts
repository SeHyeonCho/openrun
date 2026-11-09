import { cleanup } from '@testing-library/react';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, vi } from 'vitest';
import { resetServerTime } from '../lib/clock';
import { handlers } from '../mocks/handlers';

export const server = setupServer(...handlers);

const TEST_START = new Date('2026-11-10T12:00:00+09:00');

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'], now: TEST_START, shouldAdvanceTime: true });
    window.sessionStorage.clear();
    resetServerTime();
});

afterEach(() => {
    cleanup();
    server.resetHandlers();
    vi.useRealTimers();
});

afterAll(() => server.close());
