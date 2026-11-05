import { Navigate, type RouteObject } from 'react-router';
import { NotFoundPage } from '../pages/NotFoundPage';
import { QuantityPage } from '../pages/QuantityPage';
import { ShowPage } from '../pages/ShowPage';

const mockEnabled = import.meta.env.VITE_API_MOCK === 'true';

export const routes: RouteObject[] = [
    { index: true, element: mockEnabled ? <Navigate to="/shows/1" replace /> : <NotFoundPage /> },
    { path: '/shows/:showId', element: <ShowPage /> },
    { path: '/shows/:showId/showtimes/:showtimeId/quantity', element: <QuantityPage /> },
    { path: '*', element: <NotFoundPage /> },
];
