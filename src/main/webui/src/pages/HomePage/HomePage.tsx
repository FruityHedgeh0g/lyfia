import React from "react";
import { Link } from "react-router-dom";
import Carousel, { CarouselSlide } from "../../components/organisms/Carousel/Carousel";
import EventList from "../../components/organisms/EventList/EventList";
import SectionHeading from "../../components/molecules/SectionHeading/SectionHeading";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import Icon, { IconName } from "../../components/atoms/Icon/Icon";
import Spinner from "../../components/atoms/Spinner/Spinner";
import { useActiveCarouselItems } from "../../features/carousel/useCarousel";
import { useMedias } from "../../features/medias/useMedias";
import { DEFAULT_IMAGE_SRC, mediaImage } from "../../features/medias/mediaImage";
import FeatureNotice from "../../components/molecules/FeatureNotice/FeatureNotice";
import { useFeature } from "../../features/featureFlags/useFeatureFlags";
import { useAccess } from "../../auth/useAccess";
import { AccessId, entry } from "../../auth/access";
import { inSecteurChoice, useSecteurChoice } from "../../features/sectors/secteurChoice";
import styles from "./HomePage.module.css";

/** Le carrousel disparaît quand Carrousel est désactivé ; le Super admin le voit, marqué comme tel (ADR 0009). */
const HeroCarousel: React.FC = () => {
  const { ready, seesTurnedOff } = useAccess();
  const carouselOn = useFeature("carrousel");
  if (!ready || (!carouselOn && !seesTurnedOff)) return null;
  return (
    <>
      {!carouselOn && <FeatureNotice feature="carrousel" variant="turned-off" />}
      <HeroSlides />
    </>
  );
};

const HeroSlides: React.FC = () => {
  const { data: activeItems, isLoading: itemsLoading } = useActiveCarouselItems();
  const { sectorId: choice } = useSecteurChoice();
  const items = activeItems?.filter((item) => inSecteurChoice(choice, item.sectorId));
  const { data: medias, isLoading: mediasLoading } = useMedias();

  if (itemsLoading || mediasLoading) {
    return (
      <div className={styles.heroPlaceholder}>
        <Spinner label="Chargement du carrousel..." />
      </div>
    );
  }
  if (!items || items.length === 0) return null;

  const slides: CarouselSlide[] = items.map((item) => {
    const { src, alt, isDefault } = mediaImage(item.mediaId, medias, item.title);
    return {
      src,
      alt,
      title: item.title,
      caption: item.caption,
      to: item.linkTo ?? undefined,
      fit: isDefault ? "contain" : "cover",
    };
  });

  return <Carousel slides={slides} interval={6000} />;
};

interface ActionTile {
  id: string;
  icon: IconName;
  title: string;
  description: string;
  to: string;
  linkLabel: string;
  /** L'entrée de la carte d'accès dont dépend la tuile. */
  access?: AccessId;
}

const actions: ActionTile[] = [
  {
    id: "don",
    icon: "heart",
    title: "Faire un don",
    description:
      "Votre soutien est essentiel. Chaque don contribue directement au financement de la recherche et à l'accompagnement des malades et de leurs proches.",
    to: "/don",
    linkLabel: "Faire un don",
  },
  {
    id: "benevolat",
    icon: "hand",
    title: "Devenir bénévole",
    description:
      "Rejoignez notre équipe de bénévoles engagés. Que ce soit pour les collectes, les événements ou la communication, votre aide compte énormément.",
    to: "/inscription",
    linkLabel: "Je m'engage",
    access: "register",
  },
  {
    id: "contact",
    icon: "mail",
    title: "Contact",
    description: "Une question, une envie de rejoindre l'aventure ? Nous serions ravis de vous répondre.",
    to: "/contact",
    linkLabel: "Nous contacter",
  },
];

export const HomePage: React.FC = () => {
  const { canAccess } = useAccess();
  return (
  <>
    <h1 className="sr-only">Une Rose Un Espoir - Algrange</h1>
    <HeroCarousel />

    <section id="about" className={styles.about}>
      <div className={styles.aboutInner}>
        <div className={styles.aboutText}>
          <SectionHeading eyebrow="L'association" title="Qui sommes-nous ?" />
          <p className={styles.aboutLead}>
            Une Rose Un Espoir est une association loi 1901 basée à Algrange, dédiée à la lutte contre le cancer du sein.
            Nous soutenons les patients, leurs familles, et participons activement aux campagnes de sensibilisation et de
            collecte de fonds pour la recherche.
          </p>
          <ButtonLink to="/qui-sommes-nous" label="En savoir plus" variant="outline" arrow />
        </div>
        <div className={styles.aboutVisual} aria-hidden="true">
          <span className={styles.aboutBlock} />
          <span className={styles.aboutFrame}>
            <img src={DEFAULT_IMAGE_SRC} alt="" />
          </span>
          <p className={styles.aboutQuote}>
            Le cœur
            <br />
            des motards
          </p>
        </div>
      </div>
    </section>

    <section className={styles.act} aria-labelledby="act-title">
      <div className={styles.inner}>
        <SectionHeading
          id="act-title"
          eyebrow="Nous soutenir"
          title="Agir avec nous"
          lead="Donner, s'engager sur le terrain ou simplement nous écrire : chaque geste compte."
          align="center"
        />
        <div className={styles.tiles}>
          {actions.filter((action) => !action.access || canAccess(action.access)).map((action, i) => (
            <article key={action.id} id={action.id} className={styles.tile}>
              <span className={styles.tileIndex} aria-hidden="true">
                {String(i + 1).padStart(2, "0")}
              </span>
              <span className={styles.tileIcon} aria-hidden="true">
                <Icon name={action.icon} size={28} />
              </span>
              <h3 className={styles.tileTitle}>{action.title}</h3>
              <p className={styles.tileText}>{action.description}</p>
              <Link className={styles.tileLink} to={action.to} aria-label={`${action.title} — ${action.linkLabel}`}>
                {action.linkLabel}
                <Icon name="arrowRight" size={18} strokeWidth={2.5} />
              </Link>
            </article>
          ))}
        </div>
      </div>
    </section>

    <section id="events" className={styles.events}>
      <div className={styles.inner}>
        <div className={styles.eventsHeader}>
          <SectionHeading eyebrow="Agenda" title="Événements à venir" />
          <ButtonLink to="/evenements" label="Voir tous les événements" variant="outline" arrow />
        </div>
        <EventList scope="upcoming" limit={3} />
      </div>
    </section>

    <section className={styles.banner} aria-labelledby="banner-title">
      <span className={styles.bannerRibbon} aria-hidden="true" />
      <div className={styles.bannerInner}>
        <p className={styles.bannerEyebrow}>Une rose, un espoir</p>
        <h2 id="banner-title" className={styles.bannerTitle}>
          Tous unis contre le cancer
        </h2>
        <div className={styles.bannerActions}>
          <ButtonLink to="/don" label="Faire un don" variant="accent" arrow />
          {canAccess("register") && <ButtonLink to={entry("register").path} label="Je m'engage" variant="inverse" />}
        </div>
      </div>
    </section>
  </>
  );
};

export default HomePage;
