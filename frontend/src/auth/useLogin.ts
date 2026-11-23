import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { api } from '../api/queries';
import { redirectTo } from '../lib/navigation';
import { saveLoginReturn } from './loginReturn';

export const KAKAO_LOGIN_PATH = '/oauth2/authorization/kakao';

const mockEnabled = import.meta.env.VITE_API_MOCK === 'true';

export function useLogin() {
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    return useCallback(
        async (returnTo: string) => {
            if (mockEnabled) {
                await api.mockLogin();
                await queryClient.invalidateQueries();
                navigate(returnTo);
                return;
            }
            saveLoginReturn(returnTo);
            redirectTo(KAKAO_LOGIN_PATH);
        },
        [queryClient, navigate],
    );
}

export function useLogout() {
    const queryClient = useQueryClient();
    return useCallback(async () => {
        await api.logout();
        await queryClient.resetQueries();
    }, [queryClient]);
}
