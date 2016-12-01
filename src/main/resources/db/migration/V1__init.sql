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
  plan_id bigint NOT NULL,
  batch_id bigint NOT NULL,
  account_id bigint NOT NULL,
  operation shm.posting_operation_type NOT NULL,
  own_amount bigint NOT NULL,
  min_amount bigint NOT NULL,
  max_amount bigint NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  credit BOOLEAN NOT NULL,
  merged BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT account_log_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

create index account_log_account_id_idx on shm.account_log using btree (account_id);
create index acc_test_idx on shm.account_log using btree (plan_id, batch_id, account_id);


CREATE TABLE shm.posting_log
(
  id bigserial NOT NULL,
  plan_id bigint NOT NULL,
  batch_id bigint NOT NULL,
  from_account_id bigint NOT NULL,
  to_account_id bigint NOT NULL,
  operation shm.posting_operation_type NOT NULL,
  amount bigint NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  curr_sym_code character varying(4) NOT NULL,
  description character varying,
  CONSTRAINT posting_log_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

CREATE INDEX posting_log_plan_id_idx
  ON shm.posting_log
  USING btree
  (plan_id, batch_id);

CREATE TABLE shm.plan_log
(
  id bigserial NOT NULL,
  plan_id character varying(64) UNIQUE NOT NULL,
  last_batch_id bigint NOT NULL,
  last_operation shm.posting_operation_type NOT NULL,
  last_access_time timestamp without time zone NOT NULL,
  CONSTRAINT plan_log_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

CREATE INDEX plan_log_plan_id_id_idx
  ON shm.plan_log
  USING btree
  (plan_id COLLATE pg_catalog."default", id);