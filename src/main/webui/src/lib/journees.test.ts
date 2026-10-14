import { describe, expect, it } from "vitest";
import { eventJournees, journeeLabel } from "./journees";

describe("eventJournees", () => {
  it("gives each date from the start to the end", () => {
    expect(eventJournees("2026-05-09T09:00:00", "2026-05-11T18:00:00")).toEqual(["2026-05-09", "2026-05-10", "2026-05-11"]);
  });

  it("gives one Journée to an Event of one day, or without an end", () => {
    expect(eventJournees("2026-05-09T09:00:00", "2026-05-09T18:00:00")).toEqual(["2026-05-09"]);
    expect(eventJournees("2026-05-09T09:00:00", null)).toEqual(["2026-05-09"]);
  });
});

describe("journeeLabel", () => {
  it("names the weekday and the date", () => {
    expect(journeeLabel("2026-05-09")).toBe("samedi 9 mai");
  });
});
