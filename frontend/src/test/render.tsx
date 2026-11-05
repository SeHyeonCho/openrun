import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
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
    const queryClient = new QueryClient({
        defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
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
