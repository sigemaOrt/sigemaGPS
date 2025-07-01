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
import java.time.LocalDateTime;
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
        this.objectMapper.findAndRegisterModules();
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

    public void iniciarViaje(Long idEquipo, Posicion posicion, EquipoSigema equipoInfo) {
        try {
            // Crear estructura del viaje
            Map<String, Object> viajeData = new HashMap<>();
            viajeData.put("idEquipo", idEquipo);
            viajeData.put("fechaInicio", new Date());
            viajeData.put("estado", "EN_CURSO");

            // Información del equipo
            if (equipoInfo != null) {
                Map<String, Object> equipoData = new HashMap<>();
                equipoData.put("id", equipoInfo.getId());
                equipoData.put("latitud", equipoInfo.getLatitud());
                equipoData.put("longitud", equipoInfo.getLongitud());
                viajeData.put("equipoInfo", equipoData);
            }

            // Lista de posiciones (iniciamos con la primera)
            List<Map<String, Object>> posiciones = new ArrayList<>();
            Map<String, Object> posicionData = new HashMap<>();
            posicionData.put("timestamp", posicion.getFecha());
            posicionData.put("latitud", posicion.getLatitud());
            posicionData.put("longitud", posicion.getLongitud());
            posicionData.put("esFinal", posicion.isFin());
            posiciones.add(posicionData);

            viajeData.put("posiciones", posiciones);

            // Crear archivo con nomenclatura: IDMAQUINA_enCurso.json
            String fileName = idEquipo + "_enCurso.json";
            File file = new File(jsonStoragePath, fileName);

            objectMapper.writeValue(file, viajeData);
            System.out.println("Viaje iniciado - Archivo creado: " + file.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("Error al iniciar viaje en JSON: " + e.getMessage());
        }
    }

    public void agregarPosicionAViaje(Long idEquipo, Posicion posicion, EquipoSigema equipoInfo) {
        try {
            String fileName = idEquipo + "_enCurso.json";
            File file = new File(jsonStoragePath, fileName);

            if (!file.exists()) {
                System.err.println("Archivo de viaje en curso no encontrado: " + fileName);
                // Si no existe, lo creamos
                iniciarViaje(idEquipo, posicion, equipoInfo);
                return;
            }

            // Leer archivo existente
            Map<String, Object> viajeData = objectMapper.readValue(file, Map.class);
            List<Map<String, Object>> posiciones = (List<Map<String, Object>>) viajeData.get("posiciones");

            if (posiciones == null) {
                posiciones = new ArrayList<>();
                viajeData.put("posiciones", posiciones);
            }

            // Agregar nueva posición
            Map<String, Object> posicionData = new HashMap<>();
            posicionData.put("timestamp", posicion.getFecha());
            posicionData.put("latitud", posicion.getLatitud());
            posicionData.put("longitud", posicion.getLongitud());
            posicionData.put("esFinal", posicion.isFin());
            posiciones.add(posicionData);

            // Actualizar información del equipo si está disponible
            if (equipoInfo != null) {
                Map<String, Object> equipoData = new HashMap<>();
                equipoData.put("id", equipoInfo.getId());
                equipoData.put("latitud", equipoInfo.getLatitud());
                equipoData.put("longitud", equipoInfo.getLongitud());
                viajeData.put("equipoInfo", equipoData);
            }

            // Actualizar última modificación
            viajeData.put("ultimaActualizacion", new Date());

            // Guardar archivo actualizado
            objectMapper.writeValue(file, viajeData);
            System.out.println("Posición agregada al viaje - Total posiciones: " + posiciones.size());

        } catch (IOException e) {
            System.err.println("Error al agregar posición al viaje: " + e.getMessage());
        }
    }

    public void finalizarViaje(Long idEquipo) {
        try {
            String fileNameEnCurso = idEquipo + "_enCurso.json";
            File fileEnCurso = new File(jsonStoragePath, fileNameEnCurso);

            if (!fileEnCurso.exists()) {
                System.err.println("Archivo de viaje en curso no encontrado: " + fileNameEnCurso);
                return;
            }

            // Leer archivo existente
            Map<String, Object> viajeData = objectMapper.readValue(fileEnCurso, Map.class);

            // Actualizar estado
            viajeData.put("estado", "FINALIZADO");
            viajeData.put("fechaFin", new Date());

            // Crear nuevo nombre de archivo: IDMAQUINA_finalizado_fechahora
            LocalDateTime now = LocalDateTime.now();
            String fechaHora = now.format(DateTimeFormatter.ofPattern("ddMMyyyy_HHmm"));
            String fileNameFinalizado = idEquipo + "_finalizado_" + fechaHora + ".json";
            File fileFinalizado = new File(jsonStoragePath, fileNameFinalizado);

            // Guardar con nuevo nombre
            objectMapper.writeValue(fileFinalizado, viajeData);
            System.out.println("Viaje finalizado - Archivo creado: " + fileFinalizado.getAbsolutePath());

            // Eliminar archivo en curso
            if (fileEnCurso.delete()) {
                System.out.println("Archivo en curso eliminado: " + fileEnCurso.getAbsolutePath());
            } else {
                System.err.println("No se pudo eliminar el archivo en curso: " + fileEnCurso.getAbsolutePath());
            }

        } catch (IOException e) {
            System.err.println("Error al finalizar viaje: " + e.getMessage());
        }
    }

    // Métodos originales mantenidos para compatibilidad
    @Override
    public void guardarPosicionEnJSON(Posicion posicion, EquipoSigema equipoInfo) {
        try {
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
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

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