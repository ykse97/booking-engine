-- Removes the historical seeded admin account when it still uses the
-- repository-known default password hash. Rotated or explicitly provisioned
-- admin accounts are left untouched.
DELETE FROM admin_user
WHERE username = 'admin'
  AND password_hash = '$2a$10$/b8Kp9eds4FUCFT6ETS9jO2HiucVz4k3MUBdkP1BNCmX9iHZKJUmW';
