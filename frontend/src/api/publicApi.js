import { extractFriendlyErrorMessage } from '../utils/appErrorBus';

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');
const BOOKING_DEVICE_ID_STORAGE_KEY = 'bookingDeviceId';

function buildRequestUrl(path, params) {
    const url = new URL(`${API_BASE_URL}${path}`, window.location.origin);

    if (params) {
        Object.entries(params).forEach(([key, value]) => {
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
    }

    return url;
}

export class PublicApiError extends Error {
    constructor(message, status, payload) {
        super(message);
        this.name = 'PublicApiError';
        this.status = status;
        this.payload = payload;
        this.fieldErrors = payload?.fieldErrors || {};
    }
}

function generateDeviceId() {
    if (typeof window !== 'undefined' && window.crypto?.randomUUID) {
        return window.crypto.randomUUID();
    }

    return `booking-device-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function getBookingDeviceId() {
    if (typeof window === 'undefined') {
        return null;
    }

    try {
        const existingDeviceId = window.localStorage.getItem(BOOKING_DEVICE_ID_STORAGE_KEY);
        if (existingDeviceId) {
            return existingDeviceId;
        }

        const nextDeviceId = generateDeviceId();
        window.localStorage.setItem(BOOKING_DEVICE_ID_STORAGE_KEY, nextDeviceId);
        return nextDeviceId;
    } catch {
        return null;
    }
}

async function request(path, options = {}) {
    const { params, headers, body, ...rest } = options;
    const url = buildRequestUrl(path, params);

    let response;

    try {
        response = await fetch(url.toString(), {
            ...rest,
            headers: {
                Accept: 'application/json',
                ...(body ? { 'Content-Type': 'application/json' } : {}),
                ...headers
            },
            body
        });
    } catch (error) {
        throw new PublicApiError(
            extractFriendlyErrorMessage(
                error,
                'We could not reach the booking service. Please check your connection and try again.'
            ),
            0,
            null
        );
    }

    const isJson = response.headers.get('content-type')?.includes('application/json');
    const payload = isJson
        ? await response.json().catch(() => null)
        : await response.text().catch(() => '');

    if (!response.ok) {
        const message = extractFriendlyErrorMessage(
            {
                message: payload?.message || (typeof payload === 'string' ? payload : ''),
                payload,
                response: {
                    data: payload,
                    status: response.status
                },
                status: response.status
            },
            `Request failed with status ${response.status}`
        );

        throw new PublicApiError(message, response.status, payload);
    }

    return payload;
}

export const publicApi = {
    getHairSalon: () => request('/api/v1/public/hair-salon'),
    getEmployees: (params) => request('/api/v1/public/employees', { params }),
    getTreatments: () => request('/api/v1/public/treatments'),
    getBooking: (bookingId) => request(`/api/v1/public/bookings/${bookingId}`),
    getBookingCheckoutConfig: () => request('/api/v1/public/booking-checkout-config'),
    getAvailability: ({ employeeId, date, treatmentId }) =>
        request('/api/v1/public/availability', {
            params: { employeeId, date, treatmentId }
        }),
    holdBookingSlot: (payload) => {
        const bookingDeviceId = getBookingDeviceId();

        return request('/api/v1/public/bookings/hold', {
            method: 'POST',
            headers: bookingDeviceId
                ? { 'X-Booking-Device-Id': bookingDeviceId }
                : undefined,
            body: JSON.stringify(payload)
        });
    },
    validateHeldBookingCheckout: (bookingId, payload) =>
        request(`/api/v1/public/bookings/${bookingId}/checkout/validate`, {
            method: 'POST',
            body: JSON.stringify(payload)
        }),
    prepareHeldBookingCheckout: (bookingId, payload) =>
        request(`/api/v1/public/bookings/${bookingId}/checkout`, {
            method: 'POST',
            body: JSON.stringify(payload)
        }),
    confirmHeldBooking: (bookingId, payload) =>
        request(`/api/v1/public/bookings/${bookingId}/confirm`, {
            method: 'POST',
            body: JSON.stringify(payload)
        }),
    cancelBooking: (bookingId) =>
        request(`/api/v1/public/bookings/${bookingId}`, {
            method: 'DELETE'
        })
};
