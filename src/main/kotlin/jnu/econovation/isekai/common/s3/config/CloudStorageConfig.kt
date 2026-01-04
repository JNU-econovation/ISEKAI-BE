package jnu.econovation.isekai.common.s3.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.s3.InMemoryBufferingS3OutputStreamProvider
import io.awspring.cloud.s3.Jackson2JsonS3ObjectConverter
import io.awspring.cloud.s3.S3Template
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class CloudStorageConfig(
    private val properties: CloudStorageProperties
) {
    val endpointUrl: String
        get() = if (properties.host.startsWith("https")) {
            "${properties.host}:${properties.port}"
        } else {
            "https://${properties.host}:${properties.port}"
        }

    @Bean
    fun s3Client(): S3Client {
        return S3Client.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
                )
            )
            .endpointOverride(URI.create(endpointUrl))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        return S3Presigner.builder()
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
                )
            )
            .endpointOverride(URI.create(endpointUrl))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()
    }

    @Bean
    fun s3Template(
        s3Client: S3Client,
        s3Presigner: S3Presigner,
        objectMapper: ObjectMapper
    ): S3Template {
        val converter = Jackson2JsonS3ObjectConverter(objectMapper)
        val provider = InMemoryBufferingS3OutputStreamProvider(s3Client, null)
        return S3Template(s3Client, provider, converter, s3Presigner)
    }
}