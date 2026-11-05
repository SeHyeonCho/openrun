import { QRCodeSVG } from 'qrcode.react';
import { forwardRef } from 'react';
import type { OrderResponse } from '../api/types';
import { copy } from '../copy';
import { formatShowtime, isLatinTitle } from '../lib/format';
import { orderUrl } from '../lib/orderView';
import styles from './TicketCard.module.css';

type TicketCardProps = {
    order: OrderResponse;
    qr: 'clear' | 'dim' | 'none';
    qrNote?: string;
    qrSize?: number;
};

export const TicketCard = forwardRef<HTMLElement, TicketCardProps>(function TicketCard({ order, qr, qrNote, qrSize = 176 }, ref) {
    const text = copy.ticket;
    return (
        <article ref={ref} aria-label={text.label} className={`${styles.card} ${qr === 'none' ? styles.void : ''}`}>
            <div className={styles.head}>
                <span className={`${styles.title} ${isLatinTitle(order.show.title) ? styles.latin : ''}`}>{order.show.title}</span>
                <span className="t-data">{formatShowtime(order.showtime.startsAt, order.showtime.endsAt, { year: true })}</span>
                <span className="t-caption">{order.show.venue}</span>
            </div>
            <div className={styles.divider} aria-hidden="true" />
            <div className={styles.row}>
                <div className={styles.entry}>
                    <span className="t-caption">{text.entry}</span>
                    <span className={`${styles.numbers} t-numeral`}>{order.entryNumbers.join(' · ')}</span>
                </div>
                <div className={styles.buyer}>
                    <span className="t-label">{order.buyerName}</span>
                    <span className="t-caption">{text.quantity(order.quantity)}</span>
                </div>
            </div>
            {qr !== 'none' && (
                <div className={styles.qr}>
                    <div className={`${styles.qrCode} ${qr === 'dim' ? styles.dim : ''}`} role="img" aria-label={qr === 'dim' ? text.qrDim : text.qr}>
                        <QRCodeSVG value={orderUrl(order.token)} size={qrSize} level="M" fgColor="#111111" bgColor="#ffffff" aria-hidden="true" />
                    </div>
                    {qrNote && <span className="t-caption">{qrNote}</span>}
                </div>
            )}
        </article>
    );
});
