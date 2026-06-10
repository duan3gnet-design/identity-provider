package com.identity.provider.controller;

import com.identity.provider.entity.User;
import com.identity.provider.repository.UserRepository;
import com.identity.provider.service.MfaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/mfa")
@RequiredArgsConstructor
public class MfaSetupController {

    private final MfaService mfaService;
    private final UserRepository userRepository;

    @GetMapping("/setup")
    public String setupPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        boolean mfaEnabled = mfaService.isMfaEnabled(user.getId());
        model.addAttribute("mfaEnabled", mfaEnabled);

        if (!mfaEnabled) {
            String secret = mfaService.initializeMfa(user.getId());
            String qrCode = mfaService.generateQrCodeBase64(user.getUsername(), secret);
            model.addAttribute("secret", secret);
            model.addAttribute("qrCode", "data:image/png;base64," + qrCode);
        }
        return "mfa-setup";
    }

    @PostMapping("/setup/enable")
    public String enable(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam String code,
        RedirectAttributes redirectAttrs
    ) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        boolean success = mfaService.enableMfa(user.getId(), code);
        if (!success) {
            redirectAttrs.addFlashAttribute("error", "Mã OTP không đúng. Vui lòng thử lại.");
            return "redirect:/mfa/setup";
        }

        List<String> backupCodes = mfaService.generateBackupCodes(user.getId());
        redirectAttrs.addFlashAttribute("backupCodes", backupCodes);
        redirectAttrs.addFlashAttribute("success", "Xác thực 2 bước đã được bật thành công!");
        return "redirect:/mfa/backup-codes";
    }

    @GetMapping("/backup-codes")
    public String backupCodesPage(Model model) {
        // backupCodes được truyền qua flash attribute từ bước trước
        if (!model.containsAttribute("backupCodes")) {
            return "redirect:/mfa/setup";
        }
        return "mfa-backup-codes";
    }

    @PostMapping("/disable")
    public String disable(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam String code,
        RedirectAttributes redirectAttrs
    ) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("User not found"));

        if (!mfaService.verifyCode(user.getId(), code)) {
            redirectAttrs.addFlashAttribute("error", "Mã OTP không đúng.");
            return "redirect:/mfa/setup";
        }
        mfaService.disableMfa(user.getId());
        redirectAttrs.addFlashAttribute("success", "Đã tắt xác thực 2 bước.");
        return "redirect:/";
    }
}
