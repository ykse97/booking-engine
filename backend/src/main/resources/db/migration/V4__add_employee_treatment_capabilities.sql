CREATE TABLE employee_treatment (
    employee_id UUID NOT NULL REFERENCES employee(id) ON DELETE CASCADE,
    treatment_id UUID NOT NULL REFERENCES treatment(id) ON DELETE CASCADE,
    PRIMARY KEY (employee_id, treatment_id)
);

CREATE INDEX idx_employee_treatment_treatment_id
    ON employee_treatment(treatment_id);

INSERT INTO employee_treatment (employee_id, treatment_id)
SELECT e.id, t.id
FROM employee e
CROSS JOIN treatment t
WHERE e.active = TRUE
  AND t.active = TRUE
ON CONFLICT (employee_id, treatment_id) DO NOTHING;
