import React from 'react';
import { Button, OverlayTrigger, Tooltip } from 'react-bootstrap';

/**
 * Pulsanti di azione per le righe delle tabelle (Soci, Attività, Eventi, ecc.).
 *
 * Renderizza icon-button inline con tooltip al posto dei dropdown 3-puntini,
 * che venivano tagliati dal contenitore <Table responsive> a causa di
 * `overflow-x: auto` sul wrapper di Bootstrap.
 *
 * Props:
 *   actions: array di oggetti { key, icon, label, onClick, variant?,
 *                               textClass?, disabled?, hidden? }
 *
 *   - icon: ReactNode (di solito <Icon size={16} /> da lucide-react)
 *   - label: stringa mostrata nel tooltip e usata come aria-label
 *   - variant: variante Bootstrap del bottone (default 'light')
 *   - textClass: classe CSS per il colore dell'icona (es. 'text-danger')
 *   - hidden: se true il bottone non viene reso (utile per condizioni)
 *
 * Ogni click chiama stopPropagation per evitare di innescare l'onClick
 * della riga su cui è montato il bottone.
 */
export default function RowActions({ actions = [], className = '' }) {
  const visible = actions.filter((a) => a && !a.hidden);
  if (visible.length === 0) return null;

  return (
    <div className={`d-flex justify-content-end gap-2 ${className}`}>
      {visible.map((a) => (
        <OverlayTrigger
          key={a.key}
          placement="top"
          overlay={<Tooltip id={`row-action-${a.key}`}>{a.label}</Tooltip>}
        >
          <Button
            variant={a.variant || 'light'}
            size="sm"
            className={`btn-icon ${a.textClass || 'text-muted'}`}
            onClick={(e) => {
              e.stopPropagation();
              if (a.onClick) a.onClick(e);
            }}
            disabled={a.disabled}
            aria-label={a.label}
          >
            {a.icon}
          </Button>
        </OverlayTrigger>
      ))}
    </div>
  );
}
