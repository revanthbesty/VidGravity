# VidGravity AI Studio PWA

This is a new Next.js App Router application under `apps/pwa`. It is currently a separate entry point and is not yet mounted into the existing legacy `site/` PWA deployment.

## Local development

```sh
npm install
npm run dev
```

Set `NEXT_PUBLIC_VIDGRAVITY_AI_API` to the **origin** of the separately deployed AI API (for local development, `http://localhost:8787`). This variable is public and must never contain a secret. The backend's `VIDGRAVITY_AI_GATEWAY_SECRET` belongs only at the trusted server-side gateway.

## Modules

- Magic Eraser uses FFmpeg.wasm to extract a frame and submits the video, PNG mask, and frame to the provider adapter.
- Face-Off uses MediaPipe Tasks Vision in the browser to create face bounding-box timeline samples.
- Motion Transfer accepts the character image and choreography video.
- AI tasks report progress through SSE; results are saved to OPFS where supported and offered through Web Share API or a download fallback.

## Current release boundary

This PWA is a functional integration shell, not a self-contained generative model. Real inpainting, face swap, and motion transfer require a provider-specific adapter and valid server-side model credentials. The cloud service currently stores task state in memory and result files on ephemeral disk, so deploy it only behind an authenticated trusted gateway for integration testing. Durable job storage, object storage, per-user authorization, rate limiting/quotas, provider-specific adapter tests, privacy/consent review, and production monitoring remain release gates.
