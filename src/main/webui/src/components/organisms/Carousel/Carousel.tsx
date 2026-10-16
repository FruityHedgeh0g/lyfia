import React, { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../../atoms/Icon/Icon";
import buttonStyles from "../../atoms/Button/Button.module.css";
import styles from "./Carousel.module.css";

export interface CarouselSlide {
  src: string;
  alt: string;
  title?: string;
  caption?: string;
  /** Le sur-titre à côté du numéro ; à défaut, « Une rose, un espoir ». */
  kicker?: string;
  /** Chemin interne (route ou ancre "/#section"), résolu via React Router. */
  to?: string;
  /** "contain" pour une image à ne pas recadrer (ex : logo par défaut). */
  fit?: "cover" | "contain";
}

export interface CarouselProps {
  slides: CarouselSlide[];
  autoPlay?: boolean;
  interval?: number; // ms
  pauseOnHover?: boolean;
  showArrows?: boolean;
}

const clampIndex = (idx: number, len: number) => {
  if (len <= 0) return 0;
  const r = idx % len;
  return r < 0 ? r + len : r;
};

const pad = (n: number) => String(n).padStart(2, "0");

/** Le sur-titre d'une diapositive qui n'en a pas. */
export const DEFAULT_KICKER = "Une rose, un espoir";

/** Carrousel plein écran de l'accueil : fondu enchaîné, voile marine, progression par barres. */
export const Carousel: React.FC<CarouselProps> = ({
  slides,
  autoPlay = true,
  interval = 5000,
  pauseOnHover = true,
  showArrows = true,
}) => {
  const [index, setIndex] = useState(0);
  const [paused, setPaused] = useState(false);
  const [focusWithin, setFocusWithin] = useState(false);
  const len = slides.length;
  const delay = Math.max(1800, interval);
  const running = autoPlay && len > 1 && !paused && !focusWithin;

  const goTo = (i: number) => setIndex(clampIndex(i, len));
  const next = () => setIndex((prev) => clampIndex(prev + 1, len));
  const prev = () => setIndex((prev) => clampIndex(prev - 1, len));

  // Le temps déjà passé sur la diapositive : une pause (survol, focus) le garde, comme la barre de progression qui
  // reprend où elle s'était arrêtée ; une nouvelle diapositive repart de zéro
  const elapsed = useRef(0);
  useEffect(() => {
    elapsed.current = 0;
  }, [index]);
  useEffect(() => {
    if (!running) return;
    const started = performance.now();
    const id = window.setTimeout(() => setIndex((i) => clampIndex(i + 1, len)), Math.max(0, delay - elapsed.current));
    return () => {
      window.clearTimeout(id);
      elapsed.current += performance.now() - started;
    };
  }, [running, delay, len, index]);

  const onKeyDown: React.KeyboardEventHandler<HTMLDivElement> = (e) => {
    if (e.key === "ArrowRight") {
      e.preventDefault();
      next();
    } else if (e.key === "ArrowLeft") {
      e.preventDefault();
      prev();
    }
  };

  const touchStartX = useRef<number | null>(null);
  const onTouchStart: React.TouchEventHandler<HTMLDivElement> = (e) => {
    touchStartX.current = e.changedTouches[0].clientX;
  };
  const onTouchEnd: React.TouchEventHandler<HTMLDivElement> = (e) => {
    const start = touchStartX.current;
    if (start == null) return;
    const dx = e.changedTouches[0].clientX - start;
    const threshold = 40; // px
    if (dx > threshold) prev();
    else if (dx < -threshold) next();
    touchStartX.current = null;
  };

  return (
    <section
      className={styles.carousel}
      aria-roledescription="carousel"
      aria-label="Carrousel"
      onMouseEnter={() => pauseOnHover && setPaused(true)}
      onMouseLeave={() => pauseOnHover && setPaused(false)}
      onKeyDown={onKeyDown}
      onFocus={() => setFocusWithin(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setFocusWithin(false);
      }}
    >
      <div
        className={styles.viewport}
        role="group"
        aria-roledescription="slides"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        tabIndex={0}
      >
        {slides.map((s, i) => {
          const active = i === index;
          return (
            <div
              key={i}
              className={`${styles.slide}${active ? ` ${styles.active}` : ""}${s.fit === "contain" ? ` ${styles.contain}` : ""}`}
              role="group"
              aria-roledescription="slide"
              aria-label={`Slide ${i + 1} sur ${len}`}
              aria-hidden={!active}
              inert={!active}
            >
              <img className={styles.image} src={s.src} alt={s.alt} loading={i === 0 ? "eager" : "lazy"} />
              <div className={styles.overlay} aria-hidden="true" />
              {(s.title || s.caption || s.to) && (
                <div className={styles.content}>
                  <p className={styles.kicker}>
                    <span>{pad(i + 1)}</span> {s.kicker || DEFAULT_KICKER}
                  </p>
                  {s.title && <h2 className={styles.title}>{s.title}</h2>}
                  {s.caption && <p className={styles.caption}>{s.caption}</p>}
                  {s.to && (
                    <Link
                      className={`${buttonStyles.button} ${buttonStyles.accent} ${styles.cta}`}
                      to={s.to}
                      aria-label={`${s.title || s.alt} — Voir plus`}
                    >
                      <span className={buttonStyles.content}>
                        <span className={buttonStyles.label}>Voir plus</span>
                        <Icon name="arrowRight" size={18} />
                      </span>
                    </Link>
                  )}
                </div>
              )}
            </div>
          );
        })}

        {len > 1 && (
          <div className={styles.controls}>
            <p className={styles.counter} aria-hidden="true">
              <strong>{pad(index + 1)}</strong> / {pad(len)}
            </p>
            <div className={styles.bars} aria-label="Navigation des slides">
              {slides.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  className={`${styles.bar}${i === index ? ` ${styles.barActive}` : ""}${i < index ? ` ${styles.barDone}` : ""}`}
                  aria-label={`Slide ${i + 1}`}
                  aria-current={i === index ? true : undefined}
                  onClick={() => goTo(i)}
                >
                  <span
                    key={i === index ? `run-${index}` : "idle"}
                    className={styles.progress}
                    style={{ animationDuration: `${delay}ms`, animationPlayState: running ? "running" : "paused" }}
                  />
                </button>
              ))}
            </div>
            {showArrows && (
              <div className={styles.arrows}>
                <button type="button" className={styles.arrow} aria-label="Précédent" onClick={prev}>
                  <Icon name="arrowLeft" size={20} />
                </button>
                <button type="button" className={styles.arrow} aria-label="Suivant" onClick={next}>
                  <Icon name="arrowRight" size={20} />
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
};

export default Carousel;
