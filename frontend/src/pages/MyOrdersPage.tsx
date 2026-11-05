import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { useMe, useMyOrders } from '../api/queries';
import type { MyOrderSummary } from '../api/types';
import { useLogin } from '../auth/useLogin';
import { Badge } from '../components/Badge';
import { Header } from '../components/Header';
import { KakaoLoginButton } from '../components/KakaoLoginButton';
import { LoadError, Loading } from '../components/LoadState';
import { Notice } from '../components/Notice';
import { Page } from '../components/Page';
import { useToast } from '../components/Toast';
import { copy } from '../copy';
import { formatCountdown, msUntil } from '../lib/clock';
import { formatShowtime, isLatinTitle } from '../lib/format';
import { orderViewOf, type OrderView } from '../lib/orderView';
import { useServerNow } from '../lib/useServerNow';
import styles from './MyOrdersPage.module.css';

const tone = { held: 'accent', confirmed: 'solid', expired: 'muted', cancelled: 'muted' } as const;

export function MyOrdersPage() {
    const text = copy.myOrdersPage;
    const meQuery = useMe();
    const me = meQuery.data ?? null;
    const ordersQuery = useMyOrders(Boolean(me));
    const header = <Header title={text.title} showMyOrders={false} />;

    if (meQuery.isPending) {
        return <Page header={header}><Loading /></Page>;
    }
    if (!me) {
        return <LoginRequired header={header} />;
    }
    if (ordersQuery.isPending) {
        return <Page header={header}><Loading /></Page>;
    }
    if (ordersQuery.isError) {
        return <Page header={header}><LoadError onRetry={() => ordersQuery.refetch()} /></Page>;
    }
    const { orders } = ordersQuery.data;
    return (
        <Page header={header}>
            <p className="t-caption">{text.owner(me.nickname)}</p>
            {orders.length === 0 ? <Notice title={text.empty} body={text.emptyBody} /> : <OrderList orders={orders} />}
            <p className="t-caption">{text.footer}</p>
        </Page>
    );
}

function OrderList({ orders }: { orders: MyOrderSummary[] }) {
    const note = copy.myOrdersPage.note;
    const now = useServerNow(orders.some((o) => o.status === 'HELD'));
    const noteOf = (order: MyOrderSummary, view: OrderView) => {
        switch (view) {
            case 'held':
                return note.held(order.quantity, order.entryNumbers, formatCountdown(msUntil(order.heldUntil, now)));
            case 'confirmed':
                return note.confirmed(order.quantity, order.entryNumbers);
            case 'expired':
                return note.expired(order.quantity);
            case 'cancelled':
                return note.cancelled(order.quantity);
        }
    };
    return (
        <ul className={styles.list}>
            {orders.map((order) => {
                const view = orderViewOf(order, now);
                return (
                    <li key={order.token}>
                        <Link to={`/orders/${order.token}`} className={styles.item}>
                            <span className={styles.body}>
                                <span className={`${styles.title} ${isLatinTitle(order.show.title) ? styles.latin : ''}`}>{order.show.title}</span>
                                <span className="t-data">{formatShowtime(order.showtime.startsAt, order.showtime.endsAt)}</span>
                                <span className="t-caption">{noteOf(order, view)}</span>
                            </span>
                            <Badge tone={tone[view]}>{copy.orderPage.status[view]}</Badge>
                            <svg className={styles.chevron} width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                                <path d="M9 5l7 7-7 7" />
                            </svg>
                        </Link>
                    </li>
                );
            })}
        </ul>
    );
}

function LoginRequired({ header }: { header: ReactNode }) {
    const text = copy.myOrdersPage.login;
    const login = useLogin();
    const toast = useToast();
    return (
        <Page header={header}>
            <Notice title={text.title} body={text.body} variant="outlined">
                <KakaoLoginButton onLogin={() => login('/me/orders').catch(() => toast(copy.common.loginFailed))} />
                <p className="t-caption">{copy.login.note}</p>
            </Notice>
            <Notice title={text.linkTitle} body={text.linkBody} />
        </Page>
    );
}
