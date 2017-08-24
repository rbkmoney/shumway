CREATE OR REPLACE FUNCTION shm.get_acc_stat(ids BIGINT[]) RETURNS table(account_id bigint, curr_sym_code varchar, creation_time timestamp without time zone, description VARCHAR, own_accumulated bigint, max_accumulated bigint, min_accumulated BIGINT)
AS $$
BEGIN
  return QUERY (select ac.*, al.own_accumulated, al.max_accumulated, al.min_accumulated from
    (select t1.id, t1.curr_sym_code, t1.creation_time, t1.description from shm.account t1 where t1.id = any(ids)) ac LEFT JOIN
    (select t2.id, t2.account_id, t2.own_accumulated, t2.max_accumulated, t2.min_accumulated from shm.account_log t2) al
      on ac.id = al.account_id and al.id = (select max(t3.id) from shm.account_log t3  where t3.account_id=ac.id));
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION shm.get_exclusive_acc_stat(ids BIGINT[]) RETURNS table(account_id bigint, curr_sym_code varchar, creation_time timestamp without time zone, description VARCHAR, own_accumulated bigint, max_accumulated bigint, min_accumulated BIGINT)
AS $$
BEGIN
  PERFORM t.id FROM shm.account t WHERE t.id = any(ids) FOR UPDATE;
  return QUERY (select * FROM shm.get_acc_stat(ids));
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION shm.get_acc_stat_upto(ids BIGINT[], to_plan_id VARCHAR, to_batch_id BIGINT) RETURNS table(account_id bigint, curr_sym_code varchar, creation_time timestamp without time zone, description VARCHAR, own_accumulated bigint, max_accumulated bigint, min_accumulated BIGINT)
AS $$
BEGIN
  return QUERY (select ac.*, al.own_accumulated, al.max_accumulated, al.min_accumulated from
    (select t1.id, t1.curr_sym_code, t1.creation_time, t1.description from shm.account t1 where t1.id = any(ids)) ac LEFT JOIN
    (select t2.id, t2.account_id, t2.own_accumulated, t2.max_accumulated, t2.min_accumulated from shm.account_log t2) al
      on ac.id = al.account_id and al.id = (select max(t3.id) from shm.account_log t3  where t3.account_id=ac.id and t3.plan_id=to_plan_id and t3.batch_id=to_batch_id));
END;
$$ LANGUAGE plpgsql;

/*
drop TYPE IF EXISTS shm.plan_record;
create type shm.plan_record as (
  old BOOLEAN,
  plan_id VARCHAR,
  last_access_time timestamp without time zone,
  last_operation shm.posting_operation_type,
  last_batch_id BIGINT
);

CREATE OR REPLACE FUNCTION shm.get_exclusive_posting_plan(plan_id_arg VARCHAR) RETURNS shm.plan_record
AS $$
select TRUE, plan_id, last_access_time, last_operation, last_batch_id from shm.plan_log where plan_id=plan_id_arg for update;
$$ LANGUAGE sql;

CREATE OR REPLACE FUNCTION shm.update_posting_plan(plan_id_arg VARCHAR, last_access_time_arg timestamp without time zone, last_operation_arg shm.posting_operation_type, last_batch_id_arg BIGINT, overridable_operation_arg shm.posting_operation_type, same_operation_arg shm.posting_operation_type) RETURNS SETOF shm.plan_record
AS $$
DECLARE
  old_plan shm.plan_record;
  new_plan shm.plan_record;
BEGIN
  SELECT * from shm.get_exclusive_posting_plan(plan_id_arg) t INTO old_plan;
  update shm.plan_log t set
    last_access_time=last_access_time_arg,
    last_operation=last_operation_arg,
    last_batch_id=last_batch_id_arg
  where t.plan_id=plan_id_arg and t.last_operation in (overridable_operation_arg, same_operation_arg) RETURNING FALSE, t.plan_id, t. last_access_time, t.last_operation, t.last_batch_id INTO new_plan;
  RETURN NEXT old_plan;
  RETURN NEXT new_plan;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION shm.add_or_create_posting_plan(plan_id_arg VARCHAR, last_access_time_arg timestamp without time zone, last_operation_arg shm.posting_operation_type, last_batch_id_arg BIGINT, overridable_operation_arg shm.posting_operation_type) RETURNS SETOF shm.plan_record
AS $$
DECLARE
  old_plan shm.plan_record;
  new_plan shm.plan_record;
BEGIN
  SELECT * from shm.get_exclusive_posting_plan(plan_id_arg) t INTO old_plan;
  insert into shm.plan_log as t (plan_id, last_batch_id, last_access_time, last_operation) values (
    plan_id_arg, last_batch_id_arg, last_access_time_arg, last_operation_arg
  ) on conflict (plan_id) do update set
    last_access_time=last_access_time_arg,
    last_operation=last_operation_arg,
    last_batch_id=last_batch_id_arg
    where t.last_operation=overridable_operation_arg RETURNING FALSE, t.plan_id, t. last_access_time, t.last_operation, t.last_batch_id INTO new_plan;
  RETURN NEXT old_plan;
  RETURN NEXT new_plan;
END;
$$ LANGUAGE plpgsql;
*/


