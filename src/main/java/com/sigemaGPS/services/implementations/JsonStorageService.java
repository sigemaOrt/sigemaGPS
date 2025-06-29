package com.sigemaGPS.services.implementations;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import com.sigemaGPS.services.IJsonStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class JsonStorageService implements IJsonStorageService {

    private final ObjectMapper objectMapper;

    @Value("${sigema.json.storage.path:./data/posiciones}")
    private String jsonStoragePath;

    public JsonStorageService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.findAndRegisterModules(); // Para soporte de Java 8 time
    }

    @PostConstruct
    public void init() {
        System.out.println("=== JSON STORAGE SERVICE INICIALIZADO ===");
        System.out.println("jsonStoragePath = " + jsonStoragePath);
        createStorageDirectory();
    }

    private void createStorageDirectory() {
        try {
            Path path = Paths.get(jsonStoragePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                System.out.println("Directorio creado para almacenar JSON: " + jsonStoragePath);
            }
        } catch (IOException e) {
            System.err.println("Error al crear directorio de almacenamiento: " + e.getMessage());
            throw new RuntimeException("No se pudo crear el directorio de almacenamiento", e);
        }
    }

    @Override
    public void guardarPosicionEnJSON(Posicion posicion, EquipoSigema equipoInfo) {
        try {
            // Crear objeto completo con información de la posición y del equipo
            Map<String, Object> posicionCompleta = new HashMap<>();
            posicionCompleta.put("timestamp", new Date());
            posicionCompleta.put("tipo", "posicion");
            posicionCompleta.put("posicion", posicion);

            if (equipoInfo != null) {
                Map<String, Object> equipoData = new HashMap<>();
                equipoData.put("id", equipoInfo.getId());
                equipoData.put("latitud", equipoInfo.getLatitud());
                equipoData.put("longitud", equipoInfo.getLongitud());
                posicionCompleta.put("equipo", equipoData);
            }

            // Crear nombre de archivo con timestamp
            String fileName = String.format("posicion_equipo_%d_%s.json",
                    posicion.getIdEquipo(),
                    new Date().getTime());

            File file = new File(jsonStoragePath, fileName);
            objectMapper.writeValue(file, posicionCompleta);

            System.out.println("Posición guardada en JSON: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error al guardar posición en JSON: " + e.getMessage());
        }
    }

    @Override
    public void guardarReporteEnJSON(ReporteSigemaDTO reporte, String tipo) {
        try {
            Map<String, Object> reporteCompleto = new HashMap<>();
            reporteCompleto.put("timestamp", new Date());
            reporteCompleto.put("tipo", tipo);
            reporteCompleto.put("reporte", reporte);

            String fileName = String.format("%s_equipo_%d_%s.json",
                    tipo,
                    reporte.getIdEquipo(),
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

            File file = new File(jsonStoragePath, fileName);
            objectMapper.writeValue(file, reporteCompleto);

            System.out.println("Reporte guardado en JSON: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error al guardar reporte en JSON: " + e.getMessage());
        }
    }

    @Override
    public void guardarRequestEnJSON(ReporteSigemaDTO reporte, String url, String tipo) {
        try {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("timestamp", new Date());
            requestData.put("tipo", tipo);
            requestData.put("url", url);
            requestData.put("method", "POST");
            requestData.put("payload", reporte);

            String fileName = String.format("%s_equipo_%d_%s.json",
                    tipo,
                    reporte.getIdEquipo(),
                    new Date().getTime());

            File file = new File(jsonStoragePath, fileName);
            objectMapper.writeValue(file, requestData);

            System.out.println("Request guardado en JSON: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error al guardar request en JSON: " + e.getMessage());
        }
    }

    @Override
    public void guardarResponseEnJSON(ResponseEntity<String> response, String tipo) {
        try {
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("timestamp", new Date());
            responseData.put("tipo", tipo);
            responseData.put("statusCode", response.getStatusCode().value());
            responseData.put("headers", response.getHeaders().toSingleValueMap());
            responseData.put("body", response.getBody());

            String fileName = String.format("%s_%s.json",
                    tipo,
                    new Date().getTime());

            File file = new File(jsonStoragePath, fileName);
            objectMapper.writeValue(file, responseData);

            System.out.println("Response guardado en JSON: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error al guardar response en JSON: " + e.getMessage());
        }
    }
}
