import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            '/api': 'http://localhost:8080',
            '/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
            '/login/oauth2': { target: 'http://localhost:8080', changeOrigin: false },
        },
    },
    test: {
        environment: 'jsdom',
        setupFiles: ['./src/test/setup.ts'],
        restoreMocks: true,
    },
});
