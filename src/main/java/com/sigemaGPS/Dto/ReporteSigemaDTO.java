// appsigemagps/src/main/java/com/sigemaGPS/models/dto/ReporteSigemaDTO.java
package com.sigemaGPS.Dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.sigemaGPS.models.enums.UnidadMedida;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.util.Date;
import java.util.Locale;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReporteSigemaDTO {

    private Long idEquipo;

    @JsonSerialize(using = LatLonSerializer.class)
    private double latitud;

    @JsonSerialize(using = LatLonSerializer.class)
    private double longitud;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "America/Montevideo")
    private Date fecha;

    @JsonSerialize(using = HorasTrabajoSerializer.class)
    private double horasDeTrabajo;

    private double kilometros;

    private UnidadMedida unidadMedida;

    private Long idUnidad;

    public ReporteSigemaDTO() {}

    public ReporteSigemaDTO(Long idEquipo, double latitud, double longitud, Date fecha,
                            double horasDeTrabajo, double kilometros,
                            UnidadMedida unidadMedida, Long idUnidad) {
        this.idEquipo = idEquipo;
        this.latitud = latitud;
        this.longitud = longitud;
        this.fecha = fecha;
        this.horasDeTrabajo = horasDeTrabajo;
        this.kilometros = kilometros;
        this.unidadMedida = unidadMedida;
        this.idUnidad = idUnidad;
    }

    public Long getUnidad() {
        return idUnidad;
    }

    public void setUnidad(Long idUnidad) {
        this.idUnidad = idUnidad;
    }

    // ===== SERIALIZADOR PARA LAT/LON (8 decimales) =====
    public static class LatLonSerializer extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNumber(String.format(Locale.US, "%.8f", value));
        }
    }

    // ===== SERIALIZADOR PARA HORAS DE TRABAJO (2 decimales) =====
    public static class HorasTrabajoSerializer extends JsonSerializer<Double> {
        @Override
        public void serialize(Double value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeNumber(String.format(Locale.US, "%.2f", value));
        }
    }
}
