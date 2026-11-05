import type { OrderResponse } from '../api/types';

export type OrderView = 'held' | 'confirmed' | 'expired' | 'cancelled';

export function orderViewOf(order: Pick<OrderResponse, 'status' | 'heldUntil'>, now: number): OrderView {
    switch (order.status) {
        case 'CONFIRMED':
            return 'confirmed';
        case 'CANCELLED':
            return 'cancelled';
        case 'EXPIRED':
            return 'expired';
        case 'HELD':
            return now >= Date.parse(order.heldUntil) ? 'expired' : 'held';
    }
}

export const orderUrl = (token: string) => new URL(`/orders/${token}`, window.location.origin).toString();
