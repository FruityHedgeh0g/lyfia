import { useEffect, useRef, type RefObject } from "react";

/**
 * Comportement commun des dialogues modaux : focus porté sur le dialogue à l'ouverture, Échap pour fermer,
 * défilement de la page bloqué, focus rendu à l'élément d'origine à la fermeture. Le dernier `onClose` est gardé à
 * part : un nouveau à chaque rendu ne reprend pas le focus à un champ en cours de saisie.
 */
export function useDialog(isOpen: boolean, onClose: () => void, dialogRef: RefObject<HTMLElement | null>) {
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!isOpen) return;

    const previouslyFocused = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();
    document.body.style.overflow = "hidden";

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onCloseRef.current();
    };
    document.addEventListener("keydown", handleKeyDown);

    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = "";
      previouslyFocused?.focus();
    };
  }, [isOpen, dialogRef]);
}
