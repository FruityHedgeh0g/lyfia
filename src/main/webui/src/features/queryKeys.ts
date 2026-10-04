/**
 * Clés react-query de toutes les fonctionnalités. Chaque clé de détail est
 * préfixée par la clé de liste : invalider `x.all` rafraîchit aussi les détails.
 */
export const queryKeys = {
  events: { all: ["events"] as const, detail: (eventId: string | undefined) => ["events", "detail", eventId] as const },
  /** Inscriptions : les siennes et, pour le Bureau, la liste d'un Événement. */
  myRegistrations: {
    all: ["my-event-registrations"] as const,
    roster: (eventId: string) => ["my-event-registrations", "roster", eventId] as const,
    pilotes: (eventId: string | undefined) => ["my-event-registrations", "pilotes", eventId] as const,
  },
  groups: { all: ["groups"] as const },
  sectors: { all: ["sectors"] as const, detail: (sectorId: string | undefined) => ["sectors", "detail", sectorId] as const },
  members: {
    all: ["members"] as const,
    list: ["members", "list"] as const,
  },
  posts: {
    all: ["posts"] as const,
    /** Brouillons compris, pour le Bureau. */
    admin: ["posts", "admin"] as const,
    detail: (postId: string | undefined) => ["posts", "detail", postId] as const,
  },
  medias: { all: ["medias"] as const, gallery: ["medias", "gallery"] as const },
  carousel: { all: ["carousel-items"] as const, active: ["carousel-items", "active"] as const },
  configurations: { all: ["configurations"] as const },
  featureFlags: { all: ["feature-flags"] as const, journal: ["feature-flags", "journal"] as const },
  featureRequests: { all: ["feature-requests"] as const },
};
