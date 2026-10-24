import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    rollupOptions: {
      output: {
        // firebase/react/react-router cambian de versión rara vez — separarlos del
        // código de la app para que el navegador los cachee entre deploys en vez de
        // volver a descargar todo cada vez que cambia una línea de una pantalla.
        manualChunks(id) {
          if (!id.includes("node_modules")) return undefined;
          if (id.includes("firebase") || id.includes("@firebase")) return "vendor-firebase";
          if (id.includes("react")) return "vendor-react";
          return "vendor";
        },
      },
    },
  },
})
