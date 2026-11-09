import { setupWorker } from 'msw/browser';
import { resetDb } from './db';
import { handlers } from './handlers';
import { storeScenario } from './scenarios';

export async function startMockApi() {
    const url = new URL(window.location.href);
    const requested = url.searchParams.get('mock');
    if (requested !== null) {
        storeScenario(requested);
        resetDb();
        url.searchParams.delete('mock');
        window.history.replaceState(null, '', url);
    }
    await setupWorker(...handlers).start({ onUnhandledRequest: 'bypass', quiet: true });
}
