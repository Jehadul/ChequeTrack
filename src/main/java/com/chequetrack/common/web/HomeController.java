package com.chequetrack.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Landing page. Replaced by the dashboard in M12. */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "index";
    }
}
