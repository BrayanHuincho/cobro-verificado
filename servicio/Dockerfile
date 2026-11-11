FROM python:3.12-slim AS construccion
COPY --from=ghcr.io/astral-sh/uv:0.9.5 /uv /usr/local/bin/uv
ENV UV_COMPILE_BYTECODE=1 UV_LINK_MODE=copy UV_PYTHON_DOWNLOADS=never UV_NO_CACHE=1
WORKDIR /app
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project
COPY src ./src
RUN uv sync --frozen --no-dev --no-editable \
    && find .venv -depth -type d \( -name tests -o -name test \) -path '*/site-packages/*/*' -prune -exec rm -rf {} + \
    && find .venv -name '*.pyi' -delete

FROM python:3.12-slim
# opencv headless trae sus bibliotecas en la rueda, no hacen falta paquetes del sistema
RUN useradd --system --uid 10001 --create-home cobroverificado \
    && mkdir -p /var/lib/cobroverificado/imagenes \
    && chown cobroverificado /var/lib/cobroverificado/imagenes
COPY --from=construccion /app/.venv /app/.venv
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    COBROVERIFICADO_DIRECTORIO_IMAGENES=/var/lib/cobroverificado/imagenes \
    COBROVERIFICADO_ARCHIVO_LATIDO=/tmp/cobroverificado-latido
USER cobroverificado
WORKDIR /app
EXPOSE 8000
CMD ["uvicorn", "--factory", "cobroverificado.principal:create_app", "--host", "0.0.0.0", "--port", "8000"]