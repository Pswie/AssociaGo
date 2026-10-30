import { ORG_LOGO, ORG_NAME, MODULE_NAME } from '../branding';
import { useTranslation } from 'react-i18next';

/**
 * SplashScreen (SplashScreen) - Schermata di transizione generica
 *
 * Usata per:
 * - Avvio applicazione (Splash Screen)
 * - Logout / Reload
 * - Attesa backend
 *
 * @param {string} message - Messaggio da mostrare sotto lo spinner
 */
export default function SplashScreen({ message }) {
    const { t } = useTranslation();
    const displayMessage = message || t("Loading...");

    return (
        <div className="logout-screen">
            <div className="logout-content text-center">
                {/* 500px fissi erano più larghi di qualsiasi telefono: il logo
                    usciva dallo schermo e si portava dietro la pagina. clamp
                    lo fa scendere fino a 180px sui più stretti senza mai
                    superare il 60% dell'altezza disponibile. */}
                <img
                    src={ORG_LOGO}
                    alt={ORG_NAME}
                    className="logout-logo mb-4"
                    style={{
                        width: "min(220px, 50vw, 40dvh)",
                        height: "auto",
                        objectFit: 'contain',
                        padding: 12,
                        background: '#fff',
                        borderRadius: 20
                    }}
                />
                <h4 className="fw-bold mb-1">{ORG_NAME}</h4>
                <p className="text-muted mb-3">{MODULE_NAME}</p>
                <div className="d-flex align-items-center justify-content-center gap-2 text-muted">
                    <div className="spinner-border spinner-border-sm" role="status"></div>
                    <span>{displayMessage}</span>
                </div>
            </div>

            <style>{`
                .logout-screen {
                    position: fixed;
                    /* inset invece di top/left + 100vw/100vh: vw conta la barra
                       di scorrimento e vh non segue la barra degli indirizzi
                       mobile, e la schermata risultava più grande del viewport
                       in entrambe le direzioni. */
                    inset: 0;
                    padding: 1.5rem;
                    background: var(--body-bg, #f5f7fa);
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    z-index: 99999; /* Sopra tutto */
                }

                .logout-content {
                    animation: fadeIn 0.5s ease-in-out;
                }

                @keyframes fadeIn {
                    from { opacity: 0; transform: scale(0.95); }
                    to { opacity: 1; transform: scale(1); }
                }
            `}</style>
        </div>
    );
}
