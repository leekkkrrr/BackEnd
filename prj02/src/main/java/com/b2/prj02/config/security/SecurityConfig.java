package com.b2.prj02.config.security;

import com.b2.prj02.config.CorsConfig;
import com.b2.prj02.service.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CorsConfig corsConfig;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .cors().and()
                .formLogin().disable()
                .httpBasic().disable()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers("/api/user/logout", "/api/user/delete").access("hasAnyRole('USER', 'SELLER') and not hasRole('DELETED')")
                .antMatchers("/api/user/**").permitAll()
                .antMatchers("/api/shop/**").hasAnyRole("USER", "SELLER")
                .antMatchers("/api/product/**").hasRole("SELLER")
                .anyRequest().authenticated()
                .and()
                .addFilter(corsConfig.corsFilter()) // ** CorsFilter 등록 **
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}