# Architecture Map

Use this as the quick map for ISEKAI-BE maintenance.

## Runtime Shape

- Application entry: `src/main/kotlin/jnu/econovation/isekai/Application.kt`
- Default profile: `dev`
- Server port: `18081`
- Config imports:
  - `config/application-security.yml`
  - `config/application-gemini.yml`
  - `config/application-prompt.yml`
  - `config/application-cloud-storage.yml`

## Build And Dependencies

- Gradle project name: `isek-ai`
- Java toolchain: 21
- Kotlin: 2.2.0
- Spring Boot: 3.5.4
- Important dependencies:
  - Spring Web, WebFlux, WebSocket, Validation, Security, OAuth2 Client
  - Spring Data Redis
  - JPA/PostgreSQL/pgvector/Hibernate vector
  - Kotlin coroutines
  - Google GenAI Java SDK
  - Spring Cloud AWS S3
  - spring-dotenv

## Domains

- `session`: real-time WebSocket session orchestration.
  - Handler: `session/handler/IsekAiSessionHandler.kt`
  - Service: `session/service/IsekAiSessionService.kt`
  - Optimizer: `session/optimizer/SessionOptimizer.kt`
  - Request/response DTOs: `session/dto/request`, `session/dto/response`
- `gemini`: Gemini Live/REST clients, schemas, models, functions, and output processors.
  - Live client streams 16 kHz PCM audio and receives STT/function calls.
  - REST client handles dialog, RAG function calls, summaries, embeddings, and image generation.
- `chat`: chat history and memory consolidation.
  - `ChatMemoryService` saves user/bot chat pairs, increments Redis counters, summarizes every `CONSOLIDATION_COUNT`, stores embeddings, and retrieves short/mid/long-term memory.
  - `ConsolidatedMemoryRepository` uses native pgvector distance ordering.
- `character`: character generation, background generation, confirmation, thumbnail merge, listing, retrieval, deletion, and voice recovery.
  - `CharacterCoordinateService` coordinates AI server, Gemini image generation, S3, member lookup, and persistence.
- `aiServer`: external AI server REST client for Live2D generation and WebSocket client for TTS.
- `common/security`: OAuth2 login, JWT auth, redirect validation, CORS, and security filter chain.
- `common/websocket`: ticket issuance and handshake validation for voice sessions.
- `common/s3`: S3-compatible storage config and file lifecycle.
- `member`: member lookup, initialization, OAuth profile mapping.
- `common/exception`: typed error codes and REST exception mapping.

## HTTP APIs

- `POST /characters/live2d`: request Live2D generation and receive preview URL.
- `POST /characters/background-image`: generate and upload background image.
- `POST /characters/confirm`: persist preview assets and create character.
- `GET /characters`: public character list; pageable sort currently allows `CREATED_AT`.
- `GET /characters/{id}`: character details.
- `DELETE /characters/{id}`: author-only delete.
- `GET /characters/{characterId}/chats`: authenticated chat history.
- `GET /me`: current member summary.
- `POST /websocket/ticket`: one-time Redis ticket for voice WebSocket.

## WebSocket Flow

- Endpoint: `/characters/{characterId}/voice?ticket=...`
- Handshake:
  - `WebSocketTicketService` stores one-time ticket in Redis for 60 seconds.
  - `WebSocketHandshakeInterceptor` consumes the ticket, resolves `memberId`, parses `characterId`, and stores both in session attributes.
- Session:
  - `IsekAiSessionHandler` wraps the session with `ConcurrentWebSocketSessionDecorator`, creates a coroutine scope, creates a bounded input `Channel`, starts `SessionOptimizer`, and delegates to `IsekAiSessionService`.
  - Binary messages are raw audio chunks.
  - Text messages deserialize to `SessionTextRequest`.
- Output message types:
  - `SERVER_READY`
  - `USER_SUBTITLE_CHUNK`
  - `USER_SUBTITLE_COMPLETE`
  - `BOT_IS_THINKING`
  - `INTERRUPTED`
  - `TURN_COMPLETE`
  - `EMOTION`
  - `ERROR`
  - `BOT_SUBTITLE` exists in the enum but is not currently produced by the factory methods.

## Gemini And Memory Flow

1. Gemini Live receives real-time audio/text plus initial memory context.
2. Gemini Live emits STT chunks and calls `REQUEST_REPLY` when a response is needed.
3. Service sends thinking and final user subtitle messages.
4. Gemini REST generates a response, optionally calling `SEARCH_LONG_TERM_MEMORY_RAG`.
5. Service sends emotion, streams sentence chunks to TTS, sends `TURN_COMPLETE` at first TTS packet, and asynchronously saves chat history.
6. Chat memory stores user/bot rows and consolidates after 30 saved turns using Gemini summary and embedding models.

## Configuration And Environment

Core environment variables used by YAML:

- `JWT_SECRET_KEY`
- `AES256_KEY`
- `GEMINI_API_KEY`
- `AI_SERVER_WEBSOCKET_URL`
- `AI_SERVER_REST_URL`
- `KAKAO_CLIENT_ID`
- `KAKAO_CLIENT_SECRET`
- `CLOUD_STORAGE_HOST`
- `CLOUD_STORAGE_PUBLIC_URL`
- `CLOUD_STORAGE_PORT`
- `CLOUD_STORAGE_BUCKET_NAME`
- `CLOUD_STORAGE_REGION`
- `CLOUD_STORAGE_ACCESS_KEY`
- `CLOUD_STORAGE_SECRET_KEY`
- `DEV_URL`
- `PROD_URL`
- `PROD_POSTGRES_URL`
- `PROD_POSTGRES_PORT`
- `PROD_POSTGRES_USERNAME`
- `PROD_POSTGRES_PASSWORD`
- `PROD_REDIS_URL`
- `PROD_REDIS_PORT`
- `PROD_REDIS_PASSWORD`
- `PROD_REDIS_DATABASE`

`README.md` may mention additional or older variables. Verify against YAML and configuration properties before documenting.
