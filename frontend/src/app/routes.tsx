import { Navigate, type RouteObject } from 'react-router';
import { MyOrdersPage } from '../pages/MyOrdersPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { OrderPage } from '../pages/OrderPage';
import { PerformancePage } from '../pages/PerformancePage';
import { QuantityPage } from '../pages/QuantityPage';

const mockEnabled = import.meta.env.VITE_API_MOCK === 'true';

export const routes: RouteObject[] = [
    { index: true, element: mockEnabled ? <Navigate to="/performances/1" replace /> : <NotFoundPage /> },
    { path: '/performances/:performanceId', element: <PerformancePage /> },
    { path: '/performances/:performanceId/showtimes/:showtimeId/quantity', element: <QuantityPage /> },
    { path: '/orders/:token', element: <OrderPage /> },
    { path: '/me/orders', element: <MyOrdersPage /> },
    { path: '*', element: <NotFoundPage /> },
];
