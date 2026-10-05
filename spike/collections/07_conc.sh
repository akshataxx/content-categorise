#!/bin/sh
P="psql -U postgres -d spike -tA"
C3="(select id from spike_c where i=3)"
reset_c3() {  # collection 3 at exactly 4,990, none of the 16 candidates in it
  $P -c "delete from collection_items where collection_id=$C3 and user_transcript_id in (select id from spike_cand)" >/dev/null
  $P -c "delete from collection_items where collection_id=$C3 and user_transcript_id in (select user_transcript_id from collection_items where collection_id=$C3 order by sort_key desc limit (select greatest(count(*)-4990,0) from collection_items where collection_id=$C3))" >/dev/null
  $P -c "insert into collection_items(collection_id,user_transcript_id,sort_key) select $C3, ut.id, 9000000000000000000 - row_number() over () from user_transcripts ut where ut.user_id='11111111-1111-1111-1111-111111111111' and ut.id not in (select id from spike_cand) and not exists (select 1 from collection_items x where x.collection_id=$C3 and x.user_transcript_id=ut.id) limit (select greatest(4990-count(*),0) from collection_items where collection_id=$C3)" >/dev/null
}
reset_u() {  # person at exactly 199 collections, no 'race'/'recipes' names
  $P -c "delete from collections where user_id='11111111-1111-1111-1111-111111111111' and (name_key like 'race %' or name_key='recipes')" >/dev/null
  $P -c "delete from collections where id in (select id from collections where user_id='11111111-1111-1111-1111-111111111111' and name_key like 'collection 2__' order by name_key desc limit (select greatest(count(*)-199,0) from collections where user_id='11111111-1111-1111-1111-111111111111'))" >/dev/null
}
run() { pgbench -n -U postgres -d spike -f /work/spike/collections/bench/$1 -c $2 -j $2 -t 1 -D lock=$3 2>&1 | grep -E "failed|aborted" | head -2; }
for lock in false true; do
  reset_c3; echo "add race lock=$lock start=$($P -c "select count(*) from collection_items where collection_id=$C3")"
  run conc_add.sql 16 $lock; echo "  end=$($P -c "select count(*) from collection_items where collection_id=$C3")"
done
for lock in false true; do
  reset_u; echo "create race lock=$lock start=$($P -c "select count(*) from collections where user_id='11111111-1111-1111-1111-111111111111'")"
  run conc_create.sql 8 $lock; echo "  end=$($P -c "select count(*) from collections where user_id='11111111-1111-1111-1111-111111111111'")"
done
for lock in false true; do
  $P -c "delete from collections where user_id='11111111-1111-1111-1111-111111111111' and (name_key like 'race %' or name_key='recipes')" >/dev/null
  $P -c "delete from collections where id in (select id from collections where user_id='11111111-1111-1111-1111-111111111111' order by name_key desc limit 20)" >/dev/null
  echo "same-name race (8 spellings of Recipes) lock=$lock start=$($P -c "select count(*) from collections where user_id='11111111-1111-1111-1111-111111111111'")"
  run conc_name.sql 8 $lock; echo "  recipes rows=$($P -c "select count(*) from collections where user_id='11111111-1111-1111-1111-111111111111' and name_key='recipes'")"
done
