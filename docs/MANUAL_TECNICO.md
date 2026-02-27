# Manual técnico — PagosDigitales

## 1. Propósito

Este manual técnico describe la operación interna del servicio PagosDigitales, su arquitectura, configuración, integración con proveedores y guías de despliegue y operación.

## 2. Alcance

- Aplicación Spring Boot 3.x con Apache Camel.
- API REST para pagos, bancos y notificaciones.
- Confirmaciones SafetyPay con firma SHA-256.
- Configuracion de WS externos por proveedor en BD (`IN_PASARELA_WS`, `IN_PASARELA_HEADERS`, `IN_PASARELA_WS_DEFS` y `AD_MAPEO_SERVICIOS`).

## 3. Arquitectura

**Estilo:** monolito en capas (API, Application, Domain, Infrastructure) con integración dinámica por proveedor.

**Componentes clave:**
- **Controllers:** expone endpoints REST.
- **Mappers:** transforman requests/responses hacia el formato de cada proveedor.
- **Servicios de dominio:** firma y resolución de proveedor.
- **Camel Routes:** enrutan solicitudes hacia proveedores externos.

## 4. Estructura de paquetes

- `com.femsa.gpf.pagosdigitales.api`: controladores y DTOs.
- `com.femsa.gpf.pagosdigitales.application`: orquestación y mapeos.
- `com.femsa.gpf.pagosdigitales.domain`: lógica de negocio.
- `com.femsa.gpf.pagosdigitales.infrastructure`: rutas Camel y configuración.

## 5. Endpoints

| Método | Ruta | Descripción | Content-Type | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/banks` | Consulta bancos por proveedor o todos | JSON | JSON |
| POST | `/api/v1/payments` | Consulta pagos por `operation_id` | JSON (body) | JSON |
| POST | `/api/v1/direct-online-payment-requests` | Solicitud de pago en línea | JSON | JSON |
| POST | `/api/v1/payments/notifications/merchant-events` | Notificaciones de eventos del comercio | JSON | JSON |
| POST | `/api/v1/safetypay/confirmation` | Webhook SafetyPay (CSV firmado) | `application/x-www-form-urlencoded` | `text/plain` |
| GET | `/api/v1/pagos/test` | Health check | — | text/plain |

## 6. Flujo de integración

### 6.1 Pagos en línea
1. Controller valida proveedor y solicitud.
2. Mapper crea payload del proveedor según mapping.
3. Mapper/Camel Route resuelven URL y metodo desde `IN_PASARELA_WS`, headers desde `IN_PASARELA_HEADERS`, defaults/query desde `IN_PASARELA_WS_DEFS` y mapeos request/response desde `AD_MAPEO_SERVICIOS`.
4. Respuesta normalizada hacia DTO interno.

### 6.2 Consulta de pagos
1. Controller valida `payment_provider_code` y `operation_id`.
2. Camel Route agrega parámetros de consulta.
3. Respuesta normalizada a DTO interno.

### 6.3 Consulta de bancos
1. Controller consulta proveedor específico o todos.
2. Camel Route construye URL con parámetros.
3. Respuesta normalizada por mapping.

### 6.4 Merchant Events
1. Controller valida proveedor.
2. Mapper transforma payload.
3. Camel Route envía notificación.

### 6.5 SafetyPay Confirmation
1. Controller mapea form-urlencoded a DTO.
2. Service valida API Key, firma e IP permitida.
3. Aplica idempotencia y registra notificación.
4. Responde CSV firmado.

## 7. Configuración

Ubicación: `src/main/resources/application.yaml`

### 7.1 Base
- `server.port`
- `spring.datasource.*`
- `spring.jpa.*`

### 7.2 Proveedores por endpoint (BD)
- `TUKUNAFUNC.AD_BILLETERAS_DIGITALES`: proveedores activos.
- `TUKUNAFUNC.IN_PASARELA_WS`: configuracion de consumo externo por `CODIGO_BILLETERA` + `WS_KEY`.
- `TUKUNAFUNC.IN_PASARELA_HEADERS`: headers externos por `CODIGO_BILLETERA`.
- `TUKUNAFUNC.IN_PASARELA_WS_DEFS`: definiciones de request por `ID_WS` con `TIPO_DEF` (`QUERY`/`DEFAULTS`).
- `TUKUNAFUNC.AD_MAPEO_SERVICIOS`: mapeo de atributos app/proveedor por `CODIGO_BILLETERA`, `APP_SERVICE_KEY`, `APP_OPERATION`, `DIRECCION`.

### 7.3 Mappings
- Fuente unica: `TUKUNAFUNC.AD_MAPEO_SERVICIOS`
- Campos usados: `SECCION_APP`, `ATRIBUTO_APP`, `SECCION_EXT`, `ATRIBUTO_EXT`, `ORDEN_APLICACION`, `ACTIVO`

### 7.4 Errores
- Fuente unica: `TUKUNAFUNC.AD_MAPEO_SERVICIOS` con `DIRECCION='ERROR'`

### 7.5 SafetyPay
- Fuente unica en BD:
- `IN_SAFETYPAY_CFG` (`CODIGO_BILLETERA`, `ENABLED`, `API_KEY`, `SECRET`, `SIGNATURE_MODE`, `ALLOWED_IPS`, `ACTIVO`)

## 8. Seguridad

- Validación de firma SHA-256 y API Key en SafetyPay.
- Lista opcional de IPs permitidas para confirmaciones.
- Headers por proveedor obtenidos de `IN_PASARELA_HEADERS`.
- Parametros de request por proveedor obtenidos de `IN_PASARELA_WS_DEFS`.
- Mapeo request/response por proveedor obtenido de `AD_MAPEO_SERVICIOS`.

## 9. Despliegue

### 9.1 Requisitos
- Java 17
- Maven
- Oracle JDBC accesible

### 9.2 Build
```bash
mvn clean package
```

### 9.3 Docker (referencial)
```dockerfile
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/pagosdigitales-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## 10. Operación y monitoreo

- Revisar logs de Camel para trazabilidad de requests/response.
- Validar métricas de latencia por endpoint.
- Ajustar timeouts por proveedor según SLA.

## 11. Directorio de términos

- **API**: Interfaz de programación que expone los endpoints del servicio.
- **Camel Route**: Ruta de Apache Camel que orquesta llamadas a proveedores externos.
- **DTO**: Objeto de transferencia de datos usado en requests/responses.
- **Idempotencia**: Capacidad de procesar la misma notificación sin duplicar efectos.
- **Provider**: Proveedor externo de pagos configurado en tablas de BD.
- **SafetyPay Confirmation**: Webhook firmado que confirma el estado de un pago.
- **Signature**: Firma criptográfica SHA-256 usada para validar autenticidad.

## 12. Ejemplos de uso

### 11.1 SafetyPay Confirmation
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

### 11.2 Direct Online Payment Requests
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

