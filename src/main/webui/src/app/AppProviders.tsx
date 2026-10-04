import React from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { queryClient } from "./queryClient";
import { ThemeProvider } from "../theme/ThemeContext";
import { AuthProvider } from "../auth/AuthContext";
import { SecteurChoiceProvider } from "../features/sectors/secteurChoice";

export const AppProviders: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <QueryClientProvider client={queryClient}>
    <ThemeProvider>
      <AuthProvider>
        <SecteurChoiceProvider>{children}</SecteurChoiceProvider>
        {import.meta.env.DEV && <ReactQueryDevtools initialIsOpen={false} />}
      </AuthProvider>
    </ThemeProvider>
  </QueryClientProvider>
);

export default AppProviders;
