import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { ShieldCheck, ShieldAlert, Plus } from 'lucide-react';
import { associago } from '../api';
import AttachmentPanel from './AttachmentPanel';

/**
 * Le polizze che coprono i volontari — art. 18 D.Lgs. 117/2017.
 *
 * L'obbligo non è derogabile: infortuni, malattie connesse all'attività e
 * responsabilità civile verso terzi. Un volontario che opera scoperto è una
 * responsabilità dell'ente, non sua.
 *
 * Due cose che questa schermata mostra e che un elenco di polizze non
 * mostrerebbe:
 *
 *  - **chi è scoperto oggi**, che è la domanda da farsi prima di un turno e
 *    non dopo;
 *  - **quale garanzia manca**, perché avere gli infortuni ma non la RC terzi
 *    è la situazione più insidiosa: sembra copertura.
 */
const TIPI = [
  { id: 'infortuni_rc', label: 'Infortuni e RC terzi' },
  { id: 'infortuni', label: 'Solo infortuni' },
  { id: 'rc_terzi', label: 'Solo RC verso terzi' },
];

const VolunteerInsurancePanel = ({ associationId }) => {
  const { t } = useTranslation();
  const [polizze, setPolizze] = useState([]);
  const [scoperti, setScoperti] = useState([]);
  const [inScadenza, setInScadenza] = useState([]);
  const [mostraForm, setMostraForm] = useState(false);
  const [errore, setErrore] = useState('');
  const [nuova, setNuova] = useState({
    policyNumber: '',
    company: '',
    coverageType: 'infortuni_rc',
    startDate: new Date().toISOString().split('T')[0],
    endDate: '',
    notes: '',
  });

  const ricarica = useCallback(async () => {
    try {
      const [p, s, e] = await Promise.all([
        associago.volunteerInsurance.getByAssociation(associationId),
        associago.volunteerInsurance.uncovered(associationId),
        associago.volunteerInsurance.expiring(associationId, 60),
      ]);
      setPolizze(p);
      setScoperti(s);
      setInScadenza(e);
      setErrore('');
    } catch (err) {
      setErrore(err.message || t('Polizze non disponibili.'));
    }
  }, [associationId, t]);

  useEffect(() => { ricarica(); }, [ricarica]);

  const salva = async (evento) => {
    evento.preventDefault();
    setErrore('');
    try {
      await associago.volunteerInsurance.save({ ...nuova, associationId });
      setMostraForm(false);
      setNuova({ ...nuova, policyNumber: '', company: '', endDate: '', notes: '' });
      await ricarica();
    } catch (err) {
      setErrore(err.message || t('Salvataggio non riuscito.'));
    }
  };

  const giorniAllaScadenza = (endDate) => {
    const oggi = new Date();
    oggi.setHours(0, 0, 0, 0);
    return Math.round((new Date(endDate) - oggi) / 86400000);
  };

  return (
    <div>
      <div className="d-flex justify-content-between align-items-start mb-3">
        <div>
          <h5 className="fw-bold mb-1">{t('Assicurazione dei volontari')}</h5>
          <p className="text-muted small mb-0">
            {t('Obbligatoria per legge (art. 18 CTS): infortuni, malattie e responsabilità civile verso terzi.')}
          </p>
        </div>
        <button type="button" className="btn btn-primary btn-sm" onClick={() => setMostraForm(!mostraForm)}>
          <Plus size={16} className="me-1" />
          {t('Nuova polizza')}
        </button>
      </div>

      {errore && <div className="alert alert-danger py-2">{errore}</div>}

      {/* Chi è scoperto viene prima di tutto: è l'unica cosa qui che chiede
          un'azione oggi. */}
      {scoperti.length > 0 && (
        <div className="alert alert-danger d-flex gap-2">
          <ShieldAlert size={20} className="flex-shrink-0 mt-1" />
          <div>
            <div className="fw-bold">
              {t('{{n}} volontari in attività non risultano coperti', { n: scoperti.length })}
            </div>
            <div className="small">
              {t('Non devono svolgere attività finché la copertura non è completa (infortuni e RC terzi).')}
            </div>
            <ul className="small mb-0 mt-2">
              {scoperti.map((v) => (
                <li key={v.id}>
                  {t('Volontario')} #{v.id}
                  {v.startedAt && <span className="text-muted"> · {t('dal')} {v.startedAt}</span>}
                </li>
              ))}
            </ul>
          </div>
        </div>
      )}

      {inScadenza.length > 0 && (
        <div className="alert alert-warning py-2 small">
          {t('{{n}} polizze scadono entro 60 giorni.', { n: inScadenza.length })}
        </div>
      )}

      {mostraForm && (
        <form onSubmit={salva} className="card card-body mb-3">
          <div className="row g-3">
            <div className="col-md-6">
              <label className="form-label">{t('Numero di polizza')}</label>
              <input
                required
                className="form-control"
                value={nuova.policyNumber}
                onChange={(e) => setNuova({ ...nuova, policyNumber: e.target.value })}
              />
            </div>
            <div className="col-md-6">
              <label className="form-label">{t('Compagnia')}</label>
              <input
                required
                className="form-control"
                value={nuova.company}
                onChange={(e) => setNuova({ ...nuova, company: e.target.value })}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label">{t('Garanzie')}</label>
              <select
                className="form-select"
                value={nuova.coverageType}
                onChange={(e) => setNuova({ ...nuova, coverageType: e.target.value })}
              >
                {TIPI.map((tipo) => <option key={tipo.id} value={tipo.id}>{t(tipo.label)}</option>)}
              </select>
            </div>
            <div className="col-md-4">
              <label className="form-label">{t('Decorrenza')}</label>
              <input
                type="date"
                required
                className="form-control"
                value={nuova.startDate}
                onChange={(e) => setNuova({ ...nuova, startDate: e.target.value })}
              />
            </div>
            <div className="col-md-4">
              <label className="form-label">{t('Scadenza')}</label>
              <input
                type="date"
                required
                className="form-control"
                value={nuova.endDate}
                onChange={(e) => setNuova({ ...nuova, endDate: e.target.value })}
              />
            </div>
          </div>
          <div className="d-flex justify-content-end gap-2 mt-3">
            <button type="button" className="btn btn-secondary" onClick={() => setMostraForm(false)}>
              {t('Annulla')}
            </button>
            <button type="submit" className="btn btn-primary">{t('Salva la polizza')}</button>
          </div>
        </form>
      )}

      {polizze.length === 0 ? (
        <div className="alert alert-warning d-flex gap-2">
          <ShieldAlert size={20} className="flex-shrink-0" />
          <div>
            {t('Nessuna polizza registrata. Finché non ce n\'è una, nessun volontario può operare.')}
          </div>
        </div>
      ) : (
        polizze.map((p) => {
          const giorni = giorniAllaScadenza(p.endDate);
          const scaduta = giorni < 0;
          return (
            <div key={p.id} className={`card mb-3 ${scaduta ? 'border-danger' : ''}`}>
              <div className="card-body">
                <div className="d-flex justify-content-between align-items-start flex-wrap gap-2">
                  <div>
                    <div className="fw-bold d-flex align-items-center gap-2">
                      {scaduta
                        ? <ShieldAlert size={18} className="text-danger" />
                        : <ShieldCheck size={18} className="text-success" />}
                      {p.company} · <span className="font-monospace">{p.policyNumber}</span>
                    </div>
                    <div className="small text-muted">
                      {TIPI.find((tipo) => tipo.id === p.coverageType)?.label || p.coverageType}
                      {' · '}{p.startDate} → {p.endDate}
                    </div>
                  </div>
                  <span className={`badge ${scaduta ? 'bg-danger' : (giorni < 60 ? 'bg-warning text-dark' : 'bg-success')}`}>
                    {scaduta
                      ? t('scaduta da {{n}} giorni', { n: Math.abs(giorni) })
                      : t('scade fra {{n}} giorni', { n: giorni })}
                  </span>
                </div>

                <hr />
                {/* Il contratto è il documento che dimostra la copertura: senza,
                    la riga qui sopra è un'affermazione senza prova. */}
                <AttachmentPanel
                  ownerType="INSURANCE_POLICY"
                  ownerId={p.id}
                  titolo={t('Contratto di polizza')}
                  kinds={[{ id: 'polizza', label: t('Contratto') }]}
                />
              </div>
            </div>
          );
        })
      )}
    </div>
  );
};

export default VolunteerInsurancePanel;
