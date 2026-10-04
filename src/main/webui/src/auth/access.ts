import { RoleId, roleAtLeast } from "./roles";
import { FeatureName } from "../features/featureFlags/types";

/**
 * Carte d'accès : chaque espace navigable est déclaré une seule fois, avec le
 * rôle minimal et la fonctionnalité éventuelle qui le conditionnent. Le
 * routeur (garde des routes), le Header et les layouts Mon espace /
 * Administration lisent tous cette carte, ce qui garantit qu'un lien affiché
 * mène toujours à une page accessible, et inversement.
 */
export type AccessSection = "main" | "account" | "admin";

export interface AccessEntry {
  /** Chemin absolu (ou ancre "/#section") sans le basename. */
  path: string;
  label: string;
  minRole: RoleId;
  section: AccessSection;
  /** Menu déroulant du Header qui regroupe l'entrée (section "main"). */
  menu?: string;
  /** L'entrée n'est accessible que si cette fonctionnalité est active. */
  feature?: FeatureName;
  /** Lien actif uniquement sur le chemin exact (NavLink `end`). */
  end?: boolean;
}

export const ACCESS = {
  about: { path: "/qui-sommes-nous", label: "Qui sommes-nous ?", minRole: "visiteur", section: "main", menu: "Association" },
  news: { path: "/actualites", label: "Actualités", minRole: "visiteur", section: "main", menu: "Association" },
  gallery: {
    path: "/galerie",
    label: "Galerie photos",
    minRole: "visiteur",
    section: "main",
    menu: "Association",
    feature: "galerie-photos",
  },
  contact: { path: "/contact", label: "Contact", minRole: "visiteur", section: "main", menu: "Association" },
  events: { path: "/evenements", label: "Événements", minRole: "visiteur", section: "main" },
  donation: { path: "/don", label: "Faire un don", minRole: "visiteur", section: "main", menu: "Soutenir" },
  volunteer: { path: "/#benevolat", label: "Devenir bénévole", minRole: "visiteur", section: "main", menu: "Soutenir" },
  account: { path: "/mon-compte", label: "Mon espace", minRole: "benevole", section: "main" },
  administration: { path: "/administration", label: "Administration", minRole: "bureau", section: "main" },
  featureRequests: {
    path: "/demandes-fonctionnalites",
    label: "Demande de fonctionnalités",
    minRole: "bureau",
    section: "main",
    menu: "Support",
  },

  accountProfile: { path: "/mon-compte", label: "Mon profil", minRole: "benevole", section: "account", end: true },
  accountEvents: { path: "/mon-compte/evenements", label: "Mes événements", minRole: "benevole", section: "account" },
  accountGroup: { path: "/mon-compte/groupe", label: "Mon groupe", minRole: "chef_de_groupe", section: "account" },
  accountSector: { path: "/mon-compte/secteur", label: "Mon secteur", minRole: "bureau", section: "account" },

  adminMembers: { path: "/administration/membres", label: "Inscrits", minRole: "bureau", section: "admin" },
  adminSectors: { path: "/administration/secteurs", label: "Secteurs", minRole: "super_admin", section: "admin" },
  adminGroups: { path: "/administration/groupes", label: "Groupes", minRole: "bureau", section: "admin" },
  adminEvents: { path: "/administration/evenements", label: "Gestion événements", minRole: "bureau", section: "admin" },
  adminPosts: { path: "/administration/actualites", label: "Actualités", minRole: "bureau", section: "admin" },
  adminCarousel: { path: "/administration/carrousel", label: "Carrousel", minRole: "bureau", section: "admin" },
  adminMedias: { path: "/administration/medias", label: "Médiathèque", minRole: "bureau", section: "admin" },
  adminConfiguration: { path: "/administration/configuration", label: "Configuration", minRole: "super_admin", section: "admin" },
  adminFeatureFlags: { path: "/administration/fonctionnalites", label: "Fonctionnalités", minRole: "super_admin", section: "admin" },
} satisfies Record<string, AccessEntry>;

export type AccessId = keyof typeof ACCESS;

export interface AccessContext {
  role: RoleId;
  isFeatureActive: (feature: FeatureName) => boolean;
  /** Le Super admin voit aussi ce qui est désactivé, marqué comme tel, pour le vérifier avant de le réactiver (ADR 0009). */
  seesTurnedOff?: boolean;
}

export function entry(id: AccessId): AccessEntry {
  return ACCESS[id];
}

/** Le rôle suffit pour l'entrée, que sa Fonctionnalité soit active ou non. */
export function roleOpens(id: AccessId, ctx: AccessContext): boolean {
  return roleAtLeast(ctx.role, entry(id).minRole);
}

/** La Fonctionnalité dont dépend l'entrée est désactivée. */
export function isTurnedOff(id: AccessId, ctx: AccessContext): boolean {
  const feature = entry(id).feature;
  return feature !== undefined && !ctx.isFeatureActive(feature);
}

export function canAccess(id: AccessId, ctx: AccessContext): boolean {
  return roleOpens(id, ctx) && (!isTurnedOff(id, ctx) || Boolean(ctx.seesTurnedOff));
}

/** Entrées visibles d'une section, dans l'ordre de déclaration. */
export function navFor(section: AccessSection, ctx: AccessContext): (AccessEntry & { id: AccessId })[] {
  return (Object.keys(ACCESS) as AccessId[])
    .filter((id) => entry(id).section === section && canAccess(id, ctx))
    .map((id) => ({ id, ...entry(id) }));
}

export type NavGroup =
  | { kind: "link"; entry: AccessEntry & { id: AccessId } }
  | { kind: "menu"; label: string; entries: (AccessEntry & { id: AccessId })[] };

/** Regroupe les entrées de la barre principale par menu déroulant, à la position de leur première entrée. */
export function mainNav(ctx: AccessContext): NavGroup[] {
  const groups: NavGroup[] = [];
  for (const e of navFor("main", ctx)) {
    if (!e.menu) {
      groups.push({ kind: "link", entry: e });
      continue;
    }
    const menu = groups.find((g): g is Extract<NavGroup, { kind: "menu" }> => g.kind === "menu" && g.label === e.menu);
    if (menu) menu.entries.push(e);
    else groups.push({ kind: "menu", label: e.menu, entries: [e] });
  }
  return groups;
}

/** Chemin d'une entrée relatif à celui d'une entrée parente, pour les routes imbriquées ("" = route index). */
export function relativePath(id: AccessId, parent: AccessId): string {
  const path = entry(id).path;
  const base = entry(parent).path;
  if (path === base) return "";
  if (!path.startsWith(`${base}/`)) throw new Error(`${id} (${path}) n'est pas sous ${parent} (${base})`);
  return path.slice(base.length + 1);
}
