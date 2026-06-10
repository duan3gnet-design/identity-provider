package com.identity.provider.controller;

import com.identity.provider.entity.User;
import com.identity.provider.repository.UserRepository;
import com.identity.provider.security.MfaRequiredException;
import com.identity.provider.service.MfaService;
import com.identity.provider.service.MfaSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final MfaService mfaService;
    private final MfaSessionService mfaSessionService;
    private final UserRepository userRepository;

    private final SecurityContextRepository securityContextRepository =
        new HttpSessionSecurityContextRepository();

    /**
     * Key hardcoded trong HttpSessionRequestCache (Spring Security source):
     *   private static final String SAVED_REQUEST = "SPRING_SECURITY_SAVED_REQUEST";
     * Đọc thẳng từ session thay vì dùng requestCache.getRequest() vì:
     * - getRequest() chỉ match khi URL/method hiện tại khớp với saved request
     * - getRequest() xóa entry khỏi session ngay lập tức
     */
    private static final String SAVED_REQUEST_KEY = "SPRING_SECURITY_SAVED_REQUEST";

    @GetMapping("/login")
    public String loginPage(
        @RequestParam(required = false) String error,
        @RequestParam(required = false) String logout,
        Model model
    ) {
        if (error != null) model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng");
        if (logout != null) model.addAttribute("message", "Bạn đã đăng xuất thành công");
        return "login";
    }

    @PostMapping("/login/process")
    public String processLogin(
        @RequestParam String username,
        @RequestParam String password,
        HttpServletRequest request,
        HttpServletResponse response,
        HttpSession session,
        Model model
    ) {
        // Đọc saved URL TRƯỚC KHI authenticate để tránh session bị ghi đè
        String savedUrl = extractSavedUrl(session);

        try {
            Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
            );

            saveSecurityContext(auth, request, response);

            // Redirect về /oauth2/authorize?... → SAS sẽ check consent → consent page
            return "redirect:" + (savedUrl != null ? savedUrl : "/");

        } catch (MfaRequiredException mfaEx) {
            String mfaToken = mfaSessionService.createPendingSession(mfaEx.getUsername());
            session.setAttribute("mfa_token", mfaToken);
            session.setAttribute("mfa_redirect", savedUrl); // giữ lại để sau MFA redirect đúng
            return "redirect:/mfa/verify";

        } catch (Exception e) {
            model.addAttribute("error", "Tên đăng nhập hoặc mật khẩu không đúng");
            return "login";
        }
    }

    @GetMapping("/mfa/verify")
    public String mfaPage(HttpSession session, Model model) {
        String mfaToken = (String) session.getAttribute("mfa_token");
        if (mfaToken == null || mfaSessionService.getUsername(mfaToken) == null) {
            return "redirect:/login?error";
        }
        return "mfa-verify";
    }

    @PostMapping("/mfa/verify")
    public String verifyMfa(
        @RequestParam String code,
        @RequestParam(required = false) boolean useBackup,
        HttpServletRequest request,
        HttpServletResponse response,
        HttpSession session,
        Model model
    ) {
        String mfaToken = (String) session.getAttribute("mfa_token");
        if (mfaToken == null) return "redirect:/login?error";

        String username = mfaSessionService.getUsername(mfaToken);
        if (username == null) {
            model.addAttribute("error", "Phiên xác thực đã hết hạn. Vui lòng đăng nhập lại.");
            return "mfa-verify";
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return "redirect:/login?error";

        boolean valid = useBackup
            ? mfaService.verifyBackupCode(user.getId(), code)
            : mfaService.verifyCode(user.getId(), code);

        if (!valid) {
            model.addAttribute("error", "Mã không đúng. Vui lòng thử lại.");
            return "mfa-verify";
        }

        mfaSessionService.invalidate(mfaToken);
        session.removeAttribute("mfa_token");

        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
            .map(r -> new SimpleGrantedAuthority(r.getName()))
            .collect(Collectors.toSet());

        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
        saveSecurityContext(auth, request, response);

        String redirectUrl = (String) session.getAttribute("mfa_redirect");
        session.removeAttribute("mfa_redirect");
        return "redirect:" + (redirectUrl != null ? redirectUrl : "/");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void saveSecurityContext(Authentication auth,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * Đọc URL gốc từ session và xóa nó đi (consumed).
     * Spring Security lưu SavedRequest với key "SPRING_SECURITY_SAVED_REQUEST"
     * khi redirect user về /login từ /oauth2/authorize?...
     */
    private String extractSavedUrl(HttpSession session) {
        Object raw = session.getAttribute(SAVED_REQUEST_KEY);
        if (raw instanceof SavedRequest savedRequest) {
            session.removeAttribute(SAVED_REQUEST_KEY);
            return savedRequest.getRedirectUrl();
        }
        return null;
    }
}
