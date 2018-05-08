CREATE OR REPLACE FUNCTION shm.get_acc_stat(ids bigint[])
  RETURNS TABLE(account_id bigint, curr_sym_code character varying, creation_time timestamp without time zone, description character varying, own_accumulated bigint, max_accumulated bigint, min_accumulated bigint)
LANGUAGE plpgsql
AS $function$
BEGIN
  return query select ac.id, ac.curr_sym_code, ac.creation_time, ac.description, al.own_accumulated, al.max_accumulated, al.min_accumulated from shm.account ac
    LEFT JOIN LATERAL (
              select t2.own_accumulated, t2.max_accumulated, t2.min_accumulated
              from shm.account_log t2
              where t2.account_id = ac.id
                    and t2.account_id = any(ids) /* dup condition to use composite index */
              order by t2.id desc limit 1
              ) al on true
  where ac.id = any(ids);
END;
$function$;

CREATE OR REPLACE FUNCTION shm.get_acc_stat_upto(ids BIGINT[], to_plan_id VARCHAR, to_batch_id BIGINT)
  RETURNS table(account_id bigint, curr_sym_code varchar, creation_time timestamp without time zone, description VARCHAR, own_accumulated bigint, max_accumulated bigint, min_accumulated BIGINT)
LANGUAGE plpgsql
AS $function$
BEGIN
  return query select ac.id, ac.curr_sym_code, ac.creation_time, ac.description, al.own_accumulated, al.max_accumulated, al.min_accumulated from shm.account ac
    LEFT JOIN LATERAL (
              select t2.own_accumulated, t2.max_accumulated, t2.min_accumulated
              from shm.account_log t2
              where t2.account_id = ac.id
                    and t2.plan_id=to_plan_id
                    and t2.batch_id=to_batch_id
              order by t2.id desc limit 1
              ) al on true
  where ac.id = any(ids);
END;
$function$;