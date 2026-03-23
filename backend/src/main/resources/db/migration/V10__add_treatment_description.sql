ALTER TABLE treatment
ADD COLUMN description TEXT NOT NULL DEFAULT '';

UPDATE treatment
SET description = CASE
    WHEN LOWER(name) LIKE '%hair%' THEN 'A precision haircut tailored to your style, face shape, and everyday routine for a clean, confident finish.'
    WHEN LOWER(name) LIKE '%beard%' THEN 'Expert beard grooming with shaping, detailing, and finishing work that keeps your look sharp and polished.'
    WHEN LOWER(name) LIKE '%nose%' OR LOWER(name) LIKE '%wax%' THEN 'A quick detailing treatment that leaves you feeling fresh, neat, and impeccably groomed.'
    ELSE 'A premium grooming service delivered with comfort, precision, and attention to every finishing detail.'
END
WHERE description = '';
