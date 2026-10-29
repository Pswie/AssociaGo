import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Form, Row, Col } from 'react-bootstrap';
import { Plus, Trash2, Calculator } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';

/**
 * Le voci aggiunte a un movimento: IVA, commissione, spese, altro.
 *
 * Un movimento si registra quando il denaro si muove, ma la cifra definitiva
 * arriva dopo — con la fattura, con la commissione del fornitore, con l'IVA.
 * Prima l'unico modo di sistemarla era riscrivere l'importo, e un importo
 * riscritto fa sparire la storia: a fine anno non si riconcilia più con quello
 * che si è dichiarato.
 *
 * <strong>Il totale non si scrive mai da qui.</strong> Si dichiara quanto
 * aggiungere; `amount = base + somma delle aggiunte` lo ricalcola il server, in
 * un punto solo. È la differenza fra un totale sempre giusto e uno giusto
 * finché qualcuno non dimentica di aggiornarlo.
 */
const TIPI = [
  { id: 'iva', label: 'IVA' },
  { id: 'commissione', label: 'Commissione' },
  { id: 'spese', label: 'Spese' },
  { id: 'altro', label: 'Altro' },
];

const TransactionAdditions = ({ transaction, onChange }) => {
  const { t } = useTranslation();
  const [voci, setVoci] = useState([]);
  const [errore, setErrore] = useState('');
  const [nuova, setNuova] = useState({ kind: 'iva', amount: '', description: '' });

  const ricarica = useCallback(async () => {
    if (!transaction?.id) return;
    try {
      setVoci(await associago.transactionAdditions.list(transaction.id));
      setErrore('');
    } catch (err) {
      setErrore(err.message || t('Voci non disponibili.'));
    }
  }, [transaction?.id, t]);

  useEffect(() => { ricarica(); }, [ricarica]);

  const aggiungi = async (evento) => {
    evento.preventDefault();
    setErrore('');
    const importo = parseFloat(nuova.amount);
    if (!Number.isFinite(importo)) {
      setErrore(t("Indica l'importo da aggiungere."));
      return;
    }
    try {
      await associago.transactionAdditions.add(transaction.id, {
        kind: nuova.kind,
        amount: importo,
        description: nuova.description || null,
      });
      setNuova({ kind: 'iva', amount: '', description: '' });
      await ricarica();
      // L'elenco dei movimenti mostra il totale, che è appena cambiato.
      onChange?.();
    } catch (err) {
      setErrore(err.message || t('Aggiunta non riuscita.'));
    }
  };

  const togli = async (id) => {
    try {
      await associago.transactionAdditions.remove(transaction.id, id);
      await ricarica();
      onChange?.();
    } catch (err) {
      setErrore(err.message || t('Rimozione non riuscita.'));
    }
  };

  const euro = (v) => `€${Number(v).toFixed(2)}`;
  const somma = voci.reduce((acc, v) => acc + Number(v.amount), 0);
  const base = transaction?.baseAmount ?? transaction?.amount ?? 0;

  return (
    <div>
      <div className="d-flex align-items-center gap-2 mb-2">
        <Calculator size={18} className="text-primary" />
        <span className="fw-bold">{t('Aggiunte al movimento')}</span>
      </div>

      {errore && <div className="alert alert-danger py-2 small">{errore}</div>}

      <Table size="sm" className="mb-2 align-middle">
        <tbody>
          <tr className="text-muted">
            <td>{t('Importo di partenza')}</td>
            <td className="text-end">{euro(base)}</td>
            <td style={{ width: '2.5rem' }}></td>
          </tr>
          {voci.map((v) => (
            <tr key={v.id}>
              <td>
                <span className="badge bg-light text-dark border me-2">{v.kind}</span>
                {v.description}
              </td>
              {/* Con segno: una rettifica in diminuzione è un valore negativo,
                  non una riscrittura dell'originale. */}
              <td className={`text-end ${Number(v.amount) < 0 ? 'text-danger' : ''}`}>
                {Number(v.amount) > 0 ? '+' : ''}{euro(v.amount)}
              </td>
              <td className="text-end">
                <Button variant="link" size="sm" className="text-danger p-0" onClick={() => togli(v.id)}>
                  <Trash2 size={14} />
                </Button>
              </td>
            </tr>
          ))}
          <tr className="fw-bold border-top">
            <td>{t('Totale del movimento')}</td>
            <td className="text-end">{euro(Number(base) + somma)}</td>
            <td></td>
          </tr>
        </tbody>
      </Table>

      <Form onSubmit={aggiungi}>
        <Row className="g-2 align-items-end">
          <Col xs={4} md={3}>
            <Form.Label className="small mb-1">{t('Tipo')}</Form.Label>
            <Form.Select
              size="sm"
              value={nuova.kind}
              onChange={(e) => setNuova({ ...nuova, kind: e.target.value })}
            >
              {TIPI.map((tipo) => <option key={tipo.id} value={tipo.id}>{t(tipo.label)}</option>)}
            </Form.Select>
          </Col>
          <Col xs={4} md={3}>
            <Form.Label className="small mb-1">{t('Importo')}</Form.Label>
            <Form.Control
              size="sm"
              type="number"
              step="0.01"
              placeholder="0,50"
              value={nuova.amount}
              onChange={(e) => setNuova({ ...nuova, amount: e.target.value })}
            />
          </Col>
          <Col xs={12} md={4}>
            <Form.Label className="small mb-1">{t('Descrizione')}</Form.Label>
            <Form.Control
              size="sm"
              value={nuova.description}
              onChange={(e) => setNuova({ ...nuova, description: e.target.value })}
            />
          </Col>
          <Col xs={12} md={2}>
            <Button type="submit" size="sm" className="w-100">
              <Plus size={15} className="me-1" />{t('Aggiungi')}
            </Button>
          </Col>
        </Row>
        <div className="form-text">
          {t('Un importo negativo corregge in diminuzione senza riscrivere quello di partenza.')}
        </div>
      </Form>
    </div>
  );
};

export default TransactionAdditions;
