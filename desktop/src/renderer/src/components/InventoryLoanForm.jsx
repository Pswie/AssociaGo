import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

const InventoryLoanForm = ({ itemId, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm();
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    const loadMembers = async () => {
      try {
        const item = await associago.inventory.getById(itemId);
        const memberships = await associago.memberships.getByAssociation(item.associationId);
        if (!cancelled) setMembers(memberships.filter(member => member.status === 'ACTIVE'));
      } catch (error) {
        if (!cancelled) setError(error.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    loadMembers();
    return () => { cancelled = true; };
  }, [itemId]);

  const onSubmit = async (data) => {
    try {
      await associago.inventory.createLoan({
        itemId: itemId,
        userAssociationId: Number(data.memberId),
        loanDate: `${data.loanDate}T00:00:00`,
        expectedReturnDate: data.expectedReturnDate ? `${data.expectedReturnDate}T23:59:59` : null,
        conditionOnLoan: data.conditionOnLoan || null,
        notes: data.notes,
        status: 'ACTIVE'
      });
      onSuccess();
    } catch (error) {
      console.error('Error creating loan:', error);
      setError(error.message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      <div className="mb-3">
        <label className="form-label">{t('Select Member')}</label>
        <select
          {...register('memberId', { required: t('Member is required') })}
          className="form-select"
          disabled={loading || isSubmitting}
        >
          <option value="">{t('Select...')}</option>
          {members.map(m => (
            <option key={m.id} value={m.id}>
              {m.user?.firstName} {m.user?.lastName}
            </option>
          ))}
        </select>
        {errors.memberId && <span className="text-danger small">{errors.memberId.message}</span>}
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label">{t('Loan Date')}</label>
          <input
            type="date"
            {...register('loanDate', { required: t('Loan Date is required') })}
            className="form-control"
            defaultValue={new Date().toLocaleDateString('en-CA')}
          />
        </div>
        <div className="col-md-6">
          <label className="form-label">{t('Expected Return Date')}</label>
          <input
            type="date"
            {...register('expectedReturnDate', { validate: (value, values) => !value || value >= values.loanDate || t('Return date must be on or after loan date.') })}
            className="form-control"
          />
          {errors.expectedReturnDate && <span className="text-danger small">{errors.expectedReturnDate.message}</span>}
        </div>
      </div>

      <div className="mb-3">
        <label className="form-label">{t('Condition on Loan')}</label>
        <select {...register('conditionOnLoan')} className="form-select">
          <option value="">{t('Select...')}</option>
          <option value="NEW">{t('New')}</option>
          <option value="GOOD">{t('Good')}</option>
          <option value="FAIR">{t('Fair')}</option>
          <option value="POOR">{t('Poor')}</option>
          <option value="BROKEN">{t('Broken')}</option>
        </select>
      </div>

      <div className="mb-3">
        <label className="form-label">{t('Notes')}</label>
        <textarea
          {...register('notes')}
          rows="3"
          className="form-control"
        />
      </div>

      <div className="d-flex justify-content-end gap-2 mt-4">
        <button type="button" onClick={onCancel} className="btn btn-secondary">
          {t('Cancel')}
        </button>
        <button type="submit" disabled={isSubmitting || loading || !members.length} className="btn btn-primary">
          {isSubmitting ? t('Saving...') : t('Register Loan')}
        </button>
      </div>
    </form>
  );
};

export default InventoryLoanForm;
