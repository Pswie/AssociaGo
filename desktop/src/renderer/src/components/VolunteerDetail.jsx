import React, { useState, useEffect } from 'react';
import { Card, Button, Table, Badge, Nav, Spinner, Alert, Modal } from 'react-bootstrap';
import { ArrowLeft, Plus, Edit, Trash2, Clock, DollarSign, FileText, Paperclip } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';
import AttachmentPanel from './AttachmentPanel';

const VolunteerDetail = ({ volunteerId, volunteerName, associationId, shell, onBack }) => {
    const { t } = useTranslation();
    const [volunteer, setVolunteer] = useState(null);
    const [shifts, setShifts] = useState([]);
    const [expenses, setExpenses] = useState([]);
    const [loading, setLoading] = useState(true);
    const [activeTab, setActiveTab] = useState('shifts');
    const [error, setError] = useState('');
    const [updatingExpenseId, setUpdatingExpenseId] = useState(null);
    const [ricevutaDi, setRicevutaDi] = useState(null);

    useEffect(() => {
        loadData();
    }, [volunteerId]);

    const loadData = async () => {
        setLoading(true);
        setError('');
        try {
            const [v, s, e] = await Promise.all([
                associago.volunteers.getById(volunteerId),
                associago.volunteers.getShiftsByVolunteer(volunteerId),
                associago.volunteers.getExpensesByVolunteer(volunteerId)
            ]);
            setVolunteer(v);
            setShifts(s);
            setExpenses(e);
        } catch (error) {
            console.error("Error loading volunteer details:", error);
            setError(error.message);
        } finally {
            setLoading(false);
        }
    };

    const handleExpenseStatusChange = async (expenseId, newStatus) => {
        setUpdatingExpenseId(expenseId);
        try {
            await associago.volunteers.updateExpenseStatus(expenseId, newStatus, associationId);
            await loadData();
        } catch (error) {
            console.error("Error updating expense status:", error);
            setError(error.message);
        } finally {
            setUpdatingExpenseId(null);
        }
    };

    if (loading) return <div className="text-center p-5"><Spinner animation="border" variant="primary" /></div>;
    if (!volunteer) return <div className="p-4"><Button variant="link" onClick={onBack}>{t('Back')}</Button><Alert variant="danger">{error || t('Volunteer not found')} <Button variant="link" onClick={loadData}>{t('Retry')}</Button></Alert></div>;

    return (
        <div className="fade-in">
            <div className="d-flex align-items-center mb-4">
                <Button variant="link" className="p-0 me-3 text-dark" onClick={onBack} aria-label={t('Back')}>
                    <ArrowLeft size={24} />
                </Button>
                <div>
                    <h2 className="fw-bold text-dark mb-0">{volunteerName || `${t('Volunteer')} #${volunteer.id}`}</h2>
                    <div className="text-muted small">
                        {t('Member ID')}: {volunteer.memberId}
                    </div>
                </div>
                <div className="ms-auto">
                    <Button variant="outline-primary" className="me-2" onClick={() => shell.openModal('volunteer-form', { associationId, volunteerId: volunteer.id, onSuccess: loadData })}>
                        <Edit size={18} className="me-2" /> {t('Edit')}
                    </Button>
                </div>
            </div>

            {error && <Alert variant="danger" role="alert">{error}</Alert>}
            <div className="row g-4 mb-4">
                <div className="col-md-4">
                    <Card className="border-0 shadow-sm h-100">
                        <Card.Body>
                            <h6 className="text-muted text-uppercase small fw-bold">{t('Status')}</h6>
                            <Badge bg={volunteer.status === 'ACTIVE' ? 'success' : 'secondary'} className="fs-6">
                                {t(volunteer.status)}
                            </Badge>
                        </Card.Body>
                    </Card>
                </div>
                <div className="col-md-4">
                    <Card className="border-0 shadow-sm h-100">
                        <Card.Body>
                            <h6 className="text-muted text-uppercase small fw-bold">{t('Skills')}</h6>
                            <p className="mb-0">{volunteer.skills || '-'}</p>
                        </Card.Body>
                    </Card>
                </div>
                <div className="col-md-4">
                    <Card className="border-0 shadow-sm h-100">
                        <Card.Body>
                            <h6 className="text-muted text-uppercase small fw-bold">{t('Availability')}</h6>
                            <p className="mb-0">{volunteer.availability || '-'}</p>
                        </Card.Body>
                    </Card>
                </div>
            </div>

            <Card className="border-0 shadow-sm">
                <Card.Header className="bg-white border-bottom-0 pt-0 px-0">
                    <Nav variant="tabs" className="px-3 pt-3" activeKey={activeTab} onSelect={k => setActiveTab(k)}>
                        <Nav.Item><Nav.Link eventKey="shifts" className="d-flex align-items-center gap-2"><Clock size={18}/> {t('Shifts')}</Nav.Link></Nav.Item>
                        <Nav.Item><Nav.Link eventKey="expenses" className="d-flex align-items-center gap-2"><DollarSign size={18}/> {t('Expenses')}</Nav.Link></Nav.Item>
                    </Nav>
                </Card.Header>
                <Card.Body className="p-0">
                    {activeTab === 'shifts' && (
                        <div>
                            <div className="p-3 text-end border-bottom">
                                <Button size="sm" onClick={() => shell.openModal('volunteer-shift-form', { volunteerId, associationId, onSuccess: loadData })}>
                                    <Plus size={16} className="me-1"/> {t('Add Shift')}
                                </Button>
                            </div>
                            <Table hover responsive className="mb-0 align-middle">
                                <thead className="bg-light">
                                    <tr>
                                        <th className="ps-4">{t('Start Time')}</th>
                                        <th>{t('End Time')}</th>
                                        <th>{t('Role')}</th>
                                        <th>{t('Hours')}</th>
                                        <th>{t('Status')}</th>
                                        <th className="text-end pe-4">{t('Actions')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {shifts.map(s => (
                                        <tr key={s.id}>
                                            <td className="ps-4">{new Date(s.startTime).toLocaleString()}</td>
                                            <td>{new Date(s.endTime).toLocaleString()}</td>
                                            <td>{s.role}</td>
                                            <td>{s.hoursWorked}</td>
                                            <td><Badge bg="light" text="dark" className="border">{t(s.status)}</Badge></td>
                                            <td className="text-end pe-4">
                                                <Button variant="link" className="text-muted p-0 me-2" aria-label={t('Edit')} onClick={() => shell.openModal('volunteer-shift-form', { volunteerId, associationId, shift: s, onSuccess: loadData })}><Edit size={16}/></Button>
                                            </td>
                                        </tr>
                                    ))}
                                    {shifts.length === 0 && <tr><td colSpan="6" className="text-center py-4 text-muted">{t('No shifts')}</td></tr>}
                                </tbody>
                            </Table>
                        </div>
                    )}

                    {activeTab === 'expenses' && (
                        <div>
                            <div className="p-3 text-end border-bottom">
                                <Button size="sm" onClick={() => shell.openModal('volunteer-expense-form', { volunteerId, associationId: shell?.currentAssociationId, onSuccess: loadData })}>
                                    <Plus size={16} className="me-1"/> {t('Add Expense')}
                                </Button>
                            </div>
                            <Table hover responsive className="mb-0 align-middle">
                                <thead className="bg-light">
                                    <tr>
                                        <th className="ps-4">{t('Date')}</th>
                                        <th>{t('Description')}</th>
                                        <th>{t('Amount')}</th>
                                        <th>{t('Status')}</th>
                                        <th className="text-end pe-4">{t('Actions')}</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    {expenses.map(e => (
                                        <tr key={e.id}>
                                            <td className="ps-4">{new Date(e.expenseDate).toLocaleDateString()}</td>
                                            <td>{e.description}</td>
                                            <td className="fw-bold">€ {e.amount}</td>
                                            <td>
                                                <Badge bg={
                                                    e.status === 'PAID' ? 'success' :
                                                    e.status === 'APPROVED' ? 'info' :
                                                    e.status === 'REJECTED' ? 'danger' : 'warning'
                                                }>
                                                    {t(e.status)}
                                                </Badge>
                                            </td>
                                            <td className="text-end pe-4">
                                                {/* La ricevuta si allega prima di approvare: il
                                                    rimborso analitico senza giustificativo viene
                                                    respinto dall'art. 17, comma 3. */}
                                                <Button
                                                    variant="link"
                                                    size="sm"
                                                    className="text-secondary p-0 me-3"
                                                    title={t('Ricevuta')}
                                                    onClick={() => setRicevutaDi(e)}
                                                >
                                                    <Paperclip size={15} />
                                                </Button>
                                                {e.status === 'PENDING' && (
                                                    <>
                                                        <Button variant="link" size="sm" disabled={updatingExpenseId !== null} className="text-success p-0 me-2" onClick={() => handleExpenseStatusChange(e.id, 'APPROVED')}>
                                                            {t('Approve')}
                                                        </Button>
                                                        <Button variant="link" size="sm" disabled={updatingExpenseId !== null} className="text-danger p-0 me-2" onClick={() => handleExpenseStatusChange(e.id, 'REJECTED')}>
                                                            {t('Reject')}
                                                        </Button>
                                                    </>
                                                )}
                                                {e.status === 'APPROVED' && (
                                                    <Button variant="link" size="sm" disabled={updatingExpenseId !== null} className="text-success p-0 me-2" onClick={() => handleExpenseStatusChange(e.id, 'PAID')}>
                                                        {t('Mark Paid')}
                                                    </Button>
                                                )}
                                            </td>
                                        </tr>
                                    ))}
                                    {expenses.length === 0 && <tr><td colSpan="5" className="text-center py-4 text-muted">{t('No expenses')}</td></tr>}
                                </tbody>
                            </Table>
                        </div>
                    )}
                </Card.Body>
            </Card>

            <Modal show={Boolean(ricevutaDi)} onHide={() => setRicevutaDi(null)} centered>
                <Modal.Header closeButton>
                    <Modal.Title className="fs-5">{t('Giustificativo della spesa')}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                    {ricevutaDi && (
                        <>
                            <p className="text-muted small">
                                {ricevutaDi.description} · € {ricevutaDi.amount}
                            </p>
                            <AttachmentPanel
                                ownerType="VOLUNTEER_EXPENSE"
                                ownerId={ricevutaDi.id}
                                titolo=""
                                kinds={[{ id: 'ricevuta', label: t('Ricevuta o fattura') }]}
                            />
                            {ricevutaDi.reimbursementType === 'ANALITICO' && (
                                <div className="alert alert-info py-2 small mt-3 mb-0">
                                    {t("Il rimborso analitico (art. 17 c. 3) non si può approvare senza questo documento.")}
                                </div>
                            )}
                        </>
                    )}
                </Modal.Body>
            </Modal>
        </div>
    );
};

export default VolunteerDetail;
