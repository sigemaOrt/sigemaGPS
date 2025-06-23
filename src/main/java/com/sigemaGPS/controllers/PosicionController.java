package com.sigemaGPS.controllers;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.utilidades.SigemaException; // Importar para manejar excepciones específicas
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity; // Para devolver respuestas HTTP más informativas
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest; // Para obtener el token JWT del encabezado

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/posiciones")
@CrossOrigin(origins = "*") // ¡Importante! Asegúrate de que tu frontend pueda acceder. Ajustar en producción.
public class PosicionController {

    private final IPosicionService posicionService;
    private final HttpServletRequest request; // Inyectar HttpServletRequest para acceder a los encabezados

    @Autowired
    public PosicionController(IPosicionService posicionService, HttpServletRequest request) {
        this.posicionService = posicionService;
        this.request = request;
    }

    // --- Nuevo método auxiliar para extraer el token JWT ---
    private String getTokenFromRequest() {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // Extrae el token después de "Bearer "
        }
        // Puedes lanzar una excepción más específica o devolver null/vacío y manejarlo en el controlador.
        // Lanzar una RuntimeException es una opción si el token es *siempre* esperado.
        throw new RuntimeException("No se encontró el token JWT en el encabezado de autorización (Bearer Token).");
    }

    // Obtener todas las posiciones por equipo y fecha
    @GetMapping("/{idEquipo}")
    public List<Posicion> obtenerTodasPorEquipoYFecha(
            @PathVariable Long idEquipo,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) throws Exception {
        return posicionService.obtenerTodasPorIdEquipo(idEquipo, fecha);
    }

    //  Obtener reporte de viaje por equipo y fecha
    @GetMapping("/{idEquipo}/reporte")
    public ReporteFinViaje obtenerReporteViaje(
            @PathVariable Long idEquipo,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) throws Exception {
        return posicionService.obtenerReporteViaje(idEquipo, fecha);
    }

    // Saber si un equipo está en uso actualmente
    @GetMapping("/{idEquipo}/enUso")
    public boolean estaEnUso(@PathVariable Long idEquipo) throws Exception {
        return posicionService.estaEnUso(idEquipo);
    }

    // Establecer manualmente si un equipo está en uso o no
    @PostMapping("/{idEquipo}/enUso")
    public void setEnUso(@PathVariable Long idEquipo, @RequestParam boolean enUso) throws Exception {
        posicionService.setEnUso(idEquipo, enUso);
    }

    // --- MODIFICADO: Ahora requiere el token JWT ---
    @PostMapping("/iniciarTrabajo/{idEquipo}")
    public ResponseEntity<?> iniciarTrabajo(@PathVariable Long idEquipo) {
        try {
            String jwtToken = getTokenFromRequest(); // Obtiene el token del encabezado
            posicionService.iniciarTrabajo(idEquipo, jwtToken); // Pasa el token al servicio
            return ResponseEntity.ok("Trabajo iniciado exitosamente para el equipo " + idEquipo);
        } catch (SigemaException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) { // Captura si el token no se encuentra
            return ResponseEntity.status(401).body(e.getMessage()); // 401 Unauthorized
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al iniciar trabajo: " + e.getMessage());
        }
    }

    @PostMapping("/finalizarTrabajo/{idEquipo}")
    public ResponseEntity<?> finalizarTrabajo(@PathVariable Long idEquipo) {
        try {
            String jwtToken = getTokenFromRequest(); // Obtiene el token del encabezado
            posicionService.finalizarTrabajo(idEquipo, jwtToken); // Pasa el token al servicio
            return ResponseEntity.ok("Trabajo finalizado exitosamente para el equipo " + idEquipo);
        } catch (SigemaException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) { // Captura si el token no se encuentra
            return ResponseEntity.status(401).body(e.getMessage()); // 401 Unauthorized
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al finalizar trabajo: " + e.getMessage());
        }
    }

    // Opcional: Si necesitas un endpoint para eliminar el trabajo
    @DeleteMapping("/eliminarTrabajo/{idEquipo}")
    public ResponseEntity<?> eliminarTrabajo(@PathVariable Long idEquipo) {
        try {
            posicionService.eliminarTrabajo(idEquipo);
            return ResponseEntity.ok("Trabajo eliminado para el equipo " + idEquipo);
        } catch (SigemaException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al eliminar trabajo: " + e.getMessage());
        }
    }
}