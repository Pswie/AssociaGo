import React, { useId } from 'react';
import { Modal as BsModal } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';

/**
 * Finestra modale dell'applicazione.
 *
 * Sotto i 768px va a tutto schermo. Le modali di AssociaGo contengono i form di
 * inserimento — socio, evento, attività — che su desktop stanno in due colonne:
 * dentro una finestra centrata larga il 90% di un telefono, con i margini della
 * modale e quelli del form, restano tre centimetri di campo utile. A tutto
 * schermo il form recupera la larghezza intera e la tastiera virtuale non
 * spinge fuori vista il pulsante di conferma.
 *
 * `scrollable` sposta lo scorrimento nel corpo della modale: senza, il
 * contenuto lungo fa scorrere la finestra intera e l'intestazione con il
 * pulsante di chiusura se ne va in alto fuori portata.
 */
const Modal = ({ isOpen, onClose, title, children, size = "lg" }) => {
  const titleId = useId();
  const { t } = useTranslation();
  return (
    <BsModal
      show={isOpen}
      onHide={onClose}
      size={size}
      centered
      scrollable
      fullscreen="md-down"
      aria-labelledby={titleId}
    >
      <BsModal.Header closeButton closeLabel={t('Close')}>
        <BsModal.Title id={titleId}>{title}</BsModal.Title>
      </BsModal.Header>
      <BsModal.Body>
        {children}
      </BsModal.Body>
    </BsModal>
  );
};

export default Modal;
