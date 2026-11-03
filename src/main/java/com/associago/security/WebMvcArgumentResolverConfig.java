package com.associago.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcArgumentResolverConfig implements WebMvcConfigurer {
    private final ClerkIdentityService identities;
    public WebMvcArgumentResolverConfig(ClerkIdentityService identities) { this.identities = identities; }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentUserArgumentResolver(identities));
    }
}
