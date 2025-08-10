package com.sigemaGPS.services.implementations;

import com.sigemaGPS.Dto.PosicionClienteDTO;
import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.models.enums.UnidadMedida;
import com.sigemaGPS.services.IEmailService;
import com.sigemaGPS.services.IJsonStorageService;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import com.sigemaGPS.models.EquipoSigema;
import com.sigemaGPS.Dto.ReporteSigemaDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PosicionService implements IPosicionService {

    private static final Logger logger = LoggerFactory.getLogger(PosicionService.class);

    private final RestTemplate restTemplate;
    private final IJsonStorageService jsonStorageService;
    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();
    private final Map<Long, Timer> timersEquipos = new ConcurrentHashMap<>();
    private final Map<Long, List<Posicion>> posicionesPorEquipo = new ConcurrentHashMap<>();

    @Autowired
    private IEmailService emailService;


    @Value("${sigema.main.backend.url}")
    private String sigemaBackendUrl;

    @Autowired
    public PosicionService(RestTemplate restTemplate, IJsonStorageService jsonStorageService) {
        this.restTemplate = restTemplate;
        this.jsonStorageService = jsonStorageService;
    }

    @PostConstruct
    public void init() {
        System.out.println("sigema.backend.url = " + sigemaBackendUrl);
        if (sigemaBackendUrl == null || sigemaBackendUrl.isEmpty()) {
            throw new RuntimeException("La propiedad sigema.backend.url no está configurada correctamente");
        }
    }

    @Override
    public ReporteSigemaDTO iniciarTrabajo(Long idEquipo, String jwtToken, PosicionClienteDTO ubicacion) throws Exception {
        System.out.println("Iniciando trabajo para equipo: " + idEquipo);

        double lat = ubicacion.getLatitud();
        double lon = ubicacion.getLongitud();

        Posicion posicion = new Posicion();
        posicion.setIdEquipo(idEquipo);
        posicion.setLatitud(lat);
        posicion.setLongitud(lon);
        posicion.setFecha(new Date());
        posicion.setFin(false);

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicion);
        setEnUso(idEquipo, true);

        jsonStorageService.iniciarViaje(idEquipo, posicion, null);

        Timer timer = new Timer(true);
        timersEquipos.put(idEquipo, timer);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    registrarPosicionConJSON(idEquipo, false, jwtToken, false);
                } catch (Exception e) {
                    System.err.println("Error al registrar posición periódica para equipo " + idEquipo + ": " + e.getMessage());
                }
            }
        }, 15 * 60 * 1000, 15 * 60 * 1000);

        return new ReporteSigemaDTO(idEquipo, lat, lon, posicion.getFecha(), 0.0, 0.0, null, null);
    }

    @Override
    public ReporteSigemaDTO finalizarTrabajo(Long idEquipo, String jwtToken, PosicionClienteDTO ubicacion) throws Exception {

        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();

        double lat = ubicacion.getLatitud();
        double lon = ubicacion.getLongitud();
        String emails = String.join(",", ubicacion.getEmails());

        Posicion posicionFinal = new Posicion();
        posicionFinal.setIdEquipo(idEquipo);
        posicionFinal.setLatitud(lat);
        posicionFinal.setLongitud(lon);
        posicionFinal.setFecha(new Date());
        posicionFinal.setFin(true);

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicionFinal);

        // Obtener equipo con información completa
        EquipoSigema equipo = obtenerEquipoCompleto(idEquipo, jwtToken);

        jsonStorageService.agregarPosicionAViaje(idEquipo, posicionFinal, equipo);
        setEnUso(idEquipo, false);

        ReporteFinViaje reporte = obtenerReporteViaje(idEquipo, LocalDate.now());

        // Calcular valor según unidad de medida
        double valorCalculado = calcularValorSegunUnidadMedida(equipo, reporte);

        jsonStorageService.finalizarViajeConCalculo(idEquipo, valorCalculado, reporte);

        // Crear DTO con el valor calculado
        ReporteSigemaDTO dto = crearReporteSigemaDTO(
                idEquipo, lat, lon, posicionFinal.getFecha(),
                equipo, reporte, valorCalculado
        );


        boolean envioExitoso = enviarReporteAlBackendPrincipal(dto, jwtToken, equipo, emails);

//        if (!envioExitoso) {
//            emailService.enviarCorreoFinalizacionTrabajo(idEquipo, destinatario, asunto, cuerpo);
//        }


        return dto;
    }

    /**
     * Obtiene el equipo completo con toda su información
     */
    private EquipoSigema obtenerEquipoCompleto(Long idEquipo, String jwtToken) throws Exception {
        try {
            String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(jwtToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);
            EquipoSigema equipo = response.getBody();

            if (equipo == null) {
                throw new SigemaException("Equipo no encontrado con ID: " + idEquipo);
            }

            logger.info("Equipo obtenido - ID: {}", equipo.getId());
            logger.info("Unidad del equipo: {}", (equipo.getUnidad() != null ? equipo.getUnidad().getId() : "null"));
            logger.info("Unidad de medida: {}", (equipo.getModeloEquipo() != null && equipo.getModeloEquipo().getUnidadMedida() != null
                    ? equipo.getModeloEquipo().getUnidadMedida() : "null"));

            return equipo;

        } catch (Exception e) {
            logger.error("Error al obtener información del equipo {}: {}", idEquipo, e.getMessage());
            throw new SigemaException("Error al obtener información del equipo: " + e.getMessage());
        }
    }

    /**
     * Calcula el valor según la unidad de medida del equipo
     */
    private double calcularValorSegunUnidadMedida(EquipoSigema equipo, ReporteFinViaje reporte) {
        if (equipo == null || equipo.getModeloEquipo() == null) {
            logger.warn("Equipo o modelo de equipo es null, retornando 0");
            return 0.0;
        }

        UnidadMedida unidadMedida = equipo.getModeloEquipo().getUnidadMedida();

        if (unidadMedida == null) {
            logger.warn("Unidad de medida es null, retornando 0");
            return 0.0;
        }

        double valor = 0.0;

        switch (unidadMedida) {
            case HT:
                valor = reporte != null ? reporte.getTotalHoras() : 0.0;
                logger.info("Calculando HORAS - Valor: {}", valor);
                break;

            case KMs:
                valor = reporte != null ? reporte.getTotalKMs() : 0.0;
                logger.info("Calculando KILOMETROS - Valor: {}", valor);
                break;

            default:
                logger.warn("Unidad de medida desconocida: {}, retornando 0", unidadMedida);
                valor = 0.0;
                break;
        }

        logger.info("Valor final calculado para unidad {}: {}", unidadMedida, valor);
        return valor;
    }

    /**
     * Crea el DTO del reporte con la información calculada
     */
    private ReporteSigemaDTO crearReporteSigemaDTO(Long idEquipo, double lat, double lon, Date fecha,
                                                   EquipoSigema equipo, ReporteFinViaje reporte,
                                                   double valorCalculado) throws Exception {

        // Validar que el equipo tenga una unidad asignada
        if (equipo.getUnidad() == null || equipo.getUnidad().getId() == null) {
            throw new SigemaException("El equipo debe tener una unidad asignada");
        }

        Long idUnidad = equipo.getUnidad().getId();
        UnidadMedida unidadMedida = equipo.getModeloEquipo() != null ?
                equipo.getModeloEquipo().getUnidadMedida() : null;

        ReporteSigemaDTO dto = new ReporteSigemaDTO();
        dto.setIdEquipo(idEquipo);
        dto.setLatitud(lat);
        dto.setLongitud(lon);
        dto.setFecha(fecha);
        dto.setUnidadMedida(unidadMedida);
        dto.setUnidad(idUnidad);

        // Asignar el valor calculado según la unidad de medida
        if (unidadMedida == UnidadMedida.HT) {
            dto.setHorasDeTrabajo(valorCalculado);
            dto.setKilometros(0.0);
        } else if (unidadMedida == UnidadMedida.KMs) {
            dto.setKilometros(valorCalculado);
            dto.setHorasDeTrabajo(0.0);
        } else {
            dto.setHorasDeTrabajo(reporte != null ? reporte.getTotalHoras() : 0.0);
            dto.setKilometros(reporte != null ? reporte.getTotalKMs() : 0.0);
        }

        logger.info("DTO creado - Equipo: {}, Unidad: {}, UnidadMedida: {}, Valor: {}",
                idEquipo, idUnidad, unidadMedida, valorCalculado);

        return dto;
    }

    /**
     * Envía el reporte al backend principal
     */
    private boolean enviarReporteAlBackendPrincipal(ReporteSigemaDTO dto, String jwtToken, EquipoSigema equipo, String emails){
        int intentos = 0;
        boolean enviado = false;
        Exception ultimoError = null;

        while (intentos < 3 && !enviado) {
            try {
                logger.info("Intento {} de envío al backend principal", intentos + 1);

                if (dto.getUnidad() == null || dto.getUnidad() == 0) {
                    throw new SigemaException("El idUnidad no puede ser nulo o cero");
                }

                ReporteSigemaDTO reporteParaEnvio = new ReporteSigemaDTO();
                reporteParaEnvio.setIdEquipo(dto.getIdEquipo());
                reporteParaEnvio.setLatitud(dto.getLatitud());
                reporteParaEnvio.setLongitud(dto.getLongitud());
                reporteParaEnvio.setFecha(dto.getFecha());
                reporteParaEnvio.setHorasDeTrabajo(dto.getHorasDeTrabajo());
                reporteParaEnvio.setKilometros(dto.getKilometros());
                reporteParaEnvio.setIdUnidad(dto.getUnidad());
                reporteParaEnvio.setUnidadMedida(dto.getUnidadMedida());

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(jwtToken);
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<ReporteSigemaDTO> requestEntity = new HttpEntity<>(reporteParaEnvio, headers);
                String url = sigemaBackendUrl + "/api/reporte";

                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        requestEntity,
                        String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("✅ Reporte enviado exitosamente. Estado: {}", response.getStatusCode());
                    enviado = true;
                } else {
                    logger.warn("⚠️ Respuesta no exitosa: {}", response.getStatusCode());
                    intentos++;
                }

            } catch (Exception e) {
                ultimoError = e;
                logger.error("Error al enviar el reporte (intento {}): {}", intentos + 1, e.getMessage());
                intentos++;
            }
        }

        // Si no se logró enviar después de 3 intentos, se manda el mail
        if (!enviado && intentos >= 3) {
            logger.error("❌ No se pudo enviar el reporte tras 3 intentos. Enviando correo de alerta...");

            String destinatario = emails;
            String asunto = "🚨 Fallo al enviar reporte del equipo " + dto.getIdEquipo();
            String cuerpo = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: auto;'>"
                    + "<h2 style='color: #e74c3c;'>Error al Enviar Reporte</h2>"
                    + "<p>No se pudo enviar el reporte al backend principal luego de 3 intentos.</p>"
                    + "<ul>"
                    + "<li><strong>Equipo:</strong> " + dto.getIdEquipo() + "</li>"
                    + "<li><strong>Latitud:</strong> " + dto.getLatitud() + "</li>"
                    + "<li><strong>Longitud:</strong> " + dto.getLongitud() + "</li>"
                    + "<li><strong>Horas de trabajo:</strong> " + dto.getHorasDeTrabajo() + "</li>"
                    + "<li><strong>Kilómetros:</strong> " + dto.getKilometros() + "</li>"
                    + "</ul>"
                    + (ultimoError != null ? "<p><strong>Error:</strong> " + ultimoError.getMessage() + "</p>" : "")
                    + "<p style='font-size: 12px; color: #999;'>Este mensaje fue generado automáticamente.</p>"
                    + "</div>";

            emailService.enviarCorreoFinalizacionTrabajo(dto.getIdEquipo(), destinatario, asunto, cuerpo);
        }
        return enviado;
    }



    @Override
    public void eliminarTrabajo(Long idEquipo) throws Exception {
        Timer timer = timersEquipos.remove(idEquipo);
        if (timer != null) timer.cancel();
        setEnUso(idEquipo, false);
    }

    private void registrarPosicionConJSON(Long idEquipo, boolean fin, String jwtToken, boolean inicioViaje) throws Exception {
        String url = sigemaBackendUrl + "/api/equipos/" + idEquipo;
        System.out.println("Registrando posición en URL: " + url);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        double lat;
        double lon;
        EquipoSigema equipo = null;

        try {
            ResponseEntity<EquipoSigema> response = restTemplate.exchange(url, HttpMethod.GET, entity, EquipoSigema.class);
            equipo = response.getBody();

            if (equipo == null) throw new SigemaException("Equipo no encontrado");
            lat = equipo.getLatitud();
            lon = equipo.getLongitud();

            System.out.println("Posición obtenida - Lat: " + lat + ", Lon: " + lon);
        } catch (Exception e) {
            System.err.println("Error al obtener posición del equipo: " + e.getMessage());
            throw new SigemaException("Error al obtener la posición del equipo: " + e.getMessage());
        }

        Posicion posicion = new Posicion();
        posicion.setIdEquipo(idEquipo);
        posicion.setLatitud(lat);
        posicion.setLongitud(lon);
        posicion.setFecha(new Date());
        posicion.setFin(fin);

        posicionesPorEquipo.computeIfAbsent(idEquipo, k -> new ArrayList<>()).add(posicion);
        estadoEquipos.put(idEquipo, true);

        if (inicioViaje) {
            jsonStorageService.iniciarViaje(idEquipo, posicion, equipo);
        } else {
            jsonStorageService.agregarPosicionAViaje(idEquipo, posicion, equipo);
        }
    }

    @Override
    public List<Posicion> obtenerTodasPorIdEquipo(Long idEquipo, LocalDate fecha) {
        List<Posicion> todas = posicionesPorEquipo.getOrDefault(idEquipo, new ArrayList<>());
        List<Posicion> filtradas = new ArrayList<>();

        for (Posicion p : todas) {
            if (p.getFecha().toInstant().atZone(TimeZone.getDefault().toZoneId()).toLocalDate().equals(fecha)) {
                filtradas.add(p);
            }
        }

        filtradas.sort(Comparator.naturalOrder());
        return filtradas;
    }

    @Override
    public ReporteFinViaje obtenerReporteViaje(Long idEquipo, LocalDate fecha) throws Exception {
        List<Posicion> posiciones = obtenerTodasPorIdEquipo(idEquipo, fecha);
        if (posiciones.size() < 2) return new ReporteFinViaje(null, fecha, idEquipo, 0, 0);

        double totalKm = 0;
        long tiempoUso = 0;
        Posicion anterior = posiciones.get(0);

        for (int i = 1; i < posiciones.size(); i++) {
            Posicion actual = posiciones.get(i);
            double distancia = calcularDistanciaKm(anterior.getLatitud(), anterior.getLongitud(), actual.getLatitud(), actual.getLongitud());
            long tiempo = actual.getFecha().getTime() - anterior.getFecha().getTime();

            if (distancia >= 0.01 || tiempo < 30 * 60 * 1000) {
                totalKm += distancia;
                tiempoUso += tiempo;
            }

            anterior = actual;
        }

        double horas = tiempoUso / (1000.0 * 60 * 60);

        logger.info("Reporte calculado para equipo {}: {} horas, {} km", idEquipo, horas, totalKm);

        return new ReporteFinViaje(posiciones.get(posiciones.size() - 1), fecha, idEquipo, horas, totalKm);
    }

    @Override
    public boolean estaEnUso(Long idEquipo) {
        if (!estadoEquipos.getOrDefault(idEquipo, false)) return false;
        List<Posicion> posiciones = posicionesPorEquipo.getOrDefault(idEquipo, new ArrayList<>());
        if (posiciones.isEmpty()) return false;

        Posicion ultima = posiciones.get(posiciones.size() - 1);
        long diff = new Date().getTime() - ultima.getFecha().getTime();
        boolean enUso = diff < 15 * 60 * 1000;
        estadoEquipos.put(idEquipo, enUso);
        return enUso;
    }

    @Override
    public void setEnUso(Long idEquipo, boolean enUso) {
        estadoEquipos.put(idEquipo, enUso);
    }

    private double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}