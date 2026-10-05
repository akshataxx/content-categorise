SELECT c.id, c.name, (SELECT count(*) FROM collection_items i WHERE i.collection_id = c.id) AS n
FROM collections c WHERE c.user_id = '11111111-1111-1111-1111-111111111111' ORDER BY c.name_key;
