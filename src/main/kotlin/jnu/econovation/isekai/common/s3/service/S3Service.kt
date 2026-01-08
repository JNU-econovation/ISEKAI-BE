package jnu.econovation.isekai.common.s3.service

import io.awspring.cloud.s3.S3Template
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.s3.config.CloudStorageConfig
import jnu.econovation.isekai.common.s3.config.CloudStorageProperties
import jnu.econovation.isekai.common.s3.dto.internal.PersistDTO
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO
import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import jnu.econovation.isekai.common.s3.enums.FileName
import jnu.econovation.isekai.common.s3.exception.FileDownloadFailedException
import jnu.econovation.isekai.common.s3.exception.UnexpectedFileSetException
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import mu.KotlinLogging
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import java.net.URI
import java.time.Duration
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class S3Service(
    private val config: CloudStorageConfig,
    private val s3Template: S3Template,
    private val s3Presigner: S3Presigner,
    private val s3Client: S3Client,
    private val properties: CloudStorageProperties
) {
    companion object {
        private const val EXPIRATION_MINUTES = 60L
        private val logger = KotlinLogging.logger {}
    }

    fun generatePresignedPutUrl(
        memberInfo: MemberInfoDTO,
        uuid: UUID,
        fileName: FileName
    ): PresignDTO {
        val previewKey = getPreviewKey(memberInfo.id, uuid, fileName)
        val expiration = Duration.ofMinutes(EXPIRATION_MINUTES)
        val expirationTime = ZonedDateTime.now().plus(expiration)

        return try {
            val signedUrl = s3Template.createSignedPutURL(properties.bucket, previewKey, expiration)

            PresignDTO(
                url = signedUrl.toString(),
                fileName = fileName,
                expirationTime = expirationTime
            )
        } catch (e: Exception) {
            logger.error(e) { "Presigned URL 생성 실패" }
            throw InternalServerException(cause = e)
        }
    }

    fun getPreviewUrl(
        memberInfo: MemberInfoDTO,
        uuid: UUID,
        fileName: FileName
    ): PreviewDTO {
        val previewKey = getPreviewKey(memberInfo.id, uuid = uuid, fileName = fileName)

        val getObjectRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(previewKey)
            .build()

        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(EXPIRATION_MINUTES))
            .getObjectRequest(getObjectRequest)
            .build()

        val presignedRequest = s3Presigner.presignGetObject(presignRequest)
        val url = presignedRequest.url().toString()

        return PreviewDTO(
            url = url,
            uuid = uuid.toString(),
            expirationTime = ZonedDateTime.now().plusMinutes(EXPIRATION_MINUTES)
        )
    }

    fun uploadImageImmediately(
        memberInfo: MemberInfoDTO,
        uuid: UUID,
        fileName: FileName,
        image: ByteArray
    ) {
        val previewKey = getPreviewKey(memberInfo.id, uuid = uuid, fileName = fileName)
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(previewKey)
            .contentType("image/${fileName.getExtensionValue()}")
            .build()

        s3Client.putObject(request, RequestBody.fromBytes(image))
    }

    fun copyPreviewToPersisted(
        memberInfo: MemberInfoDTO,
        uuid: UUID,
        allOfFileName: List<FileName>
    ): Result<List<PersistDTO>> {
        validateAllFilesExist(memberInfo.id, uuid, allOfFileName)

        val previewKeyPrefix = getPreviewKeyPrefix(memberInfo.id, uuid)
        val persistenceKeyPrefix = "${properties.persistenceDirectory}/${memberInfo.id}/${uuid}/"
        val succeedUrls = mutableListOf<String>()

        return runCatching {
            allOfFileName.map { fileName ->
                val sourceKey = "$previewKeyPrefix$fileName"
                val destinationKey = "$persistenceKeyPrefix$fileName"

                val copyRequest = CopyObjectRequest.builder()
                    .sourceBucket(properties.bucket)
                    .sourceKey(sourceKey)
                    .destinationBucket(properties.bucket)
                    .destinationKey(destinationKey)
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build()

                s3Client.copyObject(copyRequest)

                val fullUrl = "${config.endpointUrl}/${properties.bucket}/$destinationKey"
                succeedUrls.add(fullUrl)

                PersistDTO(fileName = fileName, url = fullUrl)
            }
        }.onFailure { exception ->
            CompletableFuture.runAsync {  succeedUrls.forEach { delete(it) } }
            logger.error(exception) { "S3 복사 작업 중 에러 발생 - 정리 완료: $succeedUrls" }
        }
    }

    fun downloadPersistedFile(
        memberInfo: MemberInfoDTO,
        uuid: UUID,
        fileName: FileName
    ): ByteArray {
        val key = getPersistenceKey(memberInfo.id, uuid, fileName)

        val getRequest = GetObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .build()

        return try {
            s3Client.getObject(getRequest).readAllBytes()
        } catch (e: Exception) {
            logger.error(e) { "파일 다운로드 실패: $key" }
            throw InternalServerException(cause = FileDownloadFailedException("파일 다운로드 실패: $key"))
        }
    }

    fun uploadPersistedFile(
        memberInfo: MemberInfoDTO,
        uuid: UUID,
        fileName: FileName,
        fileBytes: ByteArray
    ): PersistDTO {
        val key = getPersistenceKey(memberInfo.id, uuid, fileName)

        val putRequest = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType("image/${fileName.getExtensionValue()}")
            .acl(ObjectCannedACL.PUBLIC_READ)
            .build()

        s3Client.putObject(putRequest, RequestBody.fromBytes(fileBytes))

        val url = "${config.endpointUrl}/${properties.bucket}/$key"

        return PersistDTO(url = url, fileName = fileName)
    }

    fun delete(url: String) {
        runCatching {
            val key = URI(url).path.removePrefix("/")
            s3Template.deleteObject(properties.bucket, key)
        }.onFailure { exception ->
            logger.error(exception) { "S3 파일 삭제 실패: $url" }
        }
    }

    private fun validateAllFilesExist(
        memberId: Long,
        uuid: UUID,
        expectedFileNames: List<FileName>
    ) {
        val prefix = getPreviewKeyPrefix(memberId, uuid)

        val listRequest = ListObjectsV2Request.builder()
            .bucket(properties.bucket)
            .prefix(prefix)
            .build()

        val actualFileNames = s3Client.listObjectsV2(listRequest).contents()
            .map { s3Object ->
                s3Object.key().substringAfterLast("/")
            }
            .toSet()

        val expectedSet = expectedFileNames.map { it.toString() }.toSet()

        if (actualFileNames != expectedSet) {
            logger.warn {
                "파일 검증 실패! -> 기대값: $expectedSet, 실제값: $actualFileNames"
            }
            throw UnexpectedFileSetException(hint = "기대값: $expectedSet, 실제값: $actualFileNames")
        }
    }

    private fun getPreviewKey(memberId: Long, uuid: UUID, fileName: FileName): String {
        return "${getPreviewKeyPrefix(memberId, uuid)}$fileName"
    }

    private fun getPreviewKeyPrefix(memberId: Long, uuid: UUID): String {
        return "${properties.previewDirectory}/$memberId/$uuid/"
    }

    private fun getPersistenceKey(memberId: Long, uuid: UUID, fileName: FileName): String {
        return "${properties.persistenceDirectory}/$memberId/$uuid/$fileName"
    }
}