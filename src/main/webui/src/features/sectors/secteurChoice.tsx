import React, { createContext, useContext, useMemo, useState } from "react";

const STORAGE_KEY = "secteur-choisi";

interface SecteurChoice {
  /** Le Secteur choisi sur le site public ; "" : tous les secteurs. */
  sectorId: string;
  setSectorId: (sectorId: string) => void;
}

const ALL: SecteurChoice = { sectorId: "", setSectorId: () => undefined };

const SecteurChoiceContext = createContext<SecteurChoice>(ALL);

const readStored = () => {
  try {
    return localStorage.getItem(STORAGE_KEY) ?? "";
  } catch {
    return "";
  }
};

/** Le choix de Secteur du site public, « Tous les secteurs » par défaut et gardé sur l'appareil. */
export const SecteurChoiceProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [sectorId, setStored] = useState(readStored);
  const value = useMemo<SecteurChoice>(
    () => ({
      sectorId,
      setSectorId: (next) => {
        setStored(next);
        try {
          if (next) localStorage.setItem(STORAGE_KEY, next);
          else localStorage.removeItem(STORAGE_KEY);
        } catch {
          // Sans stockage (navigation privée), le choix vaut pour la visite seulement
        }
      },
    }),
    [sectorId]
  );
  return <SecteurChoiceContext.Provider value={value}>{children}</SecteurChoiceContext.Provider>;
};

/** Hors de SecteurChoiceProvider (tests de composants), tous les secteurs. */
export function useSecteurChoice(): SecteurChoice {
  return useContext(SecteurChoiceContext);
}

/** Ce qui appartient à ce Secteur, ou à tout le site, entre dans le choix ; tout y entre sans choix. */
export function inSecteurChoice(choice: string, sectorId: string | null | undefined): boolean {
  return !choice || !sectorId || sectorId === choice;
}
