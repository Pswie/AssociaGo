import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

const ActivityForm = ({ associationId, activityId, activity, canFinance = Boolean(window.api), onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const id = activityId || activity?.id;
  const [loading, setLoading] = useState(Boolean(id));
  const [loadError, setLoadError] = useState('');
  const [loaded, setLoaded] = useState(!id);
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({
    defaultValues: { category: 'COURSE', subscriptionType: 'ONE_TIME', requireRegistration: true, isActive: true }
  });

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      if (!id) return;
      try {
        const existing = activity || await associago.activities.getById(id);
        if (!cancelled) { reset(existing); setLoaded(true); }
      } catch (error) {
        if (!cancelled) setLoadError(error.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [id, activity, reset]);

  const onSubmit = async (data) => {
    setLoadError('');
    try {
      const payload = {
          associationId: associationId,
          name: data.name,
          description: data.description,
          category: data.category,
          startDate: data.startDate,
          endDate: data.endDate || null,
          startTime: data.startTime || null,
          endTime: data.endTime || null,
          location: data.location,
          maxParticipants: data.maxParticipants ? parseInt(data.maxParticipants) : null,
          ...(canFinance ? { cost: data.cost ? parseFloat(data.cost) : 0 } : {}),
          isActive: data.isActive ?? true,
          // New fields
          subscriptionType: data.subscriptionType,
          scheduleDetails: data.scheduleDetails,
          requireRegistration: data.requireRegistration,
          generateInvoice: data.generateInvoice
      };
      if (id) await associago.activities.update(id, payload);
      else await associago.activities.create(payload);

      onSuccess();
    } catch (error) {
      console.error('Error saving activity:', error);
      setLoadError(error.message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      {loadError && <div className="alert alert-danger" role="alert">{loadError}</div>}
      <fieldset disabled={loading || isSubmitting}>
      <div className="mb-3">
        <label htmlFor="activity-name" className="form-label">{t('Activity Name')}</label>
        <input id="activity-name"
          {...register('name', { required: t('Name is required') })}
          className="form-control"
        />
        {errors.name && <span className="text-danger small">{errors.name.message}</span>}
      </div>

      <div className="mb-3">
        <label htmlFor="activity-description" className="form-label">{t('Description')}</label>
        <textarea id="activity-description"
          {...register('description')}
          rows="3"
          className="form-control"
        />
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="activity-category" className="form-label">{t('Category')}</label>
          <select id="activity-category"
            {...register('category', { required: t('Category is required') })}
            className="form-select"
          >
            <option value="COURSE">{t('Course')}</option>
            <option value="WORKSHOP">{t('Workshop')}</option>
            <option value="SEMINAR">{t('Seminar')}</option>
            <option value="OTHER">{t('Other')}</option>
          </select>
        </div>
        <div className="col-md-6">
          <label htmlFor="activity-subscriptionType" className="form-label">{t('Subscription Type')}</label>
          <select id="activity-subscriptionType" {...register('subscriptionType')} className="form-select">
            <option value="ONE_TIME">{t('One Time')}</option>
            <option value="MONTHLY">{t('Monthly')}</option>
            <option value="ANNUAL">{t('Annual')}</option>
          </select>
        </div>
      </div>

      <div className="row g-3 mb-3">
        {canFinance && <div className="col-md-6">
          <label htmlFor="activity-cost" className="form-label">{t('Cost')} (€)</label>
          <input id="activity-cost"
            type="number"
            step="0.01"
            min="0"
            {...register('cost', { min: 0 })}
            className="form-control"
          />
        </div>}
        <div className="col-md-6">
            <label htmlFor="activity-scheduleDetails" className="form-label">{t('Schedule Details')}</label>
            <input id="activity-scheduleDetails"
                type="text"
                {...register('scheduleDetails')}
                className="form-control"
                placeholder={t('e.g., Mon/Wed 18-20')}
            />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="activity-startDate" className="form-label">{t('Start Date')}</label>
          <input id="activity-startDate"
            type="date"
            {...register('startDate', { required: t('Start date is required') })}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="activity-endDate" className="form-label">{t('End Date')}</label>
          <input id="activity-endDate"
            type="date"
            {...register('endDate', { validate: (value, values) => !value || value >= values.startDate || t('End date must be on or after start date.') })}
            className="form-control"
          />
          {errors.endDate && <span className="text-danger small">{errors.endDate.message}</span>}
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="activity-startTime" className="form-label">{t('Start Time')}</label>
          <input id="activity-startTime"
            type="time"
            {...register('startTime')}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="activity-endTime" className="form-label">{t('End Time')}</label>
          <input id="activity-endTime"
            type="time"
            {...register('endTime')}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-8">
          <label htmlFor="activity-location" className="form-label">{t('Location')}</label>
          <input id="activity-location"
            {...register('location')}
            className="form-control"
          />
        </div>
        <div className="col-md-4">
          <label htmlFor="activity-maxParticipants" className="form-label">{t('Max Participants')}</label>
          <input id="activity-maxParticipants"
            type="number"
            min="1"
            {...register('maxParticipants', { min: 1 })}
            className="form-control"
          />
        </div>
      </div>

      <div className="mb-3">
          <div className="form-check form-check-inline">
              <input className="form-check-input" type="checkbox" id="requireRegistration" {...register('requireRegistration')} />
              <label className="form-check-label" htmlFor="requireRegistration">{t('Require Registration')}</label>
          </div>
          <div className="form-check form-check-inline">
              <input className="form-check-input" type="checkbox" id="generateInvoice" {...register('generateInvoice')} />
              <label className="form-check-label" htmlFor="generateInvoice">{t('Generate Invoice')}</label>
          </div>
      </div>

      </fieldset>
      <div className="d-flex justify-content-end gap-2 mt-4">
        <button
          type="button"
          onClick={onCancel}
          className="btn btn-secondary"
        >
          {t('Cancel')}
        </button>
        <button
          type="submit"
          disabled={isSubmitting || loading || !loaded}
          className="btn btn-primary"
        >
          {isSubmitting ? t('Saving...') : t('Save Activity')}
        </button>
      </div>
    </form>
  );
};

export default ActivityForm;
