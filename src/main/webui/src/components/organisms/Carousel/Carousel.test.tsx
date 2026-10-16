import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Carousel, { CarouselSlide } from "./Carousel";

const slides: CarouselSlide[] = [
  { src: "/a.jpg", alt: "A", title: "Première", kicker: "Édition 2026" },
  { src: "/b.jpg", alt: "B", title: "Deuxième" },
  { src: "/c.jpg", alt: "C", title: "Troisième" },
];

const renderCarousel = () =>
  render(
    <MemoryRouter>
      <Carousel slides={slides} interval={5000} />
    </MemoryRouter>
  );

const current = () => screen.getByRole("button", { current: true });
const advance = (ms: number) => act(() => vi.advanceTimersByTime(ms));

describe("Carousel", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("marks the bar of the slide shown, from the first one", () => {
    renderCarousel();
    expect(current()).toHaveAccessibleName("Slide 1");
    advance(5000);
    expect(current()).toHaveAccessibleName("Slide 2");
  });

  it("keeps the time spent on a slide through a pause, as its bar does", () => {
    renderCarousel();
    const carousel = screen.getByRole("region", { name: "Carrousel" });
    advance(3000);
    fireEvent.mouseEnter(carousel);
    advance(10000);
    expect(current()).toHaveAccessibleName("Slide 1");
    fireEvent.mouseLeave(carousel);
    // Les 2 secondes qui restaient, pas 5 de plus
    advance(2000);
    expect(current()).toHaveAccessibleName("Slide 2");
  });

  it("shows each slide's sur-titre beside its number, « Une rose, un espoir » by default", () => {
    renderCarousel();
    expect(screen.getByText("Édition 2026")).toBeInTheDocument();
    expect(screen.getAllByText("Une rose, un espoir")).toHaveLength(2);
  });
});
