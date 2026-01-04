package jnu.econovation.isekai.common.s3.service

import io.awspring.cloud.s3.S3Template
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.s3.config.CloudStorageProperties
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO
import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import jnu.econovation.isekai.common.s3.exception.NoSuchFileException
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import mu.KotlinLogging
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CopyObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*

@Service
class S3Service(
    private val s3Template: S3Template,
    private val s3Client: S3Client,
    private val properties: CloudStorageProperties
) {
    companion object {
        private const val EXPIRATION_MINUTES = 10L
        private val logger = KotlinLogging.logger {}
    }

    fun generatePresignedPutUrl(memberInfo: MemberInfoDTO, uuid: UUID): PresignDTO {
        val previewKey = getPreviewKey(memberInfo.id, uuid.toString())
        val expiration = Duration.ofMinutes(EXPIRATION_MINUTES)
        val expirationTime = ZonedDateTime.now().plus(expiration)

        return try {
            val signedUrl = s3Template.createSignedPutURL(properties.bucket, previewKey, expiration)

            PresignDTO(
                url = signedUrl.toString(),
                fileName = uuid.toString(),
                expirationTime = expirationTime
            )
        } catch (e: Exception) {
            logger.error(e) { "Presigned URL 생성 실패" }
            throw InternalServerException(cause = e)
        }
    }

    fun getPreviewUrl(memberInfo: MemberInfoDTO, uuid: UUID): PreviewDTO {
        val previewKey = getPreviewKey(memberInfo.id, uuid.toString())
        val url = s3Template.createSignedGetURL(
            properties.bucket,
            previewKey,
            Duration.ofMinutes(EXPIRATION_MINUTES)
        ).toString()

        return PreviewDTO(
            url = url,
            uuid = uuid.toString(),
            expirationTime = ZonedDateTime.now().plusMinutes(EXPIRATION_MINUTES)
        )
    }

    fun copyPreviewToPersisted(memberInfo: MemberInfoDTO, uuid: String): String {
        val previewKey = getPreviewKey(memberInfo.id, uuid)
        val persistenceKey = "${properties.persistenceDirectory}/${memberInfo.id}/$uuid.zip"
        val copyRequest = CopyObjectRequest.builder()
            .sourceBucket(properties.bucket)
            .sourceKey(previewKey)
            .destinationBucket(properties.bucket)
            .destinationKey(persistenceKey)
            .build()

        runCatching {
            s3Client.copyObject(copyRequest)
        }.onFailure { exception ->
            when (exception) {
                is NoSuchKeyException -> {
                    throw NoSuchFileException()
                }

                else -> {
                    logger.error(exception) { "S3 파일 복사 중 오류 발생: $previewKey" }
                    throw InternalServerException(cause = exception)
                }
            }
        }

        runCatching {
            s3Template.deleteObject(properties.bucket, previewKey)
        }.onFailure { exception ->
            logger.warn(exception) { "임시 파일 삭제 실패 (TTL에 의해 자동 삭제될 수 있음): $previewKey" }
        }

        return persistenceKey
    }

    private fun getPreviewKey(memberId: Long, uuid: String): String {
        return "${properties.previewDirectory}/$memberId/$uuid.zip"
    }
}