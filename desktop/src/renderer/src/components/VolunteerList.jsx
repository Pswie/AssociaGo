import React, { useState, useEffect } from 'react';
import { Card, Button, Table, Form, InputGroup, Badge, Spinner, Alert } from 'react-bootstrap';
import { Search, Plus, Edit, Trash2, UserCheck, Eye } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';
import RowActions from './common/RowActions';
import VolunteerDetail from './VolunteerDetail';
import VolunteerInsurancePanel from './VolunteerInsurancePanel';

const VolunteerList = ({ associationId, shell }) => {
    const { t } = useTranslation();
    const [volunteers, setVolunteers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [error, setError] = useState('');
    const [selectedVolunteer, setSelectedVolunteer] = useState(null);

    /*
     * Un volontario non è un fatto puntuale: ha un periodo, e il filtro lo
     * applica il server.
     *
     * Chi è iscritto al registro e non ha cessato è in essere, quindi compare
     * nell'anno guardato e in tutti quelli successivi. Chi ha cessato resta
     * visibile soltanto negli anni che ha attraversato — è così che il registro
     * risponde a «chi era volontario nel 2026», la domanda per cui l'art. 17
     * c.1 lo pretende.
     */
    const anno = shell?.anno ?? new Date().getFullYear();

    useEffect(() => {
        if (associationId) {
            fetchVolunteers();
        }
    }, [associationId, anno]);

    const fetchVolunteers = async () => {
        setLoading(true);
        setError('');
        try {
            const [data, members] = await Promise.all([
                associago.volunteers.getAll({ associationId, anno }),
                associago.members.getAll()
            ]);
            const memberById = new Map(members.map(member => [member.id, member]));
            setVolunteers(data.map(volunteer => {
                const member = memberById.get(volunteer.memberId);
                return {
                    ...volunteer,
                    name: member ? `${member.firstName || ''} ${member.lastName || ''}`.trim() : `${t('Volunteer')} #${volunteer.id}`,
                    email: member?.email || ''
                };
            }));
        } catch (error) {
            console.error("Error fetching volunteers:", error);
            setError(error.message);
        } finally {
            setLoading(false);
        }
    };

    const handleAddVolunteer = () => {
        shell.openModal('volunteer-form', { associationId, onSuccess: fetchVolunteers });
    };

    const filteredVolunteers = volunteers.filter(volunteer =>
        [volunteer.name, volunteer.email, volunteer.skills].some(value => value?.toLowerCase().includes(searchQuery.trim().toLowerCase()))
    );

    const handleDelete = async (id) => {
        if (!confirm(t('Are you sure you want to delete this volunteer?'))) return;
        try {
            await associago.volunteers.delete(id);
            await fetchVolunteers();
        } catch (error) {
            setError(error.message);
        }
    };

    if (selectedVolunteer) {
        return <VolunteerDetail volunteerId={selectedVolunteer.id} volunteerName={selectedVolunteer.name} associationId={associationId} shell={shell} onBack={() => { setSelectedVolunteer(null); fetchVolunteers(); }} />;
    }

    return (
        <div className="fade-in">
            <div className="d-flex justify-content-between align-items-center mb-4">
                <div>
                    <h2 className="fw-bold text-dark mb-1">{t('Volunteers')}</h2>
                    <p className="text-muted mb-0">{t('Manage volunteer activities')}</p>
                </div>
                <Button variant="primary" className="d-flex align-items-center" onClick={handleAddVolunteer}>
                    <Plus size={18} className="me-2" />
                    {t('Add Volunteer')}
                </Button>
            </div>

            {error && <Alert variant="danger" role="alert">{error} <Button variant="link" onClick={fetchVolunteers}>{t('Retry')}</Button></Alert>}
            {/* La copertura assicurativa sta sopra l'elenco e non in una scheda
                a parte: è un obbligo di legge (art. 18 CTS) e la domanda «chi è
                scoperto» va posta prima del turno, non cercata. */}
            <Card className="border-0 shadow-sm mb-4">
                <Card.Body>
                    <VolunteerInsurancePanel associationId={associationId} />
                </Card.Body>
            </Card>

            <Card className="border-0 shadow-sm">
                <Card.Header className="bg-white border-bottom-0 pt-4 pb-0">
                    <div className="d-flex justify-content-between align-items-center mb-3">
                        <div className="d-flex gap-2 jl-search" style={{ width: '300px', maxWidth: '100%' }}>
                            <InputGroup>
                                <InputGroup.Text className="bg-light border-end-0">
                                    <Search size={16} className="text-muted" />
                                </InputGroup.Text>
                                <Form.Control
                                    placeholder={t('Search volunteers...')}
                                    aria-label={t('Search volunteers...')}
                                    className="bg-light border-start-0 ps-0"
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                />
                            </InputGroup>
                        </div>
                    </div>
                </Card.Header>
                <Card.Body className="p-0">
                    {loading ? (
                        <div className="text-center p-5">
                            <Spinner animation="border" variant="primary" />
                        </div>
                    ) : (
                        <Table hover responsive className="mb-0 align-middle">
                            <thead className="bg-light">
                                <tr>
                                    <th className="border-0 ps-4">{t('Name')}</th>
                                    <th className="border-0">{t('Skills')}</th>
                                    <th className="border-0">{t('Availability')}</th>
                                    <th className="border-0">{t('Status')}</th>
                                    <th className="border-0 text-end pe-4">{t('Actions')}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filteredVolunteers.length > 0 ? (
                                    filteredVolunteers.map(volunteer => (
                                        <tr key={volunteer.id}>
                                            <td className="ps-4">
                                                <div className="d-flex align-items-center">
                                                    <div className="rounded bg-success bg-opacity-10 text-success d-flex align-items-center justify-content-center me-3" style={{width: '40px', height: '40px'}}>
                                                        <UserCheck size={20} />
                                                    </div>
                                                    <div>
                                                        <div className="fw-bold text-dark">{volunteer.name}</div>
                                                        <div className="small text-muted">{volunteer.email}</div>
                                                    </div>
                                                </div>
                                            </td>
                                            <td>{volunteer.skills || '-'}</td>
                                            <td>{volunteer.availability || '-'}</td>
                                            <td>
                                                <Badge bg={volunteer.status === 'ACTIVE' ? 'success' : 'secondary'} className="fw-normal px-2 py-1">
                                                    {t(volunteer.status === 'ACTIVE' ? 'Active' : 'Inactive')}
                                                </Badge>
                                            </td>
                                            <td className="text-end pe-4">
                                                <RowActions actions={[
                                                    {
                                                        key: 'detail',
                                                        icon: <Eye size={16} />,
                                                        label: t('View Details'),
                                                        onClick: () => setSelectedVolunteer(volunteer),
                                                    },
                                                    {
                                                        key: 'edit',
                                                        icon: <Edit size={16} />,
                                                        label: t('Edit'),
                                                        onClick: () => shell.openModal('volunteer-form', { associationId, volunteerId: volunteer.id, onSuccess: fetchVolunteers }),
                                                    },
                                                    {
                                                        key: 'delete',
                                                        icon: <Trash2 size={16} />,
                                                        label: t('Delete'),
                                                        textClass: 'text-danger',
                                                        onClick: () => handleDelete(volunteer.id),
                                                    },
                                                ]} />
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan="5" className="text-center py-5 text-muted">
                                            {t('No volunteers found.')}
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </Table>
                    )}
                </Card.Body>
            </Card>
        </div>
    );
};

export default VolunteerList;
