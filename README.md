# Pagos Digitales - SafetyPay Confirmation

## Documentación técnica

- Arquitectura y operación: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Manual técnico: [`docs/MANUAL_TECNICO.md`](docs/MANUAL_TECNICO.md)
- Manual técnico (PDF): [`docs/MANUAL_TECNICO.pdf`](docs/MANUAL_TECNICO.pdf)
- Manual de usuario: [`docs/MANUAL_USUARIO.md`](docs/MANUAL_USUARIO.md)
- Manual de usuario (PDF): [`docs/MANUAL_USUARIO.pdf`](docs/MANUAL_USUARIO.pdf)
- ADR de arquitectura: [`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md)
- Especificación OpenAPI: [`docs/openapi.yaml`](docs/openapi.yaml)

## Configuracion global del proyecto (application.yaml)

```yaml
server:
  port: 8080

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

providers-pay:
  codes:
    paysafe: 235689
    pichincha: 456123

providers:
  dynamic-routes-enabled: true
```

Notas:
- Ajusta `spring.datasource.*` segun el entorno.
- `providers-pay.codes` define los codigos de proveedores usados en los endpoints genericos.

## Endpoint

- Metodo: POST
- Ruta: `/api/v1/safetypay/confirmation`
- Content-Type: `application/x-www-form-urlencoded`
- Respuesta: `text/plain` con una linea CSV

## Endpoints disponibles

- `POST /api/v1/safetypay/confirmation`: webhook de confirmaciones SafetyPay (form-urlencoded, respuesta CSV firmada).
- `POST /api/v1/direct-online-payment-requests`: crea pagos en linea con proveedor.
- `POST /api/v1/payments/notifications/merchant-events`: notificaciones de eventos del comercio hacia proveedor.
- `GET /api/v1/payments`: consulta de pagos por `operation_id`.
- `POST /api/v1/banks`: consulta de bancos por proveedor o todos.

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
- payment_provider_code
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
  --data-urlencode "payment_provider_code=235689" \
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

- Metodo: GET
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
curl -X GET http://localhost:8080/api/v1/payments \
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
