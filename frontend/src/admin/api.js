import axios from 'axios';

const TOKEN_KEY = 'admin_access_token';
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export const api = axios.create({
    baseURL: API_BASE_URL,
    headers: {
        'Content-Type': 'application/json'
    }
});

api.interceptors.request.use((config) => {
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

export const authApi = {
    login: async (payload) => (await api.post('/api/v1/public/auth/login', payload)).data
};

export const publicApi = {
    getHairSalon: async () => (await api.get('/api/v1/public/hair-salon')).data,
    getBarbers: async () => (await api.get('/api/v1/public/barbers')).data,
    getTreatments: async () => (await api.get('/api/v1/public/treatments')).data,
    getAvailability: async ({ barberId, date, treatmentId }) =>
        (
            await api.get('/api/v1/public/availability', {
                params: { barberId, date, treatmentId }
            })
        ).data
};

export const adminApi = {
    updateHairSalon: async (payload) => api.put('/api/v1/admin/hair-salon', payload),

    getHairSalonHours: async (hairSalonId) =>
        (await api.get(`/api/v1/admin/hair-salons/${hairSalonId}/hours`)).data,
    updateHairSalonHours: async (hairSalonId, dayOfWeek, payload) =>
        api.put(`/api/v1/admin/hair-salons/${hairSalonId}/hours/${dayOfWeek}`, payload),

    createBarber: async (payload) => (await api.post('/api/v1/admin/barbers', payload)).data,
    updateBarber: async (id, payload) => api.put(`/api/v1/admin/barbers/${id}`, payload),
    deleteBarber: async (id) => api.delete(`/api/v1/admin/barbers/${id}`),
    reorderBarbers: async (payload) => api.post('/api/v1/admin/barbers/reorder', payload),

    getBarberSchedule: async (barberId, from, to) =>
        (
            await api.get(`/api/v1/admin/barbers/${barberId}/schedule`, {
                params: { from, to }
            })
        ).data,
    getBarberPeriodSettings: async () =>
        (await api.get('/api/v1/admin/barbers/schedule/period')).data,
    upsertBarberPeriod: async (payload) =>
        (await api.put('/api/v1/admin/barbers/schedule/period', payload)).data,
    upsertBarberDay: async (barberId, payload) =>
        api.put(`/api/v1/admin/barbers/${barberId}/schedule`, payload),

    createTreatment: async (payload) => (await api.post('/api/v1/admin/treatments', payload)).data,
    updateTreatment: async (id, payload) => api.put(`/api/v1/admin/treatments/${id}`, payload),
    deleteTreatment: async (id) => api.delete(`/api/v1/admin/treatments/${id}`),
    reorderTreatments: async (payload) => api.post('/api/v1/admin/treatments/reorder', payload),

    createAdminBooking: async (payload) => (await api.post('/api/v1/admin/bookings', payload)).data,
    getAdminBookings: async (search = '') =>
        (
            await api.get('/api/v1/admin/bookings', {
                params: { search }
            })
        ).data,
    cancelBooking: async (bookingId) =>
        (await api.post(`/api/v1/admin/bookings/${bookingId}/cancel`)).data,
    getBookingBlacklist: async () =>
        (await api.get('/api/v1/admin/bookings/blacklist')).data,
    createBookingBlacklistEntry: async (payload) =>
        (await api.post('/api/v1/admin/bookings/blacklist', payload)).data,
    deleteBookingBlacklistEntry: async (id) =>
        api.delete(`/api/v1/admin/bookings/blacklist/${id}`)
};

export const tokenStorage = {
    set: (token) => localStorage.setItem(TOKEN_KEY, token),
    get: () => localStorage.getItem(TOKEN_KEY),
    clear: () => localStorage.removeItem(TOKEN_KEY)
};

export function getApiErrorMessage(error) {
    if (!error) {
        return 'Unknown error';
    }
    if (error.response?.data?.message) {
        return error.response.data.message;
    }
    if (typeof error.response?.data === 'string') {
        return error.response.data;
    }
    return error.message || 'Request failed';
}
