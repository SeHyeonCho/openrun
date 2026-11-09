import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { copy } from '../copy';
import styles from './Toast.module.css';

const SHOW_MS = 4_000;

type ShowToast = (message: string) => void;

const ToastContext = createContext<ShowToast>(() => {});

export function useToast() {
    return useContext(ToastContext);
}

export function ToastProvider({ children }: { children: ReactNode }) {
    const [toast, setToast] = useState<{ id: number; message: string } | null>(null);
    const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

    const show = useCallback<ShowToast>((message) => {
        clearTimeout(timer.current);
        setToast({ id: Date.now(), message });
        timer.current = setTimeout(() => setToast(null), SHOW_MS);
    }, []);

    useEffect(() => () => clearTimeout(timer.current), []);

    return (
        <ToastContext.Provider value={show}>
            {children}
            <div className={styles.region} role="status" aria-live="polite">
                {toast && (
                    <div key={toast.id} className={styles.toast}>
                        <span className={styles.message}>{toast.message}</span>
                        <button type="button" className={styles.close} aria-label={copy.common.close} onClick={() => setToast(null)}>
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                                <path d="M6 6l12 12M18 6L6 18" />
                            </svg>
                        </button>
                    </div>
                )}
            </div>
        </ToastContext.Provider>
    );
}
