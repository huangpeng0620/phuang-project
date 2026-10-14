package com.sprouttrail.image.service.impl;

import com.sprouttrail.image.mapper.UserMapper;
import com.sprouttrail.image.model.entity.User;
import com.sprouttrail.image.model.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private BCryptPasswordEncoder passwordEncoder;
    private UserAuthServiceImpl userAuthService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        userAuthService = new UserAuthServiceImpl();
        ReflectionTestUtils.setField(userAuthService, "userMapper", userMapper);
        ReflectionTestUtils.setField(userAuthService, "passwordEncoder", passwordEncoder);
    }

    @Test
    void registerTrimsUsernameAndStoresOnlyPasswordHash() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        RegisterRequest request = new RegisterRequest();
        request.setUsername(" alice ");
        request.setPassword("password123");

        userAuthService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("alice", savedUser.getUsername());
        assertEquals(1, savedUser.getStatus());
        assertTrue(passwordEncoder.matches(request.getPassword(), savedUser.getPasswordHash()));
    }

    @Test
    void registerRejectsExistingUsername() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password123");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> userAuthService.register(request));

        assertEquals(409, exception.getStatusCode().value());
        verify(userMapper, never()).insert(any());
    }
}
