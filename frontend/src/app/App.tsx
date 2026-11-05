import { QueryClientProvider } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { createBrowserRouter, RouterProvider } from 'react-router';
import { ToastProvider } from '../components/Toast';
import { createQueryClient } from './queryClient';
import { routes } from './routes';

const router = createBrowserRouter(routes);

export function App({ devPanel }: { devPanel?: ReactNode }) {
    const [queryClient] = useState(createQueryClient);
    return (
        <QueryClientProvider client={queryClient}>
            <ToastProvider>
                {devPanel}
                <RouterProvider router={router} />
            </ToastProvider>
        </QueryClientProvider>
    );
}
