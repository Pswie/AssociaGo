import React, { useState, useEffect } from 'react';
import { Dropdown, Badge } from 'react-bootstrap';
import { CalendarRange, Lock } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';
import { anniDisponibili, annoCorrente } from '../esercizio';

/**
 * Il selettore dell'esercizio, nella testata.
 *
 * Sta lì e non dentro le singole schermate perché l'anno è una lente su tutto:
 * cambiare anno in contabilità e ritrovarsi il 2027 nelle assemblee sarebbe il
 * modo più rapido di guardare i numeri sbagliati credendoli giusti.
 *
 * Un esercizio chiuso lo dice, con il lucchetto. Non è solo informazione: su un
 * anno chiuso i numeri non devono più cambiare, e chi sta per scrivere qualcosa
 * deve accorgersene prima di farlo, non dopo.
 */
const SelettoreAnno = ({ anno, onChange, associationId }) => {
  const { t } = useTranslation();
  const [chiusure, setChiusure] = useState({});

  useEffect(() => {
    if (!associationId) return;
    let annullato = false;

    // Si chiede lo stato di ciascun anno dell'elenco. Sono poche chiamate e
    // solo all'apertura: un endpoint che le restituisse tutte insieme non
    // esiste, e inventarne uno per questo non vale il giro.
    (async () => {
      const esiti = {};
      for (const a of anniDisponibili()) {
        try {
          const c = await associago.fiscal.getClosure(associationId, a);
          if (c?.status) esiti[a] = c.status;
        } catch {
          // Nessuna chiusura per quell'anno: è il caso normale, non un errore.
        }
      }
      if (!annullato) setChiusure(esiti);
    })();

    return () => { annullato = true; };
  }, [associationId]);

  const chiuso = (a) => chiusure[a] === 'CLOSED';

  return (
    <Dropdown align="end">
      <Dropdown.Toggle
        variant="light"
        size="sm"
        className="d-flex align-items-center gap-2 border"
        title={t('Esercizio')}
      >
        <CalendarRange size={16} className="text-primary" />
        <span className="fw-semibold">{anno}</span>
        {chiuso(anno) && <Lock size={13} className="text-muted" />}
        {anno !== annoCorrente() && (
          <Badge bg="warning" text="dark" className="fw-normal">{t('storico')}</Badge>
        )}
      </Dropdown.Toggle>

      <Dropdown.Menu style={{ minWidth: '14rem' }}>
        <Dropdown.Header>{t('Esercizio da guardare')}</Dropdown.Header>
        {anniDisponibili().map((a) => (
          <Dropdown.Item
            key={a}
            active={a === anno}
            onClick={() => onChange(a)}
            className="d-flex align-items-center justify-content-between"
          >
            <span>
              {a}
              {a === annoCorrente() && (
                <small className="text-muted ms-2">{t('in corso')}</small>
              )}
            </span>
            {chiuso(a) && <Lock size={13} className="text-muted" />}
          </Dropdown.Item>
        ))}
        <Dropdown.Divider />
        <div className="px-3 py-2 text-muted" style={{ fontSize: '0.75rem', lineHeight: 1.5 }}>
          {/* Va detto qui, dove si cambia anno: altrimenti chi vede sparire
              metà elenco pensa a un dato perso. */}
          {t('Le cose ancora in corso — tessere aperte, progetti, attività — restano visibili in ogni anno che attraversano. Si archivia solo ciò che è finito.')}
        </div>
      </Dropdown.Menu>
    </Dropdown>
  );
};

export default SelettoreAnno;
