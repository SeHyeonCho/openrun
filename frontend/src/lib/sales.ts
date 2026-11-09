import type { SalesStatus, ShowtimeSummary } from '../api/types';

export function effectiveSalesStatus(showtime: ShowtimeSummary, now: number): SalesStatus {
    if (showtime.salesStatus === 'SALES_CLOSED' || now >= Date.parse(showtime.salesClosesAt)) {
        return 'SALES_CLOSED';
    }
    if (showtime.salesStatus === 'BEFORE_SALE') {
        return now < Date.parse(showtime.salesOpensAt) ? 'BEFORE_SALE' : 'ON_SALE';
    }
    return showtime.salesStatus;
}
