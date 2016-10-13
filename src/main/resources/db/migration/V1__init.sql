create schema if not exists shm;

CREATE TYPE shm.posting_operation_type AS ENUM ('HOLD', 'COMMIT', 'ROLLBACK');

CREATE TABLE shm.account
(
  id bigserial NOT NULL,
  curr_sym_code character varying NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  description character varying(4096),
  CONSTRAINT account_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

CREATE TABLE shm.account_log
(
  id bigserial NOT NULL,
  plan_id character varying(64) NOT NULL,
  posting_id bigint NOT NULL,
  request_id bigint NOT NULL,
  account_id bigint NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  operation shm.posting_operation_type NOT NULL,
  amount bigint NOT NULL,
  own_amount bigint NOT NULL,
  available_amount bigint NOT NULL,
  credit BOOLEAN NOT NULL,
  CONSTRAINT account_log_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

CREATE INDEX account_log_plan_id_idx
  ON shm.account_log
  USING btree
  (plan_id COLLATE pg_catalog."default");

CREATE INDEX account_log_account_id_operation_idx
  ON shm.account_log
  USING btree
  (account_id, operation);

CREATE TABLE shm.posting_log
(
  id bigserial NOT NULL,
  plan_id character varying(64) NOT NULL,
  posting_id bigint NOT NULL,
  request_id bigint NOT NULL,
  from_account_id bigint NOT NULL,
  to_account_id bigint NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  amount bigint NOT NULL,
  curr_sym_code character varying(4) NOT NULL,
  operation shm.posting_operation_type NOT NULL,
  description character varying,
  CONSTRAINT posting_log_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

CREATE INDEX posting_log_plan_id_idx
  ON shm.posting_log
  USING btree
  (plan_id COLLATE pg_catalog."default", operation);

CREATE TABLE shm.plan_log
(
  plan_id character varying(64) NOT NULL,
  last_access_time timestamp without time zone NOT NULL,
  last_operation shm.posting_operation_type NOT NULL,
  last_request_id bigint NOT NULL DEFAULT 1,
  CONSTRAINT plan_log_pkey PRIMARY KEY (plan_id)
)
WITH (
OIDS=FALSE
);
