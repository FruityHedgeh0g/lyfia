# From Membre up, a person belongs to one Secteur; Bénévoles form a shared pool

Every Membre, Chef de groupe, Bureau member and Admin belongs to exactly one Secteur, and acts only within it: Groupes, Affectations, Events, rosters and exports, Posts, the Carrousel, promotions, and the Inscrits list. Bénévoles belong to no Secteur: they form one pool, ride at any Secteur's Events, and are promoted to Membre by the Bureau of a Secteur they rode with, which gives them that Secteur. The Super admin stays above all Secteurs and alone manages what is common to all of them (Secteurs themselves, the site Configuration, feature switches). We scope now, instead of waiting for a second Secteur as ADR 0001 planned, because rules that assume a single Secteur (such as a Groupe form that never asks for one) break the day a second one opens.

## Considered Options

- **Stay unscoped until a second Secteur opens** (ADR 0001): rejected. Every screen and check built meanwhile would have to be revisited then.
- **Bénévoles also belong to a Secteur**: rejected. A Bénévole rides wherever they like; membership comes with the fee paid to one Secteur.
- **Let the Super admin move people between Secteurs**: rejected, except when appointing an Admin. A person goes back to Bénévole and is promoted again in the other Secteur.

## Consequences

- A Membre rides only at their own Secteur's Events; a Bénévole at any. ~~And the Super admin at any.~~ Superseded by ADR 0006: the Super admin is a dedicated account, which rides at no Event and has no space of a Secteur (Mes événements, Mon groupe, Mon secteur); its holder rides through their own account.
- The public site offers one Secteur choice, which filters Events, Actualités and the Carrousel.
- Existing Membres and above are attached to Algrange, the only Secteur so far, when this is deployed.
- Supersedes the last consequence of ADR 0001; the rest of ADR 0001 stands.
