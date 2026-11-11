create table comercios (
    id uuid primary key,
    nombre text not null,
    destinatarios text[] not null default '{}',
    celulares text[] not null default '{}',
    ventana_minutos integer not null default 1440 check (ventana_minutos > 0),
    tolerancia_minutos integer not null default 10 check (tolerancia_minutos >= 0),
    umbral_revision_humana numeric(12, 2),
    url_webhook text,
    secreto_webhook text,
    creado_en timestamptz not null default now()
);

create table claves_api (
    id uuid primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    hash_clave char(64) not null unique,
    creada_en timestamptz not null default now(),
    revocada_en timestamptz
);

create table dispositivos (
    id uuid primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    hash_token char(64) not null unique,
    nombre text,
    vinculado_en timestamptz not null default now(),
    revocado_en timestamptz
);

create table codigos_vinculacion (
    hash_codigo char(64) primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    expira_en timestamptz not null,
    usado_en timestamptz
);

create table pagos_recibidos (
    id uuid primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    billetera text not null check (billetera in ('yape', 'plin')),
    monto numeric(12, 2) not null check (monto > 0),
    fecha_hora timestamptz not null,
    numero_operacion text,
    remitente text,
    huella char(64) not null,
    origen text not null check (origen in ('notificacion', 'manual')),
    recibido_en timestamptz not null default now(),
    unique (comercio_id, huella)
);

create index pagos_recibidos_busqueda on pagos_recibidos (comercio_id, billetera, monto, fecha_hora);

create table verificaciones (
    id uuid primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    estado text not null check (estado in ('procesando', 'confirmado', 'pendiente', 'sospechoso', 'rechazado')),
    creada_en timestamptz not null default now(),
    monto_esperado numeric(12, 2),
    referencia text,
    clave_idempotencia text,
    datos jsonb,
    veredicto jsonb,
    revisada boolean not null default false,
    nota_revision text,
    completada_en timestamptz,
    unique (comercio_id, clave_idempotencia)
);

create index verificaciones_comercio on verificaciones (comercio_id, creada_en desc);

create table operaciones_usadas (
    comercio_id uuid not null references comercios (id) on delete cascade,
    billetera text not null,
    numero_operacion text not null,
    verificacion_id uuid not null references verificaciones (id) on delete cascade,
    usada_en timestamptz not null default now(),
    primary key (comercio_id, billetera, numero_operacion)
);

create table trabajos (
    id bigint generated always as identity primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    verificacion_id uuid not null references verificaciones (id) on delete cascade,
    ruta_imagen text not null,
    estado text not null default 'pendiente' check (estado in ('pendiente', 'en_curso', 'terminado', 'fallido')),
    intentos integer not null default 0,
    max_intentos integer not null default 5,
    disponible_en timestamptz not null default now(),
    bloqueado_en timestamptz,
    bloqueado_por text,
    ultimo_error text,
    creado_en timestamptz not null default now(),
    terminado_en timestamptz
);

create index trabajos_disponibles on trabajos (disponible_en) where estado = 'pendiente';

create table entregas_webhook (
    id bigint generated always as identity primary key,
    comercio_id uuid not null references comercios (id) on delete cascade,
    verificacion_id uuid not null references verificaciones (id) on delete cascade,
    url text not null,
    cuerpo jsonb not null,
    estado text not null default 'pendiente' check (estado in ('pendiente', 'en_curso', 'entregada', 'fallida')),
    intentos integer not null default 0,
    max_intentos integer not null default 8,
    disponible_en timestamptz not null default now(),
    bloqueado_en timestamptz,
    ultimo_codigo integer,
    ultimo_error text,
    creada_en timestamptz not null default now(),
    entregada_en timestamptz
);

create index entregas_disponibles on entregas_webhook (disponible_en) where estado = 'pendiente';
