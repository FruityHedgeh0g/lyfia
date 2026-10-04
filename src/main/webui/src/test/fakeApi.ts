import { vi } from "vitest";
import { mockSectors } from "./fixtures/sectors";
import { Sector } from "../features/sectors/types";
import { mockGroups } from "./fixtures/groups";
import { Group } from "../features/groups/types";
import { mockMembers } from "./fixtures/users";
import { Member } from "../features/users/types";
import { assignableRoles, canLeadGroupe, RoleId, roleAtLeast } from "../auth/roles";
import { createMemoryJsonStore, JsonStore } from "./fakes/jsonStore";
import { createEventsApi as fakeEventsOn } from "./fakes/fakeEvents";
import { createRegistrationsApi as fakeRegistrationsOn } from "./fakes/fakeRegistrations";
import { PhoneRequiredError } from "../features/events/registrationsApi";
import { mockPosts } from "./fixtures/posts";
import { Post } from "../features/posts/types";
import { mockConfigurations } from "./fixtures/configurations";
import { Configuration } from "../features/configurations/types";
import { mockMedias } from "./fixtures/medias";
import { Media } from "../features/medias/types";
import { mockCarouselItems } from "./fixtures/carousel";
import { CarouselItem } from "../features/carousel/types";
import { mockFeatureFlags } from "./fixtures/featureFlags";
import { FeatureFlag, JournalEntry } from "../features/featureFlags/types";
import { mockFeatureRequests } from "./fixtures/featureRequests";
import { FeatureRequest } from "../features/featureRequests/types";

/**
 * Un backend en mémoire derrière `fetch`, pour les tests de pages : chaque client passé de ses fixtures à
 * l'API (frontend: replace the mocked clients, #32) y ajoute ses routes. Remis à zéro avant chaque test
 * (test/setup.ts). Il ne refait que ce que les pages observent ; les règles elles-mêmes sont testées côté Java.
 */
interface Viewer {
  role: RoleId;
  sectorId: string | null;
  userId: string;
  firstName: string;
  lastName: string;
  phone: string;
}

interface State {
  viewer: Viewer;
  /** Événements, inscriptions et maximums des Groupes, tenus par test/fakes/fakeEvents et fakeRegistrations. */
  store: JsonStore;
  sectors: Sector[];
  groups: Group[];
  members: Member[];
  posts: Post[];
  configurations: Configuration[];
  medias: Media[];
  carousel: CarouselItem[];
  features: FeatureFlag[];
  /** Le Journal, les plus récentes d'abord. */
  journal: JournalEntry[];
  featureRequests: FeatureRequest[];
  /** L'API offre-t-elle les personas (lyfia.dev-personas) ? */
  devPersonas: boolean;
}

let state: State;

/** Qui l'API croit connecté, et son Secteur ; test/testUser le règle. */
export function setFakeViewer(role: RoleId, sectorId: string | null = null, person: Partial<Viewer> = {}) {
  state.viewer = { ...state.viewer, ...person, role, sectorId };
}

/** L'API n'offre pas les personas, comme hors développement. */
export function withoutDevPersonas() {
  state.devPersonas = false;
}

export function resetFakeApi() {
  state = {
    viewer: { role: "visiteur", sectorId: null, userId: "", firstName: "", lastName: "", phone: "" },
    store: createMemoryJsonStore(),
    sectors: structuredClone(mockSectors).map((s) => ({ ...s, closed: s.closed ?? false })),
    groups: structuredClone(mockGroups),
    members: structuredClone(mockMembers),
    posts: structuredClone(mockPosts),
    configurations: structuredClone(mockConfigurations),
    medias: structuredClone(mockMedias),
    carousel: structuredClone(mockCarouselItems),
    features: structuredClone(mockFeatureFlags),
    journal: [],
    devPersonas: true,
    featureRequests: structuredClone(mockFeatureRequests),
  };
  vi.stubGlobal("fetch", vi.fn(handle));
}

let seeded = 0;

/** Ajoute un Groupe tel quel, sans les règles de l'API ; renvoie son id. */
export function seedGroup(group: Partial<Group> & Pick<Group, "name" | "sectorId">): string {
  const groupId = group.groupId ?? `group-seed-${++seeded}`;
  state.groups.push({ description: "", area: "", chef: null, ...group, groupId });
  return groupId;
}

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
const empty = (status: number) => new Response(null, { status });

const isSuperAdmin = () => state.viewer.role === "super_admin";
/** Ce que gère la personne : son Secteur ; tout pour le Super admin (ADR 0004). */
const manages = (sectorId: string | null | undefined) => isSuperAdmin() || (Boolean(sectorId) && sectorId === state.viewer.sectorId);
/** Une Fonctionnalité que l'API ne connaît pas est active, comme FeatureLevers côté backend. */
const isOn = (name: FeatureFlag["name"], sectorId?: string) => {
  const flag = state.features.find((f) => f.name === name);
  return !flag || (flag.isActive && !(sectorId && flag.offSectors?.includes(sectorId)));
};
/** 503 `feature-off`, la réponse de l'API à ce qu'une Fonctionnalité désactivée couvre (ADR 0009). */
const featureOff = (feature: FeatureFlag["name"]) => json({ error: "feature-off", feature }, 503);
const visibleSector = (s: Sector) => !s.closed || isSuperAdmin();

async function handle(input: RequestInfo | URL, init: RequestInit = {}): Promise<Response> {
  const url = new URL(typeof input === "string" ? input : input.toString(), "http://localhost");
  const method = (init.method ?? "GET").toUpperCase();
  const body = typeof init.body === "string" ? JSON.parse(init.body) : init.body;
  const parts = url.pathname.split("/").filter(Boolean).map(decodeURIComponent);

  if (parts[0] === "api" && parts[1] === "sectors") return sectors(method, parts.slice(2), body);
  if (parts[0] === "api" && parts[1] === "groups") return groups(method, parts.slice(2), body);
  if (parts[0] === "api" && parts[1] === "users") return users(method, parts.slice(2), body);
  if (parts[0] === "api" && parts[1] === "features") return features(method, parts.slice(2), body, url.searchParams);
  if (parts[0] === "api" && parts[1] === "feature-requests") return featureRequests(method, body);
  if (parts[0] === "api" && parts[1] === "medias") return medias(method, parts.slice(2), body);
  if (parts[0] === "api" && parts[1] === "carousel") return carousel(method, parts.slice(2), body, url.searchParams);
  if (parts[0] === "api" && parts[1] === "configurations") return configurations(method, parts[2], body);
  if (parts[0] === "api" && parts[1] === "posts") return posts(method, parts.slice(2), body, url.searchParams);
  if (parts[0] === "api" && parts[1] === "dev" && parts[2] === "persona" && method === "GET") {
    return state.viewer.role === "visiteur" ? empty(401) : empty(state.devPersonas ? 204 : 404);
  }
  if (parts[0] === "api" && parts[1] === "dev" && parts[2] === "persona" && method === "PUT") {
    if (state.viewer.role === "visiteur") return empty(401);
    state.viewer = { ...state.viewer, role: body.role, sectorId: body.sectorId ?? null };
    return json({ role: body.role, sectorId: body.sectorId ?? null });
  }
  if (parts[0] === "api" && parts[1] === "events") return events(method, parts.slice(2), body, url.searchParams);
  return empty(404);
}

function sectors(method: string, [sectorId, action]: string[], body: any): Response {
  if (!sectorId) {
    if (method === "GET") return json(state.sectors.filter(visibleSector));
    if (method === "POST") {
      if (!isSuperAdmin()) return empty(403);
      if (state.sectors.some((s) => s.name === body.name)) return empty(409);
      const created: Sector = { sectorId: `sector-${state.sectors.length + 1}-new`, name: body.name, description: body.description ?? "", groups: [], closed: false };
      state.sectors.push(created);
      return json({ sectorId: created.sectorId, name: created.name, description: created.description });
    }
    if (method === "PATCH") {
      const sector = state.sectors.find((s) => s.sectorId === body.sectorId && visibleSector(s));
      if (!sector) return empty(404);
      if (sector.closed) return empty(400);
      if (body.name !== sector.name && !isSuperAdmin()) return empty(403);
      Object.assign(sector, { name: body.name, description: body.description });
      return json(sector);
    }
    return empty(405);
  }

  const sector = state.sectors.find((s) => s.sectorId === sectorId && visibleSector(s));
  if (!sector) return empty(404);
  if (method === "GET" && !action) return json(sector);
  if (method === "POST" && (action === "close" || action === "reopen")) {
    if (!isSuperAdmin()) return empty(403);
    sector.closed = action === "close";
    return json(sector);
  }
  return empty(405);
}

const sectorOf = (sectorId: string) => state.sectors.find((s) => s.sectorId === sectorId);
const visibleGroup = (g: Group) => !sectorOf(g.sectorId)?.closed || isSuperAdmin();

function groups(method: string, [groupId, action, userId]: string[], body: any): Response {
  if (!groupId) {
    if (method === "GET") return json(state.groups.filter(visibleGroup));
    if (method === "POST") {
      const sectorId = isSuperAdmin() ? body.sectorId : state.viewer.sectorId;
      if (!sectorId || (!isSuperAdmin() && body.sectorId && body.sectorId !== sectorId)) return empty(403);
      if (sectorOf(sectorId)?.closed) return empty(400);
      if (state.groups.some((g) => g.name === body.name)) return empty(409);
      const groupId = seedGroup({ name: body.name, description: body.description, area: body.area, sectorId });
      const { chef: _chef, ...created } = state.groups.find((g) => g.groupId === groupId)!;
      return json(created);
    }
    if (method === "PATCH") {
      const group = state.groups.find((g) => g.groupId === body.groupId && visibleGroup(g));
      if (!group) return empty(404);
      if (sectorOf(group.sectorId)?.closed) return empty(400);
      Object.assign(group, { name: body.name, description: body.description, area: body.area });
      return json(group);
    }
    return empty(405);
  }

  const group = state.groups.find((g) => g.groupId === groupId && visibleGroup(g));
  if (!group) return empty(404);
  if (action !== "chef") return empty(405);
  if (sectorOf(group.sectorId)?.closed) return empty(400);
  if (method === "DELETE") {
    group.chef = null;
    return json(group);
  }
  if (method === "PUT" && userId) {
    const member = state.members.find((m) => m.userId === userId);
    if (!member) return empty(404);
    // Le Chef quitte le Groupe qu'il menait
    state.groups.filter((g) => g.chef?.userId === userId).forEach((g) => (g.chef = null));
    group.chef = { userId, firstName: member.firstName, lastName: member.lastName };
    return json(group);
  }
  return empty(405);
}

/** UserDto (vue Basic) : le Secteur imbriqué. */
const userDto = (m: Member) => ({
  userId: m.userId,
  firstName: m.firstName,
  lastName: m.lastName,
  role: m.role,
  president: Boolean(m.president),
  sector: m.sectorId ? { sectorId: m.sectorId, name: sectorOf(m.sectorId)?.name ?? "" } : null,
});

/** Secteur après un changement de Rôle (ADR 0004), comme UserServiceImpl.sectorAfter. */
function sectorAfter(member: Member, role: RoleId, sectorId: string | null): string | null | Response {
  const current = member.sectorId ?? null;
  if (!isSuperAdmin()) {
    if (sectorId || !state.viewer.sectorId) return empty(403);
    if (current && current !== state.viewer.sectorId) return empty(403);
    return roleAtLeast(role, "membre") ? state.viewer.sectorId : null;
  }
  if (!roleAtLeast(role, "membre")) return null;
  if (role !== "admin" && current) return sectorId && sectorId !== current ? empty(400) : current;
  return sectorId ?? empty(400);
}

function users(method: string, [userId, action]: string[], body: any): Response {
  if (!userId) return method === "GET" ? json(state.members.map(userDto)) : empty(405);
  if (userId === "me" && method === "PATCH") {
    state.viewer.phone = body.phone ?? "";
    const { userId: id, firstName, lastName, phone, role } = state.viewer;
    return json({ userId: id, firstName, lastName, phone, role, sector: state.viewer.sectorId ? { sectorId: state.viewer.sectorId, name: "" } : null });
  }
  const member = state.members.find((m) => m.userId === userId);
  if (!member) return empty(404);

  if (method === "PATCH" && !action) {
    if (!roleAtLeast(state.viewer.role, "admin")) return empty(403);
    if (!body.firstName?.trim() || !body.lastName?.trim()) return empty(400);
    Object.assign(member, { firstName: body.firstName.trim(), lastName: body.lastName.trim() });
    return json(userDto(member));
  }
  if (method === "PUT" && action === "role") {
    const role = body.role as RoleId;
    if (!assignableRoles(state.viewer.role, member.role).includes(role)) return empty(403);
    const sectorId = sectorAfter(member, role, body.sectorId ?? null);
    if (sectorId instanceof Response) return sectorId;
    const changesSector = Boolean(member.sectorId) && member.sectorId !== sectorId;
    Object.assign(member, { role, sectorId, president: role === "bureau" ? member.president : false });
    // Perdre le titre de Chef de groupe, ou quitter son Secteur, met fin à l'Affectation
    if (!canLeadGroupe(role) || changesSector) state.groups.filter((g) => g.chef?.userId === userId).forEach((g) => (g.chef = null));
    return json(userDto(member));
  }
  if (method === "PUT" && action === "president") {
    if (!roleAtLeast(state.viewer.role, "admin")) return empty(403);
    if (member.role !== "bureau") return empty(400);
    state.members.forEach((m) => (m.president = false));
    member.president = true;
    return json(userDto(member));
  }
  return empty(405);
}

const closedSectorIds = () => new Set(state.sectors.filter((s) => s.closed).map((s) => s.sectorId));

/** Les Événements du faux backend, pour préparer un test (sans les règles d'accès de l'API). */
export const createEventsApi = () => fakeEventsOn(state.store, closedSectorIds);

/** Les inscriptions du faux backend, pour préparer un test. */
export const createRegistrationsApi = () => fakeRegistrationsOn(state.store, createEventsApi(), () => state.groups);

const me = () => ({
  userId: state.viewer.userId,
  firstName: state.viewer.firstName,
  lastName: state.viewer.lastName,
  phone: state.viewer.phone,
  sectorId: state.viewer.sectorId,
});

/** Les erreurs des règles mockées, en statuts HTTP comme le backend. */
async function answer(call: () => Promise<unknown>): Promise<Response> {
  try {
    const result = await call();
    return result === undefined ? empty(204) : json(result);
  } catch (error) {
    if (error instanceof PhoneRequiredError) return json({ error: "phone-required" }, 422);
    return empty(400);
  }
}

async function events(method: string, [eventId, ...rest]: string[], body: any, query: URLSearchParams): Promise<Response> {
  const seesPlanification = roleAtLeast(state.viewer.role, "bureau");
  const ev = createEventsApi();
  const reg = createRegistrationsApi();
  const actor = { personId: state.viewer.userId, bureau: seesPlanification };
  const path = rest.join("/");

  if (!eventId) {
    if (method === "GET") return json(await ev.fetchEvents(seesPlanification, isSuperAdmin()));
    if (!seesPlanification) return empty(403);
    if (method === "POST") {
      const sectorId = isSuperAdmin() ? body.sectorId : state.viewer.sectorId;
      return answer(() => ev.createEvent({ ...body, sectorId }));
    }
    if (method === "PATCH") {
      return answer(async () => {
        await ev.updateEvent(body.eventId, body);
        return ev.fetchEventById(body.eventId, true, true);
      });
    }
    return empty(405);
  }
  if (eventId === "registrations" && method === "GET") return answer(() => reg.fetchMyRegistrations(state.viewer.userId));
  if (eventId === "mon-groupe" && method === "GET") return answer(() => reg.fetchMonGroupe(state.viewer.userId));

  const event = await ev.fetchEventById(eventId, seesPlanification, isSuperAdmin());
  if (!event) return empty(404);
  if (!path && method === "GET") return json(event);
  if (path === "status" && method === "PUT") return answer(() => ev.changeStatus(eventId, body.status));
  const signsUp = (path === "registration" || path === "registration/demande") && method === "PUT";
  if (signsUp && !isOn("inscription-evenements", event.sectorId)) return featureOff("inscription-evenements");
  if (path === "registration" && method === "PUT") {
    const groupId = query.get("groupId") ?? undefined;
    const piloteId = query.get("piloteId") ?? undefined;
    return answer(() => reg.signUp(eventId, me(), groupId, piloteId));
  }
  if (path === "registration" && method === "DELETE") return answer(() => reg.withdraw(eventId, state.viewer.userId));
  if (path === "registration/demande" && method === "PUT") return answer(() => reg.requestGroup(eventId, state.viewer.userId, body.groupId));
  if (path === "pilotes" && method === "GET") return answer(() => reg.fetchPilotes(eventId));
  if (path === "roster" && method === "GET") return answer(() => reg.fetchRoster(eventId));

  const [kind, personId, action, groupId] = rest;
  if (kind === "demandes" && method === "POST") return answer(() => reg.decideDemande(eventId, personId, actor, action === "accept"));
  if (kind === "groups" && action === "maximum" && method === "PUT") return answer(() => reg.setGroupMaximum(eventId, personId, body.maximum || null));
  if (kind === "roster" && action === "group" && groupId && method === "PUT") return answer(() => reg.placeInGroup(eventId, personId, groupId));
  if (kind === "roster" && action === "group" && method === "DELETE") return answer(() => reg.takeOutOfGroup(eventId, personId, actor));
  if (kind === "roster" && action === "promote" && method === "POST") return answer(() => reg.promote(eventId, personId));
  if (kind === "roster" && !action && method === "DELETE") return answer(() => reg.remove(eventId, personId));
  return empty(405);
}

function posts(method: string, [postId, action]: string[], body: any, query: URLSearchParams): Response {
  const isBureau = roleAtLeast(state.viewer.role, "bureau");
  const offFor = (p: Post) => !isBureau && !isOn("actualites", p.sectorId ?? undefined);
  const visiblePost = (p: Post) => (p.status === "publie" && !offFor(p)) || (isBureau && manages(p.sectorId));
  if (method === "GET" && !isBureau && !isOn("actualites")) return featureOff("actualites");
  if (!postId) {
    if (method === "GET") {
      const managed = query.get("managed") === "true" && isBureau;
      return json(state.posts.filter((p) => (managed ? manages(p.sectorId) : visiblePost(p))));
    }
    if (!isBureau) return empty(403);
    if (!body.title?.trim() || !body.content?.trim()) return empty(400);
    if (method === "POST") {
      const { userId, firstName, lastName } = state.viewer;
      const sectorId = isSuperAdmin() ? (body.sectorId ?? null) : state.viewer.sectorId;
      const post: Post = { postId: `post-new-${state.posts.length + 1}`, title: body.title, content: body.content, status: "brouillon", author: { userId, firstName, lastName }, sectorId };
      state.posts.unshift(post);
      return json(post);
    }
    const post = state.posts.find((p) => p.postId === body.postId);
    if (method !== "PATCH" || !post) return empty(method === "PATCH" ? 404 : 405);
    if (!manages(post.sectorId)) return empty(403);
    Object.assign(post, { title: body.title, content: body.content });
    return json(post);
  }
  const offPost = state.posts.find((p) => p.postId === postId && p.status === "publie" && offFor(p));
  if (offPost && method === "GET") return featureOff("actualites");
  const post = state.posts.find((p) => p.postId === postId && visiblePost(p));
  if (!post) return empty(404);
  if (method === "GET" && !action) return json(post);
  if (method === "PUT" && action === "status" && isBureau) {
    if (!manages(post.sectorId)) return empty(403);
    post.status = body.status;
    return json(post);
  }
  return empty(405);
}

function configurations(method: string, name: string | undefined, body: any): Response {
  if (!name) return method === "GET" ? json(state.configurations) : empty(405);
  if (method !== "PUT") return empty(405);
  if (!isSuperAdmin()) return empty(403);
  const setting = state.configurations.find((c) => c.name === name);
  if (!setting) return empty(404);
  setting.value = body.value;
  return json(setting);
}

/** MediaDto : le type du fichier dans `mimeType`. */
const mediaDto = ({ url: _url, contentType, ...m }: Media) => ({ ...m, mimeType: contentType });

function medias(method: string, [mediaId]: string[], body: any): Response {
  const isBureau = roleAtLeast(state.viewer.role, "bureau");
  if (mediaId === "gallery" && method === "GET") {
    if (!isOn("galerie-photos") && !isBureau) return featureOff("galerie-photos");
    return json(state.medias.map(mediaDto));
  }
  if (!mediaId) {
    if (method === "GET") return json(state.medias.map(mediaDto));
    if (method !== "POST") return empty(405);
    if (!isBureau) return empty(403);
    if (!isOn("depot-medias")) return featureOff("depot-medias");
    const file = (body as FormData).get("file") as File;
    const media: Media = {
      mediaId: `media-new-${state.medias.length + 1}`,
      fileKey: "database",
      originalFilename: file.name,
      contentType: file.type,
      fileSize: file.size,
      url: "",
      alt: String((body as FormData).get("alt") ?? ""),
    };
    state.medias.push(media);
    return json(mediaDto(media));
  }
  const media = state.medias.find((m) => m.mediaId === mediaId);
  if (!media) return empty(404);
  if (method !== "PATCH") return empty(405);
  if (!isBureau) return empty(403);
  media.alt = body.alt;
  return json(mediaDto(media));
}

function carousel(method: string, [id, action, direction]: string[], body: any, query: URLSearchParams): Response {
  const isBureau = roleAtLeast(state.viewer.role, "bureau");
  const ordered = () => [...state.carousel].sort((a, b) => a.order - b.order);
  if (method === "GET" && !isBureau && !isOn("carrousel")) return featureOff("carrousel");
  if (!id) {
    if (method === "GET") {
      const managed = query.get("managed") === "true" && isBureau;
      return json(ordered().filter((item) => (managed ? manages(item.sectorId) : item.active || (isBureau && manages(item.sectorId)))));
    }
    if (method !== "POST") return empty(405);
    if (!isBureau) return empty(403);
    const sectorId = isSuperAdmin() ? (body.sectorId ?? null) : state.viewer.sectorId;
    const item: CarouselItem = { id: `carousel-new-${state.carousel.length + 1}`, order: Math.max(0, ...state.carousel.map((i) => i.order)) + 1, ...body, sectorId };
    state.carousel.push(item);
    return json(item);
  }
  if (!isBureau) return empty(403);
  const item = state.carousel.find((i) => i.id === id);
  if (!item) return empty(404);
  if (!manages(item.sectorId)) return empty(403);
  if (method === "PUT" && !action) {
    Object.assign(item, { ...body, sectorId: item.sectorId });
    return json(item);
  }
  if (method === "DELETE" && !action) {
    state.carousel = state.carousel.filter((i) => i.id !== id);
    return empty(204);
  }
  if (method === "POST" && action === "move") {
    const items = ordered().filter((i) => (i.sectorId ?? null) === (item.sectorId ?? null));
    const index = items.indexOf(item);
    const other = items[direction === "up" ? index - 1 : index + 1];
    if (other) [item.order, other.order] = [other.order, item.order];
    return json(ordered().filter((i) => manages(i.sectorId)));
  }
  return empty(405);
}

/** Une Fonctionnalité, telle qu'un test la veut avant de rendre une page. */
/** Une Fonctionnalité désactivée pour un Secteur seulement, sans passer par les leviers. */
export function setFakeFeatureOffIn(name: FeatureFlag["name"], sectorId: string) {
  const feature = state.features.find((f) => f.name === name);
  if (feature) feature.offSectors = [...(feature.offSectors ?? []), sectorId];
}

export function setFakeFeature(name: FeatureFlag["name"], isActive: boolean) {
  const feature = state.features.find((f) => f.name === name);
  if (feature) feature.isActive = isActive;
}

/** Ajoute des entrées au Journal, sans passer par les leviers. */
export function seedJournal(count: number) {
  for (let i = 0; i < count; i++) {
    state.journal.push({
      id: `journal-seed-${state.journal.length + 1}`,
      feature: "galerie-photos",
      sectorId: null,
      sectorName: null,
      isActive: i % 2 === 0,
      reason: null,
      switchedBy: "Jean Dupont",
      switchedAt: new Date(2026, 9, 1, 12, 0, 0).toISOString(),
    });
  }
}

function features(method: string, [name, scope, sectorId]: string[], body: any, query: URLSearchParams): Response {
  if (scope && scope !== "sectors") return empty(404);
  if (!name) return method === "GET" ? json(state.features) : empty(405);
  if (name === "journal") {
    if (method !== "GET") return empty(405);
    if (!isSuperAdmin()) return empty(403);
    const page = Number(query.get("page") ?? 0);
    const size = Number(query.get("size") ?? 20);
    return json(state.journal.slice(page * size, (page + 1) * size));
  }
  if (method !== "PUT") return empty(405);
  if (!isSuperAdmin()) return empty(403);
  const feature = state.features.find((f) => f.name === name);
  if (!feature) return empty(404);
  if (sectorId) {
    if (!feature.perSecteur) return empty(400);
    const sector = state.sectors.find((s) => s.sectorId === sectorId);
    if (!sector) return empty(404);
    if (sector.closed) return empty(400);
    const others = (feature.offSectors ?? []).filter((id) => id !== sectorId);
    feature.offSectors = body.isActive ? others : [...others, sectorId];
    state.journal.unshift({
      id: `journal-${state.journal.length + 1}`,
      feature: feature.name,
      sectorId,
      sectorName: sector.name,
      isActive: body.isActive,
      reason: body.reason ?? null,
      switchedBy: `${state.viewer.firstName} ${state.viewer.lastName}`,
      switchedAt: new Date().toISOString(),
    });
    return json(feature);
  }
  if (feature.isActive && !body.isActive) feature.refusedCount = 0;
  feature.isActive = body.isActive;
  const by = `${state.viewer.firstName} ${state.viewer.lastName}`;
  const at = new Date().toISOString();
  Object.assign(feature, { lastSwitchedBy: by, lastSwitchedAt: at, lastReason: body.reason ?? null });
  state.journal.unshift({
    id: `journal-${state.journal.length + 1}`,
    feature: feature.name,
    sectorId: null,
    sectorName: null,
    isActive: body.isActive,
    reason: body.reason ?? null,
    switchedBy: by,
    switchedAt: at,
  });
  return json(feature);
}

function featureRequests(method: string, body: any): Response {
  if (!roleAtLeast(state.viewer.role, "bureau")) return empty(403);
  if (method === "GET") return json(state.featureRequests);
  if (method !== "POST") return empty(405);
  if (!isOn("demandes-fonctionnalite")) return featureOff("demandes-fonctionnalite");
  if (!body.title?.trim() || !body.description?.trim()) return empty(400);
  const request: FeatureRequest = {
    id: `fr-new-${state.featureRequests.length + 1}`,
    title: body.title,
    description: body.description,
    createdAt: new Date().toISOString(),
    requestedBy: `${state.viewer.firstName} ${state.viewer.lastName}`,
  };
  state.featureRequests.unshift(request);
  return json(request);
}
