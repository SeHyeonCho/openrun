import type { ReactNode } from 'react';
import { MockUnavailable } from './MockUnavailable';

const loadMockModules = () => Promise.all([import('./browser'), import('./MockPanel')]);

export async function prepareMockApi(load = loadMockModules): Promise<ReactNode> {
    try {
        const [{ startMockApi }, { MockPanel }] = await load();
        await startMockApi();
        return <MockPanel />;
    } catch {
        return <MockUnavailable />;
    }
}
