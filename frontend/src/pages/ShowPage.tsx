import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { isNotFound } from '../api/client';
import { useMe, useShow } from '../api/queries';
import type { SalesStatus, ShowResponse, ShowtimeSummary } from '../api/types';
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
import styles from './ShowPage.module.css';

const badgeTone: Record<SalesStatus, BadgeTone> = {
    BEFORE_SALE: 'accent',
    ON_SALE: 'outline',
    SOLD_OUT: 'muted',
    SALES_CLOSED: 'muted',
};

function showtimeNote(showtime: ShowtimeSummary, status: SalesStatus) {
    const note = copy.showPage.note;
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

export function ShowPage() {
    const { showId = '' } = useParams();
    const showQuery = useShow(showId);

    if (showQuery.isPending) {
        return (
            <Page header={<Header />}>
                <Loading />
            </Page>
        );
    }
    if (showQuery.isError) {
        return (
            <Page header={<Header />}>
                {isNotFound(showQuery.error) ? (
                    <Notice title={copy.showPage.notFound} body={copy.showPage.notFoundBody} />
                ) : (
                    <LoadError onRetry={() => showQuery.refetch()} />
                )}
            </Page>
        );
    }
    return <ShowView show={showQuery.data} refetch={showQuery.refetch} />;
}

function ShowView({ show, refetch }: { show: ShowResponse; refetch: () => unknown }) {
    const text = copy.showPage;
    const navigate = useNavigate();
    const login = useLogin();
    const toast = useToast();
    const meQuery = useMe();
    const loggedOut = meQuery.data === null;
    const now = useServerNow();
    const [pickedId, setPickedId] = useState<number | null>(null);

    const single = show.showtimes.length === 1;
    const selected = show.showtimes.find((s) => s.id === (single ? show.showtimes[0]?.id : pickedId)) ?? null;
    const status = selected ? effectiveSalesStatus(selected, now) : null;
    const anyBeforeSale = show.showtimes.some((s) => effectiveSalesStatus(s, now) === 'BEFORE_SALE');
    const openedSinceFetch = show.showtimes.some((s) => s.salesStatus === 'BEFORE_SALE' && hasReached(s.salesOpensAt, now));
    const limits = new Set(show.showtimes.map((s) => s.maxSeatsPerBuyer));

    useEffect(() => {
        if (openedSinceFetch) {
            refetch();
        }
    }, [openedSinceFetch, refetch]);

    const book = async () => {
        if (!selected) {
            return;
        }
        const quantityPath = `/shows/${show.id}/showtimes/${selected.id}/quantity`;
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
            await login(`/shows/${show.id}`);
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
                {show.posterUrl ? (
                    <img className={styles.poster} src={show.posterUrl} alt={text.posterAlt(show.title)} />
                ) : (
                    <div className={styles.poster} aria-hidden="true" />
                )}
                <div className={styles.info}>
                    <h1 className={`${styles.title} ${isLatinTitle(show.title) ? 't-display-title' : 't-title'}`}>{show.title}</h1>
                    <p className="t-body">{show.venue}</p>
                    {show.showtimes.length > 0 && (
                        <p className="t-data">
                            {single && show.showtimes[0]
                                ? formatShowtime(show.showtimes[0].startsAt, show.showtimes[0].endsAt, { year: true })
                                : formatDateSpan(show.showtimes.map((s) => s.startsAt))}
                        </p>
                    )}
                    <p className="t-caption">
                        {[...new Set(show.showtimes.map((s) => copy.seatKind[s.seatKind]))].join(' · ')}
                        {single ? '' : ` · ${text.showtimeCount(show.showtimes.length)}`}
                    </p>
                </div>
            </section>

            <section className={styles.section}>
                <h2 className="t-heading">{text.showtimes}</h2>
                <ul className={styles.showtimes}>
                    {show.showtimes.map((showtime) => {
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
                    <li>{text.guide.hold(show.holdMinutes)}</li>
                    <li>{limits.size === 1 ? text.guide.limit([...limits][0] ?? 0) : text.guide.limitPerShowtime}</li>
                </ul>
            </section>
        </Page>
    );
}
