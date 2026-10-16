# Une Rose Un Espoir

Site and member space of the association: public pages, a member space ("Mon espace") and an administration space ("Administration").

## Glossary

### Access

**Role**: a person's single access level, ordered `visiteur` < `bénévole` < `membre` < `chef_de_groupe` < `bureau` < `admin` < `super_admin` (`RoleEnum` in the back end, stored per person; mirrored in `webui/src/auth/roles.ts`). A role grants everything the lower roles grant.
_Avoid_: permission, legal role, organizational role.

**Access map**: the single declaration of every navigable entry (path, label, minimum Role, space, optional Feature) (`webui/src/auth/access.ts`). Route guards, the header and the space tabs all derive from it, so a visible link always leads to an accessible page.

**Feature**: an emergency lever the Super admin pulls at runtime to stop one capability of the site: while a Feature is off, the site both hides that capability and refuses it, and what was already recorded stays as it was. Features: Dons en ligne, Galerie photos, Inscription aux événements, Inscription sur le site, Actualités, Carrousel, Dépôt de médias, Demandes de fonctionnalité, Export de la liste. The Super admin's own tools and signing in are never a Feature. Every Feature can be turned off for the whole site; those about what belongs to a Secteur (Actualités, Inscription aux événements, Export de la liste) can also be turned off for one Secteur. The two levers are independent: a Feature works in a Secteur only while both are on, so turning the site-wide lever back on leaves a Secteur's lever as it was. Turned off for one Secteur, a Feature stops only that Secteur's part (its Posts leave Actualités, its Events stop taking sign-ups) and the other Secteurs carry on. A new Secteur starts with every lever on. A content Feature (Actualités, Galerie photos, Carrousel) stops only what the public sees: the Bureau keeps preparing it. Inscription aux événements stops only new sign-ups and new Demandes de groupe: withdrawing, and the Bureau's and Chefs de groupe's decisions on those already signed up, go on. Dons en ligne stops only giving online, never the Faire un don page, which stays as the association's information on donations and the starting point of giving online. Where a Feature takes away what the public sees, it simply disappears; where it takes away something a registered person does, a message says it is suspended. The Super admin still sees what is turned off, marked as such, to check it before turning it back on, but cannot use it either. An Access map entry or a UI element can depend on a Feature.
_Avoid_: feature flag (in UI copy: "fonctionnalité").

**Aperçu**: the Super admin's read-only view of the site as a given Role would see it (with a Secteur for the Roles that belong to one): its navigation, its pages, the Features as they stand for it. Nothing can be done from an Aperçu, and it never shows a real person's own space.
_Avoid_: impersonation, "voir en tant que" a person, persona

**Journal**: the Super admin's record of every time a Feature was turned off or on: who, when, which Feature, for the whole site or which Secteur, and an optional reason, with how many attempts each Feature refused while off. Never changed nor deleted from the site.
_Avoid_: event, log (in UI copy), historique

**Space**: a group of pages sharing a layout and tab bar: `main` (header), `account` (Mon espace), `admin` (Administration).

### People

**Visiteur**: anyone browsing the site without being registered.

**Bénévole**: a Visiteur who has registered on the site. Has Mon espace and can sign up for Events. Belongs to no Secteur: the Bénévoles form one pool shared by every Secteur, riding at any Secteur's Events and promoted to Membre by the Bureau of a Secteur they rode with.
_Avoid_: volunteer, inscrit, user

**Bénévole sans compte**: a Bénévole who cannot register on the site themselves (no phone or no internet), recorded by the Bureau instead, who also signs them up for Events, under the same rules as any sign-up. A person in their own right, kept from one Event to the next, who can be promoted like any Bénévole; has no Mon espace, so the Bureau keeps their details up to date and withdraws them from Events.
_Avoid_: invité, externe, fantôme, guest

**Membre**: a Bénévole who is part of the association through one Secteur, promoted by a Bureau member once their membership fee is settled (the fee itself is handled off-site). Every Role from Membre up belongs to exactly one Secteur (the Super admin excepted) and rides only at that Secteur's Events. Nobody changes Secteur (except when the Super admin appoints them Admin of another one): a person goes back to Bénévole, losing their Secteur, and is promoted again in the other one.
_Avoid_: adhérent

**Chef de groupe**: a Membre holding the operational function of leading a Groupe, appointed by the Bureau. Keeps the title between Affectations. From an Event's `Planification` until it is under way, the one leading a Groupe is at each of its Journées with that Groupe, at the Poste of its kind (`conducteur` or `service`), with a place kept beyond any maximum, which they cannot give up and nobody takes from them: only a new Affectation does, leaving them signed up without a Groupe.
_Avoid_: group leader, responsable

**Affectation**: the Bureau's decision that a given Chef de groupe leads a given Groupe. A Groupe has at most one Chef de groupe at a time; a Chef de groupe leads at most one Groupe. Handing a Groupe to a new Chef de groupe is a single Affectation.
_Avoid_: assignment, nomination

**Bureau**: the Membres elected to run the association: an organizational function, ranked above Chef de groupe. Promotes Membres and Chefs de groupe. Promoted by an Admin. Sitting on the Bureau is not only a Role: everyone with the Bureau Role sits on it, and an Admin may sit on it too, in their own Secteur, as recorded by the Super admin or another Admin of that Secteur (never by themselves). An Admin who sits on the Bureau keeps the Admin Role.

**Fonction au Bureau**: what a person sitting on a Secteur's Bureau is there: Membre du Bureau or Président. Held by everyone with the Bureau Role, and by an Admin called to the Bureau; lost when the Role drops below Bureau. Grants no extra access.
_Avoid_: titre, poste, case Bureau

**Président**: the Fonction au Bureau of the one member who presides over a Secteur's Bureau (an Admin sitting on it included). Grants no extra access yet.

**Admin**: a person who manages the site for one Secteur and promotes its Bureau members. Appointed by the Super admin from all registered people, for a Secteur the Super admin chooses.

**Super admin**: the account above every Admin, which appoints Admins and is the only one to open, rename, close (Fermé) or reopen a Secteur. An account dedicated to administering the site, kept apart from the personal account of whoever holds it: that person rides, leads a Groupe or sits on the Bureau through their own account, in their Secteur. The Super admin account itself rides at no Event.

### Organisation

**Secteur**: a local branch of the national association (e.g. Algrange), subdivided into Groupes. Opened, renamed and closed only by the Super admin; its description is kept up to date by the Bureau.

**Secteur fermé**: a Secteur the Super admin has closed instead of deleting it, so that its lineage is kept: read-only, and seen with its Groupes and Events by the Super admin only. Closing ends its Groupes' Affectations (the Chefs keep their title), makes its unfinished Events Annulé (one En cours becomes Archivé) and keeps every sign-up as it was; the Super admin can reopen it.
_Avoid_: supprimé, gelé, actif (for the other state: a Secteur is simply a Secteur), deleted, frozen
_Avoid_: sector (in UI copy), zone

**Groupe**: a lasting team of a Secteur, led by a Chef de groupe, of one of two kinds: a Groupe de motards covers its own part of the Secteur on the road (its people are `conducteur`, `passager` or `chauffeur camionnette`), a Groupe de service runs one task at the Event (its people are `service`). Always belongs to exactly one Secteur. Who is with a Groupe is decided per Event (see Demande de groupe).
_Avoid_: team, équipe

**Demande de groupe**: a Participant's request to ride with a specific Groupe at one Event, made when signing up. Stays pending until the Bureau or that Groupe's Chef de groupe accepts or refuses it. A refused Participant stays signed up without a Groupe, and can make a new Demande or be placed by the Bureau.
_Avoid_: join request, candidature

### Events

**Event**: a gathering organised by the Bureau for a Secteur. Never deleted: cancelled instead.

**Event status**: where an Event stands in its lifecycle: `Planification` → `Ouvert` ⇄ `Complet` (set by hand by the Bureau), then `En cours` and `Archivé` (follow the dates of an `Ouvert` or `Complet` Event; one still in `Planification` stays there, hidden). `Annulé` is set by hand from any status before `Archivé`. Only the Bureau, and the Chefs de groupe already riding at it with their Groupe, see an Event in `Planification`.
_Avoid_: Publié (for Events)

**Journée**: one calendar date between an Event's start and its end; an Event of one day has one Journée. People sign up per Journée, and the maximums and Listes d'attente count per Journée.
_Avoid_: jour, date, session

**Participant**: a Bénévole with a confirmed place at a Journée of an Event (someone on its Liste d'attente is not yet a Participant there), at one Poste. Signing up is possible while the Event is `Ouvert` or `Complet` (each Groupe's Chef de groupe is signed up from the `Planification`); while `Complet`, or once a maximum is reached, a sign-up goes onto the Liste d'attente. A Participant can withdraw at any time before the Event is `Archivé`. The Bureau can remove a Participant from an Event; the Chef de groupe can only take them out of their Groupe, leaving them a Participant without a Groupe.

**Poste**: what a person signed up for an Event does at it: `conducteur`, `passager`, `chauffeur camionnette`, `point fixe` or `service`. Every Poste counts towards the Event's maximum: it caps how many people the Event receives, for their physical safety. Chosen by the person when signing up, per Event, the same for each of its Journées; the Bureau can change it afterwards.
_Avoid_: rôle (that is the Role), mode

**Conducteur**: the Poste of a person riding their own motorbike at an Event.
_Avoid_: pilote

**Passager**: the Poste of a registered Bénévole riding behind a `conducteur` of their own Groupe. A passager has a Groupe of their own, like a conducteur, and may sign up without naming a conducteur; naming one ("je viens avec") puts them in that conducteur's Groupe. Each Journée, a conducteur takes at most one passager, and the Bureau or the Groupe's Chef de groupe may pair a passager with another conducteur of the Groupe, for instance when theirs is not there that Journée. A conducteur withdrawing, or removed by the Bureau, chooses (the Bureau chooses) whether their passager goes too; one left behind stays signed up, flagged to the Bureau and the Chef de groupe as waiting for a conducteur. A passager rides behind a conducteur only on the Journées both are signed up for, however they were paired. A conducteur and their passager wait on the Liste d'attente and move up together, taking two places. When the Bureau or a Chef de groupe takes a conducteur out of their Groupe or places them in another, they choose whether the passager follows or the pairing ends.

**Point fixe**: the Poste of a person stationed at one place along the Event's route instead of riding; is with no Groupe.

**Chauffeur camionnette**: the Poste of a person driving the van that accompanies the motards, with a Groupe de motards. Takes no passager.

**Service**: the Poste of a person running logistics, the buvette or security at the Event, with a Groupe de service.

**Décharge**: the décharge de responsabilité a minor's legal representative approves when the minor signs up for an Event (under 18 on the first Journée signed up for), for the insurance of minors: the version in force, uploaded by the Super admin, is approved once per Event, and who approved it, when and which version are kept. When the Bureau signs a minor up, the legal representative signs the décharge on paper and the Bureau records it, keeping also which Bureau member did. Until a décharge is uploaded, minors cannot sign up. Signing up needs the person's birth date (and phone number); their e-mail is their account's.
_Avoid_: autorisation parentale, consentement

**Liste d'attente**: the queue of people who asked to take part in a Journée of an Event, or to be with a Groupe at it, after the maximum the Bureau set for that Event (or for that Groupe at that Event) was reached that Journée; a maximum applies to each Journée alike. It also serves as a reserve of riders on the day. Nobody moves up automatically: the Bureau (or, for a Groupe, its Chef de groupe) chooses. Everyone counts towards the maximums, whatever their Poste; a `passager` always follows their `conducteur`.
_Avoid_: waitlist, file d'attente

### Content

**Post**: a news article written by the Bureau of a Secteur, belonging to that Secteur, either `Brouillon` or `Publié`.
_Avoid_: article, actualité

**Media**: a picture or video shown by the site, used in Posts and in the photo gallery. A video may live on an external platform rather than on the site.
