import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import {
    adminApi,
    authApi,
    getApiErrorMessage,
    publicApi,
    setUnauthorizedHandler,
    tokenStorage
} from './api';
import AdminLayout from './components/AdminLayout';
import AdminLoginPage from './pages/AdminLoginPage';
import BarbersPage from './pages/BarbersPage';
import BookingsPage from './pages/BookingsPage';
import SalonPage from './pages/SalonPage';
import TreatmentsPage from './pages/TreatmentsPage';

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const ALL_BARBERS_OPTION = '__ALL_BARBERS__';
const DEFAULT_SCHEDULE_TIMES = {
    openTime: '09:00',
    closeTime: '17:00',
    breakStartTime: '13:00',
    breakEndTime: '14:00'
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

function getBarberPeriodDateError(startDate, endDate) {
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
            : DEFAULT_SCHEDULE_TIMES.breakStartTime,
        breakEndTime: day?.breakEndTime
            ? String(day.breakEndTime).slice(0, 5)
            : DEFAULT_SCHEDULE_TIMES.breakEndTime
    };
}

function emptyBarberPeriodConfig() {
    return DAYS.map((day) => ({
        dayOfWeek: day,
        workingDay: false,
        openTime: DEFAULT_SCHEDULE_TIMES.openTime,
        closeTime: DEFAULT_SCHEDULE_TIMES.closeTime,
        breakStartTime: DEFAULT_SCHEDULE_TIMES.breakStartTime,
        breakEndTime: DEFAULT_SCHEDULE_TIMES.breakEndTime
    }));
}

function mapPeriodSettings(periodSettings, fallbackDate) {
    if (!periodSettings) {
        return {
            targetBarberId: ALL_BARBERS_OPTION,
            startDate: fallbackDate,
            endDate: fallbackDate,
            days: emptyBarberPeriodConfig()
        };
    }

    const byDay = new Map((periodSettings.days || []).map((item) => [item.dayOfWeek, item]));

    return {
        targetBarberId:
            periodSettings.applyToAllBarbers || !periodSettings.barberId
                ? ALL_BARBERS_OPTION
                : periodSettings.barberId,
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
                    : DEFAULT_SCHEDULE_TIMES.breakStartTime,
                breakEndTime: value?.breakEndTime
                    ? String(value.breakEndTime).slice(0, 5)
                    : DEFAULT_SCHEDULE_TIMES.breakEndTime
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

function isDateWithinRange(date, startDate, endDate) {
    return Boolean(date && startDate && endDate && date >= startDate && date <= endDate);
}

function App() {
    const [token, setToken] = useState(tokenStorage.get());
    const [loading, setLoading] = useState(false);
    const [sectionErrors, setSectionErrors] = useState({});
    const [sectionSuccess, setSectionSuccess] = useState({});
    const [fieldErrors, setFieldErrors] = useState({});
    const successTimersRef = useRef({});

    const [loginForm, setLoginForm] = useState({ username: 'admin', password: 'password' });

    const [hairSalon, setHairSalon] = useState(null);
    const [hairSalonForm, setHairSalonForm] = useState({
        name: '',
        description: '',
        email: '',
        phone: '',
        address: ''
    });
    const [hairSalonHours, setHairSalonHours] = useState(emptyDayConfig());

    const [barbers, setBarbers] = useState([]);
    const [barberForm, setBarberForm] = useState({
        id: '',
        name: '',
        role: '',
        bio: '',
        photoUrl: '',
        displayOrder: ''
    });
    const [reorderForm, setReorderForm] = useState({ id1: '', id2: '' });
    const [selectedBarberId, setSelectedBarberId] = useState('');

    const [periodTargetBarberId, setPeriodTargetBarberId] = useState(ALL_BARBERS_OPTION);
    const [periodStartDate, setPeriodStartDate] = useState(todayIso());
    const [periodEndDate, setPeriodEndDate] = useState(todayIso());
    const [barberPeriodDays, setBarberPeriodDays] = useState(emptyBarberPeriodConfig());

    const [selectedDate, setSelectedDate] = useState(todayIso());
    const [barberDay, setBarberDay] = useState(mapScheduleDay(null, todayIso()));

    const [treatments, setTreatments] = useState([]);
    const [treatmentForm, setTreatmentForm] = useState({
        id: '',
        name: '',
        durationMinutes: '',
        price: '',
        displayOrder: '',
        photoUrl: '',
        description: ''
    });

    const [treatmentReorderForm, setTreatmentReorderForm] = useState({ id1: '', id2: '' });

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
    const [manualBookingBarberId, setManualBookingBarberId] = useState('');
    const [manualBookingTreatmentId, setManualBookingTreatmentId] = useState('');
    const [manualBookingDate, setManualBookingDate] = useState(todayIso());
    const [manualBookingSlots, setManualBookingSlots] = useState([]);
    const [manualBookingSlotsLoading, setManualBookingSlotsLoading] = useState(false);
    const [manualBookingSelectedSlot, setManualBookingSelectedSlot] = useState(null);
    const [manualBookingForm, setManualBookingForm] = useState({
        customerName: '',
        customerPhone: ''
    });

    const hairSalonId = hairSalon?.id || '';

    const sortedBarbers = useMemo(
        () => [...barbers].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [barbers]
    );

    const sortedTreatments = useMemo(
        () => [...treatments].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [treatments]
    );

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

    useEffect(
        () => () => {
            Object.values(successTimersRef.current).forEach((timerId) => clearTimeout(timerId));
        },
        []
    );

    useEffect(() => {
        return setUnauthorizedHandler(() => {
            setLoading(false);
            setToken(null);
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

    async function loadPublicData() {
        const [salonData, barbersData, treatmentsData] = await Promise.all([
            publicApi.getHairSalon(),
            publicApi.getBarbers(),
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
        setHairSalonHours(mapHoursResponse(salonData.workingHours || []));
        setBarbers(barbersData || []);
        setTreatments(treatmentsData || []);

        if ((barbersData || []).length > 0) {
            setSelectedBarberId((prev) => prev || barbersData[0].id);
            setManualBookingBarberId((prev) => prev || barbersData[0].id);
        }

        if ((treatmentsData || []).length > 0) {
            setManualBookingTreatmentId((prev) => prev || treatmentsData[0].id);
        }

        return {
            salonData,
            barbersData: barbersData || [],
            treatmentsData: treatmentsData || []
        };
    }

    async function loadAdminData(salonIdOverride) {
        if (!tokenStorage.get()) {
            return;
        }

        const [periodSettings, bookingOverview, confirmedReviewData, blacklistEntries] = await Promise.all([
            adminApi.getBarberPeriodSettings(),
            adminApi.getAdminBookings(bookingSearchQuery),
            adminApi.getAdminBookings(confirmedReviewSearchQuery),
            adminApi.getBookingBlacklist()
        ]);

        setAllBookingsOverview(toBookingOverview(bookingOverview));
        setConfirmedReviewOverview(toConfirmedReviewOverview(confirmedReviewData));
        setBookingBlacklistEntries(blacklistEntries || []);

        const mappedPeriodSettings = mapPeriodSettings(periodSettings, todayIso());
        setPeriodTargetBarberId(mappedPeriodSettings.targetBarberId);
        setPeriodStartDate(mappedPeriodSettings.startDate);
        setPeriodEndDate(mappedPeriodSettings.endDate);
        setBarberPeriodDays(mappedPeriodSettings.days);

        const effectiveSalonId = salonIdOverride || hairSalonId;

        if (effectiveSalonId) {
            const salonHours = await adminApi.getHairSalonHours(effectiveSalonId);
            setHairSalonHours(mapHoursResponse(salonHours));
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
        refreshAll();
    }, [token]);

    useEffect(() => {
        if (token && selectedBarberId) {
            loadBarberDay(selectedDate);
        }
    }, [token, selectedBarberId, selectedDate]);

    useEffect(() => {
        if (!tokenStorage.get()) {
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
    }, [token, bookingSearchQuery]);

    useEffect(() => {
        if (!tokenStorage.get()) {
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
    }, [token, confirmedReviewSearchQuery]);

    useEffect(() => {
        let ignore = false;

        async function loadManualBookingAvailability() {
            if (!token || !manualBookingBarberId || !manualBookingTreatmentId || !manualBookingDate) {
                setManualBookingSlots([]);
                setManualBookingSelectedSlot(null);
                return;
            }

            setManualBookingSlotsLoading(true);
            clearSectionError('adminBooking');

            try {
                const slots = await publicApi.getAvailability({
                    barberId: manualBookingBarberId,
                    treatmentId: manualBookingTreatmentId,
                    date: manualBookingDate
                });

                if (ignore) {
                    return;
                }

                const nextSlots = Array.isArray(slots) ? slots : [];
                setManualBookingSlots(nextSlots);
                setManualBookingSelectedSlot((current) =>
                    current
                        ? nextSlots.find(
                            (slot) =>
                                slot.startTime === current.startTime &&
                                slot.endTime === current.endTime &&
                                slot.available
                        ) || null
                        : null
                );
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
    }, [token, manualBookingBarberId, manualBookingTreatmentId, manualBookingDate]);

    async function handleLogin(event) {
        event.preventDefault();
        setLoading(true);
        clearSectionError('login');
        clearFieldErrors('login');

        try {
            const response = await authApi.login(loginForm);
            tokenStorage.set(response.accessToken);
            setToken(response.accessToken);
            showSectionSuccess('login', `Authenticated. Token expires in ${response.expiresInSeconds}s`);
        } catch (err) {
            setSectionFieldErrors('login', extractFieldErrors(err));
            setSectionError('login', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function handleLogout() {
        tokenStorage.clear();
        setFieldErrors({});
        setSectionSuccess({});
        setSectionErrors({});
        setToken(null);
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
            setHairSalonHours(mapHoursResponse(updatedHours));
            showSectionSuccess('hairSalonHours', `Hair salon hours updated for ${dayConfig.dayOfWeek}`);
        } catch (err) {
            setSectionError('hairSalonHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function createBarber() {
        setLoading(true);
        clearSectionError('barbers');
        clearFieldErrors('barbers');

        try {
            await adminApi.createBarber({
                name: barberForm.name,
                role: barberForm.role,
                bio: barberForm.bio || null,
                photoUrl: barberForm.photoUrl || null,
                displayOrder: barberForm.displayOrder !== '' ? Number(barberForm.displayOrder) : null
            });
            await loadPublicData();
            showSectionSuccess('barbers', 'Barber created');
        } catch (err) {
            setSectionFieldErrors('barbers', extractFieldErrors(err));
            setSectionError('barbers', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateBarber() {
        if (!barberForm.id) {
            setSectionError('barbers', 'Select or enter barber ID for update');
            return;
        }

        setLoading(true);
        clearSectionError('barbers');
        clearFieldErrors('barbers');

        try {
            await adminApi.updateBarber(barberForm.id, {
                name: barberForm.name,
                role: barberForm.role,
                bio: barberForm.bio || null,
                photoUrl: barberForm.photoUrl || null,
                displayOrder: barberForm.displayOrder !== '' ? Number(barberForm.displayOrder) : null
            });
            await loadPublicData();
            showSectionSuccess('barbers', 'Barber updated');
        } catch (err) {
            setSectionFieldErrors('barbers', extractFieldErrors(err));
            setSectionError('barbers', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function deleteBarber(barberId) {
        setLoading(true);
        clearSectionError('barbers');

        try {
            await adminApi.deleteBarber(barberId);
            await loadPublicData();
            showSectionSuccess('barbers', 'Barber deleted');
        } catch (err) {
            setSectionError('barbers', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function reorderBarbers() {
        if (!reorderForm.id1 || !reorderForm.id2) {
            setSectionError('reorder', 'Select both barber IDs for reorder');
            return;
        }

        setLoading(true);
        clearSectionError('reorder');
        setSectionSuccess((prev) => ({ ...prev, reorder: '' }));

        try {
            await adminApi.reorderBarbers(reorderForm);
            await loadPublicData();
            showSectionSuccess('reorder', 'Barbers reordered');
        } catch (err) {
            setSectionError('reorder', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function loadBarberDay(dateOverride) {
        if (!selectedBarberId) {
            setSectionError('barberHours', 'Select barber first');
            return;
        }

        const workingDate = dateOverride || selectedDate;
        setLoading(true);
        clearSectionError('barberHours');

        try {
            const days = await adminApi.getBarberSchedule(selectedBarberId, workingDate, workingDate);
            const day = days?.[0] || { workingDate };
            setBarberDay(mapScheduleDay(day, workingDate));
            showSectionSuccess('barberHours', 'Barber schedule loaded');
        } catch (err) {
            setSectionError('barberHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function updateBarberDay() {
        if (!selectedBarberId) {
            setSectionError('barberHours', 'Select barber first');
            return;
        }

        setLoading(true);
        clearSectionError('barberHours');

        try {
            await adminApi.upsertBarberDay(selectedBarberId, {
                workingDate: barberDay.workingDate,
                workingDay: barberDay.workingDay,
                openTime: barberDay.workingDay ? barberDay.openTime : null,
                closeTime: barberDay.workingDay ? barberDay.closeTime : null,
                breakStartTime: barberDay.workingDay ? barberDay.breakStartTime : null,
                breakEndTime: barberDay.workingDay ? barberDay.breakEndTime : null
            });
            await loadBarberDay(barberDay.workingDate);
            showSectionSuccess('barberHours', `Barber schedule saved for ${barberDay.workingDate}`);
        } catch (err) {
            setSectionError('barberHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    function updateBarberPeriodDay(dayOfWeek, patch) {
        setBarberPeriodDays((prev) =>
            prev.map((item) =>
                item.dayOfWeek === dayOfWeek
                    ? {
                        ...item,
                        ...DEFAULT_SCHEDULE_TIMES,
                        ...item,
                        ...patch
                    }
                    : item
            )
        );
    }

    async function updateBarberPeriod() {
        if (sortedBarbers.length === 0) {
            setSectionError('barberPeriodHours', 'No barbers available');
            return;
        }

        setLoading(true);
        clearSectionError('barberPeriodHours');

        const applyToAllBarbers = periodTargetBarberId === ALL_BARBERS_OPTION;
        const selectedBarberName = applyToAllBarbers
            ? 'all barbers'
            : sortedBarbers.find((item) => item.id === periodTargetBarberId)?.name || 'selected barber';
        const periodDateError = getBarberPeriodDateError(periodStartDate, periodEndDate);

        if (periodDateError) {
            setLoading(false);
            setSectionError('barberPeriodHours', periodDateError);
            return;
        }

        try {
            const response = await adminApi.upsertBarberPeriod({
                startDate: periodStartDate,
                endDate: periodEndDate,
                barberId: applyToAllBarbers ? null : periodTargetBarberId,
                applyToAllBarbers,
                days: barberPeriodDays.map((item) => ({
                    dayOfWeek: item.dayOfWeek,
                    workingDay: item.workingDay,
                    openTime: item.workingDay ? item.openTime : null,
                    closeTime: item.workingDay ? item.closeTime : null,
                    breakStartTime: item.workingDay ? item.breakStartTime : null,
                    breakEndTime: item.workingDay ? item.breakEndTime : null
                }))
            });

            const mappedPeriodSettings = mapPeriodSettings(response, todayIso());
            setPeriodTargetBarberId(mappedPeriodSettings.targetBarberId);
            setPeriodStartDate(mappedPeriodSettings.startDate);
            setPeriodEndDate(mappedPeriodSettings.endDate);
            setBarberPeriodDays(mappedPeriodSettings.days);

            if (
                selectedBarberId &&
                isDateWithinRange(selectedDate, periodStartDate, periodEndDate) &&
                (applyToAllBarbers || selectedBarberId === periodTargetBarberId)
            ) {
                await loadBarberDay(selectedDate);
            }

            showSectionSuccess(
                'barberPeriodHours',
                `Barber period schedule saved for ${selectedBarberName} (${periodStartDate} to ${periodEndDate})`
            );
        } catch (err) {
            setSectionError('barberPeriodHours', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
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
                displayOrder: treatmentForm.displayOrder !== '' ? Number(treatmentForm.displayOrder) : null,
                photoUrl: treatmentForm.photoUrl || null,
                description: treatmentForm.description
            });
            await loadPublicData();
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
            setSectionError('treatments', 'Select or enter treatment ID for update');
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
                displayOrder: treatmentForm.displayOrder !== '' ? Number(treatmentForm.displayOrder) : null,
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
            showSectionSuccess('treatments', 'Treatment deleted');
        } catch (err) {
            setSectionError('treatments', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function reorderTreatments() {
        if (!treatmentReorderForm.id1 || !treatmentReorderForm.id2) {
            setSectionError('treatmentReorder', 'Select both treatment IDs for reorder');
            return;
        }

        setLoading(true);
        clearSectionError('treatmentReorder');
        setSectionSuccess((prev) => ({ ...prev, treatmentReorder: '' }));
        try {
            await adminApi.reorderTreatments(treatmentReorderForm);
            await loadPublicData();
            showSectionSuccess('treatmentReorder', 'Treatments reordered');
            setTreatmentReorderForm({ id1: '', id2: '' });
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

    async function cancelBookingReview(bookingId) {
        setLoading(true);
        clearSectionError('confirmedBookingReview');

        try {
            await adminApi.cancelBooking(bookingId);
            await Promise.all([
                fetchAdminBookings(bookingSearchQuery),
                fetchConfirmedReviewBookings(confirmedReviewSearchQuery)
            ]);
            showSectionSuccess('confirmedBookingReview', 'Booking cancelled');
        } catch (err) {
            setSectionError('confirmedBookingReview', getApiErrorMessage(err));
        } finally {
            setLoading(false);
        }
    }

    async function createAdminBooking() {
        if (!manualBookingBarberId || !manualBookingTreatmentId || !manualBookingSelectedSlot) {
            setSectionError('adminBooking', 'Select service, barber, date, and a free time slot first.');
            return;
        }

        setLoading(true);
        clearSectionError('adminBooking');
        clearFieldErrors('adminBooking');

        try {
            const response = await adminApi.createAdminBooking({
                barberId: manualBookingBarberId,
                treatmentId: manualBookingTreatmentId,
                bookingDate: manualBookingDate,
                startTime: manualBookingSelectedSlot.startTime,
                endTime: manualBookingSelectedSlot.endTime,
                customerName: manualBookingForm.customerName.trim(),
                customerPhone: manualBookingForm.customerPhone.trim()
            });

            const refreshedSlots = await publicApi.getAvailability({
                barberId: manualBookingBarberId,
                treatmentId: manualBookingTreatmentId,
                date: manualBookingDate
            });

            setManualBookingSlots(Array.isArray(refreshedSlots) ? refreshedSlots : []);
            setManualBookingSelectedSlot(null);
            setManualBookingForm({
                customerName: '',
                customerPhone: ''
            });
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

    function fillBarberForm(item) {
        setBarberForm({
            id: item.id || '',
            name: item.name || '',
            role: item.role || '',
            bio: item.bio || '',
            photoUrl: item.photoUrl || '',
            displayOrder: item.displayOrder != null ? String(item.displayOrder) : ''
        });
    }

    function fillTreatmentForm(item) {
        setTreatmentForm({
            id: item.id || '',
            name: item.name || '',
            durationMinutes: item.durationMinutes != null ? String(item.durationMinutes) : '',
            price: item.price != null ? String(item.price) : '',
            displayOrder: item.displayOrder != null ? String(item.displayOrder) : '',
            photoUrl: item.photoUrl || '',
            description: item.description || ''
        });
    }

    if (!token) {
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
                            sortedBarbers={sortedBarbers}
                            sortedTreatments={sortedTreatments}
                            manualBookingBarberId={manualBookingBarberId}
                            setManualBookingBarberId={(value) => {
                                setManualBookingBarberId(value);
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
                            onManualBookingSlotSelect={(slot) => {
                                setManualBookingSelectedSlot(slot);
                                clearSectionError('adminBooking');
                            }}
                            manualBookingSlotsLoading={manualBookingSlotsLoading}
                            manualBookingForm={manualBookingForm}
                            setManualBookingForm={setManualBookingForm}
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
                    path="/barbers"
                    element={
                        <BarbersPage
                            barberForm={barberForm}
                            setBarberForm={setBarberForm}
                            reorderForm={reorderForm}
                            setReorderForm={setReorderForm}
                            sortedBarbers={sortedBarbers}
                            selectedBarberId={selectedBarberId}
                            setSelectedBarberId={setSelectedBarberId}
                            selectedDate={selectedDate}
                            setSelectedDate={setSelectedDate}
                            barberDay={barberDay}
                            setBarberDay={setBarberDay}
                            periodTargetBarberId={periodTargetBarberId}
                            setPeriodTargetBarberId={setPeriodTargetBarberId}
                            periodStartDate={periodStartDate}
                            setPeriodStartDate={setPeriodStartDate}
                            periodEndDate={periodEndDate}
                            setPeriodEndDate={setPeriodEndDate}
                            barberPeriodDays={barberPeriodDays}
                            updateBarberPeriodDay={updateBarberPeriodDay}
                            loading={loading}
                            clearFieldError={clearFieldError}
                            fieldErrors={fieldErrors}
                            sectionErrors={sectionErrors}
                            sectionSuccess={sectionSuccess}
                            createBarber={createBarber}
                            updateBarber={updateBarber}
                            fillBarberForm={fillBarberForm}
                            deleteBarber={deleteBarber}
                            reorderBarbers={reorderBarbers}
                            updateBarberPeriod={updateBarberPeriod}
                            updateBarberDay={updateBarberDay}
                            formatDayLabel={formatDayLabel}
                            allBarbersOption={ALL_BARBERS_OPTION}
                        />
                    }
                />
                <Route
                    path="/treatments"
                    element={
                        <TreatmentsPage
                            treatmentForm={treatmentForm}
                            setTreatmentForm={setTreatmentForm}
                            treatmentReorderForm={treatmentReorderForm}
                            setTreatmentReorderForm={setTreatmentReorderForm}
                            sortedTreatments={sortedTreatments}
                            loading={loading}
                            clearFieldError={clearFieldError}
                            fieldErrors={fieldErrors}
                            sectionErrors={sectionErrors}
                            sectionSuccess={sectionSuccess}
                            createTreatment={createTreatment}
                            updateTreatment={updateTreatment}
                            fillTreatmentForm={fillTreatmentForm}
                            deleteTreatment={deleteTreatment}
                            reorderTreatments={reorderTreatments}
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
                        />
                    }
                />
                <Route path="*" element={<Navigate to="/bookings" replace />} />
            </Routes>
        </AdminLayout>
    );
}

export default App;
