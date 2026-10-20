package com.chequetrack.lifecycle.web;

import com.chequetrack.lifecycle.domain.ChequeLifecycles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/** Shows the allowed status changes, rendered from {@link ChequeLifecycles} (the same rules the engine enforces). */
@Controller
public class LifecycleController {

    @GetMapping("/lifecycle")
    public String rules(Model model) {
        model.addAttribute("machines", List.of(ChequeLifecycles.RECEIVED, ChequeLifecycles.ISSUED));
        return "lifecycle/rules";
    }
}
