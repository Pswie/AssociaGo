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
 *
 * ## Touch
 *
 * Il tooltip è appeso all'hover, che sul touch non esiste: il primo tocco lo
 * apre invece di premere il bottone, e resta appeso finché non si tocca
 * altrove. Su puntatore grossolano viene quindi omesso — l'`aria-label` resta
 * in entrambi i casi, quindi chi usa un lettore di schermo sente comunque il
 * nome dell'azione.
 *
 * La misura del bersaglio (44px) la impone `assets/mobile.css` attraverso la
 * classe `btn-icon`, già presente qui.
 */

/** Vero quando il puntatore principale è impreciso: dito o pennino, non mouse. */
function useCoarsePointer() {
  const query = '(pointer: coarse)';
  const supported = typeof window !== 'undefined' && typeof window.matchMedia === 'function';
  const [coarse, setCoarse] = React.useState(
    () => supported && window.matchMedia(query).matches
  );

  React.useEffect(() => {
    if (!supported) return undefined;
    const list = window.matchMedia(query);
    const onChange = (event) => setCoarse(event.matches);
    // Un portatile con schermo tattile può passare dall'uno all'altro senza
    // che la pagina venga ricaricata.
    list.addEventListener('change', onChange);
    return () => list.removeEventListener('change', onChange);
  }, [supported]);

  return coarse;
}

export default function RowActions({ actions = [], className = '' }) {
  const coarsePointer = useCoarsePointer();
  const visible = actions.filter((a) => a && !a.hidden);
  if (visible.length === 0) return null;

  const renderButton = (a) => (
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
      // Senza tooltip resta almeno il suggerimento nativo, per chi arriva da
      // un tablet con tastiera e mouse collegati.
      title={coarsePointer ? a.label : undefined}
    >
      {a.icon}
    </Button>
  );

  return (
    <div className={`d-flex justify-content-end gap-2 ${className}`}>
      {visible.map((a) => (
        coarsePointer ? (
          <React.Fragment key={a.key}>{renderButton(a)}</React.Fragment>
        ) : (
          <OverlayTrigger
            key={a.key}
            placement="top"
            overlay={<Tooltip id={`row-action-${a.key}`}>{a.label}</Tooltip>}
          >
            {renderButton(a)}
          </OverlayTrigger>
        )
      ))}
    </div>
  );
}
