package com.marcantoinemeunier.hiversolidaire.config;

import com.marcantoinemeunier.hiversolidaire.inscription.ProfileRequiredInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class WebConfig implements WebMvcConfigurer {

    private final ProfileRequiredInterceptor profileRequired;

    WebConfig(ProfileRequiredInterceptor profileRequired) {
        this.profileRequired = profileRequired;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(profileRequired).addPathPatterns("/inscription", "/inscription/**", "/mes-inscriptions",
                "/mes-inscriptions/**");
    }
}
