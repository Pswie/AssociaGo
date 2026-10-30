package com.associago.membership;

import com.associago.user.User;
import com.associago.user.UserController;
import com.associago.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipDesktopCompatibilityTests {
    @Test void desktopMembershipBoundaryAndUserImportPreserveLocalBehavior() {
        var environment = new MockEnvironment(); environment.setActiveProfiles("desktop");
        var access = new MembershipAccess(environment, null, null);
        assertDoesNotThrow(() -> access.require(null, 1L, 2L));
        assertDoesNotThrow(() -> access.createId(91L));
        assertDoesNotThrow(() -> access.immutable(2L, 1L));
        var service = mock(UserService.class);
        var user = new User(); user.setId(91L); user.setClerkUserId("local-import-fixture");
        when(service.save(user)).thenReturn(user);
        assertSame(user, new UserController(service, environment).createUser(user));
        verify(service).save(user);
    }
}
