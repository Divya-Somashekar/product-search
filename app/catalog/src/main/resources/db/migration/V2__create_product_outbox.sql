-- A change log written in the same transaction as the product it describes, so a rolled-back
-- write can never announce itself. Consumers (today the search index) read it in `seq` order and
-- remember how far they got.
--
-- Rows carry no payload: a consumer resolves the current state of `product_id` when it reads the
-- entry. Successive edits of one product therefore collapse into whatever is current, which is
-- what a projection wants, and nothing here can go stale.
create table product_outbox (
    seq        bigserial   primary key,
    product_id uuid        not null,
    deleted    boolean     not null,
    created_at timestamptz not null
);

-- Consumers always read "the next rows after my cursor", oldest first.
create index product_outbox_seq_idx on product_outbox (seq);
