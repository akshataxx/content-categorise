SELECT spike_add((SELECT id FROM spike_c WHERE i = 3), (SELECT id FROM spike_cand WHERE client = :client_id), :lock, 0.2);
