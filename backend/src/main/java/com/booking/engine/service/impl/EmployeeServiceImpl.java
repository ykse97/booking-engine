package com.booking.engine.service.impl;

import com.booking.engine.dto.EmployeeRequestDto;
import com.booking.engine.dto.EmployeeResponseDto;
import com.booking.engine.entity.EmployeeEntity;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.EmployeeMapper;
import com.booking.engine.repository.EmployeeRepository;
import com.booking.engine.service.DisplayOrderService;
import com.booking.engine.service.EmployeeBookingGuard;
import com.booking.engine.service.EmployeeScheduleSeedService;
import com.booking.engine.service.EmployeeService;
import com.booking.engine.service.EmployeeTreatmentAssignmentService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link EmployeeService}.
 * Provides employee related business operations.
 *
 * @author Yehor
 * @version 2.0
 * @since March 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeServiceImpl implements EmployeeService {
    // ---------------------- Repositories ----------------------

    private final EmployeeRepository employeeRepository;

    // ---------------------- Mappers ----------------------

    private final EmployeeMapper mapper;

    // ---------------------- Services ----------------------

    private final DisplayOrderService displayOrderService;

    private final EmployeeTreatmentAssignmentService employeeTreatmentAssignmentService;

    private final EmployeeBookingGuard employeeBookingGuard;

    private final EmployeeScheduleSeedService employeeScheduleSeedService;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public EmployeeResponseDto createEmployee(EmployeeRequestDto request) {

        // Lock active employee ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(employeeRepository);

        log.info("event=employee_create action=start requestedOrder={}",
                request.getDisplayOrder());

        Integer order = displayOrderService.resolveDisplayOrder(
                request.getDisplayOrder(), employeeRepository);

        displayOrderService.shiftDisplayOrders(order, employeeRepository);

        EmployeeEntity employee = mapper.toEntity(request);
        Set<TreatmentEntity> requestedTreatments = employeeTreatmentAssignmentService
                .resolveRequestedTreatments(request.getTreatmentIds());
        employee.setDisplayOrder(order);
        employee.setBookable(Boolean.TRUE.equals(request.getBookable()));
        employee.setProvidedTreatments(requestedTreatments);

        EmployeeEntity saved = employeeRepository.save(employee);
        employeeScheduleSeedService.seedUpcomingScheduleIfBookable(saved);

        log.info("event=employee_create action=success employeeId={} displayOrder={}",
                saved.getId(), saved.getDisplayOrder());

        return mapper.toDto(saved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EmployeeResponseDto> getAllEmployees() {
        log.debug("event=employee_list action=start bookableOnly=false");

        List<EmployeeResponseDto> employees = employeeRepository.findAllActiveWithTreatmentsOrderByDisplayOrderAsc()
                .stream()
                .map(mapper::toDto)
                .toList();
        log.debug("event=employee_list action=success bookableOnly=false resultCount={}", employees.size());
        return employees;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<EmployeeResponseDto> getBookableEmployees() {
        log.debug("event=employee_list action=start bookableOnly=true");

        List<EmployeeResponseDto> employees = employeeRepository.findAllBookableWithTreatmentsOrderByDisplayOrderAsc()
                .stream()
                .map(mapper::toDto)
                .toList();
        log.debug("event=employee_list action=success bookableOnly=true resultCount={}", employees.size());
        return employees;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmployeeResponseDto getEmployeeById(UUID id) {
        log.debug("event=employee_get action=start employeeId={}", id);

        EmployeeEntity employee = findEmployeeWithTreatmentsOrThrow(id);
        log.debug("event=employee_get action=success employeeId={}", id);
        return mapper.toDto(employee);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void updateEmployee(UUID id, EmployeeRequestDto request) {

        // Lock active employee ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(employeeRepository);

        log.info("event=employee_update action=start employeeId={}", id);

        EmployeeEntity employee = findEmployeeWithTreatmentsOrThrow(id);

        Integer oldOrder = employee.getDisplayOrder();
        Integer newOrder = displayOrderService.resolveDisplayOrderForUpdate(
                request.getDisplayOrder(), oldOrder, employeeRepository);
        Set<TreatmentEntity> requestedTreatments = employeeTreatmentAssignmentService
                .resolveRequestedTreatments(request.getTreatmentIds());

        employeeBookingGuard.validateRemovedTreatmentsHaveNoFutureBookings(employee, requestedTreatments);

        if (!oldOrder.equals(newOrder)) {
            displayOrderService.moveDisplayOrder(oldOrder, newOrder, employeeRepository);
        }

        mapper.updateFromDto(request, employee);
        if (request.getBookable() != null) {
            employee.setBookable(request.getBookable());
        }
        employee.setDisplayOrder(newOrder);
        employee.setProvidedTreatments(requestedTreatments);
        employeeScheduleSeedService.seedUpcomingScheduleIfBookable(employee);

        log.info("event=employee_update action=success employeeId={} oldDisplayOrder={} newDisplayOrder={}",
                id, oldOrder, newOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void deleteEmployee(UUID id) {

        // Lock active employee ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(employeeRepository);

        log.info("event=employee_delete action=start employeeId={}", id);

        EmployeeEntity employee = findEmployeeOrThrow(id);

        employeeBookingGuard.validateNoActiveBookings(id);

        Integer deletedOrder = employee.getDisplayOrder();

        employeeRepository.delete(employee);

        displayOrderService.shiftOrdersAfterDelete(deletedOrder, employeeRepository);

        log.info("event=employee_delete action=success employeeId={} freedDisplayOrder={}", id, deletedOrder);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void reorderEmployees(UUID employeeId1, UUID employeeId2) {

        // Lock active employee ordering scope to avoid concurrent reorder conflicts.
        displayOrderService.lockActiveOrderingScope(employeeRepository);

        log.info("event=employee_reorder action=start employeeId1={} employeeId2={}", employeeId1, employeeId2);

        if (employeeId1.equals(employeeId2)) {
            throw new IllegalArgumentException("Cannot reorder the same employee id");
        }

        EmployeeEntity employee1 = findEmployeeOrThrow(employeeId1);
        EmployeeEntity employee2 = findEmployeeOrThrow(employeeId2);

        Integer order1 = employee1.getDisplayOrder();
        Integer order2 = employee2.getDisplayOrder();

        Integer tempOrder = displayOrderService.resolveTemporaryDisplayOrder(employeeRepository);

        employee1.setDisplayOrder(tempOrder);
        employeeRepository.saveAndFlush(employee1);

        employee2.setDisplayOrder(order1);
        employeeRepository.saveAndFlush(employee2);

        employee1.setDisplayOrder(order2);
        employeeRepository.saveAndFlush(employee1);

        log.info(
                "event=employee_reorder action=success employeeId1={} newDisplayOrder1={} employeeId2={} newDisplayOrder2={}",
                employeeId1,
                order2,
                employeeId2,
                order1);
    }

    // ---------------------- Private Methods ----------------------

    /**
     * Finds an active employee or throws when it does not exist.
     */
    private EmployeeEntity findEmployeeOrThrow(UUID id) {
        return employeeRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> {
                    log.warn("event=employee_lookup outcome=not_found employeeId={}", id);
                    return new EntityNotFoundException("Employee", id);
                });
    }

    /**
     * Finds an active employee together with provided treatments or throws when it does not exist.
     */
    private EmployeeEntity findEmployeeWithTreatmentsOrThrow(UUID id) {
        return employeeRepository.findByIdAndActiveTrueWithTreatments(id)
                .orElseThrow(() -> {
                    log.warn("event=employee_lookup outcome=not_found_with_treatments employeeId={}", id);
                    return new EntityNotFoundException("Employee", id);
                });
    }
}
