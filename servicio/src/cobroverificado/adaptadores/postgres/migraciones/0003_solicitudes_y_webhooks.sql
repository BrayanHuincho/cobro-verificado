alter table verificaciones add column huella_peticion char(64);

alter table comercios drop column secreto_webhook;
alter table comercios add column secreto_webhook_cifrado text;

drop table respuestas_idempotentes;
