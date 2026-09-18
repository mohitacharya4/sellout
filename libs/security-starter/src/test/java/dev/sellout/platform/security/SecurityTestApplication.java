package dev.sellout.platform.security;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
class SecurityTestApplication {

  @RestController
  static class PingController {
    @GetMapping("/ping")
    String ping() {
      return "pong";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    String admin() {
      return "admin";
    }

    @GetMapping("/healthz")
    String healthz() {
      return "ok";
    }
  }
}
