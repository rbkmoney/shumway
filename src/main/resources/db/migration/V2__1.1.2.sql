drop table if exists shm.account_log;

CREATE TABLE shm.account_log
(
  id bigserial NOT NULL,
  plan_id character varying(64) NOT NULL,
  batch_id bigint NOT NULL,
  account_id bigint NOT NULL,
  operation shm.posting_operation_type NOT NULL,
  own_accumulated BIGINT NULL DEFAULT 0,
  max_accumulated BIGINT NULL DEFAULT 0,
  min_accumulated BIGINT NULL DEFAULT 0,
  own_diff bigint NOT NULL,
  max_diff bigint NOT NULL,
  min_diff bigint NOT NULL,
  creation_time timestamp without time zone NOT NULL,
  credit BOOLEAN NOT NULL,
  merged BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT account_log_pkey PRIMARY KEY (id)
)
WITH (
OIDS=FALSE
);

CREATE INDEX account_log_acc_id
  ON shm.account_log
  USING btree
  (account_id, id);

CREATE INDEX account_log_pb_idx
  ON shm.account_log
  USING btree
  (plan_id COLLATE pg_catalog."default", batch_id ,id);