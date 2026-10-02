package com.example.productsearch.catalog.infrastructure

import com.example.productsearch.catalog.config.CatalogProperties
import com.example.productsearch.catalog.domain.ProductImages
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI
import java.util.UUID

/**
 * Product images in S3, reached only through presigned URLs.
 *
 * The bucket blocks all public access, so nothing here is readable without a signature. Presigning
 * is local: it is an HMAC over the request using credentials this process already holds, so no
 * call leaves the JVM and generating a link per item in a page costs nothing measurable.
 *
 * What this *does* mean is that the signing identity's own IAM policy decides whether the signed
 * link works — a presigned URL cannot grant what the signer lacks. The Terraform policy for this
 * service is exactly `s3:GetObject` and `s3:PutObject` on this bucket's objects.
 */
@Component
class S3ProductImages(
    private val presigner: S3Presigner,
    private val s3: S3Client,
    properties: CatalogProperties,
) : ProductImages {
    private val images = properties.images

    override fun viewUrl(key: String): URI =
        presigner
            .presignGetObject(
                GetObjectPresignRequest
                    .builder()
                    .signatureDuration(images.viewUrlTtl)
                    .getObjectRequest(
                        GetObjectRequest
                            .builder()
                            .bucket(images.bucket)
                            .key(key)
                            .build(),
                    ).build(),
            ).url()
            .toURI()

    override fun uploadTarget(
        productId: UUID,
        contentType: String,
    ): ProductImages.UploadTarget {
        // A fresh random segment per upload, so replacing an image never serves a stale copy from
        // a browser or CDN cache that is keyed on the old URL.
        val key = "products/$productId/${UUID.randomUUID()}.${extensionFor(contentType)}"

        val url =
            presigner
                .presignPutObject(
                    PutObjectPresignRequest
                        .builder()
                        .signatureDuration(images.uploadUrlTtl)
                        .putObjectRequest(
                            PutObjectRequest
                                .builder()
                                .bucket(images.bucket)
                                .key(key)
                                // Binding the type into the signature means the browser must send
                                // this exact Content-Type or S3 rejects the PUT. It stops a
                                // client that asked to upload a PNG from storing something else.
                                .contentType(contentType)
                                .build(),
                        ).build(),
                ).url()
                .toURI()

        return ProductImages.UploadTarget(key, url)
    }

    override fun describe(key: String): ProductImages.StoredImage? =
        try {
            val head =
                s3.headObject(
                    HeadObjectRequest
                        .builder()
                        .bucket(images.bucket)
                        .key(key)
                        .build(),
                )
            ProductImages.StoredImage(head.contentType(), head.contentLength())
        } catch (_: NoSuchKeyException) {
            // The seller asked for an upload link and then never used it, or it expired. Not an
            // error: the caller turns this into a 400 telling them to upload first.
            null
        }

    private fun extensionFor(contentType: String) =
        when (contentType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            // Unreachable: the caller validates against the configured allow-list first. Kept
            // total so adding a type to that list without touching this map fails loudly here
            // rather than writing an extensionless key.
            else -> error("no extension mapped for content type $contentType")
        }
}
