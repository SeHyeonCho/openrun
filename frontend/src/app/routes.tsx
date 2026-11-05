import { Navigate, type RouteObject } from 'react-router';
import { NotFoundPage } from '../pages/NotFoundPage';

const mockEnabled = import.meta.env.VITE_API_MOCK === 'true';

export const routes: RouteObject[] = [
    { index: true, element: mockEnabled ? <Navigate to="/shows/1" replace /> : <NotFoundPage /> },
    { path: '*', element: <NotFoundPage /> },
];
