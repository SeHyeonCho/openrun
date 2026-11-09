import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router';
import { isApiError, isNotFound, isUnauthorized, isUnknownResult, RESEND_DELAYS_MS } from '../api/client';
import { api, newIdempotencyKey, queryKeys, useAvailability, useMe, useShow } from '../api/queries';
import type { AvailabilityResponse, CreateOrderRequest, MeResponse, ShowResponse, ShowtimeSummary } from '../api/types';
import { useLogin } from '../auth/useLogin';
import { BottomBar } from '../components/BottomBar';
import { Button, ButtonLink } from '../components/Button';
import { Header } from '../components/Header';
import { KakaoLoginButton } from '../components/KakaoLoginButton';
import { LoadError, Loading } from '../components/LoadState';
import { Notice } from '../components/Notice';
import { Page } from '../components/Page';
import { ShowSummary } from '../components/ShowSummary';
import { Stepper } from '../components/Stepper';
import { useToast } from '../components/Toast';
import { copy } from '../copy';
import { formatCountdown, msUntil } from '../lib/clock';
import { formatShowtime } from '../lib/format';
import { effectiveSalesStatus } from '../lib/sales';
import { useServerNow } from '../lib/useServerNow';
import styles from './QuantityPage.module.css';

const NOT_OPEN_RETRY_MS = 1_500;
const NOT_OPEN_MAX_RETRIES = 5;
type Phase = { kind: 'idle' } | { kind: 'holding' } | { kind: 'waitingOpen' } | { kind: 'resending' } | { kind: 'unknown' } | { kind: 'closed' };

export function QuantityPage() {
    const { showId = '', showtimeId = '' } = useParams();
    const text = copy.quantityPage;
    const showQuery = useShow(showId);
    const meQuery = useMe();
    const me = meQuery.data ?? null;
    const showtime = showQuery.data?.showtimes.find((s) => String(s.id) === showtimeId) ?? null;
    const availabilityQuery = useAvailability(Number(showtimeId), Boolean(me && showtime));
    const header = <Header title={text.title} backTo={`/shows/${showId}`} />;

    if (showQuery.isPending || meQuery.isPending) {
        return <Page header={header}><Loading /></Page>;
    }
    if (showQuery.isError) {
        return (
            <Page header={header}>
                {isNotFound(showQuery.error) ? <Notice title={copy.showPage.notFound} body={copy.showPage.notFoundBody} /> : <LoadError onRetry={() => showQuery.refetch()} />}
            </Page>
        );
    }
    if (!showtime) {
        return <Page header={header}><Notice title={text.notFound} body={copy.showPage.notFoundBody} /></Page>;
    }
    if (meQuery.isError) {
        return <Page header={header}><LoadError onRetry={() => meQuery.refetch()} /></Page>;
    }
    if (!me) {
        return <LoginRequired header={header} />;
    }
    if (availabilityQuery.isPending) {
        return <Page header={header}><Loading /></Page>;
    }
    if (availabilityQuery.isError) {
        return <Page header={header}><LoadError onRetry={() => availabilityQuery.refetch()} /></Page>;
    }
    return <QuantityForm header={header} show={showQuery.data} showtime={showtime} me={me} availability={availabilityQuery.data} />;
}

function LoginRequired({ header }: { header: ReactNode }) {
    const text = copy.quantityPage.login;
    const login = useLogin();
    const toast = useToast();
    const location = useLocation();
    const loginHere = async () => {
        try {
            await login(location.pathname);
        } catch {
            toast(copy.common.loginFailed);
        }
    };
    return (
        <Page header={header}>
            <Notice title={text.title} body={text.body} variant="outlined">
                <KakaoLoginButton onLogin={loginHere} />
                <p className="t-caption">{copy.login.note}</p>
            </Notice>
        </Page>
    );
}

type FormProps = {
    header: ReactNode;
    show: ShowResponse;
    showtime: ShowtimeSummary;
    me: MeResponse;
    availability: AvailabilityResponse;
};

function QuantityForm({ header, show, showtime, me, availability }: FormProps) {
    const text = copy.quantityPage;
    const navigate = useNavigate();
    const location = useLocation();
    const queryClient = useQueryClient();
    const login = useLogin();
    const toast = useToast();
    const now = useServerNow();
    const [quantity, setQuantity] = useState(1);
    const [adjustedFrom, setAdjustedFrom] = useState<number | null>(null);
    const [buyerName, setBuyerName] = useState(me.nickname);
    const [phase, setPhase] = useState<Phase>({ kind: 'idle' });
    const retryTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    const pending = useRef<{ key: string; body: CreateOrderRequest } | null>(null);

    const unmounted = useRef(false);

    useEffect(() => {
        unmounted.current = false;
        return () => {
            unmounted.current = true;
            clearTimeout(retryTimer.current);
        };
    }, []);

    const { remaining, maxSeatsPerBuyer: max, ownedCount: owned } = availability;
    const limit = Math.max(0, Math.min(remaining, max - owned));
    const chosen = Math.min(Math.max(quantity, 1), Math.max(limit, 1));
    const status = effectiveSalesStatus({ ...showtime, salesStatus: availability.salesStatus, remaining }, now);
    const nameOk = buyerName.trim() !== '';

    const refreshAvailability = async () => {
        const fresh = await queryClient.fetchQuery({
            queryKey: queryKeys.availability(showtime.id),
            queryFn: () => api.getAvailability(showtime.id),
            staleTime: 0,
        });
        const freshLimit = Math.max(0, Math.min(fresh.remaining, fresh.maxSeatsPerBuyer - fresh.ownedCount));
        if (freshLimit < chosen) {
            setAdjustedFrom(freshLimit > 0 ? chosen : null);
            setQuantity(Math.max(1, freshLimit));
        }
        return fresh;
    };

    const hold = () => {
        pending.current = {
            key: newIdempotencyKey(),
            body: { showtimeId: showtime.id, quantity: chosen, buyerName: buyerName.trim() },
        };
        void send(0, 0);
    };

    const send = async (notOpenAttempt: number, resendCount: number) => {
        const request = pending.current;
        if (!request) {
            return;
        }
        setPhase({ kind: resendCount > 0 ? 'resending' : 'holding' });
        try {
            const order = await api.createOrder(request.body, request.key);
            pending.current = null;
            queryClient.setQueryData(queryKeys.order(order.token), order);
            void queryClient.invalidateQueries({ queryKey: queryKeys.availability(showtime.id) });
            void queryClient.invalidateQueries({ queryKey: queryKeys.myOrders });
            void queryClient.invalidateQueries({ queryKey: queryKeys.show(String(show.id)) });
            if (!unmounted.current) {
                navigate(`/orders/${order.token}`);
            }
        } catch (error) {
            if (unmounted.current) {
                return;
            }
            if (isUnknownResult(error)) {
                if (resendCount < RESEND_DELAYS_MS.length) {
                    setPhase({ kind: 'resending' });
                    retryTimer.current = setTimeout(() => void send(notOpenAttempt, resendCount + 1), RESEND_DELAYS_MS[resendCount] ?? 0);
                    return;
                }
                setPhase({ kind: 'unknown' });
                return;
            }
            pending.current = null;
            if (isUnauthorized(error)) {
                setPhase({ kind: 'idle' });
                await login(location.pathname).catch(() => toast(copy.common.loginFailed));
                return;
            }
            if (isApiError(error, 'SALES_NOT_OPEN')) {
                if (notOpenAttempt < NOT_OPEN_MAX_RETRIES) {
                    toast(text.problem.SALES_NOT_OPEN);
                    setPhase({ kind: 'waitingOpen' });
                    retryTimer.current = setTimeout(() => {
                        pending.current = { key: newIdempotencyKey(), body: request.body };
                        void send(notOpenAttempt + 1, 0);
                    }, NOT_OPEN_RETRY_MS);
                    return;
                }
                toast(text.problem.salesNotOpenGaveUp);
                setPhase({ kind: 'idle' });
                return;
            }
            if (isApiError(error, 'SALES_CLOSED')) {
                toast(text.problem.SALES_CLOSED);
                setPhase({ kind: 'closed' });
                void queryClient.invalidateQueries({ queryKey: queryKeys.show(String(show.id)) });
                return;
            }
            if (isApiError(error, 'SEAT_NOT_AVAILABLE') || isApiError(error, 'SEAT_LIMIT_EXCEEDED')) {
                setPhase({ kind: 'idle' });
                try {
                    const fresh = await refreshAvailability();
                    if (isApiError(error, 'SEAT_LIMIT_EXCEEDED')) {
                        toast(text.problem.SEAT_LIMIT_EXCEEDED(fresh.maxSeatsPerBuyer, fresh.ownedCount));
                    } else {
                        toast(fresh.remaining > 0 ? text.problem.SEAT_NOT_AVAILABLE(fresh.remaining) : text.problem.soldOut);
                    }
                } catch {
                    toast(copy.common.unknownError);
                }
                return;
            }
            toast(copy.common.unknownError);
            setPhase({ kind: 'idle' });
        }
    };

    const showWhen = formatShowtime(showtime.startsAt, showtime.endsAt);
    const summary = <ShowSummary title={show.title} when={showWhen} where={`${show.venue} · ${copy.seatKind[showtime.seatKind]}`} />;
    const backToShow = `/shows/${show.id}`;

    if (phase.kind === 'unknown') {
        const unknown = text.unknown;
        return (
            <Page
                header={header}
                bottomBar={
                    <BottomBar note={unknown.note}>
                        <ButtonLink to="/me/orders">{unknown.toMyOrders}</ButtonLink>
                        <Button variant="secondary" onClick={() => void send(0, 0)}>
                            {unknown.retry}
                        </Button>
                    </BottomBar>
                }
            >
                {summary}
                <Notice title={unknown.title} body={unknown.body} variant="outlined" centered live />
            </Page>
        );
    }

    if (phase.kind === 'closed' || status === 'SALES_CLOSED') {
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to={backToShow}>{text.closed.cta}</ButtonLink></BottomBar>}>
                {summary}
                <Notice title={text.closed.title} body={text.closed.body} live />
            </Page>
        );
    }

    if (remaining === 0 || status === 'SOLD_OUT') {
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to={backToShow}>{text.soldOut.cta}</ButtonLink></BottomBar>}>
                {summary}
                <Notice title={text.soldOut.title} body={text.soldOut.body} caption={text.soldOut.caption} live />
            </Page>
        );
    }

    if (limit === 0) {
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to="/me/orders">{text.limitReached.cta}</ButtonLink></BottomBar>}>
                {summary}
                <Notice title={text.limitReached.title(max)} body={text.limitReached.body} live />
            </Page>
        );
    }

    const beforeSale = status === 'BEFORE_SALE';
    const busy = phase.kind === 'holding' || phase.kind === 'waitingOpen' || phase.kind === 'resending';
    const cta = beforeSale ? (
        <Button disabled>
            {copy.showPage.cta.beforeSale} <span className={styles.countdown}>{formatCountdown(msUntil(showtime.salesOpensAt, now))}</span>
        </Button>
    ) : (
        <Button onClick={hold} disabled={busy || !nameOk}>
            {phase.kind === 'resending' ? text.resending : busy ? text.holding : text.hold(chosen)}
        </Button>
    );

    return (
        <Page header={header} bottomBar={<BottomBar note={text.holdNote(show.holdMinutes)}>{cta}</BottomBar>}>
            {summary}
            <section className={styles.section}>
                <div className={styles.row}>
                    <h2 className="t-heading">{text.quantity}</h2>
                    <span className="t-label">
                        {text.remaining} <span className={styles.number}>{remaining}</span>
                        {text.seatUnit}
                    </span>
                </div>
                <Stepper
                    value={chosen}
                    min={1}
                    max={limit}
                    unit={text.unit}
                    decreaseLabel={text.decrease}
                    increaseLabel={text.increase}
                    disabled={busy}
                    onChange={(next) => {
                        setAdjustedFrom(null);
                        setQuantity(next);
                    }}
                />
                <p className="t-caption">{adjustedFrom !== null ? text.adjusted(adjustedFrom, chosen) : text.limitNote(max, owned, limit)}</p>
            </section>
            <section className={styles.section}>
                <label htmlFor="buyer-name" className="t-heading">
                    {text.buyerName}
                </label>
                <input
                    id="buyer-name"
                    className={styles.input}
                    type="text"
                    value={buyerName}
                    maxLength={20}
                    autoComplete="nickname"
                    aria-describedby="buyer-name-note"
                    aria-invalid={!nameOk}
                    onChange={(e) => setBuyerName(e.target.value)}
                />
                <p id="buyer-name-note" className="t-caption" aria-live="polite">
                    {nameOk ? text.buyerNameNote : text.buyerNameRequired}
                </p>
            </section>
            <ul className={styles.guide}>
                <li>{text.guideEntry}</li>
                <li>{text.guideSpread}</li>
            </ul>
        </Page>
    );
}
