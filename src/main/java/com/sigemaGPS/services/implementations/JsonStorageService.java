package com.sigemaGPS.services.implementations;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.models.enums.UnidadMedida;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import com.sigemaGPS.services.IJsonStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class JsonStorageService implements IJsonStorageService {

    private static final Logger logger = LoggerFactory.getLogger(JsonStorageService.class);
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

    public void iniciarViaje(Long idEquipo, Posicion posicion) {
        try {
            Map<String, Object> viajeData = new HashMap<>();
            viajeData.put("idEquipo", idEquipo);
            viajeData.put("fechaInicio", new Date());
            viajeData.put("estado", "EN_CURSO");

            List<Map<String, Object>> posiciones = new ArrayList<>();
            Map<String, Object> posicionData = crearPosicionData(posicion);
            posiciones.add(posicionData);

            viajeData.put("posiciones", posiciones);

            Map<String, Object> calculos = new HashMap<>();
            calculos.put("totalHoras", 0.0);
            calculos.put("totalKilometros", 0.0);
            calculos.put("valorSegunUnidadMedida", 0.0);

            calculos.put("unidadMedidaUtilizada", posicion.getUnidadMedida());

            if ("HT".equals(posicion.getUnidadMedida())) {
                calculos.put("ht", 0.0);
                calculos.put("km", 0.0);
            } else if ("KMs".equals(posicion.getUnidadMedida())) {
                calculos.put("km", 0.0);
                calculos.put("ht", 0.0);
            } else {
                calculos.put("ht", 0.0);
                calculos.put("km", 0.0);
            }

            viajeData.put("calculos", calculos);

            String fileName = idEquipo + "_enCurso.json";
            File file = new File(jsonStoragePath, fileName);

            objectMapper.writeValue(file, viajeData);
            logger.info("Viaje iniciado - Archivo creado: {} con unidad de medida: {}", file.getAbsolutePath(), posicion.getUnidadMedida());

        } catch (IOException e) {
            logger.error("Error al iniciar viaje en JSON: {}", e.getMessage());
        }
    }

    public void agregarPosicionAViaje(Long idEquipo, Posicion posicion) {
        try {
            String fileName = idEquipo + "_enCurso.json";
            File file = new File(jsonStoragePath, fileName);

            if (!file.exists()) {
                logger.error("Archivo de viaje en curso no encontrado: {}", fileName);
                iniciarViaje(idEquipo, posicion);
                return;
            }

            Map<String, Object> viajeData = objectMapper.readValue(file, Map.class);
            List<Map<String, Object>> posiciones = (List<Map<String, Object>>) viajeData.get("posiciones");

            if (posiciones == null) {
                posiciones = new ArrayList<>();
                viajeData.put("posiciones", posiciones);
            }

            Map<String, Object> posicionData = crearPosicionData(posicion);
            posiciones.add(posicionData);

            if (posiciones.size() > 1) {
                actualizarCalculosIntermedios(viajeData, posiciones);
            }

            viajeData.put("ultimaActualizacion", new Date());

            objectMapper.writeValue(file, viajeData);
            logger.info("Posición agregada al viaje - Total posiciones: {}", posiciones.size());

        } catch (IOException e) {
            logger.error("Error al agregar posición al viaje: {}", e.getMessage());
        }
    }

    public void finalizarViajeConCalculo(Long idEquipo, double valorCalculado, ReporteFinViaje reporte) {
        try {
            String fileNameEnCurso = idEquipo + "_enCurso.json";
            File fileEnCurso = new File(jsonStoragePath, fileNameEnCurso);

            if (!fileEnCurso.exists()) {
                logger.error("Archivo de viaje en curso no encontrado: {}", fileNameEnCurso);
                return;
            }

            Map<String, Object> viajeData = objectMapper.readValue(fileEnCurso, Map.class);

            viajeData.put("estado", "FINALIZADO");
            viajeData.put("fechaFin", new Date());

            actualizarEquipoConUltimaPosicion(viajeData);

            if (reporte != null && reporte.getTotalHoras() == 0.0 && reporte.getTotalKMs() == 0.0) {
                List<Map<String, Object>> posiciones = (List<Map<String, Object>>) viajeData.get("posiciones");

                if (posiciones != null && posiciones.size() > 1) {
                    double totalHoras = calcularTiempoTotalHoras(posiciones);
                    double totalKMs = calcularDistanciaTotalKm(posiciones);

                    reporte.setTotalHoras(totalHoras);
                    reporte.setTotalKMs(totalKMs);

                    logger.info("Se calcularon valores para el reporte porque venía vacío - Horas: {}, KM: {}", totalHoras, totalKMs);
                }
            }

            actualizarCalculosFinales(viajeData, valorCalculado, reporte);
            crearArchivoFinalizado(idEquipo, viajeData);
            eliminarArchivoEnCurso(fileEnCurso);

        } catch (IOException e) {
            logger.error("Error al finalizar viaje con cálculo: {}", e.getMessage());
        }
    }

    public void finalizarViaje(Long idEquipo) {
        finalizarViajeConCalculo(idEquipo, 0.0, null);
    }

    private Map<String, Object> crearPosicionData(Posicion posicion) {
        Map<String, Object> posicionData = new HashMap<>();
        posicionData.put("timestamp", posicion.getFecha());
        System.out.println();

        String latitudFormateada = String.format(Locale.US, "%.6f", posicion.getLatitud());
        String longitudFormateada = String.format(Locale.US, "%.6f", posicion.getLongitud());

        posicionData.put("latitud", latitudFormateada);
        posicionData.put("longitud", longitudFormateada);
        posicionData.put("esFinal", posicion.isFin());

        return posicionData;
    }

    private void actualizarCalculosFinales(Map<String, Object> viajeData, double valorCalculado, ReporteFinViaje reporte) {
        Map<String, Object> calculos = (Map<String, Object>) viajeData.get("calculos");
        if (calculos == null) {
            calculos = new HashMap<>();
            viajeData.put("calculos", calculos);
        }

        String unidadMedida = obtenerUnidadMedidaDesdeViaje(viajeData);

        double totalHoras = reporte != null ? reporte.getTotalHoras() : 0.0;
        double totalKilometros = reporte != null ? reporte.getTotalKMs() : 0.0;

        double horasRedondeadas = Math.round(totalHoras * 100.0) / 100.0;
        double kilometrosRedondeados = Math.round(totalKilometros * 1000000.0) / 1000000.0;

        double valorRedondeado;
        if ("HT".equals(unidadMedida)) {
            valorRedondeado = Math.round(valorCalculado * 100.0) / 100.0;
        } else if ("KMs".equals(unidadMedida)) {
            valorRedondeado = Math.round(valorCalculado * 1000000.0) / 1000000.0;
        } else {
            valorRedondeado = Math.round(valorCalculado * 100.0) / 100.0;
        }

        calculos.put("totalHoras", horasRedondeadas);
        calculos.put("totalKilometros", kilometrosRedondeados);
        calculos.put("valorSegunUnidadMedida", valorRedondeado);
        calculos.put("fechaCalculo", new Date());
        calculos.put("unidadMedidaUtilizada", unidadMedida);

        if ("HT".equals(unidadMedida)) {
            calculos.put("ht", valorRedondeado);
            calculos.put("km", 0.0);
        } else if ("KMs".equals(unidadMedida)) {
            calculos.put("km", valorRedondeado);
            calculos.put("ht", 0.0);
        } else {
            calculos.put("ht", horasRedondeadas);
            calculos.put("km", kilometrosRedondeados);
        }

        Map<String, Object> detalleCalculo = new HashMap<>();
        detalleCalculo.put("tipoCalculo", unidadMedida);
        detalleCalculo.put("valorEnviado", valorRedondeado);
        detalleCalculo.put("horasCalculadas", horasRedondeadas);
        detalleCalculo.put("kilometrosCalculados", kilometrosRedondeados);
        calculos.put("detalleCalculo", detalleCalculo);

        logger.info("Cálculos finales actualizados - Unidad: {}, Valor final: {}, HT: {}, KM: {}",
                unidadMedida, valorRedondeado, calculos.get("ht"), calculos.get("km"));
    }

    private void actualizarCalculosIntermedios(Map<String, Object> viajeData, List<Map<String, Object>> posiciones) {
        try {
            Map<String, Object> calculos = (Map<String, Object>) viajeData.get("calculos");
            if (calculos == null) {
                calculos = new HashMap<>();
                viajeData.put("calculos", calculos);
            }

            double totalKm = calcularDistanciaTotalKm(posiciones);
            double totalHoras = calcularTiempoTotalHoras(posiciones);

            double horasRedondeadas = Math.round(totalHoras * 100.0) / 100.0; // 2 decimales
            double kilometrosRedondeados = Math.round(totalKm * 1000000.0) / 1000000.0; // 6 decimales

            calculos.put("totalKilometros", kilometrosRedondeados);
            calculos.put("totalHoras", horasRedondeadas);

            String unidadMedida = obtenerUnidadMedidaDesdeViaje(viajeData);
            double valorCalculado = 0.0;

            if ("HT".equals(unidadMedida)) {
                valorCalculado = horasRedondeadas;
                calculos.put("ht", valorCalculado);
                calculos.put("km", 0.0);
            } else if ("KMs".equals(unidadMedida)) {
                valorCalculado = kilometrosRedondeados;
                calculos.put("km", valorCalculado);
                calculos.put("ht", 0.0);
            } else {
                calculos.put("ht", horasRedondeadas);
                calculos.put("km", kilometrosRedondeados);
            }

            calculos.put("valorSegunUnidadMedida", valorCalculado);
            calculos.put("unidadMedidaUtilizada", unidadMedida);

            logger.info("Cálculos intermedios actualizados - Horas: {}, KM: {}, Valor: {} ({}), HT: {}, KM: {}",
                    horasRedondeadas, kilometrosRedondeados, valorCalculado, unidadMedida, calculos.get("ht"), calculos.get("km"));

        } catch (Exception e) {
            logger.error("Error al actualizar cálculos intermedios: {}", e.getMessage());
        }
    }

    private String obtenerUnidadMedidaDesdeViaje(Map<String, Object> viajeData) {
        try {
            Map<String, Object> equipoInfo = (Map<String, Object>) viajeData.get("equipoInfo");
            if (equipoInfo != null) {
                return (String) equipoInfo.get("unidadMedida");
            }
        } catch (Exception e) {
            logger.error("Error al obtener unidad de medida: {}", e.getMessage());
        }
        return null;
    }

    private double calcularDistanciaTotalKm(List<Map<String, Object>> posiciones) {
        if (posiciones.size() < 2) return 0.0;

        double totalKm = 0.0;
        for (int i = 1; i < posiciones.size(); i++) {
            Map<String, Object> anterior = posiciones.get(i - 1);
            Map<String, Object> actual = posiciones.get(i);

            double lat1 = Double.parseDouble((String) anterior.get("latitud"));
            double lon1 = Double.parseDouble((String) anterior.get("longitud"));
            double lat2 = Double.parseDouble((String) actual.get("latitud"));
            double lon2 = Double.parseDouble((String) actual.get("longitud"));

            double distancia = calcularDistanciaKm(lat1, lon1, lat2, lon2);
            totalKm += distancia;
        }

        return totalKm;
    }

    private Long toLongSafe(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof java.util.Date) return ((java.util.Date) value).getTime();
        if (value instanceof Instant) return ((Instant) value).toEpochMilli();
        if (value instanceof OffsetDateTime) return ((OffsetDateTime) value).toInstant().toEpochMilli();
        if (value instanceof ZonedDateTime) return ((ZonedDateTime) value).toInstant().toEpochMilli();

        String s = value.toString().trim();

        // 1) String puramente numérico (epoch en ms o s)
        if (s.matches("^-?\\d+$")) {
            try {
                // si tiene 10 dígitos, probablemente sea segundos -> convertir a ms
                if (s.length() == 10) return Long.parseLong(s) * 1000L;
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                // sigue intentando otras opciones
            }
        }

        // 2) ISO-8601 (Instant.parse)
        try {
            Instant inst = Instant.parse(s);
            return inst.toEpochMilli();
        } catch (DateTimeParseException ignore) {}

        // 3) Formato tipo "Mon Sep 15 20:07:57 UYT 2025"
        DateTimeFormatter f1 = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(s, f1);
            return zdt.toInstant().toEpochMilli();
        } catch (DateTimeParseException ignore) {}

        // 4) Sin zona: "Mon Sep 15 20:07:57 2025"
        DateTimeFormatter f2 = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH);
        try {
            LocalDateTime ldt = LocalDateTime.parse(s, f2);
            return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignore) {}

        // 5) RFC_1123 (por si viene con coma u otro formato)
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant().toEpochMilli();
        } catch (DateTimeParseException ignore) {}

        // 6) Fallback con SimpleDateFormat (otro intento con locale inglés)
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
            Date d = sdf.parse(s);
            return d.getTime();
        } catch (Exception ignore) {}

        // 7) Extraer dígitos sueltos (último recurso)
        String digits = s.replaceAll("\\D+", "");
        if (digits.length() >= 10) {
            try {
                if (digits.length() == 10) return Long.parseLong(digits) * 1000L;
                if (digits.length() > 13) digits = digits.substring(0, 13); // recortar si hay ruido
                return Long.parseLong(digits);
            } catch (Exception ignore) {}
        }

        logger.warn("No pude parsear timestamp: '{}'", s);
        return null;
    }

    public double calcularTiempoTotalHoras(List<Map<String, Object>> posiciones) {
        if (posiciones == null || posiciones.size() < 2) return 0.0;

        long tiempoTotal = 0L;
        for (int i = 1; i < posiciones.size(); i++) {
            Object tsAnteriorObj = posiciones.get(i - 1).get("timestamp");
            Object tsActualObj   = posiciones.get(i).get("timestamp");

            Long tsAnterior = toLongSafe(tsAnteriorObj);
            Long tsActual   = toLongSafe(tsActualObj);

            if (tsAnterior == null || tsActual == null) {
                logger.warn("Omitiendo par índices {}-{} por timestamp inválido: {} / {}", i-1, i, tsAnteriorObj, tsActualObj);
                continue; // no queremos lanzar excepción por un timestamp mal formado
            }

            long diferencia = tsActual - tsAnterior;
            if (diferencia > 0) tiempoTotal += diferencia; // descartamos diferencias negativas
        }

        return tiempoTotal / (1000.0 * 60 * 60);
    }

    private double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radio de la Tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void actualizarEquipoConUltimaPosicion(Map<String, Object> viajeData) {
        List<Map<String, Object>> posiciones = (List<Map<String, Object>>) viajeData.get("posiciones");
        if (posiciones != null && !posiciones.isEmpty()) {
            Map<String, Object> ultimaPosicion = posiciones.get(posiciones.size() - 1);
            Map<String, Object> equipoData = (Map<String, Object>) viajeData.get("equipoInfo");

            if (equipoData != null) {
                equipoData.put("latitud", ultimaPosicion.get("latitud"));
                equipoData.put("longitud", ultimaPosicion.get("longitud"));
            }
        }
    }

    private void crearArchivoFinalizado(Long idEquipo, Map<String, Object> viajeData) throws IOException {
        LocalDateTime now = LocalDateTime.now();
        String fechaHora = now.format(DateTimeFormatter.ofPattern("ddMMyyyy_HHmm"));
        String fileNameFinalizado = idEquipo + "_finalizado_" + fechaHora + ".json";
        File fileFinalizado = new File(jsonStoragePath, fileNameFinalizado);

        objectMapper.writeValue(fileFinalizado, viajeData);
        logger.info("Viaje finalizado - Archivo creado: {}", fileFinalizado.getAbsolutePath());
    }

    private void eliminarArchivoEnCurso(File fileEnCurso) {
        if (fileEnCurso.delete()) {
            logger.info("Archivo en curso eliminado: {}", fileEnCurso.getAbsolutePath());
        } else {
            logger.error("No se pudo eliminar el archivo en curso: {}", fileEnCurso.getAbsolutePath());
        }
    }

    public Map<String, Object> obtenerDatosViajeParaReporte(Long idEquipo, String fechaHora) {
        try {
            String fileName = idEquipo + "_finalizado_" + fechaHora + ".json";
            File file = new File(jsonStoragePath, fileName);

            if (!file.exists()) {
                logger.error("Archivo de viaje finalizado no encontrado: {}", fileName);
                return null;
            }

            Map<String, Object> viajeData = objectMapper.readValue(file, Map.class);
            logger.info("Datos del viaje obtenidos para reporte, incluyendo cálculos de unidad de medida");

            return viajeData;

        } catch (IOException e) {
            logger.error("Error al obtener datos del viaje: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void guardarPosicionEnJSON(Posicion posicion) {
        try {
            Map<String, Object> posicionCompleta = new HashMap<>();
            posicionCompleta.put("timestamp", new Date());
            posicionCompleta.put("tipo", "posicion");
            posicionCompleta.put("posicion", posicion);

            String fileName = String.format("posicion_equipo_%d_%s.json",
                    posicion.getIdEquipo(),
                    new Date().getTime());

            File file = new File(jsonStoragePath, fileName);
            objectMapper.writeValue(file, posicionCompleta);

            logger.info("Posición guardada en JSON: {}", file.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Error al guardar posición en JSON: {}", e.getMessage());
        }
    }

    @Override
    public void guardarReporteEnJSON(ReporteSigemaDTO reporte, String tipo) {
        try {
            Map<String, Object> reporteCompleto = new HashMap<>();
            reporteCompleto.put("timestamp", new Date());
            reporteCompleto.put("tipo", tipo);
            reporteCompleto.put("reporte", reporte);

            if (reporte.getUnidadMedida() != null) {
                Map<String, Object> calculosInfo = new HashMap<>();
                calculosInfo.put("unidadMedida", reporte.getUnidadMedida().toString());
                calculosInfo.put("valorPrincipal", reporte.getUnidadMedida() == UnidadMedida.HT ?
                        reporte.getHorasDeTrabajo() : reporte.getKilometros());
                calculosInfo.put("horasCalculadas", reporte.getHorasDeTrabajo());
                calculosInfo.put("kilometrosCalculados", reporte.getKilometros());
                reporteCompleto.put("calculos", calculosInfo);
            }

            String fileName = String.format("%s_equipo_%d_%s.json",
                    tipo,
                    reporte.getIdEquipo(),
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")));

            File file = new File(jsonStoragePath, fileName);
            objectMapper.writeValue(file, reporteCompleto);

            logger.info("Reporte guardado en JSON: {}", file.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Error al guardar reporte en JSON: {}", e.getMessage());
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

            logger.info("Request guardado en JSON: {}", file.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Error al guardar request en JSON: {}", e.getMessage());
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

            logger.info("Response guardado en JSON: {}", file.getAbsolutePath());

        } catch (IOException e) {
            logger.error("Error al guardar response en JSON: {}", e.getMessage());
        }
    }
}