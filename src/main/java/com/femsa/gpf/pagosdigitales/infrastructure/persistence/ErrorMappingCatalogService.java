package com.femsa.gpf.pagosdigitales.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.femsa.gpf.pagosdigitales.api.dto.ErrorInfo;
import com.femsa.gpf.pagosdigitales.api.dto.ErrorInnerDetail;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;

/**
 * Servicio de catalogo para mapear errores de proveedor desde AD_MAPEO_ERRORES.
 */
@Log4j2
@Service
public class ErrorMappingCatalogService {

    private static final String SELECT_ERROR_MAPPINGS = "SELECT HTTP_STATUS, ERROR_CATEGORY, "
            + "INNER_DETAILS_FIELD_MESSAGE, INNER_DETAILS_FIELD_MESSAGE_ES, CURRENT_ERROR_CODE, "
            + "CURRENT_ERROR_MESSAGE, CURRENT_ERROR_MESSAGE_ES "
            + "FROM TUKUNAFUNC.AD_MAPEO_ERRORES";

    private final DatabaseExecutor databaseExecutor;
    private volatile ErrorCatalog catalog = ErrorCatalog.empty();

    /**
     * Crea el servicio con configuracion de conexion.
     *
     * @param databaseExecutor ejecutor global de conexiones JDBC
     */
    public ErrorMappingCatalogService(DatabaseExecutor databaseExecutor) {
        this.databaseExecutor = databaseExecutor;
    }

    /**
     * Inicializa cache al arranque.
     */
    @PostConstruct
    public void initCache() {
        refreshCache();
    }

    /**
     * Refresca la cache del catalogo cada 6 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void refreshCache() {
        try {
            List<ErrorMappingEntry> loaded = loadMappingsFromDb();
            this.catalog = ErrorCatalog.of(loaded);
            log.info("Cache de AD_MAPEO_ERRORES actualizada. Registros cargados: {}", loaded.size());
        } catch (Exception e) {
            log.error("No fue posible refrescar cache AD_MAPEO_ERRORES. Se conserva cache anterior.", e);
        }
    }

    /**
     * Aplica el catalogo de mapeo a un error de proveedor.
     * Prioriza mensaje en espanol; si no existe, usa mensaje en ingles.
     *
     * @param providerError error recibido del proveedor
     * @return error normalizado con mapeo aplicado
     */
    public ErrorInfo mapProviderError(ErrorInfo providerError) {
        if (providerError == null) {
            return null;
        }

        ErrorMappingEntry topLevelEntry = resolveTopLevelMapping(providerError);
        ErrorInfo mapped = new ErrorInfo();
        mapped.setHttp_code(topLevelEntry != null && topLevelEntry.httpStatus() != null
                ? topLevelEntry.httpStatus()
                : providerError.getHttp_code());
        mapped.setCode(resolveCode(providerError.getCode(), topLevelEntry));
        mapped.setCategory(resolvePreferredValue(
                topLevelEntry == null ? null : topLevelEntry.errorCategory(),
                providerError.getCategory()));
        mapped.setMessage(resolvePreferredValue(
                topLevelEntry == null ? null : topLevelEntry.currentErrorMessageEs(),
                topLevelEntry == null ? null : topLevelEntry.currentErrorMessageEn(),
                providerError.getMessage()));
        mapped.setInformation_link(providerError.getInformation_link());
        mapped.setInner_details(mapInnerDetails(providerError.getInner_details(), mapped.getCategory()));
        return mapped;
    }

    /**
     * Construye un error normalizado a partir del codigo actual configurado en catalogo.
     *
     * @param currentErrorCode codigo actual de error
     * @return error normalizado si existe en catalogo
     */
    public ErrorInfo buildErrorByCurrentCode(long currentErrorCode) {
        ErrorMappingEntry entry = catalog.findByCode(currentErrorCode);
        if (entry == null) {
            return null;
        }

        ErrorInfo error = new ErrorInfo();
        error.setHttp_code(entry.httpStatus());
        error.setCode(String.valueOf(entry.currentErrorCode()));
        error.setCategory(entry.errorCategory());
        error.setMessage(resolvePreferredValue(entry.currentErrorMessageEs(), entry.currentErrorMessageEn()));
        error.setInformation_link(null);
        error.setInner_details(buildDefaultInnerDetails(entry));
        return error;
    }

    private List<ErrorInnerDetail> buildDefaultInnerDetails(ErrorMappingEntry entry) {
        String fieldMessage = resolvePreferredValue(entry.innerDetailsMessageEs(), entry.innerDetailsMessageEn());
        if (fieldMessage == null) {
            return Collections.emptyList();
        }

        ErrorInnerDetail detail = new ErrorInnerDetail();
        detail.setInner_code(entry.currentErrorCode() == null ? null : String.valueOf(entry.currentErrorCode()));
        detail.setField(null);
        detail.setField_value(null);
        detail.setField_message(fieldMessage);
        return List.of(detail);
    }

    private List<ErrorInnerDetail> mapInnerDetails(List<ErrorInnerDetail> details, String category) {
        if (details == null) {
            return null;
        }

        List<ErrorInnerDetail> mapped = new ArrayList<>(details.size());
        for (ErrorInnerDetail detail : details) {
            if (detail == null) {
                mapped.add(null);
                continue;
            }
            ErrorMappingEntry entry = catalog.findByInnerMessage(category, detail.getField_message());
            ErrorInnerDetail mappedDetail = new ErrorInnerDetail();
            mappedDetail.setInner_code(detail.getInner_code());
            mappedDetail.setField(detail.getField());
            mappedDetail.setField_value(detail.getField_value());
            mappedDetail.setField_message(resolvePreferredValue(
                    entry == null ? null : entry.innerDetailsMessageEs(),
                    entry == null ? null : entry.innerDetailsMessageEn(),
                    detail.getField_message()));
            mapped.add(mappedDetail);
        }
        return mapped;
    }

    private ErrorMappingEntry resolveTopLevelMapping(ErrorInfo providerError) {
        Long code = parseCode(providerError.getCode());
        if (code != null) {
            ErrorMappingEntry byCode = catalog.findByCode(code);
            if (byCode != null) {
                return byCode;
            }
        }
        return catalog.findByCurrentMessage(providerError.getCategory(), providerError.getMessage());
    }

    private String resolveCode(String originalCode, ErrorMappingEntry topLevelEntry) {
        if (topLevelEntry == null || topLevelEntry.currentErrorCode() == null) {
            return originalCode;
        }
        return String.valueOf(topLevelEntry.currentErrorCode());
    }

    private List<ErrorMappingEntry> loadMappingsFromDb() throws Exception {
        List<ErrorMappingEntry> entries = new ArrayList<>();
        databaseExecutor.withConnection((DatabaseExecutor.ConnectionConsumer) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SELECT_ERROR_MAPPINGS);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new ErrorMappingEntry(
                            readInteger(rs, "HTTP_STATUS"),
                            trimToNull(rs.getString("ERROR_CATEGORY")),
                            trimToNull(rs.getString("INNER_DETAILS_FIELD_MESSAGE")),
                            trimToNull(rs.getString("INNER_DETAILS_FIELD_MESSAGE_ES")),
                            readLong(rs, "CURRENT_ERROR_CODE"),
                            trimToNull(rs.getString("CURRENT_ERROR_MESSAGE")),
                            trimToNull(rs.getString("CURRENT_ERROR_MESSAGE_ES"))));
                }
            }
        });
        return entries;
    }

    private Integer readInteger(ResultSet rs, String column) throws Exception {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.intValue();
    }

    private Long readLong(ResultSet rs, String column) throws Exception {
        Number value = (Number) rs.getObject(column);
        return value == null ? null : value.longValue();
    }

    private Long parseCode(String code) {
        String sanitized = trimToNull(code);
        if (sanitized == null || !sanitized.matches("\\d+")) {
            return null;
        }
        try {
            return Long.parseLong(sanitized);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String resolvePreferredValue(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            String normalized = trimToNull(candidate);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private static String normalizeText(String text) {
        String value = trimToNull(text);
        if (value == null) {
            return null;
        }
        return value.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Registro de mapeo de error.
     *
     * @param httpStatus codigo HTTP
     * @param errorCategory categoria de error
     * @param innerDetailsMessageEn mensaje de detalle en ingles
     * @param innerDetailsMessageEs mensaje de detalle en espanol
     * @param currentErrorCode codigo actual de error
     * @param currentErrorMessageEn mensaje principal en ingles
     * @param currentErrorMessageEs mensaje principal en espanol
     */
    public record ErrorMappingEntry(
            Integer httpStatus,
            String errorCategory,
            String innerDetailsMessageEn,
            String innerDetailsMessageEs,
            Long currentErrorCode,
            String currentErrorMessageEn,
            String currentErrorMessageEs) {
    }

    private static final class ErrorCatalog {

        private final Map<Long, ErrorMappingEntry> byCode;
        private final Map<String, ErrorMappingEntry> byCurrentMessageByCategory;
        private final Map<String, ErrorMappingEntry> byCurrentMessage;
        private final Map<String, ErrorMappingEntry> byInnerMessageByCategory;
        private final Map<String, ErrorMappingEntry> byInnerMessage;

        private ErrorCatalog(Map<Long, ErrorMappingEntry> byCode,
                Map<String, ErrorMappingEntry> byCurrentMessageByCategory,
                Map<String, ErrorMappingEntry> byCurrentMessage,
                Map<String, ErrorMappingEntry> byInnerMessageByCategory,
                Map<String, ErrorMappingEntry> byInnerMessage) {
            this.byCode = byCode;
            this.byCurrentMessageByCategory = byCurrentMessageByCategory;
            this.byCurrentMessage = byCurrentMessage;
            this.byInnerMessageByCategory = byInnerMessageByCategory;
            this.byInnerMessage = byInnerMessage;
        }

        static ErrorCatalog empty() {
            return new ErrorCatalog(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        static ErrorCatalog of(List<ErrorMappingEntry> entries) {
            Map<Long, ErrorMappingEntry> byCode = new LinkedHashMap<>();
            Map<String, ErrorMappingEntry> byCurrentMessageByCategory = new LinkedHashMap<>();
            Map<String, ErrorMappingEntry> byCurrentMessage = new LinkedHashMap<>();
            Map<String, ErrorMappingEntry> byInnerMessageByCategory = new LinkedHashMap<>();
            Map<String, ErrorMappingEntry> byInnerMessage = new LinkedHashMap<>();

            for (ErrorMappingEntry entry : entries) {
                if (entry.currentErrorCode() != null) {
                    byCode.putIfAbsent(entry.currentErrorCode(), entry);
                }
                String currentMessageKey = normalizeText(entry.currentErrorMessageEn());
                String innerMessageKey = normalizeText(entry.innerDetailsMessageEn());
                String category = normalizeText(entry.errorCategory());

                if (currentMessageKey != null) {
                    byCurrentMessage.putIfAbsent(currentMessageKey, entry);
                    if (category != null) {
                        byCurrentMessageByCategory.putIfAbsent(category + "|" + currentMessageKey, entry);
                    }
                }

                if (innerMessageKey != null) {
                    byInnerMessage.putIfAbsent(innerMessageKey, entry);
                    if (category != null) {
                        byInnerMessageByCategory.putIfAbsent(category + "|" + innerMessageKey, entry);
                    }
                }
            }

            return new ErrorCatalog(
                    Collections.unmodifiableMap(byCode),
                    Collections.unmodifiableMap(byCurrentMessageByCategory),
                    Collections.unmodifiableMap(byCurrentMessage),
                    Collections.unmodifiableMap(byInnerMessageByCategory),
                    Collections.unmodifiableMap(byInnerMessage));
        }

        ErrorMappingEntry findByCode(Long code) {
            if (code == null) {
                return null;
            }
            return byCode.get(code);
        }

        ErrorMappingEntry findByCurrentMessage(String category, String messageEn) {
            String messageKey = normalizeText(messageEn);
            if (messageKey == null) {
                return null;
            }
            String categoryKey = normalizeText(category);
            if (categoryKey != null) {
                ErrorMappingEntry byCategory = byCurrentMessageByCategory.get(categoryKey + "|" + messageKey);
                if (byCategory != null) {
                    return byCategory;
                }
            }
            return byCurrentMessage.get(messageKey);
        }

        ErrorMappingEntry findByInnerMessage(String category, String messageEn) {
            String messageKey = normalizeText(messageEn);
            if (messageKey == null) {
                return null;
            }
            String categoryKey = normalizeText(category);
            if (categoryKey != null) {
                ErrorMappingEntry byCategory = byInnerMessageByCategory.get(categoryKey + "|" + messageKey);
                if (byCategory != null) {
                    return byCategory;
                }
            }
            return byInnerMessage.get(messageKey);
        }
    }
}
