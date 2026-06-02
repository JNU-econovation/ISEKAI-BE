---
name: isekai-maintainer
description: Maintain the ISEKAI-BE backend repository. Use when Codex needs to implement or review backend changes, fix bugs, update configuration, adjust WebSocket or Gemini flows, modify character/chat/member/storage/security domains, run repository validation, or explain this Kotlin/Spring Boot codebase.
---

# ISEKAI Maintainer

Use this skill for general maintenance of the ISEKAI-BE backend.

## Bootstrap

1. Read repository root `AGENTS.md`.
2. Check worktree state with `git status --short`; preserve unrelated user changes.
3. Inspect the affected domain before editing. Prefer code over README when they disagree.
4. Load references only as needed:
   - [architecture-map.md](references/architecture-map.md) for package layout, runtime flow, APIs, and configuration.
   - [maintenance-playbook.md](references/maintenance-playbook.md) for edit patterns, risks, and validation choices.

## Project Defaults

- Treat the stack as Spring Boot 3.5, Kotlin 2.2, Java 21, Gradle, Spring MVC, Spring WebSocket, Spring Security OAuth2/JWT, JPA, PostgreSQL/pgvector, Redis, S3-compatible storage, and Google Gemini.
- Keep Kotlin code idiomatic and consistent with existing service/controller/repository layering.
- Keep Java entity/value-object code compatible with the current JPA/Lombok builder style.
- Keep user-facing API messages and README prose in Korean unless the user asks otherwise.
- Add new environment-backed settings through `@ConfigurationProperties` and the imported YAML files under `src/main/resources/config/`.

## Maintenance Workflow

1. Identify the domain boundary first:
   - session/WebSocket voice flow
   - Gemini Live/REST integration
   - chat memory and pgvector RAG
   - character generation and S3 persistence
   - member/auth/OAuth/JWT/security
   - common response, exception, pageable, or config infrastructure
2. Read the domain's controller or handler, service, DTOs, repository/entity, constants, and configuration together.
3. Keep edits scoped to the current request. Do not opportunistically refactor adjacent code.
4. Preserve public contracts unless the request is explicitly a contract change. When changing a contract, update DTOs, response factories, handlers, tests, and README if relevant.
5. Validate with the narrowest useful Gradle command, then broaden if the change touches shared infrastructure.

## Validation

Prefer these commands from the repository root:

```powershell
.\gradlew.bat test
.\gradlew.bat compileKotlin compileJava
.\gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

On non-Windows shells, use `./gradlew`.

Before trusting validation, inspect `build.gradle` if Gradle fails early. This repository's build file is a common high-impact edit point, so dependency syntax errors can mask unrelated work.

## Coordination

- Use `$readme-maintainer` when a change needs README updates.
- Use `$git-commit-korean` when the user asks for commit messages or actual commits.
- Update root `AGENTS.md` whenever adding, renaming, or removing repository-local skills.
