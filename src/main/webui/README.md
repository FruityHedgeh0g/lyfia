# Une Rose Un Espoir — Front

Front du site de l'association, servi par Quarkus/Quinoa (Vite + React + TypeScript).

## Démarrer

```bash
npm install
npm run dev      # serveur de dev, sur /quinoa
npm run build    # build de prod
npm test         # tests unitaires (Vitest)
```

## Architecture

Design atomique strict, avec CSS Modules pour une atomicité réelle des styles (aucune classe globale hors `theme/tokens.css`) :

```
src/
  app/            routing (React Router), providers (TanStack Query, thème, auth)
  auth/           contexte d'auth mocké, carte d'accès (access.ts) et garde de route (RequireAccess)
  theme/          tokens CSS (charte, typographie, espacements, mode nuit) + contexte de thème
  components/
    atoms/        Button, ButtonLink, Badge, Icon, Input, Spinner, Logo...
    molecules/    FormField, MediaCard, DropdownMenu, SectionHeading, AdminListItem...
    organisms/    Header, Footer, Carousel, PageHero, SplitPanel, PostList, EventList...
    templates/    PublicLayout, SpaceLayout (Mon espace / Administration)
  pages/          une page = une route
  features/       un dossier par domaine métier (events, posts, medias, sectors,
                   groups, users, configurations, featureFlags,
                   featureRequests) : hooks TanStack Query + client API
  lib/            utilitaires partagés (dates, images de substitution)
```

## Données mockées

Le backend n'expose aujourd'hui que des `GET` liste pour la plupart des ressources
(`EventController`, `PostController`, `MediaController`...) ; les créations/éditions
et l'authentification ne sont pas encore implémentées côté Java. En attendant,
chaque domaine dans `features/*` expose un client API mocké (`*Api.ts`) qui :

- reflète exactement la forme des DTOs backend (`EventDto`, `PostDto`, `SectorDto`...) ;
- a la même signature qu'un futur appel `fetch` réel (fonctions `async`, mêmes
  paramètres/retours) ;
- persiste les créations/éditions en `localStorage` pour permettre de démontrer
  des parcours complets (inscription à un événement, édition d'un secteur...)
  sans backend actif.

**Pour rebrancher un domaine sur l'API réelle**, il suffit de remplacer le
contenu de son `*Api.ts` par de vrais appels `fetch`/`fetch` — les hooks
(`use*.ts`) et les composants qui les consomment n'ont pas besoin de changer.

Deux domaines (`featureFlags`, `configurations` en écriture, `featureRequests`)
n'ont aucune contrepartie backend actuelle et resteront mockés jusqu'à ce que
les endpoints correspondants existent.

## Rôles & authentification

La connexion passe par Keycloak ; le rôle de chaque personne est lu en base (ADR 0002) et renvoyé par
`GET /api/users/me`. Les rôles, du plus bas au plus haut : `visiteur`, `benevole`, `membre`,
`chef_de_groupe`, `bureau`, `admin`, `super_admin` (voir `auth/roles.ts`).

**Personas (développement et QA).** En profil `dev`, `lyfia.dev-personas=true` : un sélecteur « Persona (dev) »
apparaît dans l'en-tête pour la personne connectée. Il change vraiment son rôle (et son Secteur) en base
(`PUT /api/dev/persona`), pour essayer le site comme n'importe quel rôle sans autre compte Keycloak. Désactivé par
défaut, donc absent en production ; le Super admin désigné par configuration retrouve son rôle au redémarrage
(ADR 0006).

**Aperçu.** Le Super admin voit le site, en lecture seule, tel qu'un rôle le verrait (ADR 0009) ; rien n'y est
faisable.

Chaque page protégée est déclarée une fois dans `auth/access.ts` (rôle minimal,
fonctionnalité éventuelle). Le routeur l'enveloppe dans `<RequireAccess id="...">`,
qui redirige vers l'accueil si l'accès est refusé ; le Header, le Footer et les
onglets des espaces lisent la même carte.

## Charte graphique

Inspirée de [uneroseunespoir.com](https://www.uneroseunespoir.com) :

- couleurs : rouge de la rose `#d01729` (appels à l'action, filets) et bleu
  marine `#04396b` (structure, sur-titres, bandeaux) ;
- typographie : Montserrat, auto-hébergée via `@fontsource-variable/montserrat`
  (aucune requête vers Google Fonts) ; titres en capitales grasses, sur-titres
  marine précédés d'un filet rouge (classe globale `.eyebrow`) ;
- formes : angles francs (boutons, champs, cartes), bande diagonale rouge en bas
  des bandeaux (`PageHero`) ;
- mouvement : transitions courtes, désactivées si `prefers-reduced-motion`.

`theme/tokens.css` porte ces tokens et un mode nuit complet (fond marine
profond), activé via `[data-theme="dark"]` sur `<html>` (bascule dans la barre
utilitaire du header, persistée en `localStorage`).

## Tests

`npm test` lance Vitest + Testing Library. Les tests couvrent la logique
métier sensible (carte d'accès, garde de route, collections mockées) et quelques
composants partagés (Button, FormField, Header, AdminCrudList) — pas une
couverture exhaustive de chaque écran.
