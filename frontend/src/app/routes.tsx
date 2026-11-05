import type { RouteObject } from 'react-router';
import { NotFoundPage } from '../pages/NotFoundPage';

export const routes: RouteObject[] = [
    { path: '*', element: <NotFoundPage /> },
];
