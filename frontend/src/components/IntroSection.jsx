import { motion } from 'framer-motion';
import { useNavigate } from 'react-router-dom';
import GoldButton from './ui/GoldButton';
import useHairSalonInfo from '../hooks/useHairSalonInfo';

export default function IntroSection() {
    const info = useHairSalonInfo();
    const navigate = useNavigate();
    const description =
        info?.description?.trim() ||
        'Cuts, styling, coloring, and grooming for women, men, and kids - from quick trims to bold color and restorative treatments in one welcoming space.';

    return (
        <section className="intro-section services-page-shell py-10" id="intro">
            <motion.div
                initial={{ opacity: 0, y: 12 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true }}
                transition={{ duration: 0.4 }}
                className="mx-auto max-w-[820px] space-y-4 text-center"
            >
                <p className="intro-eyebrow text-sm tracking-[0.26em] text-white uppercase">
                    Your Style, Perfected
                </p>
                <p className="text-sm text-smoke leading-relaxed">
                    {description}
                </p>
                <GoldButton onClick={() => navigate('/booking')}>
                    Book Appointment
                </GoldButton>
            </motion.div>
        </section>
    );
}
