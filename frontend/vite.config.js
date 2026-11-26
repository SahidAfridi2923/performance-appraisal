import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the Spring Boot API runs on :8080; /api calls are proxied to it.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': process.env.VITE_API_PROXY || 'http://localhost:8080' },
  },
});
