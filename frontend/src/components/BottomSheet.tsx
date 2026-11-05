import { useEffect, useId, useRef, type ReactNode } from 'react';
import styles from './BottomSheet.module.css';

type BottomSheetProps = {
    title: string;
    onClose: () => void;
    children: ReactNode;
    actions: ReactNode;
};

export function BottomSheet({ title, onClose, children, actions }: BottomSheetProps) {
    const titleId = useId();
    const sheet = useRef<HTMLElement>(null);

    useEffect(() => {
        const previous = document.activeElement as HTMLElement | null;
        sheet.current?.querySelector<HTMLElement>('button, a[href]')?.focus();
        const onKey = (event: KeyboardEvent) => {
            if (event.key === 'Escape') {
                onClose();
            }
        };
        document.addEventListener('keydown', onKey);
        return () => {
            document.removeEventListener('keydown', onKey);
            previous?.focus();
        };
    }, [onClose]);

    return (
        <>
            <div className={styles.scrim} aria-hidden="true" onClick={onClose} />
            <section ref={sheet} className={styles.sheet} role="dialog" aria-modal="true" aria-labelledby={titleId}>
                <div className={styles.handle} aria-hidden="true" />
                <div className={styles.content}>
                    <h2 id={titleId} className="t-heading">
                        {title}
                    </h2>
                    {children}
                </div>
                <div className={styles.actions}>{actions}</div>
            </section>
        </>
    );
}
