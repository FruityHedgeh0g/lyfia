/** Minuscules et sans accents, pour comparer « Hélène » et « helene ». */
const fold = (text: string) => text.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();

/** Vrai si chaque mot de la recherche figure dans le texte, sans tenir compte de la casse ni des accents ; une recherche vide trouve tout. */
export const matchesSearch = (text: string, search: string) => {
  const haystack = fold(text);
  return fold(search).split(/\s+/).filter(Boolean).every((word) => haystack.includes(word));
};
