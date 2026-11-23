import { useQuery } from '@tanstack/react-query';
import { isUnauthorized, request } from './client';
import type {
    AvailabilityResponse,
    CreateOrderRequest,
    MeResponse,
    MyOrdersResponse,
    OrderResponse,
    PerformanceResponse,
} from './types';

export const IDEMPOTENCY_HEADER = 'Idempotency-Key';

export const EXPECTED_BUYER_HEADER = 'Expected-Buyer-Id';

export const newIdempotencyKey = () => crypto.randomUUID();

export const queryKeys = {
    me: ['me'] as const,
    performance: (performanceId: string) => ['performance', performanceId] as const,
    availability: (showtimeId: number) => ['availability', showtimeId] as const,
    order: (token: string) => ['order', token] as const,
    myOrders: ['me', 'orders'] as const,
};

export const api = {
    getMe: () => request<MeResponse>('/me'),
    mockLogin: () => request<MeResponse>('/auth/mock-login', { method: 'POST' }),
    logout: () => request<void>('/logout', { method: 'POST' }),
    getPerformance: (performanceId: string) =>
        request<PerformanceResponse>(`/performances/${encodeURIComponent(performanceId)}`),
    getAvailability: (showtimeId: number) => request<AvailabilityResponse>(`/showtimes/${showtimeId}/availability`),
    createOrder: (body: CreateOrderRequest, idempotencyKey: string, buyerId: string) =>
        request<OrderResponse>('/orders', {
            method: 'POST',
            body,
            headers: { [IDEMPOTENCY_HEADER]: idempotencyKey, [EXPECTED_BUYER_HEADER]: buyerId },
        }),
    getOrder: (token: string) => request<OrderResponse>(`/orders/${encodeURIComponent(token)}`),
    confirmOrder: (token: string) => request<OrderResponse>(`/orders/${encodeURIComponent(token)}/confirm`, { method: 'POST' }),
    cancelOrder: (token: string) => request<OrderResponse>(`/orders/${encodeURIComponent(token)}/cancel`, { method: 'POST' }),
    getMyOrders: () => request<MyOrdersResponse>('/me/orders'),
};

export function useMe() {
    return useQuery({
        queryKey: queryKeys.me,
        queryFn: async () => {
            try {
                return await api.getMe();
            } catch (error) {
                if (isUnauthorized(error)) {
                    return null;
                }
                throw error;
            }
        },
    });
}

export function usePerformance(performanceId: string) {
    return useQuery({ queryKey: queryKeys.performance(performanceId), queryFn: () => api.getPerformance(performanceId) });
}

export function useAvailability(showtimeId: number, enabled: boolean) {
    return useQuery({
        queryKey: queryKeys.availability(showtimeId),
        queryFn: () => api.getAvailability(showtimeId),
        enabled,
    });
}

export function useOrder(token: string) {
    return useQuery({ queryKey: queryKeys.order(token), queryFn: () => api.getOrder(token) });
}

export function useMyOrders(enabled: boolean) {
    return useQuery({ queryKey: queryKeys.myOrders, queryFn: api.getMyOrders, enabled });
}
