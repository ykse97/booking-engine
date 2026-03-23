package com.booking.engine.service.impl;

import com.booking.engine.dto.BookingConfirmationRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionRequestDto;
import com.booking.engine.dto.BookingCheckoutSessionResponseDto;
import com.booking.engine.dto.BookingHoldRequestDto;
import com.booking.engine.dto.BookingRequestDto;
import com.booking.engine.dto.BookingResponseDto;
import com.booking.engine.dto.AdminBookingCreateRequestDto;
import com.booking.engine.dto.AdminBookingListResponseDto;
import com.booking.engine.entity.BarberEntity;
import com.booking.engine.entity.BookingEntity;
import com.booking.engine.entity.BookingStatus;
import com.booking.engine.entity.TreatmentEntity;
import com.booking.engine.exception.BookingValidationException;
import com.booking.engine.exception.EntityNotFoundException;
import com.booking.engine.mapper.BookingMapper;
import com.booking.engine.properties.BookingProperties;
import com.booking.engine.repository.BarberRepository;
import com.booking.engine.repository.BookingRepository;
import com.booking.engine.repository.TreatmentRepository;
import com.booking.engine.service.AvailabilityService;
import com.booking.engine.service.BookingBlacklistService;
import com.booking.engine.service.BookingService;
import com.booking.engine.service.StripePaymentService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link BookingService}.
 * Manages booking lifecycle including slot holds, Stripe payment, and booking state changes.
 *
 * @author Yehor
 * @version 1.0
 * @since February 2026
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingServiceImpl implements BookingService {

    // ---------------------- Constants ----------------------

    private static final int SLOT_HOLD_MINUTES = 10;
    private static final int MAX_ACTIVE_HOLDS_PER_IP = 2;
    private static final int MAX_ACTIVE_HOLDS_PER_DEVICE = 2;
    private static final String PUBLIC_BOOKING_UNAVAILABLE_MESSAGE =
            "Booking through the website is temporarily unavailable. Please contact the barbershop directly.";
    private static final String HOLD_LIMIT_IP_MESSAGE =
            "This connection already has two active appointment holds. Please complete or release an existing hold before selecting another slot.";
    private static final String HOLD_LIMIT_DEVICE_MESSAGE =
            "This device already has two active appointment holds. Please complete or release an existing hold before selecting another slot.";

    // ---------------------- Repositories ----------------------

    private final BookingRepository bookingRepository;
    private final BarberRepository barberRepository;
    private final TreatmentRepository treatmentRepository;

    // ---------------------- Services ----------------------

    private final AvailabilityService availabilityService;
    private final BookingBlacklistService bookingBlacklistService;
    private final StripePaymentService stripePaymentService;

    // ---------------------- Mappers ----------------------

    private final BookingMapper mapper;

    // ---------------------- Properties ----------------------

    private final BookingProperties bookingProperties;

    // ---------------------- Public Methods ----------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public BookingResponseDto getBookingById(UUID id) {
        log.info("Retrieving booking with id={}", id);

        BookingEntity booking = findBookingOrThrow(id);

        log.info("Booking retrieved successfully with id={}", id);
        return mapper.toDto(booking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto create(BookingRequestDto request) {
        log.info("Creating booking for barberId={}, treatmentId={}, date={}, startTime={}",
                request.getBarberId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        // Acquire row lock to serialize booking creation for the same barber.
        BarberEntity barber = findActiveBarberForUpdate(request.getBarberId());

        // Delegate validation to AvailabilityService (SRP)
        availabilityService.validateBookingRequest(request);
        validatePublicCustomerAllowed(
                request.getCustomer().getEmail(),
                request.getCustomer().getPhone());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        BigDecimal paymentAmount = treatment.getPrice();

        String paymentIntentId = stripePaymentService.createAndConfirmPayment(
                paymentAmount,
                request.getCustomer().getEmail(),
                request.getPaymentMethodId(),
                buildStripeMetadata(request));

        BookingEntity booking = buildBooking(request, barber, treatment, paymentAmount, paymentIntentId);
        BookingEntity savedBooking = bookingRepository.save(booking);

        log.info("Booking created and paid successfully with id={}, paymentIntentId={}",
                savedBooking.getId(), paymentIntentId);

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto createAdminBooking(AdminBookingCreateRequestDto request) {
        log.info(
                "Creating free admin booking for barberId={}, treatmentId={}, date={}, startTime={}, customer={}",
                request.getBarberId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getCustomerName());

        BarberEntity barber = findActiveBarberForUpdate(request.getBarberId());
        availabilityService.validateSlotSelection(
                request.getBarberId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());
        bookingBlacklistService.validateAllowedCustomer(null, request.getCustomerPhone());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        BookingEntity booking = buildAdminBooking(request, barber, treatment);
        BookingEntity savedBooking = bookingRepository.save(booking);

        log.info("Free admin booking created successfully with id={}", savedBooking.getId());
        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Override
    public BookingResponseDto holdSlot(BookingHoldRequestDto request, String clientIp, String clientDeviceId) {
        log.info("Holding booking slot for barberId={}, treatmentId={}, date={}, startTime={}",
                request.getBarberId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime());

        validateHoldLimit(clientIp, clientDeviceId);

        BarberEntity barber = findActiveBarberForUpdate(request.getBarberId());
        availabilityService.validateSlotSelection(
                request.getBarberId(),
                request.getTreatmentId(),
                request.getBookingDate(),
                request.getStartTime(),
                request.getEndTime());

        TreatmentEntity treatment = findTreatmentOrThrow(request.getTreatmentId());
        BigDecimal paymentAmount = treatment.getPrice();

        BookingEntity booking = buildHeldBooking(request, barber, treatment, paymentAmount, clientIp, clientDeviceId);
        BookingEntity savedBooking = bookingRepository.save(booking);

        log.info("Booking slot held successfully with id={}, expiresAt={}",
                savedBooking.getId(), savedBooking.getExpiresAt());

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingCheckoutSessionResponseDto prepareHeldBookingCheckout(
            UUID id,
            BookingCheckoutSessionRequestDto request) {
        log.info(
                "Preparing Stripe checkout bookingId={}, confirmationTokenIdPrefix={}, customerEmail={}",
                id,
                request.getConfirmationTokenId() != null && request.getConfirmationTokenId().length() > 8
                        ? request.getConfirmationTokenId().substring(0, 8)
                        : request.getConfirmationTokenId(),
                request.getCustomer() != null ? request.getCustomer().getEmail() : null);

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validatePendingBookingAvailability(booking);
        validatePublicCustomerAllowed(
                request.getCustomer() != null ? request.getCustomer().getEmail() : null,
                request.getCustomer() != null ? request.getCustomer().getPhone() : null);

        applyCustomerDetails(booking, request.getCustomer());

        if (booking.getStripePaymentIntentId() != null && !booking.getStripePaymentIntentId().isBlank()) {
            String currentStatus = stripePaymentService.getPaymentIntentStatus(booking.getStripePaymentIntentId());
            booking.setStripePaymentStatus(currentStatus);
            log.info("Stripe payment already exists for bookingId={}, paymentIntentId={}, status={}",
                    id, booking.getStripePaymentIntentId(), currentStatus);

            if ("succeeded".equals(currentStatus)) {
                booking.setPaymentCapturedAt(resolveCapturedAt(booking));
                bookingRepository.save(booking);
                log.info("Reusing succeeded Stripe payment for bookingId={}, paymentIntentId={}",
                        id, booking.getStripePaymentIntentId());
                return BookingCheckoutSessionResponseDto.builder()
                        .paymentIntentId(booking.getStripePaymentIntentId())
                        .paymentStatus(currentStatus)
                        .build();
            }

            if ("requires_action".equals(currentStatus) || "processing".equals(currentStatus)) {
                throw new BookingValidationException(
                        "Stripe is still processing the payment for this booking. Please finish the current checkout flow.");
            }
        }

        log.info("Creating Stripe payment for bookingId={}, amount={}", id, booking.getHoldAmount());
        BookingCheckoutSessionResponseDto checkoutSession = stripePaymentService.createAndConfirmPaymentWithConfirmationToken(
                booking.getHoldAmount(),
                booking.getCustomerEmail(),
                request.getConfirmationTokenId(),
                buildStripeMetadata(booking, booking.getCustomerEmail()));

        booking.setStripePaymentIntentId(checkoutSession.getPaymentIntentId());
        booking.setStripePaymentStatus(checkoutSession.getPaymentStatus());
        if ("succeeded".equals(checkoutSession.getPaymentStatus())) {
            booking.setPaymentCapturedAt(resolveCapturedAt(booking));
        }
        bookingRepository.save(booking);

        log.info("Stripe checkout prepared for bookingId={}, paymentIntentId={}, paymentStatus={}, hasClientSecret={}",
                booking.getId(),
                checkoutSession.getPaymentIntentId(),
                checkoutSession.getPaymentStatus(),
                checkoutSession.getClientSecret() != null && !checkoutSession.getClientSecret().isBlank());

        return checkoutSession;
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto confirmHeldBooking(UUID id, BookingConfirmationRequestDto request) {
        log.info("Finalizing paid booking hold id={}", id);

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validateBookingCanFinalizePayment(booking);
        validateStripeIntentExists(booking);

        if (!booking.getStripePaymentIntentId().equals(request.getPaymentIntentId())) {
            throw new BookingValidationException(
                    "This Stripe payment does not match the current appointment hold.");
        }

        if (booking.getCustomerName() == null || booking.getCustomerName().isBlank()
                || booking.getCustomerEmail() == null || booking.getCustomerEmail().isBlank()) {
            throw new BookingValidationException("Customer details are missing. Please restart checkout.");
        }

        String paymentStatus = stripePaymentService.getPaymentIntentStatus(request.getPaymentIntentId());
        if (!"succeeded".equals(paymentStatus)) {
            throw new BookingValidationException(
                    "Stripe payment is not completed yet. Please finish payment first.");
        }

        booking.setStripePaymentStatus(paymentStatus);
        booking.setPaymentCapturedAt(resolveCapturedAt(booking));
        booking.setExpiresAt(null);

        BookingEntity savedBooking = bookingRepository.save(booking);

        log.info("Held booking finalized successfully id={}, paymentIntentId={}, status={}",
                savedBooking.getId(), request.getPaymentIntentId(), savedBooking.getStatus());

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void cancelBooking(UUID id) {
        log.info("Cancelling booking id={}", id);

        BookingEntity booking = findBookingOrThrow(id);
        validatePublicCancellation(booking, id);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);
        bookingRepository.save(booking);

        log.info("Booking successfully cancelled: {}", id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminBookingListResponseDto getAdminBookings(String search) {
        log.info("Fetching admin booking overview search={}", search);

        List<BookingEntity> activeBookings = bookingRepository.findAllActiveWithBarberAndTreatment();
        long confirmedCount = activeBookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CONFIRMED)
                .count();

        List<BookingResponseDto> filteredBookings = activeBookings.stream()
                .sorted(buildAdminBookingComparator())
                .filter(booking -> matchesAdminSearch(booking, search))
                .map(mapper::toDto)
                .toList();

        return AdminBookingListResponseDto.builder()
                .bookings(filteredBookings)
                .confirmedCount(confirmedCount)
                .filteredCount(filteredBookings.size())
                .build();
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public BookingResponseDto cancelBookingByAdmin(UUID id) {
        log.info("Cancelling booking from admin panel id={}", id);

        BookingEntity booking = findBookingForUpdateOrThrow(id);
        validateAdminCancellation(booking);

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setExpiresAt(null);

        BookingEntity savedBooking = bookingRepository.save(booking);
        log.info("Booking cancelled by salon id={}", id);

        return mapper.toDto(savedBooking);
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void syncStripePaymentIntentFromWebhook(String paymentIntentId, String paymentStatus, String eventType) {
        log.info("Synchronizing Stripe webhook paymentIntentId={}, eventType={}, paymentStatus={}",
                paymentIntentId, eventType, paymentStatus);

        bookingRepository.findByStripePaymentIntentIdForUpdate(paymentIntentId)
                .ifPresentOrElse(
                        booking -> {
                            booking.setStripePaymentStatus(paymentStatus);

                            if ("payment_intent.succeeded".equals(eventType)) {
                                booking.setPaymentCapturedAt(resolveCapturedAt(booking));
                                booking.setExpiresAt(null);

                                if (booking.getStatus() == BookingStatus.PENDING
                                        || booking.getStatus() == BookingStatus.CONFIRMED) {
                                    booking.setStatus(BookingStatus.CONFIRMED);
                                } else {
                                    log.warn(
                                            "Skipping Stripe webhook confirmation for bookingId={} because status={} cannot become CONFIRMED",
                                            booking.getId(),
                                            booking.getStatus());
                                }
                            } else if ("payment_intent.canceled".equals(eventType)
                                    || "payment_intent.payment_failed".equals(eventType)) {
                                booking.setPaymentReleasedAt(LocalDateTime.now(getZoneId()));
                            }

                            bookingRepository.save(booking);
                            log.info("Stripe webhook synced bookingId={}, eventType={}, paymentStatus={}, status={}",
                                    booking.getId(), eventType, paymentStatus, booking.getStatus());
                        },
                        () -> log.warn("No booking found for Stripe paymentIntentId={}", paymentIntentId));
    }

    // ---------------------- Private Methods ----------------------

    /*
     * Finds active barber by ID and acquires pessimistic write lock.
     * Prevents parallel create requests from booking the same slot concurrently.
     *
     * @param barberId the barber UUID
     * @return locked barber entity
     */
    private BarberEntity findActiveBarberForUpdate(UUID barberId) {
        return barberRepository.findByIdAndActiveTrueForUpdate(barberId)
                .orElseThrow(() -> {
                    log.warn("Active barber not found with ID: {}", barberId);
                    return new EntityNotFoundException("Barber", barberId);
                });
    }

    /*
     * Finds treatment by ID or throws exception.
     *
     * @param treatmentId the treatment UUID
     * @return the treatment entity
     * @throws EntityNotFoundException if not found
     */
    private TreatmentEntity findTreatmentOrThrow(UUID treatmentId) {
        return treatmentRepository.findById(treatmentId)
                .orElseThrow(() -> {
                    log.warn("Treatment not found with ID: {}", treatmentId);
                    return new EntityNotFoundException("Treatment", treatmentId);
                });
    }

    /*
     * Finds booking by ID or throws exception.
     *
     * @param id the booking UUID
     * @return the booking entity
     * @throws EntityNotFoundException if not found
     */
    private BookingEntity findBookingOrThrow(UUID id) {
        return bookingRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Booking not found: {}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Finds booking by ID with pessimistic write lock or throws exception.
     *
     * @param id the booking UUID
     * @return the locked booking entity
     */
    private BookingEntity findBookingForUpdateOrThrow(UUID id) {
        return bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> {
                    log.warn("Booking not found for update: {}", id);
                    return new EntityNotFoundException("Booking", id);
                });
    }

    /*
     * Validates Stripe payment intent is present for booking.
     */
    private void validateStripeIntentExists(BookingEntity booking) {
        if (booking.getStripePaymentIntentId() == null || booking.getStripePaymentIntentId().isBlank()) {
            throw new IllegalStateException("Booking has no Stripe payment intent");
        }
    }

    /*
     * Ensures a held booking is still eligible for final payment confirmation by
     * rejecting cancelled, expired, completed, or stale pending holds and marking
     * truly expired holds in persistence before failing.
     *
     * @param booking booking being finalized after checkout
     */
    private void validateBookingCanFinalizePayment(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return;
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This appointment has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This appointment has already been completed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if (isPaidPendingBooking(booking)) {
            return;
        }

        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(now)) {
            markBookingExpired(booking);
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }
    }

    /*
     * Validates a pending booking can still be processed before the salon decision.
     */
    private void validatePendingBookingAvailability(BookingEntity booking) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            throw new BookingValidationException("This appointment has already been confirmed.");
        }

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This appointment has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This appointment has already been completed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("This appointment hold is no longer available.");
        }

        if (booking.getExpiresAt() == null || !booking.getExpiresAt().isAfter(now)) {
            markBookingExpired(booking);
            throw new BookingValidationException("This appointment hold has expired. Please choose another time.");
        }
    }

    /*
     * Copies checkout customer details from the request DTO into the mutable
     * booking entity before Stripe payment is created or reused.
     *
     * @param booking booking entity being updated
     * @param customer customer details from checkout request
     */
    private void applyCustomerDetails(BookingEntity booking, BookingRequestDto.CustomerDetailsDto customer) {
        booking.setCustomerName(customer.getName());
        booking.setCustomerEmail(customer.getEmail());
        booking.setCustomerPhone(customer.getPhone());
    }

    /*
     * Validates public cancellation is used only for unpaid temporary holds.
     */
    private void validatePublicCancellation(BookingEntity booking, UUID id) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.warn("Booking already cancelled: {}", id);
            throw new IllegalStateException("Booking is already canceled");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("This booking can no longer be changed.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new BookingValidationException("Only pending booking holds can be cancelled from the public site.");
        }

        if (booking.getPaymentCapturedAt() != null || "succeeded".equals(booking.getStripePaymentStatus())) {
            throw new BookingValidationException("Paid bookings can only be updated from the admin panel.");
        }
    }

    /*
     * Validates admin-side cancellation request.
     */
    private void validateAdminCancellation(BookingEntity booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BookingValidationException("This booking has already been cancelled.");
        }

        if (booking.getStatus() == BookingStatus.EXPIRED) {
            throw new BookingValidationException("This booking has already expired.");
        }

        if (booking.getStatus() == BookingStatus.DONE) {
            throw new BookingValidationException("Completed bookings cannot be cancelled.");
        }

        if (booking.getStatus() == BookingStatus.PENDING && !isPaidPendingBooking(booking)) {
            validatePendingBookingAvailability(booking);
        }
    }

    /*
     * Returns the already stored payment capture timestamp when present or
     * generates a new timestamp in booking timezone for freshly confirmed
     * payments.
     *
     * @param booking booking whose capture time is being resolved
     * @return payment capture timestamp
     */
    private LocalDateTime resolveCapturedAt(BookingEntity booking) {
        return booking.getPaymentCapturedAt() != null
                ? booking.getPaymentCapturedAt()
                : LocalDateTime.now(getZoneId());
    }

    /*
     * Builds the admin booking sort order that keeps upcoming bookings first and
     * pushes historical bookings to the bottom while preserving sensible date and
     * time ordering inside each group.
     *
     * @return comparator for admin booking list rendering
     */
    private Comparator<BookingEntity> buildAdminBookingComparator() {
        LocalDate today = LocalDate.now(getZoneId());
        LocalTime nowTime = LocalTime.now(getZoneId());

        return (left, right) -> {
            boolean leftPast = isPastBooking(left, today, nowTime);
            boolean rightPast = isPastBooking(right, today, nowTime);

            if (leftPast != rightPast) {
                return leftPast ? 1 : -1;
            }

            int dateComparison = leftPast
                    ? right.getBookingDate().compareTo(left.getBookingDate())
                    : left.getBookingDate().compareTo(right.getBookingDate());

            if (dateComparison != 0) {
                return dateComparison;
            }

            int timeComparison = leftPast
                    ? right.getStartTime().compareTo(left.getStartTime())
                    : left.getStartTime().compareTo(right.getStartTime());

            if (timeComparison != 0) {
                return timeComparison;
            }

            LocalDateTime leftCreatedAt = left.getCreatedAt();
            LocalDateTime rightCreatedAt = right.getCreatedAt();

            if (leftCreatedAt == null && rightCreatedAt == null) {
                return 0;
            }

            if (leftCreatedAt == null) {
                return 1;
            }

            if (rightCreatedAt == null) {
                return -1;
            }

            return leftCreatedAt.compareTo(rightCreatedAt);
        };
    }

    /*
     * Determines whether a booking belongs to the past relative to the current
     * business date and time, using end time for same-day bookings.
     *
     * @param booking booking to inspect
     * @param today current date
     * @param nowTime current time
     * @return {@code true} when the booking is already past
     */
    private boolean isPastBooking(BookingEntity booking, LocalDate today, LocalTime nowTime) {
        return booking.getBookingDate().isBefore(today)
                || (booking.getBookingDate().isEqual(today) && booking.getEndTime() != null
                        && booking.getEndTime().isBefore(nowTime));
    }

    /*
     * Applies normalized text and digit-only phone matching so admin search can
     * find bookings by customer name, email, raw phone text, or phone digits.
     *
     * @param booking booking to inspect
     * @param search raw admin search string
     * @return {@code true} when the booking matches the search query
     */
    private boolean matchesAdminSearch(BookingEntity booking, String search) {
        String normalizedSearch = normalizeSearch(search);
        if (normalizedSearch == null) {
            return true;
        }

        String normalizedPhoneSearch = normalizePhoneSearch(search);

        return containsIgnoreCase(booking.getCustomerName(), normalizedSearch)
                || containsIgnoreCase(booking.getCustomerEmail(), normalizedSearch)
                || containsIgnoreCase(booking.getCustomerPhone(), normalizedSearch)
                || (normalizedPhoneSearch != null && normalizePhoneSearch(booking.getCustomerPhone()) != null
                        && normalizePhoneSearch(booking.getCustomerPhone()).contains(normalizedPhoneSearch));
    }

    /*
     * Normalizes free-text admin search input by trimming, lowercasing, and
     * converting blank input to {@code null}.
     *
     * @param value raw search value
     * @return normalized search text or {@code null} when blank
     */
    private String normalizeSearch(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim().toLowerCase();
        return trimmed.isBlank() ? null : trimmed;
    }

    /*
     * Extracts digits from a phone-like search value so formatted and unformatted
     * phone numbers can be compared consistently.
     *
     * @param value raw phone search value
     * @return normalized digit-only string or {@code null} when blank
     */
    private String normalizePhoneSearch(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replaceAll("[^0-9]", "");
        return normalized.isBlank() ? null : normalized;
    }

    /*
     * Performs a null-safe case-insensitive containment check used by the admin
     * search filter.
     *
     * @param value source text
     * @param search normalized search text
     * @return {@code true} when {@code value} contains {@code search}
     */
    private boolean containsIgnoreCase(String value, String search) {
        return value != null && search != null && value.toLowerCase().contains(search);
    }

    /*
     * Rejects public bookings from customers whose normalized email or phone is
     * present in the booking blacklist.
     *
     * @param email customer email
     * @param phone customer phone
     */
    private void validatePublicCustomerAllowed(String email, String phone) {
        if (bookingBlacklistService.isBlockedCustomer(email, phone)) {
            throw new BookingValidationException(PUBLIC_BOOKING_UNAVAILABLE_MESSAGE);
        }
    }

    /*
     * Enforces per-IP and per-device limits for unpaid active slot holds to reduce
     * abuse of temporary reservations.
     *
     * @param clientIp resolved client IP address
     * @param clientDeviceId persistent client device identifier
     */
    private void validateHoldLimit(String clientIp, String clientDeviceId) {
        LocalDateTime now = LocalDateTime.now(getZoneId());

        if (clientIp != null && bookingRepository.countActiveUnpaidHoldsByClientIp(
                clientIp,
                BookingStatus.PENDING,
                now) >= MAX_ACTIVE_HOLDS_PER_IP) {
            throw new BookingValidationException(HOLD_LIMIT_IP_MESSAGE);
        }

        if (clientDeviceId != null && bookingRepository.countActiveUnpaidHoldsByClientDeviceId(
                clientDeviceId,
                BookingStatus.PENDING,
                now) >= MAX_ACTIVE_HOLDS_PER_DEVICE) {
            throw new BookingValidationException(HOLD_LIMIT_DEVICE_MESSAGE);
        }
    }

    /*
     * Builds metadata payload for Stripe PaymentIntent.
     */
    private Map<String, String> buildStripeMetadata(BookingRequestDto request) {
        return Map.of(
                "barberId", request.getBarberId().toString(),
                "treatmentId", request.getTreatmentId().toString(),
                "bookingDate", request.getBookingDate().toString(),
                "startTime", request.getStartTime().toString(),
                "endTime", request.getEndTime().toString(),
                "customerEmail", request.getCustomer().getEmail());
    }

    /*
     * Builds metadata payload for Stripe PaymentIntent from an existing held
     * booking.
     */
    private Map<String, String> buildStripeMetadata(BookingEntity booking, String customerEmail) {
        return Map.of(
                "bookingId", booking.getId().toString(),
                "barberId", booking.getBarber().getId().toString(),
                "treatmentId", booking.getTreatment().getId().toString(),
                "bookingDate", booking.getBookingDate().toString(),
                "startTime", booking.getStartTime().toString(),
                "endTime", booking.getEndTime().toString(),
                "customerEmail", customerEmail);
    }

    /*
     * Marks a pending hold as expired in persistence once its expiration window is
     * known to be over.
     *
     * @param booking expired booking hold
     */
    private void markBookingExpired(BookingEntity booking) {
        booking.setStatus(BookingStatus.EXPIRED);
        booking.setExpiresAt(null);
        bookingRepository.save(booking);
    }

    /*
     * Detects the transitional case where a booking still has {@code PENDING}
     * status but Stripe has already captured payment successfully.
     *
     * @param booking booking to inspect
     * @return {@code true} when payment has already been captured
     */
    private boolean isPaidPendingBooking(BookingEntity booking) {
        return booking.getStatus() == BookingStatus.PENDING
                && (booking.getPaymentCapturedAt() != null || "succeeded".equals(booking.getStripePaymentStatus()));
    }

    /*
     * Resolves configured booking timezone.
     */
    private ZoneId getZoneId() {
        return ZoneId.of(bookingProperties.getTimezone());
    }

    /*
     * Builds a temporary held booking entity from slot selection data.
     */
    private BookingEntity buildHeldBooking(
            BookingHoldRequestDto request,
            BarberEntity barber,
            TreatmentEntity treatment,
            BigDecimal paymentAmount,
            String clientIp,
            String clientDeviceId) {
        BookingEntity booking = new BookingEntity();
        booking.setActive(true);
        booking.setBarber(barber);
        booking.setTreatment(treatment);
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setStatus(BookingStatus.PENDING);
        booking.setExpiresAt(LocalDateTime.now(getZoneId()).plusMinutes(SLOT_HOLD_MINUTES));
        booking.setHoldAmount(paymentAmount);
        booking.setHoldClientIp(clientIp);
        booking.setHoldClientDeviceId(clientDeviceId);
        return booking;
    }

    /*
     * Builds booking entity from request data.
     * Creates a paid confirmed booking for the legacy direct-payment endpoint.
     *
     * @param request         the booking request
     * @param barber          the barber entity
     * @param treatment       the treatment entity
     * @param paymentAmount   treatment price charged for this booking
     * @param paymentIntentId Stripe PaymentIntent identifier
     * @return the built booking entity (not yet saved)
     */
    private BookingEntity buildBooking(BookingRequestDto request,
            BarberEntity barber,
            TreatmentEntity treatment,
            BigDecimal paymentAmount,
            String paymentIntentId) {
        BookingEntity booking = mapper.toEntity(request);
        booking.setBarber(barber);
        booking.setTreatment(treatment);
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.setHoldAmount(paymentAmount);
        booking.setStripePaymentIntentId(paymentIntentId);
        booking.setStripePaymentStatus("succeeded");
        booking.setPaymentCapturedAt(LocalDateTime.now(getZoneId()));

        return booking;
    }

    /*
     * Builds a confirmed booking created manually from the admin panel.
     */
    private BookingEntity buildAdminBooking(
            AdminBookingCreateRequestDto request,
            BarberEntity barber,
            TreatmentEntity treatment) {
        BookingEntity booking = new BookingEntity();
        booking.setActive(true);
        booking.setBarber(barber);
        booking.setTreatment(treatment);
        booking.setCustomerName(request.getCustomerName());
        booking.setCustomerEmail(null);
        booking.setCustomerPhone(request.getCustomerPhone());
        booking.setBookingDate(request.getBookingDate());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setExpiresAt(null);
        booking.setHoldAmount(treatment.getPrice());
        booking.setStripePaymentIntentId(null);
        booking.setStripePaymentStatus(null);
        booking.setPaymentCapturedAt(null);
        booking.setPaymentReleasedAt(null);
        return booking;
    }
}
