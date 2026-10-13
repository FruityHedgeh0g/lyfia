# The Super admin is appointed by configuration, at every startup

Only the Super admin appoints Admins and opens Secteurs, and nobody can appoint the Super admin, so a fresh deployment has no way in. The deployment names the Super admin by their Keycloak username (`LYFIA_SUPER_ADMIN`); at every startup the application looks them up in Keycloak, creates their row if Keycloak never announced them, makes them Super admin outside any Secteur and mirrors the Role to Keycloak (ADR 0002). It never stops the application: if Keycloak cannot answer, it logs and tries again at the next startup.

## Considered Options

- **A Flyway migration inserting the Super admin**: rejected. The row needs that person's Keycloak id, which belongs to one deployment and would ship in a public repository and image; a migration runs once, so a wrong value could not be fixed by redeploying; and it would not run on H2 in dev.
- **At the person's first login**, from the names in their token: rejected for now. It needs the same configuration, and puts a special case in every authentication instead of once at startup.

## Consequences

- Changing `LYFIA_SUPER_ADMIN` appoints another person but does not demote the previous one: that is done from the site.
- A person appointed this way who later registers through Keycloak already has a row; the "user created" event for them is rejected as a duplicate, which is harmless.
- The Super admin is a dedicated account, kept apart from the personal account of whoever holds it: that person rides, leads a Groupe or sits on the Bureau through their own account, as an Admin of their Secteur at most, so the Super admin stays above every Secteur (ADR 0004). Moving `LYFIA_SUPER_ADMIN` from a personal account to a dedicated one leaves the personal account Super admin until it is set back to Admin from the site.
