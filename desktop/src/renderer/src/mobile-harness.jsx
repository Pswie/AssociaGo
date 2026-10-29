/**
 * Banco di prova del comportamento su schermo stretto.
 *
 * Le schermate vere stanno dietro l'accesso Clerk e una base dati: un test
 * automatico non le raggiunge senza montare mezzo sistema. Qui vengono invece
 * montati i componenti veri del guscio — quelli che non parlano con il backend —
 * dentro la stessa impalcatura di `App.jsx` e con gli stessi fogli di stile.
 *
 * Quello che questa pagina misura davvero:
 *
 *   - il guscio (`jl-root` → `jl-page`) e i suoi margini;
 *   - `LeftNav` come cassetto, con sfondo, chiusura e bersagli touch;
 *   - `TopHeader` e il suo pulsante hamburger;
 *   - `ResponsiveTables` più le regole "tabella → schede";
 *   - `RowActions` e la misura dei bottoni-icona;
 *   - `Modal` a tutto schermo.
 *
 * La tabella è copiata dal markup di `MemberList.jsx`, che è lo stesso in tutte
 * e sei le liste: se le schede funzionano qui funzionano lì.
 *
 * Non è compresa nella build di produzione: `vite.web.config.mjs` dichiara un
 * solo ingresso, `index.web.html`. Vive nel server di sviluppo, dove
 * `scripts/mobile-smoke.mjs` la interroga.
 */

import 'bootstrap/dist/css/bootstrap.min.css'
import './assets/main.css'
import './assets/mobile.css'
import './assets/adf-theme.css'
import './i18n'

import { useRef, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Badge, Button, Card, Form, InputGroup, Table } from 'react-bootstrap'
import { Edit, Plus, RefreshCw, Search, Trash2 } from 'lucide-react'

import LeftNav from './components/LeftNav'
import TopHeader from './components/TopHeader'
import Modal from './components/Modal'
import ResponsiveTables from './components/ResponsiveTables'
import RowActions from './components/common/RowActions'

const PAGES = [
  { id: 'dashboard', label: 'Cruscotto', icon: 'bi-speedometer2' },
  { id: 'members', label: 'Soci', icon: 'bi-people' },
  { id: 'activities', label: 'Attività', icon: 'bi-calendar3' },
  { id: 'events', label: 'Eventi', icon: 'bi-calendar3' },
  { id: 'fiscal', label: 'Contabilità', icon: 'bi-credit-card' },
  { id: 'assemblies', label: 'Assemblee', icon: 'bi-file-text' },
  { id: 'inventory', label: 'Inventario', icon: 'bi-box-seam' },
  { id: 'volunteers', label: 'Volontari', icon: 'bi-person-check' },
  { id: 'settings', label: 'Impostazioni', icon: 'bi-gear' }
]

// Nomi e date verosimili, non "Mario Rossi" ripetuto sei volte: una riga finta
// e regolare nasconde proprio i casi che rompono il layout, cioè i cognomi
// lunghi e gli indirizzi che non stanno in colonna.
const MEMBERS = [
  {
    id: 1,
    firstName: 'Annunziata',
    lastName: 'Esposito Ferraiuolo',
    taxCode: 'SPSNNZ78L54F839K',
    email: 'annunziata.espositoferraiuolo@example.it',
    status: 'ACTIVE',
    expiration: '14/03/2027',
    role: 'Presidente, Tesoriere'
  },
  {
    id: 2,
    firstName: 'Gennaro',
    lastName: 'Improta',
    taxCode: 'MPRGNR91T02F839B',
    email: 'g.improta@example.it',
    status: 'ACTIVE',
    expiration: '02/11/2026',
    role: 'Socio ordinario'
  },
  {
    id: 3,
    firstName: 'Luigia',
    lastName: 'Del Prete',
    taxCode: 'DLPLGU85S41A509W',
    email: 'luigia.delprete@example.it',
    status: 'PENDING',
    expiration: '30/09/2026',
    role: 'Referente volontari'
  }
]

// Il componente sta nel punto d'ingresso invece che in un file suo: è una
// pagina di prova sola, e separarla in due file per compiacere il ricaricamento
// a caldo — che qui non serve — sarebbe rumore.
// eslint-disable-next-line react-refresh/only-export-components
function Harness() {
  const [navOpen, setNavOpen] = useState(false)
  const [activeId, setActiveId] = useState('members')
  const [modalOpen, setModalOpen] = useState(false)
  const [query, setQuery] = useState('')
  const contentRef = useRef(null)

  return (
    <div className="jl-root">
      <div className="jl-appframe">
        <div className="jl-shell">
          <LeftNav
            items={PAGES}
            activeId={activeId}
            onSelect={setActiveId}
            open={navOpen}
            onClose={() => setNavOpen(false)}
          />
          <main className="jl-main">
            <TopHeader
              title="Soci"
              navOpen={navOpen}
              onMenuToggle={() => setNavOpen((open) => !open)}
              actions={
                <button type="button" className="btn btn-light btn-icon" aria-label="Profilo">
                  AF
                </button>
              }
            />
            <section className="jl-content" ref={contentRef}>
              <div className="jl-page p-4">
                <div className="container-fluid">
                  <div className="d-flex justify-content-between align-items-center mb-4">
                    <div>
                      <h2 className="fw-bold text-dark mb-1">Soci</h2>
                      <p className="text-muted mb-0">Gestisci i soci dell&apos;associazione</p>
                    </div>
                    <Button
                      variant="primary"
                      className="d-flex align-items-center"
                      onClick={() => setModalOpen(true)}
                    >
                      <Plus size={18} className="me-2" />
                      Aggiungi socio
                    </Button>
                  </div>

                  <Card className="border-0 shadow-sm">
                    <Card.Header className="bg-white border-bottom-0 pt-4 pb-0">
                      <div className="d-flex justify-content-between align-items-center mb-3">
                        <div className="d-flex gap-2 jl-search" style={{ width: '300px' }}>
                          <InputGroup>
                            <InputGroup.Text className="bg-light border-end-0">
                              <Search size={16} className="text-muted" />
                            </InputGroup.Text>
                            <Form.Control
                              placeholder="Cerca soci…"
                              className="bg-light border-start-0 ps-0"
                              value={query}
                              onChange={(event) => setQuery(event.target.value)}
                            />
                          </InputGroup>
                        </div>
                        <Button variant="outline-primary">Importa CSV</Button>
                      </div>
                    </Card.Header>
                    <Card.Body className="p-0">
                      <Table hover responsive className="mb-0 align-middle">
                        <thead className="bg-light">
                          <tr>
                            <th className="border-0 ps-4">Nome</th>
                            <th className="border-0">Email</th>
                            <th className="border-0">Stato</th>
                            <th className="border-0">Scadenza</th>
                            <th className="border-0">Ruolo</th>
                            <th className="border-0 text-end pe-4">Azioni</th>
                          </tr>
                        </thead>
                        <tbody>
                          {MEMBERS.map((member) => (
                            <tr key={member.id}>
                              <td className="ps-4">
                                <div className="d-flex align-items-center">
                                  <div
                                    className="rounded-circle bg-primary text-white d-flex align-items-center justify-content-center me-3"
                                    style={{ width: '36px', height: '36px', fontSize: '0.9rem' }}
                                  >
                                    {member.firstName[0]}
                                    {member.lastName[0]}
                                  </div>
                                  <div>
                                    <div className="fw-bold text-dark">
                                      {member.firstName} {member.lastName}
                                    </div>
                                    <div className="small text-muted">{member.taxCode}</div>
                                  </div>
                                </div>
                              </td>
                              <td>
                                <div className="text-muted">{member.email}</div>
                              </td>
                              <td>
                                <Badge
                                  bg={member.status === 'ACTIVE' ? 'success' : 'warning'}
                                  className="fw-normal px-2 py-1"
                                >
                                  {member.status}
                                </Badge>
                              </td>
                              <td>
                                <div className="text-muted small">{member.expiration}</div>
                              </td>
                              <td>{member.role}</td>
                              <td className="text-end pe-4">
                                <RowActions
                                  actions={[
                                    {
                                      key: 'renew',
                                      icon: <RefreshCw size={16} />,
                                      label: 'Rinnova tessera',
                                      textClass: 'text-success',
                                      onClick: () => {}
                                    },
                                    {
                                      key: 'edit',
                                      icon: <Edit size={16} />,
                                      label: 'Modifica',
                                      onClick: () => {}
                                    },
                                    {
                                      key: 'delete',
                                      icon: <Trash2 size={16} />,
                                      label: 'Elimina',
                                      textClass: 'text-danger',
                                      onClick: () => {}
                                    }
                                  ]}
                                />
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </Table>
                    </Card.Body>
                  </Card>
                </div>
              </div>
            </section>
            <ResponsiveTables rootRef={contentRef} />
          </main>
        </div>
      </div>

      <Modal isOpen={modalOpen} onClose={() => setModalOpen(false)} title="Nuovo socio">
        <Form>
          <div className="row">
            <div className="col-md-6 mb-3">
              <Form.Label>Nome</Form.Label>
              <Form.Control defaultValue="" />
            </div>
            <div className="col-md-6 mb-3">
              <Form.Label>Cognome</Form.Label>
              <Form.Control defaultValue="" />
            </div>
          </div>
          <div className="d-flex justify-content-end gap-2">
            <Button variant="light" onClick={() => setModalOpen(false)}>
              Annulla
            </Button>
            <Button variant="primary">Salva</Button>
          </div>
        </Form>
      </Modal>
    </div>
  )
}

createRoot(document.getElementById('root')).render(<Harness />)
