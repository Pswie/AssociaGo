// Tipologie/cariche selezionabili per il socio. Sono tutte memorizzate
// come CSV nel campo UserAssociation.role (es. "FOUNDER,TREASURER").
export const MEMBER_ROLES = [
  { value: 'MEMBER', label: 'Socio Ordinario' },
  { value: 'FOUNDER', label: 'Socio Fondatore' },
  { value: 'HONORARY', label: 'Socio Onorario' },
  { value: 'PRESIDENT', label: 'Presidente' },
  { value: 'VICE_PRESIDENT', label: 'Vice Presidente' },
  { value: 'SECRETARY', label: 'Segretario' },
  { value: 'TREASURER', label: 'Tesoriere' },
  { value: 'COUNCILLOR', label: 'Consigliere' }
]
