import { useQueryClient } from '@tanstack/react-query';
import { toPng } from 'html-to-image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useLocation, useParams } from 'react-router';
import { isApiError, isNotFound, isUnauthorized, NoResponseError } from '../api/client';
import { api, queryKeys, useMe, useOrder } from '../api/queries';
import type { OrderResponse } from '../api/types';
import { useLogin } from '../auth/useLogin';
import { ActionGrid, icons } from '../components/ActionGrid';
import { Badge } from '../components/Badge';
import { BottomBar } from '../components/BottomBar';
import { BottomSheet } from '../components/BottomSheet';
import { Button, ButtonLink } from '../components/Button';
import { Header } from '../components/Header';
import { KakaoLoginButton } from '../components/KakaoLoginButton';
import { LoadError, Loading } from '../components/LoadState';
import { Notice } from '../components/Notice';
import { Page } from '../components/Page';
import { TicketCard } from '../components/TicketCard';
import { useToast } from '../components/Toast';
import { copy } from '../copy';
import { formatCountdown, msUntil, serverNow } from '../lib/clock';
import { formatDateTime } from '../lib/format';
import { buildIcs } from '../lib/ics';
import { orderUrl, orderViewOf, type OrderView } from '../lib/orderView';
import { downloadFile, shareLink } from '../lib/share';
import { useServerNow } from '../lib/useServerNow';
import styles from './OrderPage.module.css';

export function OrderPage() {
    const { token = '' } = useParams();
    const orderQuery = useOrder(token);

    if (orderQuery.isPending) {
        return <Page header={<Header />}><Loading /></Page>;
    }
    if (orderQuery.isError) {
        if (isNotFound(orderQuery.error)) {
            return <OrderNotFound />;
        }
        return <Page header={<Header />}><LoadError onRetry={() => orderQuery.refetch()} /></Page>;
    }
    return <OrderDetail order={orderQuery.data} refetch={orderQuery.refetch} />;
}

function OrderNotFound() {
    const text = copy.orderPage.notFound;
    return (
        <Page header={<Header />} bottomBar={<BottomBar><ButtonLink to="/me/orders">{text.toMyOrders}</ButtonLink></BottomBar>}>
            <Notice title={text.title} body={text.body} caption={text.caption} centered />
        </Page>
    );
}

function OrderDetail({ order, refetch }: { order: OrderResponse; refetch: () => unknown }) {
    const text = copy.orderPage;
    const queryClient = useQueryClient();
    const toast = useToast();
    const login = useLogin();
    const location = useLocation();
    const me = useMe().data ?? null;
    const [rejectedAs, setRejectedAs] = useState<OrderView | null>(null);
    const [sheetOpen, setSheetOpen] = useState(false);
    const [busy, setBusy] = useState<'confirm' | 'cancel' | 'image' | null>(null);
    const ticketRef = useRef<HTMLElement>(null);

    const derived = orderViewOf(order, serverNow());
    const view: OrderView = derived === 'held' && rejectedAs ? rejectedAs : derived;
    const now = useServerNow(view === 'held');
    const timeUp = view === 'held' && msUntil(order.heldUntil, now) === 0;
    const shownView: OrderView = timeUp ? 'expired' : view;

    useEffect(() => {
        if (timeUp) {
            refetch();
        }
    }, [timeUp, refetch]);

    const applyOrder = (next: OrderResponse) => {
        queryClient.setQueryData(queryKeys.order(next.token), next);
        void queryClient.invalidateQueries({ queryKey: queryKeys.myOrders });
        void queryClient.invalidateQueries({ queryKey: queryKeys.availability(next.showtime.id) });
    };

    const handleFailure = async (error: unknown, action: 'confirm' | 'cancel') => {
        if (error instanceof NoResponseError) {
            toast(action === 'confirm' ? text.problem.confirmNoResponse : text.problem.cancelNoResponse);
            return;
        }
        if (isUnauthorized(error)) {
            await login(location.pathname).catch(() => toast(copy.common.loginFailed));
            return;
        }
        if (isApiError(error, 'ORDER_EXPIRED')) {
            setRejectedAs('expired');
            toast(text.problem.ORDER_EXPIRED);
        } else if (isApiError(error, 'ORDER_CANCELLED')) {
            setRejectedAs('cancelled');
            toast(text.problem.ORDER_CANCELLED);
        } else if (isApiError(error, 'ORDER_CONFIRMED')) {
            toast(text.problem.ORDER_CONFIRMED);
        } else {
            toast(copy.common.unknownError);
        }
        refetch();
    };

    const confirm = async () => {
        setBusy('confirm');
        try {
            applyOrder(await api.confirmOrder(order.token));
        } catch (error) {
            await handleFailure(error, 'confirm');
        } finally {
            setBusy(null);
        }
    };

    const cancel = async () => {
        setBusy('cancel');
        try {
            applyOrder(await api.cancelOrder(order.token));
            setSheetOpen(false);
        } catch (error) {
            setSheetOpen(false);
            await handleFailure(error, 'cancel');
        } finally {
            setBusy(null);
        }
    };

    const share = async () => {
        const result = await shareLink({ title: text.shareTitle(order.show.title), url: orderUrl(order.token) });
        if (result === 'copied') {
            toast(text.toast.copied);
        } else if (result === 'failed') {
            toast(text.toast.copyFailed);
        }
    };

    const saveImage = async () => {
        if (!ticketRef.current) {
            return;
        }
        setBusy('image');
        try {
            const dataUrl = await toPng(ticketRef.current, { pixelRatio: 2, backgroundColor: '#ffffff' });
            downloadFile(`openrun-ticket-${order.token.slice(0, 8)}.png`, dataUrl);
            toast(text.toast.saved);
        } catch {
            toast(text.toast.saveFailed);
        } finally {
            setBusy(null);
        }
    };

    const addToCalendar = () => {
        const url = orderUrl(order.token);
        const ics = buildIcs({
            uid: `${order.token}@openrun`,
            title: order.show.title,
            location: order.show.venue,
            startsAt: order.showtime.startsAt,
            endsAt: order.showtime.endsAt,
            url,
            description: text.calendarDescription(order.entryNumbers, order.buyerName, url),
            stampedAt: new Date(serverNow()).toISOString(),
        });
        const href = URL.createObjectURL(new Blob([ics], { type: 'text/calendar;charset=utf-8' }));
        downloadFile(`openrun-${order.token.slice(0, 8)}.ics`, href);
        setTimeout(() => URL.revokeObjectURL(href), 10_000);
        toast(text.toast.calendar);
    };

    const closeSheet = useCallback(() => setSheetOpen(false), []);
    const retryPath = `/shows/${order.show.id}/showtimes/${order.showtime.id}/quantity`;
    const showPath = `/shows/${order.show.id}`;
    const header = <Header />;

    if (!order.isOwner) {
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to={showPath} variant="secondary">{text.viewer.toShow}</ButtonLink></BottomBar>}>
                <Notice title={text.viewer.title} body={text.viewer.body}>
                    {!me && (
                        <>
                            <p className="t-caption">{text.viewer.ownerLogin}</p>
                            <KakaoLoginButton onLogin={() => login(location.pathname).catch(() => toast(copy.common.loginFailed))} />
                        </>
                    )}
                </Notice>
                <div className={styles.statusRow}>
                    <StatusBadge view={shownView} />
                    {shownView === 'held' && (
                        <span className="t-label">
                            {text.viewer.remaining} <span className={styles.inlineTimer}>{formatCountdown(msUntil(order.heldUntil, now))}</span>
                        </span>
                    )}
                </div>
                <TicketCard
                    order={order}
                    qr={shownView === 'confirmed' ? 'clear' : shownView === 'held' ? 'dim' : 'none'}
                    qrNote={shownView === 'held' ? text.viewer.qrNote : undefined}
                    qrSize={shownView === 'confirmed' ? 200 : 136}
                />
            </Page>
        );
    }

    if (shownView === 'held') {
        return (
            <Page
                header={header}
                bottomBar={
                    <BottomBar>
                        <Button onClick={() => void confirm()} disabled={busy !== null}>
                            {busy === 'confirm' ? text.held.confirming : text.held.confirm}
                        </Button>
                        <div className={styles.pair}>
                            <Button variant="secondary" onClick={() => setSheetOpen(true)} disabled={busy !== null}>
                                {text.held.cancel}
                            </Button>
                            <Button variant="secondary" onClick={() => void share()}>
                                {icons.share}
                                {text.held.share}
                            </Button>
                        </div>
                    </BottomBar>
                }
            >
                <section className={styles.timerBlock}>
                    <StatusBadge view="held" />
                    <span className="t-label">{text.held.remaining}</span>
                    <span className={`${styles.timer} t-numeral-timer`} role="timer" aria-live="off">
                        {formatCountdown(msUntil(order.heldUntil, now))}
                    </span>
                    <span className="t-caption">{text.held.deadline(formatDateTime(order.heldUntil))}</span>
                </section>
                <TicketCard order={order} qr="dim" qrNote={text.held.qrNote} qrSize={160} />
                {sheetOpen && (
                    <BottomSheet
                        title={text.cancelSheet.title}
                        onClose={closeSheet}
                        actions={
                            <>
                                <Button onClick={() => void cancel()} disabled={busy !== null}>
                                    {busy === 'cancel' ? text.cancelSheet.cancelling : text.cancelSheet.confirm}
                                </Button>
                                <Button variant="secondary" onClick={closeSheet}>
                                    {text.cancelSheet.keep}
                                </Button>
                            </>
                        }
                    >
                        <p className="t-body">{text.cancelSheet.body(order.entryNumbers)}</p>
                        <p className="t-caption">{text.cancelSheet.caption(order.quantity)}</p>
                    </BottomSheet>
                )}
            </Page>
        );
    }

    if (shownView === 'confirmed') {
        return (
            <Page
                header={header}
                bottomBar={
                    <BottomBar>
                        <ActionGrid
                            actions={[
                                { label: text.confirmed.saveImage, icon: icons.download, onClick: () => void saveImage(), disabled: busy === 'image' },
                                { label: text.confirmed.addCalendar, icon: icons.calendar, onClick: addToCalendar },
                                { label: text.confirmed.share, icon: icons.share, onClick: () => void share() },
                            ]}
                        />
                    </BottomBar>
                }
            >
                <section className={styles.headline}>
                    <StatusBadge view="confirmed" />
                    <h1 className="t-title">{text.confirmed.title}</h1>
                    <p className="t-body">{text.confirmed.body}</p>
                </section>
                <TicketCard ref={ticketRef} order={order} qr="clear" qrNote={text.confirmed.qrNote} qrSize={200} />
            </Page>
        );
    }

    const closed = shownView === 'expired'
        ? { title: text.expired.title, body: text.expired.body(formatDateTime(order.heldUntil), order.entryNumbers), caption: text.expired.caption, retry: text.expired.retry }
        : { title: text.cancelled.title, body: text.cancelled.body(order.entryNumbers), caption: undefined, retry: text.cancelled.retry };

    return (
        <Page header={header} bottomBar={<BottomBar><ButtonLink to={retryPath}>{closed.retry}</ButtonLink></BottomBar>}>
            <section className={styles.headline} role="status">
                <StatusBadge view={shownView} />
                <h1 className="t-title">{closed.title}</h1>
                <p className="t-body">{closed.body}</p>
                {closed.caption && <p className="t-caption">{closed.caption}</p>}
            </section>
            <TicketCard order={order} qr="none" />
        </Page>
    );
}

function StatusBadge({ view }: { view: OrderView }) {
    const tone = view === 'held' ? 'accent' : view === 'confirmed' ? 'solid' : 'muted';
    return <Badge tone={tone}>{copy.orderPage.status[view]}</Badge>;
}
