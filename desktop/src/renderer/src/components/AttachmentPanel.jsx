import React, { useState, useEffect, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Paperclip, Download, Trash2, Upload } from 'lucide-react';
import { associago } from '../api';

/**
 * Gli allegati di qualcosa.
 *
 * Un solo componente per tutto ciò che prima era una colonna `*_path`: statuto,
 * verbali, ricevute, fatture, bilanci. Riceve a che cosa si attacca
 * (`ownerType` + `ownerId`) e quali tipi di file quella cosa può avere.
 *
 * Il file finisce nel database, non su disco. Il perché sta nel backend: i
 * percorsi puntavano al disco locale del contenitore, che su Cloud Run si
 * azzera a ogni riavvio — la colonna diceva che il documento c'era, e non
 * c'era più.
 *
 * @param ownerType una voce di AttachmentOwner: ASSOCIATION, ASSEMBLY,
 *                  EXPENSE_DOCUMENT, VOLUNTEER_EXPENSE, INSURANCE_POLICY…
 * @param kinds     i tipi ammessi per questo proprietario, es.
 *                  [{ id: 'statuto', label: 'Statuto' }]. Uno solo per tipo:
 *                  ricaricare sostituisce.
 */
const AttachmentPanel = ({ ownerType, ownerId, kinds = [{ id: 'documento', label: 'Documento' }], titolo }) => {
  const { t } = useTranslation();
  const [allegati, setAllegati] = useState([]);
  const [caricamento, setCaricamento] = useState(false);
  const [scaricamento, setScaricamento] = useState(null);
  const [errore, setErrore] = useState('');

  const ricarica = useCallback(async () => {
    if (!ownerId) return;
    try {
      setAllegati(await associago.attachments.list(ownerType, ownerId));
      setErrore('');
    } catch (err) {
      setErrore(err.message || t('Allegati non disponibili.'));
    }
  }, [ownerType, ownerId, t]);

  useEffect(() => { ricarica(); }, [ricarica]);

  const carica = async (kind, file) => {
    if (!file) return;
    setCaricamento(true);
    setErrore('');
    try {
      await associago.attachments.upload(ownerType, ownerId, kind, file);
      await ricarica();
    } catch (err) {
      // Il messaggio del server è utile — dice se è il formato o la dimensione
      // — quindi si mostra invece di sostituirlo con un generico.
      setErrore(err.message || t('Caricamento non riuscito.'));
    } finally {
      setCaricamento(false);
    }
  };

  const scarica = async (kind, fileName) => {
    setScaricamento(kind);
    setErrore('');
    let objectUrl;
    try {
      const blob = await associago.attachments.download(ownerType, ownerId, kind);
      objectUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = objectUrl;
      a.download = fileName || 'allegato';
      document.body.appendChild(a);
      try {
        a.click();
      } finally {
        a.remove();
      }
    } catch (err) {
      setErrore(err.message || t('Scaricamento non riuscito.'));
    } finally {
      // Il browser deve poter iniziare il download prima di liberare il Blob.
      if (objectUrl) setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
      setScaricamento(null);
    }
  };

  const elimina = async (kind) => {
    if (!confirm(t('Eliminare questo allegato?'))) return;
    try {
      await associago.attachments.delete(ownerType, ownerId, kind);
      await ricarica();
    } catch (err) {
      setErrore(err.message || t('Eliminazione non riuscita.'));
    }
  };

  const dimensione = (byte) => {
    if (byte < 1024) return `${byte} B`;
    if (byte < 1024 * 1024) return `${Math.round(byte / 1024)} KB`;
    return `${(byte / 1024 / 1024).toFixed(1)} MB`;
  };

  if (!ownerId) {
    return (
      <p className="text-muted small mb-0">
        {t('Salva prima la scheda: gli allegati hanno bisogno di sapere a che cosa attaccarsi.')}
      </p>
    );
  }

  return (
    <div>
      <div className="d-flex align-items-center gap-2 mb-2">
        <Paperclip size={18} className="text-primary" />
        <span className="fw-bold">{titolo || t('Allegati')}</span>
      </div>

      {errore && <div className="alert alert-danger py-2 small">{errore}</div>}

      <ul className="list-group list-group-flush">
        {kinds.map((kind) => {
          const presente = allegati.find((a) => a.kind === kind.id);
          return (
            <li key={kind.id} className="list-group-item px-0 d-flex flex-wrap align-items-center gap-2">
              <div className="flex-grow-1">
                <div className="fw-semibold small">{kind.label}</div>
                {presente ? (
                  <div className="text-muted small text-break">
                    {presente.fileName} · {dimensione(presente.sizeBytes)}
                  </div>
                ) : (
                  <div className="text-muted small fst-italic">{t('nessun file')}</div>
                )}
              </div>

              <div className="d-flex gap-1">
                {presente && (
                  <>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-secondary"
                      onClick={() => scarica(kind.id, presente.fileName)}
                      disabled={scaricamento === kind.id}
                      aria-label={`${t('Scarica')} ${presente.fileName}`}
                      title={t('Scarica')}
                    >
                      <Download size={15} />
                    </button>
                    <button
                      type="button"
                      className="btn btn-sm btn-outline-danger"
                      onClick={() => elimina(kind.id)}
                      title={t('Elimina')}
                    >
                      <Trash2 size={15} />
                    </button>
                  </>
                )}
                <label className="btn btn-sm btn-outline-primary mb-0">
                  <Upload size={15} className="me-1" />
                  {presente ? t('Sostituisci') : t('Carica')}
                  <input
                    type="file"
                    className="d-none"
                    disabled={caricamento}
                    accept=".pdf,.xml,.csv,.txt,.jpg,.jpeg,.png,.webp"
                    onChange={(e) => {
                      carica(kind.id, e.target.files?.[0]);
                      // Azzerare permette di ricaricare lo stesso file dopo un
                      // errore: senza, il campo non emette un secondo change.
                      e.target.value = '';
                    }}
                  />
                </label>
              </div>
            </li>
          );
        })}
      </ul>

      <div className="form-text mt-2">
        {t('PDF, XML, CSV, testo o immagini. Massimo 10 MB. Un file per tipo: ricaricare sostituisce il precedente.')}
      </div>
    </div>
  );
};

export default AttachmentPanel;
