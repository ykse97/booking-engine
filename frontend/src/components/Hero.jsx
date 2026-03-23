import { motion } from 'framer-motion';
import useHairSalonInfo from '../hooks/useHairSalonInfo';

export default function Hero() {
    const info = useHairSalonInfo();
    const phoneNumber = info?.phone?.trim() || '+35383 182 3456';
    const telHref = `tel:${phoneNumber.replace(/[^+0-9]/g, '')}`;

    return (
        <section
            id="hero"
            className="relative overflow-hidden bg-black min-h-[80vh]"
        >
            <video
                className="absolute inset-y-0 left-1/2 -translate-x-1/2 w-[100%] h-full object-cover bg-black"
                src="/hero-bg.mp4"
                autoPlay
                loop
                muted
                playsInline
                aria-hidden="true"
            />
            <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(0,0,0,0.14),transparent_38%),radial-gradient(circle_at_80%_0%,rgba(0,0,0,0.18),transparent_42%),linear-gradient(180deg,rgba(3,3,3,0.5),rgba(3,3,3,0.86))]" />
            <div
                className="absolute inset-0 pointer-events-none mix-blend-screen opacity-65"
                style={{
                    backgroundImage:
                        "radial-gradient(1px 1px at 20% 30%, rgba(243,201,122,0.6), transparent 50%), radial-gradient(1px 1px at 70% 10%, rgba(243,201,122,0.5), transparent 50%), radial-gradient(1px 1px at 40% 70%, rgba(243,201,122,0.45), transparent 50%), radial-gradient(1px 1px at 80% 65%, rgba(243,201,122,0.55), transparent 50%)"
                }}
            />
            <div className="services-page-shell pt-16 pb-14">
                <div className="mx-auto flex w-full max-w-[960px] flex-col items-center gap-6 text-center">
                <motion.p
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.1 }}
                    className="text-sm tracking-[0.32em] text-white uppercase"
                >
                    FADE · ROYAL · STYLE
                </motion.p>
                <motion.h1
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.15 }}
                    className="text-4xl sm:text-5xl font-heading text-ivory leading-tight"
                >
                    Premium barbers for <br /> the modern people
                </motion.h1>
                <motion.a
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: 0.2 }}
                    className="text-sm text-smoke tracking-[0.14em]"
                    href={telHref}
                >
                    {phoneNumber}
                </motion.a>
                </div>
            </div>
        </section>
    );
}
