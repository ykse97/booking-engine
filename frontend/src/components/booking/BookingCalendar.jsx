import { useEffect, useState } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import LuxuryCard from '../ui/LuxuryCard';

const weekday = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function buildMonth(year, month) {
    const firstDay = new Date(year, month, 1).getDay();
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const days = [];
    for (let i = 0; i < firstDay; i++) {
        days.push(null);
    }
    for (let d = 1; d <= daysInMonth; d++) {
        days.push(d);
    }
    return days;
}

export default function BookingCalendar({ selectedDate, onSelect }) {
    const [viewDate, setViewDate] = useState(
        () => new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1)
    );

    useEffect(() => {
        setViewDate(new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1));
    }, [selectedDate]);

    const [year, month] = [viewDate.getFullYear(), viewDate.getMonth()];
    const monthDays = buildMonth(year, month);
    const title = viewDate.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });

    const changeMonth = (delta) => {
        setViewDate(new Date(year, month + delta, 1));
    };

    return (
        <LuxuryCard className="p-4">
            <div className="flex items-center justify-between mb-3 text-smoke">
                <button onClick={() => changeMonth(-1)} aria-label="Previous month">
                    <ChevronLeft size={18} />
                </button>
                <div className="font-heading tracking-[0.16em] text-ivory text-sm">{title}</div>
                <button onClick={() => changeMonth(1)} aria-label="Next month">
                    <ChevronRight size={18} />
                </button>
            </div>

            <div className="grid grid-cols-7 text-[11px] text-smoke tracking-[0.14em] mb-3 text-center">
                {weekday.map((day) => (
                    <div key={day}>{day}</div>
                ))}
            </div>

            <div className="calendar-grid">
                {monthDays.map((day, idx) => {
                    if (!day) return <div key={idx} />;
                    const cellDate = new Date(year, month, day);
                    const isActive =
                        day === selectedDate.getDate() &&
                        month === selectedDate.getMonth() &&
                        year === selectedDate.getFullYear();
                    const isPast = cellDate < new Date(new Date().setHours(0, 0, 0, 0));
                    return (
                        <button
                            key={idx}
                            className={`calendar-day ${isActive ? 'active' : ''} ${isPast ? 'muted' : ''}`}
                            disabled={isPast}
                            onClick={() => onSelect(cellDate)}
                        >
                            {day}
                        </button>
                    );
                })}
            </div>
        </LuxuryCard>
    );
}
