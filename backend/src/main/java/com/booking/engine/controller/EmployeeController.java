package com.booking.engine.controller;

import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Public REST controller for employee queries.
 * Provides read-only access to employee information.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@RestController
@RequestMapping(value = "/api/v1/public/employees", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Slf4j
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Retrieves all active employees sorted by display order.
     *
     * @return list of employees in display order
     */
    @GetMapping
    public List<EmployeeResponseDto> getAllEmployees(
            @RequestParam(name = "bookable", defaultValue = "false") boolean bookable) {
        log.info("event=http_request method=GET path=/api/v1/public/employees bookable={}", bookable);
        return bookable
                ? employeeService.getBookableEmployees()
                : employeeService.getAllEmployees();
    }

    /**
     * Retrieves employee by ID.
     *
     * @param id the employee ID
     * @return employee details
     */
    @GetMapping("/{id}")
    public EmployeeResponseDto getEmployeeById(@PathVariable UUID id) {
        log.info("event=http_request method=GET path=/api/v1/public/employees/{id} employeeId={}", id);
        return employeeService.getEmployeeById(id);
    }
}
