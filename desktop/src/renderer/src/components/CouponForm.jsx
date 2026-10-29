import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

/**
 * Un buono dato a una persona.
 *
 * Non è uno sconto. Un buono da 10 euro consegnato a qualcuno è denaro che esce
 * dall'associazione: all'emissione nasce un movimento in uscita da 10, e se poi
 * arrivano IVA o commissioni si aggiungono su quel movimento senza riscrivere
 * l'importo originale.
 *
 * I campi dello sconto sulle quote (percentuale, importo minimo, attività e
 * eventi applicabili) non ci sono più: uno sconto è denaro che non entra, un
 * buono è denaro che esce, e sommarli darebbe un rendiconto falso in entrambe
 * le colonne.
 */
const CouponForm = ({ associationId, coupon, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const { register, handleSubmit, watch, formState: { errors, isSubmitting } } = useForm({
    defaultValues: coupon || {
      code: '',
      description: '',
      nominalValue: '',
      beneficiaryKind: 'member',
      beneficiaryMemberId: '',
      beneficiaryRef: '',
      issuedDate: new Date().toISOString().split('T')[0],
      startDate: '',
      endDate: '',
      isActive: true,
    },
  });

  const [members, setMembers] = useState([]);
  const [errore, setErrore] = useState('');
  const beneficiaryKind = watch('beneficiaryKind');
  const giaEmesso = Boolean(coupon?.transactionId);

  useEffect(() => {
    associago.members.getAll()
      .then(setMembers)
      .catch((err) => console.error('Caricamento soci non riuscito', err));
  }, [associationId]);

  const onSubmit = async (data) => {
    setErrore('');
    const payload = {
      associationId,
      code: data.code,
      description: data.description,
      nominalValue: parseFloat(data.nominalValue),
      // Uno solo dei due, mai entrambi: sono due specie diverse di destinatario.
      beneficiaryMemberId: data.beneficiaryKind === 'member' && data.beneficiaryMemberId
        ? parseInt(data.beneficiaryMemberId, 10)
        : null,
      beneficiaryRef: data.beneficiaryKind === 'ref' ? (data.beneficiaryRef || '').trim() : null,
      issuedDate: data.issuedDate || null,
      startDate: data.startDate || null,
      endDate: data.endDate || null,
      isActive: data.isActive,
    };

    try {
      if (coupon?.id) {
        await associago.coupons.update(coupon.id, payload);
      } else {
        // `issue` e non `create`: crea anche il movimento in uscita. Un buono
        // senza il suo movimento sarebbe denaro sparito dai conti.
        await associago.coupons.issue(payload);
      }
      onSuccess();
    } catch (error) {
      setErrore(error.message || t('Salvataggio non riuscito'));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      {errore && <div className="alert alert-danger py-2">{errore}</div>}

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label">{t('Codice del buono')}</label>
          <input {...register('code', { required: true })} className="form-control" />
          {errors.code && <div className="form-text text-danger">{t('Obbligatorio')}</div>}
        </div>
        <div className="col-md-6">
          <label className="form-label">{t('Valore in euro')}</label>
          <input
            type="number"
            step="0.01"
            min="0.01"
            disabled={giaEmesso}
            {...register('nominalValue', { required: true })}
            className="form-control"
          />
          <div className="form-text">
            {giaEmesso
              ? t("Il valore non si modifica dopo l'emissione: correggilo aggiungendo una voce sul movimento in uscita.")
              : t("È anche l'importo che esce dal conto.")}
          </div>
        </div>
      </div>

      <div className="mb-3">
        <label className="form-label">{t('Descrizione')}</label>
        <input {...register('description')} className="form-control" />
      </div>

      <fieldset className="mb-3 border rounded p-3">
        <legend className="float-none w-auto px-2 fs-6">{t('A chi va')}</legend>

        <div className="form-check">
          <input
            type="radio"
            value="member"
            id="benef-member"
            {...register('beneficiaryKind')}
            className="form-check-input"
          />
          <label className="form-check-label" htmlFor="benef-member">{t('Un socio o un volontario')}</label>
        </div>
        {beneficiaryKind === 'member' && (
          <select {...register('beneficiaryMemberId')} className="form-select mt-2 mb-3">
            <option value="">{t('— scegli —')}</option>
            {members.map((m) => (
              <option key={m.id} value={m.id}>{m.lastName} {m.firstName}</option>
            ))}
          </select>
        )}

        <div className="form-check">
          <input
            type="radio"
            value="ref"
            id="benef-ref"
            {...register('beneficiaryKind')}
            className="form-check-input"
          />
          <label className="form-check-label" htmlFor="benef-ref">
            {t('Una persona seguita dai servizi')}
          </label>
        </div>
        {beneficiaryKind === 'ref' && (
          <>
            <input
              {...register('beneficiaryRef')}
              className="form-control mt-2"
              placeholder="ASS-0042"
            />
            {/* Il vincolo non è formale: l'anagrafica degli assistiti sta su un
                altro sistema, con i nomi dentro buste cifrate. Scriverne uno qui
                farebbe di questo programma un secondo posto dove i nomi delle
                persone fragili stanno in chiaro. */}
            <div className="form-text">
              {t('Il codice della persona in anagrafica, non il suo nome.')}
            </div>
          </>
        )}
      </fieldset>

      <div className="row g-3 mb-3">
        <div className="col-md-4">
          <label className="form-label">{t('Data di consegna')}</label>
          <input type="date" {...register('issuedDate')} className="form-control" />
        </div>
        <div className="col-md-4">
          <label className="form-label">{t('Spendibile dal')}</label>
          <input type="date" {...register('startDate')} className="form-control" />
        </div>
        <div className="col-md-4">
          <label className="form-label">{t('Spendibile fino al')}</label>
          <input type="date" {...register('endDate')} className="form-control" />
        </div>
      </div>

      <div className="form-check mb-3">
        <input type="checkbox" {...register('isActive')} className="form-check-input" id="coupon-active" />
        <label className="form-check-label" htmlFor="coupon-active">{t('Attivo')}</label>
      </div>

      {!coupon?.id && (
        <div className="alert alert-info py-2 small">
          {t("Salvando, il buono viene emesso e nasce l'uscita corrispondente in contabilità.")}
        </div>
      )}

      <div className="d-flex justify-content-end gap-2 mt-4">
        <button type="button" onClick={onCancel} className="btn btn-secondary">{t('Annulla')}</button>
        <button type="submit" disabled={isSubmitting} className="btn btn-primary">
          {isSubmitting ? t('Salvataggio…') : (coupon?.id ? t('Salva') : t('Emetti il buono'))}
        </button>
      </div>
    </form>
  );
};

export default CouponForm;
