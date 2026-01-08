# Pagos Digitales - SafetyPay Confirmation

## Endpoint

- Metodo: POST
- Ruta: `/api/v1/safetypay/confirmation`
- Content-Type: `application/x-www-form-urlencoded`
- Respuesta: `text/plain` con una linea CSV

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
