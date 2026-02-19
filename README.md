# Pagos Digitales - SafetyPay Confirmation

## Documentación técnica

- Arquitectura y operación: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Manual técnico: [`docs/MANUAL_TECNICO.md`](docs/MANUAL_TECNICO.md)
- Manual de usuario: [`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md)
- ADR de arquitectura: [`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md)
- Especificación OpenAPI: [`docs/openapi.yaml`](docs/openapi.yaml)

## Configuracion global del proyecto (application.yaml)

```yaml
server:
  port: ${SERVER_PORT:8080}

cod_GEO_FYB: ${COD_CADENA_GEO_FYB:60}
cod_GEO_SANA: ${COD_CADENA_GEO_SANA:70}
cod_GEO_OKI: ${COD_CADENA_GEO_OKI:90}
cod_GEO_FR: ${COD_CADENA_GEO_FR:70}

spring:
  application:
    name: pagosDigitales
  datasource:
    url: jdbc:oracle:thin:@localhost:1521/XEPDB1
    username: GPF
    password: GPF123
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    show-sql: false
    hibernate:
      ddl-auto: none

camel:
  springboot:
    main-run-controller: true

providers:
  dynamic-routes-enabled: true
```

Notas:
- Ajusta `spring.datasource.*` segun el entorno.
- Los proveedores de pago se leen desde `TUKUNAFUNC.AD_BILLETERAS_DIGITALES` (`CODIGO`, `NOMBRE_BILLETERA_DIGITAL`, `ACTIVA='S'`).
- La validacion principal de bancos usa `AD_TIPO_PAGO` por cadena (`CADENA_FYB`, `CADENA_SANA`, `CADENA_OKI`, `CADENA_FR`).
- La validacion por canal es un filtro adicional usando `AD_CANAL`, `AD_CANAL_TIPO_PAGO` y `AD_TIPO_PAGO`.
- Las tablas `AD_BILLETERAS_DIGITALES`, `AD_CANAL`, `AD_CANAL_TIPO_PAGO` y `AD_TIPO_PAGO` se cargan en cache en memoria.
- El refresco de cache se ejecuta al arranque y luego cada 6 horas (`00:00`, `06:00`, `12:00`, `18:00` del servidor).

## Despliegue con Docker (puerto 8080)

1. Construye la imagen:

```bash
docker build -t pagosdigitales:local .
```


2. Ejecuta el contenedor en el puerto 8080 con nombre fijo:

```bash
docker run --rm --name pagosdigitales -e SERVER_PORT=8080 -p 8080:8080 pagosdigitales:local
```

3. Mantener despliegue fijo en 8080:

```bash
docker run --rm --name pagosdigitales -e SERVER_PORT=8080 -p 8080:8080 pagosdigitales:local
```

## Endpoint

- Metodo: POST
- Ruta: `/api/v1/safetypay/confirmation`
- Content-Type: `application/x-www-form-urlencoded`
- Respuesta: `text/plain` con una linea CSV

## Endpoints disponibles

- `POST /api/v1/safetypay/confirmation`: webhook de confirmaciones SafetyPay (form-urlencoded, respuesta CSV firmada).
- `POST /api/v1/direct-online-payment-requests`: crea pagos en linea con proveedor.
- `POST /api/v1/payments/notifications/merchant-events`: notificaciones de eventos del comercio con respuesta generica local (sin consumo externo).
- `POST /api/v1/payments`: consulta de pagos por `operation_id`.
- `POST /api/v1/banks`: consulta de bancos por proveedor o todos.

Nota de contrato:
- En todos los servicios anteriores (excepto `/api/v1/pagos/test`), `channel_POS` es opcional en request.
- Si `channel_POS` no llega o llega vacio, el backend usa `POS` por defecto.
- Las respuestas JSON de estos servicios incluyen `channel_POS`.

## Auditoria de logs en base de datos

El backend registra auditoria en Oracle para cada flujo:

- `TUKUNAFUNC.IN_LOGS_APP_PAG_DIGIT`: registra consumo de servicios expuestos por la API.
- `TUKUNAFUNC.IN_LOGS_WS_EXT`: registra consumo de servicios externos por proveedor (por ejemplo Paysafe/Pichincha).
- El registro se ejecuta de forma asincrona (en paralelo al flujo HTTP) para no afectar las respuestas en linea.

Campos principales poblados por flujo:

- `request` y `response`: payload de entrada/salida serializado.
- `fecha_registro`: fecha/hora del registro.
- `mensaje`: resultado del flujo (`OK`, `ERROR_PROVEEDOR`, `ERROR_TECNICO`, etc.).
- `origen`: `WS_INTERNO` para API y nombre de proveedor para consumo externo.
- `codigo_prov_pago`, `url`, `metodo`, `cadena`, `farmacia`, `pos`, `folio`: contexto operativo del consumo.
- `folio`: se deriva de `merchant_sales_id` (request/response) cuando exista; si no, `NULL`.
- `cp_var2`: se deriva de `operation_id` (request/response) cuando exista; si no, `NULL`.
- En `IN_LOGS_WS_EXT`, `cp_var3` se registra siempre en `NULL`.

## Registro de pagos en IN_REGISTRO_PAGOS

Se incorporo persistencia de datos operativos en `TUKUNAFUNC.IN_REGISTRO_PAGOS`:

1. Endpoint `POST /api/v1/payments/notifications/merchant-events`:
- Inserta un registro por cada item de `merchant_events`.
- Mapeo:
`CADENA <- chain`,
`FARMACIA <- store`,
`NOMBRE_FARMACIA <- store_name`,
`POS <- pos`,
`CANAL <- channel_POS`,
`FECHA_REGISTRO <- merchant_events[].creation_datetime`,
`FOLIO <- merchant_events[].merchant_sales_id`,
`ID_OPERACION_EXTERNO <- merchant_events[].operation_id`,
`ID_INTERNO_VENTA <- merchant_events[].merchant_sales_id`.

En `merchant-events`, `CP_VAR1` y `CP_NUMBER1` se registran en `NULL`.
Los valores de esos campos se manejan unicamente en el flujo de `confirmation`.

Regla de validacion para `merchant-events`:
- Antes de registrar, se valida que la combinacion `ID_INTERNO_VENTA-farmacia` (`merchant_sales_id` + `store`) no exista previamente en `IN_REGISTRO_PAGOS`.
- Antes de registrar, se valida que `operation_id` no exista previamente en `IN_REGISTRO_PAGOS` (unicidad global).
- Si existe conflicto, el endpoint responde error `400` y no registra el evento como exitoso.
- Si el endpoint retorna cualquier error (`400/500`), no se inserta registro en `IN_REGISTRO_PAGOS`.

2. Endpoint `POST /api/v1/safetypay/confirmation`:
- Busca por:
`ID_INTERNO_VENTA = MerchantSalesID`,
`ID_OPERACION_EXTERNO = ReferenceNo`.
- Actualiza:
`FECHA_AUTORIZACION_PROV <- RequestDateTime`,
`NO_REFERENCIA <- ReferenceNo`,
`NO_REFERENCIA_PAGO <- PaymentReferenceNo`,
`MONTO <- Amount`,
`MONEDA <- CurrencyID`,
`COD_ESTADO_PAGO <- Status`,
`FIRMA <- Signature`,
`CP_NUMBER1 <- ErrorNumber`,
`CP_VAR1 <- descripcion de ErrorNumber`.

Valores de `ErrorNumber` en `confirmation`:
- `0`: `No error`
- `1`: `API Key not recognized`
- `2`: `Signature not valid`
- `3`: `Other errors`

Comportamiento de `OrderNo` en CSV de confirmation:
- `OrderNo` siempre retorna el mismo valor de `MerchantSalesID`.
- Entre escenarios de error, el unico campo que cambia es `ErrorNumber`.

## SLA/SLO/SLI

- Disponibilidad objetivo: 99.9%.
- Latencia p95 por endpoint: <= 300 ms.
- Tasa de errores p99: <= 0.5%.
- Ventanas de mantenimiento y contacto NOC: definir por entorno.
- Politica de reintentos: exponencial (100ms, 500ms, 2s; max 3).
- Timeout maximo por request: 2s.

## Estructura de errores (excepto SafetyPay)

Los endpoints devuelven el siguiente formato cuando el proveedor responde con error
o cuando se detecta un error de validacion interno:

```json
{
  "chain": 1,
  "store": 148,
  "store_name": "FYBECA AMAZONAS",
  "pos": 90,
  "payment_provider_code": 456123,
  "error": {
    "http_code": 400,
    "code": "INVALID_REQUEST",
    "category": "INVALID_REQUEST_ERROR",
    "message": "Request is not well-formed, schema is incorrect, or is missing a required parameter.",
    "information_link": null,
    "inner_details": [
      {
        "inner_code": null,
        "field": "country_code",
        "field_value": "ECUa",
        "field_message": "Country code is invalid. It should be 3 characters long."
      }
    ]
  }
}
```

## Configuracion de mapeo de errores por proveedor

Si un proveedor devuelve el objeto de error en otra ruta, se puede configurar
el path con `error-mapping.providers.<proveedor>.error`:

```yaml
error-mapping:
  providers:
    default:
      error: error
    paysafe:
      error: error
    pichincha:
      error: error
```

## Parametros de entrada (form-urlencoded)

- ApiKey
- RequestDateTime
- MerchantSalesID
- ReferenceNo
- CreationDateTime
- Amount
- CurrencyID
- PaymentReferenceNo
- Status
- Signature

## Ejemplo de request

```bash
curl -X POST http://localhost:8080/api/v1/safetypay/confirmation \
  -H "Content-Type: application/x-www-form-urlencoded" \
  --data-urlencode "ApiKey=MI_API_KEY" \
  --data-urlencode "RequestDateTime=2020-10-20T12:27:27" \
  --data-urlencode "MerchantSalesID=Prueba1" \
  --data-urlencode "ReferenceNo=Ref1" \
  --data-urlencode "CreationDateTime=2020-10-20T12:27:27" \
  --data-urlencode "Amount=50.00" \
  --data-urlencode "CurrencyID=USD" \
  --data-urlencode "PaymentReferenceNo=606973" \
  --data-urlencode "Status=102" \
  --data-urlencode "Signature=FIRMA_CALCULADA"
```

## Ejemplo de response (CSV)

```
0,2020-10-20T12:27:27Z,Prueba1,Ref1,2020-10-20T12:27:27,50.00,USD,606973,102,Prueba1,9F6A...
```

## Configuracion

```yaml
safetypay:
  confirmation:
    enabled: true
    providers:
      paysafe:
        apiKey: ${SAFETYPAY_CONFIRMATION_API_KEY:}
        secret: ${SAFETYPAY_CONFIRMATION_SECRET:}
        signatureMode: SHA256
        allowedIps: []
```

## Calculo de firma (SHA-256)

Texto base (concatenacion sin separadores) para request y response:

```
RequestDateTime + MerchantSalesID + ReferenceNo + CreationDateTime + Amount + CurrencyID +
PaymentReferenceNo + Status + SignatureKey
```

Para response se usa `ResponseDateTime` en lugar de `RequestDateTime`.

La firma es el hash SHA-256 del texto base, en hexadecimal mayuscula.

## Endpoint: Direct Online Payment Requests

- Metodo: POST
- Ruta: `/api/v1/direct-online-payment-requests`
- Content-Type: `application/json`
- Respuesta: `application/json`

## Parametros de entrada (JSON)

- chain
- store
- store_name
- pos
- payment_provider_code
- sales_amount.value
- sales_amount.currency_code
- country_code
- bank_id
- merchant_set_pay_amount
- expiration_time_minutes
- language_code
- custom_merchant_name
- merchant_sales_id
- requested_payment_type
- transaction_email
- send_email_shopper

## Ejemplo de request

```bash
curl -X POST http://localhost:8080/api/v1/direct-online-payment-requests \
  -H "Content-Type: application/json" \
  -d '{
    "chain": 1,
    "store": 148,
    "store_name": "FYBECA AMAZONAS",
    "pos": 90,
    "payment_provider_code": 235689,
    "sales_amount": { "value": 50.00, "currency_code": "USD" },
    "country_code": "EC",
    "bank_id": "0123",
    "merchant_set_pay_amount": true,
    "expiration_time_minutes": 30,
    "language_code": "ES",
    "custom_merchant_name": "TIENDA",
    "merchant_sales_id": "Prueba1",
    "requested_payment_type": "paycash",
    "transaction_email": "cliente@correo.com",
    "send_email_shopper": true
  }'
```

## Ejemplo de response (JSON)

```json
{
  "chain": 1,
  "store": 148,
  "pos": 90,
  "payment_provider_code": 235689,
  "response_datetime": "2025-08-27T23:22:14",
  "operation_id": "0125239606973873",
  "bank_redirect_url": "https://...",
  "payment_expiration_datetime": "2025-08-27T23:52:14",
  "payment_expiration_datetime_utc": "2025-08-28T04:52:14",
  "transaction_id": "T-001",
  "payable_amounts": [],
  "payment_locations": []
}
```

## Configuracion

```yaml
direct-online-payment-requests:
  providers:
    paysafe:
      enabled: true
      type: rest
      method: POST
      url: https://sandbox-mws.safetypay.com/mpi/api/v1/direct-online-payment-requests
      headers:
        X-Api-Key: ${API_KEY}
        X-Version: 20200803
        Content-Type: application/json
```

## Endpoint: Merchant Events

- Metodo: POST
- Ruta: `/api/v1/payments/notifications/merchant-events`
- Content-Type: `application/json`
- Respuesta: `application/json`

## Parametros de entrada (JSON)

- chain
- store
- store_name
- pos
- payment_provider_code
- merchant_events[].event_code
- merchant_events[].creation_datetime
- merchant_events[].operation_id
- merchant_events[].merchant_sales_id
- merchant_events[].operation_status
- request_datetime

Validaciones:
- `merchant_events` es requerido.
- `merchant_events[].merchant_sales_id` no puede ser nulo ni vacio.

## Ejemplo de request

```bash
curl -X POST http://localhost:8080/api/v1/payments/notifications/merchant-events \
  -H "Content-Type: application/json" \
  -d '{
    "chain": 1,
    "store": 148,
    "store_name": "FYBECA AMAZONAS",
    "pos": 90,
    "payment_provider_code": 235689,
    "merchant_events": [
      {
        "event_code": "merchant.received.transaction.notification",
        "creation_datetime": "2025-08-27T16:03:36.037",
        "operation_id": "0125239606973873",
        "merchant_sales_id": "Prueba1",
        "operation_status": "OK"
      }
    ],
    "request_datetime": "2020-08-27T16:25:55.357"
  }'
```

## Ejemplo de response (JSON)

```json
{
  "chain": 1,
  "store": 148,
  "pos": 90,
  "payment_provider_code": 235689,
  "request_id": "grpa_83d3302b-1696-4eb2-bcf4-16338ab476a1",
  "response_datetime": "2025-08-27T23:22:14"
}
```

## Configuracion

```yaml
merchant-events:
  providers:
    paysafe:
      enabled: true
      type: rest
      method: POST
      url: https://sandbox-mws.safetypay.com/mpi/api/v1/payments/notifications/merchant-events
      headers:
        X-Api-Key: ${API_KEY}
        X-Version: 20200803
        Content-Type: application/json
```

## Endpoint: Payments

- Metodo: POST
- Ruta: `/api/v1/payments`
- Content-Type: `application/json`
- Respuesta: `application/json`

## Parametros de entrada (JSON)

- chain
- store
- store_name
- pos
- payment_provider_code
- operation_id
- request_datetime

## Ejemplo de request

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "chain": 1,
    "store": 148,
    "store_name": "FYBECA AMAZONAS",
    "pos": 90,
    "payment_provider_code": 456123,
    "operation_id": "0125239606973873",
    "request_datetime": "2020-08-27T16:25:55.357"
  }'
```

## Ejemplo de response (JSON)

```json
{
  "chain": 1,
  "store": 148,
  "pos": 90,
  "payment_provider_code": 456123,
  "request_id": "grpa_83d3302b-1696-4eb2-bcf4-16338ab476a1",
  "response_datetime": "2025-08-27T23:22:14",
  "payment_operations": [
    {
      "refunds_related": [],
      "creation_datetime": "2025-08-27T23:21:15",
      "operation_id": "0125239606973873",
      "merchant_sales_id": "Test",
      "merchant_order_id": "",
      "payment_amount": { "value": 50.00, "currency_code": "USD" },
      "shopper_amount": { "value": 50.00, "currency_code": "USD" },
      "shopper_email": null,
      "additional_info": null,
      "operation_activities": [
        { "creation_datetime": "2025-08-27T23:21:15", "status_code": "101", "status_description": "Purchase Pending" }
      ],
      "payment_reference_number": "606973"
    }
  ]
}
```

## Configuracion

```yaml
payments:
  providers:
    paysafe:
      enabled: true
      type: rest
      method: GET
      url: https://sandbox-mws.safetypay.com/mpi/api/v1/payments/
      query:
        operation_id: ${operation_id}
        request_datetime: ${request_datetime}
        limit: 100
      headers:
        X-Api-Key: ${API_KEY}
        X-Version: 20200803
```

## Endpoint: Banks

- Metodo: POST
- Ruta: `/api/v1/banks`
- Content-Type: `application/json`
- Respuesta: `application/json`

## Parametros de entrada (JSON)

- chain
- store
- store_name
- pos
- payment_provider_code (opcional)
- channel_POS
- country_code

Reglas de filtrado backend:
- Se consume el servicio externo y luego se aplican dos filtros en interseccion.
- Filtro 1 (principal por cadena): `AD_TIPO_PAGO` con `ACTIVO = 'S'` y bandera de cadena en `S`
(`CADENA_FYB`/`CADENA_SANA`/`CADENA_OKI`/`CADENA_FR`) segun `chain`.
- Filtro 2 (adicional por canal): consulta `AD_CANAL` + `AD_CANAL_TIPO_PAGO` + `AD_TIPO_PAGO`
con `A.ACTIVO = 'S'`.
- En filtro por canal, el match es:
`A.DESCRIPCION -> channel_POS`, `B.CODIGO_TIPOPAGO -> banks.bank_id`,
`C.CODIGO_BILLETERA_DIGITAL -> payment_provider_code`.
- Solo se devuelven bancos que cumplan ambos filtros.
- Ambos catalogos se consultan desde cache en memoria (no por request).

## Ejemplo de request

```bash
curl -X POST http://localhost:8080/api/v1/banks \
  -H "Content-Type: application/json" \
  -d '{
    "chain": 1,
    "store": 148,
    "store_name": "FYBECA AMAZONAS",
    "pos": 90,
    "payment_provider_code": 235689,
    "country_code": "EC"
  }'
```

## Ejemplo de response (JSON)

```json
{
  "chain": 1,
  "store": 148,
  "pos": 90,
  "request_id": "grpa_83d3302b-1696-4eb2-bcf4-16338ab476a1",
  "response_datetime": "2025-08-27T23:22:14",
  "payment_providers": [
    {
      "payment_provider_name": "PAYSAFE",
      "payment_provider_code": 235689,
      "banks": []
    }
  ]
}
```

## Configuracion

```yaml
getbanks:
  providers:
    paysafe:
      enabled: true
      type: rest
      method: GET
      url: https://sandbox-mws.safetypay.com/mpi/api/v1/banks
      query:
        country_code: ${country_code}
        channel: 1
        request_datetime: ${now}
        limit: 100
      headers:
        X-Api-Key: ${API_KEY}
        X-Version: 20200803
```

## Swagger / OpenAPI

Despues de levantar el servicio, puedes acceder a:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
