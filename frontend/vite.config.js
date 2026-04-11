import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { resolve } from 'path';
import { fileURLToPath } from 'url';

const root = fileURLToPath(new URL('.', import.meta.url));

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, root, '');
    const apiProxyTarget = env.VITE_DEV_API_PROXY_TARGET?.trim();

    return {
        plugins: [react()],
        server: {
            host: '0.0.0.0',
            port: 5173,
            allowedHosts: [
                'tamara-experimentative-obliviously.ngrok-free.dev'
            ],
            proxy: apiProxyTarget
                ? {
                    '/api': {
                        target: apiProxyTarget,
                        changeOrigin: true,
                        secure: false
                    }
                }
                : undefined
        },
        build: {
            rollupOptions: {
                input: {
                    main: resolve(root, 'index.html'),
                    admin: resolve(root, 'admin.html')
                }
            }
        }
    };
});
