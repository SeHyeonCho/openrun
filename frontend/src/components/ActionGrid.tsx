import type { CSSProperties, ReactNode } from 'react';
import styles from './ActionGrid.module.css';

type Action = { label: string; icon: ReactNode; onClick: () => void; disabled?: boolean };

export function ActionGrid({ actions }: { actions: Action[] }) {
    return (
        <div className={styles.grid} style={{ '--columns': actions.length } as CSSProperties}>
            {actions.map((action) => (
                <button key={action.label} type="button" className={styles.action} onClick={action.onClick} disabled={action.disabled}>
                    {action.icon}
                    {action.label}
                </button>
            ))}
        </div>
    );
}

const iconProps = {
    width: 20,
    height: 20,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    'aria-hidden': true,
} as const;

export const icons = {
    download: (
        <svg {...iconProps}>
            <path d="M12 3v12M7 10l5 5 5-5M5 21h14" />
        </svg>
    ),
    calendar: (
        <svg {...iconProps}>
            <rect x="3" y="5" width="18" height="16" rx="2" />
            <path d="M3 10h18M8 3v4M16 3v4" />
        </svg>
    ),
    share: (
        <svg {...iconProps}>
            <path d="M12 15V3M7 8l5-5 5 5M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6" />
        </svg>
    ),
};
