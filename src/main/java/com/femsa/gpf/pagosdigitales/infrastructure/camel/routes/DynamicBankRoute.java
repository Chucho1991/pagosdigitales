package com.femsa.gpf.pagosdigitales.infrastructure.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.femsa.gpf.pagosdigitales.infrastructure.config.GetBanksProperties;
import com.femsa.gpf.pagosdigitales.infrastructure.config.ProvidersPayProperties;


@Component
public class DynamicBankRoute extends RouteBuilder {

    private final GetBanksProperties getBanksProps;
    private final ProvidersPayProperties providersPayProps;

    public DynamicBankRoute(GetBanksProperties getBanksProps,
                            ProvidersPayProperties providersPayProps) {
        this.getBanksProps = getBanksProps;
        this.providersPayProps = providersPayProps;
    }

    @Override
    public void configure() {

        from("direct:getbanks")
            .process(exchange -> {
                String provider = exchange.getIn().getHeader("getbanks", String.class);

                var cfg = getBanksProps.getProviders().get(provider);
                if (cfg == null || !cfg.isEnabled()) {
                    throw new IllegalArgumentException("Proveedor no habilitado: " + provider);
                }

                // Ejemplo: leer código numérico
                Integer code = providersPayProps.getCodes().get(provider);

                // Construcción de la URL
                StringBuilder sb = new StringBuilder(cfg.getUrl());

                if (cfg.getQuery() != null) {
                    sb.append("?");
                    cfg.getQuery().forEach((k, v) -> {
                        String value = v
                                .replace("${country_code}", exchange.getProperty("country_code", "ECU", null))
                                .replace("${now}", java.time.LocalDateTime.now().toString());
                        sb.append(k).append("=").append(value).append("&");
                    });
                    sb.deleteCharAt(sb.length() - 1);
                }

                exchange.setProperty("url", sb.toString());

                exchange.getIn().setHeader("code", code);

            })
            .toD("${exchangeProperty.url}");
    }
}
