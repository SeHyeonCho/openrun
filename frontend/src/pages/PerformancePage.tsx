import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { isNotFound } from '../api/client';
import { useMe, usePerformance } from '../api/queries';
import type { PerformanceResponse, SalesStatus, ShowtimeSummary } from '../api/types';
import { useLogin } from '../auth/useLogin';
import { Badge, type BadgeTone } from '../components/Badge';
import { BottomBar } from '../components/BottomBar';
import { Button } from '../components/Button';
import { Header } from '../components/Header';
import { KakaoLoginButton } from '../components/KakaoLoginButton';
import { LoadError, Loading } from '../components/LoadState';
import { Notice } from '../components/Notice';
import { Page } from '../components/Page';
import { useToast } from '../components/Toast';
import { copy } from '../copy';
import { formatCountdown, hasReached, msUntil } from '../lib/clock';
import { formatDateSpan, formatDateTime, formatShowtime, isLatinTitle } from '../lib/format';
import { effectiveSalesStatus } from '../lib/sales';
import { useServerNow } from '../lib/useServerNow';
import styles from './PerformancePage.module.css';

const badgeTone: Record<SalesStatus, BadgeTone> = {
    BEFORE_SALE: 'accent',
    ON_SALE: 'outline',
    SOLD_OUT: 'muted',
    SALES_CLOSED: 'muted',
};

function showtimeNote(showtime: ShowtimeSummary, status: SalesStatus) {
    const note = copy.performancePage.note;
    switch (status) {
        case 'BEFORE_SALE':
            return note.BEFORE_SALE(formatDateTime(showtime.salesOpensAt), showtime.maxSeatsPerBuyer);
        case 'ON_SALE':
            return note.ON_SALE(showtime.remaining, showtime.maxSeatsPerBuyer);
        case 'SOLD_OUT':
            return note.SOLD_OUT(showtime.maxSeatsPerBuyer);
        case 'SALES_CLOSED':
            return note.SALES_CLOSED;
    }
}

export function PerformancePage() {
    const { performanceId = '' } = useParams();
    const performanceQuery = usePerformance(performanceId);

    if (performanceQuery.isPending) {
        return (
            <Page header={<Header />}>
                <Loading />
            </Page>
        );
    }
    if (performanceQuery.isError) {
        return (
            <Page header={<Header />}>
                {isNotFound(performanceQuery.error) ? (
                    <Notice title={copy.performancePage.notFound} body={copy.performancePage.notFoundBody} />
                ) : (
                    <LoadError onRetry={() => performanceQuery.refetch()} />
                )}
            </Page>
        );
    }
    return <PerformanceView performance={performanceQuery.data} refetch={performanceQuery.refetch} />;
}

function PerformanceView({ performance, refetch }: { performance: PerformanceResponse; refetch: () => unknown }) {
    const text = copy.performancePage;
    const navigate = useNavigate();
    const login = useLogin();
    const toast = useToast();
    const meQuery = useMe();
    const loggedOut = meQuery.data === null;
    const now = useServerNow();
    const [pickedId, setPickedId] = useState<number | null>(null);

    const single = performance.showtimes.length === 1;
    const selected = performance.showtimes.find((s) => s.id === (single ? performance.showtimes[0]?.id : pickedId)) ?? null;
    const status = selected ? effectiveSalesStatus(selected, now) : null;
    const anyBeforeSale = performance.showtimes.some((s) => effectiveSalesStatus(s, now) === 'BEFORE_SALE');
    const openedSinceFetch = performance.showtimes.some((s) => s.salesStatus === 'BEFORE_SALE' && hasReached(s.salesOpensAt, now));
    const limits = new Set(performance.showtimes.map((s) => s.maxSeatsPerBuyer));
    const holds = new Set(performance.showtimes.map((s) => s.holdMinutes));

    useEffect(() => {
        if (openedSinceFetch) {
            refetch();
        }
    }, [openedSinceFetch, refetch]);

    const book = async () => {
        if (!selected) {
            return;
        }
        const quantityPath = `/performances/${performance.id}/showtimes/${selected.id}/quantity`;
        if (!loggedOut) {
            navigate(quantityPath);
            return;
        }
        try {
            await login(quantityPath);
        } catch {
            toast(copy.common.loginFailed);
        }
    };

    const loginHere = async () => {
        try {
            await login(`/performances/${performance.id}`);
        } catch {
            toast(copy.common.loginFailed);
        }
    };

    const cta = (() => {
        if (!selected || !status) {
            return { label: text.cta.pick, note: text.cta.pickNote, enabled: false };
        }
        switch (status) {
            case 'BEFORE_SALE':
                return {
                    label: (
                        <>
                            {text.cta.beforeSale} <span className={styles.countdown}>{formatCountdown(msUntil(selected.salesOpensAt, now))}</span>
                        </>
                    ),
                    note: text.cta.beforeSaleNote(formatDateTime(selected.salesOpensAt)),
                    enabled: false,
                };
            case 'ON_SALE':
                return { label: text.cta.book, note: loggedOut ? text.cta.bookNoteLoggedOut : text.cta.bookNote, enabled: true };
            case 'SOLD_OUT':
                return { label: text.cta.soldOut, note: text.cta.soldOutNote, enabled: false };
            case 'SALES_CLOSED':
                return { label: text.cta.closed, note: text.cta.closedNote, enabled: false };
        }
    })();

    return (
        <Page
            header={<Header />}
            bottomBar={
                <BottomBar note={cta.note}>
                    <Button onClick={book} disabled={!cta.enabled}>
                        {cta.label}
                    </Button>
                </BottomBar>
            }
        >
            <section className={styles.intro}>
                {performance.posterUrl ? (
                    <img className={styles.poster} src={performance.posterUrl} alt={text.posterAlt(performance.title)} />
                ) : (
                    <div className={styles.poster} aria-hidden="true" />
                )}
                <div className={styles.info}>
                    <h1 className={`${styles.title} ${isLatinTitle(performance.title) ? 't-display-title' : 't-title'}`}>{performance.title}</h1>
                    <p className="t-body">{performance.venue}</p>
                    {performance.showtimes.length > 0 && (
                        <p className="t-data">
                            {single && performance.showtimes[0]
                                ? formatShowtime(performance.showtimes[0].startsAt, performance.showtimes[0].endsAt, { year: true })
                                : formatDateSpan(performance.showtimes.map((s) => s.startsAt))}
                        </p>
                    )}
                    <p className="t-caption">
                        {[...new Set(performance.showtimes.map((s) => copy.seatKind[s.seatKind]))].join(' · ')}
                        {single ? '' : ` · ${text.showtimeCount(performance.showtimes.length)}`}
                    </p>
                </div>
            </section>

            <section className={styles.section}>
                <h2 className="t-heading">{text.showtimes}</h2>
                <ul className={styles.showtimes}>
                    {performance.showtimes.map((showtime) => {
                        const rowStatus = effectiveSalesStatus(showtime, now);
                        return (
                            <li key={showtime.id}>
                                <button
                                    type="button"
                                    className={styles.showtime}
                                    aria-pressed={selected?.id === showtime.id}
                                    onClick={() => setPickedId(showtime.id)}
                                >
                                    <span className={styles.showtimeHead}>
                                        <span className="t-data">{formatShowtime(showtime.startsAt, showtime.endsAt)}</span>
                                        <Badge tone={badgeTone[rowStatus]}>{copy.salesStatus[rowStatus]}</Badge>
                                    </span>
                                    <span className="t-caption">{showtimeNote(showtime, rowStatus)}</span>
                                </button>
                            </li>
                        );
                    })}
                </ul>
            </section>

            {meQuery.isError && (
                <Notice title={copy.common.meFailed} body={copy.common.meFailedBody}>
                    <Button variant="secondary" onClick={() => void meQuery.refetch()}>
                        {copy.common.retry}
                    </Button>
                </Notice>
            )}

            {loggedOut && (
                <section className={styles.loginCard}>
                    <h2 className="t-heading">{anyBeforeSale ? text.login.beforeSaleTitle : text.login.title}</h2>
                    <p className="t-body">{anyBeforeSale ? text.login.beforeSaleBody : text.login.body}</p>
                    <KakaoLoginButton onLogin={loginHere} />
                    <p className="t-caption">{copy.login.note}</p>
                </section>
            )}

            <section className={styles.section}>
                <h2 className="t-heading">{text.guide.title}</h2>
                <ul className={styles.guide}>
                    <li>{text.guide.entry}</li>
                    {holds.size === 1 && <li>{text.guide.hold([...holds][0] ?? 0)}</li>}
                    {holds.size > 1 && <li>{text.guide.holdPerShowtime}</li>}
                    <li>{limits.size === 1 ? text.guide.limit([...limits][0] ?? 0) : text.guide.limitPerShowtime}</li>
                </ul>
            </section>
        </Page>
    );
}
