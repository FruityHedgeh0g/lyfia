import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // quarkus:dev sert le site sur :8080 (Quinoa) ; ouvert directement sur :5173, le serveur Vite relaie l'API à Quarkus
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
