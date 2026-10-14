/** Une Journée : une date du calendrier (`AAAA-MM-JJ`), comme l'API l'échange (ADR 0011). */
export type Journee = string;

const DAY = 86_400_000;
const labelFormatter = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long", timeZone: "UTC" });

/** Les Journées d'un Événement : chaque date de son début à sa fin, comme EventEntity.journees. */
export function eventJournees(startDateTime: string, endDateTime?: string | null): Journee[] {
  const first = Date.parse(`${startDateTime.slice(0, 10)}T00:00:00Z`);
  const last = Date.parse(`${(endDateTime || startDateTime).slice(0, 10)}T00:00:00Z`);
  if (Number.isNaN(first) || Number.isNaN(last)) return [];
  const journees: Journee[] = [];
  for (let day = first; day <= last; day += DAY) journees.push(new Date(day).toISOString().slice(0, 10));
  return journees;
}

/** « samedi 10 mai ». */
export const journeeLabel = (journee: Journee) => labelFormatter.format(new Date(`${journee}T00:00:00Z`));
