import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { ChevronLeft, ChevronRight, X } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import SectionTitle from './ui/SectionTitle';
import GoldButton from './ui/GoldButton';
import { gallery } from '../data/gallery';
import useBodyScrollLock from '../hooks/useBodyScrollLock';

export default function GalleryShowcase({
    sectionId = 'gallery',
    className = 'services-page-shell py-8',
    title = 'Gallery',
    subtitle = 'Craft & Atmosphere',
    showBookingButton = true
}) {
    const navigate = useNavigate();
    const [selectedIndex, setSelectedIndex] = useState(null);

    const selectedImage = selectedIndex != null ? gallery[selectedIndex] : null;
    useBodyScrollLock(selectedImage != null);

    useEffect(() => {
        if (selectedIndex == null) {
            return undefined;
        }

        function handleKeyDown(event) {
            if (event.key === 'Escape') {
                setSelectedIndex(null);
            }

            if (event.key === 'ArrowLeft') {
                setSelectedIndex((current) => (current == null ? 0 : (current - 1 + gallery.length) % gallery.length));
            }

            if (event.key === 'ArrowRight') {
                setSelectedIndex((current) => (current == null ? 0 : (current + 1) % gallery.length));
            }
        }

        window.addEventListener('keydown', handleKeyDown);

        return () => {
            window.removeEventListener('keydown', handleKeyDown);
        };
    }, [selectedIndex]);

    function showPreviousImage() {
        setSelectedIndex((current) => (current == null ? 0 : (current - 1 + gallery.length) % gallery.length));
    }

    function showNextImage() {
        setSelectedIndex((current) => (current == null ? 0 : (current + 1) % gallery.length));
    }

    return (
        <section className={className} id={sectionId}>
            <SectionTitle title={title} subtitle={subtitle} />

            <div className="grid grid-cols-2 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {gallery.map((img, idx) => (
                    <motion.button
                        key={img}
                        type="button"
                        initial={{ opacity: 0, y: 10 }}
                        whileInView={{ opacity: 1, y: 0 }}
                        viewport={{ once: true }}
                        transition={{ delay: idx * 0.03 }}
                        onClick={() => setSelectedIndex(idx)}
                        className="group relative overflow-hidden rounded-[20px] border border-[#c6934b38] bg-[linear-gradient(180deg,rgba(12,12,12,0.96),rgba(4,4,4,0.94))] p-[10px] text-left shadow-card"
                    >
                        <div className="relative aspect-[4/5] overflow-hidden rounded-[16px] border border-[#f2d79f1f] bg-[rgba(7,7,7,0.95)]">
                            <img
                                src={img}
                                alt={`Gallery item ${idx + 1}`}
                                loading="lazy"
                                className="h-full w-full object-cover transition duration-500 group-hover:scale-[1.04]"
                            />
                            <div className="absolute inset-0 bg-[linear-gradient(180deg,rgba(3,3,3,0.06),rgba(3,3,3,0.64))]" />
                        </div>
                    </motion.button>
                ))}
            </div>

            {showBookingButton ? (
                <div className="mt-6 flex justify-center">
                    <GoldButton onClick={() => navigate('/booking')}>Book Appointment</GoldButton>
                </div>
            ) : null}

            {selectedImage ? (
                <div
                    className="fixed inset-x-0 bottom-0 top-[var(--site-header-height)] z-[120] bg-[rgba(3,3,3,0.9)] px-3 py-3 backdrop-blur-xl sm:px-5 sm:py-4 lg:px-8 lg:py-6"
                    onClick={() => setSelectedIndex(null)}
                    role="presentation"
                >
                    <div
                        className="relative mx-auto flex h-full w-full max-w-[1760px] items-center justify-center"
                        onClick={(event) => event.stopPropagation()}
                        role="dialog"
                        aria-modal="true"
                        aria-label={`Gallery image ${selectedIndex + 1}`}
                    >
                        <button
                            type="button"
                            onClick={() => setSelectedIndex(null)}
                            className="absolute right-1 top-1 z-20 inline-flex h-11 w-11 items-center justify-center rounded-full border border-[#c6934b45] bg-[rgba(6,6,6,0.94)] text-[#f5dfb1] shadow-[0_12px_30px_rgba(0,0,0,0.32)] transition hover:border-[#e3c07a] hover:text-[#fff1cf] sm:right-2 sm:top-2 sm:h-12 sm:w-12 lg:right-3 lg:top-3"
                            aria-label="Close gallery image"
                        >
                            <X size={20} />
                        </button>

                        <div className="relative flex h-full min-h-0 w-full items-center justify-center overflow-hidden rounded-[24px] border border-[#c6934b52] bg-[radial-gradient(circle_at_top,rgba(227,192,122,0.08),transparent_28%),linear-gradient(180deg,rgba(10,10,10,0.98),rgba(2,2,2,0.99))] p-3 shadow-[0_24px_60px_rgba(0,0,0,0.48)] sm:rounded-[28px] sm:p-4 lg:rounded-[32px] lg:p-6">
                            <img
                                src={selectedImage}
                                alt={`Gallery item ${selectedIndex + 1}`}
                                className="h-full w-full object-contain"
                            />

                            <button
                                type="button"
                                onClick={showPreviousImage}
                                className="absolute left-3 inline-flex h-11 w-11 items-center justify-center rounded-full border border-[#c6934b42] bg-[rgba(6,6,6,0.84)] text-[#f5dfb1] shadow-[0_10px_24px_rgba(0,0,0,0.26)] transition hover:border-[#e3c07a] hover:text-[#fff1cf] sm:left-5 sm:h-12 sm:w-12 lg:left-6 lg:h-14 lg:w-14"
                                aria-label="Show previous gallery image"
                            >
                                <ChevronLeft size={22} />
                            </button>

                            <button
                                type="button"
                                onClick={showNextImage}
                                className="absolute right-3 inline-flex h-11 w-11 items-center justify-center rounded-full border border-[#c6934b42] bg-[rgba(6,6,6,0.84)] text-[#f5dfb1] shadow-[0_10px_24px_rgba(0,0,0,0.26)] transition hover:border-[#e3c07a] hover:text-[#fff1cf] sm:right-5 sm:h-12 sm:w-12 lg:right-6 lg:h-14 lg:w-14"
                                aria-label="Show next gallery image"
                            >
                                <ChevronRight size={22} />
                            </button>
                        </div>
                    </div>
                </div>
            ) : null}
        </section>
    );
}
