import { useAuth } from "./AuthContext";
import { AccessContext, AccessId, AccessSection, canAccess, isTurnedOff, mainNav, navFor, roleOpens } from "./access";
import { useFeatures } from "../features/featureFlags/useFeatureFlags";

/** Carte d'accès appliquée à l'utilisateur courant (rôle + fonctionnalités actives). */
export function useAccess() {
  const { role } = useAuth();
  const features = useFeatures();
  const ctx: AccessContext = { role, isFeatureActive: features.isActive, seesTurnedOff: role === "super_admin" };
  return {
    /** false tant que l'état des fonctionnalités n'est pas chargé. */
    ready: features.ready,
    /** Le Super admin voit ce qui est désactivé (ADR 0009). */
    seesTurnedOff: Boolean(ctx.seesTurnedOff),
    canAccess: (id: AccessId) => canAccess(id, ctx),
    roleOpens: (id: AccessId) => roleOpens(id, ctx),
    isTurnedOff: (id: AccessId) => isTurnedOff(id, ctx),
    navFor: (section: AccessSection) => navFor(section, ctx),
    mainNav: () => mainNav(ctx),
  };
}
