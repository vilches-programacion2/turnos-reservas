package reservas.disponibilidad;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import reservas.catalogo.application.exception.ConsultaCatalogoException;
import reservas.catalogo.domain.model.AgendaProfesional;
import reservas.catalogo.domain.model.HorarioSemanal;
import reservas.catalogo.domain.ports.in.ConsultarAgendaProfesionalUseCase;
import reservas.disponibilidad.application.exception.ConsultaOcupacionesException;
import reservas.disponibilidad.application.exception.SolicitudDisponibilidadInvalidaException;
import reservas.disponibilidad.application.usecases.CalcularDisponibilidadUseCaseImpl;
import reservas.disponibilidad.domain.ports.out.OcupacionesClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CalcularDisponibilidadTest {
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 23);
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private ConsultarAgendaProfesionalUseCase agenda;
    private OcupacionesClient ocupaciones;
    private CalcularDisponibilidadUseCaseImpl useCase;

    @BeforeEach
    void preparar() {
        agenda = mock(ConsultarAgendaProfesionalUseCase.class);
        ocupaciones = mock(OcupacionesClient.class);
        useCase = caso("2026-11-22T20:00:00Z");
    }

    @Test
    void combinaAgendaYOcupacionesOrdenaYDeduplicaSinAgregarTurnosIncompletos() {
        responder(horario(DayOfWeek.MONDAY, "09:00", "10:20", 30),
                horario(DayOfWeek.MONDAY, "09:30", "11:00", 30),
                horario(DayOfWeek.TUESDAY, "07:00", "08:00", 30));
        when(ocupaciones.consultar(42L, LUNES)).thenReturn(Set.of(LocalTime.of(9, 30), LocalTime.of(6, 0)));
        var result = useCase.calcular(42L, LUNES);
        assertThat(result.getAvailableSlots()).containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(10, 30));
        assertThat(result.getProfessionalId()).isEqualTo(42L);
        assertThat(result.getDate()).isEqualTo(LUNES);
        assertThat(result.getVersionAplicada()).isEqualTo(7L);
        verify(agenda).consultar(42L);
        verify(ocupaciones).consultar(42L, LUNES);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rechazaIdAntesDeCualquierLlamada(Long id) {
        assertThatThrownBy(() -> useCase.calcular(id, LUNES)).isInstanceOf(SolicitudDisponibilidadInvalidaException.class);
        verifyNoInteractions(agenda, ocupaciones);
    }

    @Test
    void rechazaFechaNulaOPasadaAntesDeCualquierLlamada() {
        for (LocalDate date : new LocalDate[] {null, LUNES.minusDays(2)}) {
            assertThatThrownBy(() -> useCase.calcular(42L, date)).isInstanceOf(SolicitudDisponibilidadInvalidaException.class);
        }
        verifyNoInteractions(agenda, ocupaciones);
    }

    @Test
    void hoyEsArgentinaAunqueUtcYaSeaMananaYExcluyeHorasTranscurridas() {
        useCase = caso("2026-11-24T01:00:00Z"); // Lunes 22:00 en Argentina.
        responder(horario(DayOfWeek.MONDAY, "21:30", "23:30", 30));
        when(ocupaciones.consultar(42L, LUNES)).thenReturn(Set.of());
        assertThat(useCase.calcular(42L, LUNES).getAvailableSlots())
                .containsExactly(LocalTime.of(22, 0), LocalTime.of(22, 30), LocalTime.of(23, 0));
    }

    @Test
    void noVuelveAMedianocheYDescartaDuracionesQueNoCaben() {
        responder(horario(DayOfWeek.MONDAY, "23:00", "23:59", 30),
                horario(DayOfWeek.MONDAY, "09:00", "09:20", 30),
                horario(DayOfWeek.MONDAY, "09:00", "10:00", Integer.MAX_VALUE));
        when(ocupaciones.consultar(42L, LUNES)).thenReturn(Set.of());
        assertThat(useCase.calcular(42L, LUNES).getAvailableSlots()).containsExactly(LocalTime.of(23, 0));
    }

    @Test
    void sinHorariosDelDiaNoConsultaOcupaciones() {
        responder(horario(DayOfWeek.TUESDAY, "09:00", "10:00", 30));
        assertThat(useCase.calcular(42L, LUNES).getAvailableSlots()).isEmpty();
        verifyNoInteractions(ocupaciones);
    }

    @Test
    void siTodosEstanOcupadosLaListaVaciaEsValida() {
        responder(horario(DayOfWeek.MONDAY, "09:00", "10:00", 30));
        when(ocupaciones.consultar(42L, LUNES)).thenReturn(Set.of(LocalTime.of(9, 0), LocalTime.of(9, 30)));
        assertThat(useCase.calcular(42L, LUNES).getAvailableSlots()).isEmpty();
    }

    @Test
    void errorDeCatalogoNoConsultaOcupaciones() {
        when(agenda.consultar(42L)).thenThrow(new ConsultaCatalogoException(ConsultaCatalogoException.Error.PROFESIONAL_DESHABILITADO));
        assertThatThrownBy(() -> useCase.calcular(42L, LUNES)).isInstanceOf(ConsultaCatalogoException.class);
        verifyNoInteractions(ocupaciones);
    }

    @Test
    void errorDeOcupacionesNoSeConvierteEnDisponibilidadVacia() {
        responder(horario(DayOfWeek.MONDAY, "09:00", "10:00", 30));
        when(ocupaciones.consultar(42L, LUNES)).thenThrow(new ConsultaOcupacionesException(ConsultaOcupacionesException.Error.NO_DISPONIBLE));
        assertThatThrownBy(() -> useCase.calcular(42L, LUNES)).isInstanceOf(ConsultaOcupacionesException.class);
    }

    private CalcularDisponibilidadUseCaseImpl caso(String instant) {
        return new CalcularDisponibilidadUseCaseImpl(agenda, ocupaciones, Clock.fixed(Instant.parse(instant), ARGENTINA));
    }

    private void responder(HorarioSemanal... horarios) {
        when(agenda.consultar(42L)).thenReturn(AgendaProfesional.builder().versionAplicada(7L).weeklySchedules(List.of(horarios)).build());
    }

    private HorarioSemanal horario(DayOfWeek day, String start, String end, int minutes) {
        return HorarioSemanal.builder().id(9L).dayOfWeek(day).startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end)).slotDurationMinutes(minutes).build();
    }
}
