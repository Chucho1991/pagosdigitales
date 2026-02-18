# DocumentaciÃ³n tÃ©cnica â€” PagosDigitales

## 1. Arquitectura

**Estilo:** Monolito Spring Boot con capas (API, Application, Domain, Infrastructure) y rutas dinÃ¡micas con Apache Camel.

**Capas:**
- **API:** Controladores REST expuestos en `com.femsa.gpf.pagosdigitales.api.controller`.
- **Application:** Mappers y servicios de orquestaciÃ³n (`application.mapper`, `application.service`).
- **Domain:** Servicios de negocio puros (`domain.service`).
- **Infrastructure:** ConfiguraciÃ³n y adaptadores de integraciÃ³n (Camel Routes, properties).

**JustificaciÃ³n:** la integraciÃ³n con proveedores externos se desacopla mediante rutas Camel y mapeos configurables, permitiendo agregar proveedores sin alterar la API pÃºblica.

## 2. MÃ³dulos

### 2.1 API
- **Controllers**: `BanksController`, `PaymentsController`, `DirectOnlinePaymentRequestsController`, `MerchantEventsController`, `SafetypayConfirmationController`, `PagosDigitalesController`.
- **DTOs**: solicitudes y respuestas JSON/form para pagos, bancos, eventos y confirmaciones.

### 2.2 Application
- **Mappers**: normalizan requests/responses por proveedor (`DirectOnlinePaymentMap`, `PaymentsMap`, `BanksMap`, `MerchantEventsMap`).
- **Servicios**: `SafetypayConfirmationService` y componentes de idempotencia/orden.

### 2.3 Domain
- **ProvidersPayService**: resoluciÃ³n de nombre/cÃ³digo de proveedor.
- **SignatureService**: firma y validaciÃ³n SHA-256 para SafetyPay.

### 2.4 Infrastructure
- **Camel Routes**: rutas dinÃ¡micas hacia proveedores (`Dynamic*Route`).
- **Properties**: configuraciÃ³n de endpoints, credenciales y mapeos.

## 3. Endpoints REST

| MÃ©todo | Ruta | DescripciÃ³n | Content-Type | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/banks` | Consulta bancos por proveedor o todos | JSON | JSON |
| POST | `/api/v1/payments` | Consulta pagos por `operation_id` | JSON (body) | JSON |
| POST | `/api/v1/direct-online-payment-requests` | Solicitud de pago en lÃ­nea | JSON | JSON |
| POST | `/api/v1/payments/notifications/merchant-events` | Notificaciones de eventos del comercio | JSON | JSON |
| POST | `/api/v1/safetypay/confirmation` | Webhook SafetyPay (CSV firmado) | `application/x-www-form-urlencoded` | `text/plain` |
| GET | `/api/v1/pagos/test` | Health check | â€” | text/plain |

## 4. Flujo de datos

### 4.1 Direct Online Payment Requests
1. Controller valida proveedor.
2. Mapper construye payload del proveedor con mappings.
3. Camel Route arma request dinÃ¡mico (URL/mÃ©todo/headers).
4. Mapper normaliza la respuesta.

### 4.2 Payments/Banks/Merchant Events
- **Payments**: controller â†’ Camel Route con query params â†’ respuesta normalizada.
- **Banks**: controller â†’ Camel Route â†’ agregaciÃ³n por proveedor (si no se indica uno).
- **Merchant Events**: controller â†’ mapper â†’ Camel Route.

### 4.3 SafetyPay Confirmation
1. Controller mapea form-urlencoded a DTO.
2. Service valida API Key, firma, IP y secretos.
3. Idempotencia con store en memoria.
4. Respuesta CSV firmada.

## 5. ConfiguraciÃ³n

UbicaciÃ³n: `src/main/resources/application.yaml`

### 5.1 Base
- `server.port`
- `spring.datasource.*`
- `spring.jpa.*`
- `camel.springboot.main-run-controller`

### 5.2 Proveedores
- `direct-online-payment-requests.providers.*`
- `merchant-events.providers.*`
- `payments.providers.*`
- `getbanks.providers.*`

### 5.3 Mapeos
- `direct-online-payment-requests.mapping.*`
- `merchant-events.mapping.*`
- `payments.mapping.*`
- `getbanks.mapping.*`

### 5.4 CÃ³digos y errores
- `providers-pay.codes`
- `error-mapping.providers.*`

### 5.5 SafetyPay
- `safetypay.confirmation.enabled`
- `safetypay.confirmation.providers.*`

## 6. Despliegue con Docker

### 6.1 Dockerfile sugerido
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/pagosdigitales-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 6.2 Build & Run
```bash
mvn clean package
docker build -t pagosdigitales .
docker run -p 8080:8080 pagosdigitales
```

## 7. Seguridad
- ValidaciÃ³n de firmas SHA-256 en confirmaciones SafetyPay.
- ValidaciÃ³n de API Key y lista de IPs permitidas.
- Headers de autenticaciÃ³n por proveedor en la configuraciÃ³n.
- Idempotencia para evitar reprocesamiento de notificaciones.

## 8. Ejemplos de uso

### 8.1 SafetyPay Confirmation
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

### 8.2 Direct Online Payment Requests
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

### 8.3 Payments
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "chain": 1,
    "store": 148,
    "store_name": "FYBECA AMAZONAS",
    "pos": 90,
    "payment_provider_code": 235689,
    "operation_id": "0125239606973873",
    "request_datetime": "2025-08-27T23:22:14"
  }'
```

### 8.4 Banks
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

