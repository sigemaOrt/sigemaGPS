package com.sigemaGPS.services;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;

import java.time.LocalDate;
import java.util.List;

public interface IPosicionService {
    public List<Posicion> obtenerTodasPorIdEquipo(Long idEquipo, LocalDate fecha) throws Exception;
    public Posicion Crear(Posicion posicion) throws Exception;
    public ReporteFinViaje obtenerReporteViaje(Long idEquipo, LocalDate fecha) throws Exception;
    public boolean estaEnUso(Long idEquipo) throws Exception;
    public void setEnUso(Long idEquipo, boolean enUso) throws Exception;
}