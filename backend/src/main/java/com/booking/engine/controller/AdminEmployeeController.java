package com.booking.engine.controller;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.dto.ReorderRequestDto;
import com.booking.engine.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Admin REST controller for employee management.
 * Provides CRUD operations for employees (admin only).
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/admin/employees", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    /**
     * Creates a new employee.
     *
     * @param request the employee creation request
     * @return created employee with generated ID
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EmployeeResponseDto> createEmployee(
            @Valid @RequestBody EmployeeRequestDto request) {

        log.info("event=http_request method=POST path=/api/v1/admin/employees requestedOrder={}",
                request.getDisplayOrder());
        EmployeeResponseDto created = employeeService.createEmployee(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Updates existing employee.
     *
     * @param id      the employee ID
     * @param request the update request
     * @return 204 No Content on success
     */
    @PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateEmployee(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeRequestDto request) {

        log.info("event=http_request method=PUT path=/api/v1/admin/employees/{id} employeeId={}", id);
        employeeService.updateEmployee(id, request);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes employee by ID.
     *
     * @param id the employee ID
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
        log.info("event=http_request method=DELETE path=/api/v1/admin/employees/{id} employeeId={}", id);
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Reorders two employees by swapping their positions.
     *
     * @param request contains two employee IDs to swap
     * @return 204 No Content on success
     */
    @PostMapping(value = "/reorder", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> reorderEmployees(
            @Valid @RequestBody ReorderRequestDto request) {
        log.info("event=http_request method=POST path=/api/v1/admin/employees/reorder employeeId1={} employeeId2={}",
                request.getId1(), request.getId2());
        employeeService.reorderEmployees(request.getId1(), request.getId2());
        return ResponseEntity.noContent().build();
    }
}
