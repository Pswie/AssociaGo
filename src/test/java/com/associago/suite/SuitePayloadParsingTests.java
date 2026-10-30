package com.associago.suite;

import com.associago.app.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP binding with isolated service mocks; malformed inputs must never reach a write. */
@ExtendWith(MockitoExtension.class)
class SuitePayloadParsingTests {
    private static final String ID = "22222222-2222-4222-8222-222222222222";
    private static final String PATH = "/api/integrations/v1/peer/projects/" + ID;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @Mock private SuiteAccess access;
    @Mock private SuiteLocalProjectService projects;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new SuitePeerController(access, projects))
                .setControllerAdvice(new SuiteExceptionHandler(), new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.5", "1e0", "\"1\"", "true", "{}", "[]", "2147483648"})
    void schemaVersionDoesNotCoerceOtherJsonTypes(String token) throws Exception {
        ObjectNode body = projection();
        body.set("schemaVersion", mapper.readTree(token));
        invalid(mapper.writeValueAsString(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "1.9", "1e0", "\"1\"", "true", "{}", "[]", "9223372036854775808"})
    void sourceVersionDoesNotTruncateOrCoerceOtherJsonTypes(String token) throws Exception {
        ObjectNode body = projection();
        body.set("sourceVersion", mapper.readTree(token));
        invalid(mapper.writeValueAsString(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "100.25", "1e2", "true", "{}", "[]"})
    void monetaryAmountMustArriveAsAString(String token) throws Exception {
        ObjectNode body = projection();
        ((ObjectNode) body.get("plannedBudget")).set("amount", mapper.readTree(token));
        invalid(mapper.writeValueAsString(body));
    }

    @Test
    void canonicalIntegerVersionAndDecimalStringBindWithoutChangingPrecision() throws Exception {
        when(projects.upsert(eq(ID), any())).thenReturn(Map.of("accepted", true));
        mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(projection())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(true));
        ArgumentCaptor<SuitePayloads.Projection> body = ArgumentCaptor.forClass(SuitePayloads.Projection.class);
        verify(projects).upsert(eq(ID), body.capture());
        assertThat(body.getValue().schemaVersion()).isEqualTo(1);
        assertThat(body.getValue().sourceVersion()).isZero();
        assertThat(body.getValue().plannedBudget().amount()).isEqualTo("100.2500");
    }

    @Test
    void malformedJsonReturns400WithoutPrivateParserDetails() throws Exception {
        invalid("{\"sourceVersion\":1,\"private\":\"C:\\secret-password");
    }

    @Test
    void annotationsDoNotChangeLegacyMapperCoercion() throws Exception {
        assertThat(mapper.readValue("{\"version\":1.9,\"amount\":100}", LegacyShape.class))
                .isEqualTo(new LegacyShape(1L, "100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"optimistic", "write-lock", "unique"})
    void concurrentDatabaseConflictsReturnRetryableContractErrorsWithoutSqlDetails(String kind) throws Exception {
        String privateDetail = "SQL private_table / secret-password";
        RuntimeException failure = switch (kind) {
            case "optimistic" -> new org.springframework.dao.OptimisticLockingFailureException(privateDetail);
            case "write-lock" -> new org.springframework.dao.CannotAcquireLockException(privateDetail);
            default -> new org.springframework.dao.DataIntegrityViolationException(privateDetail);
        };
        when(projects.upsert(eq(ID), any())).thenThrow(failure);
        String response = mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(projection())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").isString())
                .andExpect(jsonPath("$.message").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(response).size()).isEqualTo(2);
        assertThat(response).doesNotContain("SQL", "private_table", "secret-password");
    }

    private void invalid(String body) throws Exception {
        String result = mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"))
                .andExpect(jsonPath("$.message").isString())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(result).size()).isEqualTo(2);
        assertThat(result).doesNotContain("secret-password", "JsonMappingException", "C:\\");
        verifyNoInteractions(projects, access);
    }

    private ObjectNode projection() {
        ObjectNode body = mapper.createObjectNode().put("schemaVersion", 1)
                .put("organizationId", "11111111-1111-4111-8111-111111111111")
                .put("suiteProjectId", ID).put("sourceVersion", 0)
                .put("title", "Strict project").putNull("description")
                .putNull("projectDeadline").putNull("targetLocalId");
        body.putObject("plannedBudget").put("amount", "100.2500").put("currency", "EUR");
        body.putObject("links").putNull("librepm").putNull("associago").putNull("bandi");
        return body;
    }

    private record LegacyShape(Long version, String amount) { }
}
