import { useEffect, useMemo, useState } from 'react';
import { motion } from 'framer-motion';
import SectionTitle from './ui/SectionTitle';
import LuxuryCard from './ui/LuxuryCard';
import { publicApi } from '../api/publicApi';

const FALLBACK_IMAGE =
    'https://images.unsplash.com/photo-1621605815971-fbc98d665033?auto=format&fit=crop&w=900&q=80';

export default function BarbersSection() {
    const [barbers, setBarbers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let ignore = false;

        async function loadBarbers() {
            setLoading(true);
            setError('');

            try {
                const data = await publicApi.getBarbers();

                if (!ignore) {
                    setBarbers(Array.isArray(data) ? data : []);
                }
            } catch (err) {
                if (!ignore) {
                    setError(err.message || 'Failed to load barbers');
                    setBarbers([]);
                }
            } finally {
                if (!ignore) {
                    setLoading(false);
                }
            }
        }

        loadBarbers();

        return () => {
            ignore = true;
        };
    }, []);

    const sortedBarbers = useMemo(
        () => [...barbers].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)),
        [barbers]
    );

    return (
        <section className="services-page-shell py-8" id="barbers">
            <SectionTitle title="Meet Our Barbers" subtitle="master craftsmen" />

            {loading ? <div className="text-center text-ivory/80">Loading barbers...</div> : null}
            {error ? <div className="text-center text-red-300">{error}</div> : null}

            {!loading && !error ? (
                <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
                    {sortedBarbers.map((barber, idx) => (
                        <motion.div
                            key={barber.id}
                            className="h-full"
                            initial={{ opacity: 0, y: 12 }}
                            whileInView={{ opacity: 1, y: 0 }}
                            viewport={{ once: true }}
                            transition={{ delay: idx * 0.06 }}
                        >
                            <LuxuryCard className="h-full overflow-hidden rounded-[20px] border border-[#c6934b45] bg-[linear-gradient(180deg,rgba(12,12,12,0.96),rgba(4,4,4,0.94))]">
                                <div
                                    className="relative aspect-[4/3] w-full overflow-hidden bg-[rgba(7,7,7,0.95)]"
                                >
                                    <img
                                        src={barber.photoUrl || FALLBACK_IMAGE}
                                        alt=""
                                        aria-hidden="true"
                                        loading="lazy"
                                        decoding="async"
                                        fetchPriority={idx === 0 ? 'low' : 'auto'}
                                        className="h-full w-full object-cover"
                                    />
                                    <div
                                        className="absolute inset-0"
                                        aria-hidden="true"
                                        style={{
                                            background:
                                                'linear-gradient(180deg, rgba(12,9,7,0.1), rgba(12,9,7,0.6))'
                                        }}
                                    />
                                </div>

                                <div className="flex h-full flex-col gap-4 p-5 text-center sm:p-6">
                                    <div className="min-w-0 text-center">
                                        <h3 className="font-heading text-[1rem] leading-[1.45] tracking-[0.14em] uppercase text-ivory sm:text-[1.08rem]">
                                            {barber.name}
                                        </h3>
                                    </div>

                                    <p className="mx-auto inline-flex w-fit items-center justify-center rounded-full border border-[#c6934b35] bg-[rgba(255,255,255,0.03)] px-4 py-2 text-[10px] uppercase tracking-[0.22em] text-goldBright">
                                        {barber.role || 'Senior Barber'}
                                    </p>

                                    <div className="ornament !mt-0 !w-[72%]" />

                                    <p className="mx-auto max-w-[30ch] text-sm leading-7 text-smoke">
                                        {barber.bio || 'Sharp hands, calm energy, and premium attention to detail.'}
                                    </p>
                                </div>
                            </LuxuryCard>
                        </motion.div>
                    ))}
                </div>
            ) : null}
        </section>
    );
}
