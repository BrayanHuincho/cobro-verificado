create table respuestas_idempotentes (
    clave text primary key,
    huella_peticion char(64) not null,
    estado integer not null,
    cuerpo bytea not null,
    guardada_en timestamptz not null default now()
);

create index respuestas_idempotentes_antiguedad on respuestas_idempotentes (guardada_en);
