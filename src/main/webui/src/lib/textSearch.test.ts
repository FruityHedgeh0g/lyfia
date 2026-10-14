import { describe, expect, it } from "vitest";
import { matchesSearch } from "./textSearch";

describe("matchesSearch", () => {
  it("finds everything with an empty search", () => {
    expect(matchesSearch("Jean Dupont", "  ")).toBe(true);
  });

  it("ignores case and accents", () => {
    expect(matchesSearch("Hélène Müller", "HELENE muller")).toBe(true);
  });

  it("needs every word, in any order and partially typed", () => {
    expect(matchesSearch("Jean Dupont · Groupe : Fenrir", "dup jean")).toBe(true);
    expect(matchesSearch("Jean Dupont", "jean martin")).toBe(false);
  });
});
