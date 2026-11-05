import styles from './Stepper.module.css';

type StepperProps = {
    value: number;
    min: number;
    max: number;
    unit: string;
    decreaseLabel: string;
    increaseLabel: string;
    disabled?: boolean;
    onChange: (value: number) => void;
};

export function Stepper({ value, min, max, unit, decreaseLabel, increaseLabel, disabled = false, onChange }: StepperProps) {
    return (
        <div className={styles.stepper}>
            <button type="button" className={styles.button} aria-label={decreaseLabel} disabled={disabled || value <= min} onClick={() => onChange(value - 1)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                    <path d="M5 12h14" />
                </svg>
            </button>
            <output className={styles.value} aria-live="polite">
                {value}
                <span className={styles.unit}>{unit}</span>
            </output>
            <button type="button" className={styles.button} aria-label={increaseLabel} disabled={disabled || value >= max} onClick={() => onChange(value + 1)}>
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
                    <path d="M12 5v14M5 12h14" />
                </svg>
            </button>
        </div>
    );
}
