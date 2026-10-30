import React, { useState, useRef, useEffect } from "react";
import { Settings, Info, FolderOpen, Upload, User, LogOut } from "lucide-react";
import { useTranslation } from 'react-i18next';
import { Modal as BsModal } from 'react-bootstrap';
import { ORG_LOGO, ORG_NAME, MODULE_NAME, SOURCE_CODE_URL } from '../branding';

/**
 * ProfileMenu - Menu dropdown profilo utente
 *
 * Features:
 * - Avatar con iniziali
 * - Menu dropdown con azioni custom
 * - Separatori
 *
 * @author Lorenzo DM
 * @since 0.2.0
 */
export default function ProfileMenu({ initials = "U", title, subtitle, items = [] }) {
    const [isOpen, setIsOpen] = useState(false);
    const [showInfoModal, setShowInfoModal] = useState(false);
    const menuRef = useRef(null);
    const avatarRef = useRef(null);
    const { t } = useTranslation();
    const [appVersion, setAppVersion] = useState(''); // Loaded from Electron

    // Chiudi menu quando si clicca fuori
    useEffect(() => {
        function handleClickOutside(event) {
            if (menuRef.current && !menuRef.current.contains(event.target)) {
                setIsOpen(false);
            }
        }

        document.addEventListener("mousedown", handleClickOutside);
        return () => document.removeEventListener("mousedown", handleClickOutside);
    }, []);

    useEffect(() => {
        if (!isOpen) return;
        const frame = requestAnimationFrame(() => menuRef.current?.querySelector('[role="menuitem"]')?.focus());
        return () => cancelAnimationFrame(frame);
    }, [isOpen]);

    const handleMenuKeyDown = (event) => {
        const buttons = [...menuRef.current.querySelectorAll('[role="menuitem"]')];
        const current = buttons.indexOf(document.activeElement);
        if (event.key === 'Escape') {
            event.preventDefault();
            setIsOpen(false);
            avatarRef.current?.focus();
        } else if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
            event.preventDefault();
            const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1 : (current + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
            buttons[next]?.focus();
        } else if (event.key === 'Tab') {
            setTimeout(() => setIsOpen(false), 0);
        }
    };

    useEffect(() => {
        // Fetch version dynamically if available via window.api
        if (window.api && window.api.getAppVersion) {
            window.api.getAppVersion().then(v => setAppVersion(v));
        }
    }, []);

    const handleItemClick = (item) => {
        if (item.id === 'info') {
            setShowInfoModal(true);
            setIsOpen(false);
            return;
        }
        if (item.onClick) {
            item.onClick();
        }
        setIsOpen(false);
    };

    // Helper to render icon
    const renderIcon = (iconName) => {
        switch (iconName) {
            case "bi-gear": return <Settings size={16} className="me-2" />;
            case "bi-info-circle": return <Info size={16} className="me-2" />;
            case "bi-folder2-open": return <FolderOpen size={16} className="me-2" />;
            case "bi-box-arrow-up": return <Upload size={16} className="me-2" />;
            case "bi-person-badge": return <User size={16} className="me-2" />;
            case "bi-box-arrow-right": return <LogOut size={16} className="me-2" />;
            default: return null;
        }
    };

    return (
        <div className="profile-menu" ref={menuRef}>
            {/* Avatar Button */}
            <button
                type="button"
                ref={avatarRef}
                className="profile-avatar"
                onClick={() => setIsOpen(!isOpen)}
                title={title}
                aria-label={t('Menu del profilo')}
                aria-haspopup="menu"
                aria-expanded={isOpen}
                aria-controls={isOpen ? 'profile-menu-items' : undefined}
                onKeyDown={event => { if (event.key === 'ArrowDown') { event.preventDefault(); setIsOpen(true); } }}
            >
                {initials}
            </button>

            {/* Dropdown Menu */}
            {isOpen && (
                <div className="profile-dropdown" id="profile-menu-items" role="menu" aria-label={t('Menu del profilo')} onKeyDown={handleMenuKeyDown}>
                    {/* Header */}
                    <div className="profile-dropdown-header">
                        <div className="profile-dropdown-avatar">{initials}</div>
                        <div className="profile-dropdown-info">
                            <div className="profile-dropdown-title">{title}</div>
                            {subtitle && <div className="profile-dropdown-subtitle">{subtitle}</div>}
                        </div>
                    </div>

                    <hr className="dropdown-divider" />

                    {/* Items */}
                    <div className="profile-dropdown-items">
                        {items.map((item, index) => {
                            if (item.type === "sep") {
                                return <hr key={index} className="dropdown-divider" />;
                            }

                            return (
                                <button
                                    type="button"
                                    role="menuitem"
                                    key={item.id || index}
                                    className={`profile-dropdown-item ${item.danger ? "text-danger" : ""}`}
                                    onClick={() => handleItemClick(item)}
                                >
                                    {item.icon && renderIcon(item.icon)}
                                    {item.label}
                                </button>
                            );
                        })}
                    </div>
                </div>
            )}

            {/* Info Modal */}
            <BsModal show={showInfoModal} onHide={() => setShowInfoModal(false)} centered onExited={() => avatarRef.current?.focus()} aria-labelledby="profile-info-title">
                            <BsModal.Header closeButton>
                                <BsModal.Title id="profile-info-title">{t('App Info')}</BsModal.Title>
                            </BsModal.Header>
                            <BsModal.Body className="text-center">
                                <img src={ORG_LOGO} alt={ORG_NAME} width="100" height="100" className="mb-3" style={{ objectFit: 'contain', background: '#fff', padding: 6, borderRadius: 12 }} />
                                <h4 className="fw-bold mb-1">{ORG_NAME}</h4>
                                <p className="text-muted mb-3">{MODULE_NAME} · AssociaGo{appVersion ? ` v${appVersion}` : ''}</p>

                                <p className="mb-1">Copyright © Lorenzo De Marco (Lorenzo DM)</p>
                                <p className="mb-3 text-muted small">{t('License')}: AGPLv3</p>
                                <p><a href={SOURCE_CODE_URL} target="_blank" rel="noopener noreferrer">{t('Source code')}</a></p>

                                <p className="mb-3">
                                    <a href="https://www.lorenzodm.it" target="_blank" rel="noopener noreferrer" className="text-decoration-none">
                                        www.lorenzodm.it
                                    </a>
                                </p>

                                <div className="alert alert-warning small text-start">
                                    <strong>{t('Disclaimer')}:</strong> {t('AssociaGo may make errors, always check the outputs provided before forwarding them.')}
                                </div>
                            </BsModal.Body>
                            <BsModal.Footer>
                                <button type="button" className="btn btn-secondary" onClick={() => setShowInfoModal(false)}>
                                    {t('Close')}
                                </button>
                            </BsModal.Footer>
            </BsModal>

            <style>{`
                .profile-menu {
                    position: relative;
                }

                .profile-avatar {
                    width: 36px;
                    height: 36px;
                    border-radius: 50%;
                    background: var(--primary-gradient);
                    color: white;
                    border: none;
                    font-weight: 600;
                    font-size: 0.9rem;
                    cursor: pointer;
                    transition: transform 0.15s, box-shadow 0.15s;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                }

                .profile-avatar:hover {
                    transform: scale(1.05);
                    box-shadow: 0 2px 8px rgba(0,0,0,0.15);
                }

                .profile-dropdown {
                    position: absolute;
                    top: 100%;
                    right: 0;
                    margin-top: 0.5rem;
                    background: var(--card-bg);
                    border-radius: 8px;
                    box-shadow: var(--card-shadow);
                    width: min(280px, calc(100vw - 1.5rem));
                    z-index: 1000;
                    animation: fadeIn 0.15s ease;
                    border: 1px solid var(--border-color);
                    color: var(--text-color);
                }

                @keyframes fadeIn {
                    from { opacity: 0; transform: translateY(-8px); }
                    to { opacity: 1; transform: translateY(0); }
                }

                .profile-dropdown-header {
                    display: flex;
                    align-items: center;
                    gap: 0.75rem;
                    padding: 0.75rem 1rem;
                }

                .profile-dropdown-avatar {
                    width: 40px;
                    height: 40px;
                    border-radius: 50%;
                    background: var(--primary-gradient);
                    color: white;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-weight: 600;
                }

                .profile-dropdown-info {
                    flex: 1;
                    overflow: hidden;
                }

                .profile-dropdown-title {
                    font-weight: 600;
                    font-size: 0.9rem;
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                    color: var(--text-color);
                }

                .profile-dropdown-subtitle {
                    font-size: 0.75rem;
                    color: var(--text-muted);
                    white-space: nowrap;
                    overflow: hidden;
                    text-overflow: ellipsis;
                }

                .profile-dropdown-items {
                    padding: 0.25rem 0;
                }

                .profile-dropdown-item {
                    display: flex;
                    align-items: center;
                    width: 100%;
                    padding: 0.5rem 1rem;
                    border: none;
                    background: none;
                    text-align: left;
                    font-size: 0.875rem;
                    color: var(--text-color);
                    cursor: pointer;
                    transition: background 0.1s;
                }

                .profile-dropdown-item:hover {
                    background: var(--bs-body-bg);
                }

                .dropdown-divider {
                    margin: 0.25rem 0;
                    border-top: 1px solid var(--border-color);
                    opacity: 1;
                }
            `}</style>
        </div>
    );
}
