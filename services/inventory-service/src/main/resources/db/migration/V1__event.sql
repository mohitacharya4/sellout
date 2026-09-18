create table event (
    id             uuid primary key,
    name           text        not null,
    starts_at      timestamptz not null,
    sale_opens_at  timestamptz not null,
    sale_closes_at timestamptz not null,
    constraint event_name_not_blank check (btrim(name) <> ''),
    constraint event_sale_window check (sale_opens_at < sale_closes_at and sale_closes_at <= starts_at)
);
