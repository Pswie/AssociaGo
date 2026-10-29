-- Via quello che serviva a un'associazione sportiva e non a un'APS.
--
-- ---------------------------------------------------------------------------
-- Perché c'era
-- ---------------------------------------------------------------------------
-- Questo gestionale è nato per un'ASD: l'intestazione di
-- V31__medical_certificates.sql lo dice testualmente, e i campi
-- `federation_*` su `associations` sono il numero di affiliazione a una
-- federazione sportiva — CONI, EOC. Un'APS socio-assistenziale non si affilia
-- a nessuna federazione: si iscrive al RUNTS, che è quel che ha preso il loro
-- posto in V8.
--
-- Il costo di lasciarli non era estetico. Una tabella che nessuno usa è una
-- tabella che prima o poi qualcuno riempie «perché c'è» — è già successo con
-- i certificati medici.

-- ---------------------------------------------------------------------------
-- 1. La federazione sportiva
-- ---------------------------------------------------------------------------

ALTER TABLE `associations`
  DROP COLUMN `federation_registration_number`,
  DROP COLUMN `federation_provider`,
  DROP COLUMN `federation_registered_at`;

-- ---------------------------------------------------------------------------
-- 2. Lo sconto sulle quote
-- ---------------------------------------------------------------------------
-- Il coupon qui è un buono dato a una persona: denaro che esce (V6). Lo sconto
-- su una quota è denaro che non entra — un fatto contabile opposto, che non va
-- mai sommato al primo.
--
-- E non era nemmeno usato: né `ActivityService` né `EventService` chiamavano
-- mai `calculateDiscount`. La prova che non lo fosse è l'asimmetria —
-- `activity_participants` aveva le colonne dello sconto, `event_participants`
-- no, pur essendo gli eventi dichiarati applicabili.

ALTER TABLE `coupons`
  DROP COLUMN `discount_type`,
  DROP COLUMN `discount_value`,
  DROP COLUMN `min_amount`,
  DROP COLUMN `max_uses`,
  DROP COLUMN `current_uses`;

DROP TABLE IF EXISTS `coupon_activities`;
DROP TABLE IF EXISTS `coupon_events`;

-- Le colonne che nessun servizio ha mai scritto.
ALTER TABLE `activity_participants`
  DROP COLUMN `coupon_code`,
  DROP COLUMN `discount_amount`,
  DROP COLUMN `discount_type`,
  DROP COLUMN `original_amount`;
