package reservas.procesoreserva.infrastructure.persistence.mapper;

import reservas.procesoreserva.domain.model.ProcesoReserva;
import reservas.procesoreserva.infrastructure.persistence.entity.ProcesoReservaEntity;
import org.springframework.stereotype.Component;

@Component
public class ProcesoReservaMapper {
    public ProcesoReservaEntity toEntity(ProcesoReserva source) {
        if (source == null) {
            return null;
        }
        return ProcesoReservaEntity.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .holdId(source.getHoldId())
                .reservationProcessId(source.getReservationProcessId())
                .reservationId(source.getReservationId())
                .professionalId(source.getProfessionalId())
                .appointmentDate(source.getAppointmentDate())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .status(source.getStatus())
                .expiresAt(source.getExpiresAt())
                .professionalFirstName(source.getProfessionalFirstName())
                .professionalLastName(source.getProfessionalLastName())
                .cancellationReason(source.getCancellationReason())
                .confirmedAt(source.getConfirmedAt())
                .cancelledAt(source.getCancelledAt())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .version(source.getVersion())
                .build();
    }

    public ProcesoReserva toDomainModel(ProcesoReservaEntity source) {
        if (source == null) {
            return null;
        }
        return ProcesoReserva.builder()
                .id(source.getId())
                .userId(source.getUserId())
                .holdId(source.getHoldId())
                .reservationProcessId(source.getReservationProcessId())
                .reservationId(source.getReservationId())
                .professionalId(source.getProfessionalId())
                .appointmentDate(source.getAppointmentDate())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .status(source.getStatus())
                .expiresAt(source.getExpiresAt())
                .professionalFirstName(source.getProfessionalFirstName())
                .professionalLastName(source.getProfessionalLastName())
                .cancellationReason(source.getCancellationReason())
                .confirmedAt(source.getConfirmedAt())
                .cancelledAt(source.getCancelledAt())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .version(source.getVersion())
                .build();
    }

}
