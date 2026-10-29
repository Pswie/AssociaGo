-- Tre cose che vanno insieme:
--
--   1. i certificati medici perdono i campi sanitari che non servono;
--   2. il coupon diventa quello che è davvero — un buono dato a qualcuno, che
--      costa soldi veri all'associazione;
--   3. un'uscita può ricevere aggiunte (IVA, commissioni, spese) dopo essere
--      stata registrata, e il totale si ricalcola da sé.

-- ---------------------------------------------------------------------------
-- 1. Certificati medici: restano tipo e scadenza
-- ---------------------------------------------------------------------------
-- Una data di scadenza dice se una persona può fare un turno, e non è un dato
-- sulla salute. Il nome del medico, la struttura sanitaria e una nota libera
-- attaccata a un certificato lo sono — art. 9 GDPR — e non servivano a niente
-- che l'associazione debba fare.
--
-- `file_path` se ne va con loro, e non è una perdita: puntava a un file sul
-- disco locale del contenitore Cloud Run, che al riavvio dell'istanza non
-- c'era più. Nessun endpoint lo rileggeva comunque — c'era il caricamento,
-- non lo scaricamento.

ALTER TABLE `medical_certificates`
  DROP COLUMN `file_path`,
  DROP COLUMN `notes`,
  DROP COLUMN `issued_by`,
  DROP COLUMN `medical_facility`;

-- ---------------------------------------------------------------------------
-- 2. Il coupon è un buono, non uno sconto
-- ---------------------------------------------------------------------------
-- Un buono da 10 euro dato a una persona è denaro che esce dall'associazione:
-- va nelle uscite come qualunque altra spesa. È diverso da uno sconto sulla
-- quota, che è denaro che non entra — e le colonne dello sconto restano solo
-- perché la schermata desktop le scrive ancora. Sono diventate nullable: un
-- buono non ha un tipo di sconto da dichiarare.

ALTER TABLE `coupons`
  -- Quanto vale il buono per chi lo riceve. È anche l'importo che esce, finché
  -- non gli si aggiunge qualcosa.
  ADD COLUMN `nominal_value` decimal(38,2) DEFAULT NULL AFTER `description`,

  -- A chi è andato. Due colonne e non una perché i destinatari sono di due
  -- specie diverse:
  --
  --  * un socio o un volontario sta in `members`, e allora si punta alla riga;
  --  * una persona assistita **non** sta qui e non deve starci. L'anagrafica
  --    degli assistiti è un altro database, su un altro sistema, con i nomi
  --    dentro buste cifrate. In `beneficiary_ref` va il suo codice — `ASS-0042`
  --    — non il suo nome: scriverlo qui farebbe del gestionale un secondo posto
  --    dove i nomi delle persone fragili stanno in chiaro, che è esattamente
  --    ciò che la cifratura dell'anagrafica serve a evitare.
  ADD COLUMN `beneficiary_member_id` bigint DEFAULT NULL,
  ADD COLUMN `beneficiary_ref` varchar(64) DEFAULT NULL,

  ADD COLUMN `issued_date` date DEFAULT NULL,

  -- Il movimento in uscita generato dall'emissione. Nullable perché un buono
  -- preparato e non ancora consegnato non ha ancora mosso denaro.
  ADD COLUMN `transaction_id` bigint DEFAULT NULL,

  -- emesso | consegnato | annullato
  ADD COLUMN `status` varchar(32) NOT NULL DEFAULT 'emesso',

  ADD KEY `ix_coupons_beneficiary_member` (`beneficiary_member_id`),
  ADD KEY `ix_coupons_beneficiary_ref` (`beneficiary_ref`),
  ADD KEY `ix_coupons_transaction` (`transaction_id`);

-- Lo sconto non è più obbligatorio: un buono non ne ha uno.
ALTER TABLE `coupons`
  MODIFY `discount_type`  varchar(255)   DEFAULT NULL,
  MODIFY `discount_value` decimal(38,2)  DEFAULT NULL;

-- ---------------------------------------------------------------------------
-- 3. Le aggiunte su un'uscita
-- ---------------------------------------------------------------------------
-- «Poi dopo devi mettermi nelle uscite la possibilità di aggiungere IVA o
-- altro»: un movimento si registra quando il denaro esce, ma la cifra
-- definitiva arriva dopo — con la fattura, con la commissione del fornitore,
-- con l'IVA. Finora l'unico modo era riscrivere l'importo, e riscrivere un
-- importo registrato fa sparire la storia: a fine anno non si riconcilia più
-- con quello che si è dichiarato.
--
-- Qui l'aggiunta è una riga sua. L'importo del movimento resta la somma di
-- tutto — così ogni rapporto già scritto continua a leggere `amount` e trova
-- il totale giusto — mentre `base_amount` ricorda da dove si era partiti.
--
--   amount = base_amount + somma delle aggiunte
--
-- L'invariante vale sempre, anche per le righe di prima: il backfill qui sotto
-- le allinea.

ALTER TABLE `transactions`
  ADD COLUMN `base_amount` decimal(38,2) DEFAULT NULL AFTER `amount`;

-- Le righe esistenti non hanno aggiunte, quindi partivano da dove sono
-- arrivate. Senza questo, l'invariante sarebbe falsa su tutto lo storico.
UPDATE `transactions` SET `base_amount` = `amount` WHERE `base_amount` IS NULL;

CREATE TABLE `transaction_additions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `transaction_id` bigint NOT NULL,

  -- iva | commissione | spese | altro
  --
  -- Aperto invece che fisso perché la differenza cambia caso per caso: sullo
  -- stesso buono può essere la commissione di chi lo stampa, l'IVA, o le spese
  -- di consegna. Raggrupparle per tipo nei rapporti è quel che permette di
  -- dire «nel 2026 avete pagato 200 euro di commissioni».
  `kind` varchar(32) NOT NULL DEFAULT 'altro',

  -- Con segno: un'aggiunta positiva alza il totale, una negativa lo abbassa.
  -- Serve per le rettifiche in diminuzione — un importo stimato troppo alto si
  -- corregge aggiungendo un valore negativo, non riscrivendo l'originale.
  `amount` decimal(38,2) NOT NULL,

  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,

  PRIMARY KEY (`id`),
  KEY `ix_transaction_additions_transaction` (`transaction_id`),
  KEY `ix_transaction_additions_kind` (`kind`),
  CONSTRAINT `fk_transaction_additions_transaction`
    FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
