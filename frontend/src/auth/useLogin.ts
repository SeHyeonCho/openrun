import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../api/queries';

export function useLogin() {
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    return useCallback(
        async (returnTo: string) => {
            await api.mockLogin();
            await queryClient.invalidateQueries();
            navigate(returnTo);
        },
        [queryClient, navigate],
    );
}
