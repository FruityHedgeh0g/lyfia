import React from "react";
import PageHero from "../../components/organisms/PageHero/PageHero";
import SectionHeading from "../../components/molecules/SectionHeading/SectionHeading";
import ButtonLink from "../../components/atoms/ButtonLink/ButtonLink";
import Icon, { IconName } from "../../components/atoms/Icon/Icon";
import IfAccess from "../../auth/IfAccess";
import { entry } from "../../auth/access";
import styles from "./AboutPage.module.css";

const values: { title: string; description: string; icon: IconName }[] = [
  {
    title: "Solidarité",
    icon: "heart",
    description: "Chaque collecte, chaque don, chaque bénévole compte pour soutenir les malades et leurs proches.",
  },
  {
    title: "Engagement",
    icon: "hand",
    description: "Des motards mobilisés toute l'année sur le terrain, aux côtés du monde médical et associatif.",
  },
  {
    title: "Proximité",
    icon: "pin",
    description: "Une organisation en secteurs et en groupes locaux, au plus près des besoins du territoire.",
  },
  {
    title: "Convivialité",
    icon: "users",
    description: "Des événements ouverts à tous pour rassembler motards, familles et sympathisants autour d'une même cause.",
  },
];

const actions = [
  "Organisation de collectes solidaires",
  "Reversement des fonds récoltés à la recherche contre le cancer",
  "Actions de mécénat avec les entreprises du territoire",
];

export const AboutPage: React.FC = () => (
  <>
    <PageHero
      eyebrow="L'association"
      title="Qui sommes-nous ?"
      lead="Une Rose Un Espoir est une association loi 1901 basée à Algrange, née de la volonté d'un groupe de motards de mettre leur passion au service de la lutte contre le cancer."
    />

    <section className={styles.story}>
      <div className={styles.storyInner}>
        <SectionHeading eyebrow="Depuis le début" title="Notre histoire" />
        <div className={styles.storyBody}>
          <p>
            Depuis sa création, l'association réunit des motards du bassin d'Algrange et des environs autour d'un objectif
            commun : soutenir la recherche contre le cancer et accompagner les malades et leurs familles. Ce qui a commencé
            comme une balade solidaire entre passionnés est devenu, au fil des années, un mouvement local rassemblant
            bénévoles, chefs de groupe, partenaires et donateurs.
          </p>
          <blockquote className={styles.quote}>
            <p>Une rose, un espoir.</p>
            <footer>Le cœur des motards</footer>
          </blockquote>
        </div>
      </div>
    </section>

    <section className={styles.values}>
      <div className={styles.inner}>
        <SectionHeading eyebrow="Ce qui nous anime" title="Nos valeurs" align="center" />
        <div className={styles.valuesGrid}>
          {values.map((value, i) => (
            <div className={styles.valueCard} key={value.title}>
              <span className={styles.valueIndex} aria-hidden="true">
                {String(i + 1).padStart(2, "0")}
              </span>
              <span className={styles.valueIcon} aria-hidden="true">
                <Icon name={value.icon} size={26} />
              </span>
              <h3 className={styles.valueTitle}>{value.title}</h3>
              <p className={styles.valueDescription}>{value.description}</p>
            </div>
          ))}
        </div>
      </div>
    </section>

    <section className={styles.actions}>
      <span className={styles.actionsRibbon} aria-hidden="true" />
      <div className={styles.actionsInner}>
        <SectionHeading eyebrow="Sur le terrain" title="Nos actions" inverse />
        <ul className={styles.actionsList}>
          {actions.map((action) => (
            <li key={action}>
              <span className={styles.check} aria-hidden="true">
                <Icon name="check" size={16} strokeWidth={3} />
              </span>
              {action}
            </li>
          ))}
        </ul>
      </div>
    </section>

    <section className={styles.cta}>
      <div className={styles.ctaInner}>
        <SectionHeading
          eyebrow="Nous rejoindre"
          title="Envie de nous rejoindre ?"
          lead="Que ce soit pour devenir bénévole, adhérer à l'association ou simplement nous soutenir, chaque geste compte."
          align="center"
        />
        <div className={styles.ctaActions}>
          <IfAccess id="register">
            <ButtonLink to={entry("register").path} label="Devenir membre" variant="accent" arrow />
          </IfAccess>
          <ButtonLink to="/don" label="Faire un don" variant="outline" />
        </div>
      </div>
    </section>
  </>
);

export default AboutPage;
