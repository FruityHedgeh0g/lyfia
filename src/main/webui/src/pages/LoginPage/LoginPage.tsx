import React from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../../auth/AuthContext";
import Button from "../../components/atoms/Button/Button";
import SplitPanel from "../../components/organisms/SplitPanel/SplitPanel";
import IfAccess from "../../auth/IfAccess";
import { entry } from "../../auth/access";
import forms from "../../theme/forms.module.css";

/** La connexion se fait sur la page de Keycloak, qui renvoie ensuite ici, sur la page demandée (?redirect=). */
export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const [params] = useSearchParams();

  return (
    <SplitPanel
      eyebrow="Espace membres"
      title="Heureux de vous revoir"
      aside={<p>Retrouvez vos inscriptions aux événements, votre profil et, pour les chefs de groupe, la vie de votre secteur.</p>}
      heading="Connexion"
    >
      <p>Vous allez être redirigé vers la page de connexion sécurisée de l'association.</p>
      <div className={forms.actions}>
        <Button type="button" label="Se connecter" variant="accent" onClick={() => login(params.get("redirect") ?? "/")} />
      </div>
      <IfAccess id="register">
        <p className={forms.footer}>
          Pas encore de compte ? <Link to={entry("register").path}>Inscrivez-vous</Link>
        </p>
      </IfAccess>
    </SplitPanel>
  );
};

export default LoginPage;
