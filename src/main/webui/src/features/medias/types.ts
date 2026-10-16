/** Reflète MediaDto côté backend, avec l'`url` de son fichier (GET /api/medias/{mediaId}/content). */
export interface Media {
  mediaId: string;
  fileKey: string;
  originalFilename: string;
  /** Type du fichier (image/jpeg...). */
  contentType: string;
  fileSize: number;
  url: string;
  /** Ce que montre l'image ; à défaut, le nom du fichier. */
  alt: string;
  /** Où le site l'utilise ; on ne la supprime qu'une fois inutilisée. */
  usedBy?: string[];
}
