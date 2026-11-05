import { cleanup } from '@testing-library/react';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { resetServerTime } from '../lib/clock';
import { handlers } from '../mocks/handlers';

export const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));

beforeEach(() => {
    window.sessionStorage.clear();
    resetServerTime();
});

afterEach(() => {
    cleanup();
    server.resetHandlers();
});

afterAll(() => server.close());
