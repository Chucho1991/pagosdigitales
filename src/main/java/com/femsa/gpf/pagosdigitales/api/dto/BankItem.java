package com.femsa.gpf.pagosdigitales.api.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class BankItem {
    private String bank_id;
    private String bank_name;
    private String bank_commercial_name;
    private String bank_country_code;
    private String bank_type;
    private boolean show_standalone;
    private String channel;
    private String channel_tag;
    private Integer status;
    private Integer access_type;
    private String language_code;
    private String working_hours;
    private String disclaimer;
    private String default_currency_code;
    private String locations_url;
    private List<Map<String, Object>> amount_limits;
}