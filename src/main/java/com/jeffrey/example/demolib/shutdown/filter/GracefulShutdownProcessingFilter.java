package com.jeffrey.example.demolib.shutdown.filter;

import com.jeffrey.example.demolib.shutdown.service.GracefulShutdownService;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class GracefulShutdownProcessingFilter extends OncePerRequestFilter {

    private GracefulShutdownService gracefulShutdownService;

    public GracefulShutdownProcessingFilter(GracefulShutdownService gracefulShutdownService) {
        this.gracefulShutdownService = gracefulShutdownService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (gracefulShutdownService.isInvoked()) {
            httpServletResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service shutdown in progress");
            return;
        }
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

}
