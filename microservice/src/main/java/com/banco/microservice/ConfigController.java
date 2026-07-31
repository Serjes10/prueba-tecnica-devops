package com.banco.microservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ConfigController {

    // Endpoint 1: retorna el secreto que llega como variable de entorno (inyectado desde Vault)
    @GetMapping("/secret")
    public Map<String, String> secret() {
        String secret = System.getenv().getOrDefault("APP_SECRET", "NO_SECRET_FOUND");
        return Map.of("source", "vault-env-var", "secret", secret);
    }

    // Endpoint 2: lee y expone una propiedad de configuración local simulada
    @GetMapping("/config")
    public Map<String, String> config() {
        String cfg = System.getProperty("local.config", "default-local-config-value");
        return Map.of("source", "local-system-property", "config", cfg);
    }

    // Endpoint 3: health check para los probes de Kubernetes
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}