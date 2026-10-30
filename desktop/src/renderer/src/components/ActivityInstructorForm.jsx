import React from 'react';
import { useForm } from 'react-hook-form';
import { associago } from '../api';
import { useTranslation } from 'react-i18next';

const ActivityInstructorForm = ({ activityId, instructor, canFinance = Boolean(window.api), onSuccess, onCancel }) => {
  const { t } = useTranslation();
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({
    defaultValues: instructor || {
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      specialization: '',
      certifications: '',
      compensation: 0,
      compensationType: 'HOURLY',
      active: true
    }
  });

  const onSubmit = async (data) => {
    try {
      const payload = { ...data };
      if (!canFinance) {
        delete payload.compensation;
        delete payload.compensationType;
      }
      if (instructor?.id) {
        await associago.activities.updateInstructor(instructor.id, payload);
      } else {
        await associago.activities.addInstructor(activityId, payload);
      }
      onSuccess();
    } catch (error) {
      console.error('Error saving instructor:', error);
      alert(t('Error saving instructor') + ': ' + error.message);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="p-2">
      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-firstName">{t('First Name')}</label>
          <input id="activity-instructor-firstName"
            {...register('firstName', { required: t('First Name is required') })}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-lastName">{t('Last Name')}</label>
          <input id="activity-instructor-lastName"
            {...register('lastName', { required: t('Last Name is required') })}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-email">{t('Email')}</label>
          <input id="activity-instructor-email"
            type="email"
            {...register('email')}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-phone">{t('Phone')}</label>
          <input id="activity-instructor-phone"
            type="tel"
            {...register('phone')}
            className="form-control"
          />
        </div>
      </div>

      <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-specialization">{t('Specialization')}</label>
          <input id="activity-instructor-specialization"
            {...register('specialization')}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-certifications">{t('Certifications')}</label>
          <input id="activity-instructor-certifications"
            {...register('certifications')}
            className="form-control"
            placeholder={t('e.g. CONI, FIGC, etc.')}
          />
        </div>
      </div>

      {canFinance && <div className="row g-3 mb-3">
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-compensation">{t('Compensation')} (&euro;)</label>
          <input id="activity-instructor-compensation"
            type="number"
            step="0.01"
            {...register('compensation')}
            className="form-control"
          />
        </div>
        <div className="col-md-6">
          <label className="form-label" htmlFor="activity-instructor-compensationType">{t('Compensation Type')}</label>
          <select id="activity-instructor-compensationType" {...register('compensationType')} className="form-select">
            <option value="HOURLY">{t('Hourly')}</option>
            <option value="FIXED">{t('Fixed Rate')}</option>
            <option value="PERCENTAGE">{t('Percentage')}</option>
          </select>
        </div>
      </div>}

      <div className="d-flex justify-content-end gap-2 mt-4">
        <button type="button" onClick={onCancel} className="btn btn-secondary">
          {t('Cancel')}
        </button>
        <button type="submit" disabled={isSubmitting} className="btn btn-primary">
          {isSubmitting ? t('Saving...') : t('Save Instructor')}
        </button>
      </div>
    </form>
  );
};

export default ActivityInstructorForm;
