package com.associago.suite;

import com.associago.project.Project;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SuiteUserControllerTests {
    private static final String ID = "22222222-2222-4222-8222-222222222222";
    private final SuiteSettings settings = mock(SuiteSettings.class);
    private final SuiteAccess access = mock(SuiteAccess.class);
    private final SuiteGateway gateway = mock(SuiteGateway.class);
    private final SuiteLocalProjectService local = mock(SuiteLocalProjectService.class);
    private final SuiteUserController controller = new SuiteUserController(settings, access, gateway, local);

    @Test
    void resharingAnExistingProjectionReconcilesItsInstanceWithoutSeedingAnotherPlan() throws Exception {
        Project project = new Project(); project.setId(17L);
        SuiteProjectLink link = new SuiteProjectLink(); link.setSuiteProjectId(ID); link.setProjectId(17L);
        when(local.local(17L)).thenReturn(project);
        when(local.linkedProject(17L)).thenReturn(Optional.of(link));
        var context = new ObjectMapper().readTree("{\"suiteProjectId\":\"" + ID + "\"}");
        when(gateway.request(eq("POST"), eq("/" + ID + "/instances"), any())).thenReturn(context);
        when(gateway.userView(eq(context), eq(false))).thenReturn(context);
        assertThat(controller.share(new SuitePayloads.LocalLink("17"), null)).isEqualTo(context);
        verify(gateway, never()).request(eq("POST"), eq(""), any());
        verify(local, never()).linkExisting(any(), anyLong());
    }

    @Test
    void rejectsLocalRebindingBeforeAnyRemoteMutation() {
        Project project = new Project(); project.setId(17L);
        when(local.local(17L)).thenReturn(project);
        doThrow(new SuiteException(409, "LINK_CONFLICT", "Already linked"))
                .when(local).checkLinkTarget(ID, 17L);
        assertThatThrownBy(() -> controller.link(ID, new SuitePayloads.LocalLink("17"), null)).isInstanceOf(SuiteException.class);
        verifyNoInteractions(gateway);
    }
}
