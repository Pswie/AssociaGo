-- Gemella SQLite di V11__potatura_asd.sql (serie MySQL).
--
-- Via i campi della federazione sportiva — sostituiti dal RUNTS in V39 — e la
-- macchina degli sconti sui coupon, che qui non è mai stata usata: né
-- ActivityService né EventService chiamavano `calculateDiscount`, e
-- `event_participants` non aveva nemmeno le colonne che
-- `activity_participants` aveva. Il ragionamento completo sta nella gemella.
--
-- Dialetto: un DROP COLUMN per istruzione.

-- 1. La federazione sportiva
ALTER TABLE associations DROP COLUMN federation_registration_number;
ALTER TABLE associations DROP COLUMN federation_provider;
ALTER TABLE associations DROP COLUMN federation_registered_at;

-- 2. Lo sconto sulle quote
-- Le tabelle di collegamento vanno tolte per prime: se una chiave esterna le
-- lega ai coupon, toglierne le colonne dopo sarebbe un vincolo violato.
DROP TABLE IF EXISTS coupon_activities;
DROP TABLE IF EXISTS coupon_events;

ALTER TABLE coupons DROP COLUMN discount_type;
ALTER TABLE coupons DROP COLUMN discount_value;
ALTER TABLE coupons DROP COLUMN min_amount;
ALTER TABLE coupons DROP COLUMN max_uses;
ALTER TABLE coupons DROP COLUMN current_uses;

-- Colonne che nessun servizio ha mai scritto.
ALTER TABLE activity_participants DROP COLUMN coupon_code;
ALTER TABLE activity_participants DROP COLUMN discount_amount;
ALTER TABLE activity_participants DROP COLUMN discount_type;
ALTER TABLE activity_participants DROP COLUMN original_amount;
