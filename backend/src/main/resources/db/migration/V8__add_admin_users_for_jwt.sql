CREATE TABLE admin_user (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- Default admin accounts for initial setup (password: password).
-- Change credentials immediately in production.
INSERT INTO admin_user (username, password_hash, role, active)
VALUES
('admin', '$2a$10$/b8Kp9eds4FUCFT6ETS9jO2HiucVz4k3MUBdkP1BNCmX9iHZKJUmW', 'ADMIN', true)
ON CONFLICT (username) DO NOTHING;
