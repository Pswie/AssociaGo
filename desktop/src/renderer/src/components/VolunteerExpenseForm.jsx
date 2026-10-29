import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

/**
 * Un rimborso a un volontario — art. 17 D.Lgs. 117/2017.
 *
 * Due regimi, e la scelta cambia cosa serve per approvarlo:
 *
 *  * <strong>analitico</strong> (comma 3): spesa effettivamente sostenuta.
 *    Nessun tetto, ma il giustificativo è obbligatorio — si allega dopo, dalla
 *    scheda del volontario, e senza di quello l'approvazione viene respinta.
 *  * <strong>forfettario</strong> (comma 4): autocertificazione ex art. 46 DPR
 *    445/2000, ammesso solo entro 10 euro al giorno e 150 al mese.
 *
 * Prima il modulo non chiedeva né il regime né l'autocertificazione: ogni
 * rimborso nasceva analitico per impostazione predefinita e il forfettario era
 * irraggiungibile.
 */
const VolunteerExpenseForm = ({ volunteerId, associationId, expense, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const [error, setError] = useState('');
  const { register, handleSubmit, watch, formState: { isSubmitting } } = useForm({
    defaultValues: expense || {
      expenseDate: new Date().toISOString().split('T')[0],
      description: '',
      amount: 0,
      reimbursementType: 'ANALITICO',
      selfCertified: false,
      status: 'PENDING',
    },
  });

  const regime = watch('reimbursementType');

  const onSubmit = async (data) => {
    setError('');
    try {
      const payload = {
        ...data,
        volunteerId,
        // Senza questo il pagamento non si può registrare in contabilità: da un
        // volontario non si risale alla sua associazione con nessuna JOIN.
        associationId,
        amount: parseFloat(data.amount),
      };

      if (expense?.id) {
        await associago.volunteers.updateExpenseStatus(expense.id, data.status, associationId);
      } else {
        await associago.volunteers.createExpense(payload);
      }
      onSuccess();
    } catch (error) {
      console.error('Error saving expense:', error);
      setError(error.message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      <div className="mb-3">
        <label className="form-label">{t('Regime di rimborso')}</label>
        <select {...register('reimbursementType')} className="form-select">
          <option value="ANALITICO">{t('Analitico — spesa documentata (art. 17 c. 3)')}</option>
          <option value="FORFETTARIO">{t('Forfettario — autocertificato (art. 17 c. 4)')}</option>
        </select>
        <div className="form-text">
          {regime === 'FORFETTARIO'
            ? t('Ammesso entro 10 € al giorno e 150 € al mese, e solo se l\'organo sociale ha deliberato le tipologie di spesa.')
            : t('Nessun tetto, ma senza giustificativo non si può approvare.')}
        </div>
      </div>

      <div className="mb-3">
        <label className="form-label">{t('Date')}</label>
        <input
          type="date"
          {...register('expenseDate', { required: true })}
          className="form-control"
          readOnly={Boolean(expense?.id)}
        />
      </div>

      <div className="mb-3">
        <label className="form-label">{t('Description')}</label>
        <input
          {...register('description', { required: true })}
          className="form-control"
          placeholder={t('e.g. Travel reimbursement')}
          readOnly={Boolean(expense?.id)}
        />
      </div>

      <div className="mb-3">
        <label className="form-label">{t('Amount')} (&euro;)</label>
        <input
          type="number"
          step="0.01"
          min="0.01"
          {...register('amount', { required: true, min: 0.01 })}
          className="form-control"
          readOnly={Boolean(expense?.id)}
        />
      </div>

      {expense?.id && <div className="mb-3">
        <label className="form-label" htmlFor="expense-status">{t('Status')}</label>
        <select id="expense-status" className="form-select" {...register('status')}>
          {['PENDING', 'APPROVED', 'REJECTED', 'PAID'].map(status => <option key={status} value={status}>{t(status)}</option>)}
        </select>
      </div>}

      {regime === 'FORFETTARIO' && (
        <div className="form-check mb-3">
          <input
            type="checkbox"
            {...register('selfCertified')}
            className="form-check-input"
            id="expense-self-certified"
          />
          <label className="form-check-label" htmlFor="expense-self-certified">
            {t('Il volontario ha reso l\'autocertificazione')}
          </label>
          <div className="form-text">
            {t('Senza autocertificazione il rimborso forfettario non è ammissibile.')}
          </div>
        </div>
      )}

      <div className="alert alert-info py-2 small">
        {regime === 'ANALITICO'
          ? t('Dopo il salvataggio allega la ricevuta dalla scheda del volontario: serve per approvare.')
          : t('I tetti si calcolano sull\'insieme del mese dello stesso volontario.')}
      </div>

      <div className="d-flex justify-content-end gap-2 mt-4">
        <button type="button" onClick={onCancel} className="btn btn-secondary">
          {t('Cancel')}
        </button>
        <button type="submit" disabled={isSubmitting} className="btn btn-primary">
          {isSubmitting ? t('Saving...') : t('Save Expense')}
        </button>
      </div>
    </form>
  );
};

export default VolunteerExpenseForm;
