package com.sigemaGPS.models;

import java.time.LocalDate;

public class ReporteFinViaje {
    private Posicion ultimaPosicion;
    private LocalDate fecha; // Fecha del reporte, normalmente la fecha del día
    private Long idEquipo;
    private double totalHoras;
    private double totalKMs;

    public ReporteFinViaje(Posicion ultimaPosicion, LocalDate fecha, Long idEquipo, double totalHoras, double totalKMs) {
        this.ultimaPosicion = ultimaPosicion;
        this.fecha = fecha;
        this.idEquipo = idEquipo;
        this.totalHoras = totalHoras;
        this.totalKMs = totalKMs;
    }

    // --- Métodos Getters (AGREGADOS) ---
    public Posicion getUltimaPosicion() {
        return ultimaPosicion;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Long getIdEquipo() {
        return idEquipo;
    }

    public double getTotalHoras() {
        return totalHoras;
    }

    public double getTotalKMs() {
        return totalKMs;
    }

    // --- Métodos Setters (Opcionales, pero buena práctica si los necesitas) ---
    public void setUltimaPosicion(Posicion ultimaPosicion) {
        this.ultimaPosicion = ultimaPosicion;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public void setIdEquipo(Long idEquipo) {
        this.idEquipo = idEquipo;
    }

    public void setTotalHoras(double totalHoras) {
        this.totalHoras = totalHoras;
    }

    public void setTotalKMs(double totalKMs) {
        this.totalKMs = totalKMs;
    }
}