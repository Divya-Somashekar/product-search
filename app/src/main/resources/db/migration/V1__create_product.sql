create table product (
    id             uuid primary key,
    name           varchar(200)   not null,
    description    text           not null default '',
    category       varchar(100)   not null,
    brand          varchar(100)   not null,
    price          numeric(12, 2) not null check (price >= 0),
    currency       char(3)        not null,
    stock_quantity integer        not null default 0 check (stock_quantity >= 0),
    version        bigint         not null,
    created_at     timestamptz    not null,
    updated_at     timestamptz    not null
);

-- Stable ordering for the list endpoint.
create index product_created_at_id_idx on product (created_at, id);
