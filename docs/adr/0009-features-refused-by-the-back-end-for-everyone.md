# Features are refused by the back end, for everyone, and checked through a display-only Aperçu

A Feature is an emergency lever (spam, abuse, a bug damaging data), so a Feature that is off is refused by the back end, not only hidden by the site: a lever that a direct API call gets around does not stop anything. Nobody uses what is off, not even the Super admin who pulled the lever: they see it, marked as turned off, and check how the site looks for others through an Aperçu, a read-only view of the site as a given Role (and Secteur) would see it, from which nothing can be done.

## Considered Options

- **Features only hide the UI**, as they first did: rejected for the reason above.
- **The Super admin bypasses every lever**, to try things out before turning a Feature back on: rejected; it would let real data (sign-ups, uploads) be created while the lever is down, which is what pulling it was meant to stop.
- **The Super admin views the site as a given person, or acts as them** (impersonation): rejected; seeing a Role's site is enough to check it, while a person's view exposes their personal data and acting as them blurs who did what.

## Consequences

- Turning Inscription sur le site off also turns off self-registration on the Keycloak realm: hiding the link alone would leave Keycloak's form open.
- Every attempt a Feature refuses is counted in the Journal (and logged in the application logs one by one), so the Super admin knows what the lever is holding back.
