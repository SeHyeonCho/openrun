import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient } from '../app/queryClient';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { ToastProvider } from '../components/Toast';
import { routes } from '../app/routes';
import { loadDb, saveDb, type MockDb } from '../mocks/db';
import { setMockLoggedIn, storeScenario, type ScenarioId } from '../mocks/scenarios';

type Options = {
    scenario?: ScenarioId;
    loggedIn?: boolean;
    prepare?: (db: MockDb) => void;
};

export function renderRoute(path: string, { scenario = 'default', loggedIn = false, prepare }: Options = {}) {
    storeScenario(scenario);
    setMockLoggedIn(loggedIn);
    if (prepare) {
        const db = loadDb();
        prepare(db);
        saveDb(db);
    }
    const queryClient = createQueryClient();
    const defaults = queryClient.getDefaultOptions();
    queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retry: false } });
    const router = createMemoryRouter(routes, { initialEntries: [path] });
    const view = render(
        <QueryClientProvider client={queryClient}>
            <ToastProvider>
                <RouterProvider router={router} />
            </ToastProvider>
        </QueryClientProvider>,
    );
    return { ...view, router };
}

export const disabled = (element: HTMLElement) => (element as HTMLButtonElement).disabled;

const fromServerNow = (db: MockDb, ms: number) => new Date(Date.now() + db.skewMs + ms).toISOString();

export const holdDemoEndsIn = (ms: number) => (db: MockDb) => {
    db.orders.find((o) => o.token === 'held-demo')!.heldUntil = fromServerNow(db, ms);
};

export const salesOpenIn = (showtimeId: number, ms: number) => (db: MockDb) => {
    db.showtimes.find((s) => s.id === showtimeId)!.salesOpensAt = fromServerNow(db, ms);
};
