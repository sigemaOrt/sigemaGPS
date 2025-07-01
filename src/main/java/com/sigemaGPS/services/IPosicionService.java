package com.sigemaGPS.services;

import com.sigemaGPS.Dto.PosicionClienteDTO;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;

import java.time.LocalDate;
import java.util.List;


public interface IPosicionService {
    List<Posicion> obtenerTodasPorIdEquipo(Long idEquipo, LocalDate fecha) throws Exception;
    ReporteFinViaje obtenerReporteViaje(Long idEquipo, LocalDate fecha) throws Exception;
    boolean estaEnUso(Long idEquipo) throws Exception;
    void setEnUso(Long idEquipo, boolean enUso) throws Exception;
    ReporteSigemaDTO iniciarTrabajo(Long idEquipo, String jwtToken, PosicionClienteDTO ubicacion)  throws Exception;
    ReporteSigemaDTO finalizarTrabajo(Long idEquipo, String jwtToken) throws Exception;
    void eliminarTrabajo(Long idEquipo) throws Exception;
}
