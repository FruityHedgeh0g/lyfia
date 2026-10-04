import React, { createContext, useContext, useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ROLE_LABELS, RoleId, roleAtLeast } from "./roles";
import { CurrentUser, fetchMe, loginUrl, LOGOUT_URL, Profile, updateMe, UserSector } from "./session";
import { setReadOnly } from "../lib/http";

export type { CurrentUser, Profile, UserSector } from "./session";

interface AuthContextValue {
  user: CurrentUser | null;
  role: RoleId;
  isAuthenticated: boolean;
  /** true tant que l'on ne sait pas encore qui est connecté. */
  isLoading: boolean;
  hasAtLeastRole: (required: RoleId) => boolean;
  updateProfile: (profile: Profile) => Promise<void>;
  /** Part vers Keycloak, puis revient sur `redirect` (la page courante par défaut). */
  login: (redirect?: string) => void;
  /** Part vers le formulaire d'inscription de Keycloak. */
  register: () => void;
  logout: () => void;
  /** L'Aperçu en cours (le site vu comme un Rôle le verrait), ou null. */
  preview: Preview | null;
  /** Seul le Super admin ouvre un Aperçu (ADR 0009). */
  canPreview: boolean;
  setPreview: (preview: Preview | null) => void;
}

/**
 * Un Aperçu : le site, en lecture seule, tel qu'un Rôle le verrait (avec un Secteur pour les Rôles qui en ont un).
 * Rien n'y est faisable et il ne montre l'espace de personne : la personne connectée reste le Super admin pour l'API.
 */
export interface Preview {
  role: RoleId;
  sector: UserSector | null;
}

const ME_KEY = ["me"] as const;

const AuthContext = createContext<AuthContextValue | undefined>(undefined);
const BaseAuthContext = createContext<BaseAuthValue | undefined>(undefined);

const currentPath = () => `${window.location.pathname}${window.location.search}${window.location.hash}`;

function navigateTo(url: string) {
  window.location.assign(url);
}

type BaseAuthValue = Omit<AuthContextValue, "preview" | "canPreview" | "setPreview">;

function contextValue(
  user: CurrentUser | null,
  isLoading: boolean,
  updateProfile: AuthContextValue["updateProfile"]
): BaseAuthValue {
  const role: RoleId = user?.role ?? "visiteur";
  return {
    user,
    role,
    isAuthenticated: user !== null,
    isLoading,
    hasAtLeastRole: (required) => roleAtLeast(role, required),
    updateProfile,
    login: (redirect = currentPath()) => navigateTo(loginUrl(redirect)),
    register: () => navigateTo(loginUrl("/", true)),
    logout: () => navigateTo(LOGOUT_URL),
  };
}

/** La personne connectée, lue sur GET /api/users/me ; un Visiteur n'a pas de session. */
const SessionAuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ME_KEY, queryFn: fetchMe, staleTime: Infinity, retry: false });

  const value = useMemo(
    () =>
      contextValue(me.data ?? null, me.isPending, async (profile) => {
        queryClient.setQueryData(ME_KEY, await updateMe(profile));
      }),
    [me.data, me.isPending, queryClient]
  );
  return <BaseAuthContext.Provider value={value}>{children}</BaseAuthContext.Provider>;
};

/** Une personne donnée d'avance, sans attendre /api/users/me : pour les tests (test/testUser). */
const FixedAuthProvider: React.FC<{ children: React.ReactNode; user: CurrentUser | null }> = ({ children, user }) => {
  const [current, setCurrent] = useState(user);
  const value = useMemo(
    () =>
      contextValue(current, false, async (profile) => {
        await updateMe(profile);
        setCurrent((prev) => (prev ? { ...prev, ...profile } : prev));
      }),
    [current]
  );
  return <BaseAuthContext.Provider value={value}>{children}</BaseAuthContext.Provider>;
};

/**
 * Applique l'Aperçu du Super admin : le Rôle, le Secteur et la personne vus par le site deviennent ceux de l'Aperçu,
 * une personne fictive sans données ; toute écriture vers l'API est refusée tant qu'il dure (lib/http).
 */
const PreviewLayer: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const real = useContext(BaseAuthContext)!;
  const [preview, setPreview] = useState<Preview | null>(null);
  const canPreview = real.role === "super_admin";
  const active = canPreview ? preview : null;

  useEffect(() => {
    setReadOnly(active !== null);
    return () => setReadOnly(false);
  }, [active]);

  const value = useMemo<AuthContextValue>(() => {
    const controls = { preview: active, canPreview, setPreview: canPreview ? setPreview : () => undefined };
    if (!active) return { ...real, ...controls };
    const user: CurrentUser | null =
      active.role === "visiteur"
        ? null
        : { userId: "apercu", firstName: "Aperçu", lastName: ROLE_LABELS[active.role], phone: "", role: active.role, sector: active.sector };
    return {
      ...real,
      ...controls,
      user,
      role: active.role,
      isAuthenticated: user !== null,
      hasAtLeastRole: (required) => roleAtLeast(active.role, required),
      updateProfile: async () => {
        throw new Error("Rien ne peut être fait depuis un Aperçu.");
      },
    };
  }, [real, active, canPreview]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const AuthProvider: React.FC<{ children: React.ReactNode; user?: CurrentUser | null }> = ({ children, user }) => {
  const layered = <PreviewLayer>{children}</PreviewLayer>;
  return user === undefined ? <SessionAuthProvider>{layered}</SessionAuthProvider> : <FixedAuthProvider user={user}>{layered}</FixedAuthProvider>;
};

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth doit être utilisé à l'intérieur d'un AuthProvider");
  return ctx;
}
