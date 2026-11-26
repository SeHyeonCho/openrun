import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError, isApiError, isForbidden, isUnauthorized, isUnknownResult, RESEND_DELAYS_MS } from '../api/client';
import { api, invalidateOrderViews, newIdempotencyKey, queryKeys } from '../api/queries';
import type { AvailabilityResponse } from '../api/types';
import { useLogin } from '../auth/useLogin';
import { useToast } from '../components/Toast';
import { copy } from '../copy';
import { serverNow } from './clock';
import {
    findHoldRequest,
    hasOtherBuyerHoldRequest,
    isPastResendLimit,
    removeHoldRequest,
    saveHoldRequest,
    type StoredHoldRequest,
} from './holdRequestStore';

const NOT_OPEN_RETRY_MS = 1_500;
const NOT_OPEN_MAX_RETRIES = 5;

const CONCLUDED_STATUSES = new Set([400, 404, 409, 422]);

const isConcluded = (error: unknown) =>
    error instanceof ApiError && CONCLUDED_STATUSES.has(error.status) && error.code !== 'BUYER_CHANGED';

const newHoldRequest = (body: StoredHoldRequest['body'], buyerId: string): StoredHoldRequest => ({
    key: newIdempotencyKey(),
    body,
    buyerId,
    createdAt: new Date(serverNow()).toISOString(),
});

export type HoldPhase =
    | { kind: 'idle' }
    | { kind: 'holding' }
    | { kind: 'waitingOpen' }
    | { kind: 'resending' }
    | { kind: 'unknown' }
    | { kind: 'closed' }
    | { kind: 'buyerChanged' }
    | { kind: 'pastResendLimit' };

type Options = {
    performanceId: number;
    showtimeId: number;
    buyerId: string;
    refreshAvailability: () => Promise<AvailabilityResponse>;
};

export function useHoldSubmission({ performanceId, showtimeId, buyerId, refreshAvailability }: Options) {
    const text = copy.quantityPage;
    const navigate = useNavigate();
    const location = useLocation();
    const queryClient = useQueryClient();
    const login = useLogin();
    const toast = useToast();
    const [phase, setPhase] = useState<HoldPhase>({ kind: 'idle' });
    const retryTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
    const pending = useRef<StoredHoldRequest | null>(null);
    const [stored] = useState(() => findHoldRequest(buyerId, showtimeId));
    const [otherBuyerPending] = useState(() => stored === null && hasOtherBuyerHoldRequest(buyerId, showtimeId));
    const rechecked = useRef(false);

    const unmounted = useRef(false);

    useEffect(() => {
        unmounted.current = false;
        return () => {
            unmounted.current = true;
            clearTimeout(retryTimer.current);
        };
    }, []);

    useEffect(() => {
        if (stored === null || rechecked.current) {
            return;
        }
        rechecked.current = true;
        pending.current = stored;
        void send(0, 0, true);
    }, [stored]);

    const submit = (body: StoredHoldRequest['body']) => {
        pending.current = newHoldRequest(body, buyerId);
        saveHoldRequest(pending.current);
        void send(0, 0);
    };

    const retry = () => {
        void send(0, 0);
    };

    const send = async (notOpenAttempt: number, resendCount: number, recheck = false) => {
        const request = pending.current;
        if (!request) {
            return;
        }
        if (isPastResendLimit(request, serverNow())) {
            pending.current = null;
            removeHoldRequest(request);
            setPhase({ kind: 'pastResendLimit' });
            return;
        }
        setPhase({ kind: resendCount > 0 || recheck ? 'resending' : 'holding' });
        try {
            const order = await api.createOrder(request.body, request.key, request.buyerId);
            pending.current = null;
            queryClient.setQueryData(queryKeys.order(order.token), order);
            invalidateOrderViews(queryClient, { showtimeId, performanceId });
            if (!unmounted.current) {
                removeHoldRequest(request);
                navigate(`/orders/${order.token}`);
            }
        } catch (error) {
            if (unmounted.current) {
                return;
            }
            if (isConcluded(error)) {
                removeHoldRequest(request);
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
            if (isForbidden(error)) {
                if (resendCount >= RESEND_DELAYS_MS.length) {
                    setPhase({ kind: 'unknown' });
                    return;
                }
                setPhase({ kind: 'resending' });
                await api.getMe().catch(() => undefined);
                if (!unmounted.current) {
                    void send(notOpenAttempt, resendCount + 1);
                }
                return;
            }
            if (isApiError(error, 'BUYER_CHANGED')) {
                setPhase({ kind: 'buyerChanged' });
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
                        pending.current = newHoldRequest(request.body, request.buyerId);
                        saveHoldRequest(pending.current);
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
                void queryClient.invalidateQueries({ queryKey: queryKeys.performance(String(performanceId)) });
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

    return { phase, otherBuyerPending, submit, retry };
}
