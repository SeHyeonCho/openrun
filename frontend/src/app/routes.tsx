import type { RouteObject } from 'react-router';
import { HomePage } from '../pages/HomePage';
import { MyOrdersPage } from '../pages/MyOrdersPage';
import { NotFoundPage } from '../pages/NotFoundPage';
import { OrderPage } from '../pages/OrderPage';
import { PerformancePage } from '../pages/PerformancePage';
import { QuantityPage } from '../pages/QuantityPage';

export const routes: RouteObject[] = [
    { index: true, element: <HomePage /> },
    { path: '/performances/:performanceId', element: <PerformancePage /> },
    { path: '/performances/:performanceId/showtimes/:showtimeId/quantity', element: <QuantityPage /> },
    { path: '/orders/:token', element: <OrderPage /> },
    { path: '/me/orders', element: <MyOrdersPage /> },
    { path: '*', element: <NotFoundPage /> },
];
