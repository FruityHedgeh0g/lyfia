import { ReactNode } from "react";
import { createBrowserRouter, Navigate, RouteObject } from "react-router-dom";
import PublicLayout from "../components/templates/PublicLayout/PublicLayout";
import AccountLayout from "../components/templates/AccountLayout/AccountLayout";
import AdminLayout from "../components/templates/AdminLayout/AdminLayout";
import RequireAccess from "../auth/RequireAccess";
import { AccessId, entry, relativePath } from "../auth/access";
import HomePage from "../pages/HomePage/HomePage";
import AboutPage from "../pages/AboutPage/AboutPage";
import NewsPage from "../pages/NewsPage/NewsPage";
import NewsDetailPage from "../pages/NewsDetailPage/NewsDetailPage";
import GalleryPage from "../pages/GalleryPage/GalleryPage";
import EventsPage from "../pages/EventsPage/EventsPage";
import EventDetailPage from "../pages/EventDetailPage/EventDetailPage";
import ContactPage from "../pages/ContactPage/ContactPage";
import DonationPage from "../pages/DonationPage/DonationPage";
import LoginPage from "../pages/LoginPage/LoginPage";
import RegisterPage from "../pages/RegisterPage/RegisterPage";
import ProfilePage from "../pages/ProfilePage/ProfilePage";
import MyEventsPage from "../pages/MyEventsPage/MyEventsPage";
import SectorPage from "../pages/SectorPage/SectorPage";
import MonGroupePage from "../pages/MonGroupePage/MonGroupePage";
import MembersAdminPage from "../pages/MembersAdminPage/MembersAdminPage";
import SectorsAdminPage from "../pages/SectorsAdminPage/SectorsAdminPage";
import GroupsAdminPage from "../pages/GroupsAdminPage/GroupsAdminPage";
import PostsAdminPage from "../pages/PostsAdminPage/PostsAdminPage";
import EventsAdminPage from "../pages/EventsAdminPage/EventsAdminPage";
import FeatureRequestsPage from "../pages/FeatureRequestsPage/FeatureRequestsPage";
import CarouselAdminPage from "../pages/CarouselAdminPage/CarouselAdminPage";
import MediasAdminPage from "../pages/MediasAdminPage/MediasAdminPage";
import ConfigurationPage from "../pages/ConfigurationPage/ConfigurationPage";
import FeatureFlagsPage from "../pages/FeatureFlagsPage/FeatureFlagsPage";
import NotFoundPage from "../pages/NotFoundPage/NotFoundPage";

// Aligné sur la base de Vite (quarkus.quinoa.ui-root-path) pour que les liens
// internes et l'historique du navigateur restent cohérents avec le chemin de service.
const basename = import.meta.env.BASE_URL.replace(/\/$/, "") || "/";

/**
 * Route gardée par la carte d'accès (auth/access.ts) : le chemin et les
 * conditions d'accès viennent de l'entrée `id`, relativement à `parent`.
 */
function route(id: AccessId, element: ReactNode, parent: AccessId | null = null, children?: RouteObject[]): RouteObject {
  const guarded = <RequireAccess id={id}>{element}</RequireAccess>;
  const path = parent ? relativePath(id, parent) : entry(id).path.replace(/^\//, "");
  if (path === "") return { index: true, element: guarded };
  return { path, element: guarded, children };
}

export const router = createBrowserRouter(
  [
    {
      path: "/",
      element: <PublicLayout />,
      children: [
        { index: true, element: <HomePage /> },
        route("about", <AboutPage />),
        route("news", <NewsPage />),
        {
          path: "actualites/:postId",
          element: (
            <RequireAccess id="news">
              <NewsDetailPage />
            </RequireAccess>
          ),
        },
        route("gallery", <GalleryPage />),
        route("events", <EventsPage />),
        { path: "evenements/:eventId", element: <EventDetailPage /> },
        route("contact", <ContactPage />),
        route("donation", <DonationPage />),
        { path: "connexion", element: <LoginPage /> },
        route("register", <RegisterPage />),
        route("account", <AccountLayout />, null, [
          route("accountProfile", <ProfilePage />, "account"),
          route("accountEvents", <MyEventsPage />, "account"),
          route("accountGroup", <MonGroupePage />, "account"),
          route("accountSector", <SectorPage />, "account"),
        ]),
        route("administration", <AdminLayout />, null, [
          { index: true, element: <Navigate to={relativePath("adminMembers", "administration")} replace /> },
          route("adminMembers", <MembersAdminPage />, "administration"),
          route("adminSectors", <SectorsAdminPage />, "administration"),
          route("adminGroups", <GroupsAdminPage />, "administration"),
          route("adminEvents", <EventsAdminPage />, "administration"),
          route("adminPosts", <PostsAdminPage />, "administration"),
          route("adminCarousel", <CarouselAdminPage />, "administration"),
          route("adminMedias", <MediasAdminPage />, "administration"),
          route("adminConfiguration", <ConfigurationPage />, "administration"),
          route("adminFeatureFlags", <FeatureFlagsPage />, "administration"),
        ]),
        route("featureRequests", <FeatureRequestsPage />),
        { path: "*", element: <NotFoundPage /> },
      ],
    },
  ],
  { basename }
);

export default router;
