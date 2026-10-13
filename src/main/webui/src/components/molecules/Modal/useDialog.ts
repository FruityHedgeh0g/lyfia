import { useEffect, type RefObject } from "react";

/**
 * Comportement commun des dialogues modaux : focus porté sur le dialogue à l'ouverture, Échap pour fermer,
 * défilement de la page bloqué, focus rendu à l'élément d'origine à la fermeture.
 */
export function useDialog(isOpen: boolean, onClose: () => void, dialogRef: RefObject<HTMLElement | null>) {
  useEffect(() => {
    if (!isOpen) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();
    document.body.style.overflow = "hidden";

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "";
      previouslyFocused?.focus();
    };
  }, [isOpen, onClose, dialogRef]);
}
