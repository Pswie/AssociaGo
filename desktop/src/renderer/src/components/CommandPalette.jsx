import React, { useState, useEffect, useRef } from 'react';
import { Modal, Form, ListGroup } from 'react-bootstrap';
import { Search, ArrowRight } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * CommandPalette - Finestra di comando globale (Ctrl+K / Cmd+K)
 *
 * Permette di navigare rapidamente tra le pagine e eseguire azioni.
 */
export default function CommandPalette({ shell, isOpen, onClose }) {
    const { t } = useTranslation();
    const [query, setQuery] = useState('');
    const [selectedIndex, setSelectedIndex] = useState(0);
    const inputRef = useRef(null);

    // Lista comandi base
    const commands = [
        { id: 'dashboard', label: t('nav.dashboard'), icon: 'LayoutDashboard', action: () => shell.navigate('dashboard') },
        { id: 'members', label: t('nav.members'), icon: 'Users', action: () => shell.navigate('members') },
        { id: 'activities', label: t('nav.activities'), icon: 'Calendar', action: () => shell.navigate('activities') },
        { id: 'events', label: t('nav.events'), icon: 'Calendar', action: () => shell.navigate('events') },
        { id: 'fiscal', label: t('nav.fiscal'), icon: 'CreditCard', action: () => shell.navigate('fiscal') },
        ...['assemblies', 'inventory', 'volunteers', 'attendance', 'coupons', 'budget', 'balance', 'certificates', 'communications'].map(id => ({ id, label: t(`nav.${id}`), action: () => shell.navigate(id) })),
        { id: 'manual', label: t('User Manual'), action: () => shell.navigate('manual') },
        { id: 'settings', label: t('nav.settings'), icon: 'Settings', action: () => shell.navigate('settings') },
        { id: 'new-member', label: t('New Member'), icon: 'UserPlus', action: () => {
            shell.navigate('members');
            shell.openModal('member-form', {
                associationId: shell.currentAssociationId,
                associationType: shell.currentAssociation?.tipo,
                onSuccess: shell.refreshPage
            });
        } },
    ];

    // Filtra comandi
    const filteredCommands = commands.filter(cmd =>
        (!shell.pages || shell.pages.some(page => page.id === (cmd.id === 'new-member' ? 'members' : cmd.id))) &&
        cmd.label.toLowerCase().includes(query.trim().toLowerCase())
    );

    // Reset selezione quando cambia query
    useEffect(() => {
        setSelectedIndex(0);
    }, [query]);

    // Focus input all'apertura
    useEffect(() => {
        if (isOpen) {
            setQuery('');
            setSelectedIndex(0);
            const timeout = setTimeout(() => inputRef.current?.focus(), 50);
            return () => clearTimeout(timeout);
        }
    }, [isOpen]);

    // Gestione tastiera
    const handleKeyDown = (e) => {
        if (e.key === 'ArrowDown') {
            e.preventDefault();
            if (!filteredCommands.length) return;
            setSelectedIndex(prev => (prev + 1) % filteredCommands.length);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            if (!filteredCommands.length) return;
            setSelectedIndex(prev => (prev - 1 + filteredCommands.length) % filteredCommands.length);
        } else if (e.key === 'Enter') {
            e.preventDefault();
            if (filteredCommands[selectedIndex]) {
                filteredCommands[selectedIndex].action();
                onClose();
            }
        }
    };

    return (
        <Modal show={isOpen} onHide={onClose} centered size="lg" fullscreen="sm-down" className="command-palette-modal">
            <Modal.Body className="p-0">
                <div className="d-flex align-items-center p-3 border-bottom">
                    <Search size={20} className="text-muted me-3" />
                    <Form.Control
                        ref={inputRef}
                        type="text"
                        placeholder={t('Type a command or search...')}
                        aria-label={t('Type a command or search...')}
                        role="combobox"
                        aria-autocomplete="list"
                        aria-expanded={isOpen}
                        aria-controls="command-results"
                        aria-activedescendant={filteredCommands[selectedIndex] ? `command-${filteredCommands[selectedIndex].id}` : undefined}
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        onKeyDown={handleKeyDown}
                        className="border-0 shadow-none p-0 fs-5"
                        autoComplete="off"
                    />
                    <div className="text-muted small border px-2 py-1 rounded">Esc</div>
                </div>

                <ListGroup variant="flush" className="command-list" id="command-results" role="listbox" aria-label={t('Commands')}>
                    {filteredCommands.length > 0 ? (
                        filteredCommands.map((cmd, index) => (
                            <ListGroup.Item
                                key={cmd.id}
                                id={`command-${cmd.id}`}
                                role="option"
                                aria-selected={index === selectedIndex}
                                action
                                active={index === selectedIndex}
                                onClick={() => { cmd.action(); onClose(); }}
                                className="d-flex align-items-center justify-content-between py-3 border-0"
                                onMouseEnter={() => setSelectedIndex(index)}
                            >
                                <div className="d-flex align-items-center">
                                    <div className={`me-3 text-${index === selectedIndex ? 'white' : 'muted'}`}>
                                        <ArrowRight size={16} />
                                    </div>
                                    <span className="fw-medium">{cmd.label}</span>
                                </div>
                                {index === selectedIndex && <span className="small text-white-50">Enter</span>}
                            </ListGroup.Item>
                        ))
                    ) : (
                        <div className="p-4 text-center text-muted">
                            {t('No commands found.')}
                        </div>
                    )}
                </ListGroup>
            </Modal.Body>
            <style>{`
                .command-palette-modal .modal-content {
                    border-radius: 12px;
                    overflow: hidden;
                    box-shadow: 0 20px 50px rgba(0,0,0,0.2);
                    border: none;
                }
                .command-list {
                    max-height: 400px;
                    overflow-y: auto;
                    overscroll-behavior: contain;
                }
                /* Era #0d6efd cablato: sotto il tema di Angeli del Futuro la
                   riga selezionata restava blu in mezzo al verde bosco. */
                .command-palette-modal .list-group-item.active {
                    background-color: var(--bs-primary, #0d6efd);
                    border-color: var(--bs-primary, #0d6efd);
                }
                @media (max-width: 575.98px) {
                    /* A tutto schermo la lista può usare l'altezza disponibile
                       invece di fermarsi a 400px lasciando il vuoto sotto. */
                    .command-list {
                        max-height: none;
                    }
                    .command-palette-modal .modal-content {
                        border-radius: 0;
                    }
                }
            `}</style>
        </Modal>
    );
}
