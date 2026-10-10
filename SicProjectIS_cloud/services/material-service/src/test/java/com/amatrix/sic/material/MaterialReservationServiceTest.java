package com.amatrix.sic.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import com.amatrix.sic.contracts.MaterialReservationCommand;

class MaterialReservationServiceTest {
    private JdbcTemplate jdbc;
    private MaterialReservationService service;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:material-reservation;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE material(id BIGINT PRIMARY KEY,project_id BIGINT,material_type_code VARCHAR(64))");
        jdbc.execute("CREATE TABLE material_version(id BIGINT PRIMARY KEY,material_id BIGINT,file_status VARCHAR(32))");
        jdbc.execute("CREATE TABLE material_reservation(reservation_id CHAR(36) PRIMARY KEY,command_id VARCHAR(100) UNIQUE,"
                + "project_id BIGINT,module_instance_id BIGINT,expected_seq INT,actor_user_id BIGINT,status VARCHAR(32),"
                + "expires_at TIMESTAMP,created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE material_reservation_item(reservation_id CHAR(36),material_version_id BIGINT,"
                + "material_type_code VARCHAR(64),PRIMARY KEY(reservation_id,material_version_id))");
        jdbc.update("INSERT INTO material VALUES (1,10,'APPLICATION_FORM'),(2,11,'REPORT')");
        jdbc.update("INSERT INTO material_version VALUES (101,1,'ACTIVE'),(102,1,'DELETED'),(201,2,'ACTIVE')");
        service = new MaterialReservationService(jdbc);
    }

    @Test
    void reservationIsIdempotentAndConfirmationIsIdempotent() {
        MaterialReservationCommand command = command("module-7-seq-2", 10L, List.of(101L),
                List.of("APPLICATION_FORM"));
        var first = service.reserve(command);
        var duplicate = service.reserve(command);

        assertThat(duplicate.reservationId()).isEqualTo(first.reservationId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material_reservation", Integer.class)).isEqualTo(1);
        service.confirm(first.reservationId());
        service.confirm(first.reservationId());
        assertThat(status(first.reservationId())).isEqualTo("CONFIRMED");
    }

    @Test
    void rejectsMissingDeletedWrongProjectAndIncompleteCommands() {
        assertThatThrownBy(() -> service.reserve(command("missing", 10L, List.of(), List.of("APPLICATION_FORM"))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Required materials are missing");
        assertThatThrownBy(() -> service.reserve(command("deleted", 10L, List.of(102L), List.of())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("unavailable");
        assertThatThrownBy(() -> service.reserve(command("wrong-project", 10L, List.of(201L), List.of())))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("does not belong");
        assertThatThrownBy(() -> service.reserve(new MaterialReservationCommand(null, null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("fields are required");

        jdbc.update("INSERT INTO material_reservation VALUES (?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP)",
                "00000000-0000-0000-0000-000000000002", "late-deleted", 10L, 7L, 3, 9L, "EXPIRED",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)));
        jdbc.update("INSERT INTO material_reservation_item VALUES (?,?,?)",
                "00000000-0000-0000-0000-000000000002", 102L, "APPLICATION_FORM");
        assertThatThrownBy(() -> service.confirm("00000000-0000-0000-0000-000000000002"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no longer available");
    }

    @Test
    void releaseRejectsConfirmationButLateWorkflowConfirmationCanRecoverAnExpiredReservation() {
        var released = service.reserve(command("release", 10L, List.of(101L), List.of()));
        service.release(released.reservationId());
        service.release(released.reservationId());
        assertThat(status(released.reservationId())).isEqualTo("RELEASED");
        assertThatThrownBy(() -> service.confirm(released.reservationId()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("released or unavailable");

        jdbc.update("INSERT INTO material_reservation VALUES (?,?,?,?,?,?,?, ?,CURRENT_TIMESTAMP)",
                "00000000-0000-0000-0000-000000000001", "expired", 10L, 7L, 3, 9L, "RESERVED",
                java.sql.Timestamp.from(Instant.now().minusSeconds(5)));
        jdbc.update("INSERT INTO material_reservation_item VALUES (?,?,?)",
                "00000000-0000-0000-0000-000000000001", 101L, "APPLICATION_FORM");
        service.cleanup();
        assertThat(status("00000000-0000-0000-0000-000000000001")).isEqualTo("EXPIRED");
        service.confirm("00000000-0000-0000-0000-000000000001");
        assertThat(status("00000000-0000-0000-0000-000000000001")).isEqualTo("CONFIRMED");
    }

    private static MaterialReservationCommand command(String id, Long projectId, List<Long> versions,
            List<String> required) {
        return new MaterialReservationCommand(id, 9L, projectId, 7L, 2, versions, required);
    }

    private String status(String id) {
        return jdbc.queryForObject("SELECT status FROM material_reservation WHERE reservation_id=?", String.class, id);
    }
}
