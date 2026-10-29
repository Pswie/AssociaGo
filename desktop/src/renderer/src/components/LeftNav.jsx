import React, { useState, useEffect, useMemo, useRef } from "react";
import { useTranslation } from 'react-i18next';
import {
    LayoutDashboard,
    Users,
    Calendar,
    CreditCard,
    Settings,
    ChevronLeft,
    ChevronRight,
    BookOpen,
    ClipboardList,
    Package,
    UserCheck,
    FileText,
    PiggyBank,
    Award,
    Upload,
    Mail,
    DoorOpen,
    Banknote,
    BarChart3,
    CheckSquare,
    X,
    BookOpen as BookOpenIcon
} from "lucide-react";
import AppIcon from "../assets/6.svg";

/**
 * Binario di navigazione.
 *
 * Sopra i 992px è la colonna fissa di sempre, con il suo pulsante per
 * restringerla a sole icone. Sotto, esce dal flusso e diventa un cassetto che
 * scorre sopra il contenuto: 260px fissi su un telefono da 390 sono i due
 * terzi dello schermo occupati da un menu.
 *
 * Apertura e chiusura le governa `App.jsx`, non questo componente: il pulsante
 * che apre sta nella barra in alto, e due stati separati per la stessa cosa si
 * disallineano al primo caso limite.
 *
 * @param {object[]} props.items Voci visibili per il ruolo corrente.
 * @param {string}   props.activeId Voce attiva.
 * @param {function} props.onSelect Chiamata con l'id della voce scelta.
 * @param {boolean}  props.open Cassetto aperto (ha effetto solo sotto i 992px).
 * @param {function} props.onClose Richiesta di chiusura (scelta, sfondo, Esc).
 */
export default function LeftNav({ items, activeId, onSelect, open = false, onClose }) {
    const { t } = useTranslation();
    const [collapsed, setCollapsed] = useState(false);
    const [appVersion, setAppVersion] = useState('');
    const panelRef = useRef(null);

    useEffect(() => {
        if (window.api?.getAppVersion) {
            window.api.getAppVersion().then(v => setAppVersion(v));
        }
    }, []);

    // Esc chiude il cassetto: è la scorciatoia che ogni utente prova per prima
    // quando un pannello copre la pagina.
    useEffect(() => {
        if (!open) return undefined;
        const onKeyDown = (event) => {
            if (event.key === 'Escape') onClose?.();
        };
        document.addEventListener('keydown', onKeyDown);
        return () => document.removeEventListener('keydown', onKeyDown);
    }, [open, onClose]);

    // Con il cassetto aperto la pagina sotto non deve scorrere: su iOS, senza
    // questo, il dito che scorre sul menu trascina il contenuto retrostante.
    useEffect(() => {
        if (!open) return undefined;
        const previous = document.body.style.overflow;
        document.body.style.overflow = 'hidden';
        return () => { document.body.style.overflow = previous; };
    }, [open]);

    // All'apertura il fuoco entra nel cassetto, altrimenti chi naviga da
    // tastiera continuerebbe a tabulare nella pagina che il menu sta coprendo.
    //
    // Il doppio rinvio non è prudenza. Da chiuso il pannello è
    // `visibility: hidden`, e `focus()` su un elemento invisibile viene
    // ignorato senza errore. L'effetto parte subito dopo la modifica del DOM e
    // un solo `requestAnimationFrame` non basta: le sue chiamate girano
    // *prima* del ricalcolo degli stili di quel fotogramma, quindi il pannello
    // risulterebbe ancora invisibile. Il secondo cade a ricalcolo avvenuto.
    useEffect(() => {
        if (!open) return undefined;
        let inner = 0;
        const outer = requestAnimationFrame(() => {
            inner = requestAnimationFrame(() => {
                panelRef.current?.querySelector('.jl-nav-link')?.focus();
            });
        });
        return () => {
            cancelAnimationFrame(outer);
            if (inner) cancelAnimationFrame(inner);
        };
    }, [open]);

    const visibleItems = useMemo(() => items, [items]);

    const renderIcon = (iconName) => {
        switch (iconName) {
            case "bi-speedometer2": return <LayoutDashboard size={20} />;
            case "bi-people": return <Users size={20} />;
            case "bi-calendar3": return <Calendar size={20} />;
            case "bi-credit-card": return <CreditCard size={20} />;
            case "bi-gear": return <Settings size={20} />;
            case "bi-card-checklist": return <ClipboardList size={20} />;
            case "bi-journal-text": return <BookOpen size={20} />;
            case "bi-box-seam": return <Package size={20} />;
            case "bi-person-check": return <UserCheck size={20} />;
            case "bi-file-text": return <FileText size={20} />;
            case "bi-piggy-bank": return <PiggyBank size={20} />;
            case "bi-award": return <Award size={20} />;
            case "bi-upload": return <Upload size={20} />;
            case "bi-mail": return <Mail size={20} />;
            case "bi-door-open": return <DoorOpen size={20} />;
            case "bi-banknote": return <Banknote size={20} />;
            case "bi-bar-chart": return <BarChart3 size={20} />;
            case "bi-check2-square": return <CheckSquare size={20} />;
            case "bi-ticket-perforated": return <CreditCard size={20} />;
            case "bi-envelope": return <Mail size={20} />;
            case "bi-book": return <BookOpenIcon size={20} />;
            default: return <LayoutDashboard size={20} />;
        }
    };

    const handleSelect = (id) => {
        onSelect(id);
        // Sul telefono il cassetto copre la pagina: lasciarlo aperto dopo la
        // scelta significa nascondere proprio ciò che si è appena aperto.
        onClose?.();
    };

    return (
        <>
            {/* Lo sfondo esiste solo da cassetto aperto: sopra i 992px il CSS lo
                tiene comunque fuori gioco. */}
            <div
                className={`jl-sidebar-backdrop ${open ? "show" : ""}`}
                onClick={onClose}
                aria-hidden="true"
            />

            <nav
                id="jl-primary-nav"
                ref={panelRef}
                className={`jl-sidebar ${collapsed ? "collapsed" : ""} ${open ? "open" : ""}`}
                aria-label={t('Navigation')}
            >
                {/* Logo Header */}
                <div className="jl-sidebar-header">
                    <div className="jl-logo">
                        <img src={AppIcon} alt="Angeli del Futuro" className="jl-logo-icon-img" />
                        {!collapsed && <span className="jl-logo-text">Angeli del Futuro</span>}
                    </div>

                    {/* Chiusura esplicita: sul telefono il gesto di trascinamento
                        non è ovvio e toccare fuori non lo scopre nessuno. */}
                    <button
                        type="button"
                        className="jl-drawer-close"
                        onClick={onClose}
                        aria-label={t('Close')}
                    >
                        <X size={22} />
                    </button>
                </div>

                {/* Navigation Items - Flex Grow per occupare spazio */}
                <div className="jl-nav-container">
                    <ul className="jl-nav-list">
                        {visibleItems.map((item) => (
                            <li key={item.id} className="jl-nav-item">
                                <button
                                    className={`jl-nav-link ${activeId === item.id ? "active" : ""}`}
                                    onClick={() => handleSelect(item.id)}
                                    title={collapsed ? item.label : undefined}
                                    aria-label={item.label}
                                    aria-current={activeId === item.id ? "page" : undefined}
                                >
                                    <span className="jl-nav-icon-wrapper">
                                        {renderIcon(item.icon)}
                                    </span>
                                    {!collapsed && <span className="jl-nav-label">{item.label}</span>}
                                </button>
                            </li>
                        ))}
                    </ul>
                </div>

                {/* Footer / Collapse Toggle */}
                <div className="jl-sidebar-footer">
                    <button
                        className="jl-collapse-btn"
                        onClick={() => setCollapsed(!collapsed)}
                        title={collapsed ? t("More") : t("Less")}
                        aria-label={collapsed ? t('Expand navigation') : t('Collapse navigation')}
                        aria-expanded={!collapsed}
                    >
                        {collapsed ? <ChevronRight size={18} /> : <ChevronLeft size={18} />}
                    </button>

                    {!collapsed && (
                        <div className="jl-sidebar-branding">
                            <small className="text-muted opacity-75">AssociaGo {appVersion ? `v${appVersion}` : ''}</small>
                        </div>
                    )}
                </div>

                <style>{`
                    .jl-sidebar {
                        display: flex;
                        flex-direction: column;
                        width: 260px;
                        min-width: 260px;
                        /* Il valore dentro var() è il ripiego, cioè quello storico:
                           sul desktop --adf-rail-bg non è definita, vince quello, e
                           l'aspetto dell'applicazione Electron non cambia. Sul web
                           la definisce il tema (assets/adf-theme.css) e la barra
                           diventa verde bosco, come nel sito e nella sezione bandi. */
                        background: var(--adf-rail-bg, var(--bs-body-bg));
                        border-right: 1px solid var(--adf-rail-border, var(--bs-border-color));
                        color: var(--adf-rail-text, var(--bs-body-color));
                        transition: all 0.3s cubic-bezier(0.2, 0, 0, 1);
                        /* dvh e non vh: su iOS la barra degli indirizzi si ritira
                           scorrendo e con vh il fondo del menu resta sotto il bordo
                           dello schermo, irraggiungibile. */
                        height: 100vh;
                        height: 100dvh;
                        position: sticky;
                        top: 0;
                        z-index: 100;
                    }

                    .jl-sidebar.collapsed {
                        width: 80px;
                        min-width: 80px;
                    }

                    .jl-sidebar-header {
                        padding: 1.5rem 1.25rem;
                        height: 80px;
                        display: flex;
                        align-items: center;
                    }

                    .jl-logo {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        width: 100%;
                        min-width: 0;
                    }

                    .jl-logo-icon-img {
                        width: 50px;
                        height: 50px;
                        object-fit: contain;
                        flex-shrink: 0;
                    }

                    .jl-logo-text {
                        font-weight: 800;
                        font-size: 1.75rem;
                        letter-spacing: -0.5px;
                        /* Il nome in gradiente blu-viola era l'elemento più
                           riconoscibile del tema precedente. Sul web diventa un
                           colore pieno: il gradiente e il ritaglio sul testo vanno
                           annullati insieme, altrimenti resta un testo trasparente
                           su nessuno sfondo, cioè invisibile. */
                        background: var(--adf-rail-logo-bg, linear-gradient(45deg, #0d6efd, #6610f2));
                        -webkit-background-clip: text;
                        background-clip: text;
                        -webkit-text-fill-color: var(--adf-rail-logo-fill, transparent);
                        color: var(--adf-rail-text-strong, inherit);
                        white-space: nowrap;
                        overflow: hidden;
                    }

                    /* Container che occupa lo spazio rimanente */
                    .jl-nav-container {
                        flex: 1;
                        overflow-y: auto;
                        overscroll-behavior: contain;
                        padding: 1rem 0.75rem;
                        display: flex;
                        flex-direction: column;
                    }

                    .jl-nav-list {
                        list-style: none;
                        padding: 0;
                        margin: 0;
                        width: 100%;
                    }

                    .jl-nav-item {
                        margin-bottom: 0.5rem;
                    }

                    .jl-nav-link {
                        display: flex;
                        align-items: center;
                        gap: 1rem;
                        width: 100%;
                        padding: 0.85rem 1rem;
                        border: none;
                        border-radius: 12px;
                        background: transparent;
                        color: var(--adf-rail-text, var(--bs-secondary-color));
                        font-size: 0.95rem;
                        font-weight: 500;
                        cursor: pointer;
                        transition: all 0.2s ease;
                        text-align: left;
                        white-space: nowrap;
                        overflow: hidden;
                    }

                    .jl-nav-link:hover {
                        background: var(--adf-rail-hover, var(--bs-tertiary-bg));
                        color: var(--adf-rail-text-strong, var(--bs-primary));
                        transform: translateX(4px);
                    }

                    .jl-nav-link.active {
                        background: var(--adf-rail-active-bg, linear-gradient(90deg, rgba(13, 110, 253, 0.1) 0%, rgba(13, 110, 253, 0.05) 100%));
                        color: var(--adf-rail-text-strong, var(--bs-primary));
                        font-weight: 600;
                        box-shadow: var(--adf-rail-active-bar, inset 3px 0 0 var(--bs-primary));
                    }

                    .jl-nav-icon-wrapper {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        width: 24px;
                        height: 24px;
                        flex-shrink: 0;
                    }

                    .jl-sidebar-footer {
                        padding: 1.5rem;
                        border-top: 1px solid var(--adf-rail-border, var(--bs-border-color));
                    }

                    .jl-collapse-btn {
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        width: 100%;
                        padding: 0.75rem;
                        border: 1px solid var(--adf-rail-border, var(--bs-border-color));
                        border-radius: 10px;
                        background: var(--adf-rail-hover, var(--bs-tertiary-bg));
                        color: var(--adf-rail-text, var(--bs-secondary-color));
                        cursor: pointer;
                        transition: all 0.2s ease;
                    }

                    .jl-collapse-btn:hover {
                        background: var(--bs-secondary-bg);
                        color: var(--bs-primary);
                        border-color: var(--bs-primary);
                    }

                    .jl-sidebar-branding {
                        text-align: center;
                        margin-top: 1rem;
                        font-size: 0.75rem;
                        white-space: nowrap;
                        overflow: hidden;
                    }

                    .jl-sidebar.collapsed .jl-nav-link {
                        justify-content: center;
                        padding: 0.85rem;
                    }

                    .jl-sidebar.collapsed .jl-sidebar-header {
                        justify-content: center;
                        padding: 1.5rem 0;
                    }

                    .jl-sidebar.collapsed .jl-logo {
                        justify-content: center;
                    }

                    /* Sopra i 992px il cassetto non esiste: niente sfondo, niente
                       pulsante di chiusura. */
                    .jl-sidebar-backdrop,
                    .jl-drawer-close {
                        display: none;
                    }

                    /* -----------------------------------------------------------
                       Sotto i 992px: cassetto sovrapposto
                       ----------------------------------------------------------- */
                    @media (max-width: 991.98px) {
                        .jl-sidebar {
                            position: fixed;
                            top: 0;
                            left: 0;
                            bottom: 0;
                            /* Un margine di schermo sulla destra fa capire che
                               sotto c'è ancora la pagina, e dà dove toccare per
                               chiudere. */
                            width: min(86vw, 320px);
                            min-width: 0;
                            max-width: 320px;
                            padding-left: env(safe-area-inset-left);
                            transform: translateX(-100%);
                            /* Nascondere davvero, non solo spostare: un pannello
                               fuori schermo ma visibile resta nell'ordine di
                               tabulazione e il fuoco ci finisce dentro alla cieca. */
                            visibility: hidden;
                            box-shadow: 0 0 60px rgba(0, 0, 0, 0.35);
                            transition: transform 0.28s cubic-bezier(0.2, 0, 0, 1),
                                        visibility 0s linear 0.28s;
                            z-index: 1045;
                        }

                        .jl-sidebar.open {
                            transform: translateX(0);
                            visibility: visible;
                            transition: transform 0.28s cubic-bezier(0.2, 0, 0, 1),
                                        visibility 0s;
                        }

                        /* Restringere a icone è un'idea da schermo largo: qui il
                           cassetto o c'è o non c'è. */
                        .jl-sidebar.collapsed {
                            width: min(86vw, 320px);
                            min-width: 0;
                        }

                        .jl-sidebar .jl-sidebar-footer .jl-collapse-btn {
                            display: none;
                        }

                        .jl-sidebar-footer {
                            padding: 1rem 1.5rem calc(1rem + env(safe-area-inset-bottom));
                        }

                        .jl-sidebar-branding {
                            margin-top: 0;
                        }

                        .jl-sidebar-header {
                            justify-content: space-between;
                            gap: 0.5rem;
                            padding-top: max(1.5rem, env(safe-area-inset-top));
                        }

                        .jl-logo-icon-img {
                            width: 40px;
                            height: 40px;
                        }

                        .jl-logo-text {
                            font-size: 1.35rem;
                            /* Il nome dell'associazione può essere lungo: meglio
                               troncato che sopra il pulsante di chiusura. */
                            text-overflow: ellipsis;
                        }

                        .jl-drawer-close {
                            display: flex;
                            align-items: center;
                            justify-content: center;
                            flex-shrink: 0;
                            width: 44px;
                            height: 44px;
                            border: 0;
                            border-radius: 12px;
                            background: var(--adf-rail-hover, rgba(0, 0, 0, 0.05));
                            color: var(--adf-rail-text, var(--bs-body-color));
                            cursor: pointer;
                        }

                        /* Voci più alte: 44px è il minimo sotto cui il dito manca
                           il bersaglio (WCAG 2.2, 2.5.8). */
                        .jl-nav-link {
                            padding: 0.9rem 1rem;
                            min-height: 48px;
                            font-size: 1rem;
                        }

                        /* Lo scorrimento laterale all'hover, sul touch, resta
                           incollato dopo il tocco e sembra un difetto. */
                        .jl-nav-link:hover {
                            transform: none;
                        }

                        .jl-sidebar-backdrop {
                            display: block;
                            position: fixed;
                            inset: 0;
                            background: rgba(10, 16, 12, 0.5);
                            opacity: 0;
                            visibility: hidden;
                            transition: opacity 0.28s ease, visibility 0s linear 0.28s;
                            z-index: 1040;
                        }

                        .jl-sidebar-backdrop.show {
                            opacity: 1;
                            visibility: visible;
                            transition: opacity 0.28s ease, visibility 0s;
                        }
                    }

                    @media (prefers-reduced-motion: reduce) {
                        .jl-sidebar,
                        .jl-sidebar-backdrop {
                            transition: none;
                        }
                    }
                `}</style>
            </nav>
        </>
    );
}
