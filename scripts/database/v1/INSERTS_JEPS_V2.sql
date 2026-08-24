-- ============================================================================
-- CONFIGURACION JEPFASTER V2
-- Oracle / esquema TUKUNAFUNC
--
-- Reemplazar antes de ejecutar:
--   <USUARIO_JEP_PROD>
--   <PASSWORD_JEP_PROD>
--   <CODIGO_INSTITUCION_JEP_PROD>
--
-- Script para una carga inicial. No ejecutar dos veces.
-- ============================================================================

-- ==========================================================================
-- PASO 1. AGREGAR LA BILLETERA JEPFASTER
-- TIPO_BANCO=INTERNO permite obtener sus opciones desde AD_TIPO_PAGO.
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
    300001,
    3,
    'JEPFaster',
    'JEPFaster Billetera digital',
    'S',
    'N',
    SYSDATE,
    'INTERNO'
);

COMMIT;

-- ==========================================================================
-- PASO 2. AGREGAR EL TIPO DE PAGO JEP
-- CODIGO_ESTABLECIMIENTO=1 es el bank_id que debe enviarse para JEP.
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
    300001,
    'JEPQR',
    'S',
    4,
    2000,
    'S',
    'S',
    'S',
    'S',
    1,
    SYSDATE,
    5,
    0,
    '-'
FROM TUKUNAFUNC.AD_TIPO_PAGO;

COMMIT;

-- ==========================================================================
-- PASO 3. HABILITAR JEP EN LOS CANALES 1 Y 2
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
      WHERE CODIGO_BILLETERA_DIGITAL = 300001
        AND CODIGO_ESTABLECIMIENTO = 1)
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
      WHERE CODIGO_BILLETERA_DIGITAL = 300001
        AND CODIGO_ESTABLECIMIENTO = 1)
FROM DUAL;

COMMIT;

-- ==========================================================================
-- PASO 4. AGREGAR LOS HEADERS JEP
-- La ruta Camel exige al menos un header configurado para el proveedor.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.IN_PASARELA_HEADERS (
    ID_HEADER,
    CODIGO_BILLETERA,
    HEADER_NOMBRE,
    HEADER_VALOR
)
SELECT
    NVL(MAX(ID_HEADER), 0) + 1,
    300001,
    'Content-Type',
    'application/json'
FROM TUKUNAFUNC.IN_PASARELA_HEADERS;

INSERT INTO TUKUNAFUNC.IN_PASARELA_HEADERS (
    ID_HEADER,
    CODIGO_BILLETERA,
    HEADER_NOMBRE,
    HEADER_VALOR
)
SELECT
    NVL(MAX(ID_HEADER), 0) + 1,
    300001,
    'Accept',
    'application/json'
FROM TUKUNAFUNC.IN_PASARELA_HEADERS;

COMMIT;

-- ==========================================================================
-- PASO 5. AGREGAR LOS SERVICIOS JEP
-- payments se resuelve en IN_REGISTRO_PAGOS y no consume una URL externa.
-- getbanks se construye desde AD_TIPO_PAGO.
-- ==========================================================================

-- Generacion del QR JEP.
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
    300001,
    'direct-online-payment-requests',
    'S',
    'REST',
    'POST',
    'JSON',
    'http://192.168.100.130:8685/serviciosenlineaBaseETH/integracioncomercial/qr-generation-process'
);

-- Consulta interna del estado registrado por el webhook JEP.
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
    300001,
    'payments',
    'S',
    'REST',
    'GET',
    'PARAMETROS',
    'INTERNO'
);

-- Catalogo local de bancos/tipos de pago.
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
    300001,
    'getbanks',
    'S',
    'REST',
    'GET',
    'PARAMETROS',
    'INTERNO'
);

COMMIT;

-- ==========================================================================
-- PASO 6. AGREGAR LAS CREDENCIALES DEL REQUEST JEP
-- Se agregan automaticamente al body de generacion del QR.
-- ==========================================================================

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
    'nombreUsuario',
    '<USUARIO_JEP_PROD>',
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300001
  AND WS_KEY = 'direct-online-payment-requests';

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
    'contrasena',
    '<PASSWORD_JEP_PROD>',
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300001
  AND WS_KEY = 'direct-online-payment-requests';

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
    'codigoInstitucion',
    '<CODIGO_INSTITUCION_JEP_PROD>',
    'DEFAULTS',
    NULL
FROM TUKUNAFUNC.IN_PASARELA_WS
WHERE CODIGO_BILLETERA = 300001
  AND WS_KEY = 'direct-online-payment-requests';

COMMIT;

-- ==========================================================================
-- PASO 7. MAPEOS DEL REQUEST JEP
-- APP_OPERATION debe ser JEPFASTER porque ese es el nombre resuelto por Java.
-- monto se envia como STRING conforme al contrato de JEP.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO,
    CODIGO_BILLETERA,
    ID_WS,
    APP_SERVICE_KEY,
    APP_OPERATION,
    DIRECCION,
    SECCION_APP,
    ATRIBUTO_APP,
    SECCION_EXT,
    ATRIBUTO_EXT,
    TIPO_DATO,
    ORDEN_APLICACION,
    OBLIGATORIO,
    ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300001,
    WS.ID_WS,
    'direct-online-payment-requests',
    'JEPFASTER',
    'REQUEST',
    'BODY',
    M.ATRIBUTO_APP,
    'BODY',
    M.ATRIBUTO_EXT,
    'STRING',
    M.ORDEN_APLICACION,
    M.OBLIGATORIO,
    'S'
FROM (
    SELECT 'sales_amount.value' ATRIBUTO_APP,
           'monto' ATRIBUTO_EXT, 1 ORDEN_APLICACION, 'S' OBLIGATORIO
      FROM DUAL
    UNION ALL
    SELECT 'merchant_sales_id', 'codigoTransaccion', 2, 'S' FROM DUAL
    UNION ALL
    SELECT 'store_name', 'nombreSucursal', 3, 'S' FROM DUAL
    UNION ALL
    SELECT 'member_id', 'identificacionSocio', 4, 'N' FROM DUAL
    UNION ALL
    SELECT 'city', 'ciudad', 5, 'N' FROM DUAL
    UNION ALL
    SELECT 'store_address', 'direccionSucursal', 6, 'N' FROM DUAL
    UNION ALL
    SELECT 'session_id', 'identificadorsesion', 7, 'N' FROM DUAL
) M
CROSS JOIN TUKUNAFUNC.IN_PASARELA_WS WS
WHERE WS.CODIGO_BILLETERA = 300001
  AND WS.WS_KEY = 'direct-online-payment-requests';

COMMIT;

-- ==========================================================================
-- PASO 8. MAPEOS DEL RESPONSE JEP
-- data.qr contiene el QR base64 que el mapper coloca en payment_locations.
-- ==========================================================================

INSERT INTO TUKUNAFUNC.AD_MAPEO_SERVICIOS (
    ID_MAPEO_SERVICIO,
    CODIGO_BILLETERA,
    ID_WS,
    APP_SERVICE_KEY,
    APP_OPERATION,
    DIRECCION,
    SECCION_APP,
    ATRIBUTO_APP,
    SECCION_EXT,
    ATRIBUTO_EXT,
    TIPO_DATO,
    ORDEN_APLICACION,
    OBLIGATORIO,
    ACTIVO
)
SELECT
    SEQ_AD_MAPEO_SERVICIOS.NEXTVAL,
    300001,
    WS.ID_WS,
    'direct-online-payment-requests',
    'JEPFASTER',
    'RESPONSE',
    'BODY',
    M.ATRIBUTO_APP,
    'BODY',
    M.ATRIBUTO_EXT,
    'STRING',
    M.ORDEN_APLICACION,
    'N',
    'S'
FROM (
    SELECT 'operationId' ATRIBUTO_APP,
           'codigoTransaccion' ATRIBUTO_EXT, 1 ORDEN_APLICACION
      FROM DUAL
    UNION ALL
    SELECT 'transactionId', 'codigoTransaccion', 2 FROM DUAL
    UNION ALL
    SELECT 'bankRedirectUrl', 'data.qr', 3 FROM DUAL
) M
CROSS JOIN TUKUNAFUNC.IN_PASARELA_WS WS
WHERE WS.CODIGO_BILLETERA = 300001
  AND WS.WS_KEY = 'direct-online-payment-requests';

COMMIT;

-- ============================================================================
-- FIN
--
-- No se agregan mapeos para payments porque JEP se consulta internamente en
-- IN_REGISTRO_PAGOS.
--
-- No se agrega configuracion para /api/v1/jep/notifyPayment porque es un
-- webhook entrante y no consume un servicio externo.
--
-- No se agrega DIRECCION=ERROR porque JEP devuelve "errores" como arreglo y
-- el manejador generico actual espera un objeto ErrorInfo.
--
-- Reiniciar tec-api-pagosdigitales despues de ejecutar para recargar caches.
-- ============================================================================
