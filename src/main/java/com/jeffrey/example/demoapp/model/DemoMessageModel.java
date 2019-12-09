package com.jeffrey.example.demoapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DemoMessageModel {

    @JsonProperty("demoInsurancePolicy")
    private DemoInsurancePolicy demoInsurancePolicy;

    public DemoMessageModel(@JsonProperty("demoInsurancePolicy")DemoInsurancePolicy demoInsurancePolicy) {
        this.demoInsurancePolicy = demoInsurancePolicy;
    }

    public DemoInsurancePolicy getDemoInsurancePolicy() {
        return this.demoInsurancePolicy;
    }
}


