package com.anmonu.gcpobservability.chatui.controller;

import com.anmonu.gcpobservability.chatui.service.GeminiService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@Validated
public class ChatController {

    private final GeminiService geminiService;

    public ChatController(GeminiService geminiService) {
        this.geminiService = geminiService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("prompt", "");
        model.addAttribute("response", "");
        return "index";
    }

    @PostMapping("/prompt")
    public String prompt(@RequestParam("prompt") @NotBlank String prompt, Model model) {
        model.addAttribute("prompt", prompt);
        model.addAttribute("response", geminiService.generateText(prompt));
        return "index";
    }
}
