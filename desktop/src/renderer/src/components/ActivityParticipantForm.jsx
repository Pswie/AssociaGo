import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

const ActivityParticipantForm = ({ activityId, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm();
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError('');
    const load = async () => {
      try {
        if (!activityId) throw new Error('Attività non disponibile. Riapri il dettaglio.');
        const data = await associago.activities.getParticipantCandidates(activityId);
        if (!cancelled) setMembers((data || []).filter(member => Number.isSafeInteger(member.userId) && member.userId > 0));
      } catch (err) {
        if (!cancelled) { setMembers([]); setError(err.message || 'Soci non disponibili. Riprova.'); }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [activityId, revision]);

  const onSubmit = async (data) => {
    const member = members.find(item => String(item.userId) === data.userId);
    if (!member) return;
    setError('');
    try {
      const now = new Date();
      await associago.activities.addParticipant(activityId, {
        user: { id: member.userId },
        registrationDate: `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`,
        isActive: true
      });
      onSuccess();
    } catch (error) {
      console.error('Error adding participant:', error);
      setError(error.message || t('Error adding participant'));
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      {error && <div className="alert alert-danger" role="alert">{error} <button type="button" className="btn btn-link" onClick={() => setRevision(value => value + 1)}>{t('Retry')}</button></div>}
      <div className="mb-3">
        <label className="form-label" htmlFor="activity-participant-membership">{t('Select Member')}</label>
        <select
          id="activity-participant-membership"
          {...register('userId', { required: t('Member is required') })}
          className="form-select"
          disabled={loading || isSubmitting}
        >
          <option value="">{loading ? t('Loading...') : t('Select...')}</option>
          {members.map(m => (
            <option key={m.userId} value={m.userId}>
              {m.firstName} {m.lastName}{m.membershipCardNumber ? ` · ${m.membershipCardNumber}` : ''}
            </option>
          ))}
        </select>
        {errors.userId && <span className="text-danger small">{errors.userId.message}</span>}
        {!loading && !error && !members.length && <p className="text-muted mt-2">Non ci sono soci disponibili per questa associazione.</p>}
      </div>

      <div className="d-flex justify-content-end gap-2 mt-4">
        <button type="button" onClick={onCancel} className="btn btn-secondary">
          {t('Cancel')}
        </button>
        <button type="submit" disabled={isSubmitting || loading || !members.length} className="btn btn-primary">
          {isSubmitting ? t('Saving...') : t('Add Participant')}
        </button>
      </div>
    </form>
  );
};

export default ActivityParticipantForm;
