package com.messenger.messengerserver.config;

import com.messenger.messengerserver.service.CustomUserDetailsService;
import com.messenger.messengerserver.service.UserPresenceService;
import com.messenger.messengerserver.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final CustomUserDetailsService userDetailsService;
    private final UserPresenceService userPresenceService;  // 👈 ДОБАВИТЬ


    // ===== ВРЕМЕННАЯ ЗАЩИТА (пока сервер на домашнем ПК) =====

    // Полностью доверенные IP (без проверки JWT - только для отладки)
    private static final Set<String> TRUSTED_IPS = new HashSet<>(Arrays.asList(
            "127.0.0.1",           // localhost
            "0:0:0:0:0:0:0:1",    // IPv6 localhost
            "93.189.231.32"        // Ваш VPS сервер (TURN)
    ));

    // Доверенные IP диапазоны (локальная сеть)
    private static final Set<String> TRUSTED_IP_RANGES = new HashSet<>(Arrays.asList(
            "192.168.", "10.0.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31."
    ));

    // Заблокированные User-Agent (боты и сканеры)
    private static final Set<String> BLOCKED_USER_AGENTS = new HashSet<>(Arrays.asList(
            "zgrab", "Go-http-client", "curl", "wget", "python-requests",
            "Java-http-client", "masscan", "nmap", "nikto", "sqlmap",
            "dirbuster", "gobuster", "ffuf", "hydra", "medusa", "burp", "zap"
    ));

    // Заблокированные пути (чувствительные файлы)
    private static final Set<String> BLOCKED_PATHS = new HashSet<>(Arrays.asList(
            ".git", ".env", ".aws", "credentials", ".yml", ".yaml",
            ".properties", ".xml", ".json", ".sql", ".dump", ".bak",
            ".old", ".save", ".dist", ".local", ".dev", ".test",
            "wp-admin", "wp-login", "phpmyadmin", "mysql", "adminer"
    ));

    // Rate limiting для неавторизованных запросов (для ботов)
    private final ConcurrentMap<String, AtomicInteger> unauthorizedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_UNAUTHORIZED_PER_MINUTE = 10;

    // Rate limiting для авторизованных запросов (ваши пользователи)
    private final ConcurrentMap<String, AtomicInteger> authorizedAttempts = new ConcurrentHashMap<>();
    private static final int MAX_AUTHORIZED_PER_MINUTE = 500;

    // ScheduledExecutor для очистки
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public JwtAuthenticationFilter(JwtUtil jwtUtil, CustomUserDetailsService userDetailsService, UserPresenceService userPresenceService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.userPresenceService = userPresenceService;

        // Планируем очистку карт каждую минуту
        scheduler.scheduleAtFixedRate(() -> {
            unauthorizedAttempts.clear();
            authorizedAttempts.clear();
        }, 1, 1, TimeUnit.MINUTES);

        // Добавляем shutdown hook для корректного завершения
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // ===== 1. БЛОКИРОВКА ОПАСНЫХ ПУТЕЙ (всегда) =====
        String pathLower = path.toLowerCase();
        for (String blocked : BLOCKED_PATHS) {
            if (pathLower.contains(blocked.toLowerCase())) {
                System.out.println("[SECURITY] 🚫 Блокирован опасный путь: " + path + " от IP: " + clientIp);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.getWriter().write("Access denied");
                return;
            }
        }

        // ===== 2. ПУБЛИЧНЫЕ ЭНДПОИНТЫ (без аутентификации) =====
        boolean isPublicEndpoint = path.startsWith("/api/auth/") ||
                path.startsWith("/api/test/") ||
                path.startsWith("/ws/") ||
                path.startsWith("/websocket-test.html") ||
                path.startsWith("/uploads/") ||
                path.endsWith(".html") ||
                path.endsWith(".js") ||
                path.endsWith(".css");

        if (isPublicEndpoint) {
            // Даже на публичных эндпоинтах блокируем явных ботов
            if (isBotUserAgent(userAgent)) {
                System.out.println("[SECURITY] 🚫 Бот заблокирован на публичном эндпоинте: " + userAgent);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        // ===== 3. ПОЛУЧАЕМ JWT ТОКЕН =====
        final String authorizationHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                username = jwtUtil.extractUsername(jwt);
            } catch (Exception e) {
                System.out.println("[SECURITY] Ошибка JWT от IP: " + clientIp + " - " + e.getMessage());
            }
        }

        // ===== 4. ЕСТЬ ВАЛИДНЫЙ JWT =====
        if (username != null && jwtUtil.validateToken(jwt)) {
            // Rate limiting для авторизованных
            if (isRateLimitExceeded(authorizedAttempts, clientIp, MAX_AUTHORIZED_PER_MINUTE)) {
                System.out.println("[SECURITY] ⚠️ Rate limit для авторизованного IP: " + clientIp);
                response.setStatus(429);
                return;
            }

            // 👇 ДОБАВИТЬ ПРОВЕРКУ СЕССИИ
            String sessionId = request.getHeader("X-Session-Id");
            if (sessionId != null && !sessionId.isEmpty()) {
                boolean isActive = userPresenceService.isSessionActive(username, sessionId);
                if (!isActive) {
                    System.out.println("[SECURITY] 🚫 Сессия не активна для " + username);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setHeader("X-Session-Terminated", "true");
                    response.getWriter().write("Session terminated by another device");
                    return;
                }
            } else {
                System.out.println("[SECURITY] ⚠️ Нет X-Session-Id заголовка для " + username);
            }

            try {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                System.out.println("[SECURITY] ✅ Аутентифицирован: " + username + " с IP: " + clientIp);
                chain.doFilter(request, response);
                return;
            } catch (Exception e) {
                System.out.println("[SECURITY] Ошибка загрузки пользователя: " + e.getMessage());
            }
        }

        // ===== 5. НЕТ ВАЛИДНОГО JWT =====

        // Rate limiting для неавторизованных (защита от брутфорса)
        if (isRateLimitExceeded(unauthorizedAttempts, clientIp, MAX_UNAUTHORIZED_PER_MINUTE)) {
            System.out.println("[SECURITY] 🚫 Rate limit для неавторизованного IP: " + clientIp);
            response.setStatus(429); // TOO MANY REQUESTS
            return;
        }

        // Блокировка ботов по User-Agent
        if (isBotUserAgent(userAgent)) {
            System.out.println("[SECURITY] 🚫 Бот заблокирован: " + userAgent + " от IP: " + clientIp);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // Для доверенных IP (локальная сеть) - возвращаем 401, но не блокируем
        if (isTrustedIp(clientIp)) {
            System.out.println("[SECURITY] ⚠️ Доверенный IP без JWT: " + clientIp + " URI: " + path);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader("WWW-Authenticate", "Bearer");
            response.getWriter().write("Authentication required");
            return;
        }

        // ===== 6. ВСЕ ОСТАЛЬНЫЕ - БЛОКИРУЕМ (боты, сканеры) =====
        System.out.println("[SECURITY] 🚫 ЗАБЛОКИРОВАН неизвестный запрос:");
        System.out.println("[SECURITY] IP: " + clientIp);
        System.out.println("[SECURITY] URI: " + path);
        System.out.println("[SECURITY] User-Agent: " + userAgent);

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write("Access denied");
    }

    /**
     * Проверка, является ли IP доверенным
     */
    private boolean isTrustedIp(String ip) {
        if (TRUSTED_IPS.contains(ip)) return true;
        for (String range : TRUSTED_IP_RANGES) {
            if (ip.startsWith(range)) return true;
        }
        return false;
    }

    /**
     * Проверка, является ли User-Agent ботом
     */
    private boolean isBotUserAgent(String userAgent) {
        if (userAgent == null) return true;
        String uaLower = userAgent.toLowerCase();
        for (String blocked : BLOCKED_USER_AGENTS) {
            if (uaLower.contains(blocked.toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Проверка, превышен ли лимит запросов
     * @return true если лимит превышен, false если в пределах лимита
     */
    private boolean isRateLimitExceeded(ConcurrentMap<String, AtomicInteger> map, String key, int max) {
        AtomicInteger counter = map.computeIfAbsent(key, k -> new AtomicInteger(0));
        return counter.incrementAndGet() > max;
    }

    /**
     * Получение реального IP адреса клиента с учетом прокси
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headersToCheck = {
                "X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR"
        };
        for (String header : headersToCheck) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}