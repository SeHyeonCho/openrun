import { QueryClient } from '@tanstack/react-query';
import { ApiError } from '../api/client';

export function createQueryClient() {
    return new QueryClient({
        defaultOptions: {
            queries: {
                retry: (failureCount, error) => !(error instanceof ApiError) && failureCount < 2,
                staleTime: 5_000,
            },
            mutations: { retry: false },
        },
    });
}
