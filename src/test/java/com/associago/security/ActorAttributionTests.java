package com.associago.security;

import com.associago.app.exception.GlobalExceptionHandler;
import com.associago.association.AssociationService;
import com.associago.balance.AnnualBalance;
import com.associago.balance.AnnualBalanceController;
import com.associago.balance.AnnualBalanceService;
import com.associago.budget.Budget;
import com.associago.budget.BudgetController;
import com.associago.budget.BudgetService;
import com.associago.communication.Communication;
import com.associago.communication.CommunicationController;
import com.associago.communication.CommunicationService;
import com.associago.shared.SignatureResolver;
import com.associago.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ActorAttributionTests {
    @Mock private ClerkIdentityService identityService;
    @Mock private BudgetService budgetService;
    @Mock private AnnualBalanceService balanceService;
    @Mock private AssociationService associationService;
    @Mock private SignatureResolver signatureResolver;
    @Mock private CommunicationService communicationService;
    private MockEnvironment environment;
    private ActorResolver actorResolver;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        environment.setActiveProfiles("cloud");
        actorResolver = new ActorResolver(identityService, environment);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new BudgetController(budgetService, actorResolver),
                new AnnualBalanceController(balanceService, associationService, signatureResolver, actorResolver),
                new CommunicationController(communicationService, actorResolver))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/budgets/1/approve, approvedBy",
        "/api/v1/balances/1/approve, approvedBy",
        "/api/v1/communications/1/send, sentBy"
    })
    void forgedActorIsForbiddenBeforeAnyBusinessAction(String path, String parameter) throws Exception {
        stubLinkedIdentity();

        mockMvc.perform(post(path).principal(jwt()).param(parameter, "999"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(budgetService, balanceService, communicationService);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/budgets/1/approve, approvedBy",
        "/api/v1/balances/1/approve, approvedBy",
        "/api/v1/communications/1/send, sentBy"
    })
    void unlinkedAccountCannotAttributeActionToSuppliedId(String path, String parameter) throws Exception {
        when(identityService.resolve(any())).thenReturn(Optional.empty());

        mockMvc.perform(post(path).principal(jwt()).param(parameter, "42"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(budgetService, balanceService, communicationService);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/budgets/1/approve, budget",
        "/api/v1/balances/1/approve, balance",
        "/api/v1/communications/1/send, communication"
    })
    void cloudActionUsesResolvedActorWithoutClientSuppliedId(String path, String action) throws Exception {
        stubLinkedIdentity();
        stubAction(action, 42L);

        mockMvc.perform(post(path).principal(jwt())).andExpect(status().isOk());

        verifyAction(action, 42L);
    }

    @ParameterizedTest
    @CsvSource({
        "/api/v1/budgets/1/approve, approvedBy, budget",
        "/api/v1/balances/1/approve, approvedBy, balance",
        "/api/v1/communications/1/send, sentBy, communication"
    })
    void desktopKeepsExplicitActorWithoutClerk(String path, String parameter, String action) throws Exception {
        environment.setActiveProfiles("desktop");
        stubAction(action, 7L);

        mockMvc.perform(post(path).param(parameter, "7")).andExpect(status().isOk());

        verifyAction(action, 7L);
        verifyNoInteractions(identityService);
    }

    @Test
    void unauthenticatedJwtCannotUseSuppliedActor() {
        JwtAuthenticationToken authentication = jwt();
        authentication.setAuthenticated(false);

        assertThatThrownBy(() -> actorResolver.resolve(authentication, 42L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(identityService);
    }

    @Test
    void cloudWithoutJwtCannotFallBackToClientActor() {
        assertThatThrownBy(() -> actorResolver.resolve(null, 42L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(identityService);
    }

    @Test
    void optionalDesktopCommunicationActorRemainsOptional() {
        environment.setActiveProfiles("desktop");

        assertThat(actorResolver.resolve(null, null)).isNull();
        verifyNoInteractions(identityService);
    }

    @Test
    void desktopApprovalStillRequiresAnExplicitActor() {
        environment.setActiveProfiles("desktop");

        assertThatThrownBy(() -> actorResolver.requireActor(null, null)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(identityService);
    }

    private void stubLinkedIdentity() {
        User user = new User();
        user.setId(42L);
        when(identityService.resolve(any())).thenReturn(Optional.of(user));
    }

    private void stubAction(String action, Long actor) {
        switch (action) {
            case "budget" -> when(budgetService.approve(1L, actor)).thenReturn(new Budget());
            case "balance" -> when(balanceService.approve(1L, actor, null)).thenReturn(new AnnualBalance());
            case "communication" -> when(communicationService.send(1L, actor)).thenReturn(new Communication());
            default -> throw new IllegalArgumentException(action);
        }
    }

    private void verifyAction(String action, Long actor) {
        switch (action) {
            case "budget" -> verify(budgetService).approve(1L, actor);
            case "balance" -> verify(balanceService).approve(1L, actor, null);
            case "communication" -> verify(communicationService).send(1L, actor);
            default -> throw new IllegalArgumentException(action);
        }
    }

    private JwtAuthenticationToken jwt() {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject("user_self").build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PRESIDENTE")));
    }
}
