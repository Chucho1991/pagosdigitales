# ADR 0001: Arquitectura en capas con Apache Camel

## Estado
Aceptado

## Contexto
PagosDigitales requiere exponer una API unificada de pagos y orquestar llamadas a proveedores externos con configuraciones variables (URLs, métodos, headers y mapeos de campos). Además, se necesita un mecanismo de seguridad específico para confirmaciones SafetyPay con firma y validaciones.

## Decisión
Adoptar una **arquitectura en capas** sobre Spring Boot con:
- **API** como adaptador de entrada (controllers y DTOs).
- **Application** para orquestación y mapeo de payloads.
- **Domain** con servicios de negocio puros (firma, resolución de proveedores).
- **Infrastructure** como adaptador de salida, utilizando **Apache Camel** para rutas dinámicas configuradas por proveedor.

## Consecuencias
- Facilita añadir nuevos proveedores vía configuración sin modificar la API pública.
- Mantiene aisladas las reglas de negocio del transporte y configuración.
- Introduce dependencia en Camel y su configuración dinámica.

## Alternativas consideradas
1. Integraciones directas vía RestTemplate/WebClient por proveedor: descartado por acoplamiento y mayor complejidad de mapeo.
2. Microservicios por proveedor: descartado por sobrecosto operativo para el alcance actual.
