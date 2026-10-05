CREATE OR REPLACE FUNCTION spike_reorder_b_alt(cid uuid) RETURNS int AS $$
DECLARE arr uuid[]; kk int := (nextval('spike_seq') % 2)::int;
BEGIN
  SELECT ids INTO arr FROM spike_arrays WHERE k = kk;
  RETURN spike_reorder_full(cid, arr);
END $$ LANGUAGE plpgsql;
