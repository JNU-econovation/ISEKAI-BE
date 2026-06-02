# Maintenance Playbook

## General Editing Rules

- Prefer the current package style over introducing new layers.
- Keep controllers thin: parse annotations and delegate to services.
- Keep domain orchestration in services.
- Keep repositories as Spring Data interfaces; use explicit queries for pgvector and paging behavior.
- Add validation at request DTO boundaries where possible; map domain failures through typed exceptions and `ErrorCode`.
- Keep logs useful but do not log secrets, JWTs, OAuth tokens, presigned URLs, or raw long audio payloads.

## Kotlin And Java Style

- Kotlin services use constructor injection and `@Service`, `@Component`, or `@Configuration`.
- Coroutines are already central to streaming paths; preserve cancellation semantics and avoid blocking calls inside hot stream collectors unless the existing client is blocking by design.
- Java entity/value-object classes use JPA annotations and Lombok builders. Keep protected/no-arg constructor requirements intact.
- Prefer domain-specific exceptions under the relevant package when adding user-visible failures.

## WebSocket And Session Changes

When changing voice session behavior, read these together:

- `common/websocket/service/WebSocketTicketService.kt`
- `common/websocket/interceptor/WebSocketHandshakeInterceptor.kt`
- `common/websocket/config/WebSocketConfig.kt`
- `session/handler/IsekAiSessionHandler.kt`
- `session/service/IsekAiSessionService.kt`
- `session/dto/request/SessionRequest.kt`
- `session/dto/response/SessionResponse.kt`
- `session/constant/SessionConstant.kt`

Risk checks:

- Ticket validation is one-time and Redis-backed.
- `memberId` and `characterId` are expected in session attributes before the handler starts processing.
- `SessionOptimizer` lifecycle must stop on close.
- `clientStream` backpressure uses `BufferOverflow.SUSPEND`; avoid unbounded channels for audio.
- `TURN_COMPLETE` is currently sent when the first TTS packet starts, not immediately after REST text generation.
- Cancelling an active answer should emit `INTERRUPTED`.

## Gemini Contract Changes

When changing model names, function signatures, schemas, or response parsing, read these together:

- `gemini/client/GeminiLiveClient.kt`
- `gemini/client/GeminiRestClient.kt`
- `gemini/client/processor/GeminiMessageProcessor.kt`
- `gemini/constant/function/GeminiFunctions.kt`
- `gemini/constant/schema/GeminiSchema.kt`
- `gemini/constant/enums/GeminiModel.kt`
- `gemini/constant/enums/GeminiLiveFunctionSignature.kt`
- `gemini/constant/enums/GeminiRestFunctionSignature.kt`
- `gemini/dto/client/request`
- `gemini/dto/client/response`
- `resources/prompts/*.md`

Risk checks:

- Live audio MIME type is `audio/pcm;rate=16000`.
- Live function call `REQUEST_REPLY` must receive an OK tool response.
- REST tool mode expects function calls; if no valid function call is produced, the code throws `InternalServerException`.
- Embedding dimensionality is 768 and must match pgvector storage.
- Model identifiers can become stale; verify official provider docs before changing them.

## Chat Memory And pgvector Changes

When changing memory behavior, read these together:

- `chat/service/ChatMemoryService.kt`
- `chat/service/internal/ChatDataService.kt`
- `chat/service/internal/ConsolidatedMemoryDataService.kt`
- `chat/repository/ChatRepository.kt`
- `chat/repository/ConsolidatedMemoryRepository.kt`
- `chat/model/entity/Chat.java`
- `chat/model/entity/ConsolidatedMemory.java`
- `chat/constant/ChatConstants.kt`

Risk checks:

- Each turn saves two chat rows: `USER` and `BOT`.
- Redis counter key is currently `${hostMember.id}:chatCounter`; changing character-specific behavior requires a deliberate key design.
- Consolidation resets the counter to 0 on success and also on caught consolidation failure.
- Long-term memory search uses `embedding <-> CAST(:embedding AS vector)` in a native query.
- Changes to embedding size require database schema and query compatibility checks.

## Character And Storage Changes

When changing character generation or file lifecycle, read these together:

- `character/controller/CharacterController.kt`
- `character/service/CharacterCoordinateService.kt`
- `character/service/internal/CharacterDataService.kt`
- `character/model/entity/Character.java`
- `character/model/vo`
- `common/s3/service/S3Service.kt`
- `common/s3/enums/FileName.kt`
- `common/s3/config/CloudStorageProperties.kt`
- `aiServer/client/CharacterGenerateClient.kt`

Risk checks:

- Preview files are copied to persisted storage during confirmation.
- Required preview files are Live2D model, nukki image, and background image.
- Thumbnail generation downloads persisted background and nukki files, merges into 1024x1024 PNG, then uploads persisted thumbnail.
- On confirm failure after copies, already persisted files are deleted asynchronously as compensation.
- Public persisted URLs are built from `cloud-storage.public-url`, bucket, and key; keep this aligned with storage gateway routing.

## Security Changes

When changing auth, CORS, OAuth2, redirects, or public endpoints, read these together:

- `common/security/config/SecurityConfig.kt`
- `common/security/config/UriSecurityConfig.kt`
- `common/security/filter/JwtAuthFilter.kt`
- `common/security/filter/RedirectParamFilter.kt`
- `common/security/oauth`
- `common/security/util/JwtUtil.kt`
- `resources/config/application-security.yml`

Risk checks:

- `allowedOriginPatterns` comes from `uri.allowed-front-end-origins`.
- Security currently permits `/characters/**`, `/me/**`, `/websocket/**`, and OAuth paths in the filter chain; verify whether method-level behavior still enforces intended auth before relying on it.
- Redirect validation must reject unapproved origins.
- Do not expose secrets in error messages or logs.

## Config Changes

- Put app-wide defaults in `application.yml`.
- Put profile-specific datasource, Redis, and OAuth redirect settings in `application-dev.yml` or `application-prod.yml`.
- Put grouped external-service config in `src/main/resources/config/*.yml`.
- Bind new grouped config with `@ConfigurationProperties` and add it to `@EnableConfigurationProperties` in `Application.kt`.
- Update README through `$readme-maintainer` when setup variables change.

## Testing Guidance

- For build or dependency edits, run `.\gradlew.bat compileKotlin compileJava` or `.\gradlew.bat test`.
- For controller/service logic, prefer focused unit or slice tests if practical.
- For WebSocket/session behavior, use the existing e2e test files as design references, but inspect whether they are commented out or require real external services before relying on them.
- For code that calls Gemini, AI server, S3, Redis, or PostgreSQL, mock external clients unless the user explicitly asks for live integration validation and provides environment readiness.

## Common Failure Surfaces

- Gradle dependency syntax errors.
- Missing environment variables loaded through spring-dotenv.
- Redis unavailable for WebSocket tickets or memory counters.
- PostgreSQL pgvector not installed or embedding dimensionality mismatch.
- Gemini function-call schema mismatch.
- AI server WebSocket cold-start or timeout.
- S3-compatible storage ACL/public URL mismatch.
- CORS or redirect origin drift between frontend deployments and `application-security.yml`.
