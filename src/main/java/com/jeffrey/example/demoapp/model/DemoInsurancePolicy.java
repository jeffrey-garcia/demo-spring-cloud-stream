package com.jeffrey.example.demoapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DemoInsurancePolicy {

    @JsonProperty("policyId")
    private String policyId;

    @JsonProperty("policyHolder")
    private String policyHolder;

    public DemoInsurancePolicy(@JsonProperty("policyId")String policyId, @JsonProperty("policyHolder")String policyHolder) {
        this.policyId = policyId;
        this.policyHolder = policyHolder;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getPolicyHolder() {
        return policyHolder;
    }
}
