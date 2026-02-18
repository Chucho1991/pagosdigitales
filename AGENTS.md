# AGENTS.md

## Alcance
Este archivo define lineamientos para trabajo de backend.

## Arquitectura y estructura
- Mantener arquitectura hexagonal: `domain`, `application`, `ports`, `adapters`.
- No usar entidades JPA dentro de `domain`; mapear de forma explicita.
- Ubicar DTOs de entrada/salida en `adapters/in`.
- Separar contratos (`ports/in`, `ports/out`) de implementaciones.

## Reglas de implementacion backend
- Validar entradas con `jakarta.validation`.
- Exponer endpoints REST claros y consistentes (nombres, codigos HTTP, errores).
- Aplicar seguridad por rol en cada endpoint nuevo o modificado.
- Usar soft delete por defecto cuando el modulo lo requiera.
- Si el modulo define solo eliminacion logica, no implementar borrado fisico.
- Documentar endpoints en Swagger/OpenAPI.

## Documentacion JavaDoc obligatoria
Generar documentacion del proyecto usando nomenclatura JavaDoc.

- Documentar clases, metodos publicos y DTOs.
- Incluir `@param`, `@return`, `@throws` cuando aplique.
- Mantener descripciones breves y claras.
- Aplicar JavaDoc solo a metodos con entradas y salidas.
- Si hay inclusion o modificacion relevante de endpoint, actualizar `README.md`.

## Pruebas unitarias
- Generar pruebas unitarias para cada endpoint nuevo.
- Si un endpoint existente cambia, ajustar pruebas a la nueva estructura.
- Cubrir al menos casos exitosos, validaciones y errores de negocio.

## Flujo minimo para nuevos endpoints
1. Definir caso de uso y puertos (`application`, `ports/in`, `ports/out`).
2. Implementar adaptadores (`adapters/in/rest`, `adapters/out/persistence`).
3. Agregar validaciones y seguridad por rol.
4. Documentar en Swagger y JavaDoc.
5. Actualizar `README.md` (si cambia contrato API).
6. Crear/ajustar pruebas unitarias.

## Checklist de cierre (backend)
- [ ] Endpoint implementado con arquitectura hexagonal.
- [ ] DTOs y validaciones aplicadas.
- [ ] Seguridad por rol aplicada.
- [ ] Manejo de errores y codigos HTTP correcto.
- [ ] JavaDoc completo segun reglas.
- [ ] Swagger/OpenAPI actualizado.
- [ ] `README.md` actualizado (si hubo cambio de endpoint).
- [ ] Pruebas unitarias nuevas/actualizadas y pasando.
