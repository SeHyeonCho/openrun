import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './app/App';
import { prepareMockApi } from './mocks/prepareMockApi';
import './styles/tokens.css';
import './styles/base.css';
import './styles/type.css';

const root = document.getElementById('root');
if (!root) {
    throw new Error('#root 요소가 없다');
}

const devPanel: Promise<ReactNode> = import.meta.env.VITE_API_MOCK === 'true' ? prepareMockApi() : Promise.resolve(null);

devPanel.then((panel) => {
    createRoot(root).render(
        <StrictMode>
            <App devPanel={panel} />
        </StrictMode>,
    );
});
