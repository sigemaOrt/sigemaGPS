package com.sigemaGPS.services;

import com.sigemaGPS.Dto.ReporteSigemaDTO;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.models.Posicion;
import org.springframework.http.ResponseEntity;

public interface IJsonStorageService {

    void guardarPosicionEnJSON(Posicion posicion, EquipoSigema equipoInfo);

    void guardarReporteEnJSON(ReporteSigemaDTO reporte, String tipo);

    void guardarRequestEnJSON(ReporteSigemaDTO reporte, String url, String tipo);

    void guardarResponseEnJSON(ResponseEntity<String> response, String tipo);
}