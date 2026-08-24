-- ============================================================================
-- INSERTS DEUNA V1 - PRODUCCION
-- Oracle / esquema TUKUNAFUNC
--
-- Reemplazar antes de ejecutar:
--   <TU_API_KEY_PROD>
--   <TU_API_SECRET_PROD>
--
-- El script es para una carga inicial en PRODUCCION.
-- Incluye la creacion de la tabla de puntos de venta y los INSERT de DEUNA.
-- ============================================================================

-- ==========================================================================
-- PASO 1. CREAR Y CARGAR LOS PUNTOS DE VENTA DEUNA
-- La combinacion proveedor + cadena + local + POS no puede repetirse.
-- POINT_OF_SALE es texto porque el codigo externo puede contener ceros.
-- ==========================================================================

CREATE TABLE TUKUNAFUNC.IN_PASARELA_PUNTO_VENTA (
    CODIGO_BILLETERA NUMBER(10)   NOT NULL,
    CODIGO_CADENA    NUMBER(10)   NOT NULL,
    CODIGO_LOCAL     NUMBER(10)   NOT NULL,
    CODIGO_POS       NUMBER(10)   NOT NULL,
    POINT_OF_SALE    VARCHAR2(50) NOT NULL,
    ACTIVO           CHAR(1)      DEFAULT 'S' NOT NULL,
    USR_CREACION     VARCHAR2(50) DEFAULT USER NOT NULL,
    FEC_CREACION     DATE         DEFAULT SYSDATE NOT NULL,
    CONSTRAINT PK_IN_PASARELA_PTO_VTA PRIMARY KEY (
        CODIGO_BILLETERA,
        CODIGO_CADENA,
        CODIGO_LOCAL,
        CODIGO_POS
    ),
    CONSTRAINT CK_IN_PASARELA_PTO_ACT CHECK (ACTIVO IN ('S', 'N'))
);

-- Combinaciones visibles en la matriz entregada.
INSERT ALL
    INTO TUKUNAFUNC.IN_PASARELA_PUNTO_VENTA
        (CODIGO_BILLETERA, CODIGO_CADENA, CODIGO_LOCAL, CODIGO_POS, POINT_OF_SALE)
        VALUES (300002, 60, 148, 1,  '4202373')
SELECT 1 FROM DUAL;

COMMIT;

-- ==========================================================================
-- PASO 2. AGREGAR LA BILLETERA DEUNA
-- TIPO_BANCO = INTERNO permite obtener las opciones desde AD_TIPO_PAGO.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.AD_BILLETERAS_DIGITALES (
    CODIGO,
    CODIGO_JDE,
    NOMBRE_BILLETERA_DIGITAL,
    DESCRIPCION,
    ACTIVA,
    ES_GRAN_CONTRIBUYENTE,
    FECHA_REGISTRO,
    TIPO_BANCO
) VALUES (
    300002,
    4,
    'DeUna',
    'DeUna Billetera digital',
    'S',
    'N',
    SYSDATE,
    'INTERNO'
);

COMMIT;

-- ==========================================================================
-- PASO 3. AGREGAR EL TIPO DE PAGO DEUNA
-- CODIGO_ESTABLECIMIENTO = 2 sera el bank_id utilizado para DEUNA.
-- Ajustar MINIMO, MAXIMO y las cadenas habilitadas si negocio lo requiere.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.AD_TIPO_PAGO (
    CODIGO,
    CODIGO_BILLETERA_DIGITAL,
    DESCRIPCION,
    ACTIVO,
    MINIMO,
    MAXIMO,
    CADENA_FYB,
    CADENA_SANA,
    CADENA_OKI,
    CADENA_FR,
    CODIGO_ESTABLECIMIENTO,
    FECHA_MODIFICACION,
    AUXILIAR,
    CUENTA,
    TIPO_CODIGO_GENERA_PAGO
)
SELECT
    NVL(MAX(CODIGO), 0) + 1,
    300002,
    'DeUna',
    'S',
    0.10,
    10000.00,
    'S',
    'S',
    'S',
    'S',
    2,
    SYSDATE,
    5,
    0,
    '-'
FROM TUKUNAFUNC.AD_TIPO_PAGO;

COMMIT;

-- ==========================================================================
-- PASO 4. HABILITAR DEUNA EN LOS CANALES
-- CODIGO_CANAL 1 y 2 son los canales utilizados en el ambiente actual.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.AD_CANAL_TIPO_PAGO (
    CODIGO,
    CODIGO_CANAL,
    CODIGO_TIPOPAGO
)
SELECT
    (SELECT NVL(MAX(CODIGO), 0) + 1
       FROM TUKUNAFUNC.AD_CANAL_TIPO_PAGO),
    1,
    (SELECT CODIGO
       FROM TUKUNAFUNC.AD_TIPO_PAGO
      WHERE CODIGO_BILLETERA_DIGITAL = 300002
        AND CODIGO_ESTABLECIMIENTO = 2)
FROM DUAL;

INSERT INTO TUKUNAFUNC.AD_CANAL_TIPO_PAGO (
    CODIGO,
    CODIGO_CANAL,
    CODIGO_TIPOPAGO
)
SELECT
    (SELECT NVL(MAX(CODIGO), 0) + 1
       FROM TUKUNAFUNC.AD_CANAL_TIPO_PAGO),
    2,
    (SELECT CODIGO
       FROM TUKUNAFUNC.AD_TIPO_PAGO
      WHERE CODIGO_BILLETERA_DIGITAL = 300002
        AND CODIGO_ESTABLECIMIENTO = 2)
FROM DUAL;

COMMIT;

-- ==========================================================================
-- PASO 5. AGREGAR LOS HEADERS DE AUTENTICACION DEUNA
-- Reemplazar API key y secret con los valores productivos.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.IN_PASARELA_HEADERS (
    ID_HEADER,
    CODIGO_BILLETERA,
    HEADER_NOMBRE,
    HEADER_VALOR
)
SELECT
    NVL(MAX(ID_HEADER), 0) + 1,
    300002,
    'x-api-key',
    '<TU_API_KEY_PROD>'
FROM TUKUNAFUNC.IN_PASARELA_HEADERS;

INSERT INTO TUKUNAFUNC.IN_PASARELA_HEADERS (
    ID_HEADER,
    CODIGO_BILLETERA,
    HEADER_NOMBRE,
    HEADER_VALOR
)
SELECT
    NVL(MAX(ID_HEADER), 0) + 1,
    300002,
    'x-api-secret',
    '<TU_API_SECRET_PROD>'
FROM TUKUNAFUNC.IN_PASARELA_HEADERS;

INSERT INTO TUKUNAFUNC.IN_PASARELA_HEADERS (
    ID_HEADER,
    CODIGO_BILLETERA,
    HEADER_NOMBRE,
    HEADER_VALOR
)
SELECT
    NVL(MAX(ID_HEADER), 0) + 1,
    300002,
    'Content-Type',
    'application/json'
FROM TUKUNAFUNC.IN_PASARELA_HEADERS;

COMMIT;

-- ==========================================================================
-- PASO 6. AGREGAR LOS SERVICIOS DEUNA
-- Las URLs corresponden al ambiente productivo de DEUNA.
-- ==========================================================================

-- Generacion de QR/link.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS (
    ID_WS,
    CODIGO_BILLETERA,
    WS_KEY,
    ENABLED,
    TIPO_CONEXION,
    METODO_HTTP,
    TIPO_REQUEST,
    URL
) VALUES (
    SEQ_IN_PASARELA_WS.NEXTVAL,
    300002,
    'direct-online-payment-requests',
    'S',
    'REST',
    'POST',
    'JSON',
    'https://apis-merchant.pdn.deunalab.com/merchant/v1/payment/request'
);

-- Consulta de pagos DEUNA.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS (
    ID_WS,
    CODIGO_BILLETERA,
    WS_KEY,
    ENABLED,
    TIPO_CONEXION,
    METODO_HTTP,
    TIPO_REQUEST,
    URL
) VALUES (
    SEQ_IN_PASARELA_WS.NEXTVAL,
    300002,
    'payments',
    'S',
    'REST',
    'POST',
    'JSON',
    'https://apis-merchant.pdn.deunalab.com/merchant/v1/payment/info'
);

-- BanksController exige un WS getbanks activo, aunque DEUNA se resuelva local.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS (
    ID_WS,
    CODIGO_BILLETERA,
    WS_KEY,
    ENABLED,
    TIPO_CONEXION,
    METODO_HTTP,
    TIPO_REQUEST,
    URL
) VALUES (
    SEQ_IN_PASARELA_WS.NEXTVAL,
    300002,
    'getbanks',
    'S',
    'REST',
    'GET',
    'PARAMETROS',
    'INTERNO'
);

COMMIT;

-- ==========================================================================
-- PASO 7. AGREGAR LOS DEFAULTS DE LOS REQUESTS DEUNA
-- Estos valores se agregan automaticamente al body enviado al proveedor.
-- ==========================================================================

-- format=2 devuelve QR base64 y enlace de cobro.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS_DEFS (
    ID_DEFAULT,
    ID_WS,
    DEFAULT_CLAVE,
    DEFAULT_VALOR_TEXTO,
    TIPO_DEF,
    DEFAULT_VALOR_SISTEMA
)
SELECT
    (SELECT NVL(MAX(ID_DEFAULT), 0) + 1
       FROM TUKUNAFUNC.IN_PASARELA_WS_DEFS),
    ID_WS,
    'format',
    '2',
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- qrType=dynamic genera un QR diferente para cada transaccion.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS_DEFS (
    ID_DEFAULT,
    ID_WS,
    DEFAULT_CLAVE,
    DEFAULT_VALOR_TEXTO,
    TIPO_DEF,
    DEFAULT_VALOR_SISTEMA
)
SELECT
    (SELECT NVL(MAX(ID_DEFAULT), 0) + 1
       FROM TUKUNAFUNC.IN_PASARELA_WS_DEFS),
    ID_WS,
    'qrType',
    'dynamic',
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- expiredTime=15 minutos.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS_DEFS (
    ID_DEFAULT,
    ID_WS,
    DEFAULT_CLAVE,
    DEFAULT_VALOR_NUM,
    TIPO_DEF,
    DEFAULT_VALOR_SISTEMA
)
SELECT
    (SELECT NVL(MAX(ID_DEFAULT), 0) + 1
       FROM TUKUNAFUNC.IN_PASARELA_WS_DEFS),
    ID_WS,
    'expiredTime',
    15,
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- idType=0 consulta utilizando el transactionId entregado por DEUNA.
INSERT INTO TUKUNAFUNC.IN_PASARELA_WS_DEFS (
    ID_DEFAULT,
    ID_WS,
    DEFAULT_CLAVE,
    DEFAULT_VALOR_TEXTO,
    TIPO_DEF,
    DEFAULT_VALOR_SISTEMA
)
SELECT
    (SELECT NVL(MAX(ID_DEFAULT), 0) + 1
       FROM TUKUNAFUNC.IN_PASARELA_WS_DEFS),
    ID_WS,
    'idType',
    '0',
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'payments';

COMMIT;

-- ==========================================================================
-- PASO 8. MAPEOS REQUEST DEUNA
-- APP_OPERATION debe ser DEUNA porque ese es el proveedor resuelto por Java.
-- ==========================================================================

-- direct-online-payment-requests: sales_amount.value -> amount.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'direct-online-payment-requests', 'DEUNA', 'REQUEST',
    'BODY', 'sales_amount.value', 'BODY', 'amount',
    'NUMBER', 1, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- direct-online-payment-requests: custom_merchant_name -> detail.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'direct-online-payment-requests', 'DEUNA', 'REQUEST',
    'BODY', 'custom_merchant_name', 'BODY', 'detail',
    'STRING', 2, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- direct-online-payment-requests: merchant_sales_id -> internalTransactionReference.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'direct-online-payment-requests', 'DEUNA', 'REQUEST',
    'BODY', 'merchant_sales_id', 'BODY', 'internalTransactionReference',
    'STRING', 3, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- payments: operation_id -> idTransacionReference.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'payments', 'DEUNA', 'REQUEST',
    'BODY', 'operation_id', 'BODY', 'idTransacionReference',
    'STRING', 1, 'S', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'payments';

COMMIT;

-- ==========================================================================
-- PASO 9. MAPEOS RESPONSE DEUNA
-- Los nombres paymentOperation.* permiten convertir la respuesta plana de
-- DEUNA en el arreglo payment_operations del contrato comun.
-- ==========================================================================

-- Respuesta de creacion: transactionId -> operationId.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'direct-online-payment-requests', 'DEUNA', 'RESPONSE',
    'BODY', 'operationId', 'BODY', 'transactionId',
    'STRING', 1, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- Respuesta de creacion: transactionId -> transactionId.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'direct-online-payment-requests', 'DEUNA', 'RESPONSE',
    'BODY', 'transactionId', 'BODY', 'transactionId',
    'STRING', 2, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- Respuesta de creacion: deeplink -> bankRedirectUrl.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    ID_WS,
    'direct-online-payment-requests', 'DEUNA', 'RESPONSE',
    'BODY', 'bankRedirectUrl', 'BODY', 'deeplink',
    'STRING', 3, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002
  AND WS_KEY = 'direct-online-payment-requests';

-- Consulta payments: request_id.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT SEQ_AD_MAPEO_SERVICIOS.NEXTVAL, 300002, ID_WS,
       'payments', 'DEUNA', 'RESPONSE',
       'BODY', 'requestId', 'BODY', 'transactionId',
       'STRING', 1, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002 AND WS_KEY = 'payments';

-- Consulta payments: fecha de respuesta.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT SEQ_AD_MAPEO_SERVICIOS.NEXTVAL, 300002, ID_WS,
       'payments', 'DEUNA', 'RESPONSE',
       'BODY', 'responseDatetime', 'BODY', 'date',
       'DATETIME', 2, 'N', 'S'
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300002 AND WS_KEY = 'payments';

-- Consulta payments: campos de payment_operations.
INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO, CODIGO_BILLETERA, ID_WS,
    APP_SERVICE_KEY, APP_OPERATION, DIRECCION,
    SECCION_APP, ATRIBUTO_APP, SECCION_EXT, ATRIBUTO_EXT,
    TIPO_DATO, ORDEN_APLICACION, OBLIGATORIO, ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300002,
    WS.ID_WS,
    'payments',
    'DEUNA',
    'RESPONSE',
    'BODY',
    M.ATRIBUTO_APP,
    'BODY',
    M.ATRIBUTO_EXT,
    M.TIPO_DATO,
    M.ORDEN_APLICACION,
    'N',
    'S'
FROM (
    SELECT 'paymentOperation.creation_datetime' ATRIBUTO_APP,
           'date' ATRIBUTO_EXT, 'DATETIME' TIPO_DATO, 3 ORDEN_APLICACION
      FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.operation_id',
           'transactionId', 'STRING', 4 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.merchant_sales_id',
           'internalTransactionReference', 'STRING', 5 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.merchant_order_id',
           'internalTransactionReference', 'STRING', 6 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.payment_amount.value',
           'amount', 'NUMBER', 7 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.payment_amount.currency_code',
           'currency', 'STRING', 8 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.shopper_amount.value',
           'amount', 'NUMBER', 9 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.shopper_amount.currency_code',
           'currency', 'STRING', 10 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.additional_info',
           'description', 'STRING', 11 FROM DUAL
    UNION ALL
    SELECT 'paymentOperation.payment_reference_number',
           'transferNumber', 'STRING', 12 FROM DUAL
    UNION ALL
    SELECT 'paymentOperationActivity.creation_datetime',
           'date', 'DATETIME', 13 FROM DUAL
    UNION ALL
    SELECT 'paymentOperationActivity.status_code',
           'status', 'STRING', 14 FROM DUAL
    UNION ALL
    SELECT 'paymentOperationActivity.status_description',
           'status', 'STRING', 15 FROM DUAL
) M
CROSS JOIN TUKUNAFUNC.IN_PASARELA_WS WS
WHERE WS.CODIGO_BILLETERA = 300002
  AND WS.WS_KEY = 'payments';

COMMIT;

-- ============================================================================
-- FIN DE LA CONFIGURACION
--
-- Despues de ejecutar:
-- 1. Reiniciar tec-api-pagosdigitales para recargar las caches.
-- 2. Probar POST /api/v1/banks.
-- 3. Probar POST /api/v1/direct-online-payment-requests.
-- 4. Probar POST /api/v1/payments.
-- 5. Registrar en DEUNA el webhook:
--      {URL_PUBLICA_API}/api/v1/deuna/confirmation
--
-- No se incluye payment-refund porque el proyecto actual no tiene ese endpoint.
-- No se insertan mapeos ERROR porque DEUNA responde errores planos con
-- message/statusCode/errors y el codigo actual requiere una adaptacion Java.
-- ============================================================================
