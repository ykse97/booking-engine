import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { fileURLToPath } from 'url';

const root = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig({
    plugins: [react()],
    server: {
        host: '0.0.0.0',
        port: 5173,
        allowedHosts: [
            'tamara-experimentative-obliviously.ngrok-free.dev'
        ],
        proxy: {
            '/api': 'http://localhost:8080'
        }
    },
    build: {
        rollupOptions: {
            input: {
                main: resolve(root, 'index.html'),
                admin: resolve(root, 'admin.html')
            }
        }
    }
});