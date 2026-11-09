import { describe, expect, it } from 'vitest';
import { numbersWith } from './josa';

describe('입장번호 뒤 조사', () => {
    it.each([
        [[37, 38], '37 · 38이'],
        [[12], '12가'],
        [[40], '40이'],
        [[9], '9가'],
        [[5], '5가'],
        [[21, 26], '21 · 26이'],
    ])('%j 뒤에는 %s', (numbers, expected) => {
        expect(numbersWith(numbers, ['이', '가'])).toBe(expected);
    });
});
