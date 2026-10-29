import React, { useState, useEffect, useCallback } from 'react';
import { Card, Table, Badge, Button, Spinner, Modal } from 'react-bootstrap';
import { FileText, Upload, AlertTriangle, Paperclip, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';
import AttachmentPanel from './AttachmentPanel';

/**
 * Le fatture che l'associazione riceve.
 *
 * Il backend c'era già per intero: elenco, scadute, e un lettore della fattura
 * elettronica che dall'XML ricava fornitore, numero, imponibile e IVA. Non era
 * esposto da nessuna parte — una funzione completa che nessuno poteva usare.
 *
 * L'allegato sta nel database e non su disco: `expense_documents.file_path`
 * puntava al disco locale del contenitore, che su Cloud Run si azzera a ogni
 * riavvio, e la colonna continuava a sostenere che il file ci fosse.
 */
const ExpenseDocumentList = ({ associationId, shell }) => {
  const { t } = useTranslation();
  // Una fattura è un fatto puntuale: appartiene all'anno della sua data e a
  // nessun altro.
  const anno = shell?.anno ?? new Date().getFullYear();
  const [documenti, setDocumenti] = useState([]);
  const [scadute, setScadute] = useState([]);
  const [caricamento, setCaricamento] = useState(true);
  const [errore, setErrore] = useState('');
  const [avviso, setAvviso] = useState('');
  const [aperto, setAperto] = useState(null);

  const ricarica = useCallback(async () => {
    if (!associationId) return;
    setCaricamento(true);
    try {
      const [tutti, inRitardo] = await Promise.all([
        associago.expenseDocuments.list(associationId, anno),
        associago.expenseDocuments.overdue(associationId),
      ]);
      setDocumenti(tutti);
      // Le scadute restano quelle dell'esercizio guardato: una fattura del 2026
      // non pagata non è un problema del 2027, e mostrarla lì confonderebbe il
      // conto di chi sta chiudendo l'anno.
      setScadute(inRitardo);
      setErrore('');
    } catch (err) {
      setErrore(err.message || t('Fatture non disponibili.'));
    } finally {
      setCaricamento(false);
    }
  }, [associationId, anno, t]);

  useEffect(() => { ricarica(); }, [ricarica]);

  const importaXml = async (file) => {
    if (!file) return;
    setErrore('');
    setAvviso('');
    try {
      const esito = await associago.expenseDocuments.importXml(file, associationId);
      // `alreadyPresent` non è un errore: è la difesa contro il doppione in
      // prima nota, e va detta come informazione, non come fallimento.
      setAvviso(esito.message);
      await ricarica();
    } catch (err) {
      setErrore(err.message || t('Importazione non riuscita.'));
    }
  };

  const elimina = async (id) => {
    if (!confirm(t('Eliminare questa fattura e il suo allegato?'))) return;
    try {
      await associago.expenseDocuments.delete(id);
      await ricarica();
    } catch (err) {
      setErrore(err.message || t('Eliminazione non riuscita.'));
    }
  };

  const euro = (v) => (v == null ? '—' : `€${Number(v).toFixed(2)}`);

  const statoBadge = (doc) => {
    const inRitardo = scadute.some((s) => s.id === doc.id);
    if (inRitardo) return <Badge bg="danger">{t('scaduta')}</Badge>;
    if (doc.status === 'pagata') return <Badge bg="success">{t('pagata')}</Badge>;
    if (doc.status === 'registrata') return <Badge bg="primary">{t('registrata')}</Badge>;
    return <Badge bg="warning" text="dark">{t('da pagare')}</Badge>;
  };

  return (
    <div className="fade-in">
      <div className="d-flex justify-content-between align-items-start mb-4 flex-wrap gap-2">
        <div>
          <h2 className="fw-bold text-dark mb-1">{t('Fatture ricevute')}</h2>
          <p className="text-muted mb-0">
            {t('Fatture, ricevute e note che arrivano dai fornitori. Il file resta nel database, non su disco.')}
          </p>
        </div>
        <label className="btn btn-primary d-flex align-items-center mb-0">
          <Upload size={18} className="me-2" />
          {t('Importa fattura elettronica')}
          <input
            type="file"
            className="d-none"
            accept=".xml,.p7m"
            onChange={(e) => { importaXml(e.target.files?.[0]); e.target.value = ''; }}
          />
        </label>
      </div>

      {errore && <div className="alert alert-danger py-2">{errore}</div>}
      {avviso && <div className="alert alert-info py-2">{avviso}</div>}

      {scadute.length > 0 && (
        <div className="alert alert-danger d-flex gap-2 align-items-center">
          <AlertTriangle size={20} className="flex-shrink-0" />
          <div>{t('{{n}} fatture sono scadute e non risultano pagate.', { n: scadute.length })}</div>
        </div>
      )}

      <Card className="border-0 shadow-sm">
        <Card.Body className="p-0">
          {caricamento ? (
            <div className="text-center p-5"><Spinner animation="border" variant="primary" /></div>
          ) : (
            <Table hover responsive className="mb-0 align-middle">
              <thead className="bg-light">
                <tr>
                  <th className="border-0 ps-4">{t('Fornitore')}</th>
                  <th className="border-0">{t('Numero')}</th>
                  <th className="border-0">{t('Data')}</th>
                  <th className="border-0 text-end">{t('Totale')}</th>
                  <th className="border-0 text-end">{t('di cui IVA')}</th>
                  <th className="border-0">{t('Stato')}</th>
                  <th className="border-0 text-end pe-4">{t('Azioni')}</th>
                </tr>
              </thead>
              <tbody>
                {documenti.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="text-center text-muted py-5">
                      <FileText size={32} className="mb-2 opacity-50 d-block mx-auto" />
                      {t('Nessuna fattura registrata.')}
                    </td>
                  </tr>
                ) : documenti.map((doc) => (
                  <tr key={doc.id}>
                    <td className="ps-4 fw-semibold">
                      {doc.supplierName}
                      {doc.supplierVat && (
                        <div className="small text-muted font-monospace">{doc.supplierVat}</div>
                      )}
                    </td>
                    <td className="font-monospace small">{doc.number || '—'}</td>
                    <td className="small">
                      {doc.documentDate}
                      {doc.dueDate && (
                        <div className="text-muted">{t('scade')} {doc.dueDate}</div>
                      )}
                    </td>
                    <td className="text-end fw-semibold">{euro(doc.totalAmount)}</td>
                    {/* L'IVA si mostra ma non si sottrae: un'APS senza partita
                        IVA sull'attività istituzionale non la detrae, quindi
                        l'uscita è il totale. Il campo è documentazione. */}
                    <td className="text-end small text-muted">{euro(doc.vatAmount)}</td>
                    <td>{statoBadge(doc)}</td>
                    <td className="text-end pe-4">
                      <Button
                        variant="outline-secondary"
                        size="sm"
                        className="me-1"
                        onClick={() => setAperto(doc)}
                        title={t('Allegato')}
                      >
                        <Paperclip size={15} />
                      </Button>
                      <Button
                        variant="outline-danger"
                        size="sm"
                        onClick={() => elimina(doc.id)}
                        title={t('Elimina')}
                      >
                        <Trash2 size={15} />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </Table>
          )}
        </Card.Body>
      </Card>

      <Modal show={Boolean(aperto)} onHide={() => setAperto(null)} centered>
        <Modal.Header closeButton>
          <Modal.Title className="fs-5">
            {aperto?.supplierName} · {aperto?.number || t('senza numero')}
          </Modal.Title>
        </Modal.Header>
        <Modal.Body>
          {aperto && (
            <AttachmentPanel
              ownerType="EXPENSE_DOCUMENT"
              ownerId={aperto.id}
              titolo={t('Documento della fattura')}
              kinds={[
                { id: 'fattura', label: t('Fattura (PDF o XML)') },
                { id: 'ricevuta', label: t('Ricevuta di pagamento') },
              ]}
            />
          )}
        </Modal.Body>
      </Modal>
    </div>
  );
};

export default ExpenseDocumentList;
