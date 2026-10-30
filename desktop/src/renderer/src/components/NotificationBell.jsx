import React, { useState, useEffect } from 'react';
import { Bell } from 'lucide-react';
import { Alert, OverlayTrigger, Popover, ListGroup, Button } from 'react-bootstrap';
import { useTranslation } from 'react-i18next';
import { associago } from '../api';

export default function NotificationBell({ userId }) {
    const { t } = useTranslation();
    const [notifications, setNotifications] = useState([]);
    const [unreadCount, setUnreadCount] = useState(0);
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (userId) {
            fetchNotifications();
            // Poll every 30 seconds
            const interval = setInterval(fetchNotifications, 30000);
            return () => clearInterval(interval);
        }
    }, [userId]);

    const fetchNotifications = async () => {
        if (!userId) return;
        setLoading(true);
        try {
            const data = await associago.notifications.getUserNotifications(userId);
            setNotifications(data || []);
            setUnreadCount((data || []).filter(n => !n.read).length);
            setError('');
        } catch (error) {
            console.error("Failed to fetch notifications", error);
            setError(error.message || t('Notifiche non disponibili.'));
        } finally {
            setLoading(false);
        }
    };

    const markAllRead = async () => {
        if (saving) return;
        setSaving(true);
        try {
            await associago.notifications.markAllAsRead(userId);
            await fetchNotifications();
        } catch (error) {
            console.error("Failed to mark all as read", error);
            setError(error.message);
        } finally {
            setSaving(false);
        }
    };

    const markAsRead = async (id) => {
        if (saving) return;
        setSaving(true);
        try {
            await associago.notifications.markAsRead(id);
            await fetchNotifications();
        } catch (error) {
            console.error("Failed to mark as read", error);
            setError(error.message);
        } finally {
            setSaving(false);
        }
    };

    const formatTime = (dateString) => {
        const date = new Date(dateString);
        if (Number.isNaN(date.getTime())) return '';
        const now = new Date();
        const diff = Math.max(0, Math.floor((now - date) / 1000));

        if (diff < 60) return t('notifications.timeAgo.seconds', { count: diff });
        if (diff < 3600) return t('notifications.timeAgo.minutes', { count: Math.floor(diff / 60) });
        if (diff < 86400) return t('notifications.timeAgo.hours', { count: Math.floor(diff / 3600) });
        return date.toLocaleDateString();
    };

    const popover = (
        // maxWidth: '100%' non bastava — il riferimento è il contenitore del
        // popover, non lo schermo, e Popper lo posiziona comunque a 320px
        // sforando a destra su un telefono da 320. Con calc il limite è il
        // viewport meno i margini.
        <Popover id="notification-popover" style={{ width: 'min(320px, calc(100vw - 1.5rem))' }}>
            <Popover.Header as="div" className="d-flex justify-content-between align-items-center bg-white border-bottom p-3">
                <strong className="mb-0">{t('notifications.title')}</strong>
                {unreadCount > 0 && (
                    <Button variant="link" size="sm" className="p-0 text-decoration-none" disabled={saving} onClick={markAllRead}>
                        {t('notifications.markAllRead')}
                    </Button>
                )}
            </Popover.Header>
            <Popover.Body className="p-0" style={{ maxHeight: '300px', overflowY: 'auto' }}>
                {error && <Alert variant="danger" className="m-2 small" role="alert">{error}<Button variant="link" size="sm" onClick={fetchNotifications}>{t('Retry')}</Button></Alert>}
                {loading && !notifications.length && <p role="status" className="p-3 mb-0">{t('Loading...')}</p>}
                {notifications.length > 0 ? (
                    <ListGroup variant="flush">
                        {notifications.map(n => (
                            <ListGroup.Item
                                key={n.id}
                                action
                                as="button"
                                disabled={saving}
                                className={`p-3 border-bottom ${!n.read ? 'bg-light' : ''}`}
                                onClick={() => !n.read && markAsRead(n.id)}
                                style={{ cursor: 'pointer' }}
                            >
                                <div className="d-flex justify-content-between mb-1">
                                    <strong className="small text-dark">{n.title}</strong>
                                    <small className="text-muted" style={{ fontSize: '0.7rem' }}>{formatTime(n.createdAt)}</small>
                                </div>
                                <p className="mb-0 small text-secondary">{n.message}</p>
                            </ListGroup.Item>
                        ))}
                    </ListGroup>
                ) : !loading && !error ? (
                    <div className="p-4 text-center text-muted small">
                        {t('notifications.empty')}
                    </div>
                ) : null}
            </Popover.Body>
        </Popover>
    );

    return (
        <OverlayTrigger trigger="click" placement="bottom-end" overlay={popover} rootClose>
            <button type="button" className="btn btn-icon position-relative p-2 rounded-circle hover-bg-light transition-all" disabled={!userId} aria-label={unreadCount ? `${t('notifications.title')}: ${unreadCount} ${t('non lette')}` : t('notifications.title')}>
                <Bell size={20} className="text-secondary" />
                {unreadCount > 0 && (
                    <span className="position-absolute top-0 start-100 translate-middle p-1 bg-danger border border-light rounded-circle">
                        <span className="visually-hidden">{t('notifications.newAlerts')}</span>
                    </span>
                )}
            </button>
        </OverlayTrigger>
    );
}
