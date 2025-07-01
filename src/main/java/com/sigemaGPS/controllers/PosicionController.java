package com.sigemaGPS.controllers;

import com.sigemaGPS.Dto.PosicionClienteDTO;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.utilidades.SigemaException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;



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

    @GetMapping("/{idEquipo}/enUso")
    public boolean estaEnUso(@PathVariable Long idEquipo) throws Exception {
        return posicionService.estaEnUso(idEquipo);
    }

    @PostMapping("/iniciarTrabajo/{idEquipo}")
    public ResponseEntity<?> iniciarTrabajo(
            @PathVariable Long idEquipo,
            @RequestBody PosicionClienteDTO ubicacion,
            HttpServletRequest request) {
        try {
            String jwtToken = getTokenFromRequest(request);
            ReporteSigemaDTO dto = posicionService.iniciarTrabajo(idEquipo, jwtToken, ubicacion);
            return ResponseEntity.ok(dto);
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
            String jwtToken = getTokenFromRequest(request);
            ReporteSigemaDTO dto = posicionService.finalizarTrabajo(idEquipo, jwtToken);
            return ResponseEntity.ok(dto);
        } catch (SigemaException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error interno al finalizar trabajo: " + e.getMessage());
        }
    }

}