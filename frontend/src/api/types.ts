export type IsoDateTime = string;

export type SalesStatus = 'BEFORE_SALE' | 'ON_SALE' | 'SOLD_OUT' | 'SALES_CLOSED';

export type SeatKind = 'FREE' | 'RESERVED';

export type OrderStatus = 'HELD' | 'CONFIRMED' | 'EXPIRED' | 'CANCELLED';

export type ProblemCode =
    | 'SALES_NOT_OPEN'
    | 'SALES_CLOSED'
    | 'SEAT_LIMIT_EXCEEDED'
    | 'SEAT_NOT_IN_SHOWTIME'
    | 'SEAT_NOT_AVAILABLE'
    | 'ORDER_EXPIRED'
    | 'ORDER_NOT_OWNED'
    | 'ORDER_CANCELLED'
    | 'ORDER_CONFIRMED';

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
    seatKind: SeatKind;
    salesStatus: SalesStatus;
    remaining: number;
    maxSeatsPerBuyer: number;
}

export interface ShowResponse {
    id: number;
    title: string;
    venue: string;
    posterUrl: string | null;
    holdMinutes: number;
    showtimes: ShowtimeSummary[];
    serverTime: IsoDateTime;
}

export interface MeResponse {
    nickname: string;
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

export interface OrderShow {
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
    show: OrderShow;
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
    show: OrderShow;
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
