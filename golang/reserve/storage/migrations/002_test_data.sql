-- +goose Up
INSERT INTO warehouse_item (id, amount, reserved, unit_cost) VALUES('f7c36185-f570-4e6b-b1b2-f3f0f9c46135', 100, 0, 500.0);
INSERT INTO warehouse_item (id, amount, reserved, unit_cost) VALUES('374fabf8-dd31-4912-93b1-57d177b9f6c6', 200, 0, 155.0);

-- +goose Down

delete from warehouse_item where id in ('f7c36185-f570-4e6b-b1b2-f3f0f9c46135', '374fabf8-dd31-4912-93b1-57d177b9f6c6');
