# (ISEKAI-BE) - 몰입형 AI 캐릭터 채팅 서비스 백엔드

![Project Banner](https://img.shields.io/badge/Project-ISEKAI-purple?style=for-the-badge) 
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.4-brightgreen?style=for-the-badge) 
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-purple?style=for-the-badge) 
![Gemini](https://img.shields.io/badge/Gemini-Live%20Multimodal-orange?style=for-the-badge)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue?style=for-the-badge)

## 📖 프로젝트 소개 (Introduction)

**ISEK-AI**는 사용자가 자신만의 AI 캐릭터를 생성하고, **실시간 음성 대화** 또는 **텍스트 채팅**을 통해 깊은 교감을 나눌 수 있는 **몰입형 AI 캐릭터 채팅 서비스**입니다.

단순한 텍스트 챗봇을 넘어, **Google Gemini Live**의 **Native Audio** 기능을 활용하여 실제 사람과 통화하듯 끊김 없는 대화 경험을 제공하며, 텍스트 기반 채팅도 자연스럽게 지원합니다. 또한, **단기/중기/장기 기억**으로 세분화된 독자적인 메모리 시스템을 통해, 캐릭터가 사용자와의 추억을 영구히 기억하고 대화 문맥에 맞게 활용할 수 있도록 구현했습니다.

### 💡 핵심 가치
-   **Persistent Memory**: 대화가 길어져도 잊지 않는 **기억 통합(Consolidation)** 및 **의미 기반 검색(RAG)** 시스템.
-   **Living Persona**: 사용자가 설정한 페르소나에 완벽하게 몰입하는 **LLM Cascade 아키텍처** (Live for Intent, REST for Reasoning).

---

## ✨ 주요 기능 (Key Features)

### 1. LLM Cascade 아키텍처 (LLM Cascade Architecture)
경량 모델이 의도를 분류하고, 고성능 모델이 실제 응답을 생성하는 **2단계 캐스케이드 구조**를 구현했습니다.
-   **1단계 - Router (Gemini Live)**: 사용자의 음성을 실시간으로 분석하여 **발화 의도(Intent)**를 파악하고, 대화가 필요한 경우에만 다음 단계를 트리거합니다. (Listening Mode)
-   **2단계 - Generator (Gemini REST)**: 대화 맥락, 기억 데이터, 페르소나를 종합하여, 실제 캐릭터의 답변을 생성합니다. (Reasoning Mode)
-   **효율성**: 모든 입력을 고성능 모델로 처리하지 않고, 필요한 경우에만 추론을 수행하여 비용과 지연시간을 최소화합니다.

### 2. 3단계 기억 시스템 (Tri-Layer Memory System)
인간의 기억 메커니즘을 모방하여, 대화의 연속성을 보장합니다.
-   **Short-term**: 현재 세션 내의 **Raw Transcript**를 저장.
-   **Mid-term**: 대화가 일정 횟수(`CONSOLIDATION_COUNT`) 이상 쌓이면, **요약(Summarize)**하여 핵심 내용만 추출.
-   **Long-term**: 요약된 기억을 **임베딩(Embedding)**하여 `pgvector`에 저장하고, 유사한 상황 발생 시 **의미 검색(Semantic Search)**으로 소환.

### 3. 실시간 세션 최적화 (Session Optimization)
-   **Session Optimizer**: 웹소켓 세션의 유휴 상태를 모니터링하고, 30분 이상 비활성 시 리소스를 자동 회수하여 서버 안정성을 확보했습니다.
-   **Backpressure Handling**: `Channel`과 `CompletableDeferred`를 활용하여 음성 스트림의 폭주 트래픽을 제어하고 순서를 보장합니다.

### 4. 하이브리드 비동기 처리 전략 (Hybrid Async Strategy)
-   **코틀린 코루틴 (Kotlin Coroutines)**: 실시간 음성/텍스트 채팅 세션 처리에 코루틴을 적극 활용하여 경량 스레드 기반의 고성능 비동기 처리를 구현했습니다.
-   **가상 스레드 (Virtual Threads)**: RESTful API 엔드포인트와 동기 작업은 Java 21의 가상 스레드를 사용하여 높은 동시성과 효율적인 리소스 활용을 달성했습니다.
-   **적재적소 활용**: 스트리밍 처리는 코루틴의 `Flow`로, 블로킹 I/O는 가상 스레드로 처리하는 하이브리드 접근 방식을 채택했습니다.

### 5. 캐릭터 생성 및 관리 (Character Generation & Management)
-   **AI 기반 Live2D 생성**: 사용자의 텍스트 프롬프트를 기반으로 캐릭터 외형(누끼 이미지)과 배경 이미지를 자동 생성하고, 이를 합성하여 썸네일을 만듭니다.
-   **안전장치 (Safety Guardrails)**: 부적절한 생성 요청을 사전 필터링하며, 이미지 생성 실패 시 대체 모델을 자동으로 시도하는 재시도 메커니즘을 갖추고 있습니다.
-   **트랜잭션 보장**: 캐릭터 확정 중 오류 발생 시, 업로드된 모든 파일을 비동기로 자동 삭제하여 스토리지 누수를 방지합니다.

### 6. 온프레미스 컨테이너 인프라 (On-Premise Container Infrastructure)
-   **Zero 클라우드 비용**: Synology NAS Container Manager 기반으로 전체 백엔드 인프라를 구축하여, DNS 외 클라우드 비용 **0원**을 달성했습니다.
---

## 🏗 시스템 구성 (System Architecture)

### 🛠 기술 스택 (Tech Stack)
| Layer | Technology |
| :--- | :--- |
| **Language** | ![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-purple) ![Java](https://img.shields.io/badge/Java-21-red) |
| **Framework** | **Spring Boot 3.5.4** (Spring MVC), WebFlux (WebClient only), Spring Security, OAuth2 |
| **WebSocket** | **Spring WebSocket** (Jakarta WebSocket API) |
| **AI Protocol** | Google Gemini 2.5 Flash Live (WebSocket), Gemini 3.0 Flash Preview (REST) |
| **Database** | **PostgreSQL 17** (pgvector 0.1.6), **Redis** (Session & Cache) |
| **ORM** | **Spring Data JPA**, Hibernate 6.6 (Vector Support) |
| **Storage** | **SeaweedFS** (S3-compatible API via Spring Cloud AWS 3.4.2) |
| **Async** | **Kotlin Coroutines 1.10.2**, Reactor |
| **Infrastructure** | Gradle, Docker, Nginx |

### 🔍 아키텍처 다이어그램 (Architectural Diagrams)

#### 1. 백엔드 시스템 아키텍처 (Backend System Architecture)
```mermaid
---
config:
  layout: elk
  look: neo
  theme: redux
---
flowchart LR
    subgraph Internet["☁️ Internet Layer"]
        CF["CloudFlare DNS<br>*.isek-ai.org"]
    end
    subgraph VM["🖥️ Virtual Machine<br>nginx.isek-ai.org"]
        Nginx["Nginx Proxy<br>:443 HTTPS"]
    end
    subgraph Docker["🐳 Docker Containers"]
        Backend["Spring Boot App<br>backend.isek-ai.org"]
        Storage["SeaweedFS<br>s3api.isek-ai.org"]
        DB[("PostgreSQL<br>pgvector")]
        Cache[("Redis<br>Cache")]
    end
    subgraph NAS["📦 Synology NAS<br>synology.isek-ai.org"]
        direction TB
        VM
        Docker
    end
    subgraph Home["🏠 Home Network"]
        Router["Home Router"]
        NAS
    end
    CF --> Client["👤 User Client"]
    Client --> Router
    Router --> Nginx
    Nginx --> Backend
    Backend --> Storage & DB & Cache
    Storage -.-> DB

    style CF fill:#9FA8DA
    style Nginx fill:#81C784
    style Backend fill:#4FC3F7
    style Storage fill:#4DD0E1
    style DB fill:#64B5F6
    style Cache fill:#64B5F6
    style VM fill:#C8E6C9
    style Docker fill:#B3E5FC
    style Router fill:#FFE082
    style NAS fill:#F3E5F5
    style Client fill:#FFAB91
    style Internet fill:#E8EAF6
    style Home fill:#FFF3E0
```

#### 2. 프로젝트 아키텍처 (Service Architecture)
```mermaid
graph LR
    FE[프론트 엔드]
    BE[백엔드]
    GeminiLive[Gemini 2.5 Flash<br/>Live Native Audio]
    GeminiRest[Gemini 3.0 Flash<br/>Preview]
    AI1[AI 서버 1<br/>캐릭터 모델]
    AI2[AI 서버 2<br/>TTS]
    
    FE <-->|WS/HTTP| BE
    BE <-->|WS| GeminiLive
    BE <-->|HTTP| GeminiRest
    BE -->|HTTP| AI1
    BE <-->|WS| AI2
    
    style FE fill:#FFB6C1
    style BE fill:#90EE90
    style GeminiLive fill:#87CEEB
    style GeminiRest fill:#87CEEB
    style AI1 fill:#B0C4DE
    style AI2 fill:#B0C4DE
```

### 🔄 데이터 흐름 (Data Flow)
```mermaid
sequenceDiagram
    autonumber
    actor 사용자
    participant 핸들러
    participant 세션서비스
    participant Live as Gemini Live<br/>(라우터)
    participant Rest as Gemini REST<br/>(생성기)
    participant 메모리 as 메모리<br/>(RAG)
    participant TTS as AI 서버<br/>(TTS)

    사용자->>핸들러: 🎤 음성 스트림 (Binary WS)
    핸들러->>세션서비스: 오디오 페이로드 전달
    세션서비스->>Live: 오디오 청크 전송 (WS)
    
    rect rgb(240, 248, 255)
        Note over Live: 의도 분류<br/>(리스닝 모드)
        Live-->>세션서비스: 도구 호출: REQUEST_REPLY
    end

    세션서비스->>메모리: 🔍 컨텍스트 조회
    Note over 메모리: 단기 (최근 대화)<br/>중기 (요약)<br/>장기 (벡터 검색)
    메모리-->>세션서비스: 컨텍스트 데이터

    세션서비스->>Rest: 🧠 응답 생성 요청 (HTTP)
    Note over Rest: 컨텍스트 + 페르소나 활용<br/>(추론 모드)
    Rest-->>세션서비스: 텍스트 응답

    par 이중 출력 생성
        세션서비스->>TTS: 🗣️ TTS 요청 (WS)
        TTS-->>세션서비스: 🔊 오디오 스트림
        세션서비스->>핸들러: 오디오 + 텍스트 자막
        핸들러->>사용자: 🔊 음성 + 💬 텍스트 (WS)
    end
    
    세션서비스->>메모리: 비동기 저장 및 통합
    Note over 메모리: 횟수 임계값 도달 시 트리거
```

### 💾 데이터베이스 설계 (ER Diagram)
```mermaid
erDiagram
    MEMBER ||--o{ CHARACTER : "creates (author)"
    MEMBER ||--o{ CHAT : "hosts (hostMember)"
    MEMBER ||--o{ CONSOLIDATED_MEMORY : "owns (hostMember)"
    CHARACTER ||--o{ CHAT : "participates"
    CHARACTER ||--o{ CONSOLIDATED_MEMORY : "remembers"
    
    MEMBER {
        bigint id PK
        varchar email UK
        varchar emailHash UK
        varchar nickname UK
        enum provider "KAKAO"
        enum role "USER"
        timestamp created_at
        timestamp updated_at
    }
    
    CHARACTER {
        bigint id PK
        bigint author_id FK
        varchar character_name
        text persona
        varchar live2dModelUrl
        varchar backgroundUrl
        varchar live2dModelNukkiUrl
        varchar thumbnailUrl
        bigint voiceId
        boolean isPublic
        timestamp created_at
        timestamp updated_at
    }
    
    CHAT {
        bigint id PK
        bigint host_member_id FK
        bigint character_id FK
        text content
        enum speaker "USER | BOT"
        timestamp created_at
        timestamp updated_at
    }
    
    CONSOLIDATED_MEMORY {
        bigint id PK
        bigint host_member_id FK
        bigint character_id FK
        text summary
        vector embedding "float[768]"
        timestamp created_at
        timestamp updated_at
    }
```

### 🧩 웹소켓 클래스 구조 (WebSocket Class Diagram)
```mermaid
classDiagram
direction TB
    class IsekAiSessionHandler {
        -IsekAiSessionService service
        -ObjectMapper mapper
        +afterConnectionEstablished()
        +handleBinaryMessage()
        +handleTextMessage()
    }
    
    class IsekAiSessionService {
        -GeminiLiveClient liveClient
        -GeminiRestClient restClient
        -ChatMemoryService memoryService
        -AiServerTTSService aiServerTTSService
        +processInputStream()
        -routeGeminiLiveOutput()
        -routeRestFunctionCall()
        -routeTTSOutput()
    }
    
    class ChatMemoryService {
        -ChatDataService chatService
        -GeminiRestClient geminiRestClient
        -ConsolidatedMemoryDataService consolidatedMemoryService
        +save()
        +consolidate()
        +getShortTermMemory()
        +getMidTermMemory()
        +getLongTermMemory()
    }
    
    class GeminiLiveClient {
        -GeminiConfig geminiConfig
        -ObjectMapper mapper
        +getLiveResponse()
        -buildConfig()
    }

    class GeminiRestClient {
        -GeminiConfig geminiConfig
        +getTextDialogResponse()
        +getEmbeddingResponse()
        +generateImageAsync()
    }

    class TTSClient {
        -AiServerConfig config
        -AiServerWebSocketHandlerFactory handlerFactory
        +tts()
    }
    
    IsekAiSessionHandler --> IsekAiSessionService : uses
    IsekAiSessionService --> GeminiLiveClient : "Router (WS)"
    IsekAiSessionService --> GeminiRestClient : "Generator (HTTP)"
    IsekAiSessionService --> ChatMemoryService : "RAG & Save"
    IsekAiSessionService --> TTSClient : "Speech Gen (WS)"
    ChatMemoryService --> GeminiRestClient : "Summarize & Embed"
```

---

## 🚀 시작하기 (Getting Started)

### 사전 요구사항
*   JDK 21 이상
*   Google AI Studio API Key
*   Docker (PostgreSQL & Redis 실행용)

### 1. 프로젝트 클론
```bash
git clone https://github.com/rdme0/isek-ai-be.git
cd isek-ai-be
```

### 2. 환경 변수 설정
`.env` 파일을 생성하고 다음 정보를 입력하세요.
```properties
# Application
JWT_SECRET_KEY=
AES256_KEY=

# Gemini API
GEMINI_API_KEY=

# AI Servers
AI_SERVER_WEBSOCKET_URL=
AI_SERVER_REST_URL=

# OAuth2 (Kakao Login)
KAKAO_CLIENT_ID=
KAKAO_CLIENT_SECRET=

# Cloud Storage (SeaweedFS S3 API)
CLOUD_STORAGE_HOST=
CLOUD_STORAGE_PUBLIC_URL=
CLOUD_STORAGE_PORT=443
CLOUD_STORAGE_BUCKET_NAME=
CLOUD_STORAGE_TEMP_BUCKET_NAME=
CLOUD_STORAGE_REGION=
CLOUD_STORAGE_USER=
CLOUD_STORAGE_PASSWORD=
CLOUD_STORAGE_ACCESS_KEY=
CLOUD_STORAGE_SECRET_KEY=

# Development
DEV_URL=

# Production (Optional)
PROD_URL=
PROD_POSTGRES_URL=
PROD_POSTGRES_PORT=
PROD_POSTGRES_USERNAME=
PROD_POSTGRES_PASSWORD=
PROD_REDIS_URL=
PROD_REDIS_PORT=
PROD_REDIS_PASSWORD=
PROD_REDIS_DATABASE=
```


### 3. 실행
```bash
./gradlew bootRun
```

---

## ⚠️ 한계점 (Limitations)
1.  **제3자 대화 구분 불가**: 현재 Gemini Live는 주변의 제3자 간 대화와 사용자가 AI에게 직접 말하는 것을 구분하지 못합니다. 따라서 제3자 대화에도 응답하거나, 반대로 응답하지 않을 수 있습니다.
    - **하드웨어 해결책**: 지향성 마이크를 사용하여 사용자 음성만 입력받도록 구성
    - **소프트웨어 해결책**: Gemini 3.0 Flash Native Audio 모델 출시 또는 향후 Gemini의 화자 감지(Speaker Diarization) 기능 개선을 기대
2.  **음성 커스터마이징 미지원**: TTS 서버에서 제공하는 사전 정의된 음성만 사용 가능합니다. 캐릭터 생성 시 텍스트 프롬프트로 외형을 생성하듯이, 자연어 설명으로 음성을 커스터마이징하는 기능도 기술적으로 구현 가능합니다. 필요하다면 TTS 서버 저장소를 포크하여 직접 구현해보세요.
