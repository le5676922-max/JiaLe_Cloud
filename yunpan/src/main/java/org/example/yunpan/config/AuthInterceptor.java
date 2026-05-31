package org.example.yunpan.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.yunpan.util.JwtUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final SessionManager sessionManager;
    private final UserStore userStore;

    public AuthInterceptor(JwtUtils jwtUtils, SessionManager sessionManager, UserStore userStore) {
        this.jwtUtils = jwtUtils;
        this.sessionManager = sessionManager;
        this.userStore = userStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {

        // 放行 OPTIONS 预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = null;

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        } else {
            // 回退：从 query 参数获取 token（支持 <a> 标签直接下载）
            token = request.getParameter("token");
        }

        if (token == null || token.isBlank()) {
            send401(response, "缺少有效的认证信息");
            return false;
        }

        // 校验 Token
        try {
            Claims claims = jwtUtils.parseToken(token);
            String email = claims.getSubject();
            String loginId = claims.get("loginId", String.class);

            // 检查用户是否存在（可能被管理员删除）
            var userOpt = userStore.findByEmail(email);
            if (userOpt.isEmpty() || !userOpt.get().getEnabled()) {
                send401(response, "账号已被禁用或删除");
                return false;
            }

            // 检查会话是否被顶掉
            if (loginId != null && !sessionManager.isValid(email, loginId)) {
                send401(response, "账号已在其他设备登录或已被强制下线，当前会话已失效");
                return false;
            }

            // 管理员接口校验角色
            if (request.getRequestURI().startsWith("/api/admin/")) {
                if (!"ADMIN".equals(userOpt.get().getRole())) {
                    send403(response, "权限不足，仅管理员可访问");
                    return false;
                }
            }

            request.setAttribute("email", email);
            UserContext.set(email);
            return true;
        } catch (ExpiredJwtException e) {
            send401(response, "Token 已过期，请重新登录");
            return false;
        } catch (JwtException e) {
            send401(response, "Token 无效");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    private void send403(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"code\":403,\"message\":\"%s\"}", message)
        );
    }

    private void send401(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                String.format("{\"code\":401,\"message\":\"%s\"}", message)
        );
    }
}
