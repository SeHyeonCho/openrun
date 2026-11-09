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
    const dialog = useRef<HTMLDialogElement>(null);

    useEffect(() => {
        const sheet = dialog.current;
        if (!sheet) {
            return undefined;
        }
        const previous = document.activeElement as HTMLElement | null;
        if (typeof sheet.showModal === 'function') {
            sheet.showModal();
        } else {
            sheet.setAttribute('open', '');
        }
        sheet.querySelector<HTMLElement>('button, a[href]')?.focus();
        const onCancel = (event: Event) => {
            event.preventDefault();
            onClose();
        };
        sheet.addEventListener('cancel', onCancel);
        return () => {
            sheet.removeEventListener('cancel', onCancel);
            if (sheet.open && typeof sheet.close === 'function') {
                sheet.close();
            }
            previous?.focus();
        };
    }, [onClose]);

    return (
        <dialog
            ref={dialog}
            className={styles.sheet}
            aria-labelledby={titleId}
            onClick={(event) => {
                if (event.target === event.currentTarget) {
                    onClose();
                }
            }}
        >
            <div className={styles.panel}>
                <div className={styles.handle} aria-hidden="true" />
                <div className={styles.content}>
                    <h2 id={titleId} className="t-heading">
                        {title}
                    </h2>
                    {children}
                </div>
                <div className={styles.actions}>{actions}</div>
            </div>
        </dialog>
    );
}
