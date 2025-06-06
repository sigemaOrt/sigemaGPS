package com.sigemaGPS.models;

import java.time.LocalDate;

public class ReporteFinViaje {
    private Posicion ultimaPosicion;
    private LocalDate fecha;
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
}
