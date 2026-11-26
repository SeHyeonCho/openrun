import { useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useLocation, useParams } from 'react-router';
import { isNotFound } from '../api/client';
import { api, queryKeys, useAvailability, useMe, usePerformance } from '../api/queries';
import type { AvailabilityResponse, MeResponse, PerformanceResponse, ShowtimeSummary } from '../api/types';
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
import { effectiveSalesStatus, purchasableCount } from '../lib/sales';
import { useHoldSubmission } from '../lib/useHoldSubmission';
import { useServerNow } from '../lib/useServerNow';
import styles from './QuantityPage.module.css';

export function QuantityPage() {
    const { performanceId = '', showtimeId = '' } = useParams();
    const text = copy.quantityPage;
    const performanceQuery = usePerformance(performanceId);
    const meQuery = useMe();
    const me = meQuery.data ?? null;
    const showtime = performanceQuery.data?.showtimes.find((s) => String(s.id) === showtimeId) ?? null;
    const availabilityQuery = useAvailability(Number(showtimeId), Boolean(me && showtime?.seatKind === 'GENERAL'));
    const header = <Header title={text.title} backTo={`/performances/${performanceId}`} />;

    if (performanceQuery.isPending || meQuery.isPending) {
        return <Page header={header}><Loading /></Page>;
    }
    if (performanceQuery.isError) {
        return (
            <Page header={header}>
                {isNotFound(performanceQuery.error) ? <Notice title={copy.performancePage.notFound} body={copy.performancePage.notFoundBody} /> : <LoadError onRetry={() => performanceQuery.refetch()} />}
            </Page>
        );
    }
    if (!showtime) {
        return <Page header={header}><Notice title={text.notFound} body={copy.performancePage.notFoundBody} /></Page>;
    }
    if (showtime.seatKind === 'RESERVED') {
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to={`/performances/${performanceId}`}>{text.reserved.cta}</ButtonLink></BottomBar>}>
                <Notice title={text.reserved.title} body={text.reserved.body} />
            </Page>
        );
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
    return (
        <QuantityForm
            key={`${me.buyerId}:${showtime.id}`}
            header={header}
            performance={performanceQuery.data}
            showtime={showtime}
            me={me}
            availability={availabilityQuery.data}
        />
    );
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
    performance: PerformanceResponse;
    showtime: ShowtimeSummary;
    me: MeResponse;
    availability: AvailabilityResponse;
};

function QuantityForm({ header, performance, showtime, me, availability }: FormProps) {
    const text = copy.quantityPage;
    const queryClient = useQueryClient();
    const now = useServerNow();
    const [quantity, setQuantity] = useState(1);
    const [adjustedFrom, setAdjustedFrom] = useState<number | null>(null);
    const [buyerName, setBuyerName] = useState(me.nickname);

    const { remaining, maxSeatsPerBuyer: max, ownedCount: owned } = availability;
    const limit = purchasableCount(availability);
    const chosen = Math.min(Math.max(quantity, 1), Math.max(limit, 1));
    const status = effectiveSalesStatus({ ...showtime, salesStatus: availability.salesStatus, remaining }, now);
    const nameOk = buyerName.trim() !== '';

    const refreshAvailability = async () => {
        const fresh = await queryClient.fetchQuery({
            queryKey: queryKeys.availability(showtime.id),
            queryFn: () => api.getAvailability(showtime.id),
            staleTime: 0,
        });
        const freshLimit = purchasableCount(fresh);
        if (freshLimit < chosen) {
            setAdjustedFrom(freshLimit > 0 ? chosen : null);
            setQuantity(Math.max(1, freshLimit));
        }
        return fresh;
    };

    const { phase, otherBuyerPending, submit, retry } = useHoldSubmission({
        performanceId: performance.id,
        showtimeId: showtime.id,
        buyerId: me.buyerId,
        refreshAvailability,
    });

    const hold = () => submit({ showtimeId: showtime.id, quantity: chosen, buyerName: buyerName.trim() });

    const showWhen = formatShowtime(showtime.startsAt, showtime.endsAt);
    const summary = <ShowSummary title={performance.title} when={showWhen} where={`${performance.venue} · ${copy.seatKind[showtime.seatKind]}`} />;
    const backToShow = `/performances/${performance.id}`;

    if (phase.kind === 'unknown') {
        const unknown = text.unknown;
        return (
            <Page
                header={header}
                bottomBar={
                    <BottomBar note={unknown.note}>
                        <ButtonLink to="/me/orders">{unknown.toMyOrders}</ButtonLink>
                        <Button variant="secondary" onClick={retry}>
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

    if (phase.kind === 'pastResendLimit') {
        const past = text.pastResendLimit;
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to="/me/orders">{past.toMyOrders}</ButtonLink></BottomBar>}>
                {summary}
                <Notice title={past.title} body={past.body} variant="outlined" centered live />
            </Page>
        );
    }

    if (phase.kind === 'buyerChanged') {
        const changed = text.buyerChanged;
        return (
            <Page header={header} bottomBar={<BottomBar><ButtonLink to="/me/orders">{changed.toMyOrders}</ButtonLink></BottomBar>}>
                {summary}
                <Notice title={changed.title} body={changed.body} variant="outlined" centered live />
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
            {copy.performancePage.cta.beforeSale} <span className={styles.countdown}>{formatCountdown(msUntil(showtime.salesOpensAt, now))}</span>
        </Button>
    ) : (
        <Button onClick={hold} disabled={busy || !nameOk}>
            {phase.kind === 'resending' ? text.resending : busy ? text.holding : text.hold(chosen)}
        </Button>
    );

    return (
        <Page header={header} bottomBar={<BottomBar note={text.holdNote(showtime.holdMinutes)}>{cta}</BottomBar>}>
            {summary}
            {otherBuyerPending && <Notice title={text.otherBuyerPending.title} body={text.otherBuyerPending.body} variant="outlined" />}
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
