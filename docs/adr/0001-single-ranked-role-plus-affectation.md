# One ranked Role per person; leading a Groupe is an Affectation

Each person holds exactly one Role on the ladder `visiteur < bénévole < membre < chef_de_groupe < bureau < admin < super_admin`, and each Role grants everything below it. Being Chef de groupe is a title held on that ladder and kept between Groupes; *which* Groupe a Chef leads is a separate Affectation made by the Bureau. We chose this over letting Roles stack (e.g. `bureau` + `chef_de_groupe` on one person) because the ladder keeps every access check a single comparison, and the only thing stacking would add — the Groupe someone leads — is carried by the Affectation.

## Considered Options

- **Stacked, independent Roles**: rejected. Every Bureau member would also need `chef_de_groupe` to reach pages the association considers below the Bureau, and access checks would become set membership instead of one comparison.
- **Leadership stored only on the Groupe, no title**: rejected. A Chef de groupe keeps the title while between Groupes so they can be reassigned.

## Consequences

- A Bureau member who holds the Chef de groupe title but leads no Groupe cannot be told apart from any other Bureau member. Accepted: every Bureau member is eligible for an Affectation anyway.
- ~~Roles, Bureau membership and Affectations are not yet scoped to a Secteur, since Algrange is the only one. When a second Secteur opens, Bureau and Admin will be scoped to their Secteur, with the Super admin above all Secteurs.~~ Superseded by ADR 0004: from Membre up, a person belongs to one Secteur now.

## Sitting on the Bureau is not only a Role

An Admin may also be elected to their Secteur's Bureau, and an elected person keeps their election when given or relieved of the site's management. So sitting on the Bureau is a Fonction au Bureau held next to the Role, the way leading a Groupe is an Affectation: Membre du Bureau or Président, with Trésorier, Secrétaire and the like to come. Everyone with the Bureau Role holds one; an Admin holds one only when called to the Bureau by the Super admin or another Admin of the Secteur, never by themselves. The Fonction is kept between the Bureau and Admin Roles, given as Membre du Bureau on reaching the Bureau Role, and lost below it or when an Admin is appointed to another Secteur. It grants no access: access stays a single comparison on the ladder. The Super admin, a dedicated account above every Secteur (ADR 0006), sits on no Bureau.

### Considered Options

- **Every Admin sits on the Bureau**: rejected. An Admin may manage the site without being elected.
- **A Fonction au Bureau independent of the Role**, open to any Membre: rejected. It allows a Bureau member without the Bureau's access, or a Bureau Role off the Bureau.
- **A Président flag on the Bureau Role only** (what the site had): rejected. An elected Admin could not be shown on the Bureau, and promoting a Président to Admin cleared their title.

### Consequences

- The Président flag becomes the Président Fonction; one Président per Secteur still, the previous one staying Membre du Bureau.
- An Admin of a Secteur leads a Groupe of it like any Chef de groupe: nothing new was needed for that.
