package com.associago.suite;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDate;
import java.util.Map;

public final class SuitePayloads {
    private SuitePayloads() { }

    public record Money(@JsonDeserialize(using = SuiteJson.StrictString.class) String amount, String currency) { }
    public record Projection(@JsonDeserialize(using = SuiteJson.StrictInteger.class) Integer schemaVersion,
                             String organizationId, String suiteProjectId,
                             @JsonDeserialize(using = SuiteJson.StrictLong.class) Long sourceVersion,
                             String title, String description, Money plannedBudget,
                             LocalDate projectDeadline, String targetLocalId, Map<String, String> links) { }
    public record LocalLink(String localId) { }
}
