// Development-only fixture. vite.web.config.mjs excludes this entry from production.
import 'bootstrap/dist/css/bootstrap.min.css'
import './assets/main.css'
import './assets/mobile.css'
import './assets/adf-theme.css'
import { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { associago } from './api'
import ProjectsDashboard from './components/ProjectsDashboard'

const ID = 'a5cf26bb-2601-47d7-ac55-3b514920f703'
const query = new URLSearchParams(window.location.search)
const local = [
  {
    id: 1,
    associationId: 1,
    name: 'Centro educativo',
    status: 'attivo',
    budget: '1200.50',
    referent: 'Referente progetto',
    notes: 'Progetto di collaudo con dati fittizi'
  }
]
const assigned = []
const shifts = []
const transactions = []
const context = {
  schemaVersion: 1,
  organizationId: '73013b9d-7288-4e34-a841-5db4dc15ba60',
  suiteProjectId: ID,
  title: 'Centro educativo',
  description: 'Stesso progetto nei tre siti',
  version: 0,
  instances: [
    { system: 'librepm', entity: 'project', localId: ID },
    { system: 'associago', entity: 'project', localId: '1' }
  ],
  links: {
    librepm: `https://librepm.example/?suiteProject=${ID}`,
    associago: null,
    bandi: `https://bandi.example/suite/projects/${ID}`
  },
  sync: [
    {
      system: 'bandi',
      status: 'synced',
      lastSuccessAt: '2026-10-29T20:00:00Z',
      errorCode: null,
      errorMessage: null
    },
    {
      system: 'associago',
      status: 'synced',
      lastSuccessAt: '2026-10-29T20:00:00Z',
      errorCode: null,
      errorMessage: null
    }
  ],
  financial: null,
  workforce: null,
  preparation: null
}
window.__projectHarnessCalls = []
const mock =
  (name, handler) =>
  async (...args) => {
    window.__projectHarnessCalls.push({ name, args })
    return handler(...args)
  }
associago.suiteProjects.status = mock('status', () => ({ enabled: query.get('disabled') !== '1' }))
associago.suiteProjects.getAll = mock('catalog', () => [context])
associago.suiteProjects.getById = mock('context', (id) => {
  if (id !== ID) throw Object.assign(new Error('Progetto richiesto non trovato'), { status: 404 })
  return context
})
associago.suiteProjects.share = mock('share', () => context)
associago.suiteProjects.link = mock('link', () => context)
associago.suiteProjects.sync = mock('sync', () => context)
associago.projects.getAll = mock('projects', () => [...local])
associago.projects.create = mock('create', (data) => {
  const row = { ...data, id: local.length + 1 }
  local.push(row)
  return row
})
associago.projects.update = mock('update', (id, data) => {
  const row = local.find((item) => item.id === id)
  Object.assign(row, data)
  return { ...row }
})
associago.projects.delete = mock('delete', (id) => {
  const index = local.findIndex((item) => item.id === id)
  local.splice(index, 1)
})
associago.projects.suite = mock('localLink', () => ({ suiteProjectId: ID }))
associago.projects.candidates = mock('candidates', () => [
  { personType: 'MEMBER', personId: 1, name: 'Socio di collaudo' },
  { personType: 'VOLUNTEER', personId: 2, name: 'Volontario di collaudo' }
])
associago.projects.assignments = mock('assignments', () => [...assigned])
associago.projects.assign = mock('assign', (id, data) => {
  const row = {
    ...data,
    id: assigned.length + 1,
    name: data.personType === 'VOLUNTEER' ? 'Volontario di collaudo' : 'Socio di collaudo'
  }
  assigned.push(row)
  return row
})
associago.projects.removeAssignment = mock('removeAssignment', (id, assignmentId) => {
  const index = assigned.findIndex((item) => item.id === assignmentId)
  assigned.splice(index, 1)
})
associago.projects.shifts = mock('shifts', () => [...shifts])
associago.projects.createShift = mock('createShift', (id, data) => {
  const row = { ...data, id: shifts.length + 1 }
  shifts.push(row)
  return row
})
associago.volunteers.updateShift = mock('updateShift', (id, data) => {
  const row = shifts.find((item) => item.id === id)
  Object.assign(row, data)
  return row
})
associago.projects.balance = mock('balance', () => ({
  budget: 1200.5,
  income: transactions.length ? 500 : 0,
  expense: 0,
  saldo: transactions.length ? 500 : 0,
  overBudget: false
}))
associago.projects.transactions = mock('transactions', () => [...transactions])
associago.projects.availableTransactions = mock('availableTransactions', () => [
  {
    id: 35,
    associationId: 1,
    date: '2026-10-29',
    description: 'Donazione di collaudo',
    type: 'INCOME',
    amount: 500,
    projectId: null
  }
])
associago.projects.attachTransaction = mock('attachTransaction', (id, transactionId) => {
  transactions.push({
    id: Number(transactionId),
    projectId: id,
    date: '2026-10-29',
    description: 'Donazione di collaudo',
    type: 'INCOME',
    amount: 500
  })
  return transactions.at(-1)
})

// eslint-disable-next-line react-refresh/only-export-components
function Harness() {
  const [requestedSuiteProjectId, setRequested] = useState(query.get('suiteProject'))
  return (
    <main className="container-fluid p-3 p-lg-4" style={{ maxWidth: 1280 }}>
      <ProjectsDashboard
        associationId={1}
        shell={{
          currentUser: { role: query.get('role') || 'presidente' },
          requestedSuiteProjectId,
          selectSuiteProject: (id) => {
            const url = new URL(window.location.href)
            if (id === null) url.searchParams.delete('suiteProject')
            else url.searchParams.set('suiteProject', id)
            window.history.replaceState({}, '', url)
            setRequested(id)
          }
        }}
      />
    </main>
  )
}
createRoot(document.getElementById('root')).render(<Harness />)
