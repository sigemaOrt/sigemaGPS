package com.sigemaGPS.controllers;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.services.IPosicionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/posiciones")
public class PosicionController {

    private final IPosicionService posicionService;

    @Autowired
    public PosicionController(IPosicionService posicionService) {
        this.posicionService = posicionService;
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

    @PostMapping("/iniciarTrabajo/{idEquipo}")
    public void iniciarTrabajo(@PathVariable Long idEquipo) throws Exception {
        posicionService.iniciarTrabajo(idEquipo);
    }

    @PostMapping("/finalizarTrabajo/{idEquipo}")
    public void finalizarTrabajo(@PathVariable Long idEquipo) throws Exception {
        posicionService.finalizarTrabajo(idEquipo);
    }
}
