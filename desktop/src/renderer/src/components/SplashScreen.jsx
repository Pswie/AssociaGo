import LoadingIcon from "../assets/5.svg";
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
                    src={LoadingIcon}
                    alt="AssociaGo Loading"
                    className="logout-logo mb-4"
                    style={{
                        width: "min(clamp(180px, 60vw, 500px), 60dvh)",
                        height: "auto",
                        aspectRatio: "1 / 1"
                    }}
                />
                <h4 className="fw-bold mb-3">AssociaGo</h4>
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
                    background: #f5f7fa; /* Colore neutro */
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
