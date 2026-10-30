/** Development-only fixture: actual components, synthetic data, no backend writes. */
import '@fontsource-variable/plus-jakarta-sans'
import './assets/main.css'
import './assets/mobile.css'
import './assets/adf-theme.css'
import './i18n'
import { useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { HashRouter } from 'react-router-dom'
import App from './App'
import { associago } from './api'
import AttendanceList from './components/AttendanceList'
import EventForm from './components/EventForm'
import AssemblyForm from './components/AssemblyForm'
import ActivityDetail from './components/ActivityDetail'
import ActivityForm from './components/ActivityForm'
import ActivityParticipantForm from './components/ActivityParticipantForm'
import ActivityInstructorForm from './components/ActivityInstructorForm'
import ProfileMenu from './components/ProfileMenu'
import Dashboard from './components/Dashboard'
import ManualDashboard from './components/ManualDashboard'
import ResponsiveTables from './components/ResponsiveTables'
import { ORG_NAME } from './branding'

const query = new URLSearchParams(window.location.search)
const calls = []
window.frontendFixture = { calls }
const capture = (method, id, data) => {
  calls.push({ method, id, data })
  return { ...data, id }
}
const association = { id: 7, nome: ORG_NAME, name: ORG_NAME, tipo: 'APS' }
const role = query.get('role') || 'presidente'
const canFinance = role === 'presidente' || role === 'responsabile-amministrativo'
const activity = {
  id: 71,
  associationId: 7,
  name: 'Laboratorio di comunità',
  category: 'COURSE',
  startDate: '2026-10-01',
  isActive: true,
  cost: canFinance ? 25 : null
}
const instructor = {
  id: 801,
  firstName: 'Anna',
  lastName: 'Viola',
  specialization: 'Formatrice',
  compensation: canFinance ? 35 : null,
  compensationType: canFinance ? 'HOURLY' : null
}
const event = {
  id: 41,
  associationId: 7,
  name: 'Incontro dei volontari',
  type: 'MEETING',
  status: 'PLANNED',
  startDatetime: '2026-10-30T00:15:00',
  endDatetime: '2026-10-30T01:00:00'
}
const assembly = {
  id: 81,
  title: 'Assemblea dei soci',
  date: '2026-10-30T00:15:00',
  type: 'ORDINARY',
  status: 'DRAFT'
}
const members = [
  { id: 301, firstName: 'Giulia', lastName: 'Verdi' },
  { id: 302, firstName: 'Luca', lastName: 'Bianchi' }
]
const participants = [
  ...members.map((member) => ({
    participantType: 'LEGACY_MEMBER',
    memberId: member.id,
    membershipId: null,
    displayName: `${member.firstName} ${member.lastName}`,
    membershipCardNumber: null,
    status: 'ACTIVE'
  })),
  {
    participantType: 'MEMBERSHIP',
    memberId: null,
    membershipId: 301,
    displayName: 'Sofia Rossi',
    membershipCardNumber: 'APS-2026-007',
    status: 'ACTIVE'
  },
  {
    participantType: 'MEMBERSHIP',
    memberId: null,
    membershipId: 702,
    displayName: 'Marco Neri',
    membershipCardNumber: 'APS-2025-002',
    status: 'EXPIRED'
  }
]
let sessions = [
  {
    id: 501,
    activityId: 71,
    date: '2026-10-29',
    startTime: '16:00',
    endTime: '18:00',
    notes: 'Sessione di prova'
  }
]
let records = [
  {
    id: 901,
    sessionId: 501,
    memberId: 301,
    membershipId: null,
    status: 'PRESENT',
    checkInTime: '16:00'
  }
]
let dashboardAttempts = 0
let eventListAttempts = 0
associago.init = async () => ({ baseUrl: 'fixture://local' })
associago.getPreferences = () => ({ language: 'it', theme: query.get('theme') || 'light' })
associago.getBackendInfo = async () => ({ version: 'collaudo' })
associago.getLogoUrl = async () => ''
associago.activities.getAll = async () => [activity]
associago.activities.getById = async () => activity
associago.activities.getDetails = async () => ({
  activity,
  activeParticipants: 1,
  totalRevenue: canFinance ? 25 : null,
  totalCosts: canFinance ? 10 : null,
  netProfit: canFinance ? 15 : null
})
associago.activities.getParticipants = async () => [
  {
    id: 601,
    user: { id: 91, firstName: 'Sofia', lastName: 'Rossi' },
    isActive: true,
    isPaid: canFinance ? true : null,
    amountPaid: canFinance ? 25 : null
  }
]
associago.activities.getParticipantCandidates = async (id) => {
  capture('activities.getParticipantCandidates', id, {})
  if (id !== 71) throw new Error('Attività non valida')
  return [
    {
      userId: 91,
      membershipId: 301,
      firstName: 'Sofia',
      lastName: 'Rossi',
      membershipCardNumber: 'APS-2026-007',
      status: 'ACTIVE'
    }
  ]
}
associago.activities.getCosts = async () => {
  capture('activities.getCosts', 71, {})
  if (!canFinance) throw new Error('Costi riservati')
  return []
}
associago.activities.getInstructors = async () => [instructor]
associago.activities.getSchedules = async () => []
associago.activities.update = async (id, data) => capture('activities.update', id, data)
associago.activities.addParticipant = async (id, data) =>
  capture('activities.addParticipant', id, data)
associago.activities.updateInstructor = async (id, data) =>
  capture('activities.updateInstructor', id, data)
associago.memberships.getByAssociation = async (id) => {
  capture('memberships.getByAssociation', id, {})
  if (id !== 7) throw new Error('Associazione non valida')
  return [
    {
      id: 301,
      user: { id: 91, firstName: 'Sofia', lastName: 'Rossi' },
      membershipCardNumber: 'APS-2026-007'
    }
  ]
}
associago.certificates.getTemplates = async () => {
  capture('certificates.getTemplates', 7, {})
  return []
}
associago.attachments.list = async () => []
associago.members.getAll = async () => {
  throw new Error('Il registro presenze non deve leggere l’anagrafica globale')
}
associago.attendance.getParticipants = async () => {
  if (query.has('restricted'))
    throw Object.assign(new Error('Accesso non consentito'), { status: 403 })
  return participants
}
associago.attendance.getSessions = async () => sessions
associago.attendance.createSession = async (data) => {
  const saved = capture('attendance.createSession', 510 + sessions.length, data)
  sessions = [...sessions, saved]
  return saved
}
associago.attendance.updateSession = async (id, data) => {
  const saved = capture('attendance.updateSession', id, data)
  sessions = sessions.map((item) => (item.id === id ? saved : item))
  return saved
}
associago.attendance.deleteSession = async (id) => {
  capture('attendance.deleteSession', id, {})
  sessions = sessions.filter((item) => item.id !== id)
  records = records.filter((item) => item.sessionId !== id)
}
associago.attendance.getRecords = async (id) => records.filter((item) => item.sessionId === id)
associago.attendance.createRecord = async (data) => {
  const saved = capture('attendance.createRecord', 910 + records.length, data)
  records = [...records, saved]
  return saved
}
associago.attendance.updateRecord = async (id, data) => {
  const saved = capture('attendance.updateRecord', id, data)
  records = records.map((item) => (item.id === id ? saved : item))
  return saved
}
associago.attendance.deleteRecord = async (id) => {
  capture('attendance.deleteRecord', id, {})
  records = records.filter((item) => item.id !== id)
}
associago.events.getById = async () => {
  if (query.has('fail')) throw new Error('Evento non disponibile. Riprova.')
  return event
}
associago.events.getAll = async () => {
  if (query.has('listfail') && eventListAttempts++ === 0)
    throw new Error('Elenco eventi non disponibile.')
  return [event]
}
associago.events.create = async (data) => capture('events.create', 99, data)
associago.events.update = async (id, data) => capture('events.update', id, data)
associago.assemblies.getById = async () => assembly
associago.assemblies.create = async (data) => capture('assemblies.create', 98, data)
associago.assemblies.update = async (id, data) => capture('assemblies.update', id, data)
associago.fiscal.getClosure = async () => null
associago.notifications.getUserNotifications = async () => []
associago.dashboard.getStats = async () => {
  dashboardAttempts += 1
  if (query.has('fail') && dashboardAttempts === 1)
    throw new Error('Cruscotto temporaneamente non disponibile.')
  return {
    members: { active: 23, newThisMonth: 2 },
    treasury: { balance: 1400, incomeMonth: 400, expenseMonth: 150 },
    events: { upcoming: 1, nextEvent: { title: event.name, date: event.startDatetime } },
    recentActivity: []
  }
}

const shell = {
  anno: 2026,
  currentAssociation: association,
  currentAssociationId: 7,
  currentUser: { id: 99, role },
  navigate: (id) => capture('navigate', id, {}),
  openModal: (type, props) => capture('openModal', type, props),
  pages: [
    { id: 'dashboard', label: 'Cruscotto' },
    ...(canFinance ? [{ id: 'certificates', label: 'Attestati' }] : [])
  ]
}

export function Fixture() {
  const rootRef = useRef(null)
  const [saved, setSaved] = useState(false)
  const page = query.get('page') || 'attendance'
  if (page === 'shell')
    return (
      <App
        webSession={{
          association,
          localUserId: 99,
          role,
          user: {
            id: 'fixture-clerk',
            fullName: 'Utente di collaudo',
            primaryEmailAddress: { emailAddress: 'collaudo@example.invalid' }
          },
          signOut: async () => capture('signOut', null, {})
        }}
      />
    )
  return (
    <main ref={rootRef} className="jl-page p-3 p-lg-4" style={{ maxWidth: 1200, margin: '0 auto' }}>
      <ResponsiveTables rootRef={rootRef} />
      {saved && (
        <div role="status" className="alert alert-success">
          Salvataggio verificato
        </div>
      )}
      {page === 'attendance' && <AttendanceList associationId={7} shell={shell} />}
      {page === 'activity' && (
        <ActivityDetail activityId={71} shell={shell} onBack={() => capture('back', 71, {})} />
      )}
      {page === 'activity-form' && (
        <ActivityForm
          associationId={7}
          activity={activity}
          canFinance={canFinance}
          onSuccess={() => setSaved(true)}
          onCancel={() => {}}
        />
      )}
      {page === 'activity-participant' && (
        <ActivityParticipantForm
          activityId={71}
          associationId={7}
          onSuccess={() => setSaved(true)}
          onCancel={() => {}}
        />
      )}
      {page === 'activity-instructor' && (
        <ActivityInstructorForm
          activityId={71}
          instructor={instructor}
          canFinance={canFinance}
          onSuccess={() => setSaved(true)}
          onCancel={() => {}}
        />
      )}
      {page === 'event' && (
        <EventForm
          associationId={7}
          eventId={41}
          event={query.has('fail') ? undefined : event}
          onSuccess={() => setSaved(true)}
          onCancel={() => {}}
        />
      )}
      {page === 'assembly' && (
        <AssemblyForm
          associationId={7}
          assemblyId={81}
          onSuccess={() => setSaved(true)}
          onCancel={() => {}}
        />
      )}
      {page === 'profile' && (
        <ProfileMenu
          title={ORG_NAME}
          subtitle="Utente di collaudo"
          initials="AF"
          items={[
            { id: 'settings', label: 'Impostazioni', onClick: () => setSaved(true) },
            { id: 'info', label: 'Informazioni', icon: 'bi-info-circle' }
          ]}
        />
      )}
      {page === 'dashboard' && <Dashboard associationId={7} shell={shell} />}
      {page === 'manual' && <ManualDashboard />}
    </main>
  )
}
createRoot(document.getElementById('root')).render(
  <HashRouter>
    <Fixture />
  </HashRouter>
)
