import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import './styles/tokens.css';
import './styles/base.css';
import './styles/type.css';

async function prepareMockApi(): Promise<ReactNode> {
    if (import.meta.env.VITE_API_MOCK !== 'true') {
        return null;
    }
    const [{ startMockApi }, { MockPanel }] = await Promise.all([import('./mocks/browser'), import('./mocks/MockPanel')]);
    await startMockApi();
    return <MockPanel />;
}

const root = document.getElementById('root');
if (!root) {
    throw new Error('#root 요소가 없다');
}

prepareMockApi().then((devPanel) => {
    createRoot(root).render(
        <StrictMode>
            <App devPanel={devPanel} />
        </StrictMode>,
    );
});
