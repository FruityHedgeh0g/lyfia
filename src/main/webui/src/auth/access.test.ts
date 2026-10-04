import { describe, expect, it } from "vitest";
import { AccessContext, AccessId, canAccess, isTurnedOff, mainNav, navFor, relativePath } from "./access";
import { RoleId } from "./roles";
import { FeatureName } from "../features/featureFlags/types";

const ctx = (role: RoleId, inactive: FeatureName[] = []): AccessContext => ({
  role,
  isFeatureActive: (f) => !inactive.includes(f),
});

const ids = (entries: { id: AccessId }[]) => entries.map((e) => e.id);

describe("access map", () => {
  it.each<[AccessId, RoleId, boolean]>([
    ["events", "visiteur", true],
    ["account", "visiteur", false],
    ["account", "benevole", true],
    ["accountProfile", "benevole", true],
    ["accountEvents", "benevole", true],
    ["accountEvents", "visiteur", false],
    ["accountGroup", "membre", false],
    ["accountGroup", "chef_de_groupe", true],
    ["accountGroup", "bureau", true],
    ["accountSector", "chef_de_groupe", false],
    ["accountSector", "bureau", true],
    ["administration", "chef_de_groupe", false],
    ["administration", "bureau", true],
    ["adminSectors", "bureau", false],
    ["adminSectors", "admin", false],
    ["adminSectors", "super_admin", true],
    ["adminPosts", "chef_de_groupe", false],
    ["adminPosts", "bureau", true],
    ["adminConfiguration", "bureau", false],
    ["adminConfiguration", "admin", false],
    ["adminConfiguration", "super_admin", true],
    ["featureRequests", "bureau", true],
    ["featureRequests", "admin", true],
    ["adminFeatureFlags", "admin", false],
    ["adminFeatureFlags", "super_admin", true],
  ])("%s for %s → %s", (id, role, expected) => {
    expect(canAccess(id, ctx(role))).toBe(expected);
  });

  it("hides entries whose feature is inactive, whatever the role", () => {
    expect(canAccess("gallery", ctx("admin", ["galerie-photos"]))).toBe(false);
    expect(canAccess("gallery", ctx("visiteur"))).toBe(true);
  });

  it("lets the Super admin see what is turned off (ADR 0009)", () => {
    expect(canAccess("gallery", { ...ctx("super_admin", ["galerie-photos"]), seesTurnedOff: true })).toBe(true);
    expect(isTurnedOff("gallery", ctx("super_admin", ["galerie-photos"]))).toBe(true);
  });

  it("shows the Support menu to every role that may open its page", () => {
    for (const role of ["bureau", "admin"] as RoleId[]) {
      const labels = mainNav(ctx(role)).map((g) => (g.kind === "menu" ? g.label : g.entry.label));
      expect(labels).toContain("Support");
    }
    const visitorLabels = mainNav(ctx("visiteur")).map((g) => (g.kind === "menu" ? g.label : g.entry.label));
    expect(visitorLabels).toEqual(["Association", "Événements", "Soutenir"]);
  });

  it("groups main entries into menus, in declaration order", () => {
    const groups = mainNav(ctx("admin"));
    const association = groups.find((g) => g.kind === "menu" && g.label === "Association");
    expect(association && association.kind === "menu" && ids(association.entries)).toEqual(["about", "news", "gallery", "contact"]);
  });

  it("lists section tabs by role", () => {
    expect(ids(navFor("account", ctx("benevole")))).toEqual(["accountProfile", "accountEvents"]);
    expect(ids(navFor("account", ctx("chef_de_groupe")))).toEqual(["accountProfile", "accountEvents", "accountGroup"]);
    // Un Chef de groupe qui est aussi au Bureau voit Mon groupe et Mon secteur
    expect(ids(navFor("account", ctx("bureau")))).toEqual(["accountProfile", "accountEvents", "accountGroup", "accountSector"]);
    expect(ids(navFor("admin", ctx("bureau")))).not.toContain("adminConfiguration");
    expect(ids(navFor("admin", ctx("admin")))).toEqual([
      "adminMembers",
      "adminGroups",
      "adminEvents",
      "adminPosts",
      "adminCarousel",
      "adminMedias",
    ]);
  });

  it("derives nested route paths from the map", () => {
    expect(relativePath("accountProfile", "account")).toBe("");
    expect(relativePath("adminCarousel", "administration")).toBe("carrousel");
    expect(() => relativePath("adminCarousel", "account")).toThrow();
  });
});
