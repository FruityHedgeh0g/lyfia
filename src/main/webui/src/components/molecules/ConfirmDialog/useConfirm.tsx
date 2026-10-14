import React, { useState } from "react";
import ConfirmDialog from "./ConfirmDialog";

export interface ConfirmRequest {
  title: string;
  message: string;
  /** Le verbe de l'action (« Me désinscrire », « Retirer »…). */
  confirmLabel: string;
  /** « Annuler » par défaut ; à changer quand l'action elle-même s'appelle annuler. */
  cancelLabel?: string;
}

/**
 * Confirmation avant une action qui supprime, retire ou annule : `confirm` ouvre la boîte et se résout à true si
 * la personne confirme, false si elle renonce. La page affiche `dialog` une fois, n'importe où.
 */
export function useConfirm() {
  const [request, setRequest] = useState<(ConfirmRequest & { resolve: (confirmed: boolean) => void }) | null>(null);

  const confirm = (next: ConfirmRequest) => new Promise<boolean>((resolve) => setRequest({ ...next, resolve }));
  const answer = (confirmed: boolean) => {
    request?.resolve(confirmed);
    setRequest(null);
  };

  const dialog = (
    <ConfirmDialog
      isOpen={request !== null}
      title={request?.title ?? ""}
      message={request?.message ?? ""}
      confirmLabel={request?.confirmLabel}
      cancelLabel={request?.cancelLabel}
      onConfirm={() => answer(true)}
      onCancel={() => answer(false)}
    />
  );

  return { confirm, dialog };
}
