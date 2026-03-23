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

export default function TreatmentsPage({
    treatmentForm,
    setTreatmentForm,
    treatmentReorderForm,
    setTreatmentReorderForm,
    sortedTreatments,
    loading,
    clearFieldError,
    fieldErrors,
    sectionErrors,
    sectionSuccess,
    createTreatment,
    updateTreatment,
    fillTreatmentForm,
    deleteTreatment,
    reorderTreatments
}) {
    const treatmentSections = [
        { id: 'admin-treatments-list', label: 'Treatments List' },
        { id: 'admin-treatments-reorder', label: 'Reorder' }
    ];

    return (
        <div className="admin-page-stack">
            <section className="panel">
                <SectionTitle title="Treatments" subtitle="Admin Treatments Workspace" />
                <p className="panel-note">
                    Control the service catalog, jump between editing sections quickly, and keep every treatment in one consistent admin workspace.
                </p>

                <div className="admin-subsection-nav">
                    {treatmentSections.map((section) => (
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

            <section id="admin-treatments-workspace" className="panel admin-workspace-section">
                <SectionTitle title="Service Catalog" subtitle="Treatments" />

                <form className="grid two" onSubmit={(event) => event.preventDefault()}>
                    <label>
                        ID (for update)
                        <input
                            className="payment-input"
                            value={treatmentForm.id}
                            onChange={(event) => setTreatmentForm((current) => ({ ...current, id: event.target.value }))}
                        />
                    </label>

                    <label>
                        Name
                        <input
                            className="payment-input"
                            value={treatmentForm.name}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, name: event.target.value }));
                                clearFieldError('treatments', 'name');
                            }}
                        />
                        {fieldErrors.treatments?.name ? <span className="field-error">{fieldErrors.treatments.name}</span> : null}
                    </label>

                    <label>
                        Duration (minutes)
                        <input
                            className="payment-input"
                            type="number"
                            value={treatmentForm.durationMinutes}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, durationMinutes: event.target.value }));
                                clearFieldError('treatments', 'durationMinutes');
                            }}
                        />
                        {fieldErrors.treatments?.durationMinutes ? (
                            <span className="field-error">{fieldErrors.treatments.durationMinutes}</span>
                        ) : null}
                    </label>

                    <label>
                        Price (EUR)
                        <input
                            className="payment-input"
                            type="number"
                            step="0.01"
                            value={treatmentForm.price}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, price: event.target.value }));
                                clearFieldError('treatments', 'price');
                            }}
                        />
                        {fieldErrors.treatments?.price ? <span className="field-error">{fieldErrors.treatments.price}</span> : null}
                    </label>

                    <label>
                        Photo URL
                        <input
                            className="payment-input"
                            value={treatmentForm.photoUrl}
                            onChange={(event) => setTreatmentForm((current) => ({ ...current, photoUrl: event.target.value }))}
                        />
                    </label>

                    <label>
                        Display Order
                        <input
                            className="payment-input"
                            type="number"
                            value={treatmentForm.displayOrder}
                            onChange={(event) => setTreatmentForm((current) => ({ ...current, displayOrder: event.target.value }))}
                        />
                    </label>

                    <label className="full">
                        Description
                        <textarea
                            className="payment-input admin-textarea"
                            rows={4}
                            value={treatmentForm.description}
                            onChange={(event) => {
                                setTreatmentForm((current) => ({ ...current, description: event.target.value }));
                                clearFieldError('treatments', 'description');
                            }}
                        />
                        {fieldErrors.treatments?.description ? (
                            <span className="field-error">{fieldErrors.treatments.description}</span>
                        ) : null}
                    </label>
                </form>

                <div className="row admin-form-actions">
                    <button type="button" className="btn-gold" onClick={createTreatment} disabled={loading}>
                        Create
                    </button>
                    <button type="button" className="btn-gold" onClick={updateTreatment} disabled={loading}>
                        Update
                    </button>
                    <button
                        type="button"
                        className="btn-gold"
                        onClick={() =>
                            setTreatmentForm({
                                id: '',
                                name: '',
                                durationMinutes: '',
                                price: '',
                                displayOrder: '',
                                photoUrl: '',
                                description: ''
                            })
                        }
                        disabled={loading}
                    >
                        Clear
                    </button>
                </div>

                {sectionErrors.treatments ? <p className="section-error">{sectionErrors.treatments}</p> : null}
                {sectionSuccess.treatments ? <p className="section-ok">{sectionSuccess.treatments}</p> : null}
            </section>

            <section id="admin-treatments-list" className="panel admin-workspace-section">
                <SectionTitle title="Treatments List" subtitle="Current Order" />

                <div className="table-wrapper">
                    <table>
                        <thead>
                            <tr>
                                <th>Name</th>
                                <th>Order</th>
                                <th>Duration</th>
                                <th>Price</th>
                                <th>Photo URL</th>
                                <th>ID</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody>
                            {sortedTreatments.map((item) => (
                                <tr key={item.id}>
                                    <td>{item.name}</td>
                                    <td>{item.displayOrder}</td>
                                    <td>{item.durationMinutes}</td>
                                    <td>{item.price}</td>
                                    <td>{item.photoUrl || '-'}</td>
                                    <td className="code">{item.id}</td>
                                    <td className="row admin-table-actions">
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => {
                                                fillTreatmentForm(item);
                                                scrollToAdminSection('admin-treatments-workspace');
                                            }}
                                        >
                                            Edit
                                        </button>
                                        <button
                                            type="button"
                                            className="btn-gold admin-inline-btn"
                                            onClick={() => deleteTreatment(item.id)}
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

            <section id="admin-treatments-reorder" className="panel admin-workspace-section">
                <SectionTitle title="Reorder Treatments" subtitle="Display Sequence" />

                <div className="row">
                    <select
                        value={treatmentReorderForm.id1}
                        onChange={(event) => setTreatmentReorderForm((current) => ({ ...current, id1: event.target.value }))}
                    >
                        <option value="">Select first treatment</option>
                        {sortedTreatments.map((item) => (
                            <option key={item.id} value={item.id}>
                                {item.name} ({item.displayOrder})
                            </option>
                        ))}
                    </select>

                    <select
                        value={treatmentReorderForm.id2}
                        onChange={(event) => setTreatmentReorderForm((current) => ({ ...current, id2: event.target.value }))}
                    >
                        <option value="">Select second treatment</option>
                        {sortedTreatments.map((item) => (
                            <option key={item.id} value={item.id}>
                                {item.name} ({item.displayOrder})
                            </option>
                        ))}
                    </select>

                    <button type="button" className="btn-gold" onClick={reorderTreatments} disabled={loading}>
                        Reorder
                    </button>
                </div>

                {sectionErrors.treatmentReorder ? <p className="section-error">{sectionErrors.treatmentReorder}</p> : null}
                {sectionSuccess.treatmentReorder ? <p className="section-ok">{sectionSuccess.treatmentReorder}</p> : null}
            </section>
        </div>
    );
}
