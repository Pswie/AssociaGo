import React, { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';
import { Calculator, FileHeart, Upload } from 'lucide-react';
import { MEMBER_ROLES } from './memberRoles';

const MemberForm = ({ associationId, associationType, memberId, onSuccess, onCancel }) => {
  const isASD = associationType === 'ASD';
  const today = new Date().toISOString().split('T')[0];
  const { register, handleSubmit, reset, setValue, getValues, watch, formState: { errors, isSubmitting } } = useForm({
    defaultValues: {
      registrationDate: today,
      roles: ['MEMBER'],
    },
  });
  const [calculatingCF, setCalculatingCF] = useState(false);
  const [membership, setMembership] = useState(null);
  const [loading, setLoading] = useState(Boolean(memberId));
  const [saveError, setSaveError] = useState('');

  useEffect(() => {
    let cancelled = false;
    if (!memberId) return;
    associago.memberships.getById(memberId).then(existing => {
      if (cancelled) return;
      setMembership(existing);
      reset({
        ...existing.user,
        cardNumber: existing.membershipCardNumber || '',
        registrationDate: existing.joinDate || today,
        roles: (existing.role || 'MEMBER').split(',').map(role => role.trim()),
        registerPayment: false
      });
    }).catch(error => {
      if (!cancelled) setSaveError(error.message);
    }).finally(() => {
      if (!cancelled) setLoading(false);
    });
    return () => { cancelled = true; };
  }, [memberId, reset]);

  // Watch for payment checkbox to conditionally show payment fields
  const registerPayment = watch('registerPayment', false);

  const onSubmit = async (data) => {
    setSaveError('');
    try {
      // Normalizza le tipologie selezionate in CSV. Se nessuna è spuntata
      // (caso impossibile via UI ma difensivo), usa MEMBER come default.
      const rolesArray = Array.isArray(data.roles) ? data.roles : (data.roles ? [data.roles] : []);
      const rolesCsv = rolesArray.length > 0 ? rolesArray.join(',') : 'MEMBER';

      // La data di registrazione vale come arrivo del pagamento e come
      // data di iscrizione (joinDate sulla membership).
      const registrationDate = data.registrationDate || today;

      if (memberId) {
        if (!membership?.user?.id) throw new Error(t('Unable to load member.'));
        await associago.users.update(membership.user.id, {
          ...membership.user,
          ...Object.fromEntries(['firstName', 'lastName', 'taxCode', 'email', 'phone', 'birthDate', 'birthPlace', 'gender', 'address', 'city', 'zipCode'].map(key => [key, data[key] ?? membership.user[key]]))
        });
        await associago.memberships.update(memberId, {
          ...membership,
          role: rolesCsv,
          membershipCardNumber: data.cardNumber
        });
        onSuccess();
        return;
      }

      // Step 1: Create User (handle duplicate taxCode by finding existing)
      let user;
      try {
        user = await associago.users.create({
          firstName: data.firstName,
          lastName: data.lastName,
          taxCode: data.taxCode,
          email: data.email,
          phone: data.phone,
          birthDate: data.birthDate,
          birthPlace: data.birthPlace,
          gender: data.gender,
          address: data.address,
          city: data.city,
          zipCode: data.zipCode
        });
      } catch (userError) {
        // If user creation fails (e.g. duplicate taxCode), try to find existing
        const existingUsers = await associago.users.getAll();
        user = existingUsers.find(u => u.taxCode === data.taxCode);
        if (!user) throw userError;
      }

      // Step 2: Create Membership (UserAssociation)
      await associago.memberships.create({
        user: { id: user.id },
        association: { id: associationId },
        role: rolesCsv,
        status: 'ACTIVE',
        joinDate: registrationDate,
        membershipCardNumber: data.cardNumber
      });

      // Step 3: Save Medical Certificate (if ASD and data provided)
      if (isASD && data.medicalCertIssueDate) {
        try {
          await associago.medicalCertificates.save({
            memberId: user.id,
            associationId,
            certificateType: data.medicalCertType || 'NON_AGONISTIC',
            issueDate: data.medicalCertIssueDate,
            expiryDate: data.medicalCertExpiryDate,
          });
        } catch (medError) {
          console.warn('Member created but medical certificate save failed', medError);
        }
      }

      // Step 4: Register Payment (if selected)
      if (data.registerPayment && data.paymentAmount > 0) {
        try {
          await associago.finance.createTransaction({
            associationId: associationId,
            date: registrationDate,
            amount: data.paymentAmount,
            type: 'INCOME',
            description: `Quota associativa - ${data.firstName} ${data.lastName}`,
            paymentMethod: data.paymentMethod,
            userId: user.id,
          });
        } catch (paymentError) {
          console.warn('Membership created but payment registration failed', paymentError);
          alert('Socio creato ma errore nella registrazione del pagamento.');
        }
      }

      onSuccess();
    } catch (error) {
      console.error('Error creating member:', error);
      setSaveError(error.message);
    }
  };

  const handleCalculateCF = async () => {
    const { firstName, lastName, birthDate, gender, birthPlaceCode } = getValues();

    if (!firstName || !lastName || !birthDate || !gender || !birthPlaceCode) {
      alert("Per calcolare il Codice Fiscale servono: Nome, Cognome, Data di Nascita, Sesso e Codice Catastale (es. H501).");
      return;
    }

    setCalculatingCF(true);
    try {
      const result = await associago.members.calculateFiscalCode({
        firstName, lastName, birthDate, gender, birthPlaceCode
      });
      if (result && result.fiscalCode) {
        setValue('taxCode', result.fiscalCode);
      }
    } catch (error) {
      alert("Errore calcolo CF: " + error.message);
    } finally {
      setCalculatingCF(false);
    }
  };

  return (
    <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 p-2">
      {saveError && <div className="alert alert-danger" role="alert">{saveError}</div>}
      <fieldset disabled={loading || isSubmitting || (Boolean(memberId) && !membership)}>
      <div className="row g-3">
        <div className="col-md-6">
          <label htmlFor="member-firstName" className="form-label">Nome</label>
          <input id="member-firstName"
            {...register('firstName', { required: 'Nome obbligatorio' })}
            className="form-control"
          />
          {errors.firstName && <span className="text-danger small">{errors.firstName.message}</span>}
        </div>
        <div className="col-md-6">
          <label htmlFor="member-lastName" className="form-label">Cognome</label>
          <input id="member-lastName"
            {...register('lastName', { required: 'Cognome obbligatorio' })}
            className="form-control"
          />
          {errors.lastName && <span className="text-danger small">{errors.lastName.message}</span>}
        </div>
      </div>

      <div className="row g-3 mt-1">
        <div className="col-md-4">
          <label htmlFor="member-birthDate" className="form-label">Data di Nascita</label>
          <input id="member-birthDate" type="date" {...register('birthDate', { required: !memberId })} className="form-control" />
        </div>
        <div className="col-md-4">
          <label htmlFor="member-gender" className="form-label">Sesso</label>
          <select id="member-gender" {...register('gender', { required: !memberId })} className="form-select">
            <option value="">Seleziona...</option>
            <option value="M">Maschio</option>
            <option value="F">Femmina</option>
          </select>
        </div>
        <div className="col-md-4">
          <label htmlFor="member-birthPlaceCode" className="form-label">Codice Catastale (es. H501)</label>
          <input id="member-birthPlaceCode" {...register('birthPlaceCode')} className="form-control" placeholder="es. H501" />
        </div>
      </div>

      <div className="mt-3">
        <label htmlFor="member-taxCode" className="form-label">Codice Fiscale</label>
        <div className="input-group">
          <input id="member-taxCode"
            {...register('taxCode', { required: 'Codice Fiscale obbligatorio' })}
            className="form-control"
          />
          <button
            type="button"
            className="btn btn-outline-secondary d-flex align-items-center gap-2"
            onClick={handleCalculateCF}
            disabled={calculatingCF}
          >
            <Calculator size={16} /> {calculatingCF ? '...' : 'Calcola'}
          </button>
        </div>
        {errors.taxCode && <span className="text-danger small">{errors.taxCode.message}</span>}
      </div>

      <div className="row g-3 mt-1">
        <div className="col-md-6">
          <label htmlFor="member-email" className="form-label">Email</label>
          <input id="member-email" type="email" {...register('email')} className="form-control" />
        </div>
        <div className="col-md-6">
          <label htmlFor="member-phone" className="form-label">Telefono</label>
          <input id="member-phone" type="tel" {...register('phone')} className="form-control" />
        </div>
      </div>

      <div className="row g-3 mt-1">
        <div className="col-md-12">
          <label htmlFor="member-address" className="form-label">Indirizzo</label>
          <input id="member-address" type="text" {...register('address')} className="form-control" placeholder="Via Roma 1" />
        </div>
        <div className="col-md-8">
          <label htmlFor="member-city" className="form-label">Città</label>
          <input id="member-city" type="text" {...register('city')} className="form-control" />
        </div>
        <div className="col-md-4">
          <label htmlFor="member-zipCode" className="form-label">CAP</label>
          <input id="member-zipCode" type="text" {...register('zipCode')} className="form-control" />
        </div>
      </div>

      <div className="row g-3 mt-1">
        <div className="col-md-6">
            <label htmlFor="member-cardNumber" className="form-label">Numero Tessera</label>
            <input id="member-cardNumber" {...register('cardNumber')} className="form-control" />
        </div>
        <div className="col-md-6">
            <label htmlFor="member-registrationDate" className="form-label">
              Data di Registrazione
              <span className="text-muted small ms-1">(arrivo del pagamento)</span>
            </label>
            <input id="member-registrationDate"
              type="date"
              {...register('registrationDate', { required: 'Data obbligatoria' })}
              className="form-control"
              max={today}
              readOnly={Boolean(memberId)}
            />
            {errors.registrationDate && <span className="text-danger small">{errors.registrationDate.message}</span>}
        </div>
      </div>

      <div className="row g-3 mt-1">
        <div className="col-12">
          <div className="form-label" id="member-roles-label">
            Tipologia / Cariche
            <span className="text-muted small ms-1">(seleziona una o più voci)</span>
          </div>
          <div className="d-flex flex-wrap gap-3 p-2 border rounded" role="group" aria-labelledby="member-roles-label">
            {MEMBER_ROLES.map((r) => (
              <div className="form-check" key={r.value}>
                <input
                  type="checkbox"
                  id={`role-${r.value}`}
                  value={r.value}
                  className="form-check-input"
                  {...register('roles')}
                />
                <label className="form-check-label" htmlFor={`role-${r.value}`}>
                  {r.label}
                </label>
              </div>
            ))}
          </div>
        </div>
      </div>

      {isASD && !memberId && (
        <>
          <hr className="my-4" />
          <div className="card border-primary border-opacity-25 mb-3">
            <div className="card-header bg-primary bg-opacity-10 d-flex align-items-center gap-2">
              <FileHeart size={18} className="text-primary" />
              <span className="fw-bold">{t('Sports Medical Certificate')}</span>
            </div>
            <div className="card-body">
              {/*
                Restano tipo e scadenza, e basta.

                Il nome del medico, la struttura sanitaria e una nota libera
                attaccata a un certificato sono dati sulla salute — art. 9 GDPR
                — e stavano accanto a nome, codice fiscale e indirizzo, a un
                JOIN di distanza. Non servivano a niente che l'associazione
                debba fare: la domanda a cui rispondere è «questa persona può
                fare il turno», e a quella risponde la scadenza.
              */}
              <p className="text-muted small mb-3">
                {t('Serve solo a sapere se la persona può svolgere attività. Non annotare qui informazioni sanitarie.')}
              </p>
              <div className="row g-3">
                <div className="col-md-6">
                  <label htmlFor="member-medicalCertType" className="form-label">{t('Tipo di certificato')}</label>
                  <select id="member-medicalCertType" {...register('medicalCertType')} className="form-select">
                    <option value="NON_AGONISTIC">{t('Non agonistico')}</option>
                    <option value="AGONISTIC">{t('Agonistico')}</option>
                  </select>
                </div>
                <div className="col-md-6">
                  <label htmlFor="member-medicalCertStatus" className="form-label">{t('Stato')}</label>
                  <select id="member-medicalCertStatus" {...register('medicalCertStatus')} className="form-select" disabled>
                    <option value="MISSING">{t('Assente')}</option>
                    <option value="VALID">{t('Valido')}</option>
                    <option value="EXPIRING_SOON">{t('In scadenza')}</option>
                    <option value="EXPIRED">{t('Scaduto')}</option>
                  </select>
                </div>
                <div className="col-md-6">
                  <label htmlFor="member-medicalCertIssueDate" className="form-label">{t('Data di rilascio')}</label>
                  <input id="member-medicalCertIssueDate" type="date" {...register('medicalCertIssueDate')} className="form-control" />
                </div>
                <div className="col-md-6">
                  <label htmlFor="member-medicalCertExpiryDate" className="form-label">{t('Scadenza')}</label>
                  <input id="member-medicalCertExpiryDate" type="date" {...register('medicalCertExpiryDate')} className="form-control" />
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      <hr className="my-4" />

      {!memberId && <div className="form-check mb-3">
        <input
            className="form-check-input"
            type="checkbox"
            id="registerPayment"
            {...register('registerPayment')}
        />
        <label className="form-check-label fw-bold" htmlFor="registerPayment">
            Registra Pagamento Quota Associativa
        </label>
      </div>}

      {registerPayment && (
        <div className="card bg-light border-0 p-3 mb-3">
            <div className="row g-3">
                <div className="col-md-6">
                    <label htmlFor="member-paymentAmount" className="form-label">Importo (€)</label>
                    <input id="member-paymentAmount"
                        type="number"
                        step="0.01"
                        {...register('paymentAmount', { required: registerPayment })}
                        className="form-control"
                        defaultValue="20.00"
                    />
                </div>
                <div className="col-md-6">
                    <label htmlFor="member-paymentMethod" className="form-label">Metodo di Pagamento</label>
                    <select id="member-paymentMethod" {...register('paymentMethod', { required: registerPayment })} className="form-select">
                        <option value="CASH">Contanti</option>
                        <option value="BANK_TRANSFER">Bonifico</option>
                        <option value="POS">POS / Carta</option>
                        <option value="PAYPAL">PayPal</option>
                    </select>
                </div>
            </div>
        </div>
      )}

      </fieldset>
      <div className="d-flex justify-content-end gap-2 mt-4">
        <button
          type="button"
          onClick={onCancel}
          className="btn btn-secondary"
        >
          Annulla
        </button>
        <button
          type="submit"
          disabled={isSubmitting || loading || (Boolean(memberId) && !membership)}
          className="btn btn-primary"
        >
          {isSubmitting ? 'Salvataggio...' : 'Salva Socio'}
        </button>
      </div>
    </form>
  );
};

export default MemberForm;
