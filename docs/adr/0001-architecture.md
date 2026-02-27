# ADR 0001: Arquitectura en capas con Apache Camel

## Estado
Aceptado

## Contexto
PagosDigitales requiere exponer una API unificada de pagos y orquestar llamadas a proveedores externos con configuraciones variables (URL, metodo HTTP, headers y mapeos de campos). La conectividad externa se administra en BD por `payment_provider_code`, usando `IN_PASARELA_WS` (URL/metodo por `WS_KEY`), `IN_PASARELA_HEADERS` (headers por proveedor) e `IN_PASARELA_WS_DEFS` (parametros/query/defaults por `ID_WS` y `TIPO_DEF`).

## Decision
Adoptar una arquitectura en capas sobre Spring Boot con:
- API como adaptador de entrada (controllers y DTOs).
- Application para orquestacion y mapeo de payloads.
- Domain con servicios de negocio puros (firma, resolucion de proveedores).
- Infrastructure como adaptador de salida, usando Apache Camel para rutas dinamicas y servicios de persistencia para resolver configuracion externa desde BD.

## Consecuencias
- Facilita agregar nuevos proveedores por configuracion sin cambiar la API publica.
- Centraliza la configuracion de conectividad externa en BD.
- Mantiene aisladas las reglas de negocio del transporte.
- Introduce dependencia de Camel y de la disponibilidad de las tablas de configuracion.

## Alternativas consideradas
1. Integraciones directas con RestTemplate/WebClient por proveedor: descartado por acoplamiento y mayor complejidad de mantenimiento.
2. Microservicios por proveedor: descartado por sobrecosto operativo para el alcance actual.
