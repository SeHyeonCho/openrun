import { Navigate, type RouteObject } from 'react-router';
import { MyOrdersPage } from '../pages/MyOrdersPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { OrderPage } from '../pages/OrderPage';
import { QuantityPage } from '../pages/QuantityPage';
import { ShowPage } from '../pages/ShowPage';

const mockEnabled = import.meta.env.VITE_API_MOCK === 'true';

export const routes: RouteObject[] = [
    { index: true, element: mockEnabled ? <Navigate to="/shows/1" replace /> : <NotFoundPage /> },
    { path: '/shows/:showId', element: <ShowPage /> },
    { path: '/shows/:showId/showtimes/:showtimeId/quantity', element: <QuantityPage /> },
    { path: '/orders/:token', element: <OrderPage /> },
    { path: '/me/orders', element: <MyOrdersPage /> },
    { path: '*', element: <NotFoundPage /> },
];
