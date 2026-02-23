
-- +goose Up
-- +goose StatementBegin

insert into account (client_id,amount)
values
    ('ab745479-5741-46a8-b0b4-36174ef1fe7d',5000),
    ('f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2',1000);

-- +goose StatementEnd
-- +goose Down
-- +goose StatementBegin

delete from account where client_id in  ('ab745479-5741-46a8-b0b4-36174ef1fe7d','f54e7dc2-f8aa-45bc-b632-ea0c15eaa5e2')

-- +goose StatementEnd