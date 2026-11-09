import { syncServerTime } from '../lib/clock';
import type { ProblemCode, ProblemDetail } from './types';

const TIMEOUT_MS = 10_000;

export class ApiError extends Error {
    readonly status: number;
    readonly code: ProblemCode | undefined;
    readonly problem: ProblemDetail;

    constructor(problem: ProblemDetail) {
        super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`);
        this.name = 'ApiError';
        this.status = problem.status;
        this.code = problem.code;
        this.problem = problem;
    }
}

export class NoResponseError extends Error {
    constructor(cause: unknown) {
        super('서버 응답을 받지 못했다', { cause });
        this.name = 'NoResponseError';
    }
}

export const isApiError = (error: unknown, code?: ProblemCode): error is ApiError =>
    error instanceof ApiError && (code === undefined || error.code === code);

export const isUnauthorized = (error: unknown) => error instanceof ApiError && error.status === 401;

export const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404;

function abortedBy(signal: AbortSignal): Promise<never> {
    const aborted = new Promise<never>((_, reject) => {
        signal.addEventListener('abort', () => reject(signal.reason), { once: true });
    });
    aborted.catch(() => undefined);
    return aborted;
}

async function readProblem(response: Response, aborted: Promise<never>): Promise<ProblemDetail> {
    try {
        const body = (await Promise.race([response.json(), aborted])) as Partial<ProblemDetail>;
        return { ...body, status: response.status };
    } catch {
        return { status: response.status };
    }
}

type RequestOptions = { method?: string; body?: unknown; headers?: Record<string, string> };

export async function request<T>(path: string, init: RequestOptions = {}): Promise<T> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    const aborted = abortedBy(controller.signal);
    const sentAt = Date.now();
    try {
        let response: Response;
        try {
            response = await fetch(new URL(`/api${path}`, window.location.href), {
                method: init.method ?? 'GET',
                credentials: 'include',
                headers: {
                    Accept: 'application/json',
                    ...(init.body === undefined ? {} : { 'Content-Type': 'application/json' }),
                    ...init.headers,
                },
                body: init.body === undefined ? undefined : JSON.stringify(init.body),
                signal: controller.signal,
            });
        } catch (error) {
            throw new NoResponseError(error);
        }

        if (!response.ok) {
            throw new ApiError(await readProblem(response, aborted));
        }
        if (response.status === 204) {
            return undefined as T;
        }
        let body: T;
        try {
            body = (await Promise.race([response.json(), aborted])) as T;
        } catch (error) {
            throw new NoResponseError(error);
        }
        const serverTime = (body as { serverTime?: unknown } | null)?.serverTime;
        if (typeof serverTime === 'string') {
            syncServerTime(serverTime, sentAt, Date.now());
        }
        return body;
    } finally {
        clearTimeout(timer);
    }
}
