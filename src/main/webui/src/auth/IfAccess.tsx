import React from "react";
import { useAccess } from "./useAccess";
import { AccessId } from "./access";

/** Ne montre ses enfants (un lien, un bouton) que si l'entrée de la carte d'accès est accessible. */
export const IfAccess: React.FC<{ id: AccessId; children: React.ReactNode }> = ({ id, children }) =>
  useAccess().canAccess(id) ? <>{children}</> : null;

export default IfAccess;
