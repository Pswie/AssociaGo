package com.associago.me;

import com.associago.member.repository.MemberRepository;
import com.associago.membership.repository.UserAssociationRepository;
import com.associago.security.ClerkIdentityService;
import com.associago.user.User;
import com.associago.user.repository.UserRepository;
import com.associago.volunteer.repository.VolunteerExpenseRepository;
import com.associago.volunteer.repository.VolunteerRepository;
import com.associago.volunteer.repository.VolunteerShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MeControllerTests {
    @Mock private UserRepository userRepository;
    @Mock private UserAssociationRepository membershipRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private VolunteerRepository volunteerRepository;
    @Mock private VolunteerShiftRepository shiftRepository;
    @Mock private VolunteerExpenseRepository expenseRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MeController controller = new MeController(new ClerkIdentityService(userRepository), membershipRepository,
                memberRepository, volunteerRepository, shiftRepository, expenseRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void returnsNumericInternalIdOnlyForAuthenticatedClerkSubject() throws Exception {
        User self = user(42L, "user_self");
        when(userRepository.findByClerkUserId("user_self")).thenReturn(Optional.of(self));

        mockMvc.perform(get("/api/v1/me").param("id", "999").param("userId", "999")
                        .principal(authentication("user_self", "self@example.org", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.nome").value("Self"));

        verify(membershipRepository).findByUserId(42L);
        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void profileWithoutAuthenticationCannotResolveOrCreateUser() throws Exception {
        mockMvc.perform(get("/api/v1/me").param("id", "42"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository, membershipRepository);
    }

    @Test
    void unauthenticatedJwtCannotResolveOrCreateUser() throws Exception {
        JwtAuthenticationToken token = authentication("user_self", "self@example.org", true);
        token.setAuthenticated(false);

        mockMvc.perform(get("/api/v1/me").principal(token))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userRepository, membershipRepository);
    }

    @Test
    void unverifiedEmailCannotLinkAnExistingPerson() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .principal(authentication("user_unknown", "victim@example.org", false)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void verifiedUnknownEmailDoesNotAutomaticallyCreateInternalUser() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .principal(authentication("user_new", "new@example.org", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(userRepository).findByEmailIgnoreCase("new@example.org");
        verify(userRepository, never()).save(any());
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void verifiedEmailCannotTakeOverAnAlreadyLinkedPerson() throws Exception {
        when(userRepository.findByEmailIgnoreCase("shared@example.org"))
                .thenReturn(Optional.of(user(99L, "user_other")));

        mockMvc.perform(get("/api/v1/me")
                        .principal(authentication("user_new", "shared@example.org", true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.id").doesNotExist());

        verify(userRepository, never()).save(any());
        verifyNoInteractions(membershipRepository);
    }

    private User user(Long id, String clerkId) {
        User user = new User();
        user.setId(id);
        user.setClerkUserId(clerkId);
        user.setFirstName("Self");
        user.setLastName("User");
        return user;
    }

    private JwtAuthenticationToken authentication(String subject, String email, boolean verified) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", verified)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PRESIDENTE")));
    }
}
