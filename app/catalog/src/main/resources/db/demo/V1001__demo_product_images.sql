-- Points the demo catalogue at the placeholder images that scripts/seed-demo-images.sh uploads
-- (in the infra repo), one flat-colour PNG per category.
--
-- A separate migration rather than an edit to V1000: that one has already run in every existing
-- database, and Flyway validates checksums on startup — changing it in place would stop the
-- service booting with a checksum mismatch rather than applying the change.
update product
set image_key = 'placeholders/' || category || '.png'
where image_key is null;
