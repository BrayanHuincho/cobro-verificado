# Cómo contribuir

Gracias por querer mejorar Cobro Verificado.

## Antes de empezar

- Si es un error, abre un issue con los pasos para reproducirlo.
- Si es una idea grande, abre primero un issue para conversarla, así no trabajas en algo que luego no encaja.
- Nunca subas capturas o notificaciones reales con datos de personas. Tapa nombres, montos y números de operación.

## Flujo de trabajo

`main` siempre debe estar estable. Todo cambio entra por pull request.

1. Haz un fork y crea una rama desde `main` con un nombre claro: `arreglo/fecha-plin`, `mejora/reporte-pdf`.
2. Haz commits pequeños con mensajes en presente: "Agrega filtro por monto".
3. Comprueba que todo pase antes de abrir el pull request.
4. Abre el pull request contra `main` y explica qué cambia y cómo lo probaste.

## Comprobaciones

Servicio:

```bash
cd servicio
uv sync
uv run pytest
uv run ruff check
uv run ruff format --check
uv run mypy --strict src
```

Las pruebas de base de datos necesitan un Postgres local:

```bash
docker run -d --name cv_pruebas -p 127.0.0.1:55432:5432 -e POSTGRES_PASSWORD=prueba postgres:17-alpine
```

App Android:

```bash
cd android
./gradlew testDebugUnitTest assembleDebug
```

El CI corre lo mismo en cada pull request.

## Estilo

- Nombres en español para el dominio.
- Las reglas de decisión van en `servicio/src/cobroverificado/dominio/` y no dependen de la base de datos ni de la web.
- Un adaptador nunca importa a otro: hay un test que lo comprueba.
- Comentarios solo cuando el porqué no sea obvio.
- Dinero siempre con `Decimal` en Python y `BigDecimal` en Kotlin.
