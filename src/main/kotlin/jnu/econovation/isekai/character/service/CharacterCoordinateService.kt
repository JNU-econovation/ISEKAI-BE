package jnu.econovation.isekai.character.service

import jnu.econovation.isekai.aiServer.client.CharacterGenerateClient
import jnu.econovation.isekai.aiServer.dto.request.AIServerCharacterGenerateRequest
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.dto.request.ConfirmCharacterRequest
import jnu.econovation.isekai.character.dto.request.GenerateBackgroundImageRequest
import jnu.econovation.isekai.character.dto.request.GenerateCharacterRequest
import jnu.econovation.isekai.character.dto.response.GenerateBackgroundImageResponse
import jnu.econovation.isekai.character.dto.response.GenerateCharacterResponse
import jnu.econovation.isekai.character.exception.BadUUIDException
import jnu.econovation.isekai.character.exception.IncompleteCharacterException
import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.model.vo.CharacterName
import jnu.econovation.isekai.character.model.vo.Persona
import jnu.econovation.isekai.character.service.internal.CharacterDataService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.s3.dto.internal.PersistResultDTO
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO
import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import jnu.econovation.isekai.common.s3.enums.FileName
import jnu.econovation.isekai.common.s3.exception.UnexpectedFileSetException
import jnu.econovation.isekai.common.s3.service.S3Service
import jnu.econovation.isekai.gemini.client.GeminiClient
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

@Service
class CharacterCoordinateService(
    private val client: CharacterGenerateClient,
    private val geminiClient: GeminiClient,
    private val s3Service: S3Service,
    private val memberService: MemberService,
    private val dataService: CharacterDataService
) {
    companion object {
        private const val THUMBNAIL_IMAGE_SIZE = 1024

        private val ALL_OF_BASE_NAME = listOf(
            FileName.LIVE2D_MODEL_FILE_NAME,
            FileName.LIVE2D_MODEL_NUKKI_FILE_NAME,
            FileName.BACKGROUND_IMAGE_FILE_NAME
        )
    }

    fun generateCharacter(
        memberInfo: MemberInfoDTO,
        request: GenerateCharacterRequest
    ): GenerateCharacterResponse {
        val live2dModelPresign: PresignDTO = s3Service.generatePresignedPutUrl(
            memberInfo = memberInfo,
            uuid = request.uuid.toUUID(),
            fileName = FileName.LIVE2D_MODEL_FILE_NAME
        )

        val live2dModelNukkiPresign: PresignDTO = s3Service.generatePresignedPutUrl(
            memberInfo = memberInfo,
            uuid = request.uuid.toUUID(),
            fileName = FileName.LIVE2D_MODEL_NUKKI_FILE_NAME
        )

        val aiServerRequest = AIServerCharacterGenerateRequest.from(
            generateCharacterRequest = request,
            live2dPresignDTO = live2dModelPresign,
            nukkiPresignDTO = live2dModelNukkiPresign
        )

        client.requestGeneration(aiServerRequest)
            .onFailure { cause -> throw InternalServerException(cause = cause) }

        val previewDTO = s3Service.getPreviewUrl(
            memberInfo = memberInfo,
            uuid = request.uuid.toUUID(),
            fileName = FileName.LIVE2D_MODEL_FILE_NAME
        )

        return GenerateCharacterResponse.from(previewDTO)
    }

    fun generateBackgroundImage(
        memberInfo: MemberInfoDTO,
        request: GenerateBackgroundImageRequest
    ): GenerateBackgroundImageResponse {
        val image: ByteArray = geminiClient.getImageResponse(request.prompt)
        val uuid = request.uuid.toUUID()

        s3Service.uploadImageImmediately(
            memberInfo = memberInfo,
            uuid = uuid,
            fileName = FileName.BACKGROUND_IMAGE_FILE_NAME,
            image = image
        )

        val previewDTO: PreviewDTO = s3Service.getPreviewUrl(
            memberInfo = memberInfo,
            uuid = uuid,
            fileName = FileName.BACKGROUND_IMAGE_FILE_NAME
        )

        return GenerateBackgroundImageResponse.from(previewDTO)
    }

    fun confirmCharacter(
        memberInfo: MemberInfoDTO,
        request: ConfirmCharacterRequest
    ): Long {
        val uuid = request.uuid.toUUID()

        val persistenceUrls: MutableList<PersistResultDTO> = try {
            s3Service.copyPreviewToPersisted(
                memberInfo = memberInfo,
                uuid = uuid,
                allOfFileName = ALL_OF_BASE_NAME
            )
        } catch (_: UnexpectedFileSetException) {
            throw IncompleteCharacterException()
        }.toMutableList()

        val bgBytes = s3Service.downloadPersistedFile(
            memberInfo = memberInfo,
            uuid = uuid,
            fileName = FileName.BACKGROUND_IMAGE_FILE_NAME
        )

        val nukkiBytes = s3Service.downloadPersistedFile(
            memberInfo = memberInfo,
            uuid = uuid,
            fileName = FileName.LIVE2D_MODEL_NUKKI_FILE_NAME
        )

        val thumbnailBytes = mergeImages(bgBytes, nukkiBytes)

        val thumbnailUrl: PersistResultDTO = s3Service.uploadPersistedFile(
            memberInfo = memberInfo,
            uuid = uuid,
            fileName = FileName.THUMBNAIL_IMAGE_FILE_NAME,
            fileBytes = thumbnailBytes
        )

        persistenceUrls.add(thumbnailUrl)

        val author: Member = memberService.getEntity(memberInfo.id)
            ?: throw InternalServerException(cause = IllegalStateException("회원을 찾지 못함 -> $memberInfo"))

        val characterBuilder: Character.CharacterBuilder = Character.builder()
            .author(author)
            .persona(Persona(request.persona))
            .name(CharacterName(request.name))
            .voiceId(request.voiceId)
            .isPublic(true)

        persistenceUrls.forEach { persistResultDTO ->
            when (persistResultDTO.fileName) {
                FileName.LIVE2D_MODEL_FILE_NAME -> {
                    characterBuilder.live2dModelUrl(persistResultDTO.url)
                }

                FileName.BACKGROUND_IMAGE_FILE_NAME -> {
                    characterBuilder.backgroundUrl(persistResultDTO.url)
                }

                FileName.LIVE2D_MODEL_NUKKI_FILE_NAME -> {
                    characterBuilder.live2dModelNukkiUrl(persistResultDTO.url)
                }

                FileName.THUMBNAIL_IMAGE_FILE_NAME -> {
                    characterBuilder.thumbnailUrl(persistResultDTO.url)
                }
            }
        }

        //@Transactional
        return dataService.save(characterBuilder.build())
    }

    @Transactional(readOnly = true)
    fun getCharacter(id: Long?): CharacterDTO? {
        if (id == null) return null

        val entity = getCharacterEntity(id) ?: return null

        return CharacterDTO.from(entity)
    }

    @Transactional(readOnly = true)
    fun getCharacterEntity(id: Long?): Character? {
        if (id == null) return null

        return dataService.findByIdAndIsPublic(id)
    }

    private fun String.toUUID(): UUID = try {
        UUID.fromString(this)
    } catch (_: Exception) {
        throw BadUUIDException()
    }


    private fun mergeImages(bgBytes: ByteArray, nukkiBytes: ByteArray): ByteArray {
        return try {
            val bgImage: BufferedImage = ImageIO.read(ByteArrayInputStream(bgBytes))
            val nukkiImage: BufferedImage = ImageIO.read(ByteArrayInputStream(nukkiBytes))

            // 1. 썸네일 해상도 설정 (1024x1024 권장)
            // 원본들이 고해상도라 600은 좀 작을 수 있어 1024로 잡았습니다.
            val thumbnail = BufferedImage(
                THUMBNAIL_IMAGE_SIZE,
                THUMBNAIL_IMAGE_SIZE,
                BufferedImage.TYPE_INT_ARGB
            )

            val g = thumbnail.createGraphics()

            // 2. 화질 보정 옵션 (필수)
            g.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC
            )
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // ---------------------------------------------------------
            // 3. 배경 그리기 (16:9 -> 1:1 Center Crop)
            // ---------------------------------------------------------
            // 배경의 높이를 썸네일 높이(1024)에 맞춥니다. (Height Fit)
            // 그러면 너비는 1024보다 커지게 되며, 양옆이 잘리게 됩니다.
            val bgScale = THUMBNAIL_IMAGE_SIZE.toDouble() / bgImage.height
            val bgScaledW = (bgImage.width * bgScale).toInt()
            val bgScaledH = (bgImage.height * bgScale).toInt()

            // 가로 중앙 정렬 좌표 계산 (음수 좌표가 나와서 왼쪽 부분이 잘림)
            val bgX = (THUMBNAIL_IMAGE_SIZE - bgScaledW) / 2
            val bgY = 0

            g.drawImage(bgImage, bgX, bgY, bgScaledW, bgScaledH, null)

            // ---------------------------------------------------------
            // 4. 캐릭터 그리기 (상반신 Zoom-In & 중앙 정렬)
            // ---------------------------------------------------------
            // 캐릭터 전신의 상단 "50%"가 썸네일 높이(1024)에 딱 맞게 들어오도록 확대합니다.
            // 0.5가 적당하지만, 머리가 너무 크거나 작으면 이 숫자를 조절하세요.
            // (0.4 = 더 크게 확대(얼굴 위주), 0.6 = 덜 확대(허리까지))
            val bodyRatio = 0.5

            val charScale = THUMBNAIL_IMAGE_SIZE.toDouble() / (nukkiImage.height * bodyRatio)

            val charScaledW = (nukkiImage.width * charScale).toInt()
            val charScaledH = (nukkiImage.height * charScale).toInt()

            // 가로 중앙 정렬 (배경 중앙과 일치)
            val charX = (THUMBNAIL_IMAGE_SIZE - charScaledW) / 2

            // 세로 위치: 머리 위 여백을 약간(5%) 줍니다.
            // 너무 딱 붙으면 답답해 보일 수 있어서 50px 정도 내립니다.
            val charY = (THUMBNAIL_IMAGE_SIZE * 0.05).toInt()

            g.drawImage(nukkiImage, charX, charY, charScaledW, charScaledH, null)

            // ---------------------------------------------------------

            g.dispose()

            val os = ByteArrayOutputStream()
            ImageIO.write(thumbnail, "png", os)
            os.toByteArray()
        } catch (e: Exception) {
            throw InternalServerException(cause = e)
        }
    }
}