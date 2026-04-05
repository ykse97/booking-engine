import SectionTitle from '../../components/ui/SectionTitle';

function scrollToAdminSection(sectionId) {
    if (typeof document === 'undefined') {
        return;
    }

    document.getElementById(sectionId)?.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
    });
}

export default function BarbersPage({
    barberForm,
    setBarberForm,
    reorderForm,
    setReorderForm,
    sortedBarbers,
    selectedBarberId,
    setSelectedBarberId,
    selectedDate,
    setSelectedDate,
    barberDay,
    setBarberDay,
    periodTargetBarberId,
    setPeriodTargetBarberId,
    periodStartDate,
    setPeriodStartDate,
    periodEndDate,
    setPeriodEndDate,
    barberPeriodDays,
    updateBarberPeriodDay,
    loading,
    clearFieldError,
    fieldErrors,
    sectionErrors,
    sectionSuccess,
    createBarber,
    updateBarber,
    fillBarberForm,
    deleteBarber,
    reorderBarbers,
    updateBarberPeriod,
    updateBarberDay,
    formatDayLabel,
    allBarbersOption
}) {
    const barberSections = [
        { id: 'admin-barbers-list', label: 'Barber List' },
        { id: 'admin-barbers-reorder', label: 'Reorder' },
        { id: 'admin-barbers-period', label: 'Per Period' },
        { id: 'admin-barbers-date', label: 'Per Date' }
    ];

    return (
        <div className="admin-page-stack">
            <section className="panel">
                <SectionTitle title="Barbers" subtitle="Admin Barbers Workspace" />
                <p className="panel-note">
                    Manage the team profile, edit existing barbers, and control schedule logic from one dedicated workspace.
                </p>

                <div className="admin-subsection-nav">
                    {barberSections.map((section) => (
                        <button
                            key={section.id}
                            type="button"
                            className="btn-gold admin-subsection-link"
                            onClick={() => scrollToAdminSection(section.id)}
                        >
                            {section.label}
                        </button>
                    ))}
                </div>
            </section>

            <section id="admin-barbers-workspace" className="panel admin-workspace-section">
                <SectionTitle title="Team Management" subtitle="Barbers" />

                <form className="grid two" onSubmit={(event) => event.preventDefault()}>
                    <label>
                        ID (for update)
                        <input
                            className="payment-input"
                            value={barberForm.id}
                            onChange={(event) => setBarberForm((current) => ({ ...current, id: event.target.value }))}
                        />
                    </label>

                    <label>
                        Name
                        <input
                            className="payment-input"
                            value={barberForm.name}
                            onChange={(event) => {
                                setBarberForm((current) => ({ ...current, name: event.target.value }));
                                clearFieldError('barbers', 'name');
                            }}
                        />
                        {fieldErrors.barbers?.name ? <span className="field-error">{fieldErrors.barbers.name}</span> : null}
                    </label>

                    <label>
                        Role
                        <input
                            className="payment-input"
                            value={barberForm.role}
                            onChange={(event) => {
                                setBarberForm((current) => ({ ...current, role: event.target.value }));
                                clearFieldError('barbers', 'role');
                            }}
                        />
                        {fieldErrors.barbers?.role ? <span className="field-error">{fieldErrors.barbers.role}</span> : null}
                    </label>

                    <label className="full">
                        Bio
                        <textarea
                            className="payment-input admin-textarea"
                            rows={3}
                            value={barberForm.bio}
                            onChange={(event) => setBarberForm((current) => ({ ...current, bio: event.target.value }))}
                        />
                    </label>

                    <label>
                        Photo URL
                        <input
                            className="payment-input"
                            value={barberForm.photoUrl}
                            onChange={(event) => setBarberForm((current) => ({ ...current, photoUrl: event.target.value }))}
                        />
                    </label>

                    <label>
                        Display Order
                        <input
                            className="payment-input"
                            type="number"
                            value={barberForm.displayOrder}
                            onChange={(event) => setBarberForm((current) => ({ ...current, displayOrder: event.target.value }))}
                        />
                    </label>
                </form>

                <div className="row admin-form-actions">
                    <button type="button" className="btn-gold" onClick={createBarber} disabled={loading}>
                        Create
                    </button>
                    <button type="button" className="btn-gold" onClick={updateBarber} disabled={loading}>
                        Update
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={() =>
                            setBarberForm({
                                id: '',
                                name: '',
                                role: '',
                                bio: '',
                                photoUrl: '',
                                displayOrder: ''
                            })
                        }
                        disabled={loading}
                    >
                        Clear
                    </button>
                </div>

                {sectionErrors.barbers ? <p className="section-error">{sectionErrors.barbers}</p> : null}
                {sectionSuccess.barbers ? <p className="section-ok">{sectionSuccess.barbers}</p> : null}
            </section>

            <section id="admin-barbers-list" className="panel admin-workspace-section">
                <SectionTitle title="Barber List" subtitle="Edit Existing Team" />

                <div className="table-wrapper admin-table-shell">
                    <table className="admin-data-table admin-data-table-team">
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Role</th>
                                <th>Order</th>
                                <th>Photo URL</th>
                                <th>ID</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedBarbers.map((item) => (
                                <tr key={item.id}>
                                    <td>{item.name}</td>
                                    <td>{item.role}</td>
                                    <td>{item.displayOrder}</td>
                                    <td>{item.photoUrl || '-'}</td>
                                    <td className="code">{item.id}</td>
                                    <td className="row admin-table-actions">
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => {
                                                fillBarberForm(item);
                                                scrollToAdminSection('admin-barbers-workspace');
                                            }}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => deleteBarber(item.id)}
                                            disabled={loading}
                                        >
                                            Delete
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </section>

            <section id="admin-barbers-reorder" className="panel admin-workspace-section">
                <SectionTitle title="Reorder Barbers" subtitle="Display Sequence" />

                <div className="row admin-control-row">
                    <select value={reorderForm.id1} onChange={(event) => setReorderForm((current) => ({ ...current, id1: event.target.value }))}>
                        <option value="">Select first barber</option>
                        {sortedBarbers.map((item) => (
                            <option key={item.id} value={item.id}>
                                {item.name} ({item.displayOrder})
                            </option>
                        ))}
                    </select>

                    <select value={reorderForm.id2} onChange={(event) => setReorderForm((current) => ({ ...current, id2: event.target.value }))}>
                        <option value="">Select second barber</option>
                        {sortedBarbers.map((item) => (
                            <option key={item.id} value={item.id}>
                                {item.name} ({item.displayOrder})
                            </option>
                        ))}
                    </select>

                    <button type="button" className="btn-gold" onClick={reorderBarbers} disabled={loading}>
                        Reorder
                    </button>
                </div>

                {sectionErrors.reorder ? <p className="section-error">{sectionErrors.reorder}</p> : null}
                {sectionSuccess.reorder ? <p className="section-ok">{sectionSuccess.reorder}</p> : null}
            </section>

            <section id="admin-barbers-period" className="panel admin-workspace-section">
                <SectionTitle title="Barber Schedule (per period)" subtitle="Bulk Schedule Updates" />

                <div className="row admin-control-row admin-control-row-schedule">
                    <select value={periodTargetBarberId} onChange={(event) => setPeriodTargetBarberId(event.target.value)}>
                        <option value={allBarbersOption}>All barbers</option>
                        {sortedBarbers.map((item) => (
                            <option key={item.id} value={item.id}>
                                {item.name}
                            </option>
                        ))}
                    </select>

                    <input
                        type="date"
                        value={periodStartDate}
                        max={periodEndDate || undefined}
                        onChange={(event) => setPeriodStartDate(event.target.value)}
                    />
                    <input
                        type="date"
                        value={periodEndDate}
                        min={periodStartDate || undefined}
                        onChange={(event) => setPeriodEndDate(event.target.value)}
                    />
                </div>

                <div className="table-wrapper admin-table-shell">
                    <table className="admin-data-table admin-data-table-schedule">
                        <thead>
                            <tr>
                                <th>Day</th>
                                <th>Working</th>
                                <th>Open</th>
                                <th>Close</th>
                                <th>Break Start</th>
                                <th>Break End</th>
                            </tr>
                        </thead>
                        <tbody>
                            {barberPeriodDays.map((row) => (
                                <tr key={row.dayOfWeek}>
                                    <td>{formatDayLabel(row.dayOfWeek)}</td>
                                    <td>
                                        <input
                                            type="checkbox"
                                            checked={row.workingDay}
                                            onChange={(event) => updateBarberPeriodDay(row.dayOfWeek, { workingDay: event.target.checked })}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.openTime}
                                            onChange={(event) => updateBarberPeriodDay(row.dayOfWeek, { openTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.closeTime}
                                            onChange={(event) => updateBarberPeriodDay(row.dayOfWeek, { closeTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.breakStartTime}
                                            onChange={(event) => updateBarberPeriodDay(row.dayOfWeek, { breakStartTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                    <td>
                                        <input
                                            type="time"
                                            value={row.breakEndTime}
                                            onChange={(event) => updateBarberPeriodDay(row.dayOfWeek, { breakEndTime: event.target.value })}
                                            disabled={!row.workingDay}
                                        />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                <div className="row">
                    <button type="button" className="btn-gold" onClick={updateBarberPeriod} disabled={loading || sortedBarbers.length === 0}>
                        Update Period
                    </button>
                </div>

                {sectionErrors.barberPeriodHours ? <p className="section-error">{sectionErrors.barberPeriodHours}</p> : null}
                {sectionSuccess.barberPeriodHours ? <p className="section-ok">{sectionSuccess.barberPeriodHours}</p> : null}
            </section>

            <section id="admin-barbers-date" className="panel admin-workspace-section">
                <SectionTitle title="Barber Schedule (per date)" subtitle="Single-Day Override" />

                <div className="row admin-control-row admin-control-row-schedule">
                    <select value={selectedBarberId} onChange={(event) => setSelectedBarberId(event.target.value)}>
                        <option value="">Select barber</option>
                        {sortedBarbers.map((item) => (
                            <option key={item.id} value={item.id}>
                                {item.name}
                            </option>
                        ))}
                    </select>

                    <input type="date" value={selectedDate} onChange={(event) => setSelectedDate(event.target.value)} />
                </div>

                <div className="grid two barber-day-grid admin-form-grid-shell">
                    <label className="barber-toggle-field">
                        Working Day
                        <input
                            type="checkbox"
                            checked={barberDay.workingDay}
                            onChange={(event) => setBarberDay((current) => ({ ...current, workingDay: event.target.checked }))}
                        />
                    </label>

                    <div className="barber-day-grid-spacer" aria-hidden="true" />

                    <label>
                        Open Time
                        <input
                            type="time"
                            value={barberDay.openTime}
                            onChange={(event) => setBarberDay((current) => ({ ...current, openTime: event.target.value }))}
                            disabled={!barberDay.workingDay}
                        />
                    </label>

                    <label>
                        Close Time
                        <input
                            type="time"
                            value={barberDay.closeTime}
                            onChange={(event) => setBarberDay((current) => ({ ...current, closeTime: event.target.value }))}
                            disabled={!barberDay.workingDay}
                        />
                    </label>

                    <label>
                        Break Start
                        <input
                            type="time"
                            value={barberDay.breakStartTime}
                            onChange={(event) => setBarberDay((current) => ({ ...current, breakStartTime: event.target.value }))}
                            disabled={!barberDay.workingDay}
                        />
                    </label>

                    <label>
                        Break End
                        <input
                            type="time"
                            value={barberDay.breakEndTime}
                            onChange={(event) => setBarberDay((current) => ({ ...current, breakEndTime: event.target.value }))}
                            disabled={!barberDay.workingDay}
                        />
                    </label>
                </div>

                <div className="row">
                    <button type="button" className="btn-gold" onClick={updateBarberDay} disabled={loading || !selectedBarberId}>
                        Update Day
                    </button>
                </div>

                {sectionErrors.barberHours ? <p className="section-error">{sectionErrors.barberHours}</p> : null}
                {sectionSuccess.barberHours ? <p className="section-ok">{sectionSuccess.barberHours}</p> : null}
            </section>
        </div>
    );
}
