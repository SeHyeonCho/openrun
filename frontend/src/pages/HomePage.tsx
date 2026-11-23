import { useEffect } from 'react';
import { Navigate } from 'react-router';
import { clearLoginReturn, peekLoginReturn } from '../auth/loginReturn';
import { NotFoundPage } from './NotFoundPage';

const mockEnabled = import.meta.env.VITE_API_MOCK === 'true';

export function HomePage() {
    const returnTo = peekLoginReturn();

    useEffect(() => {
        clearLoginReturn();
    }, []);

    if (returnTo) {
        return <Navigate to={returnTo} replace />;
    }
    return mockEnabled ? <Navigate to="/performances/1" replace /> : <NotFoundPage />;
}
