package com.femsa.gpf.pagosdigitales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.femsa.gpf.pagosdigitales.infrastructure.config.ExternalServiceHttpProperties;

class ExternalServiceHttpPropertiesTest {

    @Test
    void buildEndpointSuffixUsesDefaultTimeoutOfThirtySeconds() {
        ExternalServiceHttpProperties properties = new ExternalServiceHttpProperties();

        assertThat(properties.getTimeout()).isEqualTo(30000L);
        assertThat(properties.buildEndpointSuffix("https://example.com/payments"))
                .isEqualTo("?throwExceptionOnFailure=false"
                        + "&connectionRequestTimeout=30000"
                        + "&connectTimeout=30000"
                        + "&responseTimeout=30000"
                        + "&soTimeout=30000");
    }

    @Test
    void setTimeoutRejectsZeroOrNegativeValues() {
        ExternalServiceHttpProperties properties = new ExternalServiceHttpProperties();

        assertThatThrownBy(() -> properties.setTimeout(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("integration.external-http.timeout");
    }
}
