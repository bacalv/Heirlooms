UPDATE uploads SET storage_class = 'public' WHERE storage_class = 'legacy_plaintext';
UPDATE capsule_messages SET storage_class = 'public' WHERE storage_class = 'legacy_plaintext';
