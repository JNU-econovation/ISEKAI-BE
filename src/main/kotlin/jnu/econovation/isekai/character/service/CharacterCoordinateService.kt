package jnu.econovation.isekai.character.service

import com.google.genai.errors.ServerException
import jnu.econovation.isekai.aiServer.client.CharacterGenerateClient
import jnu.econovation.isekai.aiServer.dto.request.AIServerCharacterGenerateRequest
import jnu.econovation.isekai.character.dto.internal.CharacterDTO
import jnu.econovation.isekai.character.dto.request.ConfirmCharacterRequest
import jnu.econovation.isekai.character.dto.request.GenerateBackgroundImageRequest
import jnu.econovation.isekai.character.dto.request.GenerateCharacterRequest
import jnu.econovation.isekai.character.dto.response.CharacterResponse
import jnu.econovation.isekai.character.dto.response.GenerateBackgroundImageResponse
import jnu.econovation.isekai.character.dto.response.GenerateCharacterResponse
import jnu.econovation.isekai.character.exception.BadUUIDException
import jnu.econovation.isekai.character.exception.IncompleteCharacterException
import jnu.econovation.isekai.character.exception.NoSuchCharacterException
import jnu.econovation.isekai.character.exception.YouAreNotAuthorException
import jnu.econovation.isekai.character.model.entity.Character
import jnu.econovation.isekai.character.model.vo.CharacterName
import jnu.econovation.isekai.character.model.vo.Persona
import jnu.econovation.isekai.character.service.internal.CharacterDataService
import jnu.econovation.isekai.common.exception.server.InternalServerException
import jnu.econovation.isekai.common.s3.dto.internal.PersistDTO
import jnu.econovation.isekai.common.s3.dto.internal.PresignDTO
import jnu.econovation.isekai.common.s3.dto.internal.PreviewDTO
import jnu.econovation.isekai.common.s3.enums.FileName
import jnu.econovation.isekai.common.s3.exception.UnexpectedFileSetException
import jnu.econovation.isekai.common.s3.service.S3Service
import jnu.econovation.isekai.gemini.client.GeminiClient
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel.NANO_BANANA
import jnu.econovation.isekai.gemini.constant.enums.GeminiModel.NANO_BANANA_PRO
import jnu.econovation.isekai.member.dto.internal.MemberInfoDTO
import jnu.econovation.isekai.member.entity.Member
import jnu.econovation.isekai.member.service.MemberService
import mu.KotlinLogging
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.*
import java.util.concurrent.CompletableFuture
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

        private const val DEFAULT_VOICE_ID = 1L

        private val logger = KotlinLogging.logger {}

        private val IMAGE_PLANS = ImagePlan(
            planA = NANO_BANANA,
            planB = NANO_BANANA_PRO
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
        val image: ByteArray = runCatching {
            getBackgroundImage(request.prompt, IMAGE_PLANS.planB)
        }.recoverCatching { exception ->
            when (exception) {
                is InternalServerException, is ServerException -> {
                    logger.warn { "Retry exhausted로 인한 모델 교체: ${IMAGE_PLANS.planB} -> ${IMAGE_PLANS.planA}" }
                    getBackgroundImage(request.prompt, IMAGE_PLANS.planA)
                }

                else -> throw exception
            }
        }.getOrThrow()


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

        val persistence: MutableList<PersistDTO> = s3Service
            .copyPreviewToPersisted(memberInfo, uuid, ALL_OF_BASE_NAME)
            .fold(
                onSuccess = { it.toMutableList() },
                onFailure = {
                    throw when (it) {
                        is UnexpectedFileSetException -> IncompleteCharacterException()
                        else -> it
                    }
                }
            )

        return try {
            val backgroundImage: ByteArray = s3Service.downloadPersistedFile(
                memberInfo = memberInfo,
                uuid = uuid,
                fileName = FileName.BACKGROUND_IMAGE_FILE_NAME
            )

            val nukkiImage: ByteArray = s3Service.downloadPersistedFile(
                memberInfo = memberInfo,
                uuid = uuid,
                fileName = FileName.LIVE2D_MODEL_NUKKI_FILE_NAME
            )

            val thumbnailImage: ByteArray = mergeImages(backgroundImage, nukkiImage)

            val thumbnailUrl: PersistDTO = s3Service.uploadPersistedFile(
                memberInfo = memberInfo,
                uuid = uuid,
                fileName = FileName.THUMBNAIL_IMAGE_FILE_NAME,
                fileBytes = thumbnailImage
            )

            persistence.add(thumbnailUrl)

            val author: Member = memberService.getEntity(memberInfo.id)
                ?: throw InternalServerException(cause = IllegalStateException("회원을 찾지 못함 -> $memberInfo"))

            val characterBuilder: Character.CharacterBuilder = Character.builder()
                .author(author)
                .persona(Persona(request.persona))
                .name(CharacterName(request.name))
                .voiceId(request.voiceId)
                .isPublic(true)

            persistence.forEach { persistResultDTO ->
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
            dataService.save(characterBuilder.build())
        } catch (e: Exception) {
            //보상 트랜잭션
            CompletableFuture.runAsync {
                persistence.forEach { s3Service.delete(it.url) }
            }

            throw (e)
        }
    }

    @Transactional(readOnly = true)
    fun getCharacter(id: Long): CharacterDTO? {
        val entity = getCharacterEntity(id) ?: return null

        return CharacterDTO.from(entity)
    }

    @Transactional(readOnly = true)
    fun getCharacterList(memberInfo: MemberInfoDTO?, pageable: Pageable): Page<CharacterResponse> {
        return dataService.findAllByIsPublic(pageable)
            .map { CharacterDTO.from(it) }
            .map { CharacterResponse.from(viewerId = memberInfo?.id, characterDTO = it) }
    }

    @Transactional(readOnly = true)
    fun getCharacterForResponse(memberInfo: MemberInfoDTO?, id: Long): CharacterResponse {
        val characterDTO: CharacterDTO = getCharacter(id) ?: throw NoSuchCharacterException()

        return CharacterResponse.from(viewerId = memberInfo?.id, characterDTO = characterDTO)
    }

    @Transactional(readOnly = true)
    internal fun getCharacterEntity(id: Long?): Character? {
        if (id == null) return null

        return dataService.findByIdAndIsPublic(id)
    }

    @Transactional
    fun delete(memberInfo: MemberInfoDTO, id: Long) {
        val authorId = getCharacterEntity(id)?.author?.id ?: throw NoSuchCharacterException()

        if (authorId != memberInfo.id) throw YouAreNotAuthorException()

        dataService.deleteById(id)
    }

    @Transactional
    fun recoverVoiceIdToDefault(characterId: Long): Result<Unit> {
        val character = getCharacterEntity(characterId)
            ?: return Result.failure(NoSuchCharacterException())

        character.changeVoiceId(DEFAULT_VOICE_ID)

        return Result.success(Unit)
    }

    private fun String.toUUID(): UUID = try {
        UUID.fromString(this)
    } catch (_: Exception) {
        throw BadUUIDException()
    }

    @Retryable(
        value = [InternalServerException::class, ServerException::class],
        maxAttempts = 2,
        backoff = Backoff(delay = 500, multiplier = 2.0, random = true)
    )
    private fun getBackgroundImage(
        prompt: String,
        model: GeminiModel = NANO_BANANA_PRO
    ): ByteArray {
        return geminiClient.getImageResponse(prompt, model)
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

private data class ImagePlan(
    val planA: GeminiModel,
    val planB: GeminiModel
)