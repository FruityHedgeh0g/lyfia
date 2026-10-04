import React from "react";
import { Navigate } from "react-router-dom";
import { useAccess } from "./useAccess";
import { useAuth } from "./AuthContext";
import { AccessId, entry } from "./access";
import FeatureNotice from "../components/molecules/FeatureNotice/FeatureNotice";
import FeatureUnavailablePage from "../pages/FeatureUnavailablePage/FeatureUnavailablePage";

export interface RequireAccessProps {
  id: AccessId;
  children: React.ReactNode;
}

/**
 * Protège une route selon la carte d'accès : redirige vers l'accueil si le rôle ne suffit pas, et montre la page
 * « temporairement indisponible » si sa Fonctionnalité est désactivée. Le Super admin la voit quand même, marquée
 * comme désactivée (ADR 0009).
 */
export const RequireAccess: React.FC<RequireAccessProps> = ({ id, children }) => {
  const access = useAccess();
  const { isLoading } = useAuth();
  const feature = entry(id).feature;
  // Un lien direct vers une page protégée attend de savoir qui est connecté avant de rediriger
  if (isLoading) return null;
  if (!access.roleOpens(id)) return <Navigate to="/" replace />;
  if (feature && !access.ready) return null;
  if (feature && access.isTurnedOff(id)) {
    if (!access.seesTurnedOff) return <FeatureUnavailablePage />;
    return (
      <>
        <FeatureNotice feature={feature} variant="turned-off" />
        {children}
      </>
    );
  }
  return <>{children}</>;
};

export default RequireAccess;
