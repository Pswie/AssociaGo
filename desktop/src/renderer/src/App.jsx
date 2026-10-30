import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { associago } from './api';
import { pagesForRole, isPersonalOnly } from './roles';

// Layout Components
import LeftNav from "./components/LeftNav";
import TopHeader from "./components/TopHeader";
import ProfileMenu from "./components/ProfileMenu";
import CommandPalette from "./components/CommandPalette";
import NotificationBell from "./components/NotificationBell";
import LoginPage from './components/LoginPage';
import SplashScreen from './components/SplashScreen';
import Modal from './components/Modal';
import ErrorBoundary from './components/ErrorBoundary';
import ResponsiveTables from './components/ResponsiveTables';

// Componenti originali AssociaGo: caricamento statico, senza splash col logo
// durante il passaggio da una voce del menu all'altra.
import PersonalArea from './components/PersonalArea';
import MemberList from './components/MemberList';
import Dashboard from './components/Dashboard';
import ActivityList from './components/ActivityList';
import EventList from './components/EventList';
import FiscalDashboard from './components/FiscalDashboard';
import SettingsDashboard from './components/SettingsDashboard';
import AssemblyList from './components/AssemblyList';
import InventoryList from './components/InventoryList';
import VolunteerList from './components/VolunteerList';
import AttendanceList from './components/AttendanceList';
import CouponList from './components/CouponList';
import ExpenseDocumentList from './components/ExpenseDocumentList';
import SelettoreAnno from './components/SelettoreAnno';
import { leggiAnnoSalvato, salvaAnno } from './esercizio';
import ManualDashboard from './components/ManualDashboard';
import BudgetDashboard from './components/BudgetDashboard';
import AnnualBalanceDashboard from './components/AnnualBalanceDashboard';
import CertificateList from './components/CertificateList';
import CommunicationDashboard from './components/CommunicationDashboard';
import ProjectsDashboard from './components/ProjectsDashboard';

import MemberForm from './components/MemberForm';
import ActivityForm from './components/ActivityForm';
import ActivityCostForm from './components/ActivityCostForm';
import ActivityInstructorForm from './components/ActivityInstructorForm';
import ActivityScheduleForm from './components/ActivityScheduleForm';
import ActivityParticipantForm from './components/ActivityParticipantForm';
import EventForm from './components/EventForm';
import EventParticipantForm from './components/EventParticipantForm';
import AssemblyForm from './components/AssemblyForm';
import AssemblyMotionForm from './components/AssemblyMotionForm';
import AssemblyDocumentForm from './components/AssemblyDocumentForm';
import AssemblyParticipantForm from './components/AssemblyParticipantForm';
import InventoryForm from './components/InventoryForm';
import InventoryLoanForm from './components/InventoryLoanForm';
import VolunteerForm from './components/VolunteerForm';
import VolunteerShiftForm from './components/VolunteerShiftForm';
import VolunteerExpenseForm from './components/VolunteerExpenseForm';
import CouponForm from './components/CouponForm';

/**
 * @param {object} [props.webSession] Presente solo sul web: associazione già
 *   risolta e utente Clerk già autenticato da WebAuthGate. Quando c'è, la
 *   schermata di login interna viene saltata — sul web il suo endpoint è
 *   chiuso e resterebbe comunque una porta senza serratura.
 */
function App({ webSession }) {
  const { t, i18n } = useTranslation();

  const [appState, setAppState] = useState('LOADING');
  const [requestedSuiteProjectId, setRequestedSuiteProjectId] = useState(() => new URLSearchParams(window.location.search).get('suiteProject'));
  const [activeId, setActiveId] = useState(() => new URLSearchParams(window.location.search).has('suiteProject') ? 'projects' : 'dashboard');
  const [pageRevision, setPageRevision] = useState(0);

  const [currentAssociation, setCurrentAssociation] = useState(null);
  const [currentUser, setCurrentUser] = useState(null);

  const [commandPaletteOpen, setCommandPaletteOpen] = useState(false);
  const [theme, setTheme] = useState('light');
  const [modalOpen, setModalOpen] = useState(false);
  const [modalType, setModalType] = useState(null);
  const [modalProps, setModalProps] = useState({});

  // Sotto i 992px il binario di navigazione è un cassetto sovrapposto. Lo stato
  // sta qui e non dentro LeftNav perché il pulsante che lo apre vive nella
  // barra in alto: due componenti sullo stesso interruttore.
  const [navOpen, setNavOpen] = useState(false);

  // L'esercizio in vista. Ricordato fra una sessione e l'altra: chi sta
  // sistemando il 2026 non vuole ritrovarsi sul 2027 a ogni riavvio.
  const [anno, setAnno] = useState(leggiAnnoSalvato);
  const cambiaAnno = useCallback((nuovo) => {
    setAnno(nuovo);
    salvaAnno(nuovo);
  }, []);

  // Sorvegliato da ResponsiveTables per etichettare le celle delle tabelle.
  const contentRef = useRef(null);

  // Toast state
  const [toast, setToast] = useState(null);

  const selectSuiteProject = useCallback((id) => {
    const url = new URL(window.location.href);
    if (id === null) url.searchParams.delete('suiteProject');
    else url.searchParams.set('suiteProject', id);
    window.history.replaceState({}, '', url);
    setRequestedSuiteProjectId(id);
  }, []);

  useEffect(() => {
    const restore = () => {
      const query = new URLSearchParams(window.location.search);
      setRequestedSuiteProjectId(query.get('suiteProject'));
      if (query.has('suiteProject')) setActiveId('projects');
    };
    window.addEventListener('popstate', restore);
    return () => window.removeEventListener('popstate', restore);
  }, []);

  useEffect(() => {
    initApp();
    const handleKeyDown = (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
            e.preventDefault();
            setCommandPaletteOpen(true);
        }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  useEffect(() => {
    // Load preferences
    const prefs = associago.getPreferences();
    if (prefs.language) i18n.changeLanguage(prefs.language);
    if (prefs.theme) setTheme(prefs.theme);
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute('data-bs-theme', theme);
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.classList.toggle('dark', theme === 'dark');
  }, [theme]);

  // Il cassetto copre la pagina: lasciarlo aperto passando da una voce
  // all'altra nasconderebbe proprio la schermata appena scelta.
  useEffect(() => {
    setNavOpen(false);
  }, [activeId]);

  const initApp = async () => {
    try {
      const { baseUrl } = await associago.init();
      console.log('API Ready:', baseUrl);

      if (webSession) {
        // Sul web l'accesso è già avvenuto tramite Clerk e l'associazione è già
        // stata risolta: si entra direttamente in dashboard.
        const { association, user, role, localUserId } = webSession;
        associago.setCurrentAssociation(association.id);
        setCurrentAssociation(association);
        setCurrentUser({
          id: localUserId,
          clerkId: user?.id,
          displayName: user?.fullName || user?.primaryEmailAddress?.emailAddress || association.nome || association.name,
          username: user?.primaryEmailAddress?.emailAddress ?? '',
          role: role ?? user?.publicMetadata?.role ?? 'socio'
        });
        setAppState('DASHBOARD');
        return;
      }

      setAppState('LOGIN');
    } catch (err) {
      console.error("Init failed:", err);
      setTimeout(initApp, 2000);
    }
  };

  const handleLogin = (association) => {
    associago.setCurrentAssociation(association.id);
    setCurrentAssociation(association);

    setCurrentUser({
        id: association.id,
        displayName: association.nome || association.name,
        username: association.email,
        role: 'ADMIN'
    });

    setAppState('DASHBOARD');
  };

  const handleLogout = async () => {
    try {
      if (webSession?.signOut) {
        await webSession.signOut();
        return;
      }
      await associago.logout();
      associago.setPreferences({ autologin: null });
      associago.setCurrentAssociation(null);
      associago.setAuthToken(null);
      setCurrentAssociation(null);
      setCurrentUser(null);
      setActiveId('dashboard');
      closeModal();
      setAppState('LOGIN');
    } catch (error) {
      setToast({ type: 'error', message: error.message || t('Unable to log out. Please try again.') });
    }
  };

  const savePreferences = () => {
      associago.setPreferences({
          language: i18n.language,
          theme: theme
      });
      setToast({ type: 'success', message: t('Preferences saved successfully.') });
      setTimeout(() => setToast(null), 3000);
  };

  const openModal = (type, props = {}) => {
      setModalType(type);
      setModalProps(props);
      setModalOpen(true);
  };

  const closeModal = () => {
      setModalOpen(false);
      setModalType(null);
      setModalProps({});
  };

  const renderModalContent = () => {
      const commonProps = { ...modalProps, onCancel: closeModal, onSuccess: () => { modalProps.onSuccess?.(); closeModal(); } };
      switch (modalType) {
          case 'member-form': return <MemberForm {...commonProps} />;
          case 'activity-form': return <ActivityForm {...commonProps} />;
          case 'activity-cost-form': return <ActivityCostForm {...commonProps} />;
          case 'activity-instructor-form': return <ActivityInstructorForm {...commonProps} />;
          case 'activity-schedule-form': return <ActivityScheduleForm {...commonProps} />;
          case 'activity-participant-form': return <ActivityParticipantForm {...commonProps} />;
          case 'event-form': return <EventForm {...commonProps} />;
          case 'event-participant-form': return <EventParticipantForm {...commonProps} />;
          case 'assembly-form': return <AssemblyForm {...commonProps} />;
          case 'assembly-motion-form': return <AssemblyMotionForm {...commonProps} />;
          case 'assembly-document-form': return <AssemblyDocumentForm {...commonProps} />;
          case 'assembly-participant-form': return <AssemblyParticipantForm {...commonProps} />;
          case 'inventory-form': return <InventoryForm {...commonProps} />;
          case 'inventory-loan-form': return <InventoryLoanForm {...commonProps} />;
          case 'volunteer-form': return <VolunteerForm {...commonProps} />;
          case 'volunteer-shift-form': return <VolunteerShiftForm {...commonProps} />;
          case 'volunteer-expense-form': return <VolunteerExpenseForm {...commonProps} />;
          case 'coupon-form': return <CouponForm {...commonProps} />;
          default: return null;
      }
  };

  const PAGES = useMemo(() => [
    { id: "dashboard", label: t('nav.dashboard'), icon: "bi-speedometer2", component: Dashboard },
    { id: "members", label: t('nav.members'), icon: "bi-people", component: MemberList },
    { id: "activities", label: t('nav.activities'), icon: "bi-calendar3", component: ActivityList },
    { id: "events", label: t('nav.events'), icon: "bi-calendar3", component: EventList },
    { id: "projects", label: 'Progetti', icon: "bi-card-checklist", component: ProjectsDashboard },
    { id: "fiscal", label: t('nav.fiscal'), icon: "bi-credit-card", component: FiscalDashboard },
    // Le fatture ricevute: il backend le gestiva da sempre — elenco, scadenze,
    // importazione della fattura elettronica — e non c'era nessuna voce da cui
    // arrivarci.
    { id: "expense-documents", label: t('nav.expenseDocuments'), icon: "bi-receipt", component: ExpenseDocumentList },
    { id: "assemblies", label: t('nav.assemblies'), icon: "bi-file-text", component: AssemblyList },
    { id: "inventory", label: t('nav.inventory'), icon: "bi-box-seam", component: InventoryList },
    { id: "volunteers", label: t('nav.volunteers'), icon: "bi-person-check", component: VolunteerList },
    { id: "attendance", label: t('nav.attendance'), icon: "bi-check2-square", component: AttendanceList },
    { id: "coupons", label: t('nav.coupons'), icon: "bi-ticket-perforated", component: CouponList },
    { id: "budget", label: t('nav.budget'), icon: "bi-piggy-bank", component: BudgetDashboard },
    { id: "balance", label: t('nav.balance'), icon: "bi-bar-chart", component: AnnualBalanceDashboard },
    { id: "certificates", label: t('nav.certificates'), icon: "bi-award", component: CertificateList },
    { id: "communications", label: t('nav.communications'), icon: "bi-envelope", component: CommunicationDashboard },
    //{ id: "resources", label: t('nav.resources'), icon: "bi-door-open", component: ResourceBookingDashboard },
    //{ id: "cash-register", label: t('nav.cashRegister'), icon: "bi-banknote", component: CashRegisterDashboard },
    //{ id: "csv-import", label: t('nav.csvImport'), icon: "bi-upload", component: CsvImportDashboard },
    { id: "manual", label: t('User Manual'), icon: "bi-book", component: ManualDashboard },
    { id: "settings", label: t('nav.settings'), icon: "bi-gear", component: SettingsDashboard },
  ], [t]);

  /**
   * Le voci che questo ruolo può vedere.
   *
   * Nel desktop il ruolo è sempre ADMIN (accesso con master password su
   * macchina locale) e resta tutto visibile. Sul web il ruolo arriva da Clerk
   * e il menu si restringe: il presidente vede tutto, il referente volontari
   * solo la sua parte, un socio niente — ha l'area personale.
   *
   * Nascondere non è proteggere: la vera barriera è il 403 del backend.
   */
  const visiblePages = useMemo(() => {
    if (!webSession) return PAGES;
    return pagesForRole(PAGES, currentUser?.role);
  }, [PAGES, webSession, currentUser?.role]);

  const activePage = visiblePages.find(p => p.id === activeId)
      || visiblePages[0]
      || PAGES.find(p => p.id === activeId)
      || PAGES[0];

  const shell = useMemo(() => ({
    navigate: setActiveId,
    pages: visiblePages,
    refreshPage: () => setPageRevision(revision => revision + 1),
    openModal,
    closeModal,
    currentUser,
    currentAssociation,
    currentAssociationId: currentAssociation?.id, // Fix for SettingsDashboard
    requestedSuiteProjectId,
    selectSuiteProject,
    /*
     * L'esercizio che si sta guardando.
     *
     * Sta nella shell e non in ogni schermata perché è una lente unica: le
     * schermate lo leggono da qui e filtrano di conseguenza. La regola di cosa
     * appartiene a un anno — e perché ciò che è ancora in corso non si archivia
     * — sta in `esercizio.js`.
     */
    anno,
    theme,
    setTheme,
    savePreferences // Expose savePreferences to shell
  }), [currentUser, currentAssociation, visiblePages, anno, theme, i18n.language, requestedSuiteProjectId, selectSuiteProject]);

  const profileItems = [
    { id: "settings", label: t('nav.settings'), icon: "bi-gear", onClick: () => setActiveId("settings") },
    { id: "info", label: t('App Info'), icon: "bi-info-circle" },
    { type: "sep" },
    { id: "logout", label: t('Logout'), icon: "bi-box-arrow-right", danger: true, onClick: handleLogout }
  ];

  if (appState === 'LOADING') return <SplashScreen message={t('app.loading')} />;
  // Sul web la schermata di login interna non deve comparire mai: l'accesso è
  // di Clerk e il suo endpoint lato backend è chiuso.
  if (appState === 'LOGIN' && webSession) return <SplashScreen message={t('app.loading')} />;
  if (appState === 'LOGIN') return <LoginPage onLogin={handleLogin} />;

  // Soci e volontari non entrano nel gestionale: la loro schermata è un'altra.
  // Il backend risponderebbe comunque 403 su tutto il resto.
  if (webSession && isPersonalOnly(currentUser?.role)) {
    return <>{requestedSuiteProjectId !== null && <div className="alert alert-warning m-3" role="alert">Il tuo ruolo non consente di aprire il progetto richiesto. Chiedi al responsabile di verificare i permessi.</div>}<PersonalArea /></>;
  }

  if (webSession && visiblePages.length === 0) {
    return <main className="p-4"><div className="alert alert-warning" role="alert">Il ruolo di questo accesso non consente di aprire il gestionale. Chiedi al responsabile di verificare i permessi.</div><button type="button" className="btn btn-outline-secondary" onClick={handleLogout}>Esci</button></main>;
  }

  const PageComponent = activePage.component;

  return (
    <div className="jl-root">
      <div className="jl-appframe">
        <div className="jl-shell">
            <CommandPalette shell={shell} isOpen={commandPaletteOpen} onClose={() => setCommandPaletteOpen(false)} />
            <LeftNav
                items={visiblePages}
                activeId={activeId}
                onSelect={setActiveId}
                open={navOpen}
                onClose={() => setNavOpen(false)}
            />
            <main className="jl-main">
                <TopHeader
                    title={activePage.label}
                    navOpen={navOpen}
                    onMenuToggle={() => setNavOpen((open) => !open)}
                    actions={
                        <>
                            {/* L'anno sta nella testata perché è una lente su
                                tutto: cambiarlo in contabilità e ritrovarsi un
                                altro esercizio nelle assemblee sarebbe il modo
                                più rapido di leggere i numeri sbagliati
                                credendoli giusti. */}
                            <SelettoreAnno
                                anno={anno}
                                onChange={cambiaAnno}
                                associationId={currentAssociation?.id}
                            />
                            <NotificationBell userId={currentUser?.id} />
                            <ProfileMenu
                                initials={currentAssociation?.nome?.substring(0, 2)?.toUpperCase() || "AG"}
                                title={currentAssociation?.nome}
                                subtitle={currentUser?.username}
                                items={profileItems}
                            />
                        </>
                    }
                />
                <section className="jl-content" ref={contentRef}>
                    <div className="jl-page p-3 p-lg-4">
                        {requestedSuiteProjectId !== null && !visiblePages.some(page => page.id === 'projects') && (
                            <div className="alert alert-warning" role="alert">Il ruolo di questo accesso non consente di aprire il progetto richiesto. Chiedi al responsabile di verificare i permessi.</div>
                        )}
                        {webSession && !currentUser?.id && !isPersonalOnly(webSession.role) && (
                            <div className="alert alert-warning" role="status">
                                Questo accesso non è collegato a un&apos;anagrafica. Chiedi al referente di verificare la tua email: invii e approvazioni richiedono il collegamento.
                            </div>
                        )}
                        <ErrorBoundary key={`${activeId}-${pageRevision}`}>
                            <PageComponent shell={shell} associationId={currentAssociation?.id} />
                        </ErrorBoundary>
                    </div>
                </section>
                <ResponsiveTables rootRef={contentRef} />
            </main>
        </div>
      </div>
      <Modal isOpen={modalOpen} onClose={closeModal} title={t('Edit Item')}>
          {renderModalContent()}
      </Modal>

      {/* Global Toast Notification */}
      {toast && (
        <div className="position-fixed bottom-0 end-0 p-3" style={{ zIndex: 1100 }}>
            <div className={`toast show align-items-center text-white bg-${toast.type === 'error' ? 'danger' : 'success'} border-0`} role="alert" aria-live="assertive" aria-atomic="true">
                <div className="d-flex">
                    <div className="toast-body">
                        {toast.message}
                    </div>
                    <button type="button" className="btn-close btn-close-white me-2 m-auto" onClick={() => setToast(null)} aria-label="Close"></button>
                </div>
            </div>
        </div>
      )}

      <style>{`
        /* height: 100dvh e non 100vh — su iOS e Android la barra degli indirizzi
           si ritira scorrendo, e con vh l'altezza resta quella a barra estesa:
           il fondo dell'applicazione finisce sotto il bordo dello schermo e la
           pagina sobbalza a ogni scorrimento. dvh segue il viewport reale.
           La riga con vh resta prima come ripiego per i browser che non lo
           conoscono (nessuno di quelli in uso, ma costa una riga).

           width: 100vw era un errore anche su desktop: vw conta la barra di
           scorrimento, quindi la pagina risultava sempre più larga del suo
           contenitore e compariva uno scorrimento orizzontale fantasma. */
        .jl-root {
          height: 100vh;
          height: 100dvh;
          width: 100%;
          overflow: hidden;
          background-color: var(--bs-body-bg);
          color: var(--bs-body-color);
        }
        .jl-appframe { height: 100%; display: flex; flex-direction: column; }
        .jl-shell { display: flex; flex: 1; min-height: 0; overflow: hidden; }
        .jl-main { flex: 1; display: flex; flex-direction: column; min-width: 0; height: 100%; }
        .jl-content {
          flex: 1;
          overflow-y: auto;
          position: relative;
          /* Arrivato in fondo alla lista, il gesto non deve proseguire
             trascinando la pagina che sta sotto (o l'aggiornamento a strappo
             del browser). */
          overscroll-behavior-y: contain;
          -webkit-overflow-scrolling: touch;
        }
        .jl-page { max-width: 1600px; margin: 0 auto; width: 100%; }

        @media (max-width: 767.98px) {
          /* p-4 sono 1.5rem per lato: su 390px di schermo è il 15% della
             larghezza speso in margine. La classe resta nel markup, qui la si
             riduce — !important perché le utility di Bootstrap la difendono. */
          .jl-page.p-4 {
            padding: 1rem !important;
            padding-left: max(1rem, env(safe-area-inset-left)) !important;
            padding-right: max(1rem, env(safe-area-inset-right)) !important;
            padding-bottom: max(1.5rem, env(safe-area-inset-bottom)) !important;
          }
        }

        @media (max-width: 380px) {
          .jl-page.p-4 {
            padding: 0.75rem !important;
          }
        }
      `}</style>
    </div>
  );
}

export default App;
