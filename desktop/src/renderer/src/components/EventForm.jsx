import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

const EventForm = ({ associationId, eventId, event, onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const id = eventId || event?.id;
  const isEditMode = Boolean(id);
  const [loading, setLoading] = useState(isEditMode);
  const [loaded, setLoaded] = useState(!isEditMode);
  const [error, setError] = useState('');
  const { register, handleSubmit, reset, formState: { errors, isSubmitting } } = useForm({
    defaultValues: {
      name: '',
      description: '',
      type: 'MEETING',
      startDate: '',
      startTime: '',
      endDate: '',
      endTime: '',
      location: '',
      address: '',
      maxParticipants: '',
      costMember: '',
      costNonMember: '',
      isPublic: false,
      status: 'PLANNED',
      requireRegistration: true,
      generateInvoice: false
    }
  });

  useEffect(() => {
    let cancelled = false;
    if (isEditMode) {
      Promise.resolve(event || associago.events.getById(id)).then(data => {
        if (cancelled) return;
        reset({
          name: data.name || '',
          description: data.description || '',
          type: data.type || 'MEETING',
          startDate: data.startDatetime?.slice(0, 10) || '',
          startTime: data.startDatetime?.slice(11, 16) || '',
          endDate: data.endDatetime?.slice(0, 10) || '',
          endTime: data.endDatetime?.slice(11, 16) || '',
          location: data.location || '',
          address: data.address || '',
          maxParticipants: data.maxParticipants ?? '',
          costMember: data.costMember ?? '',
          costNonMember: data.costNonMember ?? '',
          isPublic: data.public ?? data.isPublic ?? false,
          status: data.status || 'PLANNED',
          requireRegistration: data.requireRegistration ?? true,
          generateInvoice: data.generateInvoice ?? false
        });
        setLoaded(true);
      }).catch(err => { if (!cancelled) setError(err.message); })
        .finally(() => { if (!cancelled) setLoading(false); });
    }
    return () => { cancelled = true; };
  }, [id, isEditMode, event, reset]);

  const onSubmit = async (data) => {
    setError('');
    try {
      const payload = {
        associationId: associationId,
        name: data.name,
        description: data.description,
        type: data.type,
        startDatetime: `${data.startDate}T${data.startTime || '00:00:00'}`,
        endDatetime: data.endDate ? `${data.endDate}T${data.endTime || '00:00:00'}` : null,
        location: data.location,
        address: data.address,
        maxParticipants: data.maxParticipants ? parseInt(data.maxParticipants) : null,
        costMember: data.costMember ? parseFloat(data.costMember) : 0,
        costNonMember: data.costNonMember ? parseFloat(data.costNonMember) : 0,
        public: data.isPublic,
        status: data.status,
        requireRegistration: data.requireRegistration,
        generateInvoice: data.generateInvoice
      };

      if (isEditMode) {
        await associago.events.update(id, payload);
      } else {
        await associago.events.create(payload);
      }

      onSuccess();
    } catch (error) {
      console.error('Error saving event:', error);
      setError(error.message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      {error && <div className="alert alert-danger" role="alert">{error}</div>}
      {loading && <p role="status">{t('Loading...')}</p>}
      <fieldset disabled={loading || isSubmitting || !loaded}>
      <div className="mb-3">
        <label htmlFor="event-name" className="form-label">{t('Event Name')}</label>
        <input id="event-name"
          {...register('name', { required: t('Name is required') })}
          className="form-control"
        />
        {errors.name && <span className="text-danger small">{errors.name.message}</span>}
      </div>

      <div className="mb-3">
        <label htmlFor="event-description" className="form-label">{t('Description')}</label>
        <textarea id="event-description"
          {...register('description')}
          rows="3"
          className="form-control"
        />
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="event-type" className="form-label">{t('Type')}</label>
          <select id="event-type"
            {...register('type', { required: t('Type is required') })}
            className="form-select"
          >
            <option value="MEETING">{t('Meeting / Assembly')}</option>
            <option value="PARTY">{t('Party')}</option>
            <option value="FUNDRAISER">{t('Fundraiser')}</option>
            <option value="CONGRESS">{t('Congress')}</option>
            <option value="CONFERENCE">{t('Conference')}</option>
            <option value="OTHER">{t('Other')}</option>
          </select>
        </div>
        <div className="col-md-6">
          <label htmlFor="event-status" className="form-label">{t('Status')}</label>
          <select id="event-status"
            {...register('status')}
            className="form-select"
          >
            <option value="PLANNED">{t('Planned')}</option>
            <option value="CONFIRMED">{t('Confirmed')}</option>
            <option value="IN_PROGRESS">{t('In Progress')}</option>
            <option value="COMPLETED">{t('Completed')}</option>
            <option value="CANCELLED">{t('Cancelled')}</option>
          </select>
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="event-startDate" className="form-label">{t('Start Date')}</label>
          <input id="event-startDate"
            type="date"
            {...register('startDate', { required: t('Start date is required') })}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="event-startTime" className="form-label">{t('Start Time')}</label>
          <input id="event-startTime"
            type="time"
            {...register('startTime')}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="event-endDate" className="form-label">{t('End Date')}</label>
          <input id="event-endDate"
            type="date"
            {...register('endDate', { validate: (value, values) => !value || `${value}T${values.endTime || '00:00'}` >= `${values.startDate}T${values.startTime || '00:00'}` || t('La fine deve essere successiva all’inizio.') })}
            className="form-control"
          />
          {errors.endDate && <span className="text-danger small">{errors.endDate.message}</span>}
        </div>
        <div className="col-md-6">
          <label htmlFor="event-endTime" className="form-label">{t('End Time')}</label>
          <input id="event-endTime"
            type="time"
            {...register('endTime')}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label htmlFor="event-location" className="form-label">{t('Location')}</label>
          <input id="event-location"
            {...register('location')}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label htmlFor="event-address" className="form-label">{t('Address')}</label>
          <input id="event-address"
            {...register('address')}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-4">
          <label htmlFor="event-maxParticipants" className="form-label">{t('Max Participants')}</label>
          <input id="event-maxParticipants"
            type="number"
            min="1"
            {...register('maxParticipants')}
            className="form-control"
            placeholder={t('Unlimited')}
          />
        </div>
        <div className="col-md-4">
          <label htmlFor="event-costMember" className="form-label">{t('Member Cost')} (&euro;)</label>
          <input id="event-costMember"
            type="number"
            step="0.01"
            min="0"
            {...register('costMember')}
            className="form-control"
          />
        </div>
        <div className="col-md-4">
          <label htmlFor="event-costNonMember" className="form-label">{t('Non-Member Cost')} (&euro;)</label>
          <input id="event-costNonMember"
            type="number"
            step="0.01"
            min="0"
            {...register('costNonMember')}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-4">
          <div className="form-check">
            <input
              type="checkbox"
              {...register('isPublic')}
              className="form-check-input"
              id="isPublic"
            />
            <label className="form-check-label" htmlFor="isPublic">
              {t('Public Event')}
            </label>
          </div>
        </div>
        <div className="col-md-4">
          <div className="form-check">
            <input
              type="checkbox"
              {...register('requireRegistration')}
              className="form-check-input"
              id="requireRegistration"
            />
            <label className="form-check-label" htmlFor="requireRegistration">
              {t('Require Registration')}
            </label>
          </div>
        </div>
        <div className="col-md-4">
          <div className="form-check">
            <input
              type="checkbox"
              {...register('generateInvoice')}
              className="form-check-input"
              id="generateInvoice"
            />
            <label className="form-check-label" htmlFor="generateInvoice">
              {t('Generate Invoice')}
            </label>
          </div>
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
          {isSubmitting ? t('Saving...') : (isEditMode ? t('Update Event') : t('Save Event'))}
        </button>
      </div>
    </form>
  );
};

export default EventForm;
