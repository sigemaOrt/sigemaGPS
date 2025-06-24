package com.sigemaGPS.controllers;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.utilidades.SigemaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/posiciones")
@CrossOrigin(origins = "*")
public class PosicionController {

    private final IPosicionService posicionService;


    @Autowired
    public PosicionController(IPosicionService posicionService) {
        this.posicionService = posicionService;
    }


    private String getTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new RuntimeException("No se encontró el token JWT en el encabezado de autorización (Bearer Token).");
    }

    @GetMapping("/{idEquipo}")
    public List<Posicion> obtenerTodasPorEquipoYFecha(
            @PathVariable Long idEquipo,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) throws Exception {
        return posicionService.obtenerTodasPorIdEquipo(idEquipo, fecha);
    }

    @GetMapping("/{idEquipo}/reporte")
    public ReporteFinViaje obtenerReporteViaje(
            @PathVariable Long idEquipo,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) throws Exception {
        return posicionService.obtenerReporteViaje(idEquipo, fecha);
    }

    @GetMapping("/{idEquipo}/enUso")
    public boolean estaEnUso(@PathVariable Long idEquipo) throws Exception {
        return posicionService.estaEnUso(idEquipo);
    }

    @PostMapping("/{idEquipo}/enUso")
    public void setEnUso(@PathVariable Long idEquipo, @RequestParam boolean enUso) throws Exception {
        posicionService.setEnUso(idEquipo, enUso);
    }


    @PostMapping("/iniciarTrabajo/{idEquipo}")
    public ResponseEntity<?> iniciarTrabajo(@PathVariable Long idEquipo, HttpServletRequest request) {
        try {
            String jwtToken = getTokenFromRequest(request); // Pasa el request al método
            posicionService.iniciarTrabajo(idEquipo, jwtToken);
            return ResponseEntity.ok("Trabajo iniciado exitosamente para el equipo " + idEquipo);
        } catch (SigemaException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al iniciar trabajo: " + e.getMessage());
        }
    }


    @PostMapping("/finalizarTrabajo/{idEquipo}")
    public ResponseEntity<?> finalizarTrabajo(@PathVariable Long idEquipo, HttpServletRequest request) {
        try {
            String jwtToken = getTokenFromRequest(request); // Pasa el request al método
            posicionService.finalizarTrabajo(idEquipo, jwtToken);
            return ResponseEntity.ok("Trabajo finalizado exitosamente para el equipo " + idEquipo);
        } catch (SigemaException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al finalizar trabajo: " + e.getMessage());
        }
    }

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