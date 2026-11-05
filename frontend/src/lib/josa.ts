const VOWEL_ENDING_DIGITS = new Set([2, 4, 5, 9]);

function endsWithVowel(n: number) {
    const last = Math.abs(n) % 10;
    return last !== 0 && VOWEL_ENDING_DIGITS.has(last);
}

export function numbersWith(numbers: number[], pair: readonly [consonant: string, vowel: string]) {
    const last = numbers[numbers.length - 1] ?? 0;
    return `${numbers.join(' · ')}${endsWithVowel(last) ? pair[1] : pair[0]}`;
}
