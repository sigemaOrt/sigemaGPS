package com.sigemaGPS.services.implementations;

import com.sigemaGPS.models.Posicion;
import com.sigemaGPS.models.ReporteFinViaje;
import com.sigemaGPS.repositories.IPosicionRepository;
import com.sigemaGPS.utilidades.SigemaException;
import com.sigemaGPS.services.IPosicionService;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@Transactional
public class PosicionService implements IPosicionService {

    private IPosicionRepository posicionRepository;
    private final Map<Long, Boolean> estadoEquipos = new ConcurrentHashMap<>();

    public PosicionService(IPosicionRepository posicionRepository){
        this.posicionRepository = posicionRepository;
    }

    @Override
    public List<Posicion> obtenerTodasPorIdEquipo(Long idEquipo, LocalDate fecha) throws Exception {
        List<Posicion> posiciones = posicionRepository.findByIdEquipoAndFecha(idEquipo, fecha).orElse(null);

        if(posiciones == null){
            posiciones = new ArrayList<>();
        }

        Collections.sort(posiciones);

        return posiciones;
    }

    @Override
    public Posicion Crear(Posicion posicion) throws Exception {
        if(posicion.getIdEquipo() == null || posicion.getIdEquipo() <= 0){
            throw new SigemaException("Debe ingresar un equipo");
        }

        if(posicion.getLatitud() == 0){
            throw new SigemaException("Debe ingresar la latitud");
        }

        if(posicion.getLongitud() == 0){
            throw new SigemaException("Debe ingresar la longitud");
        }

        posicion = posicionRepository.save(posicion);
        estadoEquipos.put(posicion.getIdEquipo(), true);

        return posicion;
    }

    @Override
    public ReporteFinViaje obtenerReporteViaje(Long idEquipo, LocalDate fecha) throws Exception {
        List<Posicion> posiciones = obtenerTodasPorIdEquipo(idEquipo, fecha);
        if (posiciones == null || posiciones.size() < 2) {
            return new ReporteFinViaje(null, fecha, idEquipo, 0, 0);
        }

        double totalKm = 0;
        long tiempoUsoMillis = 0;

        Posicion anterior = posiciones.getFirst();

        for (int i = 1; i < posiciones.size(); i++) {
            Posicion actual = posiciones.get(i);

            double distancia = calcularDistanciaKm(
                    anterior.getLatitud(), anterior.getLongitud(),
                    actual.getLatitud(), actual.getLongitud());

            long tiempoEntrePuntos = actual.getFecha().getTime() - anterior.getFecha().getTime();

            if (distancia >= 0.01 || tiempoEntrePuntos < 30 * 60 * 1000) {
                totalKm += distancia;
                tiempoUsoMillis += tiempoEntrePuntos;
            }

            anterior = actual;
        }

        double horasUso = tiempoUsoMillis / (1000.0 * 60 * 60);

        return new ReporteFinViaje(
                posiciones.getLast(),
                fecha,
                idEquipo,
                horasUso,
                totalKm
        );
    }

    @Override
    public boolean estaEnUso(Long idEquipo) throws Exception{
        if(!estadoEquipos.get(idEquipo)){
            return false;
        }

        Posicion ultimaPosicion = posicionRepository.findFirstByIdEquipoOrderByFechaDesc(idEquipo).orElse(null);

        if(ultimaPosicion == null){
            estadoEquipos.put(idEquipo, false);
            return false;
        }

        Date horaActual = new Date();
        long diferenciaMillis = horaActual.getTime() - ultimaPosicion.getFecha().getTime();
        long tiempoMaxSinUso = 15 * 60 * 1000;

        //Si la diferencia es mayor a 15 minutos significa que ya no esta en uso.

        boolean enUso = diferenciaMillis < tiempoMaxSinUso;
        estadoEquipos.put(idEquipo, enUso);

        return enUso;
    }

    @Override
    public void setEnUso(Long idEquipo, boolean enUso) throws Exception {
        estadoEquipos.put(idEquipo, enUso);
    }


    private double calcularDistanciaKm(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}

