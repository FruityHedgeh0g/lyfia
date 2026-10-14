import { apiFetch } from "../../lib/http";

/** Reflète DechargeDocumentDto : une version de la décharge de responsabilité des mineurs, sans son contenu. */
export interface DechargeDocument {
  documentId: string;
  originalFilename: string;
  fileSize: number;
  uploadedAt: string;
}

/** La version en vigueur (GET /api/decharge), ou null tant qu'aucune n'est déposée (204). */
export const fetchCurrentDecharge = async (): Promise<DechargeDocument | null> =>
  (await apiFetch<DechargeDocument | undefined>("/api/decharge")) ?? null;

/** Le PDF d'une version, la courante ou une déjà signée ; la session suffit à s'authentifier. */
export const dechargeUrl = (documentId: string) => `/api/decharge/${encodeURIComponent(documentId)}/content`;

/** Le Super admin dépose une nouvelle version, en vigueur dès maintenant ; les précédentes sont gardées. */
export function uploadDecharge(file: File): Promise<DechargeDocument> {
  const body = new FormData();
  body.append("file", file);
  return apiFetch<DechargeDocument>("/api/decharge", { method: "POST", body });
}
