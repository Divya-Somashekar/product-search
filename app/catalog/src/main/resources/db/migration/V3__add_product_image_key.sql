-- The S3 object key of this product's image, e.g. products/<id>/<random>.jpg, or
-- placeholders/<category>.png for demo data.
--
-- Only the key is stored. The URL a client receives is presigned and expires in minutes, so
-- persisting one would mean storing something that is wrong almost immediately.
--
-- Nullable: a product without an image is ordinary, not an error.
alter table product
    add column image_key varchar(300);
