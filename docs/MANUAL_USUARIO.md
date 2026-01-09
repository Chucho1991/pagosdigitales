# Manual de usuario — PagosDigitales

## 1. Objetivo

Este manual guía a usuarios funcionales y técnicos en el uso de la API de PagosDigitales, incluyendo endpoints disponibles, formatos de solicitud, respuestas y recomendaciones de operación.

## 2. Requisitos previos

- Acceso a la API (URL base y credenciales de proveedor).
- Código de proveedor (`payment_provider_code`).
- Conocer el formato de los payloads JSON o form-urlencoded según el endpoint.

## 3. URL base

```
http://<host>:8080
```

## 4. Endpoints disponibles

| Método | Ruta | Descripción | Content-Type | Respuesta |
|---|---|---|---|---|
| POST | `/api/v1/banks` | Consulta bancos por proveedor o todos | JSON | JSON |
| GET | `/api/v1/payments` | Consulta pagos por `operation_id` | JSON (body) | JSON |
| POST | `/api/v1/direct-online-payment-requests` | Solicitud de pago en línea | JSON | JSON |
| POST | `/api/v1/payments/notifications/merchant-events` | Notificaciones de eventos del comercio | JSON | JSON |
| POST | `/api/v1/safetypay/confirmation` | Webhook SafetyPay (CSV firmado) | `application/x-www-form-urlencoded` | `text/plain` |
| GET | `/api/v1/pagos/test` | Health check | — | text/plain |

## 5. Guías de uso

### 5.1 Health check
```bash
curl -X GET http://localhost:8080/api/v1/pagos/test
```

### 5.2 Consultar bancos
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

### 5.3 Solicitar pago en línea
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

### 5.4 Consultar pagos
```bash
curl -X GET http://localhost:8080/api/v1/payments \
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

### 5.5 Merchant Events
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
      { "event_type": "payment_completed", "event_datetime": "2025-08-27T23:30:00" }
    ],
    "request_datetime": "2025-08-27T23:30:00"
  }'
```

### 5.6 SafetyPay Confirmation (webhook)
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

## 6. Respuestas de error

Los endpoints (excepto SafetyPay) devuelven un objeto de error con código HTTP y detalles de validación. Revise el campo `error` en la respuesta.

## 7. Buenas prácticas

- Enviar `payment_provider_code` siempre que aplique.
- Usar `request_datetime` en formato ISO cuando sea requerido.
- Mantener sincronizadas las credenciales de proveedor.

## 8. Soporte

Para incidencias, incluya:
- Endpoint invocado
- Payload enviado
- Hora y zona horaria
- Código de proveedor
- Respuesta completa
