# VidGravity container runtime scaffold. Put behind an HTTPS reverse proxy.
FROM python:3.12-slim
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl unzip \
    && curl -fsSL https://deno.land/install.sh | DENO_INSTALL=/usr/local sh \
    && test -x /usr/local/bin/deno \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
RUN useradd --system --uid 10001 --home-dir /nonexistent --shell /usr/sbin/nologin vidgravity \
    && chown -R vidgravity:vidgravity /app \
    && mkdir -p /data \
    && chown vidgravity:vidgravity /data
USER vidgravity
EXPOSE 5173
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:5173/api/health', timeout=3).read()" || exit 1
CMD ["python", "server.py", "--host", "0.0.0.0", "--port", "5173"]
