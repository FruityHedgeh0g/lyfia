import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // En dev, Quarkus (quarkus:dev, Quinoa désactivé) sert l'API à part : le serveur Vite la lui relaie
    proxy: {
      "/api": "http://localhost:8080",
    },
  },
});
