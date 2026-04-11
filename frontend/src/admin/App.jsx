import { useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import {
    adminApi,
    authApi,
    getApiErrorMessage,
    publicApi,
    setUnauthorizedHandler
} from './api';
import { reportAppError } from '../utils/appErrorBus';
import AdminLayout from './components/AdminLayout';
import AdminLoginPage from './pages/AdminLoginPage';
import EmployeesPage from './pages/EmployeesPage';
import BookingsPage from './pages/BookingsPage';
import SalonPage from './pages/SalonPage';
import TreatmentsPage from './pages/TreatmentsPage';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const ALL_EMPLOYEES_OPTION = '__ALL_EMPLOYEES__';
const ADMIN_HOLD_SESSION_STORAGE_KEY = 'admin_booking_hold_session_id';
const ADMIN_HOLD_STATE_STORAGE_KEY = 'admin_booking_hold_state';
const ADMIN_HOLD_REFRESH_INTERVAL_MS = 30_000;
const ADMIN_BOOKING_AVAILABILITY_REFRESH_INTERVAL_MS = 5_000;
const DEFAULT_SCHEDULE_TIMES = {
    openTime: '09:00',
    closeTime: '17:00'
};

const todayIso = () => new Date().toISOString().slice(0, 10);

function isoToDate(value) {
    if (!value) {
        return new Date();
    }

    const [year, month, day] = String(value).split('-').map((part) => Number.parseInt(part, 10));

    if (!year || !month || !day) {
        return new Date();
    }

    return new Date(year, month - 1, day);
}

function dateToIso(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function isValidIsoDate(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
        return false;
    }

    const [year, month, day] = value.split('-').map((part) => Number.parseInt(part, 10));

    if (!year || !month || !day) {
        return false;
    }

    return dateToIso(new Date(year, month - 1, day)) === value;
}

function getEmployeePeriodDateError(startDate, endDate) {
    if (!isValidIsoDate(startDate)) {
        return 'Please choose a real calendar start date before saving the period schedule.';
    }

    if (!isValidIsoDate(endDate)) {
        return 'Please choose a real calendar end date before saving the period schedule.';
    }

    if (startDate > endDate) {
        return 'The end date must be the same as or later than the start date.';
    }

    return '';
}

function emptyDayConfig() {
    return DAYS.map((day) => ({
        dayOfWeek: day,
        workingDay: false,
        openTime: '',
        closeTime: ''
    }));
}

function mapHoursResponse(hours) {
    const byDay = new Map(hours.map((item) => [item.dayOfWeek, item]));
    return DAYS.map((day) => {
        const value = byDay.get(day);
        return {
            dayOfWeek: day,
            workingDay: Boolean(value?.workingDay),
            openTime: value?.openTime ? String(value.openTime).slice(0, 5) : '',
            closeTime: value?.closeTime ? String(value.closeTime).slice(0, 5) : ''
        };
    });
}

function mapScheduleDay(day, dateFallback) {
    const workingDay = Boolean(day?.workingDay);

    return {
        workingDate: day?.workingDate || dateFallback,
        workingDay,
        openTime: day?.openTime
            ? String(day.openTime).slice(0, 5)
            : DEFAULT_SCHEDULE_TIMES.openTime,
        closeTime: day?.closeTime
            ? String(day.closeTime).slice(0, 5)
            : DEFAULT_SCHEDULE_TIMES.closeTime,
        breakStartTime: day?.breakStartTime
            ? String(day.breakStartTime).slice(0, 5)
            : '',
        breakEndTime: day?.breakEndTime
            ? String(day.breakEndTime).slice(0, 5)
            : ''
    };
}

function emptyEmployeePeriodConfig() {
    return DAYS.map((day) => ({
        dayOfWeek: day,
        workingDay: false,
        openTime: DEFAULT_SCHEDULE_TIMES.openTime,
        closeTime: DEFAULT_SCHEDULE_TIMES.closeTime,
        breakStartTime: '',
        breakEndTime: ''
    }));
}

function mapPeriodSettings(periodSettings, fallbackDate) {
    if (!periodSettings) {
        return {
            targetEmployeeId: ALL_EMPLOYEES_OPTION,
            startDate: fallbackDate,
            endDate: fallbackDate,
            days: emptyEmployeePeriodConfig()
        };
    }

    const byDay = new Map((periodSettings.days || []).map((item) => [item.dayOfWeek, item]));

    return {
        targetEmployeeId:
            periodSettings.applyToAllEmployees || !periodSettings.employeeId
                ? ALL_EMPLOYEES_OPTION
                : periodSettings.employeeId,
        startDate: periodSettings.startDate || fallbackDate,
        endDate: periodSettings.endDate || fallbackDate,
        days: DAYS.map((day) => {
            const value = byDay.get(day);
            return {
                dayOfWeek: day,
                workingDay: Boolean(value?.workingDay),
                openTime: value?.openTime
                    ? String(value.openTime).slice(0, 5)
                    : DEFAULT_SCHEDULE_TIMES.openTime,
                closeTime: value?.closeTime
                    ? String(value.closeTime).slice(0, 5)
                    : DEFAULT_SCHEDULE_TIMES.closeTime,
                breakStartTime: value?.breakStartTime
                    ? String(value.breakStartTime).slice(0, 5)
                    : '',
                breakEndTime: value?.breakEndTime
                    ? String(value.breakEndTime).slice(0, 5)
                    : ''
            };
        })
    };
}

function formatDayLabel(day) {
    return day.charAt(0) + day.slice(1).toLowerCase();
}

function toBookingOverview(bookingOverview) {
    return {
        bookings: bookingOverview?.bookings || [],
        confirmedCount: bookingOverview?.confirmedCount || 0,
        filteredCount: bookingOverview?.filteredCount || 0
    };
}

function toConfirmedReviewOverview(bookingOverview) {
    const normalizedOverview = toBookingOverview(bookingOverview);
    const confirmedBookings = normalizedOverview.bookings.filter((booking) => booking.status === 'CONFIRMED');

    return {
        bookings: confirmedBookings,
        confirmedCount: normalizedOverview.confirmedCount,
        filteredCount: confirmedBookings.length
    };
}

function normalizeTimeValue(value) {
    return value ? String(value).slice(0, 5) : '';
}

function areSameSlot(left, right) {
    return Boolean(
        left
        && right
        && normalizeTimeValue(left.startTime) === normalizeTimeValue(right.startTime)
        && normalizeTimeValue(left.endTime) === normalizeTimeValue(right.endTime)
    );
}

function toHeldSlot(slot) {
    if (!slot) {
        return null;
    }

    return {
        ...slot,
        startTime: normalizeTimeValue(slot.startTime),
        endTime: normalizeTimeValue(slot.endTime),
        available: false,
        status: 'HELD'
    };
}

function releaseSlotFromList(slots, releasedSlot) {
    if (!Array.isArray(slots) || !releasedSlot) {
        return Array.isArray(slots) ? slots : [];
    }

    return slots.map((slot) => (
        areSameSlot(slot, releasedSlot)
            ? {
                ...slot,
                available: true,
                status: 'AVAILABLE'
            }
            : slot
    ));
}

function markHeldSlotInList(slots, heldSlot) {
    if (!Array.isArray(slots) || !heldSlot) {
        return Array.isArray(slots) ? slots : [];
    }

    return slots.map((slot) => (
        areSameSlot(slot, heldSlot)
            ? {
                ...slot,
                available: false,
                status: 'HELD'
            }
            : slot
    ));
}

function createClientSessionId() {
    if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
        return crypto.randomUUID();
    }

    return `admin-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function getOrCreateAdminHoldSessionId() {
    if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
        return createClientSessionId();
    }

    const stored = window.sessionStorage.getItem(ADMIN_HOLD_SESSION_STORAGE_KEY);
    if (stored) {
        return stored;
    }

    const next = createClientSessionId();
    window.sessionStorage.setItem(ADMIN_HOLD_SESSION_STORAGE_KEY, next);
    return next;
}

function readStoredAdminHoldState() {
    if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
        return null;
    }

    const raw = window.sessionStorage.getItem(ADMIN_HOLD_STATE_STORAGE_KEY);
    if (!raw) {
        return null;
    }

    try {
        const parsed = JSON.parse(raw);
        return parsed && typeof parsed.id === 'string' ? parsed : null;
    } catch {
        return null;
    }
}

function persistAdminHoldState(hold) {
    if (typeof window === 'undefined' || typeof window.sessionStorage === 'undefined') {
        return;
    }

    if (!hold?.id) {
        window.sessionStorage.removeItem(ADMIN_HOLD_STATE_STORAGE_KEY);
        return;
    }

    window.sessionStorage.setItem(ADMIN_HOLD_STATE_STORAGE_KEY, JSON.stringify(hold));
}

function emptyBookingEditForm() {
    return {
        id: '',
        bookingDate: '',
        startTime: '',
        endTime: '',
        employeeId: '',
        treatmentId: '',
        customerName: '',
        customerPhone: '',
        customerEmail: '',
        holdAmount: '',
        status: 'CONFIRMED'
    };
}

function toBookingEditForm(booking) {
    if (!booking) {
        return emptyBookingEditForm();
    }

    return {
        id: booking.id || '',
        bookingDate: booking.bookingDate || '',
        startTime: booking.startTime ? String(booking.startTime).slice(0, 5) : '',
        endTime: booking.endTime ? String(booking.endTime).slice(0, 5) : '',
        employeeId: booking.employeeId || '',
        treatmentId: booking.treatmentId || '',
        customerName: booking.customerName || '',
        customerPhone: booking.customerPhone || '',
        customerEmail: booking.customerEmail || '',
        holdAmount: booking.holdAmount != null ? String(booking.holdAmount) : '',
        status: booking.status || 'CONFIRMED'
    };
}

function isDateWithinRange(date, startDate, endDate) {
    return Boolean(date && startDate && endDate && date >= startDate && date <= endDate);
}

function normalizeIdList(values) {
    if (!Array.isArray(values)) {
        return [];
    }

    return [...new Set(values.filter((value) => typeof value === 'string' && value.trim()))];
}

function getEmployeeTreatmentIds(employee) {
    return normalizeIdList(employee?.treatmentIds);
}

function doesEmployeeProvideTreatment(employee, treatmentId) {
    if (!employee || !treatmentId) {
        return false;
    }

    return getEmployeeTreatmentIds(employee).includes(treatmentId);
}

function normalizePhoneDigits(value) {
    if (typeof value !== 'string') {
        return '';
    }

    return value.replace(/\D/g, '');
}

function areSalonHoursEqual(left, right) {
    return Boolean(left && right)
        && left.dayOfWeek === right.dayOfWeek
        && Boolean(left.workingDay) === Boolean(right.workingDay)
        && (left.openTime || '') === (right.openTime || '')
        && (left.closeTime || '') === (right.closeTime || '');
}

function getDayOfWeekFromIso(value) {
    const date = isoToDate(value);
    const jsDay = date.getDay();
    return DAYS[(jsDay + 6) % 7];
}

function startOfIsoWeek(value) {
    const date = isoToDate(value);
    const diff = (date.getDay() + 6) % 7;
    date.setDate(date.getDate() - diff);
    return dateToIso(date);
}

function mapScheduleResponseToPeriodDays(scheduleDays) {
    const byDay = new Map(
        (scheduleDays || []).map((item) => [getDayOfWeekFromIso(item.workingDate), item])
    );

    return DAYS.map((day) => {
        const value = byDay.get(day);

        return {
            dayOfWeek: day,
            workingDay: Boolean(value?.workingDay),
            openTime: value?.openTime
                ? String(value.openTime).slice(0, 5)
                : DEFAULT_SCHEDULE_TIMES.openTime,
            closeTime: value?.closeTime
                ? String(value.closeTime).slice(0, 5)
                : DEFAULT_SCHEDULE_TIMES.closeTime,
            breakStartTime: value?.breakStartTime
                ? String(value.breakStartTime).slice(0, 5)
                : '',
            breakEndTime: value?.breakEndTime
                ? String(value.breakEndTime).slice(0, 5)
                : ''
        };
    });
}

function App() {
    const location = useLocation();
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [authReady, setAuthReady] = useState(false);
    const [loading, setLoading] = useState(false);
    const [sectionErrors, setSectionErrors] = useState({});
    const [sectionSuccess, setSectionSuccess] = useState({});
    const [fieldErrors, setFieldErrors] = useState({});
    const successTimersRef = useRef({});

    const [loginForm, setLoginForm] = useState({ username: '', password: '' });

    const [hairSalon, setHairSalon] = useState(null);
    const [hairSalonForm, setHairSalonForm] = useState({
        name: '',
        description: '',
        email: '',
        phone: '',
        address: ''
    });
    const [hairSalonHours, setHairSalonHours] = useState(emptyDayConfig());
    const [hairSalonHoursSnapshot, setHairSalonHoursSnapshot] = useState(emptyDayConfig());

    const [employees, setEmployees] = useState([]);
    const [bookableEmployees, setBookableEmployees] = useState([]);
    const [employeeForm, setEmployeeForm] = useState({
        id: '',
        name: '',
        role: '',
        bio: '',
        photoUrl: '',
        bookable: false,
        treatmentIds: []
    });
    const [selectedEmployeeId, setSelectedEmployeeId] = useState('');

    const [periodTargetEmployeeId, setPeriodTargetEmployeeId] = useState(ALL_EMPLOYEES_OPTION);
    const [periodStartDate, setPeriodStartDate] = useState(todayIso());
    const [periodEndDate, setPeriodEndDate] = useState(todayIso());
    const [employeePeriodDays, setEmployeePeriodDays] = useState(emptyEmployeePeriodConfig());
    const [periodTemplateSourceEmployeeId, setPeriodTemplateSourceEmployeeId] = useState('');
    const [periodTemplateWeekStart, setPeriodTemplateWeekStart] = useState(todayIso());

    const [selectedDate, setSelectedDate] = useState(todayIso());
    const [employeeDay, setEmployeeDay] = useState(mapScheduleDay(null, todayIso()));

    const [treatments, setTreatments] = useState([]);
    const [treatmentForm, setTreatmentForm] = useState({
        id: '',
        name: '',
        durationMinutes: '',
        price: '',
        photoUrl: '',
        description: ''
    });

    const [confirmedReviewOverview, setConfirmedReviewOverview] = useState({
        bookings: [],
        confirmedCount: 0,
        filteredCount: 0
    });
    const [allBookingsOverview, setAllBookingsOverview] = useState({
        bookings: [],
        confirmedCount: 0,
        filteredCount: 0
    });
    const [confirmedReviewSearchQuery, setConfirmedReviewSearchQuery] = useState('');
    const [bookingSearchQuery, setBookingSearchQuery] = useState('');
    const [bookingBlacklistEntries, setBookingBlacklistEntries] = useState([]);
    const [bookingBlacklistForm, setBookingBlacklistForm] = useState({
        phone: '',
        email: '',
        reason: ''
    });
    const [manualBookingEmployeeId, setManualBookingEmployeeId] = useState('');
    const [manualBookingTreatmentId, setManualBookingTreatmentId] = useState('');
    const [manualBookingDate, setManualBookingDate] = useState(todayIso());
    const [manualBookingSlots, setManualBookingSlots] = useState([]);
    const [manualBookingSlotsLoading, setManualBookingSlotsLoading] = useState(false);
    const [manualBookingHoldLoading, setManualBookingHoldLoading] = useState(false);
    const [manualBookingHold, setManualBookingHold] = useState(null);
    const [manualBookingSelectedSlot, setManualBookingSelectedSlot] = useState(null);
    const [manualBookingForm, setManualBookingForm] = useState({
        customerName: '',
        customerPhone: '',
        customerEmail: ''
    });
    const [manualBookingLookupLoading, setManualBookingLookupLoading] = useState(false);
    const [manualBookingLookupMessage, setManualBookingLookupMessage] = useState('');
    const manualBookingAutofillRef = useRef(null);
    const manualBookingHoldRef = useRef(null);
    const adminHoldSessionIdRef = useRef(getOrCreateAdminHoldSessionId());
    const [bookingEditForm, setBookingEditForm] = useState(emptyBookingEditForm());

    const hairSalonId = hairSalon?.id || '';

    const sortedEmployees = useMemo(
        () => [...employees].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [employees]
    );

    const sortedBookableEmployees = useMemo(
        () => [...bookableEmployees].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [bookableEmployees]
    );

    const sortedTreatments = useMemo(
        () => [...treatments].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [treatments]
    );

    const filteredManualBookingEmployees = useMemo(() => {
        if (!manualBookingTreatmentId) {
            return [];
        }

        return sortedBookableEmployees.filter((employee) =>
            doesEmployeeProvideTreatment(employee, manualBookingTreatmentId)
        );
    }, [manualBookingTreatmentId, sortedBookableEmployees]);

    function clearSectionError(section) {
        setSectionErrors((prev) => ({ ...prev, [section]: '' }));
    }

    function setSectionError(section, message) {
        setSectionErrors((prev) => ({ ...prev, [section]: message }));
    }

    function clearFieldErrors(section) {
        setFieldErrors((prev) => ({ ...prev, [section]: {} }));
    }

    function setSectionFieldErrors(section, errors) {
        setFieldErrors((prev) => ({ ...prev, [section]: errors || {} }));
    }

    function showSectionSuccess(section, message) {
        const existingTimer = successTimersRef.current[section];
        if (existingTimer) {
            clearTimeout(existingTimer);
        }

        setSectionSuccess((prev) => ({ ...prev, [section]: message }));
        successTimersRef.current[section] = setTimeout(() => {
            setSectionSuccess((prev) => ({ ...prev, [section]: '' }));
            delete successTimersRef.current[section];
        }, 3000);
    }

    function clearFieldError(section, field) {
        setFieldErrors((prev) => ({
            ...prev,
            [section]: {
                ...(prev[section] || {}),
                [field]: ''
            }
        }));
    }

    function extractFieldErrors(err) {
        return err?.response?.data?.fieldErrors || {};
    }

    function clearAuthenticatedState() {
        setIsAuthenticated(false);
        setAuthReady(true);
        setLoginForm((current) => ({
            ...current,
            password: ''
        }));
    }

    function applyManualBookingAvailability(
        slots,
        {
            employeeId = manualBookingEmployeeId,
            treatmentId = manualBookingTreatmentId,
            bookingDate = manualBookingDate
        } = {}
    ) {
        const nextSlots = Array.isArray(slots) ? slots : [];
        const currentHold = manualBookingHoldRef.current;
        const nextSlotsWithHold = currentHold
            && currentHold.employeeId === employeeId
            && currentHold.treatmentId === treatmentId
            && currentHold.bookingDate === bookingDate
            ? markHeldSlotInList(nextSlots, currentHold)
            : nextSlots;

        setManualBookingSlots(nextSlotsWithHold);
        setManualBookingSelectedSlot((current) => {
            if (!current) {
                return null;
            }

            const matchedSlot = nextSlotsWithHold.find((slot) => areSameSlot(slot, current));
            if (!matchedSlot) {
                return null;
            }

            return currentHold && areSameSlot(currentHold, matchedSlot)
                ? toHeldSlot(matchedSlot)
                : matchedSlot.available
                    ? matchedSlot
                    : null;
        });

        return nextSlotsWithHold;
    }

    async function refreshManualBookingAvailability(
        {
            employeeId = manualBookingEmployeeId,
            treatmentId = manualBookingTreatmentId,
            bookingDate = manualBookingDate
        } = {}
    ) {
        if (!employeeId || !treatmentId || !bookingDate) {
            setManualBookingSlots([]);
            setManualBookingSelectedSlot(null);
            return [];
        }

        const slots = await publicApi.getAvailability({
            employeeId,
            treatmentId,
            date: bookingDate
        });

        return applyManualBookingAvailability(slots, {
            employeeId,
            treatmentId,
            bookingDate
        });
    }

    async function releaseCurrentManualBookingHold(options = {}) {
        const currentHold = manualBookingHoldRef.current;
        if (!currentHold?.id) {
            return null;
        }

        const {
            keepalive = false,
            restoreSlot = true,
            silent = false
        } = options;

        try {
            if (keepalive) {
                await adminApi.releaseAdminBookingHoldKeepalive(currentHold.id, currentHold.sessionId);
            } else {
                await adminApi.releaseAdminBookingHold(currentHold.id, currentHold.sessionId);
            }
        } catch (error) {
            if (!silent) {
                throw error;
            }

            reportAppError(error, {
                source: 'admin-release-hold',
                level: 'warn',
                message: 'The admin booking hold could not be released cleanly.',
                notify: false
            });
        } finally {
            if (manualBookingHoldRef.current?.id === currentHold.id) {
                manualBookingHoldRef.current = null;
            }

            setManualBookingHold((current) => (current?.id === currentHold.id ? null : current));
            setManualBookingSelectedSlot((current) => (current && areSameSlot(current, currentHold) ? null : current));

            if (restoreSlot) {
                setManualBookingSlots((currentSlots) => releaseSlotFromList(currentSlots, currentHold));
            }
        }

        return currentHold;
    }

    useEffect(
        () => () => {
            Object.values(successTimersRef.current).forEach((timerId) => clearTimeout(timerId));
        },
        []
    );

    useEffect(() => {
        manualBookingHoldRef.current = manualBookingHold;
        persistAdminHoldState(manualBookingHold);
    }, [manualBookingHold]);

    useEffect(() => {
        return setUnauthorizedHandler(() => {
            releaseCurrentManualBookingHold({
                keepalive: true,
                restoreSlot: false,
                silent: true
            });
            setLoading(false);
            clearAuthenticatedState();
            setFieldErrors({});
            setSectionSuccess({});
            setSectionErrors({
                login: 'Your session expired or is no longer valid. Please sign in again to continue.'
            });
            setLoginForm((current) => ({
                ...current,
                password: ''
            }));
        });
    }, []);

    useEffect(() => {
        let ignore = false;

        async function bootstrapAuth() {
            try {
                await authApi.getSession();
                if (!ignore) {
                    setIsAuthenticated(true);
                    setAuthReady(true);
                }
            } catch (err) {
                if (!ignore) {
                    setIsAuthenticated(false);
                    setFieldErrors({});
                    setSectionSuccess({});
                    setSectionErrors(
                        err?.response?.status && err.response.status !== 401
                            ? { login: 'Unable to verify the current admin session. Please sign in again.' }
                            : {}
                    );
                    setLoginForm((current) => ({
                        ...current,
                        password: ''
                    }));
                    setAuthReady(true);
                }
            }
        }

        bootstrapAuth();

        return () => {
            ignore = true;
        };
    }, []);

    async function loadPublicData() {
        const [salonData, employeesData, bookableEmployeesData, treatmentsData] = await Promise.all([
            publicApi.getHairSalon(),
            publicApi.getEmployees(),
            publicApi.getEmployees({ bookable: true }),
            publicApi.getTreatments()
        ]);

        setHairSalon(salonData);
        setHairSalonForm({
            name: salonData.name || '',
            description: salonData.description || '',
            email: salonData.email || '',
            phone: salonData.phone || '',
            address: salonData.address || ''
        });
        const mappedSalonHours = mapHoursResponse(salonData.workingHours || []);
        setHairSalonHours(mappedSalonHours);
        setHairSalonHoursSnapshot(mappedSalonHours);
        setEmployees(employeesData || []);
        setBookableEmployees(bookableEmployeesData || []);
        setTreatments(treatmentsData || []);

        setSelectedEmployeeId((prev) =>
            (bookableEmployeesData || []).some((employee) => employee.id === prev)
                ? prev
                : bookableEmployeesData?.[0]?.id || ''
        );
        setManualBookingTreatmentId((prev) =>
            (treatmentsData || []).some((treatment) => treatment.id === prev) ? prev : treatmentsData?.[0]?.id || ''
        );
        setPeriodTemplateSourceEmployeeId((prev) =>
            (bookableEmployeesData || []).some((employee) => employee.id === prev)
                ? prev
                : bookableEmployeesData?.[0]?.id || ''
        );

        return {
            salonData,
            employeesData: employeesData || [],
            treatmentsData: treatmentsData || []
        };
    }

    async function loadAdminData(salonIdOverride) {
        if (!isAuthenticated) {
            return;
        }

        const [periodSettings, bookingOverview, confirmedReviewData, blacklistEntries] = await Promise.all([
            adminApi.getEmployeePeriodSettings(),
            adminApi.getAdminBookings(bookingSearchQuery),
            adminApi.getAdminBookings(confirmedReviewSearchQuery),
            adminApi.getBookingBlacklist()
        ]);

        setAllBookingsOverview(toBookingOverview(bookingOverview));
        setConfirmedReviewOverview(toConfirmedReviewOverview(confirmedReviewData));
        setBookingBlacklistEntries(blacklistEntries || []);

        const mappedPeriodSettings = mapPeriodSettings(periodSettings, todayIso());
        setPeriodTargetEmployeeId(mappedPeriodSettings.targetEmployeeId);
        setPeriodStartDate(mappedPeriodSettings.startDate);
        setPeriodEndDate(mappedPeriodSettings.endDate);
        setEmployeePeriodDays(mappedPeriodSettings.days);

        const effectiveSalonId = salonIdOverride || hairSalonId;

        if (effectiveSalonId) {
            const salonHours = await adminApi.getHairSalonHours(effectiveSalonId);
            const mappedSalonHours = mapHoursResponse(salonHours);
            setHairSalonHours(mappedSalonHours);
            setHairSalonHoursSnapshot(mappedSalonHours);
        }
    }

    async function refreshAll() {
        setLoading(true);
        clearSectionError('global');

        try {
            const publicData = await loadPublicData();
            await loadAdminData(publicData?.salonData?.id || null);
            showSectionSuccess('global', 'Data refreshed');
        } catch (err) {
            setSectionError('global', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    useEffect(() => {
        if (!authReady) {
            return;
        }

        if (!isAuthenticated) {
            return;
        }

        refreshAll();
    }, [isAuthenticated, authReady]);

    useEffect(() => {
        if (isAuthenticated && selectedEmployeeId) {
            loadEmployeeDay(selectedDate);
        }
    }, [isAuthenticated, selectedEmployeeId, selectedDate]);

    useEffect(() => {
        manualBookingAutofillRef.current = null;
        setManualBookingLookupMessage('');
        setManualBookingLookupLoading(false);
    }, [isAuthenticated]);

    useEffect(() => {
        if (!isAuthenticated) {
            manualBookingHoldRef.current = null;
            setManualBookingHold(null);
            setManualBookingSelectedSlot(null);
            return;
        }

        const storedHold = readStoredAdminHoldState();
        if (!storedHold?.id || !storedHold.sessionId) {
            return;
        }

        let ignore = false;

        async function releaseRecoveredAdminHold() {
            try {
                await adminApi.releaseAdminBookingHold(storedHold.id, storedHold.sessionId);
            } catch (error) {
                reportAppError(error, {
                    source: 'admin-recover-stale-hold',
                    level: 'warn',
                    message: 'A stale admin hold could not be cleaned up automatically.',
                    notify: false
                });
            } finally {
                if (manualBookingHoldRef.current?.id === storedHold.id) {
                    manualBookingHoldRef.current = null;
                }

                if (!ignore) {
                    setManualBookingHold((current) => (current?.id === storedHold.id ? null : current));
                    setManualBookingSelectedSlot((current) => (current && areSameSlot(current, storedHold) ? null : current));
                }
            }
        }

        releaseRecoveredAdminHold();

        return () => {
            ignore = true;
        };
    }, [isAuthenticated]);

    useEffect(() => {
        setManualBookingEmployeeId((prev) =>
            filteredManualBookingEmployees.some((employee) => employee.id === prev)
                ? prev
                : filteredManualBookingEmployees[0]?.id || ''
        );
    }, [filteredManualBookingEmployees]);

    useEffect(() => {
        setManualBookingSelectedSlot(null);
    }, [manualBookingTreatmentId, manualBookingEmployeeId]);

    useEffect(() => {
        const currentHold = manualBookingHoldRef.current;
        if (!currentHold) {
            return;
        }

        if (
            currentHold.employeeId !== manualBookingEmployeeId
            || currentHold.treatmentId !== manualBookingTreatmentId
            || currentHold.bookingDate !== manualBookingDate
        ) {
            releaseCurrentManualBookingHold({ silent: true });
        }
    }, [manualBookingEmployeeId, manualBookingTreatmentId, manualBookingDate]);

    useEffect(() => {
        setPeriodTemplateSourceEmployeeId((prev) =>
            sortedBookableEmployees.some((employee) => employee.id === prev)
                ? prev
                : sortedBookableEmployees[0]?.id || ''
        );
    }, [sortedBookableEmployees]);

    useEffect(() => {
        setSelectedEmployeeId((prev) =>
            sortedBookableEmployees.some((employee) => employee.id === prev)
                ? prev
                : sortedBookableEmployees[0]?.id || ''
        );
    }, [sortedBookableEmployees]);

    useEffect(() => {
        setPeriodTargetEmployeeId((prev) => {
            if (prev === ALL_EMPLOYEES_OPTION) {
                return prev;
            }

            return sortedBookableEmployees.some((employee) => employee.id === prev)
                ? prev
                : ALL_EMPLOYEES_OPTION;
        });
    }, [sortedBookableEmployees]);

    useEffect(() => {
        const activeTreatmentIds = new Set(sortedTreatments.map((treatment) => treatment.id));

        setEmployeeForm((current) => {
            const currentTreatmentIds = normalizeIdList(current.treatmentIds);
            const nextTreatmentIds = currentTreatmentIds.filter((id) => activeTreatmentIds.has(id));
            const employeeStillExists = !current.id || sortedEmployees.some((employee) => employee.id === current.id);

            if (!employeeStillExists) {
                return {
                    id: '',
                    name: '',
                    role: '',
                    bio: '',
                    photoUrl: '',
                    bookable: false,
                    treatmentIds: []
                };
            }

            if (
                nextTreatmentIds.length === currentTreatmentIds.length
                && nextTreatmentIds.every((id, index) => id === currentTreatmentIds[index])
            ) {
                return current;
            }

            return {
                ...current,
                treatmentIds: nextTreatmentIds
            };
        });
    }, [sortedEmployees, sortedTreatments]);

    useEffect(() => {
        setTreatmentForm((current) => (
            current.id && !sortedTreatments.some((treatment) => treatment.id === current.id)
                ? {
                    id: '',
                    name: '',
                    durationMinutes: '',
                    price: '',
                    photoUrl: '',
                    description: ''
                }
                : current
        ));
    }, [sortedTreatments]);

    useEffect(() => {
        if (!isAuthenticated) {
            return undefined;
        }

        const trimmedPhone = manualBookingForm.customerPhone.trim();
        const normalizedPhone = normalizePhoneDigits(trimmedPhone);

        if (normalizedPhone.length < 7) {
            setManualBookingLookupLoading(false);
            setManualBookingLookupMessage('');
            manualBookingAutofillRef.current = null;
            return undefined;
        }

        let ignore = false;

        const debounceId = window.setTimeout(async () => {
            setManualBookingLookupLoading(true);

            try {
                const customer = await adminApi.lookupBookingCustomer(trimmedPhone);
                if (ignore) {
                    return;
                }

                if (!customer) {
                    setManualBookingLookupMessage('');
                    manualBookingAutofillRef.current = null;
                    return;
                }

                const previousAutofill = manualBookingAutofillRef.current;
                setManualBookingForm((current) => {
                    if (normalizePhoneDigits(current.customerPhone) !== normalizedPhone) {
                        return current;
                    }

                    return {
                        ...current,
                        customerName:
                            !current.customerName.trim()
                                || current.customerName === (previousAutofill?.customerName || '')
                                ? customer.customerName || current.customerName
                                : current.customerName,
                        customerPhone: customer.customerPhone || current.customerPhone,
                        customerEmail:
                            !current.customerEmail.trim()
                                || current.customerEmail === (previousAutofill?.customerEmail || '')
                                ? customer.customerEmail || current.customerEmail
                                : current.customerEmail
                    };
                });
                manualBookingAutofillRef.current = customer;
                setManualBookingLookupMessage('Loaded previous customer details for this phone number.');
            } catch (error) {
                if (!ignore) {
                    setManualBookingLookupMessage('We could not load previous customer details right now.');
                }

                reportAppError(error, {
                    source: 'admin-booking-customer-lookup',
                    level: 'warn',
                    message: 'Customer autofill is temporarily unavailable.',
                    notify: false
                });
            } finally {
                if (!ignore) {
                    setManualBookingLookupLoading(false);
                }
            }
        }, 320);

        return () => {
            ignore = true;
            window.clearTimeout(debounceId);
        };
    }, [isAuthenticated, manualBookingForm.customerPhone]);

    useEffect(() => {
        if (!isAuthenticated) {
            return undefined;
        }

        const debounceId = window.setTimeout(async () => {
            try {
                const bookingOverview = await adminApi.getAdminBookings(bookingSearchQuery);
                setAllBookingsOverview(toBookingOverview(bookingOverview));
                clearSectionError('bookingList');
            } catch (err) {
                setSectionError('bookingList', getApiErrorMessage(err));
            }
        }, 260);

        return () => window.clearTimeout(debounceId);
    }, [isAuthenticated, bookingSearchQuery]);

    useEffect(() => {
        if (!isAuthenticated) {
            return undefined;
        }

        const debounceId = window.setTimeout(async () => {
            try {
                const bookingOverview = await adminApi.getAdminBookings(confirmedReviewSearchQuery);
                setConfirmedReviewOverview(toConfirmedReviewOverview(bookingOverview));
                clearSectionError('confirmedBookingReview');
            } catch (err) {
                setSectionError('confirmedBookingReview', getApiErrorMessage(err));
            }
        }, 260);

        return () => window.clearTimeout(debounceId);
    }, [isAuthenticated, confirmedReviewSearchQuery]);

    useEffect(() => {
        if (!isAuthenticated || location.pathname === '/bookings') {
            return undefined;
        }

        releaseCurrentManualBookingHold({ silent: true });
        return undefined;
    }, [location.pathname, isAuthenticated]);

    useEffect(() => {
        if (!isAuthenticated || location.pathname !== '/bookings' || !manualBookingHold) {
            return undefined;
        }

        const intervalId = window.setInterval(async () => {
            if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
                return;
            }

            try {
                const refreshedHold = await adminApi.refreshAdminBookingHold(
                    manualBookingHold.id,
                    manualBookingHold.sessionId
                );

                if (manualBookingHoldRef.current?.id === manualBookingHold.id) {
                    manualBookingHoldRef.current = {
                        ...manualBookingHoldRef.current,
                        expiresAt: refreshedHold?.expiresAt || manualBookingHoldRef.current.expiresAt
                    };
                }

                setManualBookingHold((current) => (
                    current?.id === manualBookingHold.id
                        ? {
                            ...current,
                            expiresAt: refreshedHold?.expiresAt || current.expiresAt
                        }
                        : current
                ));
            } catch (err) {
                if (manualBookingHoldRef.current?.id === manualBookingHold.id) {
                    manualBookingHoldRef.current = null;
                }

                setManualBookingHold((current) => (current?.id === manualBookingHold.id ? null : current));
                setManualBookingSelectedSlot((current) => (current && areSameSlot(current, manualBookingHold) ? null : current));
                setSectionError('adminBooking', getApiErrorMessage(err));

                try {
                    await refreshManualBookingAvailability();
                } catch (refreshError) {
                    setSectionError('adminBooking', getApiErrorMessage(refreshError));
                }
            }
        }, ADMIN_HOLD_REFRESH_INTERVAL_MS);

        return () => {
            window.clearInterval(intervalId);
        };
    }, [location.pathname, manualBookingHold, isAuthenticated]);

    useEffect(() => {
        if (!isAuthenticated || location.pathname !== '/bookings') {
            return undefined;
        }

        function releaseForInactivePage() {
            if (!manualBookingHoldRef.current?.id) {
                return;
            }

            releaseCurrentManualBookingHold({
                keepalive: true,
                restoreSlot: false,
                silent: true
            });
        }

        function handleVisibilityChange() {
            if (document.visibilityState === 'hidden') {
                releaseForInactivePage();
            }
        }

        window.addEventListener('pagehide', releaseForInactivePage);
        window.addEventListener('beforeunload', releaseForInactivePage);
        document.addEventListener('visibilitychange', handleVisibilityChange);

        return () => {
            window.removeEventListener('pagehide', releaseForInactivePage);
            window.removeEventListener('beforeunload', releaseForInactivePage);
            document.removeEventListener('visibilitychange', handleVisibilityChange);
        };
    }, [location.pathname, isAuthenticated]);

    useEffect(() => {
        let ignore = false;

        async function loadManualBookingAvailability() {
            if (!isAuthenticated || !manualBookingEmployeeId || !manualBookingTreatmentId || !manualBookingDate) {
                setManualBookingSlots([]);
                setManualBookingSelectedSlot(null);
                return;
            }

            setManualBookingSlotsLoading(true);
            clearSectionError('adminBooking');

            try {
                const slots = await publicApi.getAvailability({
                    employeeId: manualBookingEmployeeId,
                    treatmentId: manualBookingTreatmentId,
                    date: manualBookingDate
                });

                if (ignore) {
                    return;
                }

                applyManualBookingAvailability(slots, {
                    employeeId: manualBookingEmployeeId,
                    treatmentId: manualBookingTreatmentId,
                    bookingDate: manualBookingDate
                });
            } catch (err) {
                if (!ignore) {
                    setManualBookingSlots([]);
                    setManualBookingSelectedSlot(null);
                    setSectionError('adminBooking', getApiErrorMessage(err));
                }
            } finally {
                if (!ignore) {
                    setManualBookingSlotsLoading(false);
                }
            }
        }

        loadManualBookingAvailability();

        return () => {
            ignore = true;
        };
    }, [isAuthenticated, manualBookingEmployeeId, manualBookingTreatmentId, manualBookingDate]);

    useEffect(() => {
        if (
            !isAuthenticated
            || location.pathname !== '/bookings'
            || !manualBookingEmployeeId
            || !manualBookingTreatmentId
            || !manualBookingDate
            || manualBookingHoldLoading
            || manualBookingSlotsLoading
        ) {
            return undefined;
        }

        const intervalId = window.setInterval(() => {
            if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
                return;
            }

            refreshManualBookingAvailability()
                .then(() => {
                    clearSectionError('adminBooking');
                })
                .catch((err) => {
                    setSectionError('adminBooking', getApiErrorMessage(err));
                });
        }, ADMIN_BOOKING_AVAILABILITY_REFRESH_INTERVAL_MS);

        return () => {
            window.clearInterval(intervalId);
        };
    }, [
        location.pathname,
        manualBookingDate,
        manualBookingEmployeeId,
        manualBookingHoldLoading,
        manualBookingSlotsLoading,
        manualBookingTreatmentId,
        isAuthenticated
    ]);

    async function handleLogin(event) {
        event.preventDefault();
        setLoading(true);
        clearSectionError('login');
        clearFieldErrors('login');

        try {
            const response = await authApi.login(loginForm);
            setIsAuthenticated(true);
            setAuthReady(true);
            showSectionSuccess(
                'login',
                `Authenticated as ${response.username || loginForm.username}. Session expires in ${response.expiresInSeconds}s.`
            );
        } catch (err) {
            setSectionFieldErrors('login', extractFieldErrors(err));
            setSectionError('login', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function handleLogout() {
        setLoading(true);
        clearSectionError('global');

        try {
            try {
                await releaseCurrentManualBookingHold({
                    keepalive: true,
                    restoreSlot: false,
                    silent: true
                });
            } catch (error) {
                reportAppError(error, {
                    source: 'admin-logout-release-hold',
                    level: 'warn',
                    message: 'The admin booking hold could not be released before logout.',
                    notify: false
                });
            }

            try {
                await authApi.logout();
            } catch (err) {
                if (err?.response?.status !== 401) {
                    setSectionError('global', getApiErrorMessage(err));
                    return;
                }
            }

            setFieldErrors({});
            setSectionSuccess({});
            setSectionErrors({});
            clearAuthenticatedState();
        } finally {
            setLoading(false);
        }
    }

    async function updateHairSalon() {
        setLoading(true);
        clearSectionError('hairSalon');
        clearFieldErrors('hairSalon');

        try {
            await adminApi.updateHairSalon(hairSalonForm);
            await loadPublicData();
            showSectionSuccess('hairSalon', 'Hair salon updated');
        } catch (err) {
            setSectionFieldErrors('hairSalon', extractFieldErrors(err));
            setSectionError('hairSalon', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateSalonDay(dayConfig) {
        if (!hairSalonId) {
            setSectionError('hairSalonHours', 'Hair salon is not loaded');
            return;
        }

        setLoading(true);
        clearSectionError('hairSalonHours');

        try {
            await adminApi.updateHairSalonHours(hairSalonId, dayConfig.dayOfWeek, {
                workingDay: dayConfig.workingDay,
                openTime: dayConfig.workingDay ? dayConfig.openTime : null,
                closeTime: dayConfig.workingDay ? dayConfig.closeTime : null
            });

            const updatedHours = await adminApi.getHairSalonHours(hairSalonId);
            const mappedSalonHours = mapHoursResponse(updatedHours);
            setHairSalonHours(mappedSalonHours);
            setHairSalonHoursSnapshot(mappedSalonHours);
            showSectionSuccess('hairSalonHours', `Hair salon hours updated for ${dayConfig.dayOfWeek}`);
        } catch (err) {
            setSectionError('hairSalonHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function copyMondayHoursToWeekdays() {
        const monday = hairSalonHours.find((item) => item.dayOfWeek === 'MONDAY');
        if (!monday) {
            setSectionError('hairSalonHours', 'Monday working hours are not available to copy.');
            return;
        }

        setHairSalonHours((previous) =>
            previous.map((item) =>
                ['TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'].includes(item.dayOfWeek)
                    ? {
                        ...item,
                        workingDay: monday.workingDay,
                        openTime: monday.openTime,
                        closeTime: monday.closeTime
                    }
                    : item
            )
        );
        clearSectionError('hairSalonHours');
        showSectionSuccess('hairSalonHours', 'Copied Monday hours to Tuesday-Friday.');
    }

    async function saveAllSalonHours() {
        if (!hairSalonId) {
            setSectionError('hairSalonHours', 'Hair salon is not loaded');
            return;
        }

        const changedRows = hairSalonHours.filter((row) => {
            const original = hairSalonHoursSnapshot.find((item) => item.dayOfWeek === row.dayOfWeek);
            return !areSalonHoursEqual(row, original);
        });

        if (changedRows.length === 0) {
            showSectionSuccess('hairSalonHours', 'No working-hour changes to save.');
            return;
        }

        setLoading(true);
        clearSectionError('hairSalonHours');

        try {
            await Promise.all(
                changedRows.map((row) =>
                    adminApi.updateHairSalonHours(hairSalonId, row.dayOfWeek, {
                        workingDay: row.workingDay,
                        openTime: row.workingDay ? row.openTime : null,
                        closeTime: row.workingDay ? row.closeTime : null
                    })
                )
            );

            const updatedHours = await adminApi.getHairSalonHours(hairSalonId);
            const mappedSalonHours = mapHoursResponse(updatedHours);
            setHairSalonHours(mappedSalonHours);
            setHairSalonHoursSnapshot(mappedSalonHours);
            showSectionSuccess('hairSalonHours', `Saved ${changedRows.length} working-hour changes.`);
        } catch (err) {
            setSectionError('hairSalonHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function clearEmployeeForm() {
        setEmployeeForm({
            id: '',
            name: '',
            role: '',
            bio: '',
            photoUrl: '',
            bookable: false,
            treatmentIds: []
        });
    }

    async function createEmployee() {
        setLoading(true);
        clearSectionError('employees');
        clearFieldErrors('employees');

        try {
            await adminApi.createEmployee({
                name: employeeForm.name,
                role: employeeForm.role,
                bio: employeeForm.bio || null,
                photoUrl: employeeForm.photoUrl || null,
                bookable: Boolean(employeeForm.bookable),
                treatmentIds: normalizeIdList(employeeForm.treatmentIds)
            });
            await loadPublicData();
            clearEmployeeForm();
            showSectionSuccess('employees', 'Employee created');
        } catch (err) {
            setSectionFieldErrors('employees', extractFieldErrors(err));
            setSectionError('employees', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateEmployee() {
        if (!employeeForm.id) {
            setSectionError('employees', 'Open an employee with Edit before saving changes.');
            return;
        }

        setLoading(true);
        clearSectionError('employees');
        clearFieldErrors('employees');

        try {
            await adminApi.updateEmployee(employeeForm.id, {
                name: employeeForm.name,
                role: employeeForm.role,
                bio: employeeForm.bio || null,
                photoUrl: employeeForm.photoUrl || null,
                bookable: Boolean(employeeForm.bookable),
                treatmentIds: normalizeIdList(employeeForm.treatmentIds)
            });
            await loadPublicData();
            showSectionSuccess('employees', 'Employee updated');
        } catch (err) {
            setSectionFieldErrors('employees', extractFieldErrors(err));
            setSectionError('employees', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function deleteEmployee(employeeId) {
        setLoading(true);
        clearSectionError('employees');

        try {
            await adminApi.deleteEmployee(employeeId);
            await loadPublicData();
            setEmployeeForm((current) => (current.id === employeeId ? {
                id: '',
                name: '',
                role: '',
                bio: '',
                photoUrl: '',
                bookable: false,
                treatmentIds: []
            } : current));
            showSectionSuccess('employees', 'Employee deleted');
        } catch (err) {
            setSectionError('employees', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function swapEmployees(firstEmployeeId, secondEmployeeId) {
        if (!firstEmployeeId || !secondEmployeeId || firstEmployeeId === secondEmployeeId) {
            return;
        }

        setLoading(true);
        clearSectionError('reorder');
        setSectionSuccess((prev) => ({ ...prev, reorder: '' }));

        try {
            await adminApi.reorderEmployees({ id1: firstEmployeeId, id2: secondEmployeeId });
            await loadPublicData();
            showSectionSuccess('reorder', 'Employees reordered');
        } catch (err) {
            setSectionError('reorder', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function loadEmployeeDay(dateOverride) {
        if (!selectedEmployeeId) {
            setSectionError('employeeHours', 'Select employee first');
            return;
        }

        const workingDate = dateOverride || selectedDate;
        setLoading(true);
        clearSectionError('employeeHours');

        try {
            const days = await adminApi.getEmployeeSchedule(selectedEmployeeId, workingDate, workingDate);
            const day = days?.[0] || { workingDate };
            setEmployeeDay(mapScheduleDay(day, workingDate));
            showSectionSuccess('employeeHours', 'Employee schedule loaded');
        } catch (err) {
            setSectionError('employeeHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateEmployeeDay() {
        if (!selectedEmployeeId) {
            setSectionError('employeeHours', 'Select employee first');
            return;
        }

        setLoading(true);
        clearSectionError('employeeHours');

        try {
            await adminApi.upsertEmployeeDay(selectedEmployeeId, {
                workingDate: employeeDay.workingDate,
                workingDay: employeeDay.workingDay,
                openTime: employeeDay.workingDay ? employeeDay.openTime : null,
                closeTime: employeeDay.workingDay ? employeeDay.closeTime : null,
                breakStartTime: employeeDay.workingDay ? (employeeDay.breakStartTime || null) : null,
                breakEndTime: employeeDay.workingDay ? (employeeDay.breakEndTime || null) : null
            });
            await loadEmployeeDay(employeeDay.workingDate);
            showSectionSuccess('employeeHours', `Employee schedule saved for ${employeeDay.workingDate}`);
        } catch (err) {
            setSectionError('employeeHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function updateEmployeePeriodDay(dayOfWeek, patch) {
        setEmployeePeriodDays((prev) =>
            prev.map((item) =>
                item.dayOfWeek === dayOfWeek
                    ? {
                        ...item,
                        ...patch
                    }
                    : item
            )
        );
    }

    function applyEmployeePeriodSalonDefaults() {
        setEmployeePeriodDays(
            DAYS.map((day) => {
                const salonHours = hairSalonHours.find((item) => item.dayOfWeek === day);

                return {
                    dayOfWeek: day,
                    workingDay: Boolean(salonHours?.workingDay),
                    openTime: salonHours?.openTime || DEFAULT_SCHEDULE_TIMES.openTime,
                    closeTime: salonHours?.closeTime || DEFAULT_SCHEDULE_TIMES.closeTime,
                    breakStartTime: '',
                    breakEndTime: ''
                };
            })
        );
        clearSectionError('employeePeriodHours');
        showSectionSuccess('employeePeriodHours', 'Loaded salon default hours into the period template.');
    }

    async function loadEmployeeWeekPattern(sourceEmployeeId, weekStart, successMessage) {
        if (!sourceEmployeeId) {
            setSectionError('employeePeriodHours', 'Choose a source employee for the template.');
            return;
        }

        if (!isValidIsoDate(weekStart)) {
            setSectionError('employeePeriodHours', 'Choose a valid week start date for the template.');
            return;
        }

        setLoading(true);
        clearSectionError('employeePeriodHours');

        try {
            const weekEnd = isoToDate(weekStart);
            weekEnd.setDate(weekEnd.getDate() + 6);
            const weekEndDate = dateToIso(weekEnd);
            const sourceSchedule = await adminApi.getEmployeeSchedule(sourceEmployeeId, weekStart, weekEndDate);
            setEmployeePeriodDays(mapScheduleResponseToPeriodDays(sourceSchedule));
            showSectionSuccess('employeePeriodHours', successMessage);
        } catch (err) {
            setSectionError('employeePeriodHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function copyEmployeePeriodFromEmployee() {
        const sourceEmployeeName =
            sortedBookableEmployees.find((item) => item.id === periodTemplateSourceEmployeeId)?.name || 'selected employee';

        await loadEmployeeWeekPattern(
            periodTemplateSourceEmployeeId,
            periodTemplateWeekStart,
            `Copied weekly pattern from ${sourceEmployeeName}.`
        );
    }

    async function repeatSelectedWeekPattern() {
        if (!selectedEmployeeId) {
            setSectionError('employeePeriodHours', 'Choose an employee in the per-date section first.');
            return;
        }

        const sourceWeekStart = startOfIsoWeek(selectedDate);
        const sourceEmployeeName =
            sortedBookableEmployees.find((item) => item.id === selectedEmployeeId)?.name || 'selected employee';

        await loadEmployeeWeekPattern(
            selectedEmployeeId,
            sourceWeekStart,
            `Loaded the current week pattern from ${sourceEmployeeName}.`
        );
    }

    async function updateEmployeePeriod() {
        if (sortedEmployees.length === 0) {
            setSectionError('employeePeriodHours', 'No employees available');
            return;
        }

        setLoading(true);
        clearSectionError('employeePeriodHours');

        const applyToAllEmployees = periodTargetEmployeeId === ALL_EMPLOYEES_OPTION;
        const selectedEmployeeName = applyToAllEmployees
            ? 'all employees'
            : sortedBookableEmployees.find((item) => item.id === periodTargetEmployeeId)?.name || 'selected employee';
        const periodDateError = getEmployeePeriodDateError(periodStartDate, periodEndDate);

        if (periodDateError) {
            setLoading(false);
            setSectionError('employeePeriodHours', periodDateError);
            return;
        }

        try {
            const response = await adminApi.upsertEmployeePeriod({
                startDate: periodStartDate,
                endDate: periodEndDate,
                employeeId: applyToAllEmployees ? null : periodTargetEmployeeId,
                applyToAllEmployees,
                days: employeePeriodDays.map((item) => ({
                    dayOfWeek: item.dayOfWeek,
                    workingDay: item.workingDay,
                    openTime: item.workingDay ? item.openTime : null,
                    closeTime: item.workingDay ? item.closeTime : null,
                    breakStartTime: item.workingDay ? (item.breakStartTime || null) : null,
                    breakEndTime: item.workingDay ? (item.breakEndTime || null) : null
                }))
            });

            const mappedPeriodSettings = mapPeriodSettings(response, todayIso());
            setPeriodTargetEmployeeId(mappedPeriodSettings.targetEmployeeId);
            setPeriodStartDate(mappedPeriodSettings.startDate);
            setPeriodEndDate(mappedPeriodSettings.endDate);
            setEmployeePeriodDays(mappedPeriodSettings.days);

            if (
                selectedEmployeeId &&
                isDateWithinRange(selectedDate, periodStartDate, periodEndDate) &&
                (applyToAllEmployees || selectedEmployeeId === periodTargetEmployeeId)
            ) {
                await loadEmployeeDay(selectedDate);
            }

            showSectionSuccess(
                'employeePeriodHours',
                `Employee period schedule saved for ${selectedEmployeeName} (${periodStartDate} to ${periodEndDate})`
            );
        } catch (err) {
            setSectionError('employeePeriodHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function clearTreatmentForm() {
        setTreatmentForm({
            id: '',
            name: '',
            durationMinutes: '',
            price: '',
            photoUrl: '',
            description: ''
        });
    }

    async function createTreatment() {
        setLoading(true);
        clearSectionError('treatments');
        clearFieldErrors('treatments');

        try {
            await adminApi.createTreatment({
                name: treatmentForm.name,
                durationMinutes: Number(treatmentForm.durationMinutes),
                price: Number(treatmentForm.price),
                photoUrl: treatmentForm.photoUrl || null,
                description: treatmentForm.description
            });
            await loadPublicData();
            clearTreatmentForm();
            showSectionSuccess('treatments', 'Treatment created');
        } catch (err) {
            setSectionFieldErrors('treatments', extractFieldErrors(err));
            setSectionError('treatments', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateTreatment() {
        if (!treatmentForm.id) {
            setSectionError('treatments', 'Open a treatment with Edit before saving changes.');
            return;
        }

        setLoading(true);
        clearSectionError('treatments');
        clearFieldErrors('treatments');

        try {
            await adminApi.updateTreatment(treatmentForm.id, {
                name: treatmentForm.name,
                durationMinutes: Number(treatmentForm.durationMinutes),
                price: Number(treatmentForm.price),
                photoUrl: treatmentForm.photoUrl || null,
                description: treatmentForm.description
            });
            await loadPublicData();
            showSectionSuccess('treatments', 'Treatment updated');
        } catch (err) {
            setSectionFieldErrors('treatments', extractFieldErrors(err));
            setSectionError('treatments', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function deleteTreatment(id) {
        setLoading(true);
        clearSectionError('treatments');

        try {
            await adminApi.deleteTreatment(id);
            await loadPublicData();
            setTreatmentForm((current) => (
                current.id === id
                    ? {
                        id: '',
                        name: '',
                        durationMinutes: '',
                        price: '',
                        photoUrl: '',
                        description: ''
                    }
                    : current
            ));
            showSectionSuccess('treatments', 'Treatment deleted');
        } catch (err) {
            setSectionError('treatments', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function swapTreatments(firstTreatmentId, secondTreatmentId) {
        if (!firstTreatmentId || !secondTreatmentId || firstTreatmentId === secondTreatmentId) {
            return;
        }

        setLoading(true);
        clearSectionError('treatmentReorder');
        setSectionSuccess((prev) => ({ ...prev, treatmentReorder: '' }));
        try {
            await adminApi.reorderTreatments({ id1: firstTreatmentId, id2: secondTreatmentId });
            await loadPublicData();
            showSectionSuccess('treatmentReorder', 'Treatments reordered');
        } catch (err) {
            setSectionError('treatmentReorder', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function fetchAdminBookings(searchOverride = bookingSearchQuery) {
        const bookingOverview = await adminApi.getAdminBookings(searchOverride);
        setAllBookingsOverview(toBookingOverview(bookingOverview));
        return bookingOverview;
    }

    async function fetchConfirmedReviewBookings(searchOverride = confirmedReviewSearchQuery) {
        const bookingOverview = await adminApi.getAdminBookings(searchOverride);
        setConfirmedReviewOverview(toConfirmedReviewOverview(bookingOverview));
        return bookingOverview;
    }

    async function refreshAllBookings() {
        setLoading(true);
        clearSectionError('bookingList');

        try {
            await fetchAdminBookings(bookingSearchQuery);
            showSectionSuccess('bookingList', 'Booking list refreshed');
        } catch (err) {
            setSectionError('bookingList', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function refreshConfirmedReview() {
        setLoading(true);
        clearSectionError('confirmedBookingReview');

        try {
            await fetchConfirmedReviewBookings(confirmedReviewSearchQuery);
            showSectionSuccess('confirmedBookingReview', 'Confirmed bookings refreshed');
        } catch (err) {
            setSectionError('confirmedBookingReview', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function fetchBookingBlacklist() {
        const entries = await adminApi.getBookingBlacklist();
        setBookingBlacklistEntries(entries || []);
        return entries || [];
    }

    async function refreshBookingBlacklist() {
        setLoading(true);
        clearSectionError('bookingBlacklist');

        try {
            await fetchBookingBlacklist();
            showSectionSuccess('bookingBlacklist', 'Blacklist refreshed');
        } catch (err) {
            setSectionError('bookingBlacklist', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function createBookingBlacklistEntry() {
        setLoading(true);
        clearSectionError('bookingBlacklist');
        clearFieldErrors('bookingBlacklist');

        try {
            await adminApi.createBookingBlacklistEntry({
                email: bookingBlacklistForm.email.trim() || null,
                phone: bookingBlacklistForm.phone.trim() || null,
                reason: bookingBlacklistForm.reason.trim() || null
            });
            await fetchBookingBlacklist();
            setBookingBlacklistForm({
                phone: '',
                email: '',
                reason: ''
            });
            showSectionSuccess('bookingBlacklist', 'Blacklist entry created');
        } catch (err) {
            setSectionFieldErrors('bookingBlacklist', extractFieldErrors(err));
            setSectionError('bookingBlacklist', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function deleteBookingBlacklistEntry(id) {
        setLoading(true);
        clearSectionError('bookingBlacklist');

        try {
            await adminApi.deleteBookingBlacklistEntry(id);
            await fetchBookingBlacklist();
            showSectionSuccess('bookingBlacklist', 'Blacklist entry removed');
        } catch (err) {
            setSectionError('bookingBlacklist', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function cancelBookingReview(bookingOrId) {
        const bookingId = typeof bookingOrId === 'string' ? bookingOrId : bookingOrId?.id;

        if (!bookingId) {
            setSectionError('confirmedBookingReview', 'Select a booking before cancelling it.');
            return;
        }

        setLoading(true);
        clearSectionError('confirmedBookingReview');

        try {
            const response = await adminApi.cancelBooking(bookingId);
            await Promise.all([
                fetchAdminBookings(bookingSearchQuery),
                fetchConfirmedReviewBookings(confirmedReviewSearchQuery)
            ]);

            setBookingEditForm((current) =>
                current.id === response.id
                    ? toBookingEditForm(response)
                    : current
            );
            showSectionSuccess('confirmedBookingReview', 'Booking cancelled');
        } catch (err) {
            setSectionError('confirmedBookingReview', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateBookingReview() {
        if (!bookingEditForm.id) {
            setSectionError('bookingEdit', 'Choose a confirmed booking with Edit before saving changes.');
            return;
        }

        setLoading(true);
        clearSectionError('bookingEdit');
        clearFieldErrors('bookingEdit');

        try {
            const response = await adminApi.updateBooking(bookingEditForm.id, {
                employeeId: bookingEditForm.employeeId,
                treatmentId: bookingEditForm.treatmentId,
                bookingDate: bookingEditForm.bookingDate,
                startTime: bookingEditForm.startTime,
                endTime: bookingEditForm.endTime,
                customerName: bookingEditForm.customerName.trim(),
                customerPhone: bookingEditForm.customerPhone.trim() || null,
                customerEmail: bookingEditForm.customerEmail.trim() || null,
                holdAmount: bookingEditForm.holdAmount !== '' ? Number(bookingEditForm.holdAmount) : null,
                status: bookingEditForm.status
            });

            setBookingEditForm(toBookingEditForm(response));
            await Promise.all([
                fetchAdminBookings(bookingSearchQuery),
                fetchConfirmedReviewBookings(confirmedReviewSearchQuery)
            ]);
            showSectionSuccess('bookingEdit', `Booking updated with status ${response.status || bookingEditForm.status}.`);
        } catch (err) {
            setSectionFieldErrors('bookingEdit', extractFieldErrors(err));
            setSectionError('bookingEdit', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function handleManualBookingSlotSelect(slot) {
        if (!slot?.available || manualBookingHoldLoading || !manualBookingEmployeeId || !manualBookingTreatmentId) {
            return;
        }

        const currentHold = manualBookingHoldRef.current;
        if (
            currentHold
            && currentHold.employeeId === manualBookingEmployeeId
            && currentHold.treatmentId === manualBookingTreatmentId
            && currentHold.bookingDate === manualBookingDate
            && areSameSlot(currentHold, slot)
        ) {
            setManualBookingSelectedSlot(toHeldSlot(slot));
            clearSectionError('adminBooking');
            return;
        }

        setManualBookingHoldLoading(true);
        clearSectionError('adminBooking');

        try {
            const releasedHold = await releaseCurrentManualBookingHold({
                restoreSlot: false,
                silent: true
            });

            const response = await adminApi.holdAdminBookingSlot(
                {
                    employeeId: manualBookingEmployeeId,
                    treatmentId: manualBookingTreatmentId,
                    bookingDate: manualBookingDate,
                    startTime: slot.startTime,
                    endTime: slot.endTime
                },
                adminHoldSessionIdRef.current
            );

            const nextHold = {
                id: response.id,
                sessionId: adminHoldSessionIdRef.current,
                employeeId: manualBookingEmployeeId,
                treatmentId: manualBookingTreatmentId,
                bookingDate: manualBookingDate,
                startTime: slot.startTime,
                endTime: slot.endTime,
                expiresAt: response?.expiresAt || null
            };

            manualBookingHoldRef.current = nextHold;
            setManualBookingHold(nextHold);
            setManualBookingSlots((currentSlots) => {
                const releasedSlots = releasedHold
                    ? releaseSlotFromList(currentSlots, releasedHold)
                    : currentSlots;
                return markHeldSlotInList(releasedSlots, nextHold);
            });
            setManualBookingSelectedSlot(toHeldSlot(slot));
        } catch (err) {
            manualBookingHoldRef.current = null;
            setManualBookingHold(null);
            setManualBookingSelectedSlot(null);
            setSectionError('adminBooking', getApiErrorMessage(err));

            try {
                await refreshManualBookingAvailability({
                    employeeId: manualBookingEmployeeId,
                    treatmentId: manualBookingTreatmentId,
                    bookingDate: manualBookingDate
                });
            } catch (refreshError) {
                setSectionError('adminBooking', getApiErrorMessage(refreshError));
            }
        } finally {
            setManualBookingHoldLoading(false);
        }
    }

    async function createAdminBooking() {
        if (!manualBookingEmployeeId || !manualBookingTreatmentId || !manualBookingSelectedSlot) {
            setSectionError('adminBooking', 'Select service, employee, date, and a free time slot first.');
            return;
        }

        setLoading(true);
        clearSectionError('adminBooking');
        clearFieldErrors('adminBooking');

        try {
            const response = await adminApi.createAdminBooking({
                employeeId: manualBookingEmployeeId,
                treatmentId: manualBookingTreatmentId,
                bookingDate: manualBookingDate,
                startTime: manualBookingSelectedSlot.startTime,
                endTime: manualBookingSelectedSlot.endTime,
                holdBookingId: manualBookingHoldRef.current?.id || null,
                customerName: manualBookingForm.customerName.trim(),
                customerPhone: manualBookingForm.customerPhone.trim(),
                customerEmail: manualBookingForm.customerEmail.trim() || null
            });

            manualBookingHoldRef.current = null;
            setManualBookingHold(null);

            await refreshManualBookingAvailability({
                employeeId: manualBookingEmployeeId,
                treatmentId: manualBookingTreatmentId,
                bookingDate: manualBookingDate
            });

            setManualBookingSelectedSlot(null);
            setManualBookingForm({
                customerName: '',
                customerPhone: '',
                customerEmail: ''
            });
            manualBookingAutofillRef.current = null;
            setManualBookingLookupMessage('');
            await Promise.all([
                fetchAdminBookings(bookingSearchQuery),
                fetchConfirmedReviewBookings(confirmedReviewSearchQuery)
            ]);

            showSectionSuccess(
                'adminBooking',
                `Appointment booked for ${response.customerName || 'client'} on ${manualBookingDate} at ${response.startTime?.slice(0, 5) || '--'}.`
            );
        } catch (err) {
            setSectionFieldErrors('adminBooking', extractFieldErrors(err));
            setSectionError('adminBooking', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function fillEmployeeForm(item) {
        setEmployeeForm({
            id: item.id || '',
            name: item.name || '',
            role: item.role || '',
            bio: item.bio || '',
            photoUrl: item.photoUrl || '',
            bookable: Boolean(item.bookable),
            treatmentIds: normalizeIdList(item.treatmentIds)
        });
    }

    function fillTreatmentForm(item) {
        setTreatmentForm({
            id: item.id || '',
            name: item.name || '',
            durationMinutes: item.durationMinutes != null ? String(item.durationMinutes) : '',
            price: item.price != null ? String(item.price) : '',
            photoUrl: item.photoUrl || '',
            description: item.description || ''
        });
    }

    function fillBookingEditForm(booking) {
        setBookingEditForm(toBookingEditForm(booking));
        clearSectionError('bookingEdit');
        clearFieldErrors('bookingEdit');
    }

    function clearBookingEditForm() {
        setBookingEditForm(emptyBookingEditForm());
        clearSectionError('bookingEdit');
        clearFieldErrors('bookingEdit');
    }

    if (!authReady) {
        return (null);
    }

    if (!isAuthenticated) {
        return (
            <AdminLoginPage
                loginForm={loginForm}
                setLoginForm={setLoginForm}
                clearFieldError={clearFieldError}
                fieldErrors={fieldErrors}
                sectionErrors={sectionErrors}
                sectionSuccess={sectionSuccess}
                loading={loading}
                onSubmit={handleLogin}
            />
        );
    }

    return (
        <AdminLayout
            loading={loading}
            onRefresh={refreshAll}
            onLogout={handleLogout}
            globalError={sectionErrors.global}
            globalSuccess={sectionSuccess.global}
        >
            <Routes>
                <Route path="/" element={<Navigate to="/bookings" replace />} />
                <Route
                    path="/bookings"
                    element={
                        <BookingsPage
                            sortedEmployees={sortedBookableEmployees}
                            filteredManualBookingEmployees={filteredManualBookingEmployees}
                            sortedTreatments={sortedTreatments}
                            manualBookingEmployeeId={manualBookingEmployeeId}
                            setManualBookingEmployeeId={(value) => {
                                setManualBookingEmployeeId(value);
                                setManualBookingSelectedSlot(null);
                                clearSectionError('adminBooking');
                            }}
                            manualBookingTreatmentId={manualBookingTreatmentId}
                            setManualBookingTreatmentId={(value) => {
                                setManualBookingTreatmentId(value);
                                setManualBookingSelectedSlot(null);
                                clearSectionError('adminBooking');
                            }}
                            manualBookingDate={manualBookingDate}
                            manualBookingDateObject={isoToDate(manualBookingDate)}
                            onManualBookingDateSelect={(date) => {
                                setManualBookingDate(dateToIso(date));
                                setManualBookingSelectedSlot(null);
                                clearSectionError('adminBooking');
                            }}
                            manualBookingSlots={manualBookingSlots}
                            manualBookingSelectedSlot={manualBookingSelectedSlot}
                            onManualBookingSlotSelect={handleManualBookingSlotSelect}
                            manualBookingSlotsLoading={manualBookingSlotsLoading}
                            manualBookingHoldLoading={manualBookingHoldLoading}
                            manualBookingForm={manualBookingForm}
                            setManualBookingForm={setManualBookingForm}
                            manualBookingLookupLoading={manualBookingLookupLoading}
                            manualBookingLookupMessage={manualBookingLookupMessage}
                            clearFieldError={clearFieldError}
                            fieldErrors={fieldErrors}
                            sectionErrors={sectionErrors}
                            sectionSuccess={sectionSuccess}
                            loading={loading}
                            createAdminBooking={createAdminBooking}
                            confirmedReviewOverview={confirmedReviewOverview}
                            confirmedReviewSearchQuery={confirmedReviewSearchQuery}
                            setConfirmedReviewSearchQuery={setConfirmedReviewSearchQuery}
                            refreshConfirmedReview={refreshConfirmedReview}
                            cancelBookingReview={cancelBookingReview}
                            bookingEditForm={bookingEditForm}
                            setBookingEditForm={setBookingEditForm}
                            fillBookingEditForm={fillBookingEditForm}
                            clearBookingEditForm={clearBookingEditForm}
                            updateBookingReview={updateBookingReview}
                            allBookingsOverview={allBookingsOverview}
                            bookingSearchQuery={bookingSearchQuery}
                            setBookingSearchQuery={setBookingSearchQuery}
                            refreshAllBookings={refreshAllBookings}
                            bookingBlacklistEntries={bookingBlacklistEntries}
                            bookingBlacklistForm={bookingBlacklistForm}
                            setBookingBlacklistForm={setBookingBlacklistForm}
                            createBookingBlacklistEntry={createBookingBlacklistEntry}
                            deleteBookingBlacklistEntry={deleteBookingBlacklistEntry}
                            refreshBookingBlacklist={refreshBookingBlacklist}
                        />
                    }
                />
                <Route
                    path="/employees"
                    element={
                        <EmployeesPage
                            employeeForm={employeeForm}
                            setEmployeeForm={setEmployeeForm}
                            sortedEmployees={sortedEmployees}
                            sortedBookableEmployees={sortedBookableEmployees}
                            sortedTreatments={sortedTreatments}
                            selectedEmployeeId={selectedEmployeeId}
                            setSelectedEmployeeId={setSelectedEmployeeId}
                            selectedDate={selectedDate}
                            setSelectedDate={setSelectedDate}
                            employeeDay={employeeDay}
                            setEmployeeDay={setEmployeeDay}
                            periodTargetEmployeeId={periodTargetEmployeeId}
                            setPeriodTargetEmployeeId={setPeriodTargetEmployeeId}
                            periodStartDate={periodStartDate}
                            setPeriodStartDate={setPeriodStartDate}
                            periodEndDate={periodEndDate}
                            setPeriodEndDate={setPeriodEndDate}
                            employeePeriodDays={employeePeriodDays}
                            periodTemplateSourceEmployeeId={periodTemplateSourceEmployeeId}
                            setPeriodTemplateSourceEmployeeId={setPeriodTemplateSourceEmployeeId}
                            periodTemplateWeekStart={periodTemplateWeekStart}
                            setPeriodTemplateWeekStart={setPeriodTemplateWeekStart}
                            updateEmployeePeriodDay={updateEmployeePeriodDay}
                            loading={loading}
                            clearFieldError={clearFieldError}
                            fieldErrors={fieldErrors}
                            sectionErrors={sectionErrors}
                            sectionSuccess={sectionSuccess}
                            createEmployee={createEmployee}
                            updateEmployee={updateEmployee}
                            clearEmployeeForm={clearEmployeeForm}
                            fillEmployeeForm={fillEmployeeForm}
                            deleteEmployee={deleteEmployee}
                            swapEmployees={swapEmployees}
                            updateEmployeePeriod={updateEmployeePeriod}
                            applyEmployeePeriodSalonDefaults={applyEmployeePeriodSalonDefaults}
                            copyEmployeePeriodFromEmployee={copyEmployeePeriodFromEmployee}
                            repeatSelectedWeekPattern={repeatSelectedWeekPattern}
                            updateEmployeeDay={updateEmployeeDay}
                            formatDayLabel={formatDayLabel}
                            allEmployeesOption={ALL_EMPLOYEES_OPTION}
                        />
                    }
                />
                <Route
                    path="/treatments"
                    element={
                        <TreatmentsPage
                            treatmentForm={treatmentForm}
                            setTreatmentForm={setTreatmentForm}
                            sortedTreatments={sortedTreatments}
                            loading={loading}
                            clearFieldError={clearFieldError}
                            fieldErrors={fieldErrors}
                            sectionErrors={sectionErrors}
                            sectionSuccess={sectionSuccess}
                            createTreatment={createTreatment}
                            updateTreatment={updateTreatment}
                            clearTreatmentForm={clearTreatmentForm}
                            fillTreatmentForm={fillTreatmentForm}
                            deleteTreatment={deleteTreatment}
                            swapTreatments={swapTreatments}
                        />
                    }
                />
                <Route
                    path="/salon"
                    element={
                        <SalonPage
                            hairSalonForm={hairSalonForm}
                            setHairSalonForm={setHairSalonForm}
                            hairSalonHours={hairSalonHours}
                            setHairSalonHours={setHairSalonHours}
                            clearFieldError={clearFieldError}
                            fieldErrors={fieldErrors}
                            loading={loading}
                            sectionErrors={sectionErrors}
                            sectionSuccess={sectionSuccess}
                            updateHairSalon={updateHairSalon}
                            updateSalonDay={updateSalonDay}
                            copyMondayHoursToWeekdays={copyMondayHoursToWeekdays}
                            saveAllSalonHours={saveAllSalonHours}
                        />
                    }
                />
                <Route path="*" element={<Navigate to="/bookings" replace />} />
            </Routes>
        </AdminLayout>
    );
}

export default App;
