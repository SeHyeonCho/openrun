import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router';
import styles from './Button.module.css';

type Variant = 'primary' | 'secondary';

const classOf = (variant: Variant, extra?: string) => [styles.button, styles[variant], extra].filter(Boolean).join(' ');

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant };

export function Button({ variant = 'primary', className, type = 'button', ...rest }: ButtonProps) {
    return <button type={type} className={classOf(variant, className)} {...rest} />;
}

type ButtonLinkProps = { to: string; variant?: Variant; children: ReactNode };

export function ButtonLink({ to, variant = 'primary', children }: ButtonLinkProps) {
    return (
        <Link to={to} className={classOf(variant)}>
            {children}
        </Link>
    );
}
