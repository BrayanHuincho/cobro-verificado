create unique index pagos_recibidos_operacion on pagos_recibidos (comercio_id, billetera, numero_operacion)
    where numero_operacion is not null;
