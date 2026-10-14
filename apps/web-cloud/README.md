# VidGravity Cloud AI API

This is a new, separate Node.js/TypeScript service. It is not yet wired into the existing Python companion runtime or deployed automatically.

## Required environment

- `PORT` (default `8787`)
- `VIDGRAVITY_PWA_ORIGINS`: comma-separated exact allowed PWA origins
- `VIDGRAVITY_AI_PROVIDER_URL`: HTTPS base URL for a compatible generative-media gateway
- `VIDGRAVITY_AI_PROVIDER_TOKEN`: server-side credential; never place it in a PWA environment variable
- `VIDGRAVITY_AI_GATEWAY_SECRET`: required in production; the trusted edge/API gateway must inject this as `x-vidgravity-ai-gateway`. The browser must never receive the secret.

Production AI routes fail closed if gateway authentication is not configured. CORS is not authentication. Add per-user authorization, rate limits, quotas, billing/consent checks, and durable task storage before exposing this service publicly.

## Provider adapter contract

The configured gateway must accept multipart `POST /v1/tasks` with `kind`, `metadata`, and named input files. It returns JSON with `id` or `taskId`. The API polls `GET /v1/tasks/:id` and expects `status`, optional `progress`, `message`, `error`, and a final `resultUrl` or `outputUrl`. Result URLs must share the configured provider origin and return a video MIME type. Provider progress is relayed to PWA clients via SSE.

This is an adapter contract, not a claim that Kling, Viggle, or any other provider already implements this exact API. A provider-specific adapter and valid credentials are required for real generations.

## Routes

- `POST /api/ai/inpaint`: `video`, `mask`, `frame`, `timeline`
- `POST /api/ai/faceswap`: `video`, `reference`, `timeline`
- `POST /api/ai/motion`: `character`, `choreography`, `options`
- `GET /api/ai/tasks/:id/events`: SSE progress
- `GET /api/ai/tasks/:id/result`: completed MP4 result

Tasks and result files are currently held in process memory and ephemeral disk for up to two hours. This is suitable for integration testing only; production needs a durable queue/object store and multi-instance shared state.
