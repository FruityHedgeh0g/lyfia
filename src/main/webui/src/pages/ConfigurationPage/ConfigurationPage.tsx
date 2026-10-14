import React, { useState } from "react";
import { useConfigurations, useUpdateConfiguration } from "../../features/configurations/useConfigurations";
import { NAVBAR_LOGO_CONFIG_NAME, resolveNavbarLogo } from "../../features/configurations/navbarLogo";
import { useMedias } from "../../features/medias/useMedias";
import { mediaOptions } from "../../features/medias/mediaImage";
import AdminCrudList from "../../components/organisms/AdminCrudList/AdminCrudList";
import FormField from "../../components/molecules/FormField/FormField";
import Select from "../../components/atoms/Select/Select";
import Spinner from "../../components/atoms/Spinner/Spinner";
import Button from "../../components/atoms/Button/Button";
import { useCurrentDecharge, useUploadDecharge } from "../../features/decharge/useDecharge";
import { dechargeUrl } from "../../features/decharge/dechargeApi";
import { formatDate } from "../../lib/formatDate";
import styles from "./ConfigurationPage.module.css";

/**
 * La décharge de responsabilité des mineurs (ADR 0011) : la version en vigueur, à relire, et le dépôt d'une nouvelle,
 * un PDF ; les versions précédentes restent, chaque décharge signée gardant celle qui a été approuvée.
 */
const DechargeSection: React.FC = () => {
  const { data: current, isLoading } = useCurrentDecharge();
  const upload = useUploadDecharge();
  const [file, setFile] = useState<File | null>(null);

  return (
    <section className={styles.decharge} aria-label="Décharge des mineurs">
      <h3 className={styles.title}>Décharge des mineurs</h3>
      <p className={styles.hint}>
        Approuvée par le représentant légal d'un mineur à son inscription. Tant qu'aucune n'est déposée, les mineurs ne peuvent pas s'inscrire.
      </p>
      {isLoading ? (
        <Spinner label="Chargement de la décharge..." />
      ) : current ? (
        <p>
          En vigueur :{" "}
          <a href={dechargeUrl(current.documentId)} target="_blank" rel="noreferrer">
            {current.originalFilename}
          </a>
          , déposée le {formatDate(current.uploadedAt)}.
        </p>
      ) : (
        <p className={styles.missing}>Aucune décharge déposée.</p>
      )}
      <div className={styles.upload}>
        <FormField
          label="Nouvelle version (PDF)"
          type="file"
          accept="application/pdf"
          onChange={(e) => setFile((e.target as HTMLInputElement).files?.[0] ?? null)}
        />
        <Button
          type="button"
          label="Déposer"
          disabled={!file || upload.isPending}
          onClick={() => file && upload.mutate(file, { onSuccess: () => setFile(null) })}
        />
      </div>
      {upload.isError && <p className={styles.missing}>La décharge n'a pas pu être déposée : un PDF de 8 Mo au plus est attendu.</p>}
    </section>
  );
};

export const ConfigurationPage: React.FC = () => {
  const { data: configurations, isLoading } = useConfigurations();
  const { data: medias, isLoading: mediasLoading } = useMedias();
  const updateConfiguration = useUpdateConfiguration();

  if (isLoading || mediasLoading) return <Spinner label="Chargement de la configuration..." />;

  const logoOptions = mediaOptions(medias);

  return (
    <>
      <AdminCrudList
        items={configurations ?? []}
        idOf={(config) => config.name}
        display={(config) => {
          if (config.name !== NAVBAR_LOGO_CONFIG_NAME) return { title: config.name, subtitle: config.value };
          const logo = resolveNavbarLogo(config.value, medias);
          return {
            title: config.name,
            subtitle: logo.isDefault ? "Logo par défaut" : logo.alt,
            leading: <img className={styles.thumb} src={logo.src} alt="" />,
          };
        }}
        toDraft={(config) => config.value}
        renderFields={(value, setValue, config) =>
          config?.name === NAVBAR_LOGO_CONFIG_NAME ? (
            <Select label="Logo" value={value} onChange={setValue} options={logoOptions} />
          ) : (
            <FormField label="Valeur" value={value} onChange={(e) => setValue(e.target.value)} required />
          )
        }
        onUpdate={(name, value) => updateConfiguration.mutateAsync({ name, value })}
      />
      <DechargeSection />
    </>
  );
};

export default ConfigurationPage;
