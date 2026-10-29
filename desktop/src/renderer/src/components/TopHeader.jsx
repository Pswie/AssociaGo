import React from "react";
import { Menu } from "lucide-react";
import { useTranslation } from "react-i18next";

/**
 * TopHeader - Barra superiore dell'applicazione
 *
 * @param {Object} props
 * @param {string} props.title - Titolo della pagina corrente
 * @param {React.ReactNode} props.actions - Azioni da mostrare a destra (es. bottoni, menu)
 * @param {function} [props.onMenuToggle] - Apre/chiude il cassetto di navigazione.
 *   Assente sopra i 992px, dove il binario laterale è sempre in vista.
 * @param {boolean} [props.navOpen] - Stato del cassetto, per `aria-expanded`.
 */
export default function TopHeader({ title, actions, onMenuToggle, navOpen = false }) {
    const { t } = useTranslation();

    return (
        <header className="top-header">
            {/* Il pulsante viene prima del titolo nell'ordine di lettura: è il
                primo comando che serve arrivando da telefono. */}
            {onMenuToggle && (
                <button
                    type="button"
                    className="top-header-burger"
                    onClick={onMenuToggle}
                    aria-label={t('Navigation')}
                    aria-expanded={navOpen}
                    aria-controls="jl-primary-nav"
                >
                    <Menu size={22} />
                </button>
            )}

            <div className="top-header-title">
                <h4 className="mb-0">{title}</h4>
            </div>
            <div className="top-header-actions">
                {actions}
            </div>

            <style>{`
                .top-header {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    gap: 0.75rem;
                    padding: 1rem 1.5rem;
                    /* Era #ffffff cablato: restava bianca sia in tema scuro sia
                       sotto il tema di Angeli del Futuro, e il titolo grigio
                       ferro su bianco spariva. Questi due token sono definiti da
                       entrambi i temi (assets/main.css e assets/adf-theme.css). */
                    background-color: var(--card-bg, #ffffff);
                    border-bottom: 1px solid var(--border-color, rgba(0,0,0,0.08));
                    height: 70px;
                    position: sticky;
                    top: 0;
                    z-index: 99;
                }

                .top-header-title {
                    /* Senza min-width:0 il titolo non si lascia troncare e spinge
                       le azioni fuori dallo schermo. */
                    min-width: 0;
                    flex: 1;
                }

                .top-header-title h4 {
                    font-weight: 600;
                    color: var(--text-color, #343a40);
                    overflow: hidden;
                    text-overflow: ellipsis;
                    white-space: nowrap;
                }

                .top-header-actions {
                    display: flex;
                    align-items: center;
                    gap: 1rem;
                    flex-shrink: 0;
                }

                .top-header-burger {
                    display: none;
                    align-items: center;
                    justify-content: center;
                    flex-shrink: 0;
                    width: 44px;
                    height: 44px;
                    margin-left: -0.35rem;
                    border: 0;
                    border-radius: 12px;
                    background: transparent;
                    color: var(--text-color, #343a40);
                    cursor: pointer;
                    transition: background-color 0.2s ease;
                }

                .top-header-burger:hover,
                .top-header-burger:focus-visible {
                    background: var(--border-color, rgba(0,0,0,0.06));
                }

                .top-header-burger:active {
                    transform: scale(0.94);
                }

                @media (max-width: 991.98px) {
                    .top-header-burger {
                        display: flex;
                    }

                    .top-header {
                        /* L'intaglio della fotocamera sui telefoni recenti mangia
                           la fascia superiore: senza questo il titolo ci finisce
                           sotto in orizzontale. */
                        padding: 0.75rem 1rem;
                        padding-top: max(0.75rem, env(safe-area-inset-top));
                        padding-left: max(1rem, env(safe-area-inset-left));
                        padding-right: max(1rem, env(safe-area-inset-right));
                        height: auto;
                        min-height: 60px;
                    }

                    .top-header-title h4 {
                        font-size: 1.05rem;
                    }

                    .top-header-actions {
                        gap: 0.35rem;
                    }
                }
            `}</style>
        </header>
    );
}
