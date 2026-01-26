package com.near.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

/**
 * Configuración para procesamiento asíncrono de notificaciones.
 * Permite que el envío de notificaciones no bloquee las operaciones principales.
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "notificationExecutor")
    public Executor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Configuración del pool de hilos
        executor.setCorePoolSize(5);           // Hilos mínimos
        executor.setMaxPoolSize(10);           // Hilos máximos
        executor.setQueueCapacity(100);        // Cola de espera
        executor.setThreadNamePrefix("notif-"); // Prefijo para identificar en logs
        executor.setKeepAliveSeconds(60);      // Tiempo de vida de hilos extra
        
        // Política de rechazo: ejecutar en el hilo del llamador si el pool está lleno
        executor.setRejectedExecutionHandler((r, e) -> {
            log.warn("Pool de notificaciones lleno. Ejecutando en hilo principal.");
            if (!e.isShutdown()) {
                r.run();
            }
        });
        
        // Esperar a que terminen las tareas pendientes al cerrar la aplicación
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return notificationExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * Manejo de excepciones no capturadas en métodos @Async
     */
    private static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        
        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("Error en método async '{}': {}", method.getName(), ex.getMessage());
            log.debug("Parámetros del método: {}", params);
            
            // Aquí podrías agregar:
            // - Envío a sistema de monitoreo (Sentry, etc.)
            // - Registro en base de datos para retry
            // - Notificación a admins
        }
    }
}
