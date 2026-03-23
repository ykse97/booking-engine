INSERT INTO hair_salon (id, name, description, email, phone, address)
VALUES (
'550e8400-e29b-41d4-a716-446655440000',
'Barber Shop',
'A trendy barber shop offering classic and modern haircuts.',
'info@barbershop.com',
'+353874749999',
'123 Main St, Dublin, Ireland'
);

INSERT INTO hair_salon_hour (hair_salon_id, day_of_week, working_day, open_time, close_time)
VALUES
('550e8400-e29b-41d4-a716-446655440000','MONDAY',true,'09:00','18:00'),
('550e8400-e29b-41d4-a716-446655440000','TUESDAY',true,'09:00','18:00'),
('550e8400-e29b-41d4-a716-446655440000','WEDNESDAY',true,'09:00','18:00'),
('550e8400-e29b-41d4-a716-446655440000','THURSDAY',true,'09:00','18:00'),
('550e8400-e29b-41d4-a716-446655440000','FRIDAY',true,'09:00','18:00'),
('550e8400-e29b-41d4-a716-446655440000','SATURDAY',true,'10:00','16:00'),
('550e8400-e29b-41d4-a716-446655440000','SUNDAY',false,null,null);