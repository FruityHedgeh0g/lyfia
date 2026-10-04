/** Une réponse de l'API en erreur, avec son statut HTTP. */
export class HttpError extends Error {
  constructor(readonly status: number, message: string) {
    super(message);
    this.name = "HttpError";
  }
}

/** Ce qui a été demandé relève d'une Fonctionnalité désactivée (503, ADR 0009). */
export class FeatureOffError extends HttpError {
  constructor(readonly feature: string, message: string) {
    super(503, message);
    this.name = "FeatureOffError";
  }
}

/**
 * Appel à l'API du site. `X-Requested-With` fait répondre 499 au lieu de rediriger vers Keycloak
 * quand la session manque (quarkus.oidc.authentication.java-script-auto-redirect=false) : une
 * redirection vers un autre domaine échouerait de toute façon dans le navigateur.
 */
export async function apiFetch<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set("X-Requested-With", "JavaScript");
  headers.set("Accept", "application/json");
  // Un FormData fixe lui-même son Content-Type, avec la frontière entre ses parties
  if (init.body !== undefined && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

  const response = await fetch(path, { ...init, headers, credentials: "same-origin" });
  if (response.status === 503) {
    const body = await response.json().catch(() => null);
    if (body?.error === "feature-off") throw new FeatureOffError(body.feature, "Cette fonctionnalité est temporairement suspendue.");
  }
  if (!response.ok) throw new HttpError(response.status, `${init.method ?? "GET"} ${path} : ${response.status}`);
  if (response.status === 204) return undefined as T;
  if (!response.headers.get("Content-Type")?.includes("json")) {
    // Sans backend (serveur Vite seul), le site répond index.html à tout chemin
    throw new HttpError(response.status, `${path} : réponse qui n'est pas du JSON`);
  }
  return (await response.json()) as T;
}

/** Pas de session : 401, ou 499 quand l'API refuse de rediriger vers Keycloak. */
export const isUnauthenticated = (error: unknown) =>
  error instanceof HttpError && (error.status === 401 || error.status === 499);
