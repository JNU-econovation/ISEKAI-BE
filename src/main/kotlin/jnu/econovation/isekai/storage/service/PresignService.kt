package jnu.econovation.isekai.storage.service

import io.awspring.cloud.s3.S3Template
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.storage.config.CloudStorageProperties
import jnu.econovation.isekai.storage.dto.request.PresignRequest
import mu.KotlinLogging
import org.springframework.stereotype.Service
import software.amazon.awssdk.awscore.exception.AwsServiceException
import software.amazon.awssdk.core.exception.SdkClientException
import java.time.Duration

@Service
class PresignService(
    private val s3Template: S3Template,
    private val properties: CloudStorageProperties
) {
    companion object {
        private const val EXPIRATION_MINUTES = 10L
        private val logger = KotlinLogging.logger {}
    }

    fun generatePresignedPutUrl(request: PresignRequest, memberInfoDTO: MemberInfoDTO): String {
        val objectKey = "${memberInfoDTO.id}-${request.fileName}"
        val expiration = Duration.ofMinutes(EXPIRATION_MINUTES)

        return try {
            val signedUrl = s3Template.createSignedPutURL(properties.bucket, objectKey, expiration)

            signedUrl.toString()

        } catch (e: Exception) {
            when (e) {
                is AwsServiceException -> logger.error(e) { "S3 서비스 오류: ${e.message}" }
                is SdkClientException -> logger.error(e) { "SDK 클라이언트 오류: ${e.message}" }
                else -> logger.error(e) { "Presigned URL 생성 중 예상치 못한 오류 발생" }
            }
            throw InternalServerException(cause = e)
        }
    }
}