export type IsoDateTime = string;

export type SalesStatus = 'BEFORE_SALE' | 'ON_SALE' | 'SOLD_OUT' | 'SALES_CLOSED';

export type SeatKind = 'GENERAL' | 'RESERVED';

export type OrderStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED';

export type ProblemCode =
    | 'SALES_NOT_OPEN'
    | 'SALES_CLOSED'
    | 'SEAT_LIMIT_EXCEEDED'
    | 'SEAT_NOT_IN_SHOWTIME'
    | 'SEAT_NOT_AVAILABLE'
    | 'ORDER_EXPIRED'
    | 'ORDER_CANCELLED'
    | 'ORDER_CONFIRMED'
    | 'IDEMPOTENCY_KEY_REUSED'
    | 'BUYER_CHANGED';

export interface ProblemDetail {
    type?: string;
    title?: string;
    status: number;
    detail?: string;
    instance?: string;
    code?: ProblemCode;
    seatIds?: number[];
}

export interface ShowtimeSummary {
    id: number;
    startsAt: IsoDateTime;
    endsAt: IsoDateTime;
    salesOpensAt: IsoDateTime;
    salesClosesAt: IsoDateTime;
    holdMinutes: number;
    seatKind: SeatKind;
    salesStatus: SalesStatus;
    remaining: number;
    maxSeatsPerBuyer: number;
}

export interface PerformanceResponse {
    id: number;
    title: string;
    venue: string;
    posterUrl: string | null;
    showtimes: ShowtimeSummary[];
    serverTime: IsoDateTime;
}

export interface MeResponse {
    buyerId: string;
    nickname: string;
    serverTime: IsoDateTime;
}

export interface AvailabilityResponse {
    showtimeId: number;
    salesStatus: SalesStatus;
    remaining: number;
    maxSeatsPerBuyer: number;
    ownedCount: number;
    serverTime: IsoDateTime;
}

export interface CreateOrderRequest {
    showtimeId: number;
    quantity: number;
    buyerName: string;
}

export interface OrderPerformance {
    id: number;
    title: string;
    venue: string;
}

export interface OrderShowtime {
    id: number;
    startsAt: IsoDateTime;
    endsAt: IsoDateTime;
}

export interface OrderResponse {
    token: string;
    status: OrderStatus;
    performance: OrderPerformance;
    showtime: OrderShowtime;
    quantity: number;
    entryNumbers: number[];
    buyerName: string;
    heldUntil: IsoDateTime;
    createdAt: IsoDateTime;
    isOwner: boolean;
    serverTime: IsoDateTime;
}

export interface MyOrderSummary {
    token: string;
    status: OrderStatus;
    performance: OrderPerformance;
    showtime: OrderShowtime;
    quantity: number;
    entryNumbers: number[];
    heldUntil: IsoDateTime;
    createdAt: IsoDateTime;
}

export interface MyOrdersResponse {
    orders: MyOrderSummary[];
    serverTime: IsoDateTime;
}
