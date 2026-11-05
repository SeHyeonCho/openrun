import type { ReactNode } from 'react';
import styles from './Notice.module.css';

type NoticeProps = {
    title: string;
    body?: ReactNode;
    caption?: ReactNode;
    icon?: ReactNode;
    variant?: 'muted' | 'outlined';
    centered?: boolean;
    live?: boolean;
    children?: ReactNode;
};

export function Notice({ title, body, caption, icon, variant = 'muted', centered = false, live = false, children }: NoticeProps) {
    const className = [styles.notice, variant === 'outlined' && styles.outlined, centered && styles.centered]
        .filter(Boolean)
        .join(' ');
    return (
        <section className={className} role={live ? 'status' : undefined}>
            {icon}
            <h2 className="t-heading">{title}</h2>
            {body && <p className="t-body">{body}</p>}
            {caption && <p className="t-caption">{caption}</p>}
            {children}
        </section>
    );
}
