import React from "react";
import { Outlet } from "react-router-dom";
import Header from "../../organisms/Header/Header";
import PreviewBar from "../../organisms/PreviewBar/PreviewBar";
import Footer from "../../organisms/Footer/Footer";
import BackToTop from "../../molecules/BackToTop/BackToTop";
import { useScrollToHash } from "../../../app/useScrollToHash";
import styles from "./PublicLayout.module.css";

export const PublicLayout: React.FC = () => {
  useScrollToHash();

  return (
    <div className={styles.layout}>
      <a className="skip-link" href="#main-content">
        Aller au contenu
      </a>
      <PreviewBar />
      <Header />
      <main id="main-content" className={styles.main}>
        <Outlet />
      </main>
      <Footer />
      <BackToTop />
    </div>
  );
};

export default PublicLayout;
